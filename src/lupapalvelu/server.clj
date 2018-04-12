(ns lupapalvelu.server
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [trace debug info warn error fatal tracef debugf infof warnf errorf fatalf]]
            [noir.core :refer [defpage]]
            [noir.server :as server]
            [noir.response :as response]
            [ring.middleware.session.cookie :as session]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.security-headers :as headers]
            [sade.dummy-email-server]
            [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.actions-api]
            [lupapalvelu.admin-api]
            [lupapalvelu.appeal-api]
            [lupapalvelu.application-api]
            [lupapalvelu.application-bulletins-api]
            [lupapalvelu.application-options-api]
            [lupapalvelu.application-search-api]
            [lupapalvelu.application-tabs-api]
            [lupapalvelu.archiving-api]
            [lupapalvelu.asianhallinta-config-api]
            [lupapalvelu.assignment-api]
            [lupapalvelu.attachment-api]
            [lupapalvelu.attachment.bind-attachments-api]
            [lupapalvelu.authorization-api]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.calendars-api]
            [lupapalvelu.change-email-api]
            [lupapalvelu.comment-api]
            [lupapalvelu.company-api]
            [lupapalvelu.construction-api]
            [lupapalvelu.control-api :refer [defcontrol] :as control]
            [lupapalvelu.copy-application-api]
            [lupapalvelu.ddd-map-api]
            [lupapalvelu.document.document-api]
            [lupapalvelu.document.schema-repository-api]
            [lupapalvelu.email :as email]
            [lupapalvelu.exports-api]
            [lupapalvelu.features-api]
            [lupapalvelu.file-upload-api]
            [lupapalvelu.fixture.fixture-api]
            [lupapalvelu.fixture.fixtures]
            [lupapalvelu.foreman-api]
            [lupapalvelu.guest-api]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.info-links-api]
            [lupapalvelu.integrations-api]
            [lupapalvelu.inspection-summary-api]
            [lupapalvelu.kopiolaitos-api]
            [lupapalvelu.logging]
            [lupapalvelu.logging-api]
            [lupapalvelu.migration.migration :as migration]
            [lupapalvelu.mml.yhteystiedot-api]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.municipality-api]
            [lupapalvelu.neighbors-api]
            [lupapalvelu.notice-api]
            [lupapalvelu.oauth-api]
            [lupapalvelu.onnistuu-api]
            [lupapalvelu.operations]
            [lupapalvelu.open-inforequest-api]
            [lupapalvelu.organization-api]
            [lupapalvelu.pdf.pdf-export-api]
            [lupapalvelu.perf-mon :as perf-mon]
            [lupapalvelu.perf-mon-api]
            [lupapalvelu.prev-permit-api]
            [lupapalvelu.printing-order.printing-order-api]
            [lupapalvelu.property-api]
            [lupapalvelu.proxy-services]
            [lupapalvelu.reports.reports-api]
            [lupapalvelu.rest.docstore-api]
            [lupapalvelu.rest.rest-api]
            [lupapalvelu.screenmessage-api]
            [lupapalvelu.ssokeys-api]
            [lupapalvelu.statement-api]
            [lupapalvelu.suti-api]
            [lupapalvelu.tasks-api]
            [lupapalvelu.tiedonohjaus-api]
            [lupapalvelu.ua-compatible-header :as uach]
            [lupapalvelu.user-api]
            [lupapalvelu.user-notification-api]
            [lupapalvelu.verdict-api]
            [lupapalvelu.vetuma]
            [lupapalvelu.web :as web]
            [lupapalvelu.ya-digging-permit-api]
            [lupapalvelu.ya-extension-api]
            [lupapalvelu.admin-reports-api]
            [lupapalvelu.campaign-api]
            [lupapalvelu.digitizer-api]
            [lupapalvelu.pate.verdict-template-api]
            [lupapalvelu.pate.verdict-api]
            [lupapalvelu.pate.phrases-api]
            [lupapalvelu.financial-api]
            [lupapalvelu.premises-api]
            [lupapalvelu.onkalo-operations-api])

  (:import [javax.imageio ImageIO]
           [javax.activation MailcapCommandMap]
           [fi.lupapiste.jmx ServerFactory]))

(defonce jetty (atom nil))
(defonce jmx-server (atom nil))
(defonce nrepl-server (atom nil))

(defn- calendar-mime-type-setup []
  (let [mc (MailcapCommandMap/getDefaultCommandMap)]
      (.addMailcap mc "text/calendar;; x-java-content-handler=com.sun.mail.handlers.text_plain")
      (MailcapCommandMap/setDefaultCommandMap mc)))

(defn start-nrepl! []
  (when (env/feature? :nrepl)
    (require 'clojure.tools.nrepl.server)
    (let [start-server (resolve 'clojure.tools.nrepl.server/start-server)]
      (swap! nrepl-server
             (fn [old-server]
               (if (nil? old-server)
                 (let [port 9090
                       server (start-server :port port :bind "localhost")]
                   (warn "*** Started nrepl in port" port)
                   server)
                 (do
                   (warn "nrepl already started!")
                   old-server)))))))

(defn stop-nrepl! []
  (swap! nrepl-server
         (fn [server]
           (when-not (nil? server)
             (let [stop-server (resolve 'clojure.tools.nrepl.server/stop-server)]
               (stop-server server)
               (info "nrepl stopped"))))))

(defn- init! []
  (calendar-mime-type-setup)
  (mongo/connect!)

  (migration/update!)
  (when-let [failures (seq (migration/failing-migrations))]
    (let [msg (format "%s build %s (%s)\nFailing migration(s): %s"
                env/target-env
                env/build-number
                (:git-branch env/buildinfo)
                (ss/join failures))]
      (email/send-email-message (env/value :technical-contact) "Critical: Migration failure!" [msg msg])))

  (mongo/ensure-indexes)
  (server/add-middleware web/tempfile-cleanup)
  (server/add-middleware i18n/lang-middleware)
  (server/add-middleware web/parse-json-body-middleware)
  (server/add-middleware headers/session-id-to-mdc)
  (server/add-middleware web/anti-csrf)
  (server/add-middleware web/wrap-authentication)
  (server/add-middleware web/session-timeout)
  (server/add-middleware autologin/catch-autologin-failure)

  (env/in-dev
    ; Security headers are set by Nginx in production
    (server/add-middleware uach/add-ua-compatible-header)
    (server/add-middleware headers/add-security-headers)
    ; Integration test database selection
    (server/add-middleware mongo/db-selection-middleware))

  (info "*** Instrumenting performance monitoring")
  (perf-mon/init)

  (server/add-middleware headers/sanitize-header-values)
  (server/add-middleware control/lockdown-middleware)
  (server/add-middleware web/cookie-monster))

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
    (let [jetty-opts (merge
                       {:max-threads 250}
                       (when (env/value :ssl :enabled) (assoc (env/value :ssl) :ssl? true)))
          noir-opts {:mode (keyword (or (env/value :noir :mode) env/mode))
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
    (warn "Jetty already started!")))

(defn- stop-jetty! []
  (when-not (nil? @jetty)
    (swap! jetty server/stop)
    (info "Jetty stopped")))

(defcontrol "/internal/hot-restart" []
  (util/future*
    (Thread/yield)
    (env/reload!)
    (i18n/reload!)
    (info "Reloaded env and i18n, restarting Jetty...")
    (stop-jetty!)
    (mongo/disconnect!)
    (mongo/connect!)
    (start-jetty!)))

(defn start-jmx-server! []
  (info "Starting JMX...")
  (swap! jmx-server
         (fn [old-server]
           (if (nil? old-server)
             (let [port (env/value :lupapiste :jmx :port)
                   new-server (ServerFactory/start port)]
               (info "Started JMX server on port" port)
               new-server)
             (do
               (warn "JMX server already started!")
               old-server)))))

(defn- stop-jmx-server! []
  (when-not (nil? @jmx-server)
    (swap! jmx-server #(ServerFactory/stop %))
    (info "JXM Server stopped")))

(defn stop-all! []
  (stop-jetty!)
  (stop-jmx-server!)
  (stop-nrepl!)
  (jms/close-all!)
  (mongo/disconnect!))

(defn -main [& _]
  (infof "Build %s starting in %s mode" (:build-number env/buildinfo) (name env/mode))
  (infof "Running on %s version %s (%s) [%s], trustStore is %s"
    (System/getProperty "java.vm.name")
    (System/getProperty "java.runtime.version")
    (System/getProperty "java.vm.info")
    (if (java.awt.GraphicsEnvironment/isHeadless) "headless" "headful")
    (System/getProperty "javax.net.ssl.trustStore"))
  (info "Running on Clojure" (clojure-version))
  (info "ImageIO: Registered image MIME types:" (ss/join " " (ImageIO/getReaderMIMETypes)))

  (init!)
  (start-jetty!)
  (start-jmx-server!)
  (start-nrepl!)
  (-> (Runtime/getRuntime) (.addShutdownHook (Thread. stop-all!))))

"server ready to start"
