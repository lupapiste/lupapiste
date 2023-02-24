(ns lupapalvelu.invoices
  "A common interface for accessing invoices and related data

  What comes to invoicing functionality, this namespace has the core.
  Transferbatch related functionality is in lupapalvelu.invoices.transfer-batch namespace, that that is used when needed.
  Common functionality shared with invoicing microservice is in lupapiste-invoice-commons namespace, this includes mainly state-transfer
  and validation related functionality.
  "
  (:require [lupapalvelu.application-schema :refer [Operation]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.invoices.pdf :as pdf]
            [lupapalvelu.invoices.schemas :refer [Invoice
                                                  UserOrganizationsInvoicesRequest
                                                  ->invoice-user
                                                  ->invoice-db]]
            [lupapalvelu.invoices.shared.util :as shared :refer [person-payer-update-keys
                                                                 company-payer-update-keys
                                                                 keys-used-to-update-invoice]]
            [lupapalvelu.invoices.transfer-batch :refer [add-invoice-to-transfer-batch! remove-invoice-from-transfer-batch!]]
            [lupapalvelu.invoices.util :refer [add-history-entry]]
            [lupapalvelu.money :refer [sum-with-discounts ->MoneyResponse discounted-value multiply-amount]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.price-catalogues :as catalogues]
            [lupapalvelu.user :as usr]
            [lupapiste-invoice-commons.states :refer [state-change-direction
                                                      move-to-state]]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail!] :as sade]
            [sade.strings :as ss]
            [sade.util :as util :refer [->long strip-nils]]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [swiss.arrows :refer :all]
            [taoensso.timbre :refer [debug warn warnf]]))

(defn- change-last-modified
  "Updates modified timestamp"
  ([modified-timestamp updated-doc]
   (assoc updated-doc :modified modified-timestamp))
  ([updated-doc]
   (change-last-modified updated-doc (sade/now))))

(defn invoice-param-sanity-check
  "Pre-checker for making sure that invoice application and price catalogue details are acceptable."
  [{:keys [application data created]}]
  (when-let [invoice-id (or (:invoice-id data) (:id (:invoice data)))]
    (let [invoice    (mongo/by-id :invoices invoice-id [:application-id])]
      (when-not (= (:id application) (:application-id invoice))
        (fail! :error.unauthorized))
      (when-let [catalog-id (some-> data :invoice :price-catalogue-id)]
        ;; We only check the catalog-id if it has changed. Catalog is deemed invalid ONLY
        ;; if it refers to wrong organization. Other mishaps (missing, draft, ..) are
        ;; allowed.
        (when-not (= catalog-id (:price-catalogue-id invoice))
          (when-let [{:keys [organization-id]} (mongo/by-id :price-catalogues catalog-id
                                                            [:organization-id])]
            (when-not (= organization-id (:organization application))
              (fail :error.price-catalogue.not-available))))))))

(defn invoicing-organization-ids
  "Takes an organization-ids and filters them by invoicing enabled status. Nil of none of the org-ids
  have invoicing enabled. `org-ids` can be list/vector/set or individual ids."
  [& org-ids]
  (when-let [org-ids (seq (mapcat #(cond-> % (not (coll? %)) list) org-ids))]
    (->> (org/get-organizations {:_id                     {$in org-ids}
                                 :scope.invoicing-enabled true}
                                [:_id])
         (map :id)
         seq)))


(defn invoicing-enabled
  "Pre-check that fails if the invoicing is not enabled for an organization. The actual organization
  depends on the context."
  [{:keys [organization application user data]}]
  (let [user-org-ids (usr/organization-ids user)
        org-id       (or (:organizationId data) (:organization-id data))]
    (when-not (cond
                application
                (some->> (force organization)
                         (org/resolve-organization-scope (:municipality application)
                                                         (:permitType application))
                         :invoicing-enabled)

                org-id
                (and (contains? user-org-ids org-id)
                     (invoicing-organization-ids org-id))

                (seq user-org-ids)
                (invoicing-organization-ids user-org-ids))
      (fail :error.invoicing-disabled))))

(defn invoice-is-draft
  "Pre-check that fails if the invoice does not exist or is not draft."
  [{{:keys [invoice-id]} :data}]
  (when invoice-id
    (when-not (mongo/any? :invoices {:_id   invoice-id
                                     :state :draft})
      (fail :error.invalid-invoice))))

(def state-actions {:add-to-transfer-batch add-invoice-to-transfer-batch!
                    :remove-from-transfer-batch remove-invoice-from-transfer-batch!})


(defn fetch-invoice [invoice-id]
  (mongo/by-id :invoices invoice-id))

(defn validate-invoice [invoice]
  (sc/validate Invoice invoice))

(defn sum-single-row [row]
  (let [sum-by-units (multiply-amount (:units row) (:price-per-unit row))
        discount-percent (:discount-percent row)]
    {:without-discount (->MoneyResponse sum-by-units)
     :with-discount    (-> sum-by-units
                           (discounted-value discount-percent)
                           ->MoneyResponse)}))

(defn- merge-invoice-rows [rows]
  (map (fn [row]
         {:row-total        (multiply-amount (:units row) (:price-per-unit row))
          :discount-percent (:discount-percent row)}) rows))

(defn sum-invoice [invoice]
  (let [rows (flatten (map :invoice-rows (:operations invoice)))
        merged-invoice-rows (merge-invoice-rows rows)]
    (assoc invoice :sum (sum-with-discounts :row-total :discount-percent merged-invoice-rows))))

(defn- enrich-sums-invoice-row
  "Adds the sum of the invoice row to it"
  [row]
  (assoc row :sums (sum-single-row row)))

(defn- enrich-rows-in-operations
  "Adds sums of the invoice rows to the rows"
  [{:keys [operations] :as invoice}]
  (let [enriched-operations (mapv
                              (fn [operation] (update operation :invoice-rows (partial mapv enrich-sums-invoice-row)))
                              operations)]
    (assoc invoice :operations enriched-operations)))

(defn enrich-invoice-sums-before-save [invoice]
  (-> invoice
      (enrich-rows-in-operations)
      (sum-invoice)))

(defn- update-invoice-row-vat-info [row]
  (let [vat   (some-> row :product-constants :alv
                      (ss/replace #"[\s %]" "")
                      (util/->int nil))
        total (some-> row :sums :with-discount :minor)]
    (cond-> row
      (and vat (< 0 vat 100) total (pos? total))
      (assoc :vat-percentage vat
             :vat-amount-minor (Math/round (- total (/ total (* 0.01 (+ vat 100)))))))))

(defn enrich-vat-info
  "Calculates `:vat-percentage` and `:vat-amount-minor` for each invoice row that has a
  valid `:vat` product constant. The VAT total is stored in `:vat-total-minor`. If none of
  the rows contain VAT information, the invoice is unmodified."
  [invoice]
  (let [updated (update invoice :operations
                        (fn [operations]
                          (map (fn [operation]
                                 (update operation :invoice-rows
                                         (partial map update-invoice-row-vat-info)))
                               operations)))]
    (if-let [vat-total (some->> updated :operations
                                (mapcat :invoice-rows)
                                (map :vat-amount-minor)
                                (filter integer?)
                                not-empty
                                (apply +))]
      (assoc updated :vat-total-minor vat-total)
      invoice)))

(defn ->address-str [address]
  (let [street (get-in address [:katu :value])
        zip (get-in address [:postinumero :value])
        post-office (get-in address [:postitoimipaikannimi :value])]
    (when address
      (format "%s %s %s" (str street) (str zip) (str post-office)))))

(defn person-billing-data-from [payer-doc]
  (let [person     (get-in payer-doc [:data :henkilo :henkilotiedot])
        address    (get-in payer-doc [:data :henkilo :osoite])
        first-name (get-in person [:etunimi :value])
        last-name  (get-in person [:sukunimi :value])]
    {:payer-type        "person"
     :person-id         (get-in person [:hetu :value])
     :entity-name       (when (or first-name last-name)
                          (format "%s %s" (or first-name "") (or last-name "")))
     :entity-address    (->address-str address)
     :billing-reference (get-in payer-doc [:data :laskuviite :value])}))

(defn company-billing-data-from [payer-doc]
  (let [yritys               (get-in payer-doc [:data :yritys])
        contact-person-fname (get-in yritys [:yhteyshenkilo :henkilotiedot :etunimi :value])
        contact-person-lname (get-in yritys [:yhteyshenkilo :henkilotiedot :sukunimi :value])]
    {:payer-type             "company"
     :company-id             (get-in yritys [:liikeJaYhteisoTunnus :value])
     :entity-name            (get-in yritys [:yritysnimi :value])
     :entity-address         (->address-str (:osoite yritys))
     :ovt                    (get-in yritys [:verkkolaskutustieto :ovtTunnus :value])
     :operator               (get-in yritys [:verkkolaskutustieto :valittajaTunnus :value])
     :billing-reference      (get-in payer-doc [:data :laskuviite :value])
     :company-contact-person (ss/join-non-blanks " " [contact-person-fname contact-person-lname])}))

(defn payer? [doc]
  (= :maksaja (tools/doc-subtype doc)))

(defn get-payer-doc [{:keys [documents]}]
  (first (filter payer? documents)))

(defn company? [payer-doc]
  (= "yritys" (get-in payer-doc [:data :_selected :value])))

(defn billing-data-from [application]
  (let [payer-doc (get-payer-doc application)]
    (-> (if (company? payer-doc)
          (company-billing-data-from payer-doc)
          (person-billing-data-from payer-doc))
        util/strip-blanks
        ss/trimwalk)))

(defn enrich-payer-info-from [application invoice]
  (let [payer (billing-data-from application)]
    (merge invoice (select-keys payer [:payer-type
                                       :entity-name
                                       :entity-address
                                       :billing-reference
                                       :person-id
                                       :company-id
                                       :ovt
                                       :operator
                                       :company-contact-person]))))

(defn create-invoice!
  [{:keys [application created user]} price-catalogue-id]
  (let [id      (mongo/create-id)
        user    (usr/summary user)
        invoice (->> {:id                 id
                      :application-id     (:id application)
                      :description        (:id application)
                      :organization-id    (:organization application)
                      :price-catalogue-id price-catalogue-id
                      :state              "draft"
                      :created            created
                      :created-by         user
                      :operations         []
                      :modified           created}
                     util/strip-nils
                     (enrich-payer-info-from application)
                     (add-history-entry user "create" created)
                     validate-invoice)]
    (mongo/insert :invoices invoice)
    invoice))

(defn delete-invoice!
  [invoice-id]
  (mongo/remove :invoices invoice-id))

(defn make-state-transfer [current-invoice new-state]
  (let [state-change-direction (state-change-direction (:state current-invoice) new-state :backend)]
    (if (#{:next :previous} state-change-direction)
      (move-to-state [:state] current-invoice new-state state-change-direction :backend)
      (do
        (when (nil? state-change-direction)
          (warnf "Unknown state-change-direction for invoice: %s. old-state: %s, new-state: %s"
                 (:id current-invoice)
                 (:state current-invoice)
                 new-state))
        {:actions []}))))

(defn update-valid-invoice [new-invoice user timestamp]
  (-<>> new-invoice
        enrich-invoice-sums-before-save
        (add-history-entry user "update" timestamp)
        (change-last-modified timestamp)
        (st/select-schema <> Invoice)
        validate-invoice))

(defn- invoice-catalogue [{:keys [organization]} {:keys [price-catalogue-id]}]
  (when price-catalogue-id
    (mongo/select-one :price-catalogues {:organization-id organization
                                         :_id             price-catalogue-id
                                         :state           :published})))

(defn enrich-workdays [{:keys [application]} {:keys [work-start-ms work-end-ms]
                                              :as   invoice}]
  (if (= (:permitType application) "YA")
    (let [invoice (cond-> invoice
                    (not (or work-start-ms work-end-ms))
                    (util/assoc-when :work-start-ms (:started application)
                                     :work-end-ms (:closed application)))]
      ;; Workdays cannot be nil, since its existence is used as a "signal" in the frontend.
      (assoc invoice
             :workdays (merge {} (pdf/workdays invoice (invoice-catalogue application
                                                                          invoice)))))
    invoice))

(defn update-worktime [{:keys [application user created data]}]
  (let [{:keys [invoice-id start-ts
                end-ts]} data
        invoice          (assoc (fetch-invoice invoice-id)
                                :work-start-ms start-ts
                                :work-end-ms end-ts)
        days             (pdf/workdays invoice (invoice-catalogue application invoice))]
    (when days
      (mongo/update-by-id :invoices invoice-id
                          (dissoc (update-valid-invoice invoice user created) :id))
      days)))

(defn update-invoice!
  [{:keys [id payer-type] :as invoice} user timestamp]
  (let [current-invoice   (mongo/by-id :invoices id)
        deprecated        (case payer-type
                            "person"  company-payer-update-keys
                            "company" person-payer-update-keys
                            nil)
        new-invoice       (-> (apply dissoc current-invoice deprecated)
                              (merge (select-keys invoice keys-used-to-update-invoice))
                              util/strip-nils)
        {:keys [actions]} (make-state-transfer current-invoice (:state new-invoice))
        update-result     (update-valid-invoice new-invoice user timestamp)]
    (mongo/update-by-id "invoices" id (dissoc update-result :id))
    (when (not-empty actions)
      (doseq [action actions
              :let   [action-fn (action state-actions)]
              :when  action-fn]
        (action-fn new-invoice (:created-by new-invoice) timestamp)))
    update-result))

(defn fetch-by-application-id [application-id]
  (mongo/select "invoices" {:application-id application-id}))

(sc/defn ^:always-validate get-operations-from-application :- [Operation]
  "Returns a vector (primaryOperation being first) of operations from application, by combining primary and secondary operations to one seq"
  [application]
  (let [primary-operation (:primaryOperation application)
        secondary-operations (:secondaryOperations application)]
    (concat [primary-operation] secondary-operations)))

(defn fetch-invoices-for-organizations [organization-ids & [{:keys [states from until limit exclude-states] :as filters}]]
  (let [in-any-given-org {:organization-id {$in organization-ids}}
        filters (cond-> []
                  (seq states) (conj {:state   {$in states}})
                  (seq exclude-states) (conj {:state {$nin exclude-states}})
                  from         (conj {:created {$gte from}})
                  until        (conj {:created {$lte until}}))
        query {$and (concat [in-any-given-org] filters)}]

    ;;sort-by :created ascending (-1 = newest first)
    (mongo/select-ordered :invoices query {:created -1} (or limit 0))))

(defn enrich-org-data [{:keys [user-organizations organization]} {:keys [organization-id] :as invoice}]
  (let [organization (or (force organization)
                         (util/find-by-id organization-id user-organizations))
        localized-names (:name organization)]
    (assoc-in invoice [:enriched-data :organization :name] localized-names)))

(defn enrich-application-data [application-or-applicationss {:keys [application-id state] :as invoice}]
  (let [{:keys [address] :as application}  (util/pcond->> application-or-applicationss
                                             sequential? (util/find-by-id application-id))
        {:keys [entity-address] :as payer} (billing-data-from application)]
    (merge
      (assoc-in invoice [:enriched-data :application] {:address address
                                                       :payer   payer})
      ;; Entity-address is generated only when the invoice is being checked.
      (when (= "checked" state)
        {:entity-address (or entity-address "")}))))

(defn new-verdict-invoice
  "Post-fn for Pate verdict publishing commands. Creates new invoice
  draft for the application."
  [{:keys [user created application] :as command} _]
  ;; Pre-checker is nil on success.
  (when (nil? (invoicing-enabled command))
    (debug "Attempting to create invoice")
    (let [ops      (get-operations-from-application application)
          op-names (map :name ops)
          inv-user (->invoice-user user)
          catalog (first (catalogues/application-price-catalogues application
                                                                  created))]
      (when-let [rows (some->> catalog
                               :rows
                               (filter (util/fn->> :operations
                                                   (util/intersection-as-kw op-names)
                                                   not-empty))
                               (map-indexed (fn [i row]
                                              (assoc row :order-number i)))
                               not-empty)]
        (->> (->invoice-db
               {:id                 (mongo/create-id)
                :price-catalogue-id (:id catalog)
                :state              "draft"
                :operations         (map (fn [op]
                                           {:operation-id (:id op)
                                            :name         (:name op)
                                            :invoice-rows (some->> rows
                                                                   (filter (util/fn-> :operations
                                                                                      (util/includes-as-kw? (:name op))))
                                                                   (map shared/->invoice-row))})
                                         ops)}
               command
               user)
             (enrich-payer-info-from application)
             (add-history-entry inv-user "created-from-verdict" created)
             (change-last-modified created)
             (sc/validate Invoice)
             (mongo/insert :invoices))
        (debug "Attempt to insert invoice done.")))))

(defn unit-price-within-limits? [{:keys [price-per-unit min-unit-price max-unit-price]}]
  (shared/between? min-unit-price max-unit-price price-per-unit))

(defn invoice-unit-prices-within-limits? [{:keys [operations]}]
  (when-let [invoice-rows (mapcat :invoice-rows operations)]
    (->> invoice-rows
         (map unit-price-within-limits?)
         (every? identity))))

(defn unit-prices-within-limits? [cmd]
  (let [invoice (get-in cmd [:data :invoice])]
    (when (not (invoice-unit-prices-within-limits? invoice))
      (fail :error.unit-price-not-within-allowed-limits))))

(defn- any-non-numeric-value? [& xs]
  (not (every? #(or (number? %) (ss/numeric? %)) (remove nil? xs))))

(defn validate-user-organization-invoices-request [{{:keys [states from until limit] :as optional-params} :data :as command}]
  (try
    (when (any-non-numeric-value? from until limit)
      (throw (Exception. (str "Some optional filter was provided but its value was not a number." (format "from:% until:% limit:%" from until limit)))))

    (sc/validate UserOrganizationsInvoicesRequest (strip-nils {:states states
                                                               :from  (->long from)
                                                               :until (->long until)
                                                               :limit (->long limit)}))
    nil
    (catch Exception e
      (warn "Invalid user-organization-invoices request" (.getMessage e))
      (fail :error.invalid-request))))

;; ---------------------------
;; Backend ID configuration
;; ---------------------------

(defn command->invoicing-configs
  [{:keys [organization data]}]
  (some-> (if organization
            (force organization) ; Application action
            (some-<> data
                     :organizationId
                     (mongo/by-id :organizations <> [:invoicing-config
                                                     :invoicing-backend-id-config])))
          (select-keys [:invoicing-config :invoicing-backend-id-config])))

(defn- known-code-id? [config id]
  {:pre [id]}
  (boolean (util/find-by-id id (:codes config))))

(defn- upsert-code [org-id {:keys [codes] :as config} {id :id :as code}]
  ;; Codes must be unique
  (when (some (fn [other]
                (and (= (:code other) (:code code))
                     (not= (:id other) id)))
              codes)
    (fail! :error.code-reserved))
  (when (and id (not (known-code-id? config id)))
    (fail! :error.id-not-found))

  (if id
    (mongo/update-by-query :organizations
                           {:_id                               org-id
                            :invoicing-backend-id-config.codes {$elemMatch {:id id}}}
                           {$set {:invoicing-backend-id-config.codes.$.code (:code code)
                                  :invoicing-backend-id-config.codes.$.text (:text code)}})
    (mongo/update-by-id :organizations
                        org-id
                        {$push {:invoicing-backend-id-config.codes
                                (assoc code :id (mongo/create-id))}})))


(defn configure-backend-id [org-id config op]
  (let [[k v] (first op)
        ubi   #(mongo/update-by-id :organizations org-id %)]
    (case k
      :set
      (ubi {$set {:invoicing-backend-id-config.numbers (:numbers v)}})

      :delete
      (when (known-code-id? config (:id v))
        (ubi {$pull {:invoicing-backend-id-config.codes {:id (:id v)}}}))

      :upsert
      (upsert-code org-id config v))))
