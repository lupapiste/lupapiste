(ns lupapalvelu.statistics.entry
  "Processing entries for supplement requests and delays.

  Entries track requests for additional information (`:request`) and processing delays
  (`:delay`) on applications.

  Entries affect processing time calculations - delay entries pause the processing clock
  until resolved."
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.statistics.core :as stats]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail!]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.coerce :as sco]
            [schema.core :as sc]
            [sade.env :as env]
            [lupapalvelu.states :as states]
            [lupapalvelu.operations :as ops]))

(set! *warn-on-reflection* true)

(sc/defschema ^:private EntryTs
  {:ts   ssc/Timestamp
   :text ssc/NonBlankStr
   :user usr/SummaryUser})

(defn- good-dates?
  "For delays, the `closed-ts` cannot be before `delay-start`."
  ([type delay-start closed-ts]
   (not (and (util/=as-kw type :delay)
             (some-> closed-ts (< delay-start)))))
  ([{:keys [type delay-start closed]}]
   (good-dates? type delay-start (:ts closed))))

(sc/defschema Entry
  (sc/constrained (st/merge
                    ssc/SheriffNotes
                    {:id          ssc/ObjectIdStr
                     :type        stats-sc/EntryType
                     :application {:id           ssc/ApplicationId
                                   :organization org/OrgId}
                     :created     EntryTs
                     :modified    ssc/Timestamp
                     :delay-start ssc/Timestamp}
                    (st/optional-keys
                      {:answered    EntryTs
                       ;; Grounds for delay
                       :explanation ssc/NonBlankStr
                       :closed      (st/assoc EntryTs
                                              (sc/optional-key :text)
                                              ssc/NonBlankStr)
                       :deleted     sc/Bool
                       :history     [{:user   usr/SummaryUser
                                      :ts     ssc/Timestamp
                                      :update stats-sc/EntryUpdate}]
                       :seen-by     [{:user-id ssc/NonBlankStr
                                      :ts      ssc/Timestamp}]}))
                  good-dates?
                  "For delays, the closed-ts cannot be before delay-start."))

(def ->entry (sco/coercer! Entry sco/json-coercion-matcher))

(defn- check-dates!
  "Nil on success, `fail!` when delay dates are bad."
  ([type delay-start closed-ts]
   (when-not (good-dates? type delay-start closed-ts)
     (fail! :error.invalid-date)))
  ([{:keys [type delay-start closed]}]
   (check-dates! type delay-start (:ts closed))))

(defn- entry-ts->frontend [k {:keys [ts text user]}]
  (letfn [(->kw [field] (keyword (str (name k) "-" (name field))))]
    (util/assoc-when {(->kw :ts)   ts
                      (->kw :name) (ss/trim-join " "
                                                 (:firstName user)
                                                 (:lastName user))}
                     (->kw :text) text)))

(defn- entry->frontend [{:keys [user]} {:keys [seen-by deleted] :as entry}]
  (->> (select-keys entry [:created :answered :closed])
       (map #(apply entry-ts->frontend %))
       (into {})
       (merge (-> (select-keys entry [:id :type :delay-start :explanation
                                      :modified])
                  (util/assoc-when
                    :deleted? deleted
                    :seen-by? (some->> (seq seen-by)
                                       (util/find-by-key :user-id (:id user))
                                       boolean))))
       stats-sc/->fronted-entry))

(defn- make-entry-ts
  ([org-id user ts text]
   (let [user (usr/summary (or user (usr/batchrun-user [org-id])))
         ts   (date/timestamp (or ts (date/now)))
         text (ss/trim-or-nil text)]
     (util/strip-nils (util/mmap user ts text))))
  ([org-id user ts]
   (make-entry-ts org-id user ts nil))
  ([org-id user]
   (make-entry-ts org-id user nil))
  ([org-id]
   (make-entry-ts org-id nil)))

(sc/defn ^:always-validate make-entry :- Entry
  "Creates a new entry from application, user, timestamp and update data.
  Two-arity version extracts these from a command map."
  ([{:keys [id organization]} user created-ts
    {:keys [type delay-start created-text explanation
            closed? closed-ts closed-text]} :- stats-sc/EntryUpdate]
   (-> (cond-> {:id          (mongo/create-id)
                :type        type
                :application {:id id :organization organization}
                :created     (make-entry-ts organization user created-ts created-text)
                :modified    created-ts
                :delay-start (date/timestamp delay-start)
                :seen-by     [{:user-id (:id user)
                               :ts      created-ts}]}
         closed? (assoc :closed (make-entry-ts organization user closed-ts closed-text)))
       (util/assoc-when :explanation (ss/trim-or-nil explanation))
       ->entry))
  ([{:keys [application user created]} updates]
   (make-entry application user created updates)))

(defn- entry-ts-update [user entry k flag? ts text]
  (let [entry-ts (k entry)
        ts       (date/timestamp ts)
        trimmed  (ss/trim-or-nil text)]
    (cond
      (true? flag?)                     {:in {k (make-entry-ts nil user ts trimmed)}}
      (and entry-ts (false? flag?))     {:out k}
      (and entry-ts (or ts text))
      (let [txt-path (util/kw-path k :text)]
        (cond-> {:in (util/assoc-when {(util/kw-path k :user) user}
                                      (util/kw-path k :ts) ts)}
          (and text trimmed)
          (assoc-in [:in txt-path] trimmed)
          (and text (not trimmed))
          (assoc :out txt-path))))))

(defn- entry-flag-update [entry k flag?]
  (let [old? (k entry)]
    (cond
      (= old? flag?)                 nil
      (and (true? flag?) (not old?)) {:in {k true}}
      (and (false? flag?) old?)      {:out k})))

(defn- explanation-update [explanation]
  (when (string? explanation)
    (if-let [explanation (ss/trim-or-nil explanation)]
      {:in (util/mmap explanation)}
      {:out :explanation})))

(sc/defn ^:private ^:always-validate entry-updates
  :- (sc/maybe (st/optional-keys {:in {sc/Keyword sc/Any} :out #{sc/Keyword}}))
  [{:keys [application] :as entry} user
   {:keys [created-text deleted? delay-start
           answered? answered-ts answered-text explanation
           closed? closed-ts closed-text]} :- stats-sc/EntryUpdate]
  (check-dates! (:type entry)
                (or delay-start (:delay-start entry))
                (or closed-ts (some-> entry :closed :ts)))
  (let [user (or user (usr/batchrun-user [(:organization application)]))
        xs   [{:in (util/assoc-when {}
                                    :created.text (ss/trim-or-nil created-text)
                                    :delay-start delay-start)}
              (entry-flag-update entry :deleted deleted?)
              (entry-ts-update user entry :answered answered? answered-ts answered-text)
              (entry-ts-update user entry :closed closed? closed-ts closed-text)
              (explanation-update explanation)]]
    (-> {:in  (some->> xs (keep :in) seq (apply merge)
                       (filter (fn [[kwp v]]
                                 (let [path (util/split-kw-path kwp )]
                                   (not= v (get-in entry path)))))
                       seq
                       (into {}))
         :out (some->> xs (keep :out) seq set)}
        util/strip-nils
        not-empty)))

(defn- command-entry-id
  [{:keys [::entry data]}]
  (or (:id entry) (:entry-id data)))

(defn- entry-command? [command]
  (boolean (command-entry-id command)))

(defn allowed-actions
  "Returns a map of entry-id to allowed actions for the entry in the given command context.
  Two-arity version allows passing an explicit entry."
  ([command]
   (when-let [entry-id (command-entry-id command)]
     {entry-id (action/allowed-category-actions-for-command :processing-entries
                                                            command)}))
  ([command entry]
   (allowed-actions (assoc command ::entry entry))))

;; ----------------------------------------------
;; Mongo
;; ----------------------------------------------

(defn- entry-query [application entry-id]
  {:_id            entry-id
   :application.id (:id application)})

(defn- command->entry-query
  ([{:keys [application data]} extras]
   (merge extras (entry-query application (:entry-id data))))
  ([command]
   (command->entry-query command nil)))

(defn command->entry
  "Fetches entry for the command from mongo, or returns cached `::entry` if present.
  Fails with `:error.not-found` if entry doesn't exist."
  ([{:keys [::entry] :as command} extras]
   (when (entry-command? command)
     (or (some-> entry ->entry) ; See `allowed-actions` above
         (some->> (command->entry-query command extras)
                  (mongo/select-one :processing-entries)
                  ->entry)
         (fail! :error.not-found))))
  ([command]
   (command->entry command nil)))

(defn insert-new-entry
  "Creates and inserts a new entry from command data. Returns the new entry id."
  [{:keys [data] :as command} entry-type]
  (let [entry (->> (assoc data :type entry-type)
                   stats-sc/->entry-update
                   (make-entry command))]
    (mongo/insert :processing-entries entry)
    (:id entry)))

(defn update-entry
  "Updates an existing entry with command data. Tracks changes in history and resets
  seen-by to current user. Returns entry id on success, nil if no changes needed."
  ([{:keys [data user created] :as command} extras]
   (let [user           (usr/summary user)
         entry          (command->entry command)
         eu             (stats-sc/->entry-update (merge data extras))
         {:keys [in out]
          :as   in-out} (entry-updates entry user eu)
         updates        (when in-out
                          (util/assoc-when {$push {:history {:ts     created
                                                             :user   user
                                                             :update eu}}
                                            $set  (merge in
                                                         {:modified created
                                                          :seen-by  [{:user-id (:id user)
                                                                      :ts      created}]})}
                                           $unset (some-> out (zipmap (repeat true)))))]
     (when updates
       (when (pos? (mongo/update-by-query :processing-entries
                                          (command->entry-query command)
                                          updates))
         (:id entry)))))
  ([command] (update-entry command nil)))

(defn- application-entries [{app-id :id} extras]
  (->> (merge extras {:application.id app-id})
       (mongo/select :processing-entries)
       (mapv ->entry)))

(defn delay-entries
  "Returns active delay entries for the application up to cutoff timestamp.
  Excludes deleted entries and those answered/closed before submission."
  ([{:keys [submitted] :as application} cutoff-ts]
   (application-entries application {:type        :delay
                                     :deleted     {$ne true}
                                     :delay-start {$lt cutoff-ts}
                                     :answered.ts {$not {$lt submitted}}
                                     :closed.ts   {$not {$lt submitted}}}))
  ([application]
   (delay-entries application (stats/cutoff-ts application))))

(defn delay-end-ts
  "Delay end timestamp or nil."
  [entry]
  (some->> entry :closed :ts))

(defn- merge-ranges
  "Takes a collection of [start end] vectors and returns a sorted
   vector of merged, non-overlapping ranges. Nil for empty ranges."
  [ranges]
  (some->> (seq ranges)
           (sort-by first)
           (reduce (fn [acc [curr-start curr-end]]
                     (if (empty? acc)
                       (conj acc [curr-start curr-end])
                       (let [[prev-start prev-end] (peek acc)]
                         ;; If current start is <= previous end, they overlap or touch
                         (if (<= curr-start prev-end)
                           (conj (pop acc) [prev-start (max prev-end curr-end)])
                           (conj acc [curr-start curr-end])))))
                   [])))

(defn delay-ranges
  "Combines `application` delays into non-overlapping ranges. Each range is a two-item
  integer vector, where the first item is less than the last. Each range is
  truncated/filtered so that they are within [`submitted` `cutoff-ts`] range."
  ([{:keys [submitted] :as application} cutoff-ts]
   (->> (delay-entries application cutoff-ts)
        (keep (fn [{:keys [delay-start] :as entry}]
                (let [start (max delay-start submitted)
                      end   (min (or (delay-end-ts entry) cutoff-ts)
                                 cutoff-ts)]
                  (when (< start end)
                    [start end]))))
        merge-ranges))
  ([application]
   (delay-ranges application (stats/cutoff-ts application))))

(defn delay-ranges->ms
  "Total amount of time (in milliseconds) spent in delays. `ranges` is `delay-ranges`
  result. Zero if no delays."
  [ranges]
  (reduce (fn [acc [start end]]
            (+ acc (- end start)))
          0
          ranges))

(defn delayed?
  "True if `zoned` is within any delay range `ranges`."
  [ranges zoned]
  (boolean (some (fn [[start end]]
                   (date/before? start zoned end))
                 ranges)))

(defn frontend-entries+auth-models
  "Returns all entries for the application formatted for frontend display,
  along with auth-models for each entry. Non-authorities see only non-deleted entries."
  [{:keys [application] :as command}]
  (let [entries (->> (when-not (auth/application-authority? command)
                       {:deleted {$ne true}})
                     (application-entries application))]
    {:entries     (mapv (partial entry->frontend command) entries)
     :auth-models (apply merge (keep #(allowed-actions command %) entries))}))

(defn frontend-entry+auth-model
  "Returns a single entry formatted for frontend with its auth-model."
  ([command]
   (let [entry (command->entry command)]
     {:entry      (entry->frontend command entry)
      :auth-model (allowed-actions command entry)}))
  ([command  entry-id]
   (frontend-entry+auth-model (assoc-in command [:data :entry-id] entry-id))))

(defn application-open-entry-count
  "Counts entries that are neither answered, closed, nor deleted."
  [{app-id :id}]
  (mongo/count :processing-entries {:application.id app-id
                                    :closed         {$exists false}
                                    :answered       {$exists false}
                                    :deleted        {$ne true}}))

(defn entries?
  "True if application has any non-deleted entries."
  [{:keys [application]}]
  (mongo/any? :processing-entries {:application.id (:id application)
                                   :deleted       {$ne true}}))

(defn toggle-seen
  [{:keys [application user created]} seen? entry-id-or-ids]
  (let [q       {:_id            (util/pcond->> entry-id-or-ids
                                   coll? (hash-map $in))
                 :application.id (:id application)}
        user-id (:id user)]
    ;; Remove old bookkeeping entries just in case before adding new ones.
    (mongo/update-by-query :processing-entries
                           (assoc q :seen-by.user-id user-id)
                           {$pull {:seen-by {:user-id user-id}}})
    (when seen?
      (mongo/update-by-query :processing-entries
                             q
                             {$push {:seen-by {:user-id user-id
                                               :ts      created}}}))))

(defn unseen-entries?
  "True if application has non-deleted entries not yet seen by the user."
  [{:keys [user application]}]
  (mongo/any? :processing-entries {:application.id  (:id application)
                                   :deleted         {$ne true}
                                   :seen-by.user-id {$ne (:id user)}}))

(defn applications-with-unseen-entries
  "Filters `application-ids` by unseen entries. Returns a set of application ids that have
  _unseen_ entries. Nil, if all seen."
  [user application-ids]
  (when-let [app-id-q (cond
                        (empty? application-ids)      nil
                        (= 1 (count application-ids)) (first application-ids)
                        :else                         {$in application-ids})]
    (some->> (mongo/select :processing-entries {:application.id  app-id-q
                                                :deleted         {$ne true}
                                                :seen-by.user-id {$ne (:id user)}}
                           [:application.id])
             seq
             (map (comp :id :application))
             set)))

(defn check-entry
  "Pre-checker that fails if the entry does not exist or any of the keyword flags does not
  match. Each `kws` is a keyword argument with boolean value. Supported flags are
  `:request?`, `:answered?`, `:deleted?` and `closed?`."
  [& kws]
  (fn [command]
    (when-let [entry (command->entry command)]
      (some (fn [[k v]]
              (let [check (case k
                             :request?  (= :request (:type entry))
                             :answered? (:answered entry)
                             :deleted?  (:deleted entry)
                             :closed?   (:closed entry))]
                (when-not (= (boolean check) (boolean v))
                  (fail :error.entry))))
            (apply hash-map kws)))))

(defn entries-application?
  "Most of the R and all P applications support entries, even if they do not support
  processing."
  [application]
  (and
    (env/feature?  :statistics-processing)
    (util/includes-as-kw? #{:R :P} (:permitType application))
    (not (:facta-imported application))
    (not (:infoRequest application))
    (not (-> application :primaryOperation :name
             (ops/get-operation-metadata :no-requests)))))

(defn check-entries-tab
  "Pre-checker that fails if the entries tab (Käsittelyaika) should not be visible. The tab
  is always visible if there are entries, but also visible for authority in non-terminal
  and non-draft states."
  [{:keys [application] :as command}]
  (when-not (and (entries-application? application)
                 (or (entries? command)
                     (and (auth/application-authority? command)
                          (util/includes-as-kw? states/all-application-states-but-draft-or-terminal
                                                (:state application)))))
    (fail :error.unauthorized)))
