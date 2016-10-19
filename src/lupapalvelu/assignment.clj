(ns lupapalvelu.assignment
  (:require [monger.operators :refer [$set $in]]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [sade.schemas :as ssc])
  (:import [org.bson.types ObjectId]))

(sc/defschema AssignmentStatus
  (sc/enum "active" "inactive" "completed"))

(sc/defschema Assignment
  {:id             ssc/ObjectIdStr
   :organizationId sc/Str
   :applicationId  sc/Str
   :target         sc/Any
   :created        ssc/Timestamp
   :creator        usr/SummaryUser
   :recipient      usr/SummaryUser
   :completed      (sc/maybe ssc/Timestamp)
   :completer      (sc/maybe usr/SummaryUser)
   :status         AssignmentStatus
   :description    sc/Str})

(sc/defschema NewAssignment
  (select-keys Assignment
               [:organizationId
                :applicationId
                :creator
                :recipient
                :target
                :description]))

(sc/defn ^:private new-assignment :- Assignment
  [assignment :- NewAssignment
   timestamp  :- ssc/Timestamp]
  (merge assignment
         {:id        (mongo/create-id)
          :created   timestamp
          :completed nil
          :completer nil
          :status    :active}))

;;
;; Querying assignments
;;

(sc/defn ^:always-validate get-assignments :- [Assignment]
  ([]
   (get-assignments {}))
  ([query]
   (mongo/select :assignments query))
  ([query projection]
   (mongo/select :assignments query projection)))

(defn get-assignments-for-application [application-id]
  (get-assignments {:applicationId application-id}))

;;
;; Inserting and modifying assignments
;;

(sc/defn ^:always-validate insert-assignment :- ssc/ObjectIdStr
  [assignment :- NewAssignment
   timestamp  :- ssc/Timestamp]
  (let [created-assignment (new-assignment assignment timestamp)]
    (mongo/insert :assignments created-assignment)
    (:id created-assignment)))

(defn- update-assignment [query assignment-changes]
  (mongo/update-n :assignments query assignment-changes))

(sc/defn ^:always-validate complete-assignment [assignment-id :- ssc/ObjectIdStr
                                                completer     :- usr/SessionSummaryUser
                                                timestamp     :- ssc/Timestamp]
  (let [auth-organizations (into [] (usr/organization-ids-by-roles completer #{:authority}))]
    (update-assignment {:_id            assignment-id
                        :organizationId {$in auth-organizations}
                        :completed      nil}
                       {$set {:completed timestamp
                              :status    "completed"
                              :completer (usr/summary completer)}})))

; A temporary test function, to be removed before merge to develop
(defn test-assignment [application-id target description]
  {:organizationId "753-R"
   :applicationId application-id
   :creator (usr/summary (usr/get-user {:username "sonja"}))
   :recipient (usr/summary (usr/get-user {:username "pena"}))
   :target target
   :description description})
