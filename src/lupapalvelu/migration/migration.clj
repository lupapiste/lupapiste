(ns lupapalvelu.migration.migration
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.migration.core :refer :all]
            [lupapalvelu.migration.migrations]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.pprint :refer [pprint]])
  (:import [java.text SimpleDateFormat]
           [java.util Date]))


(def time-formatter (SimpleDateFormat. "yyyy/MM/dd HH:mm:ss"))
(defn time->str [t]
  (->> t Date. (.format time-formatter)))

(defn show-help []
  (println "Migrations commands:")
  (println "  list ............... List known migrations")
  (println "  hist ............... Show migration executions")
  (println "  hist -l ............ Show migration executions in long format")
  (println "  update ............. Execute migrations that have not been executed")
  (println "  run [id, id...] .... Execute migrations with given ID's"))

(defn rtfm []
  (println "What? I dont even...")
  (show-help)
  1)

(defn list-migrations []
  (doseq [m (sort-by :id (vals @migrations))]
    (printf "%3d: %s%n" (:id m) (:name m)))
  (flush))

(def status {true  "SUCCESS"
             false "FAIL"})

(defn migration-name [id]
  (:name (migration-by-id id)))

(defn show-history [long-format]
  (doseq [r (migration-history)]
    (printf "%s: %3d: '%s' %s%n" (time->str (:time r)) (:id r) (migration-name (:id r)) (status (:ok r)))
    (when long-format
      (if (:ok r)
        (pprint (:result r))
        (println (:error r)))
      (println))))

(defn run-migration! [migration-name]
  (let [m (@migrations migration-name)]
    (when-not m (throw+ (str "unknwon migration name: '" migration-name "'")))
    (printf "Executing migration '%s' (%s)%n" (:name m) (:id m))
    (let [result (execute-migration! m)]
      (if (:ok result)
        (do
          (println "Successful:")
          (pprint (:result result))
          (println))
        (do
          (println "Failure")
          (println result)
          (throw+ result))))))

(defn run-migrations! [migration-names]
  (try+
    (dorun (map run-migration! migration-names))
    (println "All migrations executes successfully")
    (catch string? message
      (println "Execution terminated by failure:" message)
      1)
    (catch map? result
      (println "Migration execution failure")
      1)
    (catch Exception e
      (println "Execution terminated by failure")
      (print-cause-trace e)
      1)))

(defn -main [& [action & args]]
  (mongo/connect!)
  (cond
    (nil? action)       (show-help)
    (= action "list")   (list-migrations)
    (= action "hist")   (show-history (= "-l" (first args)))
    (= action "update") (run-migrations! (map :name (unexecuted-migrations)))
    (= action "run")    (if (seq args) (run-migrations! args) (rtfm))
    :else               (rtfm)))
