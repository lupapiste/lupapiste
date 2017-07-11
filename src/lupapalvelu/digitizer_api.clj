(ns lupapalvelu.digitizer-api
  (:require [lupapalvelu.digitizer :as d]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.user :as user]
            [lupapalvelu.action :as action :refer [defcommand defquery]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [monger.operators :refer :all]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.application :as app]
            [clojure.set :as set]
            [lupapalvelu.permit :as permit]))

(defn user-is-allowed-to-digitize [{user-orgs :user-organizations user :user {:keys [organizationId]} :data}]
  (let [archive-enabled? (some :permanent-archive-enabled (if organizationId
                                                            (filter #(= organizationId (:id %)) user-orgs)
                                                            user-orgs))
        roles (if organizationId
                ((keyword organizationId) (:orgAuthz user))
                (apply set/union (vals (:orgAuthz user))))
        correct-role? (seq (set/intersection #{:authority :digitizer} roles))]
    (when-not (and archive-enabled? correct-role?)
      unauthorized)))

(defcommand create-archiving-project
            {:parameters       [:lang :x :y :address :propertyId organizationId kuntalupatunnus createAnyway createWithoutBuildings]
             :user-roles       #{:authority}
             :input-validators [(partial action/non-blank-parameters [:lang :organizationId]) ;; no :address included
                                ;; the propertyId parameter can be nil
                                (fn [{{propertyId :propertyId} :data :as command}]
                                  (when (not (ss/blank? propertyId))
                                    (action/property-id-parameters [:propertyId] command)))]
             :pre-checks       [user-is-allowed-to-digitize]}
            [{:keys [user] :as command}]
  (if-let [app-with-verdict (domain/get-application-as
                              {:organization organizationId
                               :verdicts     {$elemMatch {:kuntalupatunnus kuntalupatunnus}}
                               :permitType :ARK}
                              user
                              :include-canceled-apps? false)]
    ;;Found an application of same organization that has a verdict with the given kuntalupatunnus -> Open it.
    (ok :id (:id app-with-verdict))
    (d/fetch-or-create-archiving-project! command)))

(defcommand submit-archiving-project
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :user-authz-roles roles/default-authz-writer-roles
   :states           #{:open}
   :pre-checks       [permit/is-archiving-project]}
  [{:keys [application created user] :as command}]
  (action/update-application command
                             {$set  {:state     :underReview
                                     :modified  created
                                     :opened    (:opened application)}
                              $push {:history (app/history-entry :underReview created user)}}))

(defquery user-is-pure-digitizer
  {:user-roles #{:authority}
   :pre-checks [(fn [{{org-authz :orgAuthz} :user}]
                  (let [all-roles (apply set/union (vals org-authz))]
                    (when (or (not (all-roles :digitizer))
                              (all-roles :authority))
                      unauthorized)))]}
  (ok))
