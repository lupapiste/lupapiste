(ns lupapalvelu.server
  (:use lupapalvelu.log)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.web]
            [lupapalvelu.vetuma]
            [lupapalvelu.env :as env]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.fixture.kind]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.action]
            [lupapalvelu.admin] 
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.commands])
  (:gen-class))


(defn -main [& _]
  (info "Server starting")
  (info "Running on Java %s %s %s (%s)"
        (System/getProperty "java.vm.vendor")
        (System/getProperty "java.vm.name")
        (System/getProperty "java.runtime.version")
        (System/getProperty "java.vm.info"))
  (info "Running on Clojure %d.%d.%d"
        (:major *clojure-version*)
        (:minor *clojure-version*)
        (:incremental *clojure-version*))
  (mongo/connect!)
  (env/in-dev
    (warn "*** Starting development services ***")
    (fixture/apply-fixture "minimal")
    (nrepl/start-server :port 9000))
  (server/start env/port {:mode env/mode
                          :jetty-options {:ssl? true
                                          :ssl-port 8443
                                          :keystore "./keystore"
                                          :key-password "lupapiste"}
                          :ns 'lupapalvelu.web})
  (info "Server running"))
