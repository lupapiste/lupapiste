(ns lupapalvelu.digitizer-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.digitizer :as d]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn user-is-allowed-to-digitize [{user-orgs :user-organizations user :user {:keys [organizationId]} :data}]
  (let [archive-enabled? (some #(and (:permanent-archive-enabled %) (:digitizer-tools-enabled %))
                               (if organizationId
                                 (filter #(= organizationId (:id %)) user-orgs)
                                 user-orgs))
        roles (if organizationId
                ((keyword organizationId) (:orgAuthz user))
                (apply set/union (vals (:orgAuthz user))))
        correct-role? (seq (set/intersection #{:digitizer :archivist :authorityAdmin} roles))]
    (when-not (and archive-enabled? correct-role?)
      unauthorized)))

(defn default-digitalization-location-set-if-used
  "Verifies that default digitalization location can only be used if
  it is set for the organization and is valid."
  [{user-orgs :user-organizations {:keys [organizationId createWithDefaultLocation]} :data}]
  (when organizationId
    (when createWithDefaultLocation
      (if-let [{:keys [x y]} (->> user-orgs (util/find-by-id organizationId) :default-digitalization-location)]
        (coord/validate-coordinates [x y])
        (fail :error.no-default-digitalization-location)))))

(defcommand create-archiving-project
  {:parameters       [:lang :x :y :address :propertyId organizationId kuntalupatunnus
                      createWithoutPreviousPermit createWithoutBuildings createWithDefaultLocation]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:lang :organizationId]) ;; no :address included
                      ;; the propertyId parameter can be nil
                      (fn [{{propertyId :propertyId} :data :as command}]
                        (when-not (ss/blank? propertyId)
                          (action/property-id-parameters [:propertyId] command)))]
   :pre-checks       [user-is-allowed-to-digitize
                      default-digitalization-location-set-if-used]}
  [{:keys [user] :as command}]
  (if-let [app-with-verdict (when-not (ss/blank? kuntalupatunnus)
                              (domain/get-application-as
                                {:organization organizationId
                                 :verdicts     {$elemMatch {:kuntalupatunnus (ss/trim kuntalupatunnus)}}
                                 :permitType   :ARK}
                                user
                                :include-canceled-apps? false))]
    ;;Found an application of same organization that has a verdict with the given kuntalupatunnus -> Open it.
    (ok :id (:id app-with-verdict))
    (d/fetch-or-create-archiving-project! (update command :data ss/trimwalk))))

(defcommand submit-archiving-project
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :user-authz-roles roles/default-authz-writer-roles
   :states           #{:open}
   :pre-checks       [permit/is-archiving-project]}
  [{:keys [application created user] :as command}]
  (action/update-application command
                             (util/deep-merge
                               (app-state/state-transition-update
                                 :underReview created application user)
                               {$set {:opened (:opened application)}})))

(defcommand store-archival-project-backend-ids
  {:parameters [id verdicts]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/vector-parameters [:verdicts])]
   :user-roles #{:authority}
   :user-authz-roles roles/default-authz-writer-roles
   :states           #{:open :underReview}
   :pre-checks       [permit/is-archiving-project]}
  [command]
  (if (some #(ss/blank? (ss/trim (:kuntalupatunnus %))) verdicts)
    (fail :error.no-verdict-municipality-id)
    (do (d/update-verdicts command verdicts)
        (ok))))

(defcommand set-archival-project-permit-date
  {:parameters [id date]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/number-parameters [:date])]
   :user-roles #{:authority}
   :user-authz-roles roles/default-authz-writer-roles
   :states           #{:open :underReview}
   :pre-checks       [permit/is-archiving-project
                      (fn [{{d :date} :data}]
                        (when (and (number? d) (> d (now)))
                          (fail :error.invalid-date)))]}
  [command]
  (d/update-verdict-date command date)
  (ok))

(defquery user-is-pure-digitizer
  {:description "Pseudo query that succeeds only when the user is digitizer."
   :user-roles #{:authority}
   :pre-checks [(fn [{user :user}]
                  (when-not (usr/user-is-pure-digitizer? user)
                    unauthorized))]}
  [_])

(defquery digitizing-enabled
  {:user-roles #{:authority}
   :pre-checks [user-is-allowed-to-digitize]}
  (ok))

(defcommand remove-buildings
  {:parameters  [id]
   :permissions [{:required [:application/remove-buildings-in-archiving-projects]}]
   :states      states/all-archiving-project-states
   :pre-checks  [(permit/validate-permit-type-is permit/ARK)]}
  [command]
  (d/remove-secondary-buildings command))
