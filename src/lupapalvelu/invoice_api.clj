(ns lupapalvelu.invoice-api
  (:require [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [lupapalvelu.action :refer [defquery defcommand defraw notify] :as action]
            [lupapalvelu.invoices :refer [Invoice] :as invoices]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [sade.core :refer [ok fail]]
            [schema.core :as sc]
            [lupapalvelu.application-schema :refer [Operation]]))

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
        invoice-to-db (invoices/->invoice-db invoice-request application user)]
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
   :parameters       [org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]}
  [{:keys [data user user-organizations] :as command}]
  (let [price-catalogues (invoices/fetch-price-catalogues org-id)]
    (invoices/validate-price-catalogues price-catalogues)
    (ok {:price-catalogues price-catalogues})))
