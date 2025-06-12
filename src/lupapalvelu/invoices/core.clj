(ns lupapalvelu.invoices.core
  (:require [clojure.set :as set]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.invoices.pdf :as pdf]
            [lupapalvelu.invoices.schemas :refer [Invoice]]
            [lupapalvelu.invoices.transfer-batch :as tb]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.price-catalogues :as catalogues]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail!] :as sade]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [swiss.arrows :refer :all]))

(set! *warn-on-reflection* true)

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

(defn invoice-check
  "Pre-check that fails if the invoice does not exist or is in the wrong state."
  [& states]
  (fn [{:keys [data application]}]
    (when-let [invoice-id (:invoice-id data)]
      (when-not (mongo/any? :invoices
                            (util/strip-nils {:_id            invoice-id
                                              :application-id (:id application)
                                              :state          (when (seq states)
                                                                {$in states})}))
        (fail :error.invalid-invoice)))))

(defn enrich-sum-total
  "Adds each row `:total-cents` together into top-level `:sum-total-cents`."
  [{:keys [rows] :as invoice}]
  (->> (keep :total-cents rows)
       (apply +)
       (assoc invoice :sum-total-cents)))

(defn enrich-vat-info
  "Calculates `:vat-info` map (with `:vat-percentage`,`:vat-cents` and `:taxfree-cents`) for
  each invoice row that has a valid `:vat` product constant. The invoice VAT total is
  stored in the top-level `:sum-vat-info` map (`:vat-cents` and `:taxfree-cents`). If
  none of the rows contain VAT information, the invoice is unmodified."
  [{:keys [rows] :as invoice}]
  (let [new-rows                  (map (fn [{:keys [total-cents product-constants] :as row}]
                                         (let [vat-info (invoices-util/extract-vat total-cents
                                                                                   (:alv product-constants))]
                                           (cond-> row
                                             vat-info (assoc :vat-info vat-info))))
                                       rows)
        vat-infos                 (seq (keep :vat-info new-rows))

        {:keys [sum-total-cents]} (enrich-sum-total invoice)
        sum-vat-cents             (apply + (map :vat-cents vat-infos))]
    (cond-> invoice
      vat-infos (assoc :rows new-rows
                       :sum-vat-info {:vat-cents     sum-vat-cents
                                      :taxfree-cents (- sum-total-cents sum-vat-cents)}))))

(sc/defn ^:always-validate new-invoice-draft :- Invoice
  ([{:keys [application organization created user]} price-catalogue-id]
   (let [user          (usr/summary user)
         backend-codes (invoices-util/backend-codes organization)]
     (util/strip-nils {:id                 (mongo/create-id)
                       :application-id     (:id application)
                       :description        (:id application)
                       :application-payer? true
                       :organization-id    (:organization application)
                       :price-catalogue-id price-catalogue-id
                       :backend-code       (when (= 1 (count backend-codes))
                                             (-> backend-codes first :code))
                       :state              "draft"
                       :created            created
                       :created-by         user
                       :operations         []
                       :rows               []
                       :modified           created
                       :modified-by        user})))
  ([{:keys [application created] :as command}]
   (new-invoice-draft command
                      (some-> (catalogues/application-price-catalogues application
                                                                       created)
                              first
                              :id))))

(defn create-invoice
  [command]
  (let [invoice (new-invoice-draft command)]
    (mongo/insert :invoices invoice)
    invoice))

(defn delete-invoice!
  [invoice-id]
  (mongo/remove :invoices invoice-id))

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
      (util/assoc-when invoice
                       :workdays (pdf/workdays invoice (invoice-catalogue application
                                                                          invoice))))
    invoice))

(defn command->invoice
  ([{:keys [data application]} resolve-payer?]
   (cond->  (mongo/select-one :invoices {:_id            (:invoice-id data)
                                         :application-id (:id application)})
     resolve-payer? (invoices-util/resolve-invoice-payer-fields application)))
  ([command]
   (command->invoice command true)))

(defn- invoice->catalogue
  [{:keys [price-catalogue-id organization-id]}]
  (when-not (ss/blank? price-catalogue-id)
    (mongo/select-one :price-catalogues
                      {:_id             price-catalogue-id
                       :organization-id organization-id
                       :state           :published})))

(defn make-modified [{:keys [user created]}]
  {:modified    created
   :modified-by (usr/summary user)})

(defn- modify-invoice
  "Adds modified(-by) information and persists `mongo-updates`. Returns `:modified` -
  timestamp map, where the timestamp is the `command`'s `:created` value. `:modified-by`
  is not provided outside of mongo."
  [command invoice-id mongo-updates]
  (let [modified (make-modified command)]
    (mongo/update-by-id :invoices
                        invoice-id
                        (update mongo-updates $set merge modified))
    (select-keys modified [:modified])))

(defn update-worktime
  ([{:keys [application] :as command} start-ts end-ts]
   (let [{invoice-id :id
          :as        invoice} (command->invoice command)
         updates              {:work-start-ms start-ts
                               :work-end-ms   end-ts}
         days                 (pdf/workdays (merge invoice updates)
                                            (invoice-catalogue application invoice))]
     (when days
       (assoc (modify-invoice command invoice-id {$set updates})
              :workdays days))))
  ([command]
   (let [{invoice-id :id
          :as        invoice} (-<> (command->invoice command)
                                   (dissoc :work-start-ms :work-end-ms)
                                   (enrich-workdays command <>))]
     (merge (modify-invoice command invoice-id
                            {$unset {:work-start-ms true
                                     :work-end-ms   true}})
            (select-keys invoice [:workdays :work-start-ms :work-end-ms])))))

(defn- fix-catalogue-row [{:keys [discount-percent] :as row}]
  (cond-> (-> row
              (dissoc :id :operations)
              (set/rename-keys {:max-total-price :max-unit-price
                                :min-total-price :min-unit-price}))
    ;; No need to show zero discounts.
    (some-> discount-percent zero?)
    (dissoc :discount-percent)

    true util/strip-nils))

(defn- make-operation-rows [{op-id :id op-name :name} catalog]
  (some->> (:rows catalog)
           (keep (fn [{:keys [operations] :as row}]
                   (when (contains? (set operations) op-name)
                     (assoc (fix-catalogue-row row)
                            :id (mongo/create-id)
                            :operation-id op-id))))
           seq))

(defn- row-count-exceeded?
  [{:keys [invoicing-config]} rows]
  (when-let [limit (get invoicing-config :max-invoice-row-count-in-transfer-batch)]
    (< limit rows)))

(defn- assert-row-count-not-exceeded!
  "No individual invoice may exceed the maximum transfer batch row count."
  [organization {:keys [rows]} rows-to-add]
  (when (row-count-exceeded? (force organization) (+ (count rows) rows-to-add))
    (fail! :error.invoice.too-many-rows)))

(defn add-operation
  "Adds operation and its default rows or fail!s."
  [{:keys [application organization] :as command} operation-id]
  (let [op       (-> operation-id
                     (util/find-by-id (app-utils/get-operations application))
                     (select-keys [:id :name]))
        invoice  (command->invoice command false)
        rows     (make-operation-rows op (invoice->catalogue invoice))
        _        (assert-row-count-not-exceeded! organization invoice (count rows))
        modified (modify-invoice command
                                 (:id invoice)
                                 (cond-> {$push {:operations op}}
                                   rows (assoc-in [$push :rows $each] rows)))]
    (assoc modified
           :operation op
           :rows (or rows []))))

(defn delete-operation
  [command operation-id]
  (let [{:keys [id operations]} (command->invoice command)]
    (when (util/find-by-id operation-id operations)
      (modify-invoice command
                      id
                      {$pull {:operations {:id operation-id}
                              :rows       {:operation-id operation-id}}}))))

(defn add-row
  [{:keys [organization] :as command} invoice-id operation-id row-id]
  (let [base-row {:id           (mongo/create-id)
                  :operation-id operation-id
                  :unit         "kpl"}
        custom?  (util/=as-kw row-id :custom)
        invoice  (command->invoice command)
        _        (assert-row-count-not-exceeded! organization invoice 1)
        cat-row  (when-not custom?
                   (some->> invoice
                            invoice->catalogue
                            :rows
                            (util/find-by-id row-id)
                            fix-catalogue-row))
        row      (cond
                   cat-row (merge base-row (dissoc cat-row :id))
                   custom? (assoc base-row :custom? true)
                   :else   (fail! :error.not-found))
        modified (modify-invoice command invoice-id
                                 {$push {:rows row}})]
    (assoc modified :row row)))

(defn delete-row
  [command row-id]
  (let [{:keys [id rows]} (command->invoice command)]
    (when (util/find-by-id row-id rows)
      (modify-invoice command id {$pull {:rows {:id row-id}}}))))

(defn calculate-row-total
  "Returns the `total-cents` value taking into consideration the units, price and
  discount. Note that only the end result is scaled. Thus, even with the same unit prices
  and discounts the effective unit price could differ between rows/invoices due to
  cumulative rounding."
  [{:keys [units price-per-unit discount-percent]}]
  (let [units          (or units 0)
        price-per-unit (or price-per-unit 0)
        factor         (- 1 (/ (or discount-percent 0) 100.0))]
    (->> (* price-per-unit factor)
         (* units)
         invoices-util/num->cents)))

(defmulti ^:private row-field-updates
  "Each method returns operation - value map or throws via fail!
  Supported operations
   :row-in and value is a map of fields (kw-paths and values) to be merged into row.
   :row-out and value is a list of keys (kw-paths) to be removed from the row.
   :row-error value is a key - error-code map"
  (fn [_command _row k _value] k))

(defmethod row-field-updates :default
  [_ {:keys [custom?]} k v]
  (if (and (#{:text :unit} k) (not custom?))
    {:row-error {k :error.read-only}}
    {:row-in {k v}}))

(defmethod row-field-updates :price-per-unit
  [_ {:keys [custom? min-unit-price max-unit-price] :as row} k v]
  (let [v       (some-> v invoices-util/parse-number invoices-util/scale)
        calc-fn #(calculate-row-total (assoc row k v))]
    (cond
      (not (or custom?
               (number? min-unit-price)
               (number? max-unit-price)))
      {:row-error {k :error.read-only}}

      (nil? v) {:row-out [k]
                :row-in  {:total-cents (calc-fn)}}

      (some-> min-unit-price (> v))
      {:row-error {k [:invoice.error.min-unit-price (invoices-util/euro-format min-unit-price)]}}

      (some-> max-unit-price (< v))
      {:row-error {k [:invoice.error.max-unit-price (invoices-util/euro-format max-unit-price)]}}

      :else    {:row-in {k            v
                         :total-cents (calc-fn)}})))

(defmethod row-field-updates :units
  [_ row k v]
  (let [;; Integration requirement: units granularity is two decimals.
        v      (some-> v invoices-util/parse-number invoices-util/scale)
        row-in {:row-in {:total-cents (calculate-row-total (assoc row k v))}}]
    (if v
      (assoc-in row-in [:row-in k] v)
      (assoc row-in :row-out [k]))))

(defmethod row-field-updates :discount-percent
  [_ row k v]
  (let [;; Integration requirement: percentage granularity is two decimals.
        v        (some-> v invoices-util/parse-percentage invoices-util/scale)
        total-fn #(hash-map :total-cents (calculate-row-total (assoc row k v)))]
    (cond
      (nil? v) {:row-out [k]
                :row-in  (total-fn)}

      (not (<= 0 v 100))
      {:row-error {k :invoice.error.percentage}}

      :else    {:row-in (assoc (total-fn) k v)})))

(defn set-row-field [command row-id path value]
  (let [invoice         (command->invoice command)
        row             (util/find-by-id row-id (:rows invoice))
        {:keys [row-in row-out row-error]
         :as   updates} (if row
                          (try
                            (row-field-updates command row (util/kw-path path) value)
                            (catch Exception _
                              {:row-error {(last path) :error.invalid-value}}))
                          (fail! :error.not-found))]
    (cond
      row-error {:row-error row-error}

      (seq updates)
      (let [modified (make-modified command)
            $-fn     (partial util/map-keys #(util/kw-path :rows.$ %)) ]
        (mongo/update-by-query :invoices
                               {:_id  (:id invoice)
                                :rows {$elemMatch {:id row-id}}}
                               (cond-> {$set (merge modified ($-fn row-in))}
                                 row-out (merge {$unset ($-fn (zipmap row-out (cycle [true])))})))
        (util/strip-nils {:in      (select-keys modified [:modified])
                          :row-in  row-in
                          :row-out row-out})))))

(defmulti ^:private invoice-field-updates
  "Each method returns operation - value map or throws via fail!
   Supported operations

  :in   Value is a map of fields to be merged into invoice.

  :out  Value is a list of keys to be removed from the invoice."
  (fn [_command _invoice k _value] k))

(defmethod invoice-field-updates :default
  [_ {:keys [application-payer?]} k v]
  (if (and application-payer? (invoices-util/invoice-payer-fields k))
    (fail! :error.read-only)
    {:in {k v}}))

;; Backend code can be set if it is supported by the configuration and the backend-id has
;; not already been set.
(defmethod invoice-field-updates :backend-code
  [{:keys [organization]} invoice _ code]
  (cond
    (:backend-id invoice)
    (fail! :error.backend-id-already-defined)

    (ss/blank? code)
    {:out [:backend-code]}

    (not (util/find-by-key :code code
                           (invoices-util/backend-codes organization)))
    (fail! :error.unknown-backend-code)

    :else {:in {:backend-code code}}))

(defmethod invoice-field-updates :transfer-batch-id
  [_ {:keys [state] :as invoice} k batch-id]
  (cond
    (util/not=as-kw state :confirmed)
    (fail! :error.invoice.illegal-state)

    (not (tb/transfer-batch-for-invoice invoice batch-id))
    (fail! :error.not-found)

    :else {:in {k batch-id}}))

(defmethod invoice-field-updates :application-payer?
  [{:keys [application]} _ k flag?]
  ;; Even if the checkbox is cleared, we intialize the payer fields with the application
  ;; information.
  {:in (assoc (invoices-util/application-payer-info application)
              k flag?)})

(defmethod invoice-field-updates :price-catalogue-id
  [{:keys [application]} _ k v]
  (let [{:keys [organization permitType]} application]
    (cond
      (ss/blank? v) {:out [k]}

      (mongo/any? :price-catalogues
                  {:_id             v
                   :organization-id organization
                   :state           :published
                   :type            (if (= permitType "YA") :YA :R)})
      {:in {k v}}

      :else         (fail! :error.not-found))))

(defmethod invoice-field-updates :state
  [{:keys [organization] :as command}
   {:keys [state rows organization-id backend-code backend-id] :as invoice}
   k
   new-state]
  ;; We only consider the state changes that are explicitly set by the user.
  (let [base {:in {k new-state}}]
    (case (util/kw-path state new-state)
      :draft.checked
      (if (zero? (count rows))
        (fail! :error.invoice.no-rows)
        base)

      :checked.draft
      (assoc-in base [:in :application-payer?] true)

      :checked.confirmed
      (let [backend (when (and (ss/not-blank? backend-code)
                               (ss/blank? backend-id))
                      (if-let [backend-id (invoices-util/generate-backend-id organization
                                                                             backend-code)]
                        {:in {:backend-id backend-id}}
                        {:out [:backend-code]}))]
        (-> base
            (util/deep-merge backend)
            (assoc-in [:in :transfer-batch-id]
                      (or (tb/transfer-batch-for-invoice invoice)
                          (tb/insert-new-transfer-batch command organization-id)))))

      :confirmed.checked
      (assoc base :out [:transfer-batch-id])

      (fail! :error.invoice.illegal-state))))

(defn set-invoice-field
  "Updates the invoice according to command data. Returns  :in (excluding the target
  field), :out and :errors map (if `errors?`)."
  ([{:keys [data] :as command} errors?]
   (let [{:keys [invoice-id field
                 value]}  (ss/trimwalk (dissoc data :id))
         field            (keyword field)
         invoice          (command->invoice command)
         {:keys [in out]} (invoice-field-updates command invoice field value)
         changes          (merge in (make-modified command))
         updates          (cond-> {$set changes}
                            (seq out) (assoc $unset (zipmap out (cycle [true]))))]
     (mongo/update-by-id :invoices invoice-id updates)
     (util/strip-nils
       {:in     (dissoc changes :modified-by field)
        :out    out
        :errors (when errors?
                  (invoices-util/validate-invoice-fields (merge invoice changes)))})))
  ([command]
   (set-invoice-field command true)))

(defn- invoices-for-org-query
  [organization-ids & {:keys [states from until exclude-states?]}]
  (cond-> {:organization-id {$in organization-ids}}
    (seq states) (assoc-in [:state (if exclude-states? $nin $in)] states)
    from         (assoc-in [:created $gte] from)
    until        (assoc-in [:created $lte] until)))

(defn fetch-invoices-for-organizations [organization-ids & {:keys [limit] :as opts}]
  (let [query (invoices-for-org-query organization-ids opts)]
    ;;sort-by :created ascending (-1 = newest first)
    (mongo/select-ordered :invoices query {:created -1} (or limit 0))))

(defn new-verdict-invoice
  "Post-fn for Pate verdict publishing commands. Creates new invoice draft for the
  application, if none yet exists. Operation is created even if it does not have any
  products defined in the catalogue (empty operation). This approach supports more use
  cases and it is more convenient to delete an empty operation than create new invoice.
  However, if the organization has not published any price catalogues, the invoice draft
  is not created."
  [{:keys [created application] :as command} _]
  ;; Pre-checker is nil on success.
  (when (nil? (invoicing-enabled command))
    (when-let [catalog (first (catalogues/application-price-catalogues application
                                                                       created))]
      (when-not (mongo/any? :invoices {:application-id (:id application)})
        (some->> (app-utils/get-operations application)
                 (map #(select-keys % [:id :name]))
                 (reduce (fn [acc op]
                           (-> acc
                               (update :operations concat [op])
                               (update :rows concat (make-operation-rows op catalog))))
                         {})
                 not-empty
                 (merge (new-invoice-draft command (:id catalog)))
                 (mongo/insert :invoices))))))

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
