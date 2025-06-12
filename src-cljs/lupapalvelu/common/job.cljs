(ns lupapalvelu.common.job
  "Functionality for polling back-end jobs and subscriptions to job updates"
  (:require [lupapalvelu.next.router-api :as router]
            [re-frame.core :as rf]
            [sade.util :as util]))

(def terminal-statuses #{"done" "error"})

(defn run-callbacks [db job updates]
  (run! (fn [f] (f job updates))
        (get-in db [::jobs (:id job) :callback-fns])))

(defn sub-task-updates [job prev-state]
  (some->> job
           :value
           (remove (fn [[sub-task {:keys [status]}]]
                     (= status
                        (get-in prev-state [:value sub-task :status]))))
           (into {})))

(rf/reg-event-fx ::job-update
  (fn [{:keys [db]} [_ {:keys [result job]} job-id]]
    (if (get-in db [::jobs job-id :stop-polling])
      {:db (util/dissoc-in db [::jobs job-id])}
      (let [prev-state       (get-in db [::jobs job-id :job])
            sub-task-updates (sub-task-updates job prev-state)
            status           (:status job)
            running?         (= status "running")
            update?          (= result "update")
            timeout?         (= result "timeout")]

        ;; Job done => do callbacks
        (when-not (empty? sub-task-updates)
          (run-callbacks db job sub-task-updates))

        {:db (cond
               ;; Job finished => remove from db
               (terminal-statuses status) (util/dissoc-in db [::jobs job-id])
               ;; Job has been updated => Store new job state to app-db
               (and update? job) (assoc-in db [::jobs job-id :job] job)
               :else             db)
         :fx [(when (or timeout? running?)
                ;; Job is not done yet => Keep polling
                [:dispatch [::poll job-id]])]}))))

(rf/reg-event-fx ::handle-job-error
  (fn [{:keys [db]} [_ _ job-id]]
    (run-callbacks db {:status "error" :id job-id} nil)))

(rf/reg-event-fx ::poll
  (fn [{:keys [db]} [_ job-id]]
    (when-let [job (get-in db [::jobs job-id :job])]
      (let [version (:version job)
            id      (router/path-parameter db :app-id)]
        {:action/query {:name    :job-status
                        :params  (util/mmap id job-id version)
                        :success [::job-update job-id]
                        :error   [::handle-job-error job-id]}}))))

;; Starts polling for job updates. Callback function is called every time
;; a sub-task status updates
(rf/reg-event-fx ::start-polling
  (fn [{:keys [db]} [_ job-id callback]]
    (let [existing-job (get-in db [::jobs job-id])]
      (if existing-job
        {:db (-> db
                 (update-in [::jobs job-id :callback-fns] conj callback)
                 (util/dissoc-in [::jobs job-id :stop-polling]))}
        ;; Polling is started with version -1 so that the first update comes immediately
        (let [new-job {:id job-id :version -1}]
          {:db       (assoc-in db [::jobs job-id] {:job          new-job
                                                   :callback-fns [callback]})
           :dispatch [::poll job-id]})))))

(rf/reg-event-fx ::stop-polling
  (fn [{:keys [db]} [_ job-id]]
    {:db (assoc-in db [::jobs job-id :stop-polling] true)}))


;; Subscriptions

(rf/reg-sub ::jobs :-> ::jobs)

(rf/reg-sub ::job
  :<- [::jobs]
  (fn [jobs [_ job-id]]
    (get-in jobs [job-id :job])))

(rf/reg-sub ::job-status
  (fn [[_ job-id]]
    (rf/subscribe [::job job-id]))
  (fn [job _]
    (:status job)))

(rf/reg-sub ::sub-task
  (fn [[_ job-id _sub-task]]
    (rf/subscribe [::job job-id]))
  (fn [job [_ _job-id sub-task]]
    (get-in job [:value (keyword sub-task)])))

(rf/reg-sub ::sub-task-status
  (fn [[_ job-id sub-task]]
    (rf/subscribe [::sub-task job-id sub-task]))
  (fn [sub-task _]
    (:status sub-task)))

(rf/reg-sub ::is-finished?
  (fn [[_ job-id sub-task]]
    (if sub-task
      (rf/subscribe [::sub-task-status job-id sub-task])
      (rf/subscribe [::job-status job-id])))
  (fn [status _]
    (boolean (terminal-statuses status))))

(rf/reg-sub ::in-progress?
  (fn [[_ job-id sub-task]]
    (if sub-task
      (rf/subscribe [::sub-task-status job-id sub-task])
      (rf/subscribe [::job-status job-id])))
  (fn [status _]
    (boolean (when status
               (not (terminal-statuses status))))))
