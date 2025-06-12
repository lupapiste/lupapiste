(ns lupapalvelu.digitizer-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.db :as db]
            [lupapalvelu.digitizer :as d]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.permissions :as perm]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- digitizing-organization? [{archive?     :permanent-archive-enabled
                                  tools?       :digitizer-tools-enabled
                                  deactivated? :deactivated
                                  scope        :scope}]
  (and archive? tools? (not deactivated?)
       (util/find-by-key :permitType "R" scope)))


(defn- digitizing-user? [user org-id]
  (contains? (perm/get-organization-permissions {:data {:organizationId org-id}
                                                 :user user})
             :organization/digitize))

(defn- user-is-allowed-to-digitize
  "Pre-check that fails if the current user cannot digitize (create archiving projects) in
  any of their organizations."
  [{user-orgs :user-organizations user :user}]
  (let [good-org-ids (some->> user-orgs
                              (filter digitizing-organization?)
                              seq
                              (mapv :id))]
    (when-not (some #(digitizing-user? user %) good-org-ids)
      unauthorized)))

(defn- digitizing-organization
  "Pre-check that fails if the organization does not support creating archiving projects."
  [{:keys [user-organizations data]}]
  (when-let [org-id (:organizationId data)]
    (when-not (digitizing-organization? (util/find-by-id org-id user-organizations))
      (fail :error.digitalization-not-supported))))


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
  {:description         "Create a new ARK application. The parameters determine, what kinds of
  preliminary data are needed."
   :parameters          [organizationId]
   :optional-parameters [kuntalupatunnus]
   :permissions         [{:required [:organization/digitize]}]
   :input-validators    [{:lang                                        i18n/Lang
                          :organizationId                              ssc/NonBlankStr
                          (sc/optional-key :kuntalupatunnus)           sc/Str
                          (sc/optional-key :x)                         ssc/ValidX
                          (sc/optional-key :y)                         ssc/ValidY
                          (sc/optional-key :address)                   ssc/NonBlankStr
                          (sc/optional-key :propertyId)                ssc/Kiinteistotunnus
                          (sc/optional-key :createWithPreviousPermit)  sc/Bool
                          (sc/optional-key :createWithBuildings)       sc/Bool
                          (sc/optional-key :createWithDefaultLocation) sc/Bool
                          (sc/optional-key :refreshBuildings)          sc/Bool
                          sc/Keyword                                   sc/Any}]

   :pre-checks [digitizing-organization
                default-digitalization-location-set-if-used]}
  [{:keys [user] :as command}]
  (if-let [[old-app-id] (when-not (ss/blank? kuntalupatunnus)
                          (db/application-ids-by-kuntalupatunnus organizationId
                                                                 kuntalupatunnus
                                                                 {:permitType :ARK
                                                                  :state      {$ne :canceled}}
                                                                 {:limit 1}))]
    ;;Found an application of same organization that has a verdict with the given kuntalupatunnus -> Open it.
    (ok :id old-app-id)
    (d/fetch-or-create-archiving-project! (update command :data ss/trimwalk))))

(defcommand submit-archiving-project
  {:parameters       [:id]
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
  {:parameters [:id verdicts]
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
  [_]
  (ok))

(defquery digitizing-enabled
  {:description "Pseudo-query for checking, whether the digitizing tools are available for the user."
   :user-roles  #{:authority}
   :pre-checks  [user-is-allowed-to-digitize]}
  [_]
  (ok))

(defcommand remove-buildings
  {:parameters  [:id]
   :permissions [{:required [:application/remove-buildings-in-archiving-projects]}]
   :states      states/all-archiving-project-states
   :pre-checks  [(permit/validate-permit-type-is permit/ARK)]}
  [command]
  (or (d/remove-secondary-buildings command)
      (ok)))
