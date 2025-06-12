(ns lupapalvelu.verdict-api
  "Backing system verdicts. The API for manually created verdicts
  resides in `pate/verdict_api.clj`."
  (:require [lupapalvelu.action :refer [defcommand notify] :as action]
            [lupapalvelu.backing-system.allu.contract :as allu-contract]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.dmcity.core :as dmcity]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.notice-forms :as forms]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict :refer [backing-system-verdict command->backing-system-verdict]]
            [lupapalvelu.pate.verdict-date :as verdict-date]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail ok?]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(defn application-has-verdict-given-state [{:keys [application]}]
  (when-not (and application (some (partial sm/valid-state? application) states/verdict-given-states))
    (fail :error.command-illegal-state)))

(defn- backing-system-is-defined [{:keys [application organization]}]
  (when-let [permit-type (:permitType application)]
    (when-not (or (org/resolve-krysp-wfs @organization permit-type)
                  (allu/allu-application? (:organization application) permit-type))
      (fail :error.no-legacy-available))))

(defn- not-converted-application
  [{:keys [application]}]
  (when (:facta-imported application)
    (fail :error.converted-application)))

(defn- check-for-verdict [{:keys [application data] :as command}]
  (let [result (if (allu/allu-application? (:organization application) (permit/permit-type application))
                 (let [history (allu/application-history command (date/zoned-date-time (:created application)))]
                   (when (allu/application-history-fetchable? history)
                     (when-let [verdicts (allu-contract/fetch-allu-contract command)]
                       (ok :verdicts verdicts))))
                 (verdict/do-check-for-verdict command (:foreman-doc-id data)))]
    (cond
      (nil? result) (fail :info.no-verdicts-found-from-backend)
      (ok? result) (ok :verdictCount (count (:verdicts result))
                       :taskCount (count (:tasks result)))
      :else result)))

(defcommand check-for-verdict
  {:description         "Fetches verdicts from municipality backend system.
  If the command is run more than once, existing verdicts are replaced
  by the new ones. Note: everything related to old backing system
  verdicts (tasks, attachments, appeals, appealVerdicts) are
  deleted."
   :parameters          [:id]
   :states              (conj states/give-verdict-states :constructionStarted) ; states reviewed 2015-10-12
   :user-roles          #{:authority}
   :notified            true
   :pre-checks          [application-has-verdict-given-state
                         not-converted-application
                         verdict/no-sent-backing-system-verdict-tasks
                         backing-system-is-defined
                         dmcity/check-dmcity-disabled]
   :on-success          [(notify :application-state-change)
                         verdict-date/update-verdict-date]}
  [command]
  (check-for-verdict command))

(defcommand check-verdict-for-document
  {:description         "Fetches foreman verdicts from municipality backend system.
  If the command is run more than once, existing verdicts are replaced
  by the new ones. Note: everything related to old backing system
  verdicts (tasks, attachments, appeals, appealVerdicts) are
  deleted."
   :parameters          [:id :foreman-doc-id]
   :categories          #{:documents}
   :states              (conj states/give-verdict-states :constructionStarted) ; states reviewed 2015-10-12
   :user-roles          #{:authority}
   :input-validators    [{:id             ssc/NonBlankStr
                          :foreman-doc-id ssc/NonBlankStr}]
   :pre-checks          [application-has-verdict-given-state
                         not-converted-application
                         backing-system-is-defined
                         dmcity/check-dmcity-disabled
                         foreman/foreman-doc-id-is-foreman-v3-document]}
  [command]
  (check-for-verdict command))


(sc/defschema VerdictFetchOptions
  {:send-notifications        sc/Bool
   :remove-verdict-attachment sc/Bool
   :update-bulletin           sc/Bool})

(def verdict-fetch-options-checker (sc/checker VerdictFetchOptions))

(defn- valid-verdict-fix-options [{{:keys [verdict-fix-options]} :data}]
  (when (verdict-fetch-options-checker verdict-fix-options)
    (fail :error.unsupported-parameters {:parameters verdict-fix-options})))

(defn- fix-not-supported-in-allu [{{:keys [organization permitType]} :application}]
  (when (allu/allu-application? organization permitType)
    (fail :error.allu-not-supported)))

(defn- verdict-fix-email-notification
  "State is not changed if the fetched verdict is a fixed version of an existing verdict and not a new one"
  [command status]
  (when (-> command :data :verdict-fix-options :send-notifications)
    (notifications/notify! :verdict-update command status)))

(defcommand check-for-verdict-fix
  {:description      "Like check-for-verdict, but meant for fixing an incorrect
  verdict (attachment) in a terminal state and so doesn't have the same side-effects that
  command has.  Does not have an ALLU implementation since the ready state is not
  available for YA (and by extension ALLU) applications."
   :parameters       [id verdict-fix-options]
   :input-validators [(partial action/non-blank-parameters [:id])
                      valid-verdict-fix-options]
   :states           #{:ready}
   :user-roles       #{:authority}
   :notified         true
   :pre-checks       [application-has-verdict-given-state
                      not-converted-application
                      verdict/no-sent-backing-system-verdict-tasks
                      fix-not-supported-in-allu
                      backing-system-is-defined
                      dmcity/check-dmcity-disabled]
   :on-success       [verdict-fix-email-notification
                      verdict-date/update-verdict-date]}
  [command]
  (let [result (verdict/do-check-for-verdict-fix command)]
    (cond
      (nil? result) (fail :info.no-verdicts-found-from-backend)
      (ok? result)  (ok :verdictCount (count (:verdicts result))
                        :taskCount (count (:tasks result)))
      :else         result)))

(defcommand delete-verdict
  {:parameters       [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           states/post-submitted-states
   :notified         true
   :categories       #{:pate-verdicts}
   :user-roles       #{:authority}
   :pre-checks       [backing-system-verdict]
   :on-success       [verdict-date/update-verdict-date
                      forms/cleanup-notice-forms]}
  [command]
  (verdict/delete-verdict command
                          (command->backing-system-verdict command)
                          true)
  (ok))
