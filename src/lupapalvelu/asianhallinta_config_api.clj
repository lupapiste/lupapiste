(ns lupapalvelu.asianhallinta-config-api
  "Configuration API for asianhallinta"
  (:require [sade.core :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.organization :as org]
            [sade.util :as util]))


(defquery asianhallinta-config
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (if-let [{:keys [scope]} (util/find-by-id organizationId user-organizations)]
    (ok :scope scope)
    (fail :error.unknown-organization)))

(defn not-r-permit [{data :data}]
  (when (= "R" (:permitType data))
    (fail :error.invalid-permit-type)))

(defcommand save-asianhallinta-config
  {:parameters       [organizationId permitType municipality enabled version]
   :input-validators [(partial action/non-blank-parameters [:permitType :municipality])
                      (partial action/string-parameters [:version])
                      (partial action/boolean-parameters [:enabled])
                      not-r-permit]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (mongo/update-by-query :organizations
                         {:_id   organizationId
                          :scope {$elemMatch {:permitType permitType :municipality municipality}}}
                         {$set {:scope.$.caseManagement.enabled enabled
                                :scope.$.caseManagement.version version}})
  (ok))
