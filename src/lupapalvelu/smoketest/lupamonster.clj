(ns lupapalvelu.smoketest.lupamonster
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.cmoketest.core :refer :all]))

(defn -main [& args]
  (mongo/connect! "lupaci.solita.fi" 27018)
  
  )
