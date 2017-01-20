(ns lupapalvelu.verdict-api
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [ok fail fail! ok?]]
            [lupapalvelu.application :as application]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.action :refer [defquery defcommand update-application notify boolean-parameters] :as action]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.document.transformations :as doc-transformations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.tiedonohjaus :as t]
            [lupapalvelu.user :as user]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.appeal-common :as appeal-common]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [sade.env :as env]))

;;
;; KRYSP verdicts
;;

(defn application-has-verdict-given-state [{:keys [application]}]
  (when-not (and application (some (partial sm/valid-state? application) states/verdict-given-states))
    (fail :error.command-illegal-state)))

(def give-verdict-states (clojure.set/union #{:submitted :complementNeeded :sent} states/verdict-given-states))

(defquery verdict-attachment-type
  {:parameters       [:id]
   :states           states/all-states
   :user-roles       #{:authority}}
  [{:keys [application]}]
  (ok :attachmentType (verdict/verdict-attachment-type application)))

(defcommand check-for-verdict
  {:description "Fetches verdicts from municipality backend system.
                 If the command is run more than once, existing verdicts are
                 replaced by the new ones."
   :parameters [:id]
   :states     (conj give-verdict-states :constructionStarted) ; states reviewed 2015-10-12
   :user-roles #{:authority}
   :notified   true
   :pre-checks [application-has-verdict-given-state]
   :on-success (notify :application-state-change)}
  [{app :application :as command}]
  (let [result (verdict/do-check-for-verdict command)]
    (cond
      (nil? result) (fail :info.no-verdicts-found-from-backend)
      (ok? result) (ok :verdictCount (count (:verdicts result)) :taskCount (count (:tasks result)))
      :else result)))

;;
;; Manual verdicts
;;

(defn- validate-status [{{:keys [status]} :data}]
  (when (and status (or (< status 1) (> status 42)))
    (fail :error.false.status.out.of.range.when.giving.verdict)))

(defcommand new-verdict-draft
  {:parameters [:id]
   :states     give-verdict-states
   :pre-checks [application-has-verdict-given-state]
   :user-roles #{:authority}}
  [{:keys [application] :as command}]
  (let [organization (get application :organization)
        tosFunction (get application :tosFunction)
        metadata (when (seq tosFunction) (t/metadata-for-document organization tosFunction "p\u00e4\u00e4t\u00f6s"))
        blank-verdict (cond-> (domain/->paatos {:draft true})
                              (seq metadata) (assoc :metadata metadata))]
    (update-application command {$push {:verdicts blank-verdict}})
    (ok :verdictId (:id blank-verdict))))

(defn- find-verdict [{verdicts :verdicts} id]
  (some #(when (= id (:id %)) %) verdicts))

(defcommand save-verdict-draft
  {:parameters [:id verdictId :backendId #_:status :name :section :agreement :text :given :official]
   :description  "backendId = Kuntalupatunnus, status = poytakirjat[] / paatoskoodi,
                  name = poytakirjat[] / paatoksentekija, section = poytakirjat[] / pykala
                  agreement = (sopimus), text =  poytakirjat[] / paatos
                  given, official = paivamaarat / antoPvm, lainvoimainenPvm"
   :input-validators [validate-status
                      (partial action/non-blank-parameters [:verdictId])
                      (partial action/boolean-parameters [:agreement])]
   :states     give-verdict-states
   :user-roles #{:authority}
   :pre-checks [application-has-verdict-given-state
                (fn [{{:keys [verdictId]} :data application :application}]
                  (when verdictId
                    (when-not (:draft (find-verdict application verdictId))
                      (fail :error.verdict.not-draft))))]}
  [{:keys [application created data] :as command}]
  (let [paatos-id (-> (find-verdict application verdictId) :paatokset first :id)
        verdict (domain/->paatos
                  (merge
                    (select-keys data [:verdictId :backendId :status :name :section :agreement :text :given :official])
                    {:timestamp created, :draft true, :paatos-id paatos-id}))]
    (update-application command
      {:verdicts {$elemMatch {:id verdictId}}}
      {$set {"verdicts.$.kuntalupatunnus" (:kuntalupatunnus verdict)
             "verdicts.$.draft" true
             "verdicts.$.timestamp" created
             "verdicts.$.sopimus" (:sopimus verdict)
             "verdicts.$.paatokset" (:paatokset verdict)}})))

(defn- create-verdict-pdfa! [user application verdict-id lang]
  (let [application (domain/get-application-no-access-checking (:id application))]
    (when (> 1 (count (:paatokset (first (filter #(= verdict-id (:id %)) (:verdicts application)))))) (error "Too many paatokset in verdict( " verdict-id ") in application: " (:id application)))
    (child-to-attachment/create-attachment-from-children user application :verdicts verdict-id lang)))

(defn- publish-verdict [{timestamp :created application :application lang :lang user :user :as command} {:keys [id kuntalupatunnus sopimus]}]
  (if-not (ss/blank? kuntalupatunnus)
    (when-let [next-state (sm/verdict-given-state application)]
      (let [doc-updates (doc-transformations/get-state-transition-updates command next-state)
            verdict-updates (util/deep-merge
                              (application/state-transition-update next-state timestamp application user)
                              {$set {:verdicts.$.draft false}})]
        (update-application command {:verdicts {$elemMatch {:id id}}} verdict-updates)
        (when (seq doc-updates)
          (update-application command
                              (:mongo-query doc-updates)
                              (:mongo-updates doc-updates)))
        (t/mark-app-and-attachments-final! (:id application) timestamp)
        (create-verdict-pdfa! user application id lang)
        (ok)))
    (fail :error.no-verdict-municipality-id)))

(defcommand publish-verdict
  {:parameters [id verdictId lang]
   :input-validators [(partial action/non-blank-parameters [:id :verdictId :lang])]
   :states     give-verdict-states
   :pre-checks [application-has-verdict-given-state]
   :notified   true
   :on-success (notify :application-state-change)
   :user-roles #{:authority}}
  [{:keys [application] :as command}]
  (if-let [verdict (find-verdict application verdictId)]
    (publish-verdict command verdict)
    (fail :error.unknown)))

(defcommand delete-verdict
  {:parameters [id verdictId]
   :input-validators [(partial action/non-blank-parameters [:id :verdictId])]
   :states     give-verdict-states
   :user-roles #{:authority}}
  [{:keys [application created] :as command}]
  (when-let [verdict (find-verdict application verdictId)]
    (let [target {:type "verdict", :id verdictId} ; key order seems to be significant!
          is-verdict-attachment? #(= (select-keys (:target %) [:id :type]) target)
          attachments (filter is-verdict-attachment? (:attachments application))
          {:keys [sent state verdicts]} application
          ; Deleting the only given verdict? Return sent or submitted state.
          step-back? (and (= 1 (count verdicts)) (states/verdict-given-states (keyword state)))
          task-ids (verdict/deletable-verdict-task-ids application verdictId)
          attachments (concat attachments (verdict/task-ids->attachments application task-ids))
          updates (merge {$pull {:verdicts {:id verdictId}
                                 :comments {:target target}
                                 :tasks {:id {$in task-ids}}}}
                    (when step-back? {$set {:state (if (and sent (sm/valid-state? application :sent)) :sent :submitted)}}))]
      (update-application command updates)
      (attachment/delete-attachments! application (remove nil? (map :id attachments)))
      (appeal-common/delete-by-verdict command verdictId)
      (child-to-attachment/delete-child-attachment application :verdicts verdictId)
      (when step-back?
        (notifications/notify! :application-state-change command)))))

(defcommand sign-verdict
  {:description "Applicant/application owner can sign an application's verdict"
   :parameters [id verdictId password lang]
   :input-validators [(partial action/non-blank-parameters [:id :verdictId :password :lang])]
   :states     states/post-verdict-states
   :pre-checks [domain/validate-owner-or-write-access]
   :user-roles #{:applicant :authority}}
  [{:keys [application created user] :as command}]
  (if (user/get-user-with-password (:username user) password)
    (when (find-verdict application verdictId)
      (let [result (update-application command
                          {:verdicts {$elemMatch {:id verdictId}}}
                          {$set  {:modified              created}
                           $push {:verdicts.$.signatures {:created created
                                                          :user (user/summary user)}}
                          })]
          (create-verdict-pdfa! user application verdictId lang)
          result))
    (do
      ; Throttle giving information about incorrect password
      (Thread/sleep 2000)
      (fail :error.password))))
