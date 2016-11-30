(ns lupapalvelu.assignment
  (:require [clojure.set :refer [rename-keys]]
            [monger.operators :refer [$and $in $ne $options $or $regex $set $push]]
            [monger.collection :as collection]
            [taoensso.timbre :as timbre :refer [errorf]]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.application-utils :as app-utils]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]))

(defonce ^:private registered-assignment-targets (atom {}))

(sc/defschema Target
  {:id                               sc/Str
   :type-key                         sc/Str
   (sc/optional-key :info-key)       sc/Str          ; localization key for additional target info
   (sc/optional-key :description)    sc/Str})        ; localized description for additional target info

(sc/defschema TargetGroup
  (sc/pair sc/Keyword "Group name" [Target] "Targets"))

(defn register-assignment-target! [target-group target-descriptor-fn]
  {:pre [(fn? target-descriptor-fn)]}
  (swap! registered-assignment-targets assoc (keyword target-group) target-descriptor-fn))

(defn assignment-targets [application]
  (map (fn [[group descriptor]] [group (descriptor application)]) @registered-assignment-targets))

;; Helpers and schemas

(defn- assignment-in-user-organization-query [user]
  {:application.organization {$in (usr/organization-ids-by-roles user #{:authority})}})

(defn- organization-query-for-user [user query]
  (merge query (assignment-in-user-organization-query user)))

(def assignment-statuses
  "Assignment is active, when it's parent application is active.
   When application is canceled, also assignment status is set to canceled."
  #{"active" "canceled"})

(def assignment-state-types #{"created" "completed"})

(sc/defschema AssignmentState
  {:type (apply sc/enum assignment-state-types)
   :user usr/SummaryUser
   :timestamp ssc/Timestamp})

(sc/defschema Assignment
  {:id             ssc/ObjectIdStr
   :application    {:id           ssc/ApplicationId
                    :organization sc/Str
                    :address      sc/Str
                    :municipality sc/Str}
   :target         {:group                         sc/Str
                    :id                            ssc/ObjectIdStr
                    (sc/optional-key :type-key)    sc/Str
                    (sc/optional-key :info-key)    sc/Str
                    (sc/optional-key :description) sc/Str}
   :recipient      usr/SummaryUser
   :status         (apply sc/enum assignment-statuses)
   :states         [AssignmentState]
   :description    sc/Str})

(sc/defschema NewAssignment
  (-> (select-keys Assignment [:application :description :recipient :target])
      (assoc :state AssignmentState)))

(sc/defschema UpdateAssignment
  (select-keys Assignment [:recipient :description]))

(sc/defschema AssignmentsSearchQuery
  {:searchText (sc/maybe sc/Str)
   :state (apply sc/enum "all" assignment-state-types)
   :operation [(sc/maybe sc/Str)] ; allows for empty filter vector
   :recipient [(sc/maybe sc/Str)]
   :area [(sc/maybe sc/Str)]
   :createdDate (sc/maybe {:start (sc/maybe ssc/Timestamp)
                           :end   (sc/maybe ssc/Timestamp)})
   :targetType [(sc/maybe sc/Str)]
   :sort {:asc sc/Bool
          :field sc/Str}
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
  (let [search-keys [:description :applicationDetails.address :applicationDetails._id
                     :applicationDetails.verdicts.kuntalupatunnus]
        fuzzy       (ss/fuzzy-re filter-search)
        ops         (app-utils/operation-names filter-search)]
    {$or (concat
           (map #(hash-map % {$regex   fuzzy
                            $options "i"})
              search-keys)
           [{:applicationDetails.primaryOperation.name {$in ops}}
            {:applicationDetails.secondaryOperations.name {$in ops}}])}))

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
          :operation nil
          :area nil
          :createdDate nil
          :targetType nil
          :skip   0
          :limit  100
          :sort   {:asc true :field "created"}}
         (select-keys data (keys AssignmentsSearchQuery))))

(defn- make-query [query {:keys [searchText state recipient operation area createdDate targetType]} user]
  {$and
   (filter seq
           [query
            (when-not (ss/blank? searchText) (make-text-query (ss/trim searchText)))
            (when-not (empty? operation)
              {:applicationDetails.primaryOperation.name {$in operation}})
            (when-not (empty? recipient)
              {:recipient.id {$in recipient}})
            (when-not (empty? area)
              (app-utils/make-area-query area user :applicationDetails))
            (when-not (empty? createdDate)
              {:states.0.timestamp {"$gte" (or (:start createdDate) 0)
                                    "$lt"  (or (:end createdDate) (tc/to-long (t/now)))}})
            (when-not (empty? targetType)
              {:target.group {$in targetType}})
            {:status {$ne "canceled"}}])})


(defn sort-query [sort]
   (let [dir (if (:asc sort) 1 -1)]
      {(:field sort) dir}))

(defn search [{state :state} mongo-query skip limit sort]
  (try
    (let [aggregate (->> [{"$lookup" {:from :applications
                                      :localField "application.id"
                                      :foreignField "_id"
                                      :as "applicationDetails"}}
                          {"$unwind" "$applicationDetails"}
                          {"$match"  mongo-query}
                          {"$project"
                           ;; pull the creation state to root of document for sorting purposes
                           ;; it might also be possible to use :document "$$ROOT" in aggregation
                           {:currentState   {"$arrayElemAt" ["$states" -1]} ;; for sorting
                            :created        {"$arrayElemAt" ["$states" 0]}
                            :description-ci {"$toLower" "$description"} ;; for sorting
                            :application    {:id "$applicationDetails._id"
                                             :organization "$applicationDetails.organization"
                                             :address "$applicationDetails.address"
                                             :municipality "$applicationDetails.municipality"}
                            :target         "$target"
                            :recipient      "$recipient"
                            :status         "$status"
                            :states         "$states"
                            :description    "$description"}}
                          (when (and (string? state) (not= "all" state))
                            {"$match" {:currentState.type state}})
                          {"$sort" (sort-query sort)}]
                         (remove nil?))
          res (collection/aggregate (mongo/get-db) "assignments" aggregate)
          converted
             (map
                 #(dissoc % :description-ci :created :currentState)
                 (map #(rename-keys % {:_id :id}) res))]
      converted)
    (catch com.mongodb.MongoException e
      (errorf "Assignment search query=%s failed: %s" mongo-query e)
      (fail! :error.unknown))))

(defn- get-targets-for-applications [application-ids]
  (->> (mongo/select :applications {:_id {$in (set application-ids)}} [:documents :attachments :primaryOperation :secondaryOperations])
       (util/key-by :id)
       (util/map-values (comp (partial into {}) assignment-targets))))

(defn- enrich-assignment-target [application-targets assignment]
  (let [group-targets (-> assignment :target :group keyword application-targets)]
    (update assignment :target #(merge % (util/find-by-id (:id %) group-targets)))))

(defn- enrich-targets [assignments]
  (let [app-id->targets (->> (map (comp :id :application) assignments)
                             get-targets-for-applications)]
    (map #(enrich-assignment-target (-> % :application :id app-id->targets) %) assignments)))

(sc/defn ^:always-validate get-assignments :- [Assignment]
  ([user :- usr/SessionSummaryUser]
   (get-assignments user {}))
  ([user query]
   (->> (mongo/select :assignments (organization-query-for-user user query))
        (enrich-targets)))
  ([user query projection]
   (->> (mongo/select :assignments (organization-query-for-user user query) projection)
        (enrich-targets))))

(sc/defn ^:always-validate get-assignment :- (sc/maybe Assignment)
  [user           :- usr/SessionSummaryUser
   application-id :- ssc/ObjectIdStr]
  (first (get-assignments user {:_id application-id})))

(sc/defn ^:always-validate get-assignments-for-application :- [Assignment]
  [user           :- usr/SessionSummaryUser
   application-id :- sc/Str]
  (get-assignments user {:application.id application-id
                         :status {$ne "canceled"}}))

(sc/defn ^:always-validate assignments-search :- AssignmentsSearchResponse
  [user  :- usr/SessionSummaryUser
   query :- AssignmentsSearchQuery]
  (let [user-query  (organization-query-for-user user {})
        mongo-query (make-query user-query query user)
        assignments (search query
                            mongo-query
                            (util/->long (:skip query))
                            (util/->long (:limit query))
                            (:sort query))]
    {:userTotalCount (mongo/count :assignments)
     ;; https://docs.mongodb.com/v3.0/reference/operator/aggregation/match/#match-perform-a-count
     :totalCount     (count assignments)
     :assignments    (->> assignments
                          (enrich-targets))}))

(sc/defn ^:always-validate count-active-assignments-for-user :- sc/Int
  [{user-id :id}]
  (mongo/count :assignments {:status "active"
                             :states.type {$ne "completed"}
                             :recipient.id user-id}))

;;
;; Inserting and modifying assignments
;;

(sc/defn ^:always-validate insert-assignment :- ssc/ObjectIdStr
  [assignment :- NewAssignment]
  (let [created-assignment (new-assignment assignment)]
    (mongo/insert :assignments created-assignment)
    (:id created-assignment)))

(defn- update-to-db [assignment-id query assignment-changes]
  (mongo/update-n :assignments (assoc query :_id assignment-id) assignment-changes))

(defn- update-assignments [query assignment-changes]
  (mongo/update-n :assignments query assignment-changes :multi true))

(defn count-for-assignment-id [assignment-id]
  (mongo/count :assignments {:_id assignment-id}))

(sc/defn ^:always-validate update-assignment [assignment-id :- ssc/ObjectIdStr
                                              updated-assignment :- UpdateAssignment]
  (update-to-db assignment-id {} {$set updated-assignment}))

(sc/defn ^:always-validate complete-assignment [assignment-id :- ssc/ObjectIdStr
                                                completer     :- usr/SessionSummaryUser
                                                timestamp     :- ssc/Timestamp]
  (update-to-db assignment-id
                     (organization-query-for-user completer {:status "active", :states.type {$ne "completed"}})
                     {$push {:states (new-state "completed" (usr/summary completer) timestamp)}}))

(defn- set-assignments-statuses [query status]
  {:pre [(assignment-statuses status)]}
  (update-assignments query {$set {:status status}}))

(sc/defn ^:always-validate cancel-assignments [application-id :- ssc/ApplicationId]
  (set-assignments-statuses {:application.id application-id} "canceled"))

(sc/defn ^:always-validate activate-assignments [application-id :- ssc/ApplicationId]
  (set-assignments-statuses {:application.id application-id} "active"))

(defn set-assignment-status [application-id target-id status]
  (set-assignments-statuses {:application.id application-id :target.id target-id} status))

(defn remove-assignments-by-target [application-id target-id]
  (mongo/remove-many :assignments {:application.id application-id :target.id target-id}))
