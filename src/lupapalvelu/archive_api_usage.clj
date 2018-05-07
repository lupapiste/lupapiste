(ns lupapalvelu.archive-api-usage
  ""
  (:require [monger.operators :refer :all]
            [schema.core :as sc]
            [taoensso.timbre :refer [errorf]]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]))


(sc/defschema ArchiveApiUsageLogEntry
  "Schema for API usage log entries, produced by Onkalo"
  {:organization             org/OrgId
   :timestamp                ssc/Nat   ; Timestamp of download, change to :downloaded
   (sc/optional-key :logged) ssc/Nat   ; Timestamp of logging, added when sent to Mongo
   sc/Keyword                sc/Any})  ; Rest of the data TBD

(def archive-api-usage-collection :archive-api-usage)

(sc/defn ^:always-validate log-archive-api-usage!
  [entry :- ArchiveApiUsageLogEntry
   log-timestamp :- ssc/Nat]
  (mongo/insert archive-api-usage-collection
                (assoc entry :logged log-timestamp)))

(when (env/feature? :jms)
(defn- message-handler
  [payload]
  (if-let [schema-error (sc/check ArchiveApiUsageLogEntry payload)]
    (errorf "Archive API usage log entry does not match schema: %s" schema-error)
    (log-archive-api-usage! payload (now))))

(def archive-api-usage-queue "onkalo.api-usage")

(def api-usage-consumer (jms/create-nippy-consumer archive-api-usage-queue message-handler))
)
