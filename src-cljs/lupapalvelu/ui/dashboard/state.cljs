(ns lupapalvelu.ui.dashboard.state
  (:require [clojure.edn :as edn]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.dashboard.shortcuts :refer [cards]]
            [re-frame.core :as rf]
            [reagent.hooks :as hooks]))

(defn add-widget [{:keys [widgets _widget-order] :as x} new-widget]
  (let [new-id (if (seq widgets)
                 (inc (apply max (map :id (vals widgets))))
                 0)]
    (-> x
        (update :widgets assoc new-id (-> new-widget
                                          (assoc :id new-id)))
        (update :widget-order conj new-id))))

(defn remove-widget [x widget-id]
  (-> x
      (update :widgets dissoc widget-id)
      (update :widget-order (fn [xs]
                              (into (empty xs)
                                    (remove #(= widget-id %) xs))))))

(rf/reg-event-db ::initialize
  (fn [db _]
    ;; TODO: Local Storage is temporary impl for testing
    (let [{:keys [widgets order]} (try (edn/read-string (.getItem js/localStorage "widgets"))
                                       (catch :default _e nil))]
      (update db ::data #(merge {:editing? false
                                 :widgets (or widgets {0 {:id 0
                                                          :type :numeric
                                                          :width "1"
                                                          :height "1"
                                                          :options {:name "1-row variant"}}

                                                       1 {:id 1
                                                          :type :numeric
                                                          :width "1"
                                                          :height "2"
                                                          :options {:name "2-row variant"}}})
                                 :widget-order (or order [0 1])}
                                %)))))

(rf/reg-event-db ::start-editing
  (fn [db [_]]
    (-> db
        (assoc-in [::data :editing?] true)
        (assoc-in [::data :backup] (select-keys (::data db) [:widgets :widget-order])))))

(rf/reg-event-db ::save
  (fn [db [_]]
    (assoc-in db [::data :editing?] false)))

(rf/reg-event-db ::cancel
  (fn [db [_]]
    (-> db
        (assoc-in [::data :editing?] false)
        (update ::data merge (:backup (::data db)))
        (update ::data dissoc :backup))))

(rf/reg-event-db ::add-widget-modal
  (fn [db _]
    (assoc-in db [::data :edit-modal] {:new? true})))

(rf/reg-event-db ::set-edit-modal-state
  (fn [db [_ path new-value]]
    (update-in db [::data :edit-modal :state] assoc-in path new-value)))

(rf/reg-event-db ::init-custom-widget
  (fn [db _]
    (update-in db [::data :edit-modal] assoc
               :custom? true
               :state {:width "1"
                       :height "1"
                       :type nil
                       :options {:name ""}})))

(rf/reg-event-db ::remove-widget
  (fn [db [_ id]]
    (-> db
        (update ::data remove-widget id)
        (update ::data dissoc :edit-modal))))

(rf/reg-event-db ::set-order
  (fn [db [_ {:keys [_id from to]}]]
    ;; https://dndkit.com/concepts/sortable#single-list
    ;; Cut the item away from old index, and place into the new index.
    (if (not= from to)
      (update-in db [::data :widget-order]
                 (fn [widgets]
                   (let [this (get widgets from)
                         without (into (subvec widgets 0 from)
                                       (subvec widgets (inc from)))]
                     (into (conj (subvec without 0 to)
                                 this)
                           (subvec without to)))))
      db)))

(rf/reg-event-db ::open-modal
  (fn [db [_ widget-id]]
    (assoc-in db [::data :edit-modal] {:widget-id widget-id})))

(rf/reg-event-db ::close-modal
  (fn [db [_]]
    (update db ::data dissoc :edit-modal)))

(rf/reg-event-db ::add-widget
  (fn [db [_ shortcut-key]]
    (if (or shortcut-key
            (:state (:edit-modal (::data db))))
      (-> db
          (update ::data add-widget (if shortcut-key
                                      {:shortcut-key shortcut-key}
                                      (:state (:edit-modal (::data db)))))
          (update ::data dissoc :edit-modal))
      db)))

(rf/reg-event-db ::open-drawer
  (fn [db [_ widget-id]]
    (assoc-in db [::data :detail-drawer] {:widget-id widget-id})))

(rf/reg-event-db ::close-drawer
  (fn [db [_]]
    (update db ::data dissoc :detail-drawer)))

(rf/reg-sub ::data
  :-> ::data)

(defn apply-shortcut [widget]
  (if-let [k (:shortcut-key widget)]
    (merge widget (get cards k))
    widget))

(rf/reg-sub ::widgets
  :<- [::data]
  :-> :widgets)

(rf/reg-sub ::widget-order
  :<- [::data]
  :-> :widget-order)

(rf/reg-sub ::edit-modal-state
  :<- [::data]
  :-> :edit-modal)

(rf/reg-sub ::drawer-state
  :<- [::data]
  :-> :detail-drawer)

(rf/reg-sub ::get-widget
  :<- [::widgets]
  (fn [widgets [_ widget-id]]
    (apply-shortcut (get widgets widget-id))))

(rf/reg-sub ::editing?
  :<- [::data]
  :-> :editing?)

(rf/reg-event-db ::set-value
  (fn [db [_ widget-id path new-value]]
    (update-in db [::data :widgets widget-id] assoc-in path new-value)))

(rf/reg-event-db ::make-copy
  (fn [db [_ widget-id]]
    (update-in db [::data :widgets widget-id] (fn [x]
                                                (-> (apply-shortcut x)
                                                    (dissoc :shortcut-key))))))

;; TODO: Trigger save from real edit effects, not from a hook (now triggered by any render)
(defn save-to-local-storage [widgets order]
  (when widgets
    (.setItem js/localStorage "widgets"
              (pr-str {:widgets widgets
                       :order order}))))

(rf/reg-event-fx ::run-query
  (fn [_ [_ query-key]]
    {:fx [[:action/query {:name :dashboard-query
                          :params {:query query-key}
                          ;; FIXME: Response is added as first parameter, not the last?
                          :success [::set-result query-key]}]]}))

(rf/reg-event-fx ::set-result
  (fn [{:keys [db]} [_ resp query]]
    {:db (update-in db [::data :queries query] assoc
                    :active false
                    :updated (.now js/Date)
                    :data resp)}))

(rf/reg-sub ::query-results
  :<- [::data]
  :-> :queries)

(rf/reg-sub ::query-result
  :<- [::query-results]
  (fn [data [_ query]]
    (get data query)))

(rf/reg-event-fx ::maybe-update-query
  (fn [{:keys [db]} [_ query]]
    (let [now (.now js/Date)
          {:keys [updated active]} (get-in db [::data :queries query])
          ;; Make the timeout configurable?
          ;; Update after 100ms?
          stale? (or (not updated)
                     (> now (+ updated 100)))
          run-query? (and stale? (not active))]
      {:db (update-in db [::data :queries query] (fn [x]
                                                   (-> x
                                                       (update :uses (fnil inc 0))
                                                       (cond->
                                                         run-query? (assoc :active true)))))
       :fx [(when run-query?
              [:dispatch [::run-query query]])]})))

(rf/reg-event-db ::decrement-uses
  (fn [db [_ query]]
    (update-in db [::data :queries query :uses] (fnil dec 0))))

(defn use-query
  "Request a dashboard-query result from the backend.

  If multiple widgets use the same query, only one request is made and they see
  the same result.

  A query is active as long as more than one widget is using it.
  Active queries can be refreshed automatically."
  [query]
  ;; Keep also track of how many users the query has
  ;; - useful for clearing stale data
  ;; - going over all active queries and updating the results
  ;; TODO: Calculate query "identity" from the query (key + parameters + context?) and use that for effects/sub etc.
  (hooks/use-effect (fn []
                      (>evt [::maybe-update-query query])
                      (fn []
                        (>evt [::decrement-uses query])))
                    [query])
  (<sub [::query-result query]))

(rf/reg-event-fx ::update-all-queries
  (fn [{:keys [db]} [_]]
    (let [now (.now js/Date)]
      {:fx (into []
                 (keep (fn [[query-key {:keys [uses updated active]}]]
                         (when (and (pos? uses)
                                    (or (not updated)
                                        (> now (+ updated 100)))
                                    (not active))
                           [:dispatch [::run-query query-key]]))
                       (-> db ::data :queries)))})))

;;
;; Search
;;

(rf/reg-event-db ::toggle-filters
  (fn [db [_]]
    (update-in db [::data :search :filters-open?] not)))

(rf/reg-sub ::search
  :<- [::data]
  :-> :search)

(rf/reg-sub ::filters-open?
  :<- [::search]
  :-> :filters-open?)

(rf/reg-sub ::search-text
  :<- [::search]
  (fn [search _]
    (or (:text search)
        "")))

(rf/reg-event-db ::set-search-text
  (fn [db [_ new-search]]
    (assoc-in db [::data :search :text] new-search)))

;; NOTE: Filters stored as kv map
;; Would be converted to vector of {:name, :value} for API?
;; NOTE: IF we need duplicate instances of the same filter,
;; this should also use the vector collection of filters?
(rf/reg-sub ::search-filter-value
  :<- [::search]
  (fn [search [_ k]]
    (get (:filters search) k)))

(rf/reg-event-db ::set-search-filter
  (fn [db [_ k v]]
    ;; Nil -> remove filter
    (if (some? v)
      (assoc-in db [::data :search :filters k] v)
      (update-in db [::data :search :filters] dissoc k))))
