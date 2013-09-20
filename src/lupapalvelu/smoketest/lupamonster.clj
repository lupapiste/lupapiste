(ns lupapalvelu.smoketest.lupamonster
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.smoketest.core :refer :all]))

(defn -main [& args]
  (if-not @mongo/connected
    (do
      (mongo/connect! "lupaci.solita.fi" 27018)
      (let [report (execute-all-tests)]
        (doseq [[test-name v] report]
          (printf "%-40s %s\n" (str test-name ":") (if (= :ok v) "OK" (str "FAIL:" v))))
        )
      (mongo/disconnect!))
    (println "ERROR: Already connected to a MongoDB!")))
