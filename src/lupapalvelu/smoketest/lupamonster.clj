(ns lupapalvelu.smoketest.lupamonster
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.smoketest.core :refer :all]
            [lupapalvelu.smoketest.application-smoke-tests]
            [lupapalvelu.smoketest.user-smoke-tests]))


(defn -main [& args]
  (when @mongo/connected
    (println "Warning: disconnecting current MongoDB connection!")
    (mongo/disconnect!))

  (mongo/connect!)

  (let [started-from-cli (find-ns 'lupapalvelu.main)
        results (apply execute-tests args)
        all-ok  (reduce
                  (fn [ok [test-name v]]
                    (printf "%-50s %s\n" (str test-name ":") (if (= :ok v) "OK" (str "FAIL: " v))) (flush)
                    (and ok (= :ok v)))
                  true results)]
    (println "Tests done, disconnecting")
    (mongo/disconnect!)
    (when (and started-from-cli (not all-ok)) (System/exit 1))))
