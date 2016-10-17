(ns lupapalvelu.assignment
  (:require [monger.operators :refer [$set]]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]
            [sade.schemas :as ssc])
  (:import [org.bson.types ObjectId]))

(sc/defschema Assignment
  {:id              ssc/ObjectIdStr
   :organization-id sc/Str
   :application-id  sc/Str
   :target          sc/Any
   :created         ssc/Timestamp
   :creator-id      ssc/ObjectIdStr
   :recipient-id    ssc/ObjectIdStr
   :completed       (sc/maybe ssc/Timestamp)
   :completer-id    (sc/maybe sc/Str)
   :active          sc/Bool
   :description     sc/Str})

(sc/defschema NewAssignment
  (select-keys Assignment
               [:organization-id
                :application-id
                :creator-id
                :recipient-id
                :target
                :description]))

(defn- new-assignment-fields [timestamp]
  {:id           (mongo/create-id)
   :created      timestamp
   :completed    nil
   :completer-id nil
   :inactive     false})

(defn get-assignments
  ([]
   (get-assignments {}))
  ([query]
   (mongo/select :assignments query))
  ([query projection]
   (mongo/select :assignments query projection)))

(sc/defn ^:always-validate insert-assignment [assignment :- NewAssignment
                                              timestamp  :- ssc/Timestamp]
  (mongo/insert :assignments (merge assignment (new-assignment-fields timestamp))))

(sc/defn ^:always-validate update-assignment [assignment-id      :- ssc/ObjectIdStr
                                              assignment-changes]
  (mongo/update-by-id :assignments
                      assignment-id
                      assignment-changes))

(defn complete-assignment [assignment-id completer-id timestamp]
  (update-assignment assignment-id
                     {$set {:completed    timestamp
                            :completer-id completer-id}}))
