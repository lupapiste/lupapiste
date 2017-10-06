(ns lupapalvelu.integrations.messages
  "Namespace to handle integration messages metadata via db"
  (:require [schema.core :as sc]
            [sade.schemas :as ssc]
            [monger.operators :refer [$set]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states])
  (:import (com.mongodb WriteConcern)))

(def create-id mongo/create-id)

(def partners #{"ely" "mylly"})

(sc/defschema IntegrationMessage
  {:id                            ssc/ObjectIdStr
   :direction                     (sc/enum "in" "out")
   :messageType                   sc/Str
   :partner                       (apply sc/enum partners)
   :format                        (sc/enum "xml" "json")
   :created                       ssc/Timestamp
   (sc/optional-key :external-reference) sc/Str
   (sc/optional-key :output-dir)  sc/Str
   (sc/optional-key :application) {:id           ssc/ApplicationId
                                   :organization sc/Str
                                   :state        (apply sc/enum (map name states/all-states))}
   (sc/optional-key :target)      {:id   ssc/ObjectIdStr
                                   :type sc/Str}
   (sc/optional-key :initator)    {:id       sc/Str
                                   :username sc/Str}
   (sc/optional-key :action)            sc/Str
   (sc/optional-key :attached-files)    [ssc/ObjectIdStr]
   (sc/optional-key :attachmentsCount)  sc/Int
   (sc/optional-key :acknowledged)      ssc/Timestamp})

(sc/defn ^:always-validate save
  [message :- IntegrationMessage]
  (mongo/insert :integration-messages message WriteConcern/UNACKNOWLEDGED))

(sc/defn ^:always-validate mark-acknowledged-and-return :- (sc/maybe IntegrationMessage)
  ([message-id timestamp]
    (mark-acknowledged-and-return message-id timestamp nil))
  ([message-id timestamp updates]
   (mongo/with-id (mongo/update-one-and-return :integration-messages
                                               {:_id message-id}
                                               {$set (merge {:acknowledged timestamp} updates)}))))