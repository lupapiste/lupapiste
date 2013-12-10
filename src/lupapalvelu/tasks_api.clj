(ns lupapalvelu.tasks-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters update-application]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]))

;; Helpers

(defn- get-task [tasks task-id]
  (some #(when (= (:id %) task-id) %) tasks))

(defn- assert-task-state-in [states {{task-id :taskId} :data {tasks :tasks} :application}]
  (if-let [task (get-task tasks task-id)]
    (when-not ((set states) (keyword (:state task)))
      (fail! :error.command-illegal-state))
    (fail! :error.task-not-found)))

(defn- set-state [{created :created :as command} task-id state]
  (update-application command
    {:tasks {$elemMatch {:id task-id}}}
    {$set {:tasks.$.state state :modified created}}))

;; API

(defcommand delete-task
  {:parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :roles      [:authority]}
  [{created :created :as command}]
  (assert-task-state-in [:requires_user_action :requires_authority_action :ok] command)
  (update-application command
    {$pull {:tasks {:id taskId}}
     $set  {:modified  created}}))

(defcommand approve-task
  {:description "Authority can approve task, moves to ok"
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :roles       [:authority]}
  [command]
  (assert-task-state-in [:requires_user_action :requires_authority_action] command)
  (set-state command taskId :ok))

(defcommand reject-task
  {:description "Authority can reject task, requires user action."
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :roles       [:authority]}
  [command]
  (assert-task-state-in [:ok :requires_user_action :requires_authority_action] command)
  (set-state command taskId :requires_user_action))

(defcommand send-task
  {:description "Authority can send task info to municipality backend system."
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :roles       [:authority]}
  [{application :application :as command}]
  (assert-task-state-in [:ok :sent] command)
  (let [task (get-task {:tasks application} taskId)]
    (when-not (= "task-katselmus" (-> task :schema-info :name)) (fail! :error.task-not-found)) ; TODO change key
    (mapping-to-krysp/save-review-as-krysp application task)
    (set-state command taskId :sent)))
