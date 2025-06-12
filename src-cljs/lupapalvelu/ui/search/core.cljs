(ns lupapalvelu.ui.search.core
  (:require [clojure.set :as set]
            [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.next.session :as session]
            [lupapalvelu.ui.attachment.shared :refer [person-name]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.datepicker :refer [day-begin-ts day-end-ts]]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(def STATES
  "The same as `lupapalvelu.status/all-states`."
  #{:archived :open :closed :foremanVerdictGiven :ready
    :constructionStarted :agreementPrepared :appealed
    :complementNeeded :extinct :hearing :inUse :final
    :finished :sent :submitted :canceled :survey
    :agreementSigned :draft :acknowledged :answered
    :sessionHeld :info :underReview :proposal
    :registered :proposalApproved :sessionProposal
    :verdictGiven :onHold})

(def FOREMAN-STATES
  "Combinataion tj-ilmoitus-state-graph and tj-hakemus-state-graph states from
  `lupapiste-commons.states`."
  #{:open :foremanVerdictGiven :appealed :complementNeeded
    :sent :submitted :canceled :draft :acknowledged})

(def FOREMAN-OPERATIONS
  #{:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2})

(def FILTER-FIELDS
  [:handler-role :areas :handlers :organization-tags :company-tags
   :operations :event-range :organizations :states :sort :recipient
   :created-range :include-converted :include-paper-applications :foreman-roles])

(def VIEWS
  "Every view identifier. Note that some are mutually exlusive"
  #{:applications :company-applications :authority-applications
    :foreman-applications :assignments})

(defn view-filter-keys [view]
  (case view
    :authority-applications
    {:array-key  :applicationFilters ; Array in user
     :id-key     :id                 ; User defautFilter key
     :type-key   :application        ; filterType parameter value
     }
    :foreman-applications
    {:array-key  :foremanFilters
     :id-key     :foremanFilterId
     :type-key   :foreman}
    :new-foreman-applications
    {:array-key  :foremanDocumentFilters
     :id-key     :foremanDocumentFilterId
     :type-key   :foremanDocument}
    :company-applications
    {:array-key  :companyApplicationFilters
     :id-key     :companyFilterId
     :type-key   :company}
    ;; Saved filters are not supported for assignments
    nil))

(defn- select-filter-fields [m]
  (select-keys m FILTER-FIELDS))

(defn authority-view? [view]
  (#{:authority-applications :foreman-applications :assignments} view))

(defn state-text [view state]
  (if (and (authority-view? view) (= state :draft))
    (->> [:draft :open]
         (map common/loc)
         (ss/join " / "))
    (common/loc state)))

(defn- make-state-items [view]
  (->> (case view
         :authority-applications
         ;; draft contains Open for authority.
         (disj STATES :open :foremanVerdictGiven)

         :foreman-applications
         (disj FOREMAN-STATES :open :verdictGiven)

         STATES)
       (map #(hash-map :value % :text (state-text view %)))
       (sort-by :text)
       (into [])))

(def ^:private items-by-state {:authority-applications (make-state-items :authority-applications)
                               :foreman-applications   (make-state-items :foreman-applications)
                               ::other                 (make-state-items nil)})

(defn state-items [view]
  (or (get items-by-state view)
      (get items-by-state ::other)))

(defn- company-tags []
  (common/->cljs (.currentCompanyTags js/lupapisteApp.services.companyTagsService)))

(defn event-range-supported? []
  (<sub [:auth/global? :event-search]))

(defn- bad-event-range? [{:keys [event start-ts end-ts]}]
  (and (ss/not-blank? event) start-ts end-ts (>= start-ts end-ts)))

(def event-range-events
  (->> [:license-period-start      :license-period-end
        :license-started-not-ready :license-ended-not-ready
        :warranty-period-end :announced-to-ready-state-not-ready]
       (map #(hash-map :value %
                       :text (common/loc (util/kw-path :applications.event %))))
       (sort-by :text)))

(defn- bad-created-range? [{:keys [start-ts end-ts]}]
  (and start-ts end-ts (>= start-ts end-ts)))

(defn search-text-param [{:keys [::search-text]}]
  (some->> search-text
           ss/blank-as-nil
           ss/trim
           (hash-map :searchText)))

(defmulti ^:private field-param (fn [_ k v]
                                  (when-not (ss/blank? v)
                                    k)))

;; Blank values are skipped
(defmethod field-param :default
  [_ _ _])

(defmethod field-param :handler-role
  [_ _ role]
  (some->> (case role
             :general true
             :other false
             nil)
           (hash-map :userIsGeneralHandler)))

(defn resolve-ids
  "Extracts those ids in `full-data` (maps with `:id`) that are also in
  `selected-ids`. Returns nil if no suitable ids is found."
  [full-data selected-ids]
  (some->> full-data
           (keep (fn [{id :id}]
                   (when (contains? (set selected-ids) id)
                     id)))
           not-empty))

(defmethod field-param :areas
  [{:keys [::areas]} _ ids]
  {:areas (resolve-ids (mapcat :areas areas) ids)})

(defmethod field-param :handlers
  [{:keys [::handlers]} _ ids]
  {:handlers (if (util/=as-kw :no-authority (first ids))
               ids
               (resolve-ids handlers ids))})

(defmethod field-param :recipient
  [_ _ value]
  {:recipient (case (ss/blank-as-nil value)
                nil              nil
                ("no-one" "all") value
                [value])})

(defmethod field-param :organization-tags
  [{:keys [::organization-tags]} _ ids]
  {:tags (resolve-ids organization-tags ids)})

(defmethod field-param :company-tags
  [_ _ ids]
  {:companyTags (resolve-ids (company-tags) ids)})

(defmethod field-param :operations
  [{:keys [::operations]} _ ids]
  {:operations (resolve-ids operations ids)})

(defmethod field-param :event-range
  [_ _ {:keys [event start-ts end-ts] :as value}]
  (cond
    (not (event-range-supported?)) nil
    (ss/blank? event)              nil
    (bad-event-range? value)       (throw (ex-info "Bad event range" {:value value}))
    :else
    {:event (util/assoc-when {:eventType [event]}
                             :start start-ts
                             :end end-ts)}))

(defmethod field-param :created-range
  [_ _ {:keys [start-ts end-ts] :as value}]
  (cond
    (bad-created-range? value)
    (throw (ex-info "Bad created range" {:value value}))

    (or start-ts end-ts)
    {:createdDate {:start start-ts :end end-ts}}))

(defmethod field-param :organizations
  [{:keys [::organizations]} _ ids]
  {:organizations (resolve-ids organizations ids)})

(defmethod field-param :foreman-roles
  [_ _ ids]
  (when (seq ids)
    {:foremanRoles ids}))

(defmethod field-param :states
  [{:keys [::view]} _ ids]
  (when-let [ids (not-empty (set/intersection (set ids) STATES))]
    {:applicationType "state-filter"
     :states          (cond-> ids
                        (and (authority-view? view) (ids :draft))
                        (conj :open))}))

(defmethod field-param :sort
  [_ _ [field asc]]
  {:sort {:field field :asc asc}})

(defmethod field-param :include-converted
  [_ _ include?]
  {:includeConverted include?})

(defmethod field-param :include-paper-applications
  [_ _ include?]
  {:includePaperApplications include?})

(defn- view-path [view & extras]
  (->> [::views view extras]
       flatten
       (remove nil?)))

(defn- fields-path [view]
  (view-path view :fields))

(defn- field-path [view field]
  (view-path view :fields field))

(defn- filters-path [view]
  (view-path view :filters))

(def compatible-operations-mappings
  "Mappings for which operations should also be added when limiting searches by operations.
  When limiting by the `key` also display all operations in the `value` vector"
  {"sijoittamislupa-v2"                ["sijoittamislupa"]
   "sijoittamislupa-v3"                ["sijoittamislupa" "sijoittamislupa-v2"]
   "puhtaan-siirtyman-sijoittamislupa" ["sijoittamislupa" "sijoittamislupa-v2"]})

(defn add-compatible-operations
  "Add backwards compatible operations.
  When a user searches for an operation like `operation-v2`
  we want to also diplay all previous versions of that same operation type"
  [m]
  (let [ops-to-add (apply concat (vals (select-keys compatible-operations-mappings (:operations m))))]
    (if (seq ops-to-add)
      (update m :operations into ops-to-add)
      m)))

(defn search-params [db view]
  (let [params (->> (get-in db (fields-path view))
                    select-filter-fields
                    (map (fn [[k v]] (field-param db k v)))
                    (apply merge (search-text-param db))
                    add-compatible-operations
                    (util/filter-map-by-val (complement nil?)))]
    (case view
      ;; The special recipient values must be resolved after the params cleanup.
      :assignments (update params :recipient
                           (fn [rep]
                             (case rep
                               nil      [(session/user-id db)]
                               "all"    nil
                               "no-one" [nil]
                               rep)))
      params)))

(defmulti ^:private set-field (fn [_ _ field _] field))

(defmethod set-field :default
  [db view field value]
  {:db       (assoc-in db (field-path view field) value)
   :dispatch [::search]})

(defmethod set-field [:event-range :start-ts]
  [db view field value]
  {:db       (assoc-in db
                       (field-path view field)
                       (day-begin-ts value))
   :dispatch [::search]})

(defmethod set-field [:event-range :end-ts]
  [db view field value]
  {:db       (assoc-in db
                       (field-path view field)
                       (day-end-ts value))
   :dispatch [::search]})

(defmethod set-field [:created-range :start-ts]
  [db view field value]
  {:db       (assoc-in db
                       (field-path view field)
                       (day-begin-ts value))
   :dispatch [::search]})

(defmethod set-field [:created-range :end-ts]
  [db view field value]
  {:db       (assoc-in db
                       (field-path view field)
                       (day-end-ts value))
   :dispatch [::search]})

(defmethod set-field :sort
  [db view field value]
  (let [path (field-path view field)]
    (when-not (= value (get-in db path))
      {:db (assoc-in db path value)
       :dispatch [::search]})))

(defn- current-filter-id [db view]
  (get-in db (view-path view :filter-id)))

(defn- view-filters [db view]
  (get-in db (filters-path view)))

(defn- get-filter [db view filter-id]
  (util/find-by-id filter-id (view-filters db view)))

(defn- get-save-title [db view]
  (get-in db (view-path view :save-title)))

(defn- fields->save-filter-params
  [db view]
  (let [{:keys [role event sort]
         :as   params} (-> (search-params db view)
                           (set/rename-keys {:searchText           :text
                                             :userIsGeneralHandler :role}))
        fltr           (cond-> (dissoc params :event :sort :applicationType)
                         (some? role) (update :role str)
                         event        (assoc :event (:eventType event)))]
    {:filterId   (current-filter-id db view)
     :title      (ss/trim (get-save-title db view))
     :sort       sort
     :filter     fltr
     :filterType (:type-key (view-filter-keys view))}))

;; ---------------------------
;; Initialization functions
;; ---------------------------

(defn- default-sort [view]
  (case view
    :assignments          [:created true]
    :foreman-applications [:submitted false]
    [:modified false]))

(defn- parse-saved-filter
  "Filter `fltr` is originally from mongo."
  [fltr]
  (->> (set/rename-keys (:filter fltr)
                        {:foremanRoles             :foreman-roles
                         :tags                     :organization-tags
                         :companyTags              :company-tags
                         :includeConverted         :include-converted
                         :includePaperApplications :include-paper-applications})
       (map (fn [[k v]]
              (case k
                (:handlers
                 :operations :organizations :areas :id :states
                 :foreman-roles)            (when (seq v)
                                              [k (cond->> v (= k :states) (mapv keyword))])
                :text                       [k (ss/trim v)]
                (:organization-tags
                 :company-tags)             (when (seq v)
                                              [k v])
                :event                      [:event-range {:event (some-> v first keyword)}]
                :role                       (case v
                                              "true" [:handler-role :general]
                                              "false" [:handler-role :other]
                                              nil)
                :include-converted          [k v]
                :include-paper-applications [k v])))
       (into {:id    (:id fltr)
              :title (ss/trim (:title fltr))
              :sort  [(-> fltr :sort :field keyword)
                      (-> fltr :sort :asc)]})))

(defn- user-filters [user view]
  (let [{:keys [array-key id-key]} (view-filter-keys view)]
    {:filters    (some->> array-key
                          (get user)
                          (mapv #(parse-saved-filter %))
                          not-empty)
     :default-id (get-in user [:defaultFilter id-key])}))

(defn- lower-keys [m]
  (util/map-keys (comp keyword ss/lower-case name) m))

(defn all-areas
  "Every area for the current user."
  []
  (some->> (.data js/lupapisteApp.services.areaFilterService)
           common/->cljs
           vals
           not-empty
           (mapv (fn [{:keys [name areas]}]
                   {:organization name
                    :areas        (mapv (fn [a]
                                          {:id   (:id a)
                                           :name (-> a :properties lower-keys :nimi str)})
                                        (:features areas))}))))

(defn handlers []
  (->> (.data js/lupapisteApp.services.handlerFilterService)
       common/->cljs
       (mapv (fn [{:keys [id email] :as h}]
               {:id   id
                :name (or (person-name h) email)}))))

(defn organization-tags []
  (let [items   (-> js/lupapisteApp.services.tagFilterService
                    .data
                    common/->cljs
                    vals)
        groups? (> (count items) 1)
        lang    (keyword (common/get-current-language))]
    (->> items
         (mapcat (fn [{:keys [name tags]}]
                   (cond->> tags
                     groups? (map #(assoc % :group (get name lang))))))
         (into []))))

(defn operations []
  (->> js/lupapisteApp.services.operationFilterService
       .data
       common/->cljs
       (mapcat (fn [[permit-type ops]]
                 (map #(hash-map :id %
                                 :text (common/loc (util/kw-path :operations %))
                                 :group (common/loc permit-type))
                      ops)))
       (remove empty?)
       (into [])))

(defn organizations []
  (->> js/lupapisteApp.services.organizationFilterService
       .data
       common/->cljs
       (map #(set/rename-keys % {:label :text}))
       (sort-by :text)
       (into [])))


(defn recipients
  "Assignment recipients list contains both authorities and two special entries: No
  recipient, All. In the UI the default/empty value denotes the current user's
  assignments."
  [user-id]
  (->> js/lupapisteApp.services.organizationsUsersService
       .users
       common/->cljs
       (keep (fn [{:keys [id fullName]}]
               (when-not (= id user-id)
                 {:id id :text fullName})))
       (sort-by :text)
       (concat [{:id   "no-one"
                 :text (common/loc :applications.search.recipient.no-one)}
                {:id   "all"
                 :text (common/loc :all)}])
       (into [])))

(defn foreman-roles []
  (->> (common/->cljs js/LUPAPISTE.config.foremanRoles)
       (map #(hash-map :text (-> % :i18nkey common/loc)
                       :value (:name %)))
       (sort-by :text)
       (into [])))

(defn- refresh-data [db & ks]
  (reduce (fn [acc-db k]
            (assoc acc-db
                   k
                   (case k
                     ::areas             (all-areas)
                     ::handlers          (handlers)
                     ::organization-tags (organization-tags)
                     ::operations        (operations)
                     ::organizations     (organizations)
                     ::recipients        (recipients (session/user-id db))
                     ::company-tags      (company-tags)
                     ::foreman-roles     (foreman-roles))))
          db
          ks))

(defn- get-view-default-search-text [db view]
  (let [{:keys [filters default-filter-id]} (get-in db [::views view])]
    (some->> filters
             (util/find-by-key :id default-filter-id)
             :text)))

;; ------------------
;; Subs
;; ------------------

(rf/reg-sub
  ::field
  (fn [db [_ view field]]
    (get-in db (field-path view field))))

(rf/reg-sub
  ::search-text
  (fn [db _]
    (::search-text db)))

(rf/reg-sub
  ::bad-event-range?
  (fn [db [_ view]]
    (bad-event-range? (get-in db (field-path view :event-range)))))

(rf/reg-sub
  ::bad-created-range?
  (fn [db [_ view]]
    (bad-created-range? (get-in db (field-path view :created-range)))))

(rf/reg-sub
  ::view-filters
  (fn [db [_ view]]
    (not-empty (view-filters db view))))

(rf/reg-sub
  ::view
  :-> ::view)

(rf/reg-sub
  ::current-filter-id
  (fn [db [_ view]]
    (current-filter-id db view)))

(rf/reg-sub
  ::default-filter-id
  (fn [db [_ view]]
    (get-in db (view-path view :default-filter-id))))

(rf/reg-sub
  ::save-title
  (fn [db [_ view]]
    (get-save-title db view)))

(rf/reg-sub ::areas-data
  :-> ::areas)

(rf/reg-sub ::areas
  :<- [::areas-data]
  (fn [areas _]
    (let [groups? (> (count areas) 1)]
      (->> areas
           (mapcat (fn [{:keys [organization areas]}]
                     (let [org-name (get organization
                                         (keyword (common/get-current-language)))]
                       (cond->> (map #(hash-map :value (:id %)
                                                :text (:name %))
                                     areas)
                         groups? (map #(assoc % :group org-name))))))
           not-empty
           (into [])))))

(rf/reg-sub
  ::handlers
  (fn [db _]
    (->> (::handlers db)
         (map #(set/rename-keys % {:id :value :name :text}))
         (sort-by :text))))

(rf/reg-sub ::organization-tags-data
  :-> ::organization-tags)

(rf/reg-sub ::organization-tags
  :<- [::organization-tags-data]
  (fn [organization-tags _]
    (some->> organization-tags
             (map #(set/rename-keys % {:id :value :label :text}))
             (sort-by (juxt :group :text))
             not-empty
             (into []))))

(rf/reg-sub ::operations-data
  :-> ::operations)

(rf/reg-sub ::operations
  :<- [::operations-data]
  (fn [operations _]
    (->> operations
         (map #(set/rename-keys % {:id :value}))
         (sort-by (juxt :group :text)))))

(rf/reg-sub ::organizations-data
  :-> ::organizations)

(rf/reg-sub ::organizations
  :<- [::organizations-data]
  (fn [organizations _]
    (mapv #(set/rename-keys % {:id :value})
          organizations)))

(rf/reg-sub ::foreman-roles
  :-> ::foreman-roles)

(rf/reg-sub ::recipients-data
  :-> ::recipients)

(rf/reg-sub ::recipients
  :<- [::recipients-data]
  (fn [recipients _]
    (mapv #(set/rename-keys % {:id :value})
          recipients)))

(rf/reg-sub
  ::company-tags
  (fn [db _]
    (::company-tags db)))

(rf/reg-sub
  ::flag?
  (fn [db [_ view flag]]
    (boolean (get-in db (view-path view :flags flag)))))

(rf/reg-sub
  ::title-reserved?
  (fn [db [_ view]]
    (let [fid        (current-filter-id db view)
          save-title (ss/trim (get-in db (view-path view :save-title)))]
      (boolean (some (fn [{:keys [id title]}]
                       (and (= title save-title)
                            (not= id fid)))
                     (view-filters db view))))))

(rf/reg-sub
  ::initialized?
  (fn [db _]
    (some #(get-in db (view-path % :initialized?)) VIEWS)))

;; ------------------
;; Events
;; ------------------

(rf/reg-event-fx
  ::search
  (fn [{db :db} _]
    (if-let [view (::view db)]
      (try
        {:hub/send {:event (if (= view :assignments)
                            "Dashboard::search-assignments"
                            "Dashboard::search-applications")
                    :data  {:fields (cond-> (search-params db view)
                                      (= view :foreman-applications)
                                      (assoc :operations FOREMAN-OPERATIONS))}}}
        (catch :default _
          {}))

      {})))

(rf/reg-event-fx
  ::set-field
  (fn [{db :db} [_ field value]]
    (set-field db (::view db) field value)))

(rf/reg-event-fx
  ::set-search-text
  (fn [{db :db} [_ text]]
    {:db       (assoc db ::search-text text)
     :dispatch [::search]}))

(rf/reg-event-fx
  ::set-view
  (fn [{db :db} [_ view]]
    {:db       (assoc db ::view view ::search-text (get-view-default-search-text db view))
     :dispatch [::search]
     :hub/send {:event "Dashboard::view-changed"
                :data  {:view (case view
                                :foreman-applications     :foreman
                                :new-foreman-applications :new-foreman
                                :assignments              :assignments
                                :company-applications     :company
                                :applications)}}}))

(rf/reg-event-fx
  ::clear-filter
  (fn [{db :db} [_ clear-text? search?]]
    (let [view     (::view db)
          old-sort (get-in db (field-path view :sort))]
      (merge
        {:db (-> db
                 (assoc-in (fields-path view) {:sort old-sort})
                 (update-in (view-path view)
                            dissoc :filter-id :save-title)
                 (cond-> clear-text? (dissoc ::search-text)))}
        (when-not (false? search?)
          {:dispatch [::search]})))))

(rf/reg-event-fx
  ::select-filter
  (fn [{db :db} [_ view filter-id search?]]
    (let [{:keys [title text]
           :as   fltr} (get-filter db view filter-id)]
      (cond
        (ss/blank? filter-id)
        ;; Blank selection clears the filter, but not the search text.
        {:dispatch [::clear-filter false search?]}

        fltr
        (merge
          {:db (-> db
                   (assoc-in (view-path view :filter-id) filter-id)
                   (assoc-in (fields-path view) (select-filter-fields fltr))
                   (assoc-in (view-path view :save-title) title)
                   (cond-> search? (assoc ::search-text text)))}
          (when search?
            {:dispatch [::search]}))

        ;; Filter not found. This could be due to an old filter removal bug.
        :else {}))))

(rf/reg-event-fx
  ::initialize-view
  (fn [{db :db} [_ view]]
    (if (get-in db (view-path view :initialized?))
      {}
      (let [{:keys [filters default-id]} (user-filters (session/user db) view)]
        (cond-> {:db (-> db
                         (assoc-in (view-path view :initialized?) true)
                         (assoc-in (field-path view :sort) (default-sort view)))}
          filters    (update :db assoc-in (filters-path view) filters)
          default-id (update :db assoc-in (view-path view :default-filter-id)
                             default-id)
          default-id (assoc :dispatch [::select-filter view default-id false]))))))

(rf/reg-event-db
  ::refresh
  (fn [db [_ & ks]]
    (apply refresh-data db ks)))

(rf/reg-event-fx
  ::initialize
  (fn [{db :db} [_ views]]
    {:db         (refresh-data db
                               ::areas ::handlers ::organization-tags
                               ::operations ::organizations ::recipients
                               ::company-tags ::foreman-roles)
     :dispatch-n (concat (for [v views]
                           [::initialize-view v])
                         (when-not (::view db)
                           [[::set-view (first views)]]))}))

(rf/reg-event-fx
  ::sort-changed
  (fn [{db :db} [_ {:keys [field asc]}]]
    {:dispatch [::set-field (::view db) :sort [(keyword field) asc]]}))

(rf/reg-event-db
  ::set-save-title
  (fn [db [_ view title]]
    (assoc-in db (view-path view :save-title) title)))

(rf/reg-event-db
  ::save-filter-response
  (fn [db [_ resp view]]
    (let [{fid :id :as fltr} (parse-saved-filter (:filter resp))
          filters            (not-empty (get-in db (filters-path view)))
          exists?            (util/find-by-id fid filters)]
      (cond-> (update-in db (filters-path view)
                         (if exists?
                           #(util/update-by-id fid (constantly fltr) %)
                           #(conj % fltr)))
        (nil? filters) (assoc-in (view-path view :default-filter-id) fid)
        (not exists?)  (assoc-in (view-path view :filter-id) fid)))))

(rf/reg-event-fx
  ::save-filter
  (fn [{db :db} [_ view]]
    {:action/command {:name                  :save-application-filter
                      :show-saved-indicator? true
                      :params                (fields->save-filter-params db view)
                      :success [::save-filter-response view]}}))

(rf/reg-event-fx
  ::set-default-filter-id
  (fn [{db :db} [_ view filter-id]]
    (let [{:keys [type-key]} (view-filter-keys view)]
      {:db             (assoc-in db (view-path view :default-filter-id) filter-id)
       :action/command {:name                  :update-default-application-filter
                        :show-saved-indicator? true
                        :params                {:filterId   filter-id
                                                :filterType type-key}}})))

(rf/reg-event-db
  ::set-flag
  (fn [db [_ view flag flag?]]
    (assoc-in db (view-path view :flags flag) flag?)))

(rf/reg-event-fx
  ::delete-filter
  (fn [{db :db} [_ view filter-id]]
    (merge
      {:db             (update-in db (filters-path view)
                                  (fn [filters]
                                    (remove #(= (:id %) filter-id)
                                            filters)))
       :action/command {:name   :remove-application-filter
                        :params {:filterId   filter-id
                                 :filterType (:type-key (view-filter-keys view))}}}
      (when (= (current-filter-id db view) filter-id)
        {:dispatch [::clear-filter false]}))))

(rf/reg-event-fx
  ::confirm-delete-filter
  (fn [{db :db} [_ view filter-id]]
    (when-let [fltr (get-filter db view filter-id)]
      {:dialog/show {:text  (common/loc :a11y.filter.delete-confirm
                                         (:title fltr))
                     :type  :yes-no
                     :event [::delete-filter view filter-id]}})))
