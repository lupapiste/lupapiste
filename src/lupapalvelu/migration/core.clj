(ns lupapalvelu.migration.core
  (:require [monger.collection :as mc]
            [monger.query :as q]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.stacktrace :refer [print-cause-trace]]))

(defn now [] (java.lang.System/currentTimeMillis))

(defonce migrations (atom {}))
(defonce migration-order (atom 0))

(defn- ->assertion  [body]
  `(fn [] ~@(map #(cons 'assert (list %)) body)))

(defmacro defmigration
  "TODO: doc"
  [migration-name & body]
  (let [name-str       (name migration-name)
        order          (swap! migration-order inc)
        has-opts?      (and (map? (first body)) (> (count body) 1))
        {:keys [pre post apply-when]} (when has-opts? (first body))
        pre            (->assertion pre)
        post           (->assertion post)
        body           (if has-opts? (rest body) body)]
    `(swap! migrations assoc ~name-str {:name ~name-str
                                        :order ~order
                                        :pre ~pre
                                        :post ~post
                                        :apply-when (when (quote ~apply-when) (fn [] ~apply-when))
                                        :fn (fn [] (do ~@body))})))

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

(defn- execute-migration [m]
  (try+
    (if (or (nil? (:apply-when m)) (call-execute :apply-when m))
      (do
        (call-execute :pre m)
        (let [result (call-execute :fn m)]
          (when (call-execute :apply-when m)
            (throw+ {:ok false :error "migration execution did not change result of apply-when"}))
          (call-execute :post m)
          {:ok true :result result}))
      {:ok true :result "execution not needed"})
    (catch map? e
      e)))

(defn execute-migration! [migration-name]
  (if-let [m (@migrations migration-name)] 
    (when-let [result (execute-migration m)]
      (let [record (assoc result :name (:name m) :time (now))]
        (mc/insert :migrations record)
        record))
    {:ok false :error "unknown migration"}))

(defn unexecuted-migrations []
  (let [all-migrations (sort-by :order (vals @migrations))
        executed-migration-names (set (map :name (filter :ok (migration-history))))]
    (filter (comp (complement executed-migration-names) :name) all-migrations)))
