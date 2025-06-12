(ns lupapalvelu.conversion.import.task
  "Task import support."
  (:require [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.import.shared :as shared]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.tasks :as tasks]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- make-text [{:keys [text received processed]}]
  (ss/trim-join "\n\n"
                text
                (ss/trim-join
                  "\n"
                  (some->> received date/finnish-date (format "Saapunut %s."))
                  (some->> processed date/finnish-date (format "Käsitelty %s.")))))

(defn- plan-pred
  "Returns predicate that is true for lupamääräys with a matching name."
  [target-taskname]
  (fn [{:keys [taskname schema-info]}]
    (and (ss/=trim-i taskname target-taskname)
         (util/=as-kw (:name schema-info) :task-lupamaarays))))

(defn- task-text [task]
  (some-> task :data :kuvaus :value ss/trim))

(defn- old-match-pred
  "Returns predicate that is true for old match for `import`. The match is an approved
  lupamääräys with matching data."
  [{:keys [taskname] :as import}]
  (let [plan? (plan-pred taskname)
        text  (make-text import)]
    (fn [task]
      (and (plan? task)
           (util/=as-kw (:state task) :ok)
           (ss/ends-with? (task-text task) text)))))

(defn- import-task
  "Approves and updates the matching `task-lupamaarays` in the converted application. There
  are number of usecases to consider:

  1. Task has already been updated earlier -> do nothing
  2. Matching unapproved task is found (aka the regular case) -> approve and update
  3. No suitable match found -> create new approved task"
  [user organization-id {:keys [backend-id taskname] :as import}]
  (if-let [app-id (:LP-id (conv-doc/document {:organization organization-id
                                              :backend-id   backend-id
                                              :converted    true}))]
    (log/with-logging-context {:applicationId app-id}
      (let [{:keys [facta-imported
                    tasks]}   (mongo/by-id :applications app-id
                                           [:tasks :facta-imported])
            oldie             (util/find-first (old-match-pred import)
                                               tasks)
            {task-state :state task-id :id
             :as        task} (when-not oldie
                                (util/find-first (plan-pred taskname)
                                                 tasks))
            text              (make-text import)
            old-text          (task-text task)]
        (cond
          (not facta-imported)
          (log/errorf "Import task: Converted application for %s not found."
                      backend-id)

          ;; Since different plans can have the same name, we need to check the text as
          ;; well.
          oldie
          (log/warnf "Import task: %s (%s) already done." taskname (:id oldie))

          (util/=as-kw task-state :requires_user_action)
          (if (pos? (mongo/update-by-query :applications
                                           {:_id   app-id
                                            :tasks {$elemMatch {:id task-id}}}
                                           (conv-util/with-sheriff-note
                                             (format  "LPK-6769: Import task %s (%s)."
                                                      (ss/capitalize taskname) task-id)
                                             {$set (util/strip-blanks
                                                     {:tasks.$.assignee          (select-keys user [:firstName
                                                                                                    :lastName
                                                                                                    :id])
                                                      :tasks.$.state             :ok
                                                      :tasks.$.data.kuvaus.value (ss/trim-join "\n\n"
                                                                                               old-text
                                                                                               text)})})))
            (log/infof "Import task: %s (%s) updated." taskname task-id)
            ;; This should never happen, but just in case.
            (log/warnf "Import task: %s (%s) not found." taskname task-id))

          :else
          (let [new-task (tasks/new-task :task-lupamaarays
                                         taskname
                                         {:kuvaus (make-text import)}
                                         {:created  (date/timestamp (date/now))
                                          :assignee user
                                          :state    :ok}
                                         {:type :background})]
            (mongo/update-by-id :applications app-id
                                (conv-util/with-sheriff-note
                                  (format "LPK-6769: Import new task %s (%s)."
                                          (ss/capitalize taskname) (:id new-task))
                                  {$push {:tasks new-task}}))))))
    (log/errorf "Import task: %s not converted." backend-id)))

(defmethod shared/import--items :tasks
  [{:keys [user organization-id items]}]
  (doseq [item (mapv conv-sc/valid-task-item! items)]
    (try
      (import-task user organization-id item)
      (catch Exception e
        (log/error e)))))
