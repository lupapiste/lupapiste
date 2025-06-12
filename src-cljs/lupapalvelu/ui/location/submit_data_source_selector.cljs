(ns lupapalvelu.ui.location.submit-data-source-selector
  (:require [lupapalvelu.application.submit-data-source :as sds]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.help-toggle :as help-toggle]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn- label
  [data-source]
  (let [label    (common/loc (util/kw-path :submit-data-source.option data-source))
        default? (= sds/default-data-source data-source)]
    [:span label
     (when default?
       [:span " ("
        [:span.font-bold
         (common/loc :submit-data-source.default)]
        ")"])]))

(defn- help-generic
  [{:keys [data-source]}]
  [:div {:class [:p-4
                 :grid :gap-2 :grid-cols-1]}
   [:h3.font-bold [label data-source]]
   [:p (common/loc (util/kw-path :submit-data-source.help data-source :base))]
   [:ul.list-disc.list-inside
    (->> (sds/help-sections data-source)
         (map (fn [section]
                ^{:key section}
                [:li [:span.font-bold (common/loc section)]
                 [:span ": " (common/loc (util/kw-path section :content))]]))
         (doall))]])

(defn- help-content
  []
  [:div {:class [:grid :grid-cols-2
                 ;; blueish border between items
                 :divide-x-2 :divide-solid :divide-ui-lp-blueish]}
   (->> sds/data-sources
        (map (fn [data-source]
               ^{:key data-source}
               [help-generic {:data-source data-source}]))
        doall)])

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::submit-data-source
  :<- [::app/application-id]
  :<- [::app/submit-data-source]
  :<- [::data]
  (fn [[app-id from-app data]]
    (or (get data app-id)
        from-app)))

(rf/reg-event-fx ::data-source-selected
  (fn [{:keys [db]} [_ datasource]]
    (let [app-id (app/db->application-id db)]
      {:fx [[:action/command {:name    :set-submit-data-source
                              :params  {:id         app-id
                                        :datasource datasource}
                              :success [::set-submit-data-source.success app-id datasource]}]]})))

(rf/reg-event-fx ::set-submit-data-source.success
  (fn [{:keys [db]} [_ _resp app-id datasource]]
    {:db (assoc-in db [::data app-id] datasource)}))

(defn render
  []
  (let [current-data-source (<sub [::submit-data-source])
        locked?             (<sub [::app/submit-data-source-locked?])
        read-only?          (not (<sub [:auth/application? :set-submit-data-source]))
        can-modify?         (and (not locked?)
                                 (not read-only?))]
    [:div
     {:class [:twp ; reset styles for tailwind!
              :border-2 :border-ui-lp-ui-accent-04 :rounded-md ; orange border
              :bg-ui-lp-sea-blue-85 ; blueish background
              :grid :gap-2 :grid-cols-1 ; child alignment
              :p-4]}
     [:h1.normal-case.font-bold.text-xl (common/loc :submit-data-source.title)]
     [:div
      [:div (common/loc :submit-data-source.base)
       [help-toggle/help-toggle
        {:help-content [help-content]}]]]
     [:div
      (->> sds/data-sources
           (map (fn [data-source]
                  (let [id (name data-source)]
                    ^{:key id}
                    [:<>
                     [ui-input/radio-button
                      (cond-> {:id       id
                               :value    id
                               :checked? (= data-source current-data-source)}
                        (not can-modify?) (assoc :read-only? true
                                                 :disabled? true)
                        can-modify?       (assoc :on-change (fn [v] (>evt [::data-source-selected (keyword v)]))))]
                     [:label {:for id}
                      [label data-source]]])))
           doall)]]))
