(ns lupapalvelu.ui.filebank.filter-box
  (:require [rum.core :as rum]
            [clojure.set :refer [difference]]
            [lupapalvelu.ui.common :refer [loc]]))

(defn filter-box-item
  "Creates checkbox for item-name and adds item name to filter-options"
  [filter-options test-id [i item-name]]
  [:div.filter-wrapper
   [:input.hidden {:id        (str test-id "-" i)
                   :type      "checkbox"
                   :checked   (when-not (empty? (rum/react filter-options))
                                (when (contains? (rum/react filter-options) item-name)
                                  "true"))
                   :on-click  (fn [e]
                                 (if (.-checked (.-target e))
                                  (swap! filter-options conj item-name)
                                  (swap! filter-options difference #{item-name})))}]
   [:label.filter-label {:data-test-id    (str test-id "-" i)
                         :for             (str test-id "-" i)} item-name]])

(rum/defcs filter-box < rum/reactive
                        (rum/local #{} ::filter-options)
  "Creates filter options for user."
  [state filter-words & [{:keys [callback test-id]}]]
  (let [filter-options (state ::filter-options)]
    (when callback (add-watch filter-options nil
                              (fn [_ _ _ new] (callback new))))
   [:div.filter-group
    [:input.hidden {:type "checkbox"
                    :id (str test-id "-show-all")
                    :on-click (fn [e] (if (.-checked (.-target e))
                                        (reset! filter-options (into #{} filter-words))
                                        (reset! filter-options #{})))}]
    [:label.filter-label {:for          (str test-id "-show-all")
                          :data-test-id (str test-id "-show-all")}
     (loc "filter.all-keywords")]
    (->> filter-words
         (zipmap (range))
         (mapv (partial filter-box-item filter-options test-id))
         (into [:div]))]))