(ns lupapalvelu.financial-api
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.user :as usr]
            [lupapalvelu.financial :as financial]))

(defquery get-organizations-financial-handlers
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)]
    (financial/fetch-organization-financial-handlers org-id)))

(defcommand delete-financial-handle
  {:parameters       [email]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :user-roles       #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)]
    (financial/delete-organization-financial-handler email org-id)))