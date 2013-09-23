(ns lupapalvelu.smoketest.lupamonster
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.smoketest.core :refer :all]
            [lupapalvelu.smoketest.application-tests]))

(defn -main [& args]
  (when @mongo/connected
    (println "Warning: disconnecting current MongoDB connection!")
    (mongo/disconnect!))

  (mongo/connect! "lupaci.solita.fi" 27018)

  (let [started-from-cli (find-ns 'lupapalvelu.main)
        results (execute-all-tests)
        all-ok  (reduce
                  (fn [ok [test-name v]]
                    (printf "%-40s %s\n" (str test-name ":") (if (= :ok v) "OK" (str "FAIL:" v))) (flush)
                    (and ok (= :ok v)))
                  true results)]
    (when (and started-from-cli (not all-ok)) (System/exit 1))))
