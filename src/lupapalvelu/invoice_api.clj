(ns lupapalvelu.invoice-api
  (:require [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [lupapalvelu.action :refer [defquery defcommand defraw notify] :as action]
            [lupapalvelu.invoices :refer [Invoice] :as invoices]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [sade.core :refer [ok fail]]
            [schema.core :as sc]))

;; ------------------------------------------
;; Invoice API
;; ------------------------------------------

(defcommand insert-invoice
  {:description      "Inserts one invoice to db with state as draft"
   :feature          :invoices
   :user-roles       #{:authority}
   :org-authz-roles  roles/reader-org-authz-roles
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
   :org-authz-roles  roles/reader-org-authz-roles
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
