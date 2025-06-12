(ns lupapalvelu.smoketest.lupamonster
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongocheck.core :as mongocheck]
            [lupapalvelu.smoketest.application-smoke-tests]
            [lupapalvelu.smoketest.assignment-smoke-tests]
            [lupapalvelu.smoketest.core :refer :all]
            [lupapalvelu.smoketest.invoice-smoke-tests]
            [lupapalvelu.smoketest.organization-smoke-tests]
            [lupapalvelu.smoketest.statistics-entry-smoke-tests]
            [lupapalvelu.smoketest.user-smoke-tests]
            [lupapalvelu.smoketest.verdict-template-smoke-tests]
            [lupapalvelu.system.system-util :as system-util]
            [sade.env :as env]))

(set! *warn-on-reflection* true)

(defmonster mongochecks
  (let [results (mongocheck/execute-checks (mongo/get-db))]
    (if (->> results vals (filter seq) seq)
      {:ok false :results (str results)}
      {:ok true})))

(defn- print-info [{:keys [name test-ok test-duration-ms]}]
  (let [minutes (int (/ test-duration-ms (* 60 1000)))
        seconds (int (/ (- test-duration-ms (* minutes 60 1000)) 1000))]
    (printf "%-50s %-10s %d min %d sec\n"
            (str "\"" name "\":")
            (if (= :ok test-ok) "OK" (str "FAIL: " test-ok))
            minutes
            seconds)
    (flush)))

(defn -main [& args]
  (println "Smoketest starting in target-env: " env/target-env)
  (let [started-from-cli (find-ns 'lupapalvelu.main)
        all-ok           (system-util/with-mongo
                           (->> (apply execute-tests args)
                                (reduce
                                  (fn [ok test-result-info]
                                    (print-info test-result-info)
                                    (and ok (= :ok (:test-ok test-result-info))))
                                  true)))]
    (println "Tests done, disconnecting")
    (when (and started-from-cli (not all-ok)) (System/exit 1))))
