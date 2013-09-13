(ns lupapalvelu.server
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal tracef debugf infof warnf errorf fatalf]]
            [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.logging]
            [lupapalvelu.web :as web]
            [lupapalvelu.vetuma]
            [sade.env :as env]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.fixture.kind]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.fixture.municipality-test-users]
            [lupapalvelu.fixture.finland-rakval]
            [lupapalvelu.fixture.cgi-test-users]
            [lupapalvelu.admin]
            [lupapalvelu.application]
            [lupapalvelu.authority-admin]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.commands]
            [lupapalvelu.user]
            [lupapalvelu.operations]
            [lupapalvelu.statement]
            [lupapalvelu.proxy-services]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.ua-compatible-header :as uach]
            [lupapalvelu.etag :as etag]
            [lupapalvelu.document.schema-repository]
            [sade.security-headers :as headers]
            [sade.dummy-email-server]
            [lupapalvelu.migration.migration :as migration]))

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
  (migration/update!)
  (server/add-middleware i18n/lang-middleware)
  (server/add-middleware web/parse-json-body-middleware)
  (server/add-middleware uach/add-ua-compatible-header)
  (server/add-middleware headers/session-id-to-mdc)
  (server/add-middleware headers/add-security-headers)
  (server/add-middleware web/anti-csrf)
  (server/add-middleware web/authentication)
  (server/add-middleware web/session-timeout)
  (server/add-middleware etag/if-none-match-build-number)
  (env/in-dev
    (warn "*** Instrumenting performance monitoring")
    (require 'lupapalvelu.perf-mon)
    ((resolve 'lupapalvelu.perf-mon/init)))
  (when (env/feature? :nrepl)
    (warn "*** Starting nrepl")
    (nrepl/start-server :port 9090))
  (let [jetty-opts (into
                     {:max-threads 250}
                     (when (env/dev-mode?)
                       {:ssl? true
                        :ssl-port 8443
                        :keystore "./keystore"
                        :key-password "lupapiste"}))]
    (server/start env/port {:mode env/mode
                            :ns 'lupapalvelu.web
                            :jetty-options jetty-opts
                            :session-cookie-attrs (env/value :cookie)}))
  "server running")

"server ready to start"
