(ns lupapalvelu.ui.docgen.feature.assignments
  (:require [lupapalvelu.next.event :refer [>evt]]
            [re-frame.core :as rf]))

;; Store KO objects outside the app-db. Otherwise we re-frame runs into problems
;; comparing app-db values when advanced optimizations are used.
(def *ko-state (atom {}))

(defn- cleanup
  []
  (let [{:keys [^js computed ^js subscription]} (deref *ko-state)]
    (when computed
      (.dispose computed))
    (when subscription
      (.dispose subscription))
    (reset! *ko-state {})))

(defn- assignment->clj
  [^js assignment]
  (-> (js->clj assignment :keywordize-keys true)
      ;; The JS service adds a `edit` observable. We need to drop it,
      ;; otherwise we get "TypeError: ({change:[]}) is not a function"
      ;; errors in prod builds.
      (dissoc :edit)))

(defn initialize-assignments-subscription
  []
  (cleanup)
  ;; service might not exist
  (when-let [^js service (.. js/lupapisteApp -services -assignmentService)]
    (let [;; NOTE: We use `.assignments`, which already filters out some assignments.
          obs (.pureComputed js/ko (fn [] (mapv assignment->clj (.assignments service))))
          sub (.subscribe obs (fn [new-assignments] (>evt [::new-assignments new-assignments])))]
      (reset! *ko-state {:computed     obs
                         :subscription sub})
      (>evt [::new-assignments (.peek obs)]))))

(rf/reg-fx ::initialize-assignments-subscription
  (fn []
    (initialize-assignments-subscription)))

(rf/reg-fx ::cleanup-assignments-subscription
  cleanup)

(defn on-app-loaded [_ [_ _]]
  {:fx [[::initialize-assignments-subscription]]})

(defn on-unload [{:keys [db]}]
  {:fx [[::cleanup-assignments-subscription]]
   :db (dissoc db ::data)})

(rf/reg-event-db ::new-assignments
  (fn [db [_ assignments]]
    ;; retain original order
    (assoc-in db [::data :assignments] assignments)))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::assignments
  :<- [::data]
  :-> :assignments)

(rf/reg-sub ::document-assignments
  :<- [::assignments]
  (fn [assignments [_ doc-id]]
    (->> assignments
         (filter (fn [{:keys [currentState targets]}]
                   (and (not= "completed" (:type currentState))
                        (some (fn [target]
                                (= doc-id (:id target)))
                              targets))))
         (into []))))

(rf/reg-sub ::document-assignments-ids
  (fn [[_ doc-id]]
    {:pre [(string? doc-id)]}
    (rf/subscribe [::document-assignments doc-id]))
  (fn [assignments]
    (mapv :id assignments)))

(rf/reg-sub ::document-assignments-map
  (fn [[_ doc-id]]
    {:pre [(string? doc-id)]}
    (rf/subscribe [::document-assignments doc-id]))
  (fn [assignments]
    (->> assignments
         (map (juxt :id identity))
         (into {}))))

(rf/reg-sub ::document-assignment
  (fn [[_ doc-id assignment-id]]
    {:pre [(string? doc-id) (string? assignment-id)]}
    (rf/subscribe [::document-assignments-map doc-id]))
  (fn [m [_ _doc-id assignment-id]]
    (get m assignment-id)))

(rf/reg-sub ::description
  (fn [[_ doc-id assignment-id]]
    (rf/subscribe [::document-assignment doc-id assignment-id]))
  :-> :description)

(rf/reg-sub ::recipient
  (fn [[_ doc-id assignment-id]]
    (rf/subscribe [::document-assignment doc-id assignment-id]))
  :-> :recipient)

(rf/reg-sub ::creator
  (fn [[_ doc-id assignment-id]]
    (rf/subscribe [::document-assignment doc-id assignment-id]))
  (fn [assignment]
    (-> assignment :createdState :user)))

(rf/reg-sub ::target
  (fn [[_ doc-id assignment-id]]
    (rf/subscribe [::document-assignment doc-id assignment-id]))
  (fn [assignment]
    (-> assignment
        :targets
        first)))
