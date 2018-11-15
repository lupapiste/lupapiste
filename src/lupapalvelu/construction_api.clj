(ns lupapalvelu.construction-api
  (:require [monger.operators :refer [$set $elemMatch]]
            [lupapalvelu.action :refer [defcommand update-application notify defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.util :as util]))


;;
;; Inform construction started & ready
;;

(defn- save-as-krysp-if-possible
  [{:keys [application organization] :as cmd} lang app-updates]
  (if (organization/krysp-integration? @organization (permit/permit-type application))
    (let [krysp-app (app/post-process-app-for-krysp (merge application app-updates) @organization)]
      (mapping-to-krysp/save-application-as-krysp (assoc cmd :application krysp-app) lang krysp-app)
      true)
    false))

(defn- construction-start-date-present [{:keys [application]}]
  (when application
    (when-not (:started application)
      (fail :error.command-illegal-state))))

(defn- construction-start-date-absent [{:keys [application]}]
  (when (and application (:started application))
    (fail :error.command-illegal-state)))

(defn- when-state-changed [success-fn]
  (fn [command status]
    (when (:stateChanged status)
      (success-fn command status))))

(defcommand inform-construction-started
  {:parameters       ["id" startedTimestampStr lang]
   :user-roles       #{:applicant :authority}
   :states           states/post-verdict-but-terminal
   :notified         true
   :on-success       (when-state-changed (notify :application-state-change))
   :pre-checks       [(permit/validate-permit-type-is permit/YA)
                      construction-start-date-absent]
   :input-validators [(partial action/non-blank-parameters [:startedTimestampStr])]}
  [{:keys [user created application] :as command}]
  (let [timestamp     (util/to-millis-from-local-date-string startedTimestampStr)
        app-updates   {:startedBy (select-keys user [:id :firstName :lastName])
                       :started   timestamp}
        krysp?        (save-as-krysp-if-possible command lang app-updates)
        change-state? (sm/can-proceed? application :constructionStarted)]
    (update-application command (util/deep-merge
                                 (when change-state?
                                   (app-state/state-transition-update :constructionStarted created application user))
                                 {$set app-updates}))
    (ok :integrationAvailable krysp?
        :stateChanged change-state?)))

(defcommand inform-construction-ready
  {:parameters       ["id" readyTimestampStr lang]
   :user-roles       #{:authority}
   :states           states/post-verdict-but-terminal
   :on-success       (notify :application-state-change)
   :pre-checks       [(permit/validate-permit-type-is permit/YA)
                      (action/some-pre-check
                        (partial sm/validate-state-transition :closed)
                        (partial sm/validate-state-transition :finished))
                      construction-start-date-present]
   :input-validators [(partial action/non-blank-parameters [:readyTimestampStr])]}
  [{user :user created :created orig-app :application :as command}]
  (let [timestamp   (util/to-millis-from-local-date-string readyTimestampStr)
        app-updates {:modified created
                     :closed   timestamp
                     :closedBy (select-keys user [:id :firstName :lastName])
                     :state    (if (sm/can-proceed? orig-app :closed)
                                 :closed
                                 :finished)}
        krysp?      (save-as-krysp-if-possible command lang app-updates)]
    (update-application command (util/deep-merge
                                  (if (sm/can-proceed? orig-app :closed)
                                    (app-state/state-transition-update :closed created orig-app user)
                                    (app-state/state-transition-update :finished created orig-app user))
                                  (if krysp?
                                    {$set app-updates}
                                    {$set (merge app-updates (app/warranty-period timestamp))})))
    (ok :integrationAvailable krysp?)))

(defquery info-construction-status
  {:parameters [id]
   :states #{:verdictGiven :constructionStarted :closed :finished}
   :user-roles #{:applicant :authority}
   :pre-checks [(permit/validate-permit-type-is permit/YA)]}
   [_])
