(ns lupapalvelu.batchrun.fetch-verdict
  (:require [clojure.edn :as edn]
            [schema.core :as sc]
            [taoensso.timbre :refer [error errorf info infof warn]]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.action :refer [application->command]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.user :as user]
            [lupapalvelu.verdict :as verdict])
  (:import (javax.jms Session)))

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


(sc/defschema FetchVerdictMessage
  {:id sc/Str
   :database sc/Str})

(defn read-message [msg]
  (try
    (let [message (edn/read-string msg)]
      (sc/validate FetchVerdictMessage message)
      message)
    (catch Throwable t
      (errorf t "Invalid message for fetch-verdict: %s" (.getMessage t))
      nil)))

(defn handle-fetch-verdict-message [^Session session]
  (fn [msg]
    (mongo/connect!)
    (if-let [{:keys [id database]} (read-message msg)]
      (mongo/with-db database
        (let [application (mongo/select-one :applications {:_id id})
              batchrun-user (user/batchrun-user [(:organization application)])
              batchrun-name "Automatic verdicts checking"]
          (if (fetch-verdict batchrun-name batchrun-user application)
            (jms/commit session)
            (jms/rollback session))))
      (jms/commit session)))) ; Invalid message, nothing to be done

(when (env/feature? :jms)
 (def fetch-verdicts-queue "lupapiste/fetch-verdicts.#")

 (def fetch-verdicts-transacted-session (-> (jms/get-default-connection)
                                            (jms/create-transacted-session)
                                            (jms/register-session :consumer)))

 (defonce fetch-verdicts-consumer
   (jms/create-consumer fetch-verdicts-transacted-session
                        fetch-verdicts-queue
                        (handle-fetch-verdict-message fetch-verdicts-transacted-session))))
