(ns lupapalvelu.construction-api
  (:require [lupapalvelu.action :refer [defcommand update-application notify defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [monger.operators :refer [$set]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.util :as util]))


;;
;; Inform construction started & ready
;;

(defn- save-as-krysp-if-possible
  [{:keys [application organization] :as cmd} lang app-updates]
  (if (organization/krysp-write-integration? @organization (permit/permit-type application))
    (let [krysp-app (app/post-process-app-for-krysp (merge application app-updates) @organization)]
      (mapping-to-krysp/save-application-as-krysp (assoc cmd :application krysp-app) lang krysp-app)
      true)
    false))

(defn- when-state-changed [success-fn]
  (fn [command status]
    (when (:stateChanged status)
      (success-fn command status))))

(defn- dates-sanity-check
  "Time part of the dates is igored during checking."
  [{:keys [application data created]}]
  (when application
    (let [fail-on-after (fn [a b err]
                          (when (and a b
                                     (date/after? (date/start-of-day a)
                                                  (date/start-of-day b)))
                            (fail err)))
          started       (or (:startedTimestampStr data)
                            (:started application))
          ready         (or (:readyTimestampStr data)
                            (:closed application))]
      (or (fail-on-after started created :error.started-in-the-future)
          (fail-on-after ready created :error.ready-in-the-future)
          (fail-on-after started ready :error.started-after-ready)))))

(defcommand inform-construction-started
  {:parameters       ["id" startedTimestampStr lang]
   :permissions      [{:context  {:application {:started nil?}}
                       :required [:application/inform-construction-state]}
                      {:required [:application/edit-construction-state]}]
   :states           states/post-verdict-but-terminal
   :notified         true
   :on-success       (when-state-changed (notify :application-state-change))
   :pre-checks       [(permit/validate-permit-type-is permit/YA)
                      dates-sanity-check]
   :input-validators [(action/date-parameter :startedTimestampStr)]}
  [{:keys [user created application] :as command}]
  (let [timestamp     (date/timestamp startedTimestampStr)
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
   :permissions      [{:context  {:application {:started number?}}
                       :required [:application/edit-construction-state]}]
   :states           states/post-verdict-but-terminal
   :on-success       (notify :application-state-change)
   :pre-checks       [(permit/validate-permit-type-is permit/YA)
                      (action/some-pre-check
                        (partial sm/validate-state-transition :closed)
                        (partial sm/validate-state-transition :finished))
                      dates-sanity-check]
   :input-validators [(action/date-parameter :readyTimestampStr)]}
  [{user :user created :created orig-app :application :as command}]
  (let [timestamp     (date/timestamp readyTimestampStr)
        app-updates   {:modified created
                       :closed   timestamp
                       :closedBy (select-keys user [:id :firstName :lastName])
                       :state    (if (sm/can-proceed? orig-app :closed)
                                   :closed
                                   :finished)}
        krysp?        (save-as-krysp-if-possible command lang app-updates)
        has-warranty? (or (:warrantyStart orig-app) (:warrantyEnd orig-app))]
    (update-application command (util/deep-merge
                                  (if (sm/can-proceed? orig-app :closed)
                                    (app-state/state-transition-update :closed created orig-app user)
                                    (app-state/state-transition-update :finished created orig-app user))
                                  (if (or krysp? has-warranty?)
                                    {$set app-updates}
                                    {$set (merge app-updates (app/warranty-period timestamp))})))
    (ok :integrationAvailable krysp?)))

(defquery info-construction-status
  {:parameters       [id]
   :states           #{:verdictGiven :constructionStarted :closed :finished}
   :permissions      [{:required [:application/read]}]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [(permit/validate-permit-type-is permit/YA)]}
   [_])
