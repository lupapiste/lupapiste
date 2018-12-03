(ns lupapalvelu.invoice-api
  (:require [clj-time.coerce :as tc]
            [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [lupapalvelu.action :refer [defquery defcommand defraw notify] :as action]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.invoices.schemas :refer [->invoice-db Invoice]]
            [lupapalvelu.price-catalogues :as catalogues]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.time-util :refer [timestamp-day-before]]
            [sade.core :refer [ok fail]]
            [sade.util :refer [to-finnish-date]]
            [schema.core :as sc]
            [lupapalvelu.application-schema :refer [Operation]]
            [lupapalvelu.invoices.transfer-batch :refer [get-transfer-batch-for-orgs]]))

;; ------------------------------------------
;; Invoice API
;; ------------------------------------------

(defcommand insert-invoice
  {:description      "Inserts one invoice to db with state as draft"
   :feature          :invoices
   :user-roles       #{:authority}
   :org-authz-roles  roles/default-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [id invoice]
   :input-validators [(partial action/non-blank-parameters [:id])
                      invoices/validate-insert-invoice-request]
   :states           states/post-submitted-states}
  [{:keys [application data user] :as command}]
  (let [invoice-request (:invoice data)
        invoice-to-db (->invoice-db invoice-request application user)]
    (debug "insert-invoice invoice-request:" invoice-request)
    (ok :invoice-id (invoices/create-invoice! (merge invoice-to-db
                                                     {:state "draft"})))))

(defcommand update-invoice
  {:description      "Updates an existing invoice in the db"
   :feature          :invoices
   :user-roles       #{:authority}
   :org-authz-roles  roles/default-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [id invoice]
   :input-validators [(partial action/non-blank-parameters [:id])
                      ;;(partial action/non-blank-parameters [:invoice])
                      ]
   :states           states/post-submitted-states}
  [{:keys [data] :as command}]
  (do (debug "update-invoice invoice-request:" (:invoice data))
      (invoices/update-invoice! (:invoice data))
      (ok)))

(defquery fetch-invoice
  {:description      "Fetch invoice from db"
   :feature          :invoices
   :user-roles       #{:authority}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [id invoice-id]
   :input-validators [(partial action/non-blank-parameters [:id :invoice-id])]
   :states           states/post-submitted-states}
  [{:keys [application] :as command}]
  (let [invoice (invoices/fetch-invoice invoice-id)]
    (sc/validate Invoice invoice)
    (ok :invoice invoice)))

(defquery application-invoices
  {:description      "Returns all invoices for an application"
   :feature          :invoices
   :user-roles       #{:authority}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           states/post-submitted-states}
  [{:keys [application] :as command}]
  (debug "applicatin-invoices id: " (:id application))
  (let [invoices (invoices/fetch-by-application-id (:id application))]
    (sc/validate [Invoice] invoices)
    (ok :invoices invoices)))

(defquery application-operations
  {:description      "Returns operations for application"
   :feature          :invoices
   :user-roles       #{:authority}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           states/post-submitted-states}
  [{:keys [application] :as command}]
  (let [operations (invoices/fetch-application-operations (:id application))]
    (sc/validate [Operation] operations)
    (ok :operations operations)))

(defquery invoices-tab
  {:description      "Pseudo-query that fails if the invoices tab
  should not be shown on the UI."
   :feature          :invoices
   :parameters       [:id]
   :user-roles       #{:authority}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :states           states/post-submitted-states}
  [_])

(defquery user-organizations-invoices
  {:description "Query that returns invoices for users' organizations"
   :feature          :invoices
   :user-roles       #{:authority} ;;will be changed to laskuttaja role
   :parameters       []}
  [{:keys [user user-organizations] :as command}]
  (let [required-role-in-orgs "authority" ;;Will be changed to laskuttaja role
        user-org-ids (invoices/get-user-orgs-having-role user required-role-in-orgs)
        invoices (invoices/fetch-invoices-for-organizations user-org-ids)
        applications (invoices/fetch-application-data (map :application-id invoices) [:address])
        invoices-with-extra-data (->> invoices
                                      (map (partial invoices/enrich-org-data user-organizations))
                                      (map (partial invoices/enrich-application-data applications)))]
    (ok {:invoices invoices-with-extra-data})))

(defquery organization-price-catalogues
  {:description "Query that returns price catalogues for an organization"
   :permissions      [{:required [:organization/admin]}]
   :user-roles       #{:authority}
   :feature          :invoices
   :parameters       [organization-id]
   :input-validators [(partial action/non-blank-parameters [:organization-id])]}
  [{:keys [data user user-organizations] :as command}]
  (let [price-catalogues (catalogues/fetch-price-catalogues organization-id)]
    (catalogues/validate-price-catalogues price-catalogues)
    (ok {:price-catalogues price-catalogues})))

(defquery organizations-transferbatches
  {:description "Query that returns transferbatches for organization"
   :feature          :invoices
   :user-roles       #{:authority} ;;will be changed to laskuttaja role
   :parameters       []}
  [{:keys [user user-organizations] :as command}]
  (let [required-role-in-orgs "authority" ;;Will be changed to laskuttaja role
        user-org-ids (invoices/get-user-orgs-having-role user required-role-in-orgs)
        transfer-batches-for-orgs (get-transfer-batch-for-orgs user-org-ids)]
    (ok {:transfer-batches transfer-batches-for-orgs})))
    
(defcommand publish-price-catalogue

  {:description      "Insert a price catalogue to the db"
   :permissions      [{:required [:organization/admin]}]
   :user-roles       #{:authority}
   :feature          :invoices
   :parameters       [organization-id price-catalogue]
   :input-validators [(partial action/non-blank-parameters [:organization-id])
                      catalogues/validate-insert-price-catalogue-request]}
  [{:keys [user] :as command}]
  (let [catalogue-to-db (catalogues/->price-catalogue-db price-catalogue user organization-id)
        previous-catalogue (catalogues/fetch-previous-published-price-catalogue catalogue-to-db)
        same-day-catalogues (catalogues/fetch-same-day-published-price-catalogues catalogue-to-db)
        next-catalogue (catalogues/fetch-next-published-price-catalogue catalogue-to-db)
        valid-until (timestamp-day-before (:valid-from next-catalogue))]

    ;; (println "same-day catalogues: " (map (fn [{:keys [id valid-from]}]
    ;;                                         {:id id :valid-from valid-from :findate (to-finnish-date valid-from)})
    ;;                                       same-day-catalogues))

    ;; (info "previous catalogue: " {:id (:id previous-catalogue) :valid-from (:valid-from previous-catalogue) :findate (to-finnish-date (:valid-from previous-catalogue))})
    ;; (info "next-catalogue " next-catalogue ;;{:id (:id next-catalogue) :valid-from (:valid-from next-catalogue) :findate (to-finnish-date (:valid-from next-catalogue))}
    ;;       )
    (catalogues/update-previous-catalogue! previous-catalogue catalogue-to-db)

    (let [id (catalogues/create-price-catalogue! catalogue-to-db {:state "published" :valid-until valid-until})]
      (catalogues/delete-catalogues! same-day-catalogues)
      (ok {:price-catalogue-id id}))))
