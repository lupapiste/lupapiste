(ns lupapalvelu.tasks-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters update-application]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.document.model :as model]))

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

(defn- task-is-review? [task]
  (let [task-type (-> task :schema-info :name)]
    (contains? #{"task-katselmus" "task-katselmus-ya"} task-type)))

;; API
(def valid-source-types #{"verdict" "task"})

(def valid-states states/all-application-states-but-draft-or-terminal)

(defn- valid-source [{:keys [id type]}]
  (when (and (string? id) (valid-source-types type))
    {:id id, :type type}))

(defcommand create-task
  {:parameters [id taskName schemaName]
   :optional-parameters [taskSubtype]
   :input-validators [(partial non-blank-parameters [:id :taskName :schemaName])]
   :user-roles #{:authority}
   :states     valid-states}
  [{:keys [created application user data] :as command}]
  (when-not (some #(let [{:keys [name type]} (:info %)] (and (= name schemaName ) (= type :task))) (tasks/task-schemas application))
    (fail! :illegal-schema))
  (let [meta {:created created :assignee user}
        source (or (valid-source (:source data)) {:type :authority :id (:id user)})
        task (tasks/new-task schemaName
                             taskName
                             (if-not (ss/blank? taskSubtype)
                               {:katselmuksenLaji taskSubtype}
                               {})
                             meta
                             source)
        validation-results (tasks/task-doc-validation schemaName task)
        error (when (seq validation-results)
                (-> (first validation-results)
                    :result
                    last))]
    (if-not error
      (do
        (update-application command
                            {$push {:tasks task}
                             $set {:modified created}})
        (ok :taskId (:id task)))
      (fail (str "error." error)))))

;;TODO: remove task PDF attachment if it exists [and if you can figure out how to identify it]
(defcommand delete-task
  {:parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states     valid-states}
  [{:keys [application created] :as command}]
  (assert-task-state-in [:requires_user_action :requires_authority_action :ok] command)
  (child-to-attachment/delete-child-attachment application :tasks taskId)
  (update-application command
    {$pull {:tasks {:id taskId}}
     $set  {:modified  created}}))

(defn generate-task-pdfa [application {info :schema-info :as task} user lang]
  (when (= "task-katselmus" (:name info))
    (child-to-attachment/create-attachment-from-children user application :tasks (:id task) lang)))

(defcommand approve-task
  {:description "Authority can approve task, moves to ok"
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      valid-states}
  [{:keys [application user lang] :as command}]
  (assert-task-state-in [:requires_user_action :requires_authority_action] command)
  (generate-task-pdfa application (get-task (:tasks application) taskId) user lang)
  (set-state command taskId :ok))

(defcommand reject-task
  {:description "Authority can reject task, requires user action."
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      valid-states}
  [{:keys [application] :as command}]
  (assert-task-state-in [:ok :requires_user_action :requires_authority_action] command)
  (set-state command taskId :requires_user_action))

(defn- validate-task-is-review [{data :data} {tasks :tasks}]
  (when-let [task-id (:taskId data)]
    ; TODO create own auth model for task and combile let forms
    (when-not (task-is-review? (get-task tasks task-id))
      (fail :error.invalid-task-type))))

(defn- validate-review-kind [{data :data} {tasks :tasks}]
  (when-let [task-id (:taskId data)]
    ; TODO create own auth model for task and combile let forms
    (when (ss/blank? (get-in (get-task tasks task-id) [:data :katselmuksenLaji :value]))
      (fail :error.missing-parameters))))

;; TODO to be deleted after review-done feature is in production
(defcommand send-task
  {:description "Authority can send task info to municipality backend system."
   :parameters  [id taskId lang]
   :input-validators [(partial non-blank-parameters [:id :taskId :lang])]
   :pre-checks  [validate-task-is-review
                 validate-review-kind
                 (permit/validate-permit-type-is permit/R permit/YA)] ; KRYPS mapping currently implemented only for R & YA
   :user-roles  #{:authority}
   :states      valid-states}
  [{application :application user :user created :created :as command}]
  (assert-task-state-in [:ok :sent] command)
  (let [task (get-task (:tasks application) taskId)
        all-attachments (:attachments (domain/get-application-no-access-checking id [:attachments]))
        sent-file-ids (mapping-to-krysp/save-review-as-krysp application task user lang)
        set-statement (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids created)]
    (set-state command taskId :sent (when (seq set-statement) {$set set-statement}))))

(defn- schema-with-type-options
  "Genereate 'subtype' options for readonly elements with sequential body"
  [{info :info body :body}]
  {:schemaName (:name info)
   :types      (some (fn [{:keys [name readonly body]}]
                       (when (and readonly (seq body))
                         (for [option body
                               :let [option-id (:name option)]]
                           {:id   option-id
                            :text (i18n/localize i18n/*lang* (:name info) name option-id)})))
                     body)})

(defquery task-types-for-application
  {:description      "Returns a list of allowed schema maps for current application and user. Map has :schemaName, and :types is list of subtypes for that schema"
   :parameters       [:id :lang]
   :user-roles       #{:authority}
   :input-validators [(partial non-blank-parameters [:lang])]
   :states           states/all-states}
  [{application :application}]
  (ok :schemas (->> application
                 (tasks/task-schemas)
                 (sort-by (comp :order :info))
                 (map schema-with-type-options))))
