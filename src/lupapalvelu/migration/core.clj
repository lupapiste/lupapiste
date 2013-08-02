(ns lupapalvelu.migration.core
  (:require [monger.collection :as mc]
            [monger.query :as q]
            [lupapalvelu.core :refer [now]]
            [clojure.stacktrace :refer [print-cause-trace]]))

(defonce migration-id (atom 0))
(defonce migrations (atom {}))

(defmacro defmigration [migration-name & body]
  `(let [name-str# (name (quote ~migration-name))
         id#       (swap! migration-id inc)]
     (swap! migrations assoc name-str# {:id id# 
                                        :name name-str#
                                        :fn (fn [] (do ~@body))})))

(defn migration-by-id [id]
  (first (filter (comp (partial = id) :id) (vals @migrations))))

(defn migration-history []
  (q/with-collection "migrations"
    (q/sort {:time 1})))

(defn- execute-migration-fn [f]
  (try
    {:ok true :result (f)}
    (catch Exception e
      {:ok false :ex (with-out-str (print-cause-trace e))})))

(defn execute-migration! [m]
  (let [result (assoc (execute-migration-fn (:fn m)) :id (:id m) :time (now))]
    (mc/insert :migrations result)
    result))

(defn unexecuted-migrations []
  (let [all-migrations (sort-by :id (vals @migrations))
        executed-migration-ids (set (map :id (filter :ok (migration-history))))]
    (filter (comp (complement executed-migration-ids) :id) all-migrations)))
