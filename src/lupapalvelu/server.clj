(ns lupapalvelu.server
  (:use lupapalvelu.log)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.web]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.env :as env])
  (:gen-class))

(defn -main [& m]
  (info "Server starting")
  (mongo/init)
  (if (= :dev env/mode) (nrepl/start-server :port 9000))
  (server/start env/port {:mode env/mode :ns 'lupapalvelu.web})
  (info "Server running"))
