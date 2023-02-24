(ns lupapalvelu.batchrun.fetch-verdict
  (:require [lupapalvelu.action :refer [application->command]]
            [lupapalvelu.integrations.message-queue :as mq]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.pate.verdict-date :as verdict-date]
            [lupapalvelu.verdict :as verdict]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [taoensso.timbre :refer [info infof]]))

(defn fetch-verdict
  [batchrun-name batchrun-user {:keys [id permitType organization] :as app}]
  (logging/with-logging-context {:applicationId id, :userId (:id batchrun-user)}
    (infof "Checking verdict in db %s" mongo/*db-name*)
    (try
      (let [command (assoc (application->command app) :user batchrun-user :created (now) :action "fetch-verdicts")
            result (verdict/do-check-for-verdict command)]
        (when (-> result :verdicts count pos?)
          (verdict-date/update-verdict-date (:id app))
          (infof "Found %s verdicts" (-> result :verdicts count))
          ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
          (logging/log-event :info {:run-by batchrun-name :event "Found new verdict"})
          (notifications/notify! :application-state-change command))
        (when (or (nil? result) (fail? result))
          (info "No verdicts found, result: " (if (nil? result) :error.no-app-xml result))
          (logging/log-event :error {:run-by       batchrun-name
                                     :event        "Failed to check verdict"
                                     :failure      (if (nil? result) :error.no-app-xml result)
                                     :organization {:id organization :permit-type permitType}
                                     }))
        true)
      (catch Throwable t
        (logging/log-event :error {:run-by            batchrun-name
                                   :event             "Unable to get verdict from backend"
                                   :exception-message (.getMessage t)
                                   :application-id    id
                                   :organization      {:id organization :permit-type permitType}})
        ;; ACK the message even if e.g. the connection failed - a new message will be generated the next night anyway
        ;; and usually the issue won't be resolved immediately so that an immediate retry would help
        true))))

(def unified-fetch-verdicts-queue "lupapiste.fetch-verdicts")

(defn queue-for-organization [org-id]
  (if (= (env/value :integration-message-queue) "pubsub")
    unified-fetch-verdicts-queue
    (str  unified-fetch-verdicts-queue "." org-id)))

(defn fetch-verdict-message [app-id organization]
  {:id           app-id
   :organization organization})

(defn publish-to-queue [organization id db-name]
  (mq/publish (queue-for-organization organization)
              (util/assoc-when (fetch-verdict-message id organization)
                               ;; Needed to provide the correct test db name to the receiver
                               :db-name db-name)))
