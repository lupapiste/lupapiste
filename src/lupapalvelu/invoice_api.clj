(ns lupapalvelu.invoice-api
  (:require [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [lupapalvelu.action :refer [defquery defcommand defraw notify] :as action]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]))

;; ------------------------------------------
;; Invoice API
;; ------------------------------------------

(defcommand insert-invoice
  {:description "A collection of prices. Item properties:
                       id:        catalogue id
                       prices:    list of prices"
   :feature          :invoices
   :user-roles       #{:authority}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [id invoice]
   :input-validators [(partial action/non-blank-parameters [:id])
                      invoices/validate-insert-invoice-request]
   :states           states/post-submitted-states}
  [{:keys [application data] :as command}]
  (let [invoice-request (:invoice data)
        invoice-to-db (invoices/->invoice-db invoice-request application)]

    (debug "insert-invoice invoice-request:" invoice-request)
    (debug "insert-invoice invoice-to-db:" invoice-to-db)

    (ok :invoice-id (invoices/create-invoice! (merge invoice-to-db
                                                     {:state "draft"})))))
