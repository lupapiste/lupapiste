(ns lupapalvelu.assignment
  (:require [monger.operators :refer [$set $in]]
            [schema.core :as sc]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [sade.schemas :as ssc]
            [sade.strings :as ss]))

;; Helpers and schemas

(defn- assignment-in-user-organization-query [user]
  {:organizationId {$in (into [] (usr/organization-ids-by-roles user #{:authority}))}})

(defn- organization-query-for-user [user query]
  (merge query (assignment-in-user-organization-query user)))

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
   :status         (sc/enum "active" "inactive" "completed")
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
          :status    "active"}))

;;
;; Querying assignments
;;

(sc/defn ^:always-validate get-assignments :- [Assignment]
  ([user :- usr/SessionSummaryUser]
   (get-assignments user {}))
  ([user query]
   (mongo/select :assignments (organization-query-for-user user query)))
  ([user query projection]
   (mongo/select :assignments (organization-query-for-user user query) projection)))

(sc/defn ^:always-validate get-assignment :- (sc/maybe Assignment)
  [user           :- usr/SessionSummaryUser
   application-id :- ssc/ObjectIdStr]
  (first (get-assignments user {:_id application-id})))

(sc/defn ^:always-validate get-assignments-for-application :- [Assignment]
  [user           :- usr/SessionSummaryUser
   application-id :- sc/Str]
  (get-assignments user {:applicationId application-id}))

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
  (update-assignment
   (organization-query-for-user completer
                                {:_id       assignment-id
                                 :status    "active"
                                 :completed nil})
   {$set {:completed timestamp
          :status    "completed"
          :completer (usr/summary completer)}}))

; A temporary test function, to be removed before merge to develop
(defn test-assignment [application-id target description]
  {:organizationId "753-R"
   :applicationId application-id
   :creator (usr/summary (usr/get-user {:username "sonja"}))
   :recipient (usr/summary (usr/get-user {:username "pena"}))
   :target target
   :description description})

(defn display-text-for-document [doc lang]
  (let [schema-loc-key (str (get-in doc [:schema-info :name]) "._group_label")
        schema-localized (i18n/localize lang schema-loc-key)
        accordion-datas (schemas/resolve-accordion-field-values doc)]
    (if (seq accordion-datas)
      (str schema-localized " - " (ss/join " " accordion-datas))
      schema-localized)))
