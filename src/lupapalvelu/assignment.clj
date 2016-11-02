(ns lupapalvelu.assignment
  (:require [clojure.set :refer [rename-keys]]
            [monger.operators :refer [$and $in $ne $options $or $regex $set $push]]
            [monger.query :as query]
            [taoensso.timbre :as timbre :refer [errorf]]
            [schema.core :as sc]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]))

;; Helpers and schemas

(defn- assignment-in-user-organization-query [user]
  {:application.organization {$in (usr/organization-ids-by-roles user #{:authority})}})

(defn- organization-query-for-user [user query]
  (merge query (assignment-in-user-organization-query user)))

(def assignment-statuses
  "Assignment is active, when it's parent application is active.
   When application is canceled, also assignment status is set to canceled."
  ["active" "canceled"])

(def assignment-state-types ["created" "completed"])

(sc/defschema AssignmentState
  {:type (apply sc/enum assignment-state-types)
   :user usr/SummaryUser
   :timestamp ssc/Timestamp})

(sc/defschema Assignment
  {:id             ssc/ObjectIdStr
   :application    {:id           sc/Str
                    :organization sc/Str
                    :address      sc/Str
                    :municipality sc/Str}
   :target         sc/Any
   :recipient      usr/SummaryUser
   :status         (apply sc/enum assignment-statuses)
   :states         [AssignmentState]
   :description    sc/Str})

(sc/defschema NewAssignment
  (-> (select-keys Assignment [:application :description :recipient :target])
      (assoc :state AssignmentState)))

(sc/defschema AssignmentsSearchQuery
  {:searchText (sc/maybe sc/Str)
   :state (apply sc/enum "all" assignment-state-types)
   :recipient (sc/maybe sc/Str)
   :skip   sc/Int
   :limit  sc/Int})

(sc/defschema AssignmentsSearchResponse
  {:userTotalCount sc/Int
   :totalCount     sc/Int
   :assignments    [Assignment]})

(sc/defn ^:private new-assignment :- Assignment
  [assignment :- NewAssignment]
  (-> assignment
      (assoc :states [(:state assignment)])                 ; initial state
      (dissoc :state)
      (merge {:id        (mongo/create-id)
              :status    "active"})))

(sc/defn new-state :- AssignmentState
  [type         :- (:type AssignmentState)
   user-summary :- (:user AssignmentState)
   created      :- (:timestamp AssignmentState)]
  {:type type
   :user user-summary
   :timestamp created})

;;
;; Querying assignments
;;

(defn- make-free-text-query [filter-search]
  (let [search-keys [:description]
        fuzzy (ss/fuzzy-re filter-search)]
    {$or (map #(hash-map % {$regex   fuzzy
                            $options "i"})
              search-keys)}))

(defn- make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^([Ll][Pp])-\d{3}-\d{4}-\d{5}$" filter-search)
    {:application.id (ss/upper-case filter-search)}

    :else
    (make-free-text-query filter-search)))

(defn search-query [data]
  (merge {:searchText nil
          :state "all"
          :recipient nil
          :skip   0
          :limit  100}
         (select-keys data (keys AssignmentsSearchQuery))))

(defn- make-query [query {:keys [searchText state recipient]}]
  {$and
   (filter seq
           [query
            (when-not (ss/blank? searchText) (make-text-query (ss/trim searchText)))
            (when-not (ss/blank? recipient)
              {:recipient.username recipient})
            (if (= state "all")
              {:status {$ne "canceled"}}
              {:status {$ne "canceled"}
               :states.type state})])})

(defn search [query skip limit]
  (try
    (->> (mongo/with-collection "assignments"
           (query/find query)
           (query/skip skip)
           (query/limit limit))
         (map #(rename-keys % {:_id :id})))
    (catch com.mongodb.MongoException e
      (errorf "Assignment search query=%s failed: %s" query e)
      (fail! :error.unknown))))

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
  (get-assignments user {:application.id application-id}))

(sc/defn ^:always-validate assignments-search :- AssignmentsSearchResponse
  [user  :- usr/SessionSummaryUser
   query :- AssignmentsSearchQuery]
  (let [user-query  (organization-query-for-user user {})
        mongo-query (make-query user-query query)]
    {:userTotalCount (mongo/count :assignments )
     :totalCount     (mongo/count :assignments mongo-query)
     :assignments    (search mongo-query
                             (util/->long (:skip query))
                             (util/->long (:limit query)))}))

;;
;; Inserting and modifying assignments
;;

(sc/defn ^:always-validate insert-assignment :- ssc/ObjectIdStr
  [assignment :- NewAssignment]
  (let [created-assignment (new-assignment assignment)]
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
                                 :states.type {$ne "completed"}})
   {$push {:states (new-state "completed" (usr/summary completer) timestamp)}}))

(defn display-text-for-document
  "Return localized text for frontend. Text is schema name + accordion-fields if defined."
  [doc lang]
  (let [schema-loc-key (str (get-in doc [:schema-info :name]) "._group_label")
        schema-localized (i18n/localize lang schema-loc-key)
        accordion-datas (schemas/resolve-accordion-field-values doc)]
    (if (seq accordion-datas)
      (str schema-localized " - " (ss/join " " accordion-datas))
      schema-localized)))
