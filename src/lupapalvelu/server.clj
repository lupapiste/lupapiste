(ns lupapalvelu.server
  (:use lupapalvelu.log)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.web]
            [lupapalvelu.vetuma]
            [lupapalvelu.env :as env]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.fixture.full]
            [lupapalvelu.fixture.kind]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.action]
            [lupapalvelu.mongo :as mongo])
  (:gen-class))

(defn -main [& args]
  (info "Server starting")
  (mongo/connect!)
  (env/in-dev
    (warn "*** Starting development services ***")
    (fixture/apply-fixture "minimal")
    (nrepl/start-server :port 9000))
  (server/start env/port {:mode env/mode
                          :jetty-options {:ssl? true
                                          :ssl-port 8443
                                          :keystore "./resources/keystore"
                                          :key-password "lupapiste"}
                          :ns 'lupapalvelu.web})
  (info "Server running"))
