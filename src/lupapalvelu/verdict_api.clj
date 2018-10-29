(ns lupapalvelu.verdict-api
  "Backing system verdicts. The API for manually created verdicts
  resides in `pate/verdict_api.clj`."
  (:require[taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
           [monger.operators :refer :all]
           [sade.core :refer [ok fail fail! ok?]]
           [sade.util :as util]
           [lupapalvelu.action :refer [defcommand notify] :as action]
           [lupapalvelu.application-state :as app-state]
           [lupapalvelu.attachment :as attachment]
           [lupapalvelu.backing-system.allu.core :as allu]
           [lupapalvelu.organization :as org]
           [lupapalvelu.pate.verdict :as pate-verdict :refer [backing-system-verdict command->backing-system-verdict]]
           [lupapalvelu.permit :as permit]
           [lupapalvelu.state-machine :as sm]
           [lupapalvelu.states :as states]
           [lupapalvelu.verdict :as verdict]))

(defn application-has-verdict-given-state [{:keys [application]}]
  (when-not (and application (some (partial sm/valid-state? application) states/verdict-given-states))
    (fail :error.command-illegal-state)))

(defn- backing-system-is-defined [{:keys [application organization]}]
  (when-let [permit-type (:permitType application)]
    (when-not (or (org/resolve-krysp-wfs @organization permit-type)
                  (allu/allu-application? (:organization application) permit-type))
      (fail :error.no-legacy-available))))

(defcommand check-for-verdict
  {:description "Fetches verdicts from municipality backend system.
  If the command is run more than once, existing verdicts are replaced
  by the new ones. Note: everything related to old backing system
  verdicts (tasks, attaachments, appeals, appealVerdicts) are
  deleted."
   :parameters  [:id]
   :states      (conj states/give-verdict-states :constructionStarted) ; states reviewed 2015-10-12
   :user-roles  #{:authority}
   :notified    true
   :pre-checks  [application-has-verdict-given-state
                 verdict/no-sent-backing-system-verdict-tasks
                 backing-system-is-defined]
   :on-success  (notify :application-state-change)}
  [{:keys [application created user] :as command}]
  (let [result (if (allu/allu-application? (:organization application) (permit/permit-type application))
                 (pate-verdict/fetch-allu-verdicts command)
                 ;; HACK: This is here instead of e.g. do-check-for-verdict to avoid verdict/allu/pate-verdict
                 ;;       dependency cycles:
                 #_(when-let [filedata (allu/load-contract-document! command)]
                   ;; FIXME: Some times should be dates, not timestamps:
                   (let [creator (:creator application)
                         signatures [{:name (:username user)
                                      :user-id (:id user)
                                      :date created}]
                         signatures (if (= :final (allu/agreement-state application))
                                      (conj signatures {:name (str (:firstName creator) " " (:lastName creator))
                                                        :user-id (:id (:creator application))
                                                        :date created})
                                      signatures)
                         verdict (pate-verdict/new-allu-verdict command)
                         verdict (assoc verdict
                                   :published {:published created
                                               :tags      (pr-str {:body (pate-verdict/backing-system--tags
                                                                           application verdict)})}
                                   ;; FIXME: Should be general-handler fullname instead of current username:
                                   :archive {:verdict-giver (:username user)}
                                   ;; FIXME: Should be general-handler fullname instead of current username:
                                   :signatures signatures) ; HACK
                         transition-update (app-state/state-transition-update (sm/next-state application)
                                                                              created application user)]
                     (attachment/convert-and-attach! command
                                                     {:created created
                                                      :attachment-type {:type-group :muut
                                                                        :type-id :sopimus}
                                                      :target {:id (:id verdict)
                                                               :type :verdict}
                                                      :modified created
                                                      :locked true
                                                      :read-only true}
                                                     filedata)
                     (action/update-application command (util/deep-merge transition-update
                                                                         {$push {:pate-verdicts verdict}}))
                     (ok :verdicts [verdict])))
                 (verdict/do-check-for-verdict command))]
    (cond
      (nil? result) (fail :info.no-verdicts-found-from-backend)
      (ok? result)  (ok :verdictCount (count (:verdicts result))
                        :taskCount (count (:tasks result)))
      :else         result)))

(defcommand delete-verdict
  {:parameters       [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           states/give-verdict-states
   :notified         true
   :categories       #{:pate-verdicts}
   :user-roles       #{:authority}
   :pre-checks       [backing-system-verdict]}
  [{:keys [application] :as command}]
  (verdict/delete-verdict command
                          (command->backing-system-verdict command)
                          true))
