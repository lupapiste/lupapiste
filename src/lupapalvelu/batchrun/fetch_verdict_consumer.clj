(ns lupapalvelu.batchrun.fetch-verdict-consumer
  (:require [clojure.tools.reader.edn :as edn]
            [schema.core :as sc]
            [taoensso.timbre :refer [error errorf info infof warn]]
            [sade.env :as env]
            [lupapalvelu.batchrun.fetch-verdict :as fetch-verdict]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user])
  (:import (javax.jms Session)))

(sc/defschema FetchVerdictMessage
  {:id sc/Str
   (sc/optional-key :database) sc/Str})

(def fetch-verdict-message-validator (sc/validator FetchVerdictMessage))

(defn read-message [msg]
  (try
    (let [message (edn/read-string msg)]
      (fetch-verdict-message-validator message)
      message)
    (catch Throwable t
      (errorf "Invalid message '%s' for fetch-verdict: %s" msg (.getMessage t))
      nil)))

(defn handle-fetch-verdict-message
  "Returns a function for handling fetch verdict messages. The returned function
  calls commit-fn if fetching succeeds, but also if something fails and  there's
  nothing to retry, and alternatively calls rollback-fn fetching fails in a way
  that warrants a retry."
  [commit-fn rollback-fn]
  (fn [msg]
    (if-let [{:keys [id database]} (read-message msg)]
      (mongo/with-db (or database mongo/*db-name*)
        (if-let [application (mongo/select-one :applications {:_id   id
                                                              :state "sent"})]
          (let [batchrun-user (user/batchrun-user [(:organization application)])
                batchrun-name "Automatic verdicts checking"]
            (logging/with-logging-context {:applicationId id, :userId (:id batchrun-user)}
              (if (fetch-verdict/fetch-verdict batchrun-name batchrun-user application)
                (commit-fn)
                (rollback-fn))))
          (do (errorf "Could not find application for fetching verdict: %s" id)
              (commit-fn))))
      (commit-fn)))) ; Invalid message, nothing to be done

(when (env/feature? :jms)

(def fetch-verdicts-queue "lupapiste.fetch-verdicts.#")

(def fetch-verdicts-transacted-session (-> (jms/get-default-connection)
                                           (jms/create-transacted-session)
                                           (jms/register-session :consumer)))

(defn commit-fetch-verdict [^Session session]
  (info "Commiting fetch-verdict message")
  (jms/commit session))

(defn rollback-fetch-verdict [^Session session]
  (warn "Rollbacking fetch-verdict message")
  (jms/rollback session))

(defonce fetch-verdicts-consumer
  (jms/create-consumer fetch-verdicts-transacted-session
                       fetch-verdicts-queue
                       (handle-fetch-verdict-message (partial commit-fetch-verdict fetch-verdicts-transacted-session)
                                                     (partial rollback-fetch-verdict fetch-verdicts-transacted-session)))))
