(ns lupapalvelu.asianhallinta-config-api
  "Configuration API for asianhallinta"
  (:require [sade.core :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]))


(defquery asianhallinta-config
  {:user-roles #{:authorityAdmin}}
  [{{:keys [organizations]} :user}]
  (let [organization-id (first organizations)]
    (if-let [{:keys [scope]} (org/get-organization organization-id)]
      (ok :scope scope)
      (fail :error.unknown-organization))))

(defcommand save-asianhallinta-config
  {:parameters [permitType municipality enabled version]
   :user-roles #{:authorityAdmin}}
  [{{:keys [organizations]} :user}]
  (mongo/update-by-query :organizations
      {:scope {$elemMatch {:permitType permitType :municipality municipality}}}
      {$set {:scope.$.caseManagement.enabled enabled
             :scope.$.caseManagement.version version}})
  (ok))
