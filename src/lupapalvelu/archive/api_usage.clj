(ns lupapalvelu.archive.api-usage
  "Functionality for consuming Onkalo API usage log messages and
  inserting them into Mongo"
  (:require [monger.operators :refer :all]
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
   :timestamp                ssc/Nat   ; Timestamp of download, change to :downloaded
   (sc/optional-key :logged) ssc/Nat   ; Timestamp of logging, added when sent to Mongo
   sc/Keyword                sc/Any})  ; Rest of the data TBD

(def archive-api-usage-collection :archive-api-usage)

(sc/defn ^:always-validate log-archive-api-usage! :- sc/Bool
  [entry :- ArchiveApiUsageLogEntry
   log-timestamp :- ssc/Nat]
  (try (mongo/insert archive-api-usage-collection
                     (assoc entry :logged log-timestamp))
       true
       (catch Throwable e
         (errorf "Could not insert archive API usage log entry to mongo: %s" (.getMessage e))
         false)))

(when (env/feature? :jms)
(defn- message-handler [^Session session]
  (fn [payload]
    (if-let [schema-error (sc/check ArchiveApiUsageLogEntry payload)]
      (do (errorf "Archive API usage log entry does not match schema: %s" schema-error)
          (jms/commit session))
      (if (log-archive-api-usage! payload (now))
        (jms/commit session)
        (jms/rollback session)))))

(def archive-api-usage-queue "onkalo.api-usage")

(def archive-api-usage-transacted-session (-> (jms/get-default-connection)
                                              (jms/create-transacted-session)
                                              (jms/register-session :consumer)))

(defonce api-usage-consumer (jms/create-nippy-consumer
                              archive-api-usage-transacted-session
                              archive-api-usage-queue
                              (message-handler archive-api-usage-transacted-session)))
)
