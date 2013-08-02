(ns lupapalvelu.migration.core
  (:require [monger.collection :as mc]
            [monger.query :as q]
            [lupapalvelu.core :refer [now]]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.stacktrace :refer [print-cause-trace]]))

(defonce migration-id (atom 0))
(defonce migrations (atom {}))

(defmacro defmigration [migration-name & body]
  (let [has-opts? (map? (first body))
        opts      (when has-opts? (first body))
        pre       (or (:pre opts) 'true)
        post      (or (:post opts) 'true)
        body      (if has-opts? (rest body) body)]
    `(let [name-str# (name (quote ~migration-name))
           id#       (swap! migration-id inc)]
       (swap! migrations assoc name-str# {:id id# 
                                          :name name-str#
                                          :pre (fn [] (assert ~pre))
                                          :post (fn [] (assert ~post))
                                          :fn (fn [] (do ~@body))})
       nil)))

(defn migration-by-id [id]
  (first (filter (comp (partial = id) :id) (vals @migrations))))

(defn migration-history []
  (q/with-collection "migrations"
    (q/sort {:time 1})))

(def ^:private execution-name {:pre "pre-condition"
                               :fn "execution"
                               :post "post-condition"})

(defn- call-execute [execution-type m]
  (try
    ((execution-type m))
    (catch Throwable e
      (throw+ {:ok false :ex (str (execution-name execution-type) " failed: " (with-out-str (print-cause-trace e)))}))))

(defn- execute-migration [m]
  (try+
    (call-execute :pre m)
    (let [result {:ok true :result (call-execute :fn m)}]
      (call-execute :post m)
      result)
    (catch map? e
      e)))

(defn execute-migration! [m]
  (let [result (assoc (execute-migration m) :id (:id m) :time (now))]
    (mc/insert :migrations result)
    result))

(defn unexecuted-migrations []
  (let [all-migrations (sort-by :id (vals @migrations))
        executed-migration-ids (set (map :id (filter :ok (migration-history))))]
    (filter (comp (complement executed-migration-ids) :id) all-migrations)))
