(ns lupapalvelu.server
  (:use lupapalvelu.log)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.web]
            [lupapalvelu.env :as env]
            [lupapalvelu.mongo :as mongo])
  (:gen-class))

(defn -main [& m]
  (info "Server starting")
  (env/in-dev
    (mongo/init)
    (nrepl/start-server :port 9000))
  (server/start env/port {:mode env/mode :ns 'lupapalvelu.web})
  (info "Server running"))
