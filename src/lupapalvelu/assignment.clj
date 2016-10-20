(ns lupapalvelu.assignment
  (:require [monger.operators :refer [$in $options $or $regex $set]]
            [schema.core :as sc]
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

(sc/defschema AssignmentsSearchQuery
  {:searchText (sc/maybe sc/Str)})

(sc/defschema AssignmentsSearchResponse
  {:userTotalCount sc/Int
   :totalCount     sc/Int
   :assignments    [Assignment]})

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

(defn- make-free-text-query [filter-search]
  (let [search-keys [:description] ; and what else?
        fuzzy (ss/fuzzy-re filter-search)]
    {$or (map #(hash-map % {$regex   fuzzy
                            $options "i"})
              search-keys)}))

(defn- make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^([Ll][Pp])-\d{3}-\d{4}-\d{5}$" filter-search)
    {:applicationId (ss/upper-case filter-search)}

    :else
    (make-free-text-query filter-search)))

(defn search-query [data]
  (merge (into {} (map (juxt identity (constantly nil))
                       (keys AssignmentsSearchQuery)))
         (select-keys data (keys AssignmentsSearchQuery))))

(defn- make-query [query]
  (make-text-query (:searchText query)))

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

(sc/defn ^:always-validate assignments-search :- AssignmentsSearchResponse
  [user  :- usr/SessionSummaryUser
   query :- AssignmentsSearchQuery]
  {:userTotalCount 0
   :totalCount     0
   :assignments    (get-assignments user (make-query query))})

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
