(ns lupapalvelu.server
  (:require [clojure.java.io :as io]
            [lupapalvelu.actions-api]
            [lupapalvelu.admin-api]
            [lupapalvelu.admin-reports-api]
            [lupapalvelu.allu-api]
            [lupapalvelu.appeal-api]
            [lupapalvelu.application-api]
            [lupapalvelu.application-bulletins-api]
            [lupapalvelu.application-options-api]
            [lupapalvelu.application-search-api]
            [lupapalvelu.application-tabs-api]
            [lupapalvelu.archive.archiving-api]
            [lupapalvelu.asianhallinta-config-api]
            [lupapalvelu.assignment-api]
            [lupapalvelu.attachment-api]
            [lupapalvelu.attachment.bind-attachments-api]
            [lupapalvelu.authorization-api]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.automatic-assignment.filter-api]
            [lupapalvelu.building-api]
            [lupapalvelu.building-site-api]
            [lupapalvelu.bulletin-report.bulletin-report-api]
            [lupapalvelu.calendars-api]
            [lupapalvelu.campaign-api]
            [lupapalvelu.change-email-api]
            [lupapalvelu.comment-api]
            [lupapalvelu.company-api]
            [lupapalvelu.construction-api]
            [lupapalvelu.control-api :refer [defcontrol] :as control]
            [lupapalvelu.copy-application-api]
            [lupapalvelu.ddd-map-api]
            [lupapalvelu.digitizer-api]
            [lupapalvelu.document.document-api]
            [lupapalvelu.document.schema-repository-api]
            [lupapalvelu.exports-api]
            [lupapalvelu.features-api]
            [lupapalvelu.file-upload-api]
            [lupapalvelu.filebank-api]
            [lupapalvelu.financial-api]
            [lupapalvelu.fixture.fixture-api]
            [lupapalvelu.fixture.fixtures]
            [lupapalvelu.foreman-api]
            [lupapalvelu.gis.gis-api]
            [lupapalvelu.guest-api]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.info-links-api]
            [lupapalvelu.inspection-summary-api]
            [lupapalvelu.integrations-api]
            [lupapalvelu.integrations.jms-consumers]
            [lupapalvelu.integrations.pubsub]
            [lupapalvelu.invoice-api]
            [lupapalvelu.kopiolaitos-api]
            [lupapalvelu.linked-file-api]
            [lupapalvelu.location-api]
            [lupapalvelu.logging]
            [lupapalvelu.logging-api]
            [lupapalvelu.login.api]
            [lupapalvelu.migration.migration]
            [lupapalvelu.mml.yhteystiedot-api]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.municipality-api]
            [lupapalvelu.neighbors-api]
            [lupapalvelu.notice-api]
            [lupapalvelu.notice-forms-api]
            [lupapalvelu.oauth.api]
            [lupapalvelu.onkalo-operations-api]
            [lupapalvelu.onnistuu-api]
            [lupapalvelu.open-inforequest-api]
            [lupapalvelu.operations]
            [lupapalvelu.organization-api]
            [lupapalvelu.pate.phrases-api]
            [lupapalvelu.pate.verdict-api]
            [lupapalvelu.pate.verdict-template-api]
            [lupapalvelu.pdf.pdf-export-api]
            [lupapalvelu.perf-mon-api]
            [lupapalvelu.premises-api]
            [lupapalvelu.prev-permit-api]
            [lupapalvelu.printing-order.printing-order-api]
            [lupapalvelu.property-api]
            [lupapalvelu.proxy-services]
            [lupapalvelu.reports.reports-api]
            [lupapalvelu.rest.docstore-api]
            [lupapalvelu.rest.rest-api]
            [lupapalvelu.screenmessage-api]
            [lupapalvelu.sftp.sftp-api]
            [lupapalvelu.ssokeys-api]
            [lupapalvelu.statement-api]
            [lupapalvelu.suomifi-messages-api]
            [lupapalvelu.suti-api]
            [lupapalvelu.tasks-api]
            [lupapalvelu.tiedonohjaus-api]
            [lupapalvelu.user-api]
            [lupapalvelu.user-notification-api]
            [lupapalvelu.verdict-api]
            [lupapalvelu.verdict-robot.api]
            [lupapalvelu.vetuma]
            [lupapalvelu.web :as web]
            [lupapalvelu.xml.gcs-writer]
            [lupapalvelu.ya-digging-permit-api]
            [lupapalvelu.ya-extension-api]
            [mount.core :as mount :refer [defstate]]
            [noir.content.defaults :as defaults]
            [noir.options :as options]
            [noir.server :as server]
            [noir.statuses :as statuses]
            [ring.middleware.session.cookie :as session]
            [sade.core :refer [now]]
            [sade.dummy-email-server]
            [sade.env :as env]
            [sade.security-headers :as headers]
            [sade.util :as util]
            [taoensso.timbre :refer [error info infof warn]])
  (:import [fi.lupapiste.jmx ServerFactory]
           [java.awt GraphicsEnvironment]
           [java.io ByteArrayOutputStream]
           [javax.activation MailcapCommandMap]
           [org.eclipse.jetty.server Server]))

(defn read-session-key []
  {:post [(or (nil? %) (= (count %) 16))]}
  (let [keyfile (io/file "sessionkey")]
    (when (.exists keyfile)
      (with-open [in (io/input-stream keyfile)
                  out (ByteArrayOutputStream.)]
        (io/copy in out)
        (into-array Byte/TYPE (take 16 (.toByteArray out)))))))

(alter-var-root
  (var noir.exception/wrap-exceptions)
  (constantly
    (fn [handler]
      (fn [request]
        (try
          (handler request)
          (catch Exception e
            (error e "Internal noir server error") ; replaced (.printStackTrace e) from noir default wrapper
            (let [content (if (options/dev-mode?)
                            (try
                              (defaults/stack-trace (noir.exception/parse-ex e))
                              (catch Throwable _
                                (statuses/get-page 500)))
                            (statuses/get-page 500))]
              {:status  500
               :headers {"Content-Type" "text/html"}
               :body    content})))))))

;; FIXME: Stopping and then starting JMX server ends up with an exception, maybe a bug in ServerFactory?
(defstate ^{:on-reload :noop} jmx-server
  :start (let [port (env/value :lupapiste :jmx :port)]
           (if (int? port)
             (do
               (info "Starting JMX...")
               (let [new-server (ServerFactory/start port)]
                 (info "Started JMX server on port" port)
                 new-server))
             (do
               (info "Skipping JMX")
               (when-not env/dev-mode?
                 (throw (ex-info "JMX port not specified!" {}))))))

  :stop (when jmx-server
          (ServerFactory/stop jmx-server)
          (info "JMX Server stopped")))

(defstate ^{:on-reload :noop} calendar-mime-type
  :start (MailcapCommandMap/setDefaultCommandMap
           (doto ^MailcapCommandMap (MailcapCommandMap/getDefaultCommandMap)
             (.addMailcap "text/calendar;; x-java-content-handler=com.sun.mail.handlers.text_plain"))))

(defstate ^{:on-reload :noop} server-middleware
  :start (do (server/add-middleware web/tempfile-cleanup)
             (server/add-middleware i18n/lang-middleware)
             (server/add-middleware web/parse-json-body-middleware)
             (server/add-middleware headers/session-id-to-mdc)
             (server/add-middleware web/anti-csrf)
             (server/add-middleware web/wrap-authentication)
             (server/add-middleware web/session-timeout)
             (server/add-middleware autologin/catch-autologin-failure)
             (server/add-middleware headers/add-security-headers)
             (env/in-dev
               ; Integration test database selection
               (server/add-middleware mongo/db-selection-middleware))

             (server/add-middleware headers/sanitize-header-values)
             (server/add-middleware control/lockdown-middleware)
             (server/add-middleware web/cookie-monster)))

(defstate jetty
  :start (let [jetty-opts (merge {:max-threads (env/value :jetty :max-threads)}
                                 (when (env/value :ssl :enabled) (assoc (env/value :ssl) :ssl? true)))
               noir-opts {:mode                 (keyword (or (env/value :noir :mode) env/mode))
                          :ns                   'lupapalvelu.web
                          :jetty-options        jetty-opts
                          :session-store        (session/cookie-store {:key (read-session-key)})
                          :session-cookie-attrs (env/value :cookie)}
               starting (double (now))
               jetty-instance ^Server (server/start env/port noir-opts)]
           (.setStopTimeout jetty-instance 10000)
           (infof "Jetty startup took %.3f seconds" (/ (- (now) starting) 1000))
           jetty-instance)

  :stop (do (server/stop jetty)
            (info "Jetty stopped")))

(defstate nrepl-server
  :start (when (env/feature? :nrepl)
           (require 'clojure.tools.nrepl.server)
           (let [start-server (resolve 'clojure.tools.nrepl.server/start-server)
                 port 9090
                 server (start-server :port port :bind "localhost")]
             (warn "*** Started nrepl in port" port)
             server))

  :stop (when nrepl-server
          (let [stop-server (resolve 'clojure.tools.nrepl.server/stop-server)]
            (stop-server nrepl-server)
            (info "nrepl stopped"))))

(defcontrol "/internal/hot-restart" []
  (util/future*
    (Thread/yield)
    (mount/stop)
    (mount/start)))

(defn -main [& _]
  (infof "Build %s starting in %s mode" (:build-number env/buildinfo) (name env/mode))
  (infof "Running on %s version %s (%s) [%s], trustStore is %s"
         (System/getProperty "java.vm.name")
         (System/getProperty "java.runtime.version")
         (System/getProperty "java.vm.info")
         (if (GraphicsEnvironment/isHeadless) "headless" "headful")
         (System/getProperty "javax.net.ssl.trustStore"))
  (info "Running on Clojure" (clojure-version))

  (mount/start)
  (-> (Runtime/getRuntime) (.addShutdownHook (Thread. mount/stop))))

"server ready to start"
