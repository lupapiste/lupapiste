(ns lupapalvelu.batchrun.fetch-verdict
  (:require [taoensso.timbre :refer [error errorf info infof warn]]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [application->command]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.verdict :as verdict]))

(defn fetch-verdict
  [batchrun-name batchrun-user {:keys [id permitType organization] :as app}]
  (logging/with-logging-context {:applicationId id, :userId (:id batchrun-user)}
    (infof "Checking verdict for %s in db %s" id mongo/*db-name*)
    (try
      (let [command (assoc (application->command app) :user batchrun-user :created (now) :action "fetch-verdicts")
            result (verdict/do-check-for-verdict command)]
        (when (-> result :verdicts count pos?)
          (infof "Found %s verdicts" (-> result :verdicts count))
          ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
          (logging/log-event :info {:run-by batchrun-name :event "Found new verdict"})
          (notifications/notify! :application-state-change command))
        (when (or (nil? result) (fail? result))
          (infof "No verdicts found, result: " (if (nil? result) :error.no-app-xml result))
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
        false))))

(defn queue-for-organization [org-id]
  (str "lupapiste/fetch-verdicts." org-id))

(defn fetch-verdict-message [app-id database-name]
  (pr-str {:id app-id :database database-name}))
