(ns sade.status)

(defonce statuses (atom {}))

(defmacro defstatus [name & body]
  `(swap! statuses assoc (keyword ~name) (fn [] ~@body)))

(defn status []
  (let [results (into {}
                  (for [[k v] @statuses]
                    [k (try
                         (let [status (v)] {:ok (not (false? status)) :data status})
                         (catch Exception e {:ok false :data (str e)}))]))
        not-ok    (some #(-> % :ok not) (vals results))]
    {:ok (not not-ok)
     :data results}))
