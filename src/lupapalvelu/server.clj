(ns lupapalvelu.server
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [taoensso.timbre :as timbre :refer [trace debug info warn error fatal tracef debugf infof warnf errorf fatalf]]
            [noir.core :refer [defpage]]
            [noir.server :as server]
            [noir.response :as response]
            [ring.middleware.session.cookie :as session]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.security-headers :as headers]
            [sade.email :as email]
            [sade.dummy-email-server]
            [sade.util :as util]
            [lupapalvelu.logging]
            [lupapalvelu.web :as web]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.ua-compatible-header :as uach]
            [lupapalvelu.migration.migration :as migration]
            [lupapalvelu.perf-mon :as perf-mon]
            [scss-compiler.core :as scss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.vetuma]
            [lupapalvelu.fixture.fixture-api]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.document.document-api]
            [lupapalvelu.prev-permit-api]
            [lupapalvelu.user-api]
            [lupapalvelu.mml.yhteystiedot-api]
            [lupapalvelu.operations]
            [lupapalvelu.attachment-api]
            [lupapalvelu.authorization-api]
            [lupapalvelu.comment-api]
            [lupapalvelu.statement-api]
            [lupapalvelu.tasks-api]
            [lupapalvelu.verdict-api]
            [lupapalvelu.notice-api]
            [lupapalvelu.company-api]
            [lupapalvelu.onnistuu-api]
            [lupapalvelu.kopiolaitos-api]
            [lupapalvelu.admin-api]
            [lupapalvelu.proxy-services]
            [lupapalvelu.exports-api]
            [lupapalvelu.document.schema-repository-api]
            [lupapalvelu.actions-api]
            [lupapalvelu.screenmessage-api]
            [lupapalvelu.integrations-api]
            [lupapalvelu.construction-api]
            [lupapalvelu.asianhallinta-config-api]
            [lupapalvelu.perf-mon-api]
            [lupapalvelu.user-notification-api]
            [lupapalvelu.tiedonohjaus-api]
            [lupapalvelu.application-tabs-api]
            [lupapalvelu.application-bulletins-api]))

(defonce jetty (atom nil))

(defn- init! []
  (mongo/connect!)

  (migration/update!)
  (when-let [failures (seq (migration/failing-migrations))]
    (let [msg (format "%s build %s (%s)\nFailing migration(s): %s"
                env/target-env
                (:build-number env/buildinfo)
                (:hg-branch env/buildinfo)
                (s/join failures))]
      (email/send-email-message "lupapalvelu@solita.fi" "Critical: Migration failure!" [msg msg])))

  (mongo/ensure-indexes)
  (server/add-middleware web/tempfile-cleanup)
  (server/add-middleware i18n/lang-middleware)
  (server/add-middleware web/parse-json-body-middleware)
  (server/add-middleware uach/add-ua-compatible-header)
  (server/add-middleware headers/session-id-to-mdc)
  (server/add-middleware headers/add-security-headers)
  (server/add-middleware web/anti-csrf)
  (server/add-middleware web/wrap-authentication)
  (server/add-middleware web/session-timeout)
  (env/in-dev
   (server/add-middleware mongo/db-selection-middleware))

  (when-let [gemsdir (io/resource "gems")]
    (scss/initialize :gempath (.getPath gemsdir)))

  (info "*** Instrumenting performance monitoring")
  (perf-mon/init)
  (when (env/feature? :nrepl)
    (warn "*** Starting nrepl in port 9090")
    (require 'clojure.tools.nrepl.server)
    ((resolve 'clojure.tools.nrepl.server/start-server) :port 9090)))

(defn read-session-key []
  {:post [(or (nil? %) (= (count %) 16))]}
  (let [keyfile (io/file "sessionkey")]
    (when (.exists keyfile)
      (with-open [in (io/input-stream keyfile)
                  out (java.io.ByteArrayOutputStream.)]
        (io/copy in out)
        (into-array Byte/TYPE (take 16 (.toByteArray out)))))))

(defn- start-jetty! []
  (if (nil? @jetty)
    (let [jetty-opts (into
                       {:max-threads 250}
                       (when (env/feature? :ssl)
                         {:ssl? true
                          :ssl-port 8443
                          :keystore "./keystore"
                          :key-password "lupapiste"}))
          noir-opts {:mode env/mode
                     :ns 'lupapalvelu.web
                     :jetty-options jetty-opts
                     :session-store (session/cookie-store {:key (read-session-key)})
                     :session-cookie-attrs (env/value :cookie)}
          starting  (double (now))
          jetty-instance ^org.eclipse.jetty.server.Server (server/start env/port noir-opts)]
      (.setStopTimeout jetty-instance 10000)
      (reset! jetty jetty-instance)
      (infof "Jetty startup took %.3f seconds" (/ (- (now) starting) 1000))
      "server running")
    (warn "Server already started!")))

(defn- stop-jetty! []
  (when-not (nil? @jetty) (swap! jetty server/stop)))

(defpage "/internal/hot-restart" []
  (if (#{"127.0.0.1" "0:0:0:0:0:0:0:1"} (:remote-addr (noir.request/ring-request)))
    (do
      (util/future*
        (Thread/yield)
        (env/reload!)
        (i18n/reload!)
        (info "Reloaded env and i18n, restarting Jetty...")
        (stop-jetty!)
        (mongo/disconnect!)
        (mongo/connect!)
        (start-jetty!))
      (response/status 200 "OK"))
    (response/status 401 "Unauthorized")))

(defpage "/internal/reload" []
  (if (#{"127.0.0.1" "0:0:0:0:0:0:0:1"} (:remote-addr (noir.request/ring-request)))
    (do
      (env/reload!)
      (i18n/reload!)
      (info "Reloaded env and i18n.")
      (response/status 200 "OK"))
    (response/status 401 "Unauthorized")))

(defn -main [& _]
  (infof "Build %s starting in %s mode" (:build-number env/buildinfo) (name env/mode))
  (infof "Running on %s version %s (%s) [%s], trustStore is %s"
    (System/getProperty "java.vm.name")
    (System/getProperty "java.runtime.version")
    (System/getProperty "java.vm.info")
    (if (java.awt.GraphicsEnvironment/isHeadless) "headless" "headful")
    (System/getProperty "javax.net.ssl.trustStore"))
  (info "Running on Clojure" (clojure-version))

  (init!)
  (start-jetty!))

"server ready to start"
