(ns lupapalvelu.integrations.messages
  "Namespace to handle integration messages metadata via db"
  (:require [schema.core :as sc]
            [sade.schemas :as ssc]
            [monger.operators :refer [$set]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [sade.shared-schemas :as sssc])
  (:import (com.mongodb WriteConcern)))

(def create-id mongo/create-id)

(def partners #{"allu" "ely" "mylly" "matti" "internal"})

(sc/defschema IntegrationMessageStatus
  (sc/enum "done" "published" "processing" "processed" "received" "queued" "consumed"))

(sc/defschema IntegrationMessage
  {:id                                   ssc/ObjectIdStr
   :direction                            (sc/enum "in" "out")
   :messageType                          sc/Str
   (sc/optional-key :transferType)       (sc/enum "http" "sftp" "jms")
   (sc/optional-key :partner)            (apply sc/enum partners)
   :format                               (sc/enum "xml" "json" "clojure" "bytes")
   :created                              ssc/Timestamp
   :status                               IntegrationMessageStatus
   (sc/optional-key :external-reference) sc/Str
   (sc/optional-key :output-dir)         sc/Str
   (sc/optional-key :application)        {:id                             ssc/ApplicationId
                                          (sc/optional-key :organization) sc/Str
                                          (sc/optional-key :state)        (apply sc/enum (map name states/all-states))}
   (sc/optional-key :target)             {:id   ssc/ObjectIdStr
                                          :type sc/Str}
   (sc/optional-key :initator)           {:id       sc/Str
                                          :username sc/Str}
   (sc/optional-key :action)             (sc/maybe sc/Str)
   (sc/optional-key :attached-files)     [sssc/FileId]
   (sc/optional-key :attachmentsCount)   sc/Int
   (sc/optional-key :acknowledged)       ssc/Timestamp
   (sc/optional-key :data)               (sc/cond-pre sc/Str {sc/Keyword sc/Any})})

(sc/defn ^:always-validate save
  ([message :- IntegrationMessage]
    (save message WriteConcern/UNACKNOWLEDGED))
  ([message :- IntegrationMessage write-concern]
    (mongo/insert :integration-messages message write-concern)))

(sc/defn ^:always-validate mark-acknowledged-and-return :- (sc/maybe IntegrationMessage)
  ([message-id timestamp]
    (mark-acknowledged-and-return message-id timestamp nil))
  ([message-id timestamp updates]
   (mongo/with-id (mongo/update-one-and-return :integration-messages
                                               {:_id message-id}
                                               {$set (merge {:acknowledged timestamp} updates)}))))

(defn update-message
  ([message-id updates]
    (update-message message-id updates WriteConcern/ACKNOWLEDGED))
  ([message-id updates write-concern]
    (mongo/update-by-id :integration-messages message-id updates :write-concern write-concern)))

(sc/defn set-message-status [message-id status :- IntegrationMessageStatus]
  (update-message message-id {$set {:status status}} WriteConcern/UNACKNOWLEDGED))
