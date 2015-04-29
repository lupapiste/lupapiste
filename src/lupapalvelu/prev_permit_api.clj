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
            [lupapalvelu.user :as user]
            [noir.response :as resp]))

(defraw get-lp-id-from-previous-permit
  {:parameters [kuntalupatunnus]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus])]
   :user-roles #{:rest-api}}
  [{:keys [user] :as command}]
  (let [organizations (user/organization-ids-by-roles user #{:authority})
        _             (assert (= 1 (count organizations)))
        command       (update-in command [:data] merge {:organizationId (first organizations)})
        existing-app  (domain/get-application-as {:state    {$ne "canceled"}
                                                  :verdicts {$elemMatch {:kuntalupatunnus kuntalupatunnus}}} user)
        result        (apply merge (if existing-app
                                     [(ok :id (:id existing-app)) {:text :already-existing-application}]
                                     [(prev-permit/fetch-prev-application! command) {:text :created-new-application}]))]
    (resp/json result)))

(defcommand create-application-from-previous-permit
  {:parameters       [:lang :x :y :address :propertyId organizationId kuntalupatunnus]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:lang :organizationId]) ;; no :address included
                      ;; the propertyId parameter can be nil
                      (fn [{{propertyId :propertyId} :data :as command}]
                        (when (not (ss/blank? propertyId))
                          (application/property-id-parameters [:propertyId] command)))]
   :pre-checks [(fn [{:keys [user data]} _]
                  (when-let [organization-id (:organizationId data)]
                    (when-not (user/user-is-authority-in-organization? user organization-id)
                      (info "Precheck FAILED!")
                      unauthorized)))]}
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
