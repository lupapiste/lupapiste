(ns lupapalvelu.smoketest.lupamonster
  (:require [lupapiste.mongocheck.core :as mongocheck]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.smoketest.core :refer :all]
            [lupapalvelu.smoketest.application-smoke-tests]
            [lupapalvelu.smoketest.assignment-smoke-tests]
            [lupapalvelu.smoketest.organization-smoke-tests]
            [lupapalvelu.smoketest.user-smoke-tests]))

(defmonster mongochecks
  (let [results (mongocheck/execute-checks (mongo/get-db))]
    (if (->> results vals (filter seq) seq)
      {:ok false :results (str results)}
      {:ok true})))

(defn- print-info [{:keys [name test-ok test-duration-ms]}]
  (let [minutes (int (/ test-duration-ms (* 60 1000)))
        seconds (int (/ (- test-duration-ms (* minutes (* 60 1000))) 1000))]
    (printf "%-50s %-10s %d min %d sec\n"
      (str "\"" name "\":")
      (if (= :ok test-ok) "OK" (str "FAIL: " test-ok))
      minutes
      seconds)
    (flush)))

(defn -main [& args]
  (when @mongo/connection
    (println "Warning: disconnecting current MongoDB connection!")
    (mongo/disconnect!))

  (mongo/connect!)

  (let [started-from-cli (find-ns 'lupapalvelu.main)
        results (apply execute-tests args)
        all-ok  (reduce
                  (fn [ok test-result-info]
                    (print-info test-result-info)
                    (and ok (= :ok (:test-ok test-result-info))))
                  true
                  results)]
    (println "Tests done, disconnecting")
    (mongo/disconnect!)
    (when (and started-from-cli (not all-ok)) (System/exit 1))))
