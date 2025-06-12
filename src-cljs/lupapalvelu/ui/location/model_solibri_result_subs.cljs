(ns lupapalvelu.ui.location.model-solibri-result-subs
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.location.model-subs :as model-subs]
            [lupapalvelu.ui.media-query :as media-query]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-event-db ::init-component
  (fn [db [_ _visible?]]
    (dissoc db
            ::selected-result-type
            ::opened-topics-rule-id
            ::navigation-root
            ::results-visible)))

(rf/reg-sub ::ui-mode-wide?
  (fn [_ _]
    (<sub [::media-query/breakpoint-matches? :md])))

(rf/reg-sub ::selected-version-check-results
  :<- [::model-subs/model]
  :-> (comp :check-results :selected-version))

(rf/reg-event-db ::set-selected-result-type
  (fn [db [_ result-type]]
    (-> db
        (assoc ::selected-result-type result-type)
        (dissoc db ::opened-topics-rule-id ::navigation-root))))

(rf/reg-sub ::selected-result-type
  (fn [db _]
    (or (::selected-result-type db) :validation-result)))

(defn find-parent-id [data rule-set-id]
  (->> data
       :rules
       (keep (fn [rule]
               (let [path-ids (->> rule :path)
                     i        (.indexOf path-ids rule-set-id)]
                 (when (pos? i)
                   (nth path-ids (dec i))))))
       first))

(defn has-children? [data id]
  (->> data
       ((juxt :rules :ruleSets))
       flatten
       (some (fn [r] (-> r :parent (= id))))))

(rf/reg-sub ::selected-check-results
  :<- [::selected-version-check-results]
  :<- [::selected-result-type]
  (fn [[check-results result-type] _]
    (as-> (-> check-results (get result-type)) data
          (update data :ruleSets #(mapv (fn [rule-set]
                                          (assoc rule-set
                                                 :type :rule-set
                                                 :parent (find-parent-id data (:id rule-set))
                                                 :has-children? true)) %))
          (update data :rules #(mapv (fn [rule] (assoc rule
                                                       :type :rule
                                                       :parent (some-> rule :path last))) %))
          (update data :rules #(mapv (fn [rule] (assoc rule :has-children? (has-children? data (:id rule)))) %)))))

(rf/reg-event-db ::open-result-topics
  (fn [db [_ id]]
    (assoc db ::opened-topics-rule-id id)))

(rf/reg-sub ::opened-topics-rule-id :-> ::opened-topics-rule-id)

(rf/reg-event-db ::show-results
  (fn [db _]
    (assoc db ::results-visible true)))

(rf/reg-event-db ::hide-results
  (fn [db _]
    (dissoc db ::results-visible)))

(rf/reg-sub ::results-visible
  (fn [db _]
    (and (::results-visible db)
         (::opened-topics-rule-id db))))

(rf/reg-sub ::topics
  :<- [::selected-check-results]
  :<- [::opened-topics-rule-id]
  (fn [[data rule-id] _]
    (when rule-id
      (let [rule      (->> data :rules (util/find-by-key :id rule-id))
            topic-ids (-> rule :topicIds set)]
        {:rule   rule
         :topics (->> data
                      :topics
                      (filter #(topic-ids (:id %))))}))))

(rf/reg-event-db ::set-navigation-root
  (fn [db [_ item]]
    (let [id (if (map? item) (:id item) item)]
      (assoc db
             ::navigation-root id
             ::opened-topics-rule-id (if (and (map? item) (= (:type item) :rule)) id nil)))))

(rf/reg-sub ::navigation-root :-> ::navigation-root)

(defn build-path [items path]
  (let [[{:keys [parent]} & _] path]
    (if parent
      ;; path must be a list otherwise the order is screwed
      (build-path items (conj path (util/find-by-id parent items)))
      path)))

(rf/reg-sub ::navigation-items
  :<- [::selected-check-results]
  :<- [::navigation-root]
  (fn [[data root-id] _]
    (let [items     (apply concat ((juxt :ruleSets :rules) data))
          root-item (when root-id (util/find-by-id root-id items))]
      {:path  (when root-item (build-path items (list root-item)))
       :items (->> items
                   (filter #(-> % :parent (= root-id))))})))

(rf/reg-sub ::child-status-counts
  :<- [::selected-check-results]
  (fn [data [_ id]]
    (->> data
         :rules
         (filter (fn [{:keys [path]}] (-> path set (contains? id))))
         (group-by :status)
         (util/map-values count))))

(rf/reg-sub ::copy-guid-text
  (fn [db _]
    (or (::copy-guid-text db) (common/loc :model-details-page.results.copy-guid))))

(rf/reg-event-db ::reset-copy-guid-text
  (fn [db _]
    (dissoc db ::copy-guid-text)))

(rf/reg-event-fx ::copy-guid
  (fn [{db :db} [_ guid]]
    {:db                     (assoc db ::copy-guid-text (common/loc :model-details-page.results.copied))
     :text/copy-to-clipboard guid}))
