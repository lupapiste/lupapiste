(ns lupapalvelu.asianhallinta-config-api
  "Configuration API for asianhallinta"
  (:require [sade.core :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.organization :as org]))


(defquery asianhallinta-config
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization-id (user/authority-admins-organization-id user)]
    (if-let [{:keys [scope]} (org/get-organization organization-id)]
      (ok :scope scope)
      (fail :error.unknown-organization))))

(defcommand save-asianhallinta-config
  {:parameters [permitType municipality enabled version]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization-id (user/authority-admins-organization-id user)]
    (mongo/update-by-query :organizations
       {:_id organization-id
        :scope {$elemMatch {:permitType permitType :municipality municipality}}}
       {$set {:scope.$.caseManagement.enabled enabled
              :scope.$.caseManagement.version version}})
    (ok)))
