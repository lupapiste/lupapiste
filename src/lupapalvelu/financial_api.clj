(ns lupapalvelu.financial-api
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.user :as usr]
            [lupapalvelu.financial :as financial]))

(defquery get-organizations-financial-handlers
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)]
    (financial/fetch-organization-financial-handlers org-id)))

(defcommand create-financial-handler
  {:parameters [:email]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :notified true
   :user-roles #{:admin :authorityAdmin}}
  [{user-data :data user :user}]
  (let [org-id (usr/authority-admins-organization-id user)]
    (financial/create-financial-handler user-data org-id user)))

(defcommand delete-financial-handler
  {:parameters       [email]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :user-roles       #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)]
    (financial/delete-organization-financial-handler email org-id)))