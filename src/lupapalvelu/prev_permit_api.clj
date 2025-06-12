(ns lupapalvelu.prev-permit-api
  (:require [lupapalvelu.action :refer [defcommand defraw] :as action]
            [lupapalvelu.db :as db]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.ryhti.permanent-id :as ryhti-id]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]))

(defraw get-lp-id-from-previous-permit
  {:parameters       [kuntalupatunnus authorizeApplicants]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus])]
   :user-roles       #{:rest-api}}
  [{:keys [user] :as command}]
  (let [organizations (usr/organization-ids-by-roles user #{:authority})
        _             (assert (= 1 (count organizations)))
        command       (update-in command [:data] merge {:organizationId (first organizations)})
        [old-app-id]  (db/application-ids-by-kuntalupatunnus (seq organizations)
                                                             kuntalupatunnus
                                                             {:state {$ne :canceled}}
                                                             {:limit 1})]
    (if old-app-id
      (ok :id old-app-id :text :already-existing-application)
      (let [result (prev-permit/fetch-prev-application! command)]
        (if (ok? result)
          (ok :id (:id result) :text :created-new-application)
          (select-keys result [:ok :text]))))))

(defcommand create-application-from-previous-permit
  {:parameters       [:lang :x :y :address :propertyId organizationId kuntalupatunnus authorizeApplicants]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:lang :organizationId]) ;; no :address included
                      ;; the propertyId parameter can be nil
                      (fn [{{propertyId :propertyId} :data :as command}]
                        (when (not (ss/blank? propertyId))
                          (action/property-id-parameters [:propertyId] command)))]
   :pre-checks       [(fn [{:keys [user data]}]
                        (when-let [organization-id (:organizationId data)]
                          (when-not (usr/user-is-authority-in-organization? user organization-id)
                            unauthorized)))]
   :on-success       [ryhti-id/trigger-ryhti-id-fetching]}
  [{:keys [user] :as command}]
  ;; Prevent creating many applications based on the same kuntalupatunnus:
  ;; Check if we have in database an application of same organization that has a verdict
  ;; with the given kuntalupatunnus.
  (if-let [[old-app-id] (db/application-ids-by-kuntalupatunnus organizationId
                                                               kuntalupatunnus
                                                               {:permitType :R
                                                                :state      {$ne :canceled}}
                                                               {:limit 1})]
    ;;Found an application of same organization that has a verdict with the given kuntalupatunnus -> Open it.
    (ok :id old-app-id)
    (prev-permit/fetch-prev-application! command)))
