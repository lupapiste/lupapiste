(ns lupapalvelu.job
  (:refer-clojure :exclude [update])
  (:require [slingshot.slingshot :refer [throw+]]))

(defonce jobs (ref {}))
(defonce job-id (atom 0))
(defonce deliverer (agent nil))

(defn- next-job-id []
  (str (swap! job-id inc)))

(defn- delivered [value]
  (doto (promise)
    (deliver value)))

(defn- create-job [id initial-value status-fn]
  {:id id
   :version 0
   :value initial-value
   :status (status-fn initial-value)
   :status-fn status-fn
   :next (promise)})

(defn- find-job [id]
  (or (get @jobs id) (throw+ {:error :not-found :message (str "unknown job: id=" id)})))

(defn- trim [job]
  (when job
    (select-keys job [:id :version :status :value])))

(defn start [initial-value status-fn]
  (let [id (next-job-id)
        job (create-job id initial-value status-fn)]
    (dosync
      (alter jobs assoc id job))
    (trim job)))

(defn- job-changed [_ old-job new-job]
  (deliver (:next old-job) (trim new-job)))

(defn update [id f & args]
  (dosync
    (let [old-job (find-job id)
          new-value (apply f (cons (:value old-job) args))
          new-job (assoc old-job :version (inc (:version old-job))
                         :value new-value
                         :status ((:status-fn old-job) new-value)
                         :next (promise))]
      (alter jobs assoc id new-job)
      (send deliverer job-changed old-job new-job)
      (:version new-job))))

(defn- get-update-promise [id version]
  (let [job (find-job id)]
    (if (= version (:version job))
      (:next job)
      (delivered job))))

(defn status [id version timeout]
  (let [v (deref (get-update-promise id version) timeout :timeout)]
    (if (= v :timeout)
      {:result :timeout}
      {:result :update :job (trim v)})))
