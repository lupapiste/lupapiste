(ns lupapalvelu.tasks-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters update-application]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]))

;; Helpers

(defn- get-task [tasks task-id]
  (some #(when (= (:id %) task-id) %) tasks))

(defn- assert-task-state-in [states {{task-id :taskId} :data {tasks :tasks} :application}]
  (if-let [task (get-task tasks task-id)]
    (when-not ((set states) (keyword (:state task)))
      (fail! :error.command-illegal-state))
    (fail! :error.task-not-found)))

(defn- set-state [{created :created :as command} task-id state & [updates]]
  (update-application command
    {:tasks {$elemMatch {:id task-id}}}
    (util/deep-merge
      {$set {:tasks.$.state state :modified created}}
      updates)))

;; API
(def valid-source-types #{"verdict"})

(defn- valid-source [{:keys [id type]}]
  (when (and (string? id) (valid-source-types type))
    {:id id, :type type}))

(defcommand create-task
  {:parameters [id taskName schemaName]
   :input-validators [(partial non-blank-parameters [:id :taskName :schemaName])]
   :user-roles #{:authority}
   :states     [:open :submitted :sent :complement-needed :verdictGiven :constructionStarted]}
  [{:keys [created application user data] :as command}]
  (when-not (some #(let [{:keys [name type]} (:info %)] (and (= name schemaName ) (= type :task))) (tasks/task-schemas application))
    (fail! :illegal-schema))
  (let [task (tasks/new-task schemaName taskName {} {:created created :assignee user} (or (valid-source (:source data)) {:type :authority :id (:id user)}))]
    (update-application command
      {$push {:tasks task}
       $set {:modified created}})
    (ok :taskId (:id task))))

(defcommand delete-task
  {:parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states     [:open :submitted :sent :complement-needed :verdictGiven :constructionStarted]}
  [{created :created :as command}]
  (assert-task-state-in [:requires_user_action :requires_authority_action :ok] command)
  (update-application command
    {$pull {:tasks {:id taskId}}
     $set  {:modified  created}}))

(defcommand approve-task
  {:description "Authority can approve task, moves to ok"
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      [:open :submitted :sent :complement-needed :verdictGiven :constructionStarted]}
  [command]
  (assert-task-state-in [:requires_user_action :requires_authority_action] command)
  (set-state command taskId :ok))

(defcommand reject-task
  {:description "Authority can reject task, requires user action."
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      [:open :submitted :sent :complement-needed :verdictGiven :constructionStarted]}
  [command]
  (assert-task-state-in [:ok :requires_user_action :requires_authority_action] command)
  (set-state command taskId :requires_user_action))

(defcommand send-task
  {:description "Authority can send task info to municipality backend system."
   :parameters  [id taskId lang]
   :input-validators [(partial non-blank-parameters [:id :taskId :lang])]
   :pre-checks  [(permit/validate-permit-type-is permit/R)] ; KRYPS mapping currently implemented only for R
   :user-roles #{:authority}
   :states      [:open :submitted :sent :complement-needed :verdictGiven :constructionStarted]}
  [{application :application user :user created :created :as command}]
  (assert-task-state-in [:ok :sent] command)
  (let [task (get-task (:tasks application) taskId)]
    (when-not (= "task-katselmus" (-> task :schema-info :name)) (fail! :error.invalid-task-type))
    (when (ss/blank? (get-in task [:data :katselmuksenLaji :value])) (fail! :error.missing-parameters))
    (let [sent-file-ids (mapping-to-krysp/save-review-as-krysp application task user lang)
          set-statement (attachment/create-sent-timestamp-update-statements (:attachments application) sent-file-ids created)]
      (set-state command taskId :sent (when (seq set-statement) {$set set-statement})))))

(defquery task-types-for-application
  {:description "Returns a list of allowed schema names for current application and user"
   :parameters [:id]
   :user-roles #{:authority}
   :states     states/all-states}
  [{application :application}]
  (ok :schemas (map (comp :name :info) (sort-by (comp :order :info) (tasks/task-schemas application)))))
