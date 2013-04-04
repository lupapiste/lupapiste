(ns lupapalvelu.server
  (:use clojure.tools.logging)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.logging]
            [lupapalvelu.web :as web]
            [lupapalvelu.vetuma]
            [sade.env :as env]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.fixture.kind]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.fixture.municipality-test-users]
            [lupapalvelu.action]
            [lupapalvelu.admin]
            [lupapalvelu.application]
            [lupapalvelu.authority-admin]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.commands]
            [lupapalvelu.user]
            [lupapalvelu.operations]
            [lupapalvelu.proxy-services]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.ua-compatible-header :as uach]
            [sade.security-headers :as headers]))

(defn -main [& _]
  (infof "Server starting in %s mode" env/mode)
  (infof "Running on %s version %s (%s) [%s], trustStore is %s"
    (System/getProperty "java.vm.name")
    (System/getProperty "java.runtime.version")
    (System/getProperty "java.vm.info")
    (if (java.awt.GraphicsEnvironment/isHeadless) "headless" "headful")
    (System/getProperty "javax.net.ssl.trustStore"))

  (info "Running on Clojure" (clojure-version))
  (mongo/connect!)
  (mongo/ensure-indexes)
  (server/add-middleware uach/add-ua-compatible-header)
  (server/add-middleware headers/session-id-to-mdc)
  (server/add-middleware headers/add-security-headers)
  (server/add-middleware web/anti-csrf)
  (server/add-middleware web/apikey-authentication)
  (server/add-middleware i18n/lang-middleware)
  (env/in-dev
    (warn "*** Instrumenting performance monitoring")
    (require 'lupapalvelu.perf-mon)
    ((resolve 'lupapalvelu.perf-mon/init)))
    (warn "*** Starting nrepl")
    (nrepl/start-server :port 9000)
    (when (env/value [:email :dummy-server])
      (require 'sade.dummy-email-server)
      ((resolve 'sade.dummy-email-server/start))))
  (with-logs "lupapalvelu"
    (server/start env/port {:mode env/mode
                            :ns 'lupapalvelu.web
                            :jetty-options (if (env/dev-mode?)
                                             {:ssl? true
                                              :ssl-port 8443
                                              :keystore "./keystore"
                                              :key-password "lupapiste"}
                                             {})
                            :session-cookie-attrs (:cookie env/config)}))
  "ok")

(comment
  (-main))
