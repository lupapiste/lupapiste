(ns lupapalvelu.prev-permit-api
  (:require [taoensso.timbre :refer [info]]
            [lupapalvelu.action :as action]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.application :as application]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :refer [defcommand defraw]]
            [lupapalvelu.prev-permit :as prev-permit]
            [noir.response :as resp]))

(defraw get-lp-id-from-previous-permit
  {:parameters [kuntalupatunnus]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus])]
   :user-roles #{:rest-api}}
  [{:keys [user] :as command}]
  (let [command (update-in command [:data] merge {:organizationId (first (:organizations user))})
        existing-app (domain/get-application-as {:state    {$ne "canceled"}
                                                 :verdicts {$elemMatch {:kuntalupatunnus kuntalupatunnus}}} user)]
    (if existing-app
      (resp/status 200 (str (merge (ok :id (:id existing-app))
                                   {:status :already-existing-application})))
      (resp/status 200 (str (merge (prev-permit/fetch-prev-application! command)
                                   {:status :created-new-application}))))))

(defcommand create-application-from-previous-permit
  {:parameters       [:lang :x :y :address :propertyId organizationId kuntalupatunnus]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:lang :organizationId]) ;; no :address included
                      ;; the propertyId parameter can be nil
                      (fn [{{propertyId :propertyId} :data :as command}]
                        (when (not (ss/blank? propertyId))
                          (application/property-id-parameters [:propertyId] command)))]}
  [{:keys [user] :as command}]
    ;; Prevent creating many applications based on the same kuntalupatunnus:
    ;; Check if we have in database an application of same organization that has a verdict with the given kuntalupatunnus.
    (if-let [app-with-verdict (domain/get-application-as
                                {:organization organizationId
                                 :state        {$ne "canceled"}
                                 :verdicts     {$elemMatch {:kuntalupatunnus kuntalupatunnus}}}
                                user)]
      ;;Found an application of same organization that has a verdict with the given kuntalupatunnus -> Open it.
      (ok :id (:id app-with-verdict))
      (prev-permit/fetch-prev-application! command)))
