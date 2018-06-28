(ns lupapalvelu.archive.api-usage
  "Functionality for consuming Onkalo API usage log messages and
  inserting them into Mongo"
  (:require [clojure.tools.reader.edn :as edn]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [taoensso.timbre :refer [errorf]]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org])
  (:import (javax.jms Session)))


(sc/defschema ArchiveApiUsageLogEntry
  "Schema for API usage log entries, produced by Onkalo"
  {:organization             org/OrgId
   :fileId                   sc/Str
   :apiUser                  sc/Str
   :externalId               sc/Str
   :timestamp                ssc/Nat   ; Timestamp of download, change to :downloaded
   (sc/optional-key :logged) ssc/Nat   ; Timestamp of logging, added when sent to Mongo
   :metadata {(sc/optional-key :henkilotiedot)       sc/Str
              (sc/optional-key :julkisuusluokka)     sc/Str
              (sc/optional-key :myyntipalvelu)       sc/Bool
              (sc/optional-key :nakyvyys)            sc/Str
              (sc/optional-key :security-period-end) sc/Str}})

(def archive-api-usage-collection :archive-api-usage)

(defn api-usage-entry-id
  "If the request originated from Lupapiste kauppa, use the provided
  external ID. Otherwise, use standard mongo ID. The external ID is
  used to ensure that each document download belonging to a given
  Lupapiste kauppa transaction is logged only once."
  [entry]
  (if-let [docstore-id (when (= (:apiUser entry) "document_store")
                         (not-empty (:externalId entry)))]
    docstore-id
    (mongo/create-id)))

(sc/defn ^:always-validate log-archive-api-usage! :- sc/Bool
  [entry :- ArchiveApiUsageLogEntry
   log-timestamp :- ssc/Nat]
  (try (mongo/insert archive-api-usage-collection
                     (assoc entry
                            :_id (api-usage-entry-id entry)
                            :logged log-timestamp))
       true
       (catch Throwable e
         (errorf "Could not insert archive API usage log entry to mongo: %s" (.getMessage e))
         false)))

(def api-usage-message-validator (sc/validator ArchiveApiUsageLogEntry))

(defn read-message [msg]
  (try
    (let [message (edn/read-string msg)]
      (api-usage-message-validator message)
      message)
    (catch Throwable t
      (errorf "Invalid message '%s' for api usage: %s" msg (.getMessage t))
      nil)))

(when (env/feature? :jms)
(defn- message-handler [commit-fn rollback-fn]
  (fn [msg]
    (if-let [api-usage-entry (read-message msg)]
      (if (log-archive-api-usage! api-usage-entry (now))
        (commit-fn)
        (rollback-fn))
      (commit-fn))))

(def archive-api-usage-queue "onkalo.api-usage")

(def archive-api-usage-transacted-session (-> (jms/get-default-connection)
                                              (jms/create-transacted-session)
                                              (jms/register-session :consumer)))

(defonce api-usage-consumer (jms/create-consumer
                              archive-api-usage-transacted-session
                              archive-api-usage-queue
                              (message-handler #(jms/commit archive-api-usage-transacted-session)
                                               #(jms/rollback archive-api-usage-transacted-session))))
)
