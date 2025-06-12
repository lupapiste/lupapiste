(ns lupapalvelu.job
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [slingshot.slingshot :refer [throw+]])
  (:import [java.util Date]
           [org.bson.types ObjectId]))

(set! *warn-on-reflection* true)

(defn check-status [data]
  (if (every? #{:done :error} (map #(keyword (get-in % [:status])) (vals data)))
    :done
    :running))

(defn- create-job [data initial-value]
  (merge {:id      (ObjectId.)
          :version 0
          :value   initial-value
          :status  (check-status initial-value)
          :created (Date.)}
         (when data {:data data})))

(defn- stringify-keys [{:keys [value] :as job}]
  (when job
    (->> value
         (reduce-kv #(assoc %1 (mongo/unescape-key (name %2)) %3) {})
         (assoc job :value))))

(defn find-job [^String id]
  (or (-> (mongo/by-id :jobs (ObjectId. id))
          stringify-keys)
      (throw+ {:error :not-found :message (str "unknown job: id=" id)})))

(defn- trim [job]
  (when job
    (-> (select-keys job [:id :version :status :value :data])
        (update-in [:id] str))))

(defn- store-in-db [{:keys [value] :as job}]
  (->> (if (map? value)
         (assoc job :value (reduce-kv #(assoc %1 (mongo/escape-key %2) %3) {} value))
         job)
       (mongo/insert :jobs)))

(defn start
  ([data initial-value]
   (let [job (create-job data initial-value)]
     (store-in-db job)
     (trim job)))
  ([initial-value]
   (start nil initial-value)))

(defn update-job [id f & args]
  (let [old-job   (find-job id)
        new-value (apply f (:value old-job) args)
        new-job   (assoc old-job :version (inc (:version old-job))
                         :value (if (map? new-value)
                                  (reduce-kv #(assoc %1 (mongo/escape-key (name %2)) %3) {} new-value)
                                  new-value)
                         :status (check-status new-value))]
    (mongo/update-by-id :jobs (:id old-job) {$set (dissoc new-job :id)})
    (:version new-job)))

(defn update-by-id
  "Updates the status for a lupapiste job's single subtask by replacing the old map with the new"
  [^String job-id sub-task-id sub-task-status]
  (or (let [new-job (mongo/update-one-and-return :jobs
                                                 {:_id (ObjectId. job-id)}
                                                 {$set {(util/kw-path "value" (mongo/escape-key sub-task-id))
                                                        sub-task-status}
                                                  $inc {:version 1}}
                                                 :fields [:version :value :status])]
        (-> (if (and (= :done (check-status (:value new-job)))
                     (not= :done (:status new-job)))
              (mongo/update-one-and-return :jobs
                                           {:_id (ObjectId. job-id)}
                                           {$set {:status :done}
                                            $inc {:version 1}}
                                           :fields [:version])
              new-job)
            :version))
      (throw+ {:error :not-found :message (str "unknown job: id=" job-id)})))

(defn merge-by-id
  "Updates the status for a lupapiste job's single subtask by merging the old map with the new"
  [job-id sub-task-name sub-task-status-map]
  (update-by-id job-id
                sub-task-name
                (-> (find-job job-id)
                    (get-in [:value sub-task-name])
                    (merge sub-task-status-map))))

(def ^:private query-spacing "wait period in ms between polling" 800)

(defn- wait-for-job-update [^String id version timeout start-ts]
  (if-let [updated (->> (mongo/find-maps :jobs {:_id (ObjectId. id)
                                                $or  [{:version {$gt version}}
                                                      {:status "done"}]})
                        (map mongo/with-id)
                        first
                        stringify-keys)]
    (trim updated)
    (when (< (- (now) start-ts) timeout)
      (Thread/sleep ^long query-spacing)
      (wait-for-job-update id version timeout start-ts))))

(defn status [id version timeout]
  (if-let [job (wait-for-job-update id version timeout (now))]
    {:result :update :job job}
    {:result :timeout}))


;; ---------------------------------------
;; Organization jobs
;; ---------------------------------------

(def ^:private boolstring
  "Either Boolean or true/false string."
  (sc/conditional
    string?  #"(?i)^true|false$"
    boolean? sc/Bool))

(sc/defschema OrgJobsQuery
  "Query definition for organization jobs."
  {:organizationId             ssc/NonBlankStr
   (sc/optional-key :jobIds)   [ssc/NonBlankStr]
   (sc/optional-key :types)    [ssc/NonBlankStr]
   ;; By default only the user's own jobs are returned
   (sc/optional-key :allUsers) boolstring
   ;; If true (default false) the query is internally repeated until
   ;; it returns values (or timeout). The timeout window is 55 seconds
   ;; from created. Polling frequency is hardcoded.
   (sc/optional-key :poll)     boolstring
   ;; Timestamp string. If polling, returns when the job is modified later than this. If
   ;; modified is not given, the polling is done against `:status` field.
   (sc/optional-key :modified) ssc/NonBlankStr})

(defn store-organization-job
  "Creates new job into mongo. Returns the job with its id as string."
  [organization-id user job-type initial-value & {:as data}]
  (let [job (assoc (create-job (not-empty data) initial-value)
                   :status :running
                   :organization organization-id
                   :user (usr/summary user)
                   :type job-type
                   :modified (now))]
    (store-in-db job)
    (update job :id str)))

(defn- polling? [data]
  (ss/=trim-i (str (:poll data)) "true"))

(defn- params->mongo-query [{:keys [user data]}]
  (let [{job-ids :jobIds types :types
         all?    :allUsers} data
        poll?               (polling? data)
        modified            (some-> data :modified date/timestamp)]
    (util/strip-nils (cond-> {:organization (:organizationId data)
                              :_id          (some->> (seq job-ids) (map #(ObjectId. (str %))) (hash-map $in))
                              :type         (some->> (seq types) (hash-map $in))
                              :user.id      (when-not all?
                                              (:id user))}
                       (and poll? modified)       (assoc :modified {$gt modified})
                       (and poll? (not modified)) (assoc :status {$ne :running})))))

(defn- make-query-fn [command]
  (let [q (params->mongo-query command)]
    (fn []
      (mapv #(update % :id str) (mongo/select :jobs q)))))

(def ^{:private true
       :doc     "55 seconds, so under one minute frontend timeout."}
  poll-timeout-ms (* 55 1000))

(defn query-organization-jobs
  "Returns jobs matching `command`. See `OrgJobsQuery` for parameters. When polling, returns
  empty vector on timeout."
  [{:keys [data created] :as command}]
  (let [q-fn (make-query-fn command)]
    (if-not (polling? data)
      (q-fn)
      (loop [xs (q-fn)]
        (cond
          (seq xs)         xs
          (> (- (now) created)
             poll-timeout-ms) []
          :else            (do
                             (Thread/sleep ^long query-spacing)
                             (recur (q-fn))))))))

(defn update-organization-job
  "Updates job with given changed fields map. The supported fields are `:value`, `:status`
  and `:data`. Returns true, if the job has been successfully updated."
  [job-id & {:as changes}]
  {:pre [job-id]}
  (some->> (select-keys changes [:value :status :data])
           not-empty
           (merge {:modified (now)})
           (hash-map $set)
           (mongo/update-by-query :jobs {:_id (ObjectId. (str job-id))})
           pos?))
