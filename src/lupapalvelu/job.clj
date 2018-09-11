(ns lupapalvelu.job
  (:refer-clojure :exclude [update])
  (:require [slingshot.slingshot :refer [throw+]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer [now]])
  (:import [org.bson.types ObjectId]
           [java.util Date]))

(defn check-status [data]
  (if (every? #{:done :error} (map #(keyword (get-in % [:status])) (vals data)))
    :done
    :running))

(defn- create-job [initial-value]
  {:id      (ObjectId.)
   :version 0
   :value   initial-value
   :status  (check-status initial-value)
   :created (Date.)})

(defn- stringify-keys [{:keys [value] :as job}]
  (when job
    (->> value
         (reduce-kv #(assoc %1 (mongo/unescape-key (name %2)) %3) {})
         (assoc job :value))))

(defn- find-job [^String id]
  (or (-> (mongo/by-id :jobs (ObjectId. id))
          stringify-keys)
      (throw+ {:error :not-found :message (str "unknown job: id=" id)})))

(defn- trim [job]
  (when job
    (-> (select-keys job [:id :version :status :value])
        (update-in [:id] str))))

(defn- store-in-db [{:keys [value] :as job}]
  (->> (if (map? value)
         (assoc job :value (reduce-kv #(assoc %1 (mongo/escape-key %2) %3) {} value))
         job)
       (mongo/insert :jobs)))

(defn start [initial-value]
  (let [job (create-job initial-value)]
    (store-in-db job)
    (trim job)))

(defn update [id f & args]
  (let [old-job (find-job id)
        new-value (apply f (:value old-job) args)
        new-job (assoc old-job :version (inc (:version old-job))
                               :value (if (map? new-value)
                                        (reduce-kv #(assoc %1 (mongo/escape-key (name %2)) %3) {} new-value)
                                        new-value)
                               :status (check-status new-value))]
    (mongo/update-by-id :jobs (:id old-job) {$set (dissoc new-job :id)})
    (:version new-job)))

(defn update-by-id [job-id sub-task-id sub-task-status]
  (or (let [new-job (mongo/update-one-and-return :jobs
                                                 {:_id (ObjectId. job-id)}
                                                 {$set {(str "value." (mongo/escape-key sub-task-id)) sub-task-status}
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

(def query-spacing 800)

(defn- wait-for-job-update [^String id version timeout start-ts]
  (if-let [updated (->> (mongo/find-maps :jobs {:_id (ObjectId. id)
                                                $or [{:version {$gt version}}
                                                     {:status "done"}]})
                        (map mongo/with-id)
                        first
                        stringify-keys)]
    (trim updated)
    (when (< (- (now) start-ts) timeout)
      (Thread/sleep query-spacing)
      (wait-for-job-update id version timeout start-ts))))

(defn status [id version timeout]
  (if-let [job (wait-for-job-update id version timeout (now))]
    {:result :update :job job}
    {:result :timeout}))
