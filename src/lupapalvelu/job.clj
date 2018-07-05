(ns lupapalvelu.job
  (:refer-clojure :exclude [update])
  (:require [slingshot.slingshot :refer [throw+]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer [now]])
  (:import [org.bson.types ObjectId]
           [java.util Date]))

(defn check-status [data]
  (if (every? #{:done :error} (map #(get-in % [:status]) (vals data)))
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
         (map (fn [[k v]] [(name k) v]))
         (into {})
         (assoc job :value))))

(defn- find-job [^String id]
  (or (-> (mongo/by-id :jobs (ObjectId. id))
          stringify-keys)
      (throw+ {:error :not-found :message (str "unknown job: id=" id)})))

(defn- trim [job]
  (when job
    (-> (select-keys job [:id :version :status :value])
        (update-in [:id] str))))

(defn- store-in-db [job]
  (mongo/insert :jobs job))

(defn start [initial-value]
  (let [job (create-job initial-value)]
    (store-in-db job)
    (trim job)))

(defn update [id f & args]
  (let [old-job (find-job id)
        new-value (apply f (:value old-job) args)
        new-job (assoc old-job :version (inc (:version old-job))
                               :value new-value
                               :status (check-status new-value))]
    (mongo/update-by-id :jobs (:id old-job) {$set (dissoc new-job :id)})
    (:version new-job)))

(def query-spacing 800)

(defn- wait-for-job-update [^String id version timeout start-ts]
  (if-let [updated (->> (mongo/find-maps :jobs {:_id (ObjectId. id)
                                                :version {$gt version}})
                        (map mongo/with-id)
                        first)]
    (trim updated)
    (when (< (- (now) start-ts) timeout)
      (Thread/sleep query-spacing)
      (wait-for-job-update id version timeout start-ts))))

(defn status [id version timeout]
  (let [job (find-job id)]
    (if (<= version (:version job))
      {:result :update :job (trim job)}
      (if-let [job (wait-for-job-update id version timeout (now))]
        {:result :update :job job}
        {:result :timeout}))))
