(ns lupapalvelu.server
  (:use lupapalvelu.log)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.web]
            [lupapalvelu.env :as env]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.fixture.full]
            [lupapalvelu.fixture.kind]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.action]
            [lupapalvelu.mongo :as mongo])
  (:gen-class))

(defn start-server [port mode]
  (server/start port {:mode mode :ns 'lupapalvelu.web}))

(defn stop-server [server]
  (server/stop server))

(defn -main [& args]
  (info "Server starting")
  (mongo/connect! mongo/mongouri)
  (env/in-dev
    (warn "*** Applying test fixture")
    (fixture/apply-fixture "minimal")
    (warn "*** Starting nrepl")
    (nrepl/start-server :port 9000))
  (start-server env/port env/mode)
  (info "Server running"))
