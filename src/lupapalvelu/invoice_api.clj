(ns lupapalvelu.invoice-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand defraw] :as action]
            [lupapalvelu.application-schema :refer [Operation]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.invoices.excel-converter :as invoices-excel]
            [lupapalvelu.invoices.pdf :as pdf]
            [lupapalvelu.invoices.schemas
             :refer [->invoice-user Invoice NoBillingPeriod PriceCatalogueDraft
                     PriceCatalogueDraftEditParams MoveRow]]
            [lupapalvelu.invoices.transfer-batch
             :refer [get-transfer-batch-for-orgs close-transfer-batch!
                     user-has-role-in-transfer-batch-org?] :as tb]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict :refer [try-again-page]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.price-catalogues :as catalogues]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail!]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :refer [->long] :as util]
            [schema.core :as sc]
            [swiss.arrows :refer [-<>]]
            [taoensso.timbre :refer [debug error trace]]))

;; Pre-checkers

(defn org-has-supported-permit-type
  "Pre-checker that fails if org does not have R or YA permit type or has both"
  [{{:keys [organizationId]} :data}]
  (when-not (ss/blank? organizationId)
    (let [org         (org/get-organization organizationId)
          r           (org/has-permit-type? org :R)
          ya          (org/has-permit-type? org :YA)
          has-neither (and (not r) (not ya))
          has-both    (and r ya)]
      (when (or has-both has-neither)
        (fail :error.price-catalogue.unsupported-permit-type)))))

(defn price-catalogue-not-valid-yet
  "Pre-check that fails if the price catalogue is not published AND not valid yet."
  [{:keys [created data]}]
  (let [{:keys [organizationId price-catalogue-id]} data]
    (when (and price-catalogue-id
               (zero? (mongo/count :price-catalogues
                                   {:_id             price-catalogue-id
                                    :organization-id organizationId
                                    :state           "published"
                                    :valid-from      {$gt created}})))
      (fail :error.price-catalogue.not-found))))

(sc/defn ^:always-validate price-catalogue-exists
  "Returns pre-checker that fails if the given catalogue does not exist.
   Additional conditions (every given condition must match):
    :draft?     MUST be draft.
    :published? MUST be published
    :ya?        MUST be YA catalogue

  Corresponding negations (MUST NOT be ...):
    :!draft, :!published and :!ya

  Note: if the price-catalogue-id is not given, then pre-check succeeds."
  [& conditions :- [(sc/enum :draft? :published? :ya?
                             :!draft :!published :!ya)]]
  (let [{:keys [draft? published?  ya? !draft !published
                !ya]} (zipmap conditions (repeat true))]
    (fn [{:keys [data organization]}]
      (when-let [catalog-id (:price-catalogue-id data)]
        (let [org-id            (or (:id (force organization))
                                    (:organizationId data))
              {:keys [type state]
               :as   catalog}   (mongo/select-one :price-catalogues
                                                  {:_id             catalog-id
                                                   :organization-id org-id}
                                                  [:type :state])
              [draft published] (map #(= % state) ["draft" "published"])
              ya                (= type "YA")]
          (some-> (cond
                    (nil? catalog)
                    :error.price-catalogue.not-found

                    (or (and draft? (not draft))
                        (and !draft draft)
                        (and published? (not published))
                        (and !published published))
                    :error.price-catalogue.wrong-state

                    (or (and ya? (not ya))
                        (and !ya ya))
                    :error.price-catalogue.wrong-type)
                  fail))))))

(defn check-valid-period
  "Pre-checker that fails if data has incorrect `:valid` period."
  [{data :data}]
  (when (some-> data :edit :valid)
    (let [{:keys [valid-from valid-until]} (catalogues/parse-valid-period (:edit data))]
      (when (and valid-from valid-until (>= valid-from valid-until))
        (fail :error.price-catalogue.bad-dates)))))

;; ------------------------------------------
;; Invoice API
;; ------------------------------------------

(defcommand new-invoice-draft
  {:description         "Returns freshly made (and stored) invoice"
   :permissions         [{:required [:invoice/edit]}]
   :parameters          [id]
   :optional-parameters [price-catalogue-id]
   :input-validators    [(partial action/non-blank-parameters [:id])]
   :pre-checks          [invoices/invoicing-enabled
                         (action/some-pre-check
                           (util/fn-> :data :price-catalogue-id)
                           (price-catalogue-exists :published?))]
   :states              states/post-submitted-states}
  [command]
  (->>  (invoices/create-invoice! command price-catalogue-id)
        (invoices/enrich-workdays command)
        (ok :invoice)))

(defcommand update-invoice
  {:description      "Updates an existing invoice in the db"
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice]
   :input-validators [(partial action/non-blank-parameters [:id])
                      invoices/unit-prices-within-limits?]
   :pre-checks       [invoices/invoicing-enabled
                      invoices/invoice-param-sanity-check]
   :states           states/post-submitted-states}
  [{:keys [data user created] :as command}]
  (do (trace "update-invoice invoice-request:" (:invoice data))
      (invoices/update-invoice! (:invoice data) (->invoice-user user) created)
      (ok)))

(defcommand close-transfer-batch
  {:description      "Close transfer batch"
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [organizationId transfer-batch-id]
   :input-validators [(partial action/non-blank-parameters [:transfer-batch-id :organizationId])]
   :pre-checks       [invoices/invoicing-enabled]}
  [{:keys [user user-organizations data created] :as command}]
  (trace "close-transfer-batch request" data)
  (let [user-org-ids (doall (roles/organization-ids-by-roles user #{:biller}))]
    (if (close-transfer-batch! (:transfer-batch-id data) user-org-ids (->invoice-user user) created)
      (ok)
      (fail :error.unauthorized))))

(defcommand delete-invoice
  {:description      "Delete invoice from db. Deletes only invoices in draft state"
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])]
   :pre-checks       [invoices/invoicing-enabled
                      invoices/invoice-is-draft
                      invoices/invoice-param-sanity-check]
   :states           states/post-submitted-states}
  [{:keys [data] :as command}]
  (do (debug "delete-invoice invoice-request:" data)
      (invoices/delete-invoice! invoice-id)
      (ok)))

(defquery fetch-invoice
  {:description      "Fetch invoice from db"
   :permissions      [{:required [:invoice/read]}]
   :parameters       [id invoice-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])]
   :pre-checks       [invoices/invoicing-enabled
                      invoices/invoice-param-sanity-check]
   :states           states/post-submitted-states}
  [{:keys [application] :as command}]
  (->> (sc/validate Invoice (invoices/fetch-invoice invoice-id))
       (invoices/enrich-org-data command)
       (invoices/enrich-application-data application)
       (invoices/enrich-vat-info)
       (invoices/enrich-workdays command)
       (ok :invoice)))

(defquery application-invoices
  {:description      "Returns all invoices for an application"
   :permissions      [{:required [:invoice/read]}]
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [invoices/invoicing-enabled]
   :states           states/post-submitted-states}
  [{:keys [application] :as command}]
  (let [invoices (invoices/fetch-by-application-id (:id application))]
    (trace "application invoice id:s " (pr-str (map :id invoices)))
    (sc/validate [Invoice] invoices)
    (ok :invoices (map (util/fn->> invoices/enrich-vat-info
                                   (invoices/enrich-workdays command))
                       invoices))))

(defquery application-operations
  {:description      "Returns operations for application"
   :permissions      [{:required [:application/read]}]
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           (states/all-states-but :draft)}
  [{:keys [application] :as command}]
  (let [operations (invoices/get-operations-from-application application)]
    (sc/validate [Operation] operations)
    (ok :operations operations)))

(defquery invoices-tab
  {:description "Pseudo-query that fails if the invoices tab
  should not be shown on the UI."
   :permissions [{:required [:invoice/read]}]
   :parameters  [:id]
   :pre-checks  [invoices/invoicing-enabled]
   :states      states/post-submitted-states}
  [_])

(defquery user-organizations-invoices
  {:description         "Query that returns invoices for user's organizations."
   :permissions         [{:required [:invoice/read]}]
   :pre-checks          [invoices/invoicing-enabled]
   :optional-parameters [states from until limit organization-id]
   :input-validators    [invoices/validate-user-organization-invoices-request]}
  [{:keys [user] :as command}]
  (let [user-org-ids             (-> user
                                     (roles/organization-ids-by-roles #{:biller})
                                     invoices/invoicing-organization-ids
                                     set)
        search-in-orgs           (cond-> user-org-ids
                                   (ss/not-blank? organization-id)
                                   (set/intersection (set [(ss/trim organization-id)])))
        _                        (when (empty? search-in-orgs)
                                   (fail! :error.no-organizations))
        optional-filters         {:states states
                                  :from   (->long from)
                                  :until  (->long until)
                                  :limit  (->long limit)}
        invoices                 (invoices/fetch-invoices-for-organizations search-in-orgs optional-filters)
        applications             (domain/get-multiple-applications-no-access-checking (map :application-id invoices) [:address :documents])
        invoices-with-extra-data (->> invoices
                                      (map (partial invoices/enrich-org-data command))
                                      (map (partial invoices/enrich-application-data applications)))]

    (ok :invoices invoices-with-extra-data)))

;; ------------------------------------------
;; Price catalogues API
;; ------------------------------------------

(defquery organization-price-catalogues
  {:description      "Basic information (name, valid from - until, state) for organization price catalogues."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [invoices/invoicing-enabled]}
  [_]
  (->> (mongo/select :price-catalogues
                     {:organization-id organizationId}
                     [:valid-from :valid-until :state :name])
       (ok :price-catalogues)))

(defquery organization-price-catalogue
  {:description      "The wholde price catalogue with the given id."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId price-catalogue-id]
   :input-validators [(partial action/non-blank-parameters [:organizationId
                                                            :price-catalogue-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (price-catalogue-exists)]}
  [_]
  (ok :price-catalogue (catalogues/fetch-price-catalogue-by-id price-catalogue-id)))

(defcommand update-invoice-worktime
  {:description      "Updates worktime for the invoice."
   :permissions      [{:required [:invoice/edit]}]
   :pre-checks       [invoices/invoicing-enabled
                      (permit/validate-permit-type-is permit/YA)
                      invoices/invoice-param-sanity-check]
   :parameters       [id invoice-id start-ts end-ts]
   :input-validators [{:id         ssc/ApplicationId
                       :invoice-id ssc/NonBlankStr
                       :start-ts   ssc/Timestamp
                       :end-ts     ssc/Timestamp}]}
  [command]
  (if-let [days (invoices/update-worktime command)]
    (ok :workdays days)
    (fail :error.invalid-date)))

(defcommand move-price-catalogue-row
  {:description      "Command that moves given catalogue row and returns the updated price catalogue."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId]
   :input-validators [MoveRow]
   :pre-checks       [invoices/invoicing-enabled
                      (price-catalogue-exists)]}
  [command]
  (ok :updated-catalogue (catalogues/move-row command)))


(defquery organizations-transferbatches
  {:description "Query that returns transfer-batches for organization"
   :permissions [{:required [:invoice/transfer-batch]}]
   :parameters  [organizationId]
   :pre-checks  [invoices/invoicing-enabled]
   :optional-parameters  [states from until limit]
   :input-validators [(partial action/string-parameters [:organizationId])
                      tb/validate-organization-transferbatches-request]}
  [{:keys [user user-organizations] :as command}]
  (let [optional-filters         {:states states
                                  :from  (->long from)
                                  :until (->long until)
                                  :limit (->long limit)}
        transfer-batches-for-orgs (get-transfer-batch-for-orgs [organizationId] optional-filters)]
    (ok {:transfer-batches transfer-batches-for-orgs})))

(defcommand new-price-catalogue-draft
  {:description         "Create new price catalogue draft."
   :permissions         [{:required [:organization/admin]}]
   :parameters          [organizationId]
   :optional-parameters [price-catalogue-id]
   :input-validators    [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks          [invoices/invoicing-enabled
                         org-has-supported-permit-type
                         (price-catalogue-exists)]}
  [{:keys [user created lang] :as command}]
  (let  [base    (when price-catalogue-id
                   (mongo/select-one :price-catalogues
                                     {:_id             price-catalogue-id
                                      :organization-id organizationId}
                                     [:name :rows :valid-from :valid-until]))
         catalog (sc/validate
                   PriceCatalogueDraft
                   (util/strip-nils
                     {:id              (mongo/create-id)
                      :organization-id organizationId
                      :type            (catalogues/->catalogue-type (org/get-organization organizationId))
                      :state           "draft"
                      :valid-from      (:valid-from base)
                      :valid-until     (:valid-until base)
                      :name            (if base
                                         (i18n/localize-and-fill lang :price-catalogue.copy-name
                                                                 (:name base))
                                         (i18n/localize lang :auth-admin.price-catalogue.title))
                      :rows            (or (:rows base)
                                           [{:id               (mongo/create-id)
                                             :discount-percent 0}])
                      :meta            (catalogues/command->modified command)}))]
    (mongo/insert :price-catalogues catalog)
    (ok :draft catalog)))

(defcommand edit-price-catalogue-draft
  {:description         "Edit price catalogue draft. Returns updated draft."
   :permissions         [{:required [:organization/admin]}]
   :input-validators    [PriceCatalogueDraftEditParams
                         check-valid-period]
   :pre-checks          [invoices/invoicing-enabled
                         (price-catalogue-exists :draft?)]}
  [command]
  (ok :draft (catalogues/edit-price-catalogue-draft command)))

(defcommand delete-price-catalogue
  {:description         "A price catalogue can be deleted if it is either draft OR not yet valid."
   :permissions         [{:required [:organization/admin]}]
   :parameters          [organizationId price-catalogue-id]
   :input-validators    [(partial action/non-blank-parameters [:organizationId
                                                               :price-catalogue-id])]
   :pre-checks          [invoices/invoicing-enabled
                         (price-catalogue-exists)]}
  [command]
  (mongo/remove :price-catalogues price-catalogue-id)
  (ok))

(defcommand publish-price-catalogue
  {:description      "Publish an existing catalogue draft"
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId price-catalogue-id]
   :input-validators [(partial action/non-blank-parameters [:organizationId
                                                            :price-catalogue-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (price-catalogue-exists :draft?)]}
  [command]
  (let [promoted (catalogues/promote-price-catalogue-draft command)]
    (->> (select-keys promoted [:state :rows :meta])
         (hash-map $set)
         (mongo/update-by-id :price-catalogues price-catalogue-id))
    (ok :price-catalogue-id price-catalogue-id)))

(defcommand revert-price-catalogue-to-draft
  {:description      "Reverts a published catalogue to draft"
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId price-catalogue-id]
   :input-validators [(partial action/non-blank-parameters [:organizationId
                                                            :price-catalogue-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (price-catalogue-exists :published?)]}
  [{:keys [created user] :as command}]
  (mongo/update-by-id :price-catalogues price-catalogue-id
                      {$set {:meta  (catalogues/command->modified command)
                             :state "draft"}})
  (ok :price-catalogue-id price-catalogue-id))

(defquery application-price-catalogues
  {:description      "Published price catalogues for application organization. The catalogues
  are ordered first by validity (currently valids are first) and then by publication
  date (latest first). Thus, the first catalogue is a good candidate for an invoice price
  Catalogue."
   :permissions      [{:required [:invoice/read]}]
   :parameters       [id]
   :states           states/post-submitted-states
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [invoices/invoicing-enabled]}
  [{:keys [application created] :as command}]
  (ok :price-catalogues (catalogues/application-price-catalogues application created)))

(defraw invoice-excel
  {:description      "Print the given transfer-batch into an excel spreadsheet"
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [transfer-batch-id]
   :input-validators [(partial action/non-blank-parameters [:transfer-batch-id])]}
  [_]
  (invoices-excel/invoice-excel-conversion transfer-batch-id))

;; This is a temporary solution for getting the SAP IDOC XML directly to the browser as a file
;; The ultimate goal is to send the XML message to SAP automatically
(defraw get-sap-idoc-transfer-batch
  {:description      "Creates and returns a SAP IDOC xml for a transferbatch."
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [transfer-batch-id]
   :input-validators [(partial action/non-blank-parameters [:transfer-batch-id])]
   :pre-checks       [invoices/invoicing-enabled]}
  [{:keys [user organization] :as req}]
  (let [transfer-batch (mongo/by-id :invoice-transfer-batches transfer-batch-id)]
    (if (user-has-role-in-transfer-batch-org? user :biller transfer-batch)
      (do
        (let [xml (tb/generate-idoc-xml transfer-batch)
              filename "sap-idoc.xml"]
          {:status  200
           :body    xml
           :headers {"Content-Type"        "application/xml;charset=UTF-8"
                     "Content-Disposition" (str "attachment;filename=\"" filename "\"")
                     "Cache-Control"       "no-cache"}}))
      (fail :error.unauthorized) )))

(defcommand process-sap-idoc-transfer-batch-integration
  {:description      "Creates a SAP IDOC xml for a transferbatch and sends it to target SAP system"
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [transfer-batch-id]
   :input-validators [(partial action/non-blank-parameters [:transfer-batch-id])]
   :pre-checks       [invoices/invoicing-enabled]}
  [{:keys [user organization created] :as req}]
  (let [transfer-batch (mongo/by-id :invoice-transfer-batches transfer-batch-id)]
    (if (user-has-role-in-transfer-batch-org? user :biller transfer-batch)
      (try
        (tb/deliver-tampere-ya-transfer-batch-to-sap! transfer-batch user created)
        (ok :transfer-batch-id transfer-batch-id)
        (catch Exception e
          (do (error e (str "Exception when delivering message to SAP: " (.getMessage e)))
              (fail :error.invoice-delivery-failed))))
      (fail :error.unauthorized))))


(defcommand process-general-api-transfer-batch-integration
  {:description      "Creates an invoicing xml for a transferbatch and sends it to target system"
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [transfer-batch-id]
   :input-validators [(partial action/non-blank-parameters [:transfer-batch-id])]
   :pre-checks       [invoices/invoicing-enabled]}
  [{:keys [user] :as command}]
  (let [transfer-batch (mongo/by-id :invoice-transfer-batches transfer-batch-id)]
    (if (user-has-role-in-transfer-batch-org? user :biller transfer-batch)
      (try
        (tb/deliver-general-transfer-batch-to-invoicing-system! command transfer-batch)
        (ok :transfer-batch-id transfer-batch-id)
        (catch Exception e
          (error e (str "Exception when delivering message to invoicing system: " (.getMessage e)))
          (fail :error.invoice-delivery-failed)))
      (fail :error.unauthorized))))

(defraw download-transfer-batch-xml
  {:description      "Download general API transferbatch XML."
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [transfer-batch-id]
   :input-validators [(partial action/non-blank-parameters [:transfer-batch-id])]}
  [{:keys [user created] :as command}]
  (let [transfer-batch   (mongo/by-id :invoice-transfer-batches transfer-batch-id)
        invoicing-config (some-> transfer-batch :organization-id org/get-invoicing-config)]
    (try
      (cond
        (nil? transfer-batch)
        {:status 404} ; Not found

        (not (user-has-role-in-transfer-batch-org? user :biller transfer-batch))
        {:status 401} ; Unauthorized

        (not (:download? invoicing-config))
        {:status 403} ; Forbidden

        :else
        {:status  200 ; OK
         :headers {"Content-Type"        "text/xml"
                   "Content-Disposition" (format "attachment;filename=\"%s.xml\""
                                                 transfer-batch-id)}
         :body    (tb/generate-general-xml (util/deep-merge {:constants {:myyntiorg ""
                                                                         :jakelutie ""
                                                                         :sektori   ""}}
                                                            invoicing-config)
                                           transfer-batch
                                           created)})
      (catch Exception e
        (error e "XML download failed for transfer batch" (:id transfer-batch))
        (try-again-page command {:raw    :download-transfer-batch-xml
                                 :status 500 ;; Internal Server Error
                                 :error  :transfer-batch.download-xml.error})))))


(defcommand save-no-billing-periods
  {:description      "Command that saves no-billing periods"
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId price-catalogue-id no-billing-periods]
   :input-validators [(partial action/non-blank-parameters [:organizationId :price-catalogue-id])
                      (partial action/parameters-matching-schema
                               [:no-billing-periods]
                               {sc/Keyword NoBillingPeriod})
                      ;; No billing period sanity checks
                      (fn [{:keys [data]}]
                        (doseq [period (some->> data :no-billing-periods vals ss/trimwalk)]
                          (try
                            (catalogues/no-billing-period->date-time-interval period)
                            (catch Exception e
                              (fail! :error.price-catalogue.bad-no-billing-period
                                     :period period
                                    :message (ex-message e))))))]
   :pre-checks       [invoices/invoicing-enabled
                      (price-catalogue-exists :ya?)]}
  [command]
  (catalogues/save-no-billing-periods! command)
  (ok :updated-catalogue (catalogues/fetch-price-catalogue-by-id price-catalogue-id)))

(defraw invoice-pdf
  {:description      "PDF version of the invoice. Very similar to Pate verdict preview."
   :permissions      [{:required [:invoice/read]}]
   :org-authz-roles  roles/all-org-authz-roles
   :parameters       [id invoice-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])]
   :pre-checks       [invoices/invoicing-enabled
                      invoices/invoice-param-sanity-check]
   :states           states/post-submitted-states}
  [command]
  (let [{:keys [error pdf-file-stream
                filename]} (->> (invoices/fetch-invoice invoice-id)
                                invoices/enrich-vat-info
                                (pdf/create-invoice-pdf command))]
    (if error
      (try-again-page command {:raw    :invoice-pdf
                               :status 503 ;; Service Unavailable
                               :error  error})
      {:status  200
       :headers {"Content-Type"        "application/pdf"
                 "Content-Disposition" (format "filename=\"%s\"" filename)}
       :body    pdf-file-stream})))


;; ---------------------------
;; Backend ID configuration
;; ---------------------------

(defn- backend-id-enabled
  "Pre-check that fails if the invoicing backend-id is NOT enabled for the target
  organization."
  [command]
  (when-not (some-> (invoices/command->invoicing-configs  command)
                    :invoicing-config
                    :backend-id?)
    (fail :error.backend-id-not-enabled)))

(defn- invoice-exists
  "Invoice exists and is for the given application"
  [{{:keys [id invoice-id]} :data}]
  (when invoice-id
    (when-not (mongo/any? :invoices {:_id invoice-id :application-id id})
      (fail :error.not-found))))

(defn- backend-code-check
  "Backend code can be set if it is supported by the configuration and the backend-id has
  not already been set."
  [command]
  (let [{:keys [invoice-id code]} (:data command)]
    (when (and invoice-id (ss/not-blank? code))
      (let [{:keys [organization-id
                    backend-id]} (mongo/by-id :invoices invoice-id
                                              [:organization-id :backend-id])]
        ;; Since the code is part of backend-id and the id can be defined only once, it
        ;; does not make sense to allow the code change after the id has already been
        ;; defined.
        (when backend-id
          (fail! :error.backend-id-already-defined))

        (when-not (mongo/any? :organizations
                              {:_id                                    organization-id
                               :invoicing-backend-id-config.codes.code code})
          (fail :error.unknown-backend-code))))))

(sc/defschema BackendIdConfigOperation
  (sc/conditional
    :set {:set {:numbers (apply sc/enum (range 1 11))}}
    :upsert {:upsert {:code                 ssc/NonBlankStr
                      (sc/optional-key :id) ssc/ObjectIdStr
                      :text                 ssc/NonBlankStr}}
    :delete {:delete {:id ssc/ObjectIdStr}}))

(defcommand configure-invoicing-backend-id
  {:description      "Configure codes or numbers. Returns the updated config on success."
   :parameters       [organizationId op]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (partial action/parameters-matching-schema
                               [:op] BackendIdConfigOperation)]
   :pre-checks       [invoices/invoicing-enabled
                      backend-id-enabled]
   :permissions      [{:required [:organization/admin]}]}
  [command]
  (-<> (invoices/command->invoicing-configs command)
       :invoicing-backend-id-config
       (invoices/configure-backend-id organizationId <> (ss/trimwalk op)))
  (->> (invoices/command->invoicing-configs command)
       :invoicing-backend-id-config
       (ok :config)))

(defcommand set-invoice-backend-code
  {:description "The backend codes are defined in the backend-id configuration and used as
  a prefix for the backend-id. Blank code removes the field."
   :parameters       [id invoice-id code]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])]
   :pre-checks       [invoices/invoicing-enabled
                      backend-id-enabled
                      invoice-exists
                      backend-code-check]
   :permissions      [{:required [:invoice/edit]}]}
  [_]
  (mongo/update-by-id :invoices invoice-id
                      (if (ss/blank? code)
                        {$unset {:backend-code true}}
                        {$set {:backend-code code}}))
  (ok))

(defquery invoicing-backend-id-codes
  {:description      "Current invoicing backend id configuration for the application organization."
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [invoices/invoicing-enabled
                      backend-id-enabled]
   :permissions      [{:required [:invoice/read]}]}
  [{:keys [organization]}]
  (->> (force organization)
       :invoicing-backend-id-config
       :codes
       (map #(select-keys % [:code :text]))
       (ok :codes)))
