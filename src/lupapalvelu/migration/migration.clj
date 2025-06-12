(ns lupapalvelu.migration.migration
  (:require [clojure.pprint :refer [pprint]]
            [clojure.stacktrace :refer [print-cause-trace]]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.logging :refer [errorf info infof]]
            [lupapalvelu.migration.core :refer [execute-migration! migration-history migrations unexecuted-migrations]]
            [lupapalvelu.migration.migrations]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [def-]]
            [slingshot.slingshot :refer [throw+ try+]])
  (:import [java.text SimpleDateFormat]
           [java.util Date]))

(set! *warn-on-reflection* true)

(def- ^SimpleDateFormat time-formatter (SimpleDateFormat. "yyyy/MM/dd HH:mm:ss"))

(defn- time->str [^long t]
  (->> t Date. (.format time-formatter)))

(defn show-help []
  (println "Migrations commands:")
  (println "  list ............... List known migrations")
  (println "  hist ............... Show migration executions")
  (println "  hist -l ............ Show migration executions in long format")
  (println "  update ............. Execute migrations that have not been executed successfully")
  (println "  count .............. Count migrations that have not been executed successfully, returned as exit code")
  (println "  run [name...] ...... Execute migrations by name(s)"))

(defn rtfm []
  (info "What? I dont even...")
  (show-help)
  1)

(defn list-migrations []
  (doseq [m (sort-by :id (vals @migrations))]
    (info (:name m)))
  (flush))

(def- status {true "SUCCESS" false "FAIL"})

(defn show-history [long-format]
  (doseq [r (migration-history)]
    (infof "%s: %s: %s%n" (time->str (:time r)) (:name r) (status (:ok r)))
    (when long-format
      (if (:ok r)
        (pprint (:result r))
        (info (:error r)))
      (info))))

(defn run-migration! [migration-name]
  (infof "Executing migration '%s': " migration-name)
  (let [result (execute-migration! migration-name)]
    (if (:ok result)
      (info "Successful:" (:result result))
      (do
        (errorf "Failure: %s" result)
        (throw+ result))))
  (flush))

(defn run-migrations! [migration-names]
  (when (seq migration-names)
    (lockdown/in-lockdown
      (try+
        (run! run-migration! migration-names)
        (info "All migrations executed successfully")
      (catch string? message (info "Execution terminated by failure:" message) 1)
      (catch map? _ (info "Migration execution failure") 1)
      (catch Exception e (info "Execution terminated by failure") (print-cause-trace e) 1)))))

(defn update-or-die! []
  (when-let [migrations (seq (map :name (unexecuted-migrations)))]
    (lockdown/in-lockdown
      (dorun (map run-migration! migrations)))))

(defn update! []
  (run-migrations! (map :name (unexecuted-migrations))))

(defn migration-results-summary-by-name []
  (map
    (fn [results] {:name (:name (first results)), :ok (some :ok results)})
    (->> (mongo/select :migrations {} {:name 1, :ok 1}) (sort-by :name) (partition-by :name))))

(defn all-ok? [] (every? :ok (migration-results-summary-by-name)))

(defn failing-migrations [] (map :name (remove :ok (migration-results-summary-by-name))))

(defn count! []
  (System/exit (count (unexecuted-migrations))))
