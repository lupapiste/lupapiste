(ns lupapalvelu.integrations.messages
  "Namespace to handle integration messages metadata via db"
  (:require [schema.core :as sc]
            [sade.schemas :as ssc]
            [monger.operators :refer [$set]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states])
  (:import (com.mongodb WriteConcern)))

(def create-id mongo/create-id)

(def partners #{"ely" "mylly" "matti"})

(sc/defschema IntegrationMessage
  {:id                            ssc/ObjectIdStr
   :direction                     (sc/enum "in" "out")
   :messageType                   sc/Str
   :partner                       (apply sc/enum partners)
   :format                        (sc/enum "xml" "json")
   :created                       ssc/Timestamp
   :status                         (sc/enum "done" "published" "processing" "processed")
   (sc/optional-key :external-reference) sc/Str
   (sc/optional-key :output-dir)  sc/Str
   (sc/optional-key :application) {:id           ssc/ApplicationId
                                   :organization sc/Str
                                   :state        (apply sc/enum (map name states/all-states))}
   (sc/optional-key :target)      {:id   ssc/ObjectIdStr
                                   :type sc/Str}
   (sc/optional-key :initator)    {:id       sc/Str
                                   :username sc/Str}
   (sc/optional-key :action)            (sc/maybe sc/Str)
   (sc/optional-key :attached-files)    [ssc/ObjectIdStr]
   (sc/optional-key :attachmentsCount)  sc/Int
   (sc/optional-key :acknowledged)      ssc/Timestamp
   (sc/optional-key :data)              {sc/Keyword sc/Any}})

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