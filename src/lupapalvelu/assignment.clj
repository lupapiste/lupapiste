(ns lupapalvelu.assignment
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clojure.set :refer [rename-keys]]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$and $each $setOnInsert $in $ne $nin $options $or $regex $set $pull $push]]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [error errorf]]))

(defonce ^:private registered-assignment-targets (atom {}))

(defn assignments-enabled?
  "True if the assignments are enabled for the command organization."
  [{:keys [organization]}]
  (boolean (:assignments-enabled (force organization))))

(defn assignments-enabled-for-application
  "Pre-checker version of `assignments-enabled?`. Fails if the assignments are not enabled."
  [command]
  (when-not (assignments-enabled? command)
    (fail :error.assignments-not-enabled)))

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

(defn targeting-assignments
  "Given assignments and attachment, retuns assignments that target attachment"
  [assignments attachment]
  (->> assignments
       (filter #((set (map :id (:targets %))) (:id attachment)))))

(defn assignment-tag [tag-id]
  (str "assignment-" tag-id))

(defn- assignment-in-user-organization-query [user]
  {:application.organization {$in (usr/organization-ids-by-roles user #{:authority :digitizer})}})

(defn- organization-query-for-user [user query]
  (merge query (assignment-in-user-organization-query user)))

(def assignment-statuses
  "Assignment is active, when it's parent application is active.
   When application is canceled, also assignment status is set to canceled."
  #{"active" "canceled"})

(def assignment-state-types #{"created" "completed" "targets-added"})

(sc/defschema AssignmentState
  {:type (apply sc/enum assignment-state-types)
   :user usr/SummaryUser
   :timestamp ssc/Timestamp})

(defn completed? [assignment]
  (= "completed" (-> (:states assignment) last :type)))

(def user-created-trigger "user-created")
(def notice-form-trigger  "notice-form")
(def foreman-trigger      "foreman")
(def review-trigger       "review")

(defn- supported-trigger? [trigger]
  (some #(nil? (sc/check % trigger))
        [(sc/enum user-created-trigger notice-form-trigger foreman-trigger review-trigger)
         ssc/ObjectIdStr]))

(sc/defschema AssignmentTriggerId (sc/constrained sc/Str supported-trigger?))

(sc/defschema RecipientRole
  "Handler role id for automatic assignments."
  {(sc/optional-key :roleId) ssc/ObjectIdStr})

(sc/defschema Recipient
  "Assignment recipient. Defined here in order to avoid cyclic dependency."
  (sc/conditional :id (merge usr/SummaryUser
                             RecipientRole
                             {(sc/optional-key :handlerId) ssc/ObjectIdStr})
                  :else RecipientRole))

(sc/defschema Assignment
  {:id                          ssc/ObjectIdStr
   :application                 {:id           ssc/ApplicationId
                                 :organization sc/Str
                                 :address      sc/Str
                                 :municipality sc/Str}
   :trigger                     AssignmentTriggerId
   :targets                     [{:group                         sc/Str
                                  :id                            sc/Str
                                  :timestamp                     ssc/Timestamp
                                  (sc/optional-key :type-key)    sc/Str
                                  (sc/optional-key :info-key)    sc/Str
                                  (sc/optional-key :description) sc/Str}]
   :recipient                   (sc/maybe Recipient)
   :status                      (apply sc/enum assignment-statuses)
   :states                      [AssignmentState]
   :description                 sc/Str
   :modified                    ssc/Timestamp
   (sc/optional-key :filter-id) ssc/ObjectIdStr})

(sc/defschema NewAssignment
  (-> (select-keys Assignment [:application :description :recipient :targets])
      (assoc :state AssignmentState)))

(sc/defschema UpdateAssignment
  (select-keys Assignment [:recipient :description :modified]))

(sc/defschema AssignmentsSearchQuery
  {:searchText  (sc/maybe sc/Str)
   :state       (apply sc/enum "all" assignment-state-types)
   :operation   [(sc/maybe sc/Str)] ; allows for empty filter vector
   :recipient   [(sc/maybe sc/Str)]
   :area        [(sc/maybe sc/Str)]
   :createdDate (sc/maybe {:start (sc/maybe ssc/Timestamp)
                           :end   (sc/maybe ssc/Timestamp)})
   :targetType  [(sc/maybe sc/Str)]
   :sort        {:asc   sc/Bool
                 :field sc/Str}
   :skip        sc/Int
   :limit       sc/Int
   :trigger     (sc/maybe sc/Str)
   :filter-id   (sc/maybe ssc/ObjectIdStr)})

(sc/defschema AssignmentsSearchResponse
  {:totalCount     sc/Int
   :assignments    [Assignment]})

(sc/defn new-state :- AssignmentState
  [type         :- (:type AssignmentState)
   user-summary :- (:user AssignmentState)
   created      :- (:timestamp AssignmentState)]
  {:type type
   :user user-summary
   :timestamp created})

(sc/defn ^:always-validate new-assignment :- Assignment
  ([user        :- usr/SummaryUser
    recipient   :- (sc/maybe Recipient)
    application
    trigger     :- AssignmentTriggerId
    created     :- ssc/Timestamp
    description :- sc/Str
    targets     :- [{:group sc/Str, :id sc/Str}]]
   {:id          (mongo/create-id)
    :status      "active"
    :trigger     trigger
    :application (select-keys application
                              [:id :organization :address :municipality])
    :states      [(new-state "created" user created)]
    :recipient   recipient
    :targets     (map #(assoc % :timestamp created) targets)
    :description description
    :modified    created})
  ([{:keys [user application created]} {:keys [recipient trigger description targets filter-id]}]
   (util/assoc-when (new-assignment (usr/summary user) recipient application
                                    trigger created description targets)
                    :filter-id filter-id)))

;;
;; Querying assignments
;;


(defn- make-free-text-query [filter-search]
  (let [search-keys [:description :applicationDetails.address :applicationDetails._id
                     :applicationDetails.verdicts.kuntalupatunnus]
        fuzzy       (ss/fuzzy-re filter-search)
        ops         (operations/operation-names filter-search)]
    {$or (concat
           (map #(hash-map % {$regex   fuzzy
                            $options "i"})
              search-keys)
           [{:applicationDetails.primaryOperation.name {$in ops}}
            {:applicationDetails.secondaryOperations.name {$in ops}}])}))

(defn- make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^[Ll][PpXx]-\d{3}-\d{4}-\d{5}$" filter-search)
    {:application.id (ss/upper-case filter-search)}

    :else
    (make-free-text-query filter-search)))

(defn search-query [data]
  (->> (select-keys data (keys AssignmentsSearchQuery))
       (merge {:searchText  nil
               :state       "all"
               :recipient   nil
               :operation   nil
               :area        nil
               :createdDate nil
               :targetType  nil
               :skip        0
               :limit       100
               :sort        {:asc true :field "modified"}
               :trigger     nil
               :filter-id   nil})))

(defn- make-query
    "Returns query parameters in two parts:
   - pre-lookup: query conditions that can be executed directly against the assignments collection itself
   - post-lookup: conditions that need data fetched via the assignments->applications lookup stage in aggregation query
                  (basically the ones that are targeted to the :applicationDetails subdocument"
  [{:keys [searchText recipient operation area createdDate targetType trigger filter-id]} user]
  {:pre-lookup (filter seq
                       [{:application.organization {$in (usr/organization-ids-by-roles user #{:authority :digitizer})}}
                        (when-not (empty? recipient)
                         {:recipient.id {$in recipient}})
                        (when-not (empty? createdDate)
                          {:states.0.timestamp {"$gte" (or (:start createdDate) 0)
                                                "$lt"  (or (:end createdDate) (tc/to-long (t/now)))}})
                        (when-not (empty? targetType)
                          {:targets.group {$in targetType}})
                        (when-not (empty? trigger)
                          (case trigger
                            "user-created" {:trigger "user-created"}
                            "any" nil
                            {:trigger {"$ne" "user-created"}}))
                        (when filter-id
                          {:filter-id filter-id})
                        {:status {$ne "canceled"}}])
  :post-lookup (filter seq
                       [(when-not (ss/blank? searchText)
                          (make-text-query (ss/trim searchText)))
                        (when-not (empty? operation)
                          {:applicationDetails.primaryOperation.name {$in operation}})
                        (when-not (empty? area)
                          (app-utils/make-area-query area user :applicationDetails))])})

(defn sort-query [sort]
   (let [dir (if (:asc sort) 1 -1)]
      {(:field sort) dir}))

(defn match-state [state]
  (cond (= state "created")
        {"$match" {$or [{:currentState.type "created"}
                        {:currentState.type "targets-added"}]}}

        (= state "all") nil

        :else {"$match" {:currentState.type state}}))

(defn search [{state :state} {:keys [pre-lookup post-lookup] :as mongo-query} skip limit sort]
  (try
    (let [aggregate (->> [(when-not (empty? pre-lookup)
                            {"$match" {$and pre-lookup}})
                          {"$lookup" {:from :applications
                                      :localField "application.id"
                                      :foreignField "_id"
                                      :as "applicationDetails"}}
                          {"$unwind" "$applicationDetails"}
                          (when-not (empty? post-lookup)
                            {"$match" {$and post-lookup}})
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
                            :targets        "$targets"
                            :trigger        "$trigger"
                            :filter-id      "$filter-id"
                            :recipient      "$recipient"
                            :status         "$status"
                            :states         "$states"
                            :description    "$description"
                            :modified       "$modified"}}
                          (match-state state)
                          {"$sort" (sort-query sort)}]
                         (remove nil?))
          res (mongo/aggregate "assignments" aggregate)
          converted
             (map
                 #(dissoc % :description-ci :created :currentState)
                 (map #(rename-keys % {:_id :id}) res))]
      {:count       (count converted)
       :assignments (->> converted (drop skip) (take limit))})
    (catch com.mongodb.MongoException e
      (errorf "Assignment search query=%s failed: %s" mongo-query e)
      (fail! :error.unknown))))

(defn- get-targets-for-applications [application-ids]
  (->> (mongo/select :applications {:_id {$in (set application-ids)}} [:documents :attachments :primaryOperation :secondaryOperations])
       (util/key-by :id)
       (util/map-values (comp (partial into {}) assignment-targets))))

(defn- enrich-assignment-target [application-targets assignment]
  (update assignment :targets
          (partial map
                   #(merge % (util/find-by-id (:id %)
                                              (-> %
                                                  :group
                                                  keyword
                                                  application-targets))))))

(defn- enrich-targets [assignments]
  (let [app-id->targets (->> (map (comp :id :application) assignments)
                             get-targets-for-applications)]
    (map #(enrich-assignment-target (-> % :application :id app-id->targets) %) assignments)))

(sc/defn ^:always-validate get-assignments :- [Assignment]
  ([user :- usr/SessionSummaryUser]
   (get-assignments user {}))
  ([user query]
   (->> (mongo/select-ordered :assignments (organization-query-for-user user query) {:modified 1})
        (enrich-targets))))

(sc/defn ^:always-validate get-assignments-for-application :- [Assignment]
  [user           :- usr/SessionSummaryUser
   application-id :- sc/Str]
  (get-assignments user {:application.id application-id
                         :status         {$ne "canceled"}}))

(sc/defn ^:always-validate assignments-search :- AssignmentsSearchResponse
  [user  :- usr/SessionSummaryUser
   query :- AssignmentsSearchQuery]
  (let [mongo-query (make-query query user)
        assignments-result (search query
                                   mongo-query
                                   (util/->long (:skip query))
                                   (util/->long (:limit query))
                                   (:sort query))]
    {;; https://docs.mongodb.com/v3.0/reference/operator/aggregation/match/#match-perform-a-count
     :totalCount     (:count assignments-result)
     :assignments    (:assignments assignments-result)}))

(sc/defn ^:always-validate count-active-assignments-for-user :- sc/Int
  [{user-id :id}]
  (mongo/count :assignments {:status "active"
                             :states.type {$ne "completed"}
                             :recipient.id user-id}))

;;
;; Inserting and modifying assignments
;;

(sc/defn ^:always-validate insert-assignment :- ssc/ObjectIdStr
  [assignment :- Assignment]
  (mongo/insert :assignments assignment)
  (:id assignment))

(defn- update-to-db [assignment-id query assignment-changes]
  (mongo/update-by-query :assignments (assoc query :_id assignment-id) assignment-changes))

(defn update-assignments [query assignment-changes]
  (mongo/update-by-query :assignments query assignment-changes))

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
                {$push {:states (new-state "completed" (usr/summary completer) timestamp)}
                 $set  {:modified timestamp}}))

(defn- set-assignments-statuses [query status]
  {:pre [(assignment-statuses status)]}
  (update-assignments query {$set {:status   status
                                   :modified (now)}}))

(sc/defn ^:always-validate cancel-assignments [application-id :- ssc/ApplicationId]
  (set-assignments-statuses {:application.id application-id} "canceled"))

(sc/defn ^:always-validate activate-assignments [application-id :- ssc/ApplicationId]
  (set-assignments-statuses {:application.id application-id} "active"))

(defn set-assignment-status [application-id target-id status]
  (set-assignments-statuses {:application.id application-id :targets.id target-id} status))

(defn remove-target-from-assignments
  "Removes given target from assignments, then removes assignments left with no target"
  [application-id target-id]
  (mongo/update-by-query :assignments
                         {:application.id application-id
                          :targets.id     target-id}
                         {$pull {:targets {:id target-id}}})
  (mongo/remove-many :assignments {:application.id application-id
                                   :targets        []}))

(defn- ->target
  ([group {:keys [id]}]
   {:id    id
    :group group})
  ([group timestamp {:keys [id]}]
   {:id        id
    :group     group
    :timestamp timestamp}))

(sc/defn ^:always-validate create-recipient :- (sc/maybe Recipient)
  [role-id handler]
  (merge (when role-id
           {:roleId role-id})
         (when handler
           (let [{:keys [username role]} (usr/get-user-by-id (:userId handler))]
             {:id        (:userId handler)
              :username  username
              :firstName (:firstName handler)
              :lastName  (:lastName handler)
              :role      role
              :handlerId (:id handler)}))))

(defn recipient [trigger application]
  (when-let [role-id (get-in trigger [:handlerRole :id])]
    (create-recipient role-id
                      (util/find-by-key :roleId
                                        role-id
                                        (:handlers application)))))

(defn upsert-assignment-targets
  [user application {:keys [filter-id filter-name recipient]} timestamp assignment-group targets]
  (let [query  {:application.id (:id application)
                :status         "active"
                :states.type    {$nin ["completed"]}
                :trigger        filter-id
                :filter-id      filter-id}
        update {$push {:targets {$each (map (partial ->target assignment-group timestamp)
                                            targets)}
                       :states  (new-state "targets-added"
                                           user
                                           timestamp)}
                $set  {:recipient recipient
                       :modified  timestamp}}]

    ; As of Mongo 3.4, the below cannot be implemented using $setOnInsert due to write conflicts.
    ; https://jira.mongodb.org/browse/SERVER-10711
    (when (not (pos? (mongo/update-by-query :assignments query update)))
      (try (insert-assignment (new-assignment {:user user :application application :created timestamp}
                                              {:recipient   recipient
                                               :trigger     filter-id
                                               :description filter-name
                                               :targets     (map (partial ->target assignment-group)
                                                                 targets)
                                               :filter-id   filter-id}))
           ; Try again in case the assignment was inserted between the previous update and insert call attempts
           (catch Exception _
             (try (mongo/update-by-query :assignments query update)
                  (catch Exception e
                    (error "could not upsert assignment targets for filter " filter-id filter-name
                           " and application " (:id application) ": " (.getMessage e)))))))))


(defn change-assignment-recipient [app-id role-id handler]
  (let [query  {:application.id   app-id
                :status           "active"
                :states.type      {$nin ["completed"]}
                :trigger          {$nin [user-created-trigger]}
                :recipient.roleId role-id}
        update {$set {:recipient (create-recipient role-id handler)
                      :modified  (now)}}]
    (mongo/update-by-query :assignments query update)))

(defn remove-application-assignments
  "Removes every assignment for the given application id."
  [app-id]
  (mongo/remove-many :assignments {:application.id app-id}))
