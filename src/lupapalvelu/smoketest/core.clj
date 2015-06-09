(ns lupapalvelu.smoketest.core)

(defonce tests (atom {}))

(defn execute-test [test-name]
  (try
    (if-let [m (@tests test-name)]
      (if-let [result ((:fn m))]
        (if (and (map? result) (contains? result :ok))
          result
          {:ok false :error "invalid test: must return a map with :ok key or nil"})
        {:ok true})
      {:ok false :error "unknown test"})
    (catch Throwable e
      (.printStackTrace e)
      {:ok false :error (.getMessage e)})))

(defn execute-tests
  "Lets the monsters loose. Returns a map with test names as keys.
   Value is either :ok (if the test passes) or the failure report."
  [& test-names]
  (into {} (pmap (fn [name]
                 [name (let [result (execute-test name)]
                         (if (true? (:ok result))
                           :ok
                           (or (:results result) (:error result))))])
             (if (seq test-names)
               (filter #((set test-names) %) (keys @tests))
               (keys @tests)))))

(defmacro defmonster
  "Defines a smoke test. First argument is the name, followed by the body of actual test.
   Test body must return a map with :ok key."
  [test-name & body]
  (let [name-str (name test-name)]
    `(do
       (swap! tests assoc ~name-str {:name ~name-str
                                     :fn (fn [] (do ~@body))})
       (defn ~test-name []
         (execute-test ~(name name-str))))))
