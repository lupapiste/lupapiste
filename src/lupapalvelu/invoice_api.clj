(ns lupapalvelu.invoice-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defcommand defquery defraw] :as action]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices.core :as invoices]
            [lupapalvelu.invoices.integration.deliver :as deliver]
            [lupapalvelu.invoices.integration.excel-converter :as integration-excel]
            [lupapalvelu.invoices.integration.generate :as generate]
            [lupapalvelu.invoices.pdf :as pdf]
            [lupapalvelu.invoices.schemas
             :refer [AuthorityEditableInvoiceFields BillerEditableInvoiceFields EditableRowFields MoveRow
                     NoBillingPeriod PriceCatalogueDraftEditParams TransferBatchId]
             :as ischemas]
            [lupapalvelu.invoices.transfer-batch :as tb]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.logging :as log :refer [error]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pageutil :refer [try-again-page]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.price-catalogues :as catalogues]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.web :refer [defjson]]
            [monger.operators :refer :all]
            [noir.request :as request]
            [ring.util.codec :as codec]
            [rum.core :as rum]
            [sade.core :refer [fail fail! ok]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [swiss.arrows :refer :all]))

(set! *warn-on-reflection* true)

;; Pre-checkers

(defn invalid-org?
  "Org must have either R or YA, but not both"
  [org]
  (let [r           (org/has-permit-type? org :R)
        ya          (org/has-permit-type? org :YA)
        has-neither (and (not r) (not ya))
        has-both    (and r ya)]
    (or has-both has-neither)))

(defn org-has-supported-permit-type
  "Pre-checker that fails if org does not have R or YA permit type or has both"
  [{{:keys [organizationId]} :data}]
  (when-not (ss/blank? organizationId)
    (let [org (org/get-organization organizationId)]
      (when (invalid-org? org)
        (fail :error.price-catalogue.unsupported-permit-type)))))

(def ^:private check->error {:sap?        :error.integration.sap-not-supported
                             :sap-excel?  :error.integration.sap-excel-not-supported
                             :download?   :error.integration.download-not-supported
                             :general?    :error.integration.general-not-supported
                             :backend-id? :error.backend-id-not-enabled})

(defn invoicing-config-check
  "Pre-checker 'factory' for various configuration details. Fails if any check fails.
  Supported checks:

  `:sap?` SAP integration supported for the organization. The organizations are hard-coded.

  `:sap-excel?` SAP excel download is supported explicitly (hard-coded) for some
               organizations.

  `:download?` Integration message download supported. `:sap-excel?` and `download?`
               cannot be true at the same time.

  `:general?` General XML integration is configured.

  `:backend-id?` Backend ID supported."
  [& checks]
  {:pre [(every? #{:sap? :sap-excel? :download? :general? :backend-id?} checks)]}
  (fn [command]
    (let [cfg (or (invoices-util/resolve-invoicing-config command) {})]
      (some->> checks
               (remove cfg)
               first
               check->error
               fail))))

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

(defquery application-invoicing-configuration
  {:description      "One-stop query for all the configuration details needed in the application invoices tab."
   :permissions      [{:required [:invoice/read]}]
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [invoices/invoicing-enabled]}
  [{:keys [application organization created]}]
  (ok :catalogues (->> (catalogues/application-price-catalogues application created)
                       (mapv (fn [catalogues]
                               (-> catalogues
                                   (select-keys [:id :name :valid-from :valid-until :rows])
                                   (update :rows (fn [rows]
                                                   (mapv #(select-keys % [:id :code :text]) rows)))))))
      :worktime? (= (:permitType application) "YA")
      :backend-codes (invoices-util/backend-codes organization)
      :operations (mapv #(select-keys % [:id :name :description])
                        (app-utils/get-operations application))))

(defcommand new-invoice-draft
  {:description      "Returns freshly made (and stored) invoice."
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [invoices/invoicing-enabled]
   :states           states/all-but-draft}
  [command]
  (->>  (invoices/create-invoice command)
        (invoices/enrich-workdays command)
        (ok :invoice)))

(defn operation-check
  "`:out` operation MUST be in the application and MUST NOT already be in the invoice.
  `:in` operation MUST be in the invoice."
  [op]
  {:pre [(#{:in :out} op)]}
  (let [out? (= op :out)]
    (fn [{:keys [application data]}]
      (let [{:keys [operation-id
                    invoice-id]} data
            any*?                (delay (mongo/any? :invoices
                                                    {:_id            invoice-id
                                                     :application-id (:id application)
                                                     :operations.id  operation-id}))]
        (when operation-id
          (cond
            (and out? (not (util/find-by-id operation-id (app-utils/get-operations application))))
            (fail :error.operation-not-found)

            (and out? @any*?)
            (fail :error.invalid-operation)

            (and (not out?) (not @any*?))
            (fail :error.operation-not-found)))))))

(defcommand add-invoice-operation
  {:description      "Adds an application operation to the invoice. Returns operation, its
  default rows and modified(-by)."
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice-id operation-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id :operation-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check :draft)
                      (operation-check :out)]
   :states           states/all-but-draft}
  [command]
  (ok (invoices/add-operation command operation-id)))

(defcommand delete-invoice-operation
  {:description      "Deletes the invoice operation and its rows. Removing non-existing
  operation is allowed/ignored. Returns modified(-by), if changes."
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice-id operation-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id :operation-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check :draft)]
   :states           states/all-but-draft}
  [command]
  (ok (invoices/delete-operation command operation-id)))

(defcommand add-invoice-row
  {:description      "Adds either a custom or catalogue row to the invoice. If the `row-id` is
  'custom' then a custom row is added, otherwise `row-id` refers to the invoice price
  catalogue row id. Returns row and modified(-by)."
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice-id operation-id row-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id :operation-id :row-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check :draft)
                      (operation-check :in)]
   :states           states/all-but-draft}
  [command]
  (ok (invoices/add-row command invoice-id operation-id row-id)))

(defcommand delete-invoice-row
  {:description      "Deletes the row if it exists. Returns modified(-by), if changes."
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice-id row-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id :row-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check :draft)]
   :states           states/all-but-draft}
  [command]
  (ok (invoices/delete-row command row-id)))

(defn set-field-input-validator [schema]
  (fn [{data :data}]
    (when-let [{:keys [field path value]} (not-empty data)]
      (let [path         (map keyword (or path [field]))
            field-schema (st/get-in schema path)]
        (when (or (nil? field-schema)
                  (sc/check field-schema value))
          (fail :error.unsupported-field-or-value))))))

(defcommand set-invoice-row-field
  {:description      "Edits an invoice row.

  `:in` Changes to the top-level invoice data (typically modified and modified-by)

  `:row-in` Map of changes to the row data (at least modified and modified-by).

  `:row-out` List of removed row data keys

  `:row-errors` Row-specific validation errors"
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice-id row-id path value]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id :row-id])
                      (set-field-input-validator EditableRowFields)]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check :draft)]
   :states           states/all-but-draft}
  [command]
  (ok (invoices/set-row-field command
                              row-id
                              (map keyword path)
                              (ss/trimwalk value))))

(def BillerEditableFieldsSchema (-> BillerEditableInvoiceFields
                                    ;; Blank string is supported in params but not in invoice
                                    (st/assoc :backend-code sc/Str)
                                    st/optional-keys))

(defcommand set-biller-invoice-field
  {:description      "Update a general invoice field. Does not touch the actual operation
  rows. Success response can have the following keys:

  `:in` Map of changes to the invoice data (at least modified and modified-by). Typically
        the target field is not included.

  `:out` List of removed invoice data keys

  `:errors` Validation errors"
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [id invoice-id field value]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])
                      (set-field-input-validator BillerEditableFieldsSchema)]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check :checked :confirmed :transferred)]
   :states           states/all-but-draft}
  [command]
  (ok (invoices/set-invoice-field command)))

(defcommand set-invoice-field
  {:description      "A more limited version of the `set-biller-invoice-field` command."
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice-id field value]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])
                      (set-field-input-validator AuthorityEditableInvoiceFields)]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check :draft :checked)]
   :states           states/all-but-draft}
  [command]
  (ok (invoices/set-invoice-field command false)))

(defcommand delete-invoice
  {:description      "Delete invoice from db. Deletes only invoices in draft state"
   :permissions      [{:required [:invoice/edit]}]
   :parameters       [id invoice-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check :draft)]
   :states           states/all-but-draft}
  [{:keys [data] :as command}]
  (invoices/delete-invoice! invoice-id)
  (ok))

(defquery application-invoices
  {:description         "Returns either all application invoices (default) or the one explicitly
  requested."
   :permissions         [{:required [:invoice/read]}]
   :parameters          [id]
   :optional-parameters [invoice-id]
   :input-validators    [(partial action/non-blank-parameters [:id])]
   :pre-checks          [invoices/invoicing-enabled]
   :states              states/all-but-draft}
  [{:keys [application] :as command}]
  (->>  (util/assoc-when {:application-id id}
                         :_id (ss/blank-as-nil invoice-id))
        (mongo/select :invoices)
        ischemas/valid-invoices!
        (mapv (fn [{:keys [state] :as invoice}]
                (cond-> (->> invoice
                             invoices-util/clean-invoice
                             (invoices/enrich-workdays command))
                  (util/not=as-kw state :draft) invoices/enrich-vat-info)))
        (ok :invoices)))

(defquery invoices-tab
  {:description "Pseudo-query that fails if the invoices tab
  should not be shown on the UI."
   :permissions [{:required [:invoice/read]}]
   :parameters  [:id]
   :pre-checks  [invoices/invoicing-enabled]
   :states      states/all-but-draft}
  [_]
  (ok))

(defn- biller-invoicing-org-ids [user]
  (some-> user
          (roles/organization-ids-by-roles #{:biller})
          invoices/invoicing-organization-ids
          set))

(defquery biller-organizations
  {:description "Organization ids, names and relevant invoicing configuration for every
  organization where the user has `:biller` authz. Fails if no suitable organizations
  found."
   :user-roles  #{:authority}
   :pre-checks  [(fn [command]
                   (when-not (biller-invoicing-org-ids (:user command))
                     (fail :error.no-organizations)))]}
  [{:keys [user]}]
  (let [org-ids (biller-invoicing-org-ids user)]
    (->> (mongo/select :organizations
                       {:_id {$in org-ids}}
                       [:name :invoicing-config :invoicing-backend-id-config.codes])
         (mapv (fn [{:keys [id name] :as organization}]
                 (let [cfg (invoices-util/resolve-invoicing-config {:organization organization})]
                   (util/assoc-when (select-keys cfg [:general? :sap? :sap-excel? :download?])
                                    :id                      id
                                    :name                    name
                                    :backend-codes           (invoices-util/backend-codes organization)
                                    :client-number-required? (:integration-requires-customer-number? cfg)
                                    :max-rowcount            (:max-invoice-row-count-in-transfer-batch cfg)))))
         (ok :organizations))))

(def biller-invoice-state
  "Draft state is excluded since those are not visible to the biller."
  (sc/enum "checked" "confirmed" "transferred"))

(sc/defschema BaseParams
  {:organizationId            org/OrgId
   (sc/optional-key :include) [sc/Str]
   (sc/optional-key :exclude) [sc/Str]})

(defcommand biller-invoices
  {:description          "Command for querying organization (enriched) invoices. The `include` /
  `exclude` parameters can define which invoice ids MUST (if also other criteria matches)
  and which MUST NOT be included in the results. If the both are given `include` is
  used. The idea is to support very reliable paging without resorting to any arbitrary
  timestamp limits. Since the list of ids can be quite long, it makes sense to use
  command (POST) instead of query (GET). The invoices are ordered descending by the
  created timestamp.

  If `exclude` is given the response can contain `:draft-ids` keys, which lists ids of
  those invoices that are 'excluded' and whose state is draft. The rationale/use case is
  to let the biller know if an invoice state is changed outside of the biller. In other
  words, an authority (invoice creator) may have reverted a checked invoice back to draft."
   :permissions          [{:required [:invoice/read]}]
   :pre-checks           [invoices/invoicing-enabled]
   :parameters           [organizationId]
   :optional-parameters  [states limit batch-ids include exclude]
   :allow-impersonation? true
   :input-validators     [(sc/conditional
                            :states (st/merge BaseParams
                                              {:states                  [biller-invoice-state]
                                               (sc/optional-key :limit) ssc/Nat})
                            :batch-ids (st/assoc BaseParams
                                                 :batch-ids [sc/Str]))]}
  [_]
  (let [query     (util/strip-nils {:organization-id   organizationId
                                    :state             (some->> states seq (hash-map $in))
                                    :_id               (cond
                                                         (seq include) {$in include}
                                                         (seq exclude) {$nin exclude})
                                    :transfer-batch-id (some->> batch-ids seq (hash-map $in))})
        invoices  (mongo/select-ordered :invoices query {:created -1} (or limit 0))
        draft-ids (when (seq exclude)
                    (->> (mongo/select :invoices
                                       {:organization-id organizationId
                                        :_id             {$in exclude}
                                        :state           :draft}
                                       [:id])
                         (mapv :id)
                         not-empty))]
    (ok (util/assoc-when {:invoices (mapv (comp invoices/enrich-sum-total
                                                invoices-util/clean-invoice)
                                          (invoices-util/enrich-invoices invoices))}
                         :draft-ids draft-ids))))

(defjson [:post "/api/biller-poller"] []
  "Polling has its own specific JSON endpoint. The main reason is to make sure that the
  polling does not prevent the session from expiring since `defjson` implementation does
  not refresh/return cookies.

  The polling actually just checks whether an actual `billing-invoices` query is
  needed. The use case is that an application authority ('invoicer') can change an invoice
  state between draft and checked and those changes should eventually be reflected in the
  biller UI.

  Params:

  `organization-id` Organization id. The current user MUST have biller authz for the
                    organization.

  `invoice-ids` Ids for the invoices that the caller (biller UI) assumes to be in the
                checked state.

  The return body:

  `:ok` If false, the the polling failed. The frontend should stop polling.

  `:new-ids` New (not in `invoice-ids`) checked invoice ids.

  `:gone-ids` Ids that no longer are in the checked state. The underlying assumption is
              that the invoice has been reverted to the draft state. Of course, if the
              organization has multiple billers, the invoice may have been moved to
              confirmed but that is unlikely."
  (let [{:keys [params] :as req} (request/ring-request)
        {:keys [organization-id
                invoice-ids]}    params
        user                     (usr/current-user req)]
    (if (usr/user-has-role-in-organization? user organization-id #{:biller})
      (let [old-ids     (set invoice-ids)
            checked-ids (->> (mongo/select :invoices
                                           {:organization-id organization-id
                                            :state           :checked}
                                           [:id])
                             (map :id)
                             set)]
        (ok :new-ids (set/difference checked-ids old-ids)
            :gone-ids (set/difference old-ids checked-ids)))
      (fail :error.unauthorized))))

(defcommand biller-transfer-batches
  {:description          "Command for querying organization transfer-batches. The `include` /
  `exclude` parameters can define which batch ids MUST (if also other criteria matches) and
  which MUST NOT be included in the results. If the both are given `include` is used. The
  idea is to support very reliable paging without resorting to any arbitrary timestamp
  limits. Since the list of ids can be quite long, it makes sense to use command (POST)
  instead of query (GET). The batches are ordered descending by the created timestamp."
   :permissions          [{:required [:invoice/read]}]
   :pre-checks           [invoices/invoicing-enabled]
   :parameters           [organizationId]
   :optional-parameters  [states limit include exclude]
   :allow-impersonation? true
   :input-validators     [(merge BaseParams
                                 {(sc/optional-key :states) [(sc/enum "open" "closed")]
                                  (sc/optional-key :limit)  ssc/Nat})]}
  [_]
  (let [query (util/strip-nils {:organization-id organizationId
                                :state           (some->> states seq (hash-map $in))
                                :_id             (cond
                                                   (seq include) {$in include}
                                                   (seq exclude) {$nin exclude})})]
    (->> (mongo/select-ordered :invoice-transfer-batches
                               query
                               {:created -1}
                               (or limit 0))
         (mapv #(select-keys % [:id :state :organization-id
                                :created :closed :name :sent]))
         (ok :transfer-batches))))

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
       (into [])
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

(defn command->worktime [{{:keys [start-ts end-ts]} :data}]
  (when (and start-ts end-ts)
    [(-> start-ts date/start-of-day date/timestamp)
     (-> end-ts date/end-of-day date/timestamp)]))

(defn valid-worktime [command]
  (when-let [[start-ts end-ts] (command->worktime command)]
    (when-not (< start-ts end-ts)
      (fail :error.startdate-before-enddate))))

(defcommand update-invoice-worktime
  {:description      "Updates worktime for the invoice. Returns workdays structure and
  modified(-by). Fails for bad dates. If no dates are given, the fields are cleared from
  the invoice and the return value is the default (calculated from the application)
  workdays result."
   :permissions      [{:required [:invoice/edit]}]
   :pre-checks       [invoices/invoicing-enabled
                      (permit/validate-permit-type-is permit/YA)
                      (invoices/invoice-check)]
   :parameters       [id invoice-id]
   :input-validators [{:id                         ssc/ApplicationId
                       :invoice-id                 ssc/NonBlankStr
                       (sc/optional-key :start-ts) ssc/Timestamp
                       (sc/optional-key :end-ts)   ssc/Timestamp}
                      valid-worktime]}
  [command]
  (ok (apply invoices/update-worktime command (or (command->worktime command)
                                                  []))))

(defcommand move-price-catalogue-row
  {:description      "Command that moves given catalogue row and returns the updated price catalogue."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId]
   :input-validators [MoveRow]
   :pre-checks       [invoices/invoicing-enabled
                      (price-catalogue-exists)]}
  [command]
  (ok :updated-catalogue (catalogues/move-row command)))

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
         catalog (ischemas/valid-catalogue-draft!
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
  {:description      "Edit price catalogue draft. Returns updated draft."
   :permissions      [{:required [:organization/admin]}]
   :input-validators [PriceCatalogueDraftEditParams
                      check-valid-period]
   :pre-checks       [invoices/invoicing-enabled
                      (price-catalogue-exists :draft?)]}
  [command]
  (ok :draft (catalogues/edit-price-catalogue-draft command)))

(defcommand delete-price-catalogue
  {:description      "A price catalogue can be deleted if it is either draft OR not yet valid."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId price-catalogue-id]
   :input-validators [(partial action/non-blank-parameters [:organizationId
                                                            :price-catalogue-id])]
   :pre-checks       [invoices/invoicing-enabled
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
   :states           states/all-but-draft
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [invoices/invoicing-enabled]}
  [{:keys [application created] :as command}]
  (ok :price-catalogues (catalogues/application-price-catalogues application created)))

;; ------------------------------------------
;; Transfer batches
;; ------------------------------------------

(defcommand new-transfer-batch
  {:description      "Create new empty transfer batch. Returns the transfer batch id."
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [organizationId]
   :input-validators [{:organizationId ssc/NonBlankStr}]
   :pre-checks       [invoices/invoicing-enabled]}
  [command]
  (ok :transfer-batch-id (tb/insert-new-transfer-batch command)))

(defcommand delete-transfer-batch
  {:description      "Delete an empty transfer batch."
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [organizationId transfer-batch-id]
   :input-validators [{:organizationId    ssc/NonBlankStr
                       :transfer-batch-id TransferBatchId}]
   :pre-checks       [invoices/invoicing-enabled
                      (tb/transfer-batch-check :empty)]}
  [_]
  (mongo/remove :invoice-transfer-batches transfer-batch-id)
  (ok))

(defcommand rename-transfer-batch
  {:description         "Give name to a transfer batch."
   :permissions         [{:required [:invoice/transfer-batch]}]
   :parameters          [organizationId transfer-batch-id]
   :optional-parameters [name]
   :input-validators    [{:organizationId         ssc/NonBlankStr
                          :transfer-batch-id      TransferBatchId
                          (sc/optional-key :name) sc/Str}]
   :pre-checks          [invoices/invoicing-enabled
                         (tb/transfer-batch-check)]}
  [_]
  (mongo/update-by-id :invoice-transfer-batches transfer-batch-id
                      (if-let [txt (some-> name ss/trim ss/blank-as-nil)]
                        {$set {:name txt}}
                        {$unset {:name true}}))
  (ok))

(defcommand close-transfer-batch
  {:description      "Close transfer batch and mark its invoices transferred."
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [organizationId transfer-batch-id]
   :input-validators [{:organizationId    ssc/NonBlankStr
                       :transfer-batch-id TransferBatchId}]
   :pre-checks       [invoices/invoicing-enabled
                      (tb/transfer-batch-check :open :not-empty)]}
  [command]
  (tb/set-transfer-batch-state command :closed)
  (ok :closed (:created command)))

(defcommand open-transfer-batch
  {:description      "Open transfer batch and mark its invoices confirmed."
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [organizationId transfer-batch-id]
   :input-validators [{:organizationId    ssc/NonBlankStr
                       :transfer-batch-id TransferBatchId}]
   :pre-checks       [invoices/invoicing-enabled
                      (tb/transfer-batch-check :closed)]}
  [command]
  (tb/set-transfer-batch-state command :open)
  (ok :opened (:created command)))

(defraw download-transfer-batch-excel
  {:description         "Generate SAP-friendly excel file for the given transfer batch."
   :permissions         [{:required [:invoice/transfer-batch]}]
   :parameters          [organizationId transfer-batch-id]
   :optional-parameters [lang]
   :pre-checks          [invoices/invoicing-enabled
                         (tb/transfer-batch-check :not-empty)
                         (invoicing-config-check :sap-excel?)]
   :input-validators    [{:organizationId         ssc/NonBlankStr
                          :transfer-batch-id      TransferBatchId
                          (sc/optional-key :lang) i18n/Lang}]}
  [command]
  (integration-excel/transfer-batch-excel lang (tb/command->transfer-batch command)))

(defcommand process-transfer-batch-integration
  {:description      "Creates an invoicing xml for a transfer batch and sends it to target system"
   :permissions      [{:required [:invoice/transfer-batch]}]
   :parameters       [organizationId transfer-batch-id]
   :input-validators [{:organizationId    ssc/NonBlankStr
                       :transfer-batch-id TransferBatchId}]
   :pre-checks       [invoices/invoicing-enabled
                      (tb/transfer-batch-check :not-empty)
                      (invoicing-config-check :general?)]}
  [{:keys [created] :as command}]
  (let [cfg                 (-> command
                                invoices-util/resolve-invoicing-config
                                invoices-util/validate-config-credentials!)
        batch               (tb/command->transfer-batch command)
        invoices            (invoices-util/transfer-batch-invoices batch)
        transfer-batch-file (generate/generate-invoice-transfer-batch
                              (util/mmap cfg created invoices))]
    (if-let [error (:error transfer-batch-file)]
      (do
        (log/warnf "Failed to generate transfer batch, error: %s" error)
        (fail :error.transfer-batch.could-not-process))
      (do
        (deliver/deliver-transfer-batch-to-invoicing-system! command
                                                             cfg
                                                             organizationId
                                                             batch
                                                             invoices
                                                             transfer-batch-file)
        (ok :sent (:created command))))))

(defn- error-page
  "Display a HTML error page describing the problem"
  [{:keys [lang data]} {:keys [raw status error error-data]}]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (let [msg        (i18n/localize lang error)
                  cell-style {:style {:padding "5px"}}]
              (rum/render-static-markup
                [:html
                 [:head [:title msg]]
                 [:body
                  [:div
                   {:style {:margin  "2em 2em"
                            :border  "2px solid red"
                            :padding "1em 1em"}}
                   [:h3 {:style {:margin-top 0}} msg]
                   [:table
                    [:thead
                     [:tr
                      [:td cell-style [:strong "Sijainti"]]
                      [:td cell-style [:strong "Virhe"]]
                      [:td cell-style [:strong "Arvo"]]]]
                    (->> error-data
                         (map (fn [{:keys [_error _field value _expected loc]}]
                                [:tr
                                 [:td cell-style (:location loc)]
                                 [:td cell-style (:error loc)]
                                 [:td cell-style (when (or (string? value) (number? value))
                                                   (str value))]]))
                         (into [:tbody]))]
                   [:a {:href (format "/api/raw/%s?%s"
                                      (name raw)
                                      (codec/form-encode data))}
                    (i18n/localize lang :pate.try-again)]]]]))})

(defraw download-transfer-batch-xml
  {:description         "Download transfer batch as a file. Depending on the organization, the format is
  either one of the SAP variants or general API."
   :permissions         [{:required [:invoice/transfer-batch]}]
   :parameters          [organizationId transfer-batch-id]
   :optional-parameters [lang]
   :input-validators    [{:organizationId         ssc/NonBlankStr
                          :transfer-batch-id      TransferBatchId
                          (sc/optional-key :lang) i18n/Lang}]
   :pre-checks          [invoices/invoicing-enabled
                         (tb/transfer-batch-check :not-empty)
                         (invoicing-config-check :download?)]}
  [{:keys [user created] :as command}]
  (let [batch    (tb/command->transfer-batch command)
        invoices (invoices-util/transfer-batch-invoices batch)
        cfg      (invoices-util/resolve-invoicing-config command)]
    (try
      (let [{:keys [error
                    content
                    filename
                    content-type]} (generate/generate-invoice-transfer-batch
                                     (-> {:pretty-print? true
                                          :cfg           (util/deep-merge
                                                           {:constants {:myyntiorg ""
                                                                        :jakelutie ""
                                                                        :sektori   ""}}
                                                           cfg)}
                                         (util/mmap created invoices lang)
                                         util/strip-nils))]
        (if error
          (error-page command {:raw        :download-transfer-batch-xml
                               :status     400
                               :error      :transfer-batch.download-xml.error
                               :error-data error})
          {:status  200
           :headers {"Content-Type"        content-type
                     "Content-Disposition" (format "attachment;filename=\"%s\"" filename)}
           :body    content}))
      (catch Exception e
        (error e "XML download failed for transfer batch" transfer-batch-id)
        (try-again-page command {:raw    :download-transfer-batch-xml
                                 :status 500                ;; Internal Server Error
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
                            (catalogues/no-billing-period->dates period)
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
  {:description      "PDF version of the invoice."
   :permissions      [{:required [:invoice/read]}]
   :parameters       [id invoice-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])]
   :pre-checks       [invoices/invoicing-enabled
                      (invoices/invoice-check)]
   :states           states/all-but-draft}
  [{:keys [application] :as command}]
  (let [{:keys [error pdf-file-stream
                filename]} (-<>> (invoices/command->invoice command)
                                 (invoices-util/enrich-invoice <> application)
                                 invoices/enrich-sum-total
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
                      (invoicing-config-check :backend-id?)]
   :permissions      [{:required [:organization/admin]}]}
  [command]
  (-<> (invoices/command->invoicing-configs command)
       :invoicing-backend-id-config
       (invoices/configure-backend-id organizationId <> (ss/trimwalk op)))
  (->> (invoices/command->invoicing-configs command)
       :invoicing-backend-id-config
       (ok :config)))

(defquery invoicing-backend-id-codes
  {:description      "Current invoicing backend id configuration for the application organization."
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [invoices/invoicing-enabled
                      (invoicing-config-check :backend-id?)]
   :permissions      [{:required [:invoice/read]}]}
  [{:keys [organization]}]
  (ok :codes (invoices-util/backend-codes organization)))
