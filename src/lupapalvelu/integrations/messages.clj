(ns lupapalvelu.integrations.messages
  "Namespace to handle integration messages metadata via db"
  (:require [schema.core :as sc]
            [sade.schemas :as ssc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states])
  (:import (com.mongodb WriteConcern)))

(def create-id mongo/create-id)

(def partners #{"ely"})

(sc/defschema IntegrationMessage
  {:id                            ssc/ObjectIdStr
   :messageType                   sc/Str
   :partner                       (apply sc/enum partners)
   :output-dir                    sc/Str
   :format                        (sc/enum "xml" "json")
   :created                       ssc/Timestamp
   (sc/optional-key :application) {:id           ssc/ApplicationId
                                   :organization sc/Str
                                   :state        (apply sc/enum (map name states/all-states))}
   (sc/optional-key :target)      {:id   ssc/ObjectIdStr
                                   :type sc/Str}
   (sc/optional-key :initator)    {:id       sc/Str
                                   :username sc/Str}
   (sc/optional-key :action)            sc/Str
   (sc/optional-key :attachmentsCount)  sc/Int
   (sc/optional-key :acknowledged)      ssc/Timestamp})

(sc/defn ^:always-validate save
  [message :- IntegrationMessage]
  (mongo/insert :integration-messages message WriteConcern/UNACKNOWLEDGED))
