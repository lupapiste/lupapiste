(ns lupapalvelu.digitizer-api
  (:require [lupapalvelu.digitizer :as d]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.user :as user]
            [lupapalvelu.action :as action :refer [defcommand]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [monger.operators :refer :all]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.application :as app]
            [clojure.set :as set]))

(defn user-is-allowed-to-digitize [{user-orgs :user-organizations user :user {:keys [organizationId]} :data}]
  (let [archive-enabled? (some :permanent-archive-enabled (filter #(= organizationId (:id %)) user-orgs))
        roles ((keyword organizationId) (:orgAuthz user))
        correct-role? (seq (set/intersection #{:authority :digitizer} roles))]
    (when-not (and archive-enabled? correct-role?)
      unauthorized)))

(defn archiving-project-permit-type [{{:keys [permitType]} :application}]
  (when-not (= permitType "ARK")
    unauthorized))

(defcommand create-archiving-project
            {:parameters       [:lang :x :y :address :propertyId organizationId kuntalupatunnus createAnyway]
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
   :pre-checks       [archiving-project-permit-type]}
  [{:keys [application created user] :as command}]
  (action/update-application command
                             {$set  {:state     :underReview
                                     :modified  created
                                     :opened    (:opened application)}
                              $push {:history (app/history-entry :underReview created user)}}))
