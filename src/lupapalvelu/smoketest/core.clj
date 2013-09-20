(ns lupapalvelu.smoketest.core)

(defonce tests (atom {}))

(defn execute-test! [test-name]
  (if-let [m (@tests test-name)]
    (if-let [result ((:fn m))]
      (if (and (map? result) (contains? result :ok))
        result
        {:ok false :error "invalid test: must return a map with :ok key or nil"})
      {:ok true})
    {:ok false :error "unknown test"}))

(defmacro defmonster
  "Defines a smoke test. First argument is the name, followed by the body of actual test.
   Test body must return a map with :ok key."
  [test-name & body]
  (let [name-str (name test-name)]
    `(do
       (swap! tests assoc ~name-str {:name ~name-str
                                     :fn (fn [] (do ~@body))})
       (defn ~test-name []
         (execute-test! ~(name name-str))))))
