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
  (if (= :dev env/mode)
    (do
      (mongo/init)
      (nrepl/start-server :port 9000)))
  (server/start env/port {:mode env/mode :ns 'lupapalvelu.web})
  (info "Server running"))
