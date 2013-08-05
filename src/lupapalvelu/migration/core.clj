(ns lupapalvelu.migration.core
  (:require [monger.collection :as mc]
            [monger.query :as q]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.stacktrace :refer [print-cause-trace]]))

(defn now [] (java.lang.System/currentTimeMillis))

(defonce migrations (atom {}))

(defmacro defmigration [migration-name & body]
  (let [has-opts?                      (map? (first body))
        {:keys [pre post apply-when]}  (when has-opts? (first body))
        body                           (if has-opts? (rest body) body)]
    `(let [name-str#   (name (quote ~migration-name))
           pre#        (when (quote ~pre) (fn [] (assert ~pre)))
           post#       (when (quote ~post) (fn [] (assert ~post)))
           apply-when# (when (quote ~apply-when) (fn [] ~apply-when))]
       (swap! migrations assoc name-str# {:name name-str#
                                          :pre pre#
                                          :post post#
                                          :apply-when apply-when#
                                          :fn (fn [] (do ~@body))})
       nil)))

(defn migration-history []
  (q/with-collection "migrations"
    (q/sort {:time 1})))

(def ^:private execution-name {:pre "pre-condition"
                               :post "post-condition"
                               :apply-when "apply-when"
                               :fn "execution"})

(defn- call-execute [execution-type m]
  (when-let [f (execution-type m)]
    (try
      (f)
      (catch Throwable e
        (throw+ {:ok false :error (str (execution-name execution-type) " failed: " (with-out-str (print-cause-trace e)))})))))

(defmacro dbg [msg & body]
  `(do
     (println ~msg)
     (let [r# (do ~@body)]
       (clojure.pprint/pprint r#)
       r#)))

(defn- execute-migration [m]
  (try+
    (when (or (nil? (:apply-when m)) (call-execute :apply-when m))
      (call-execute :pre m)
      (let [result (call-execute :fn m)]
        (when (call-execute :apply-when m)
          (throw+ {:ok false :error "migration execution did not change result of apply-when"}))
        (call-execute :post m)
        {:ok true :result result}))
    (catch map? e
      e)))

(defn execute-migration! [m]
  (assert m)
  (when-let [result (execute-migration m)]
    (let [record (assoc result :name (:name m) :time (now))]
      (mc/insert :migrations record)
      record)))

(defn unexecuted-migrations []
  (let [all-migrations (sort-by :id (vals @migrations))
        executed-migration-names (set (map :name (filter :ok (migration-history))))]
    (filter (comp (complement executed-migration-names) :name) all-migrations)))
