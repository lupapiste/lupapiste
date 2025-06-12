(ns lupapalvelu.archive.api-usage
  "Functionality for consuming Onkalo API usage log messages and
  inserting them into Mongo"
  (:require [clojure.string :as str]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :refer [errorf warnf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

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
              (sc/optional-key :security-period-end) sc/Str
              (sc/optional-key :applicationId)       sc/Str
              (sc/optional-key :address)             sc/Str
              (sc/optional-key :type)                sc/Str
              (sc/optional-key :propertyId)          sc/Str}})

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
    (str (random-uuid))))

(sc/defn ^:always-validate log-archive-api-usage! :- sc/Bool
  [entry :- ArchiveApiUsageLogEntry
   log-timestamp :- ssc/Nat]
  (try (mongo/insert archive-api-usage-collection
                     (assoc entry
                            :_id (api-usage-entry-id entry)
                            :logged log-timestamp))
       true
       (catch Throwable e
         (let [msg (.getMessage e)]
           (if (and (string? msg)
                    (str/includes? msg "E11000 duplicate key error collection: lupapiste.archive-api-usage"))
             ;; Duplicates can be caused by re-downloads in docstore and they can be ignored
             (do (warnf "Ignoring duplicate key error in lupapiste.archive-api-usage: %s" (.getMessage e))
                 true)
             ;; Something unexpected occurred, NACK the message
             (errorf "Could not insert archive API usage log entry to mongo: %s" (.getMessage e)))))))

(def api-usage-message-validator (sc/validator ArchiveApiUsageLogEntry))

(defn- pubsub-message-handler [_system message]
  (try
    (let [api-usage-entry (-> (dissoc message :message-id)
                              (api-usage-message-validator))]
      (log-archive-api-usage! api-usage-entry (now)))
    (catch Exception e
      (errorf "Invalid message '%s' for api usage: %s" message (.getMessage e))
      ;; Return true to remove the invalid message from queue
      true)))

(defmethod lip/get-pubsub-subscription-handler :archive/api-usage
  [_id]
  pubsub-message-handler)
