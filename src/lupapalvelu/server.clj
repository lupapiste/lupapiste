(ns lupapalvelu.server
  "Require api namespaces here so their queries, commands, etc. are included"
  (:require [lupapalvelu.actions-api]
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
            [lupapalvelu.assignment-api]
            [lupapalvelu.assurances-api]
            [lupapalvelu.attachment-api]
            [lupapalvelu.attachment.bind-attachments-api]
            [lupapalvelu.authority-notice-api]
            [lupapalvelu.authorization-api]
            [lupapalvelu.automatic-assignment.filter-api]
            [lupapalvelu.backing-system.integration-config-api]
            [lupapalvelu.bim.api]
            [lupapalvelu.bim.checking-plan.api]
            [lupapalvelu.building-api]
            [lupapalvelu.building-site-api]
            [lupapalvelu.bulletin-report.bulletin-report-api]
            [lupapalvelu.change-email-api]
            [lupapalvelu.comment-api]
            [lupapalvelu.company-api]
            [lupapalvelu.construction-api]
            [lupapalvelu.conversion.conversion-api]
            [lupapalvelu.copy-application-api]
            [lupapalvelu.ddd-map-api]
            [lupapalvelu.digitizer-api]
            [lupapalvelu.dmcity-api]
            [lupapalvelu.document.document-api]
            [lupapalvelu.document.low-carbon-assessment-api]
            [lupapalvelu.document.schema-repository-api]
            [lupapalvelu.exports-api]
            [lupapalvelu.features-api]
            [lupapalvelu.file-upload-api]
            [lupapalvelu.filebank-api]
            [lupapalvelu.financial-api]
            [lupapalvelu.fixture.fixture-api]
            [lupapalvelu.foreman-api]
            [lupapalvelu.gis.gis-api]
            [lupapalvelu.guest-api]
            [lupapalvelu.http.jetty :as jetty]
            [lupapalvelu.ident.ad-login-api]
            [lupapalvelu.imports.imports-api]
            [lupapalvelu.info-links-api]
            [lupapalvelu.inspection-summary-api]
            [lupapalvelu.integrations-api]
            [lupapalvelu.integrations.mq-consumers]
            [lupapalvelu.integrations.pubsub :as pubsub]
            [lupapalvelu.invoice-api]
            [lupapalvelu.job-api]
            [lupapalvelu.kopiolaitos-api]
            [lupapalvelu.linked-file-api]
            [lupapalvelu.location-api]
            [lupapalvelu.logging :refer [info infof]]
            [lupapalvelu.logging-api]
            [lupapalvelu.login.api]
            [lupapalvelu.migration.migration]
            [lupapalvelu.mml.yhteystiedot-api]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongo-watcher.client]
            [lupapalvelu.municipality-api]
            [lupapalvelu.neighbors-api]
            [lupapalvelu.notice-forms-api]
            [lupapalvelu.notices.notices-api]
            [lupapalvelu.oauth.api]
            [lupapalvelu.onkalo-operations-api]
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
            [lupapalvelu.replies-api]
            [lupapalvelu.reports.reports-api]
            [lupapalvelu.rest.docstore-api]
            [lupapalvelu.rest.rest-api]
            [lupapalvelu.rss.bulletin]
            [lupapalvelu.screenmessage-api]
            [lupapalvelu.sftp.sftp-api]
            [lupapalvelu.ssokeys-api]
            [lupapalvelu.statement-api]
            [lupapalvelu.statistics.statistics-api]
            [lupapalvelu.suomifi-messages-api]
            [lupapalvelu.supplement-request-api]
            [lupapalvelu.suti-api]
            [lupapalvelu.system.build :as build]
            [lupapalvelu.system.config :as config]
            [lupapalvelu.system.system :as system]
            [lupapalvelu.system.util :as system-util]
            [lupapalvelu.tasks-api]
            [lupapalvelu.tiedonohjaus-api]
            [lupapalvelu.user-api]
            [lupapalvelu.user-notification-api]
            [lupapalvelu.verdict-api]
            [lupapalvelu.vetuma]
            [lupapalvelu.xml.gcs-writer]
            [lupapalvelu.ya-digging-permit-api]
            [lupapalvelu.ya-extension-api]
            [noir.exception]
            [noir.session]
            [ring.middleware.session]
            [sade.dummy-email-server]
            [sade.env :as env])
  (:import [java.awt GraphicsEnvironment]))

(set! *warn-on-reflection* true)

(defonce ^{:private true} *lupapiste-system (atom nil))

(def required-components #{::jetty/server
                           ::mongo/database
                           ::pubsub/client
                           :laundry/client})

(defn build-lupapiste
  []
  (let [blueprint (-> (config/config)
                      build/build-system-lupapiste)]
    ;; Other applications (e.g. batchruns) might have minimal configs
    ;; and use only some of the available components. Lupapiste server
    ;; requires more so check that the required components are configured
    ;; and enabled.
    (system-util/assert-components! blueprint required-components)
    blueprint))

(def on-shutdown-hook (Thread. ^Runnable (fn []
                                           (system/halt! *lupapiste-system)
                                           (shutdown-agents))))

(defn -main [& _]
  (infof "Build %s starting in %s mode" (:build-number env/buildinfo) (name env/mode))
  (infof "Running on %s version %s (%s) [%s], trustStore is %s"
         (System/getProperty "java.vm.name")
         (System/getProperty "java.runtime.version")
         (System/getProperty "java.vm.info")
         (if (GraphicsEnvironment/isHeadless) "headless" "headful")
         (System/getProperty "javax.net.ssl.trustStore"))
  (info "Running on Clojure" (clojure-version))

  (reset! *lupapiste-system (-> (build-lupapiste)
                                system/init!))
  (-> (Runtime/getRuntime) (.addShutdownHook on-shutdown-hook)))
