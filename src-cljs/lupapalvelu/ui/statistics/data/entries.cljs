(ns lupapalvelu.ui.statistics.data.entries
  "Logic for maintaining entries UI and communicating with the backend.

  Uses datapath prefix mechanism for app-db storage (see `lupapalvelu.data`). The relevant
  fields are:

  `:entries` Entry-id - entry map, where each entry is `stats-sc/FrontendEntry` instance.

  `:auth-models` Entry-id - auth-model map

  When an entry is being manipulated by the user, some information is temporarily stored
  to `:editing`, `:answering` and `:closing` fields, each of which are entry-id - data
  maps. In addition a `:waiting` map has a true entry-id property when the editing waits
  for the backend."
  (:require [lupapalvelu.data :as data]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.misc]
            [lupapalvelu.ui.statistics.data.entriestitle :as stats-title]
            [lupapalvelu.ui.statistics.data.summary :as stats]
            [re-frame.core :as rf]
            [sade.util :as util]
            [schema-tools.core :as st]
            [sade.strings :as ss]))

(def datapath (partial data/datapath ::data))
(def set-d    (partial data/set-d ::data))
(def get-d    (partial data/get-d ::data))
(def unset-d  (partial data/unset-d ::data))

(def new-id ::new-id)

(def new-id? (partial = new-id))
(def old-id? (complement new-id?))

(def day-in-ms (* 24 60 60 1000))

(defn request? [entry]
  (= :request (:type entry)))

(defn process-auth-model [m]
  (into {}
        (keep (fn [[entry-id actions]]
                (some->> actions
                         (util/filter-map-by-val :ok)
                         (util/map-values :ok)
                         not-empty
                         (vector (keyword entry-id))))
              m)))

(defn- set-waiting [db entry-id waiting?]
  (if waiting?
    (set-d db :waiting entry-id true)
    (unset-d db :waiting entry-id)))

(defn clear [db entry-id]
  (-> db
      (set-waiting entry-id false)
      (unset-d :editing entry-id)
      (unset-d :answering entry-id)
      (unset-d :closing entry-id)))

(defn entry-state [{:keys [deleted? answered-ts closed-ts delay-start]
                    :as   entry}]
  (let [now           (date/now)
        r?            (request? entry)
        d?            (not r?)]
    (cond
      deleted?                              :deleted
      (and d?
           (some-> delay-start
                   (< now (or closed-ts
                              (inc now))))) :delayed
      (some-> closed-ts (< now))            :closed
      (some-> answered-ts (< now))          :answered
      :else                                 :open)))

(def ^:private state-order-map
  (zipmap [:open :delayed :answered :closed :deleted]
          (range)))

(defn state-order [entry]
  ((entry-state entry) state-order-map))

;; -----------------------------
;; Events
;; -----------------------------

(rf/reg-event-fx ::signal-changed
  (fn [_]
    (when-let [event (cond
                       (auth/application-auth? :processing-days)
                       [:lupapalvelu.ui.statistics.data.log/fetch-logdata]

                       (auth/application-auth? :processing-requests-status)
                       [::stats-title/fetch-requests-status])]
      {:fx [[:dispatch event]]})))

(rf/reg-event-db ::cancel
  (fn [db [_ entry-id]]
    (clear db entry-id)))

(rf/reg-event-db ::stop-waiting
  (fn [db [_ _ entry-id]]
    (set-waiting db entry-id false)))

(rf/reg-event-fx ::mark-entries-seen
  (fn [{db :db}]
    (when (auth/application-auth? :mark-entries-seen)
      (when-let [unseen-ids (some->> (get-d db :entries)
                                     vals
                                     (remove :seen-by?)
                                     seq
                                     (mapv :id))]
        {:action/command {:name   :mark-entries-seen
                          :params {:id        (common/application-id)
                                   :entry-ids unseen-ids}
                          :success [::stats/fetch-processing-days]}}))))

(rf/reg-event-fx ::toggle-entry-seen
  (fn [{db :db} [_ entry-id]]
    (let [seen? (not (get-d db :entries entry-id :seen-by?))]
      {:db             (set-d db :entries entry-id :seen-by? seen?)
       :action/command {:name    :toggle-entry-seen
                        :params  {:id       (common/application-id)
                                  :entry-id entry-id
                                  :seen     seen?}
                        :success [::stats/fetch-processing-days]}})))

(rf/reg-event-fx ::processing-entries.success
  (fn [{db :db} [_ {:keys [entries auth-models]}]]
    (let [entries (some->> entries seq
                           (mapv (juxt :id stats-sc/->fronted-entry))
                           (into {}))]
      {:db (-> db
               (set-d :entries (update-keys entries keyword))
               (set-d :auth-models (process-auth-model auth-models)))
       :fx [[:dispatch [::mark-entries-seen]]]})))

(rf/reg-event-fx ::toggle-view
  (fn [{db :db} [_ visible?]]
    (if visible?
      {:db           (set-d db {:visible? true})
       :action/query {:name    :processing-entries
                      :params  {:id (common/application-id)}
                      :success [::processing-entries.success]}
       :fx           [[:dispatch [:auth/refresh]]]}
      {:db (unset-d db)})))

(rf/reg-event-fx ::entry-update.success
  (fn [{db :db} [_ {:keys [entry auth-model]} entry-id]]
    {:db (-> db
             (clear entry-id)
             (set-d :entries (:id entry) (stats-sc/->fronted-entry entry))
             (update-in (datapath :auth-models) merge (process-auth-model auth-model)))
     :fx [[:dispatch [::signal-changed]]]}))

;; ----------------------------
;; Edit
;; ----------------------------

(rf/reg-event-db ::start-editing
  (fn [db [_ entry-id extras]] ; `new-id` for new entries
    (set-d db :editing entry-id (merge (get-d db :entries entry-id)
                                       extras))))

(rf/reg-event-db ::edit
  (fn [db [_ entry-id field value]]
    (set-d db :editing entry-id field value)))

(rf/reg-event-fx ::save-editing
  (fn [{db :db} [_ entry-id]]
    (let [old    (get-d db :entries entry-id)
          new    (get-d db :editing entry-id)
          new?   (new-id? entry-id)
          req?   (request? new)
          new    (cond-> (dissoc new :type)
                   (and new? req?)        (dissoc :closed-ts :closed-text)
                   (not req?)             (dissoc :auto-delay?)
                   (not (:closed-ts new)) (dissoc :closed-text))
          params (some-> (stats-sc/diff-map new old)
                         stats-sc/->entry-update
                         not-empty
                         (assoc :id (common/application-id))
                         (util/assoc-when-pred old-id? :entry-id entry-id))
          [action params] (case [new? req?]
                            [true true]  [:new-processing-request
                                          (st/select-schema params stats-sc/NewRequestParams)]
                            [true false] [:new-processing-delay
                                          (st/select-schema params stats-sc/NewDelayParams)]
                            [:update-processing-entry params])]
      (if params
        {:db             (set-waiting db entry-id true)
         :action/command {:name     action
                          :params   params
                          :success  [::entry-update.success entry-id]
                          :complete [::stop-waiting entry-id]}}
        {:db (unset-d db :editing entry-id)}))))

;; ----------------------------
;; Answer
;; ----------------------------

(rf/reg-event-db ::start-answering
  (fn [db [_ entry-id]]
    (set-d db :answering entry-id "")))

(rf/reg-event-db ::answer
  (fn [db [_ entry-id value]]
    (set-d db :answering entry-id value)))

(rf/reg-event-fx ::save-answering
  (fn [{db :db} [_ entry-id]]
    {:db             (set-waiting db entry-id true)
     :action/command {:name     :answer-processing-entry
                      :params   {:id            (common/application-id)
                                 :entry-id      entry-id
                                 :answered-text (get-d db :answering entry-id)}
                      :success  [::entry-update.success entry-id]
                      :complete [::stop-waiting entry-id]}}))

;; ----------------------------
;; Close
;; ----------------------------

(rf/reg-event-db ::start-closing
  (fn [db [_ entry-id]]
    (set-d db :closing entry-id :closed-ts (date/now))))

(rf/reg-event-db ::close
  (fn [db [_ entry-id field value]]
    (set-d db :closing entry-id field value)))

(rf/reg-event-fx ::save-closing
  (fn [{db :db} [_ entry-id]]
    {:db             (set-waiting db entry-id true)
     :action/command {:name     :close-processing-entry
                      :params   (assoc (get-d db :closing entry-id)
                                       :id (common/application-id)
                                       :entry-id entry-id)
                      :success  [::entry-update.success entry-id]
                      :complete [::stop-waiting entry-id]}}))

;; ----------------------------
;; Delete
;; ----------------------------

(rf/reg-event-db ::toggle-show-deleted
  (fn [db [_ show?]]
    (set-d db :show-deleted? (boolean show?))))

(rf/reg-event-fx ::mark-deleted
  (fn [{db :db} [_ entry-id deleted?]]
    {:db (set-d db :entries entry-id :deleted? deleted?)
     :action/command {:name        :update-processing-entry
                      :params      {:id       (common/application-id)
                                    :entry-id entry-id
                                    :deleted? deleted?}
                      :success     [::entry-update.success entry-id]}}))

;; -----------------------------
;; Subs
;; -----------------------------

(rf/reg-sub ::data :-> get-d)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-sub ::show-deleted?
  :<- [::data]
  :-> (comp boolean :show-deleted?))

(rf/reg-sub ::entry
  (fn [db [_ entry-id & ks]]
    (apply get-d db :entries entry-id ks)))

(rf/reg-sub ::entries
  :<- [::data]
  :<- [::show-deleted?]
  (fn [[data all?]]
    (cond->> (some-> data :entries vals)
      (not all?) (remove :deleted?))))

(rf/reg-sub ::request-ids
  :<- [::entries]
  (fn [entries]
    (some->> entries
             (filter request?)
             (sort-by (juxt state-order :answered-ts :delay-start))
             (mapv :id)
             seq)))

(rf/reg-sub ::delay-ids
  :<- [::entries]
  (fn [entries]
    (some->> entries
             (remove request?)
             (sort-by (juxt state-order :closed-ts :delay-start))
             (mapv :id)
             seq)))

(rf/reg-sub ::answering
  (fn [db [_ entry-id]]
    (get-d db :answering entry-id)))

(rf/reg-sub ::closing
  (fn [db [_ entry-id]]
    (get-d db :closing entry-id)))

(rf/reg-sub ::editing
  (fn [db [_ entry-id]]
    (get-d db :editing entry-id)))

(rf/reg-sub ::editing?
  (fn [db [_ entry-id]]
    (boolean (get-d db :editing entry-id))))

(rf/reg-sub ::waiting?
  (fn [db [_ entry-id]]
    (boolean (get-d db :waiting entry-id))))

(rf/reg-sub ::deleted-entries?
  :<- [::data]
  :-> (util/fn->> :entries vals (some :deleted?) boolean))

(rf/reg-sub ::adding?
  (fn [db]
    (boolean (get-d db :editing new-id))))

(rf/reg-sub ::auths-for-entries?
  (fn [db [_ & entry-ids]]
    (->> (flatten entry-ids)
         (some #(not-empty (get-d db :auth-models %)))
         boolean)))

(rf/reg-sub ::entry-auth?
  (fn [db [_ entry-id action]]
    (boolean (get-d db :auth-models entry-id action))))

(rf/reg-sub ::late?
  (fn [db [_ entry-id]]
    (let [{:keys [deleted answered-ts closed-ts delay-start]
           :as   entry} (get-d db :entries entry-id)]
      (boolean (when-not (or (not (request? entry)) deleted
                             answered-ts closed-ts)
                 (->> (+ delay-start day-in-ms)
                      date/start-of-day
                      (> (date/now))))))))

(rf/reg-sub ::entry-state
  (fn [db [_ entry-id]]
    (entry-state (get-d db :entries entry-id))))

(rf/reg-sub ::answered?
  (fn [[_ entry-id]]
    (rf/subscribe [::entry entry-id :answered-ts]))
  :-> boolean)

(rf/reg-sub ::closed?
  (fn [[_ entry-id]]
    ;; We check the state instead of timestamp, since the delays support closing dates in
    ;; the future.
    (rf/subscribe [::entry-state entry-id]))
  :-> (comp boolean #{:closed}))

(rf/reg-sub ::authorstamp
  (fn [[_ entry-id]]
    (rf/subscribe [::entry entry-id]))
  (fn [entry [_ _ k]]
    (let [[date-k name-k] (case k
                            :created  [:created-ts :created-name]
                            :answered [:answered-ts :answered-name]
                            :closed   [:closed-ts :closed-name])]
      (when-let [date (some-> entry date-k date/finnish-date)]
        (ss/trim-join " " date (get entry name-k))))))
