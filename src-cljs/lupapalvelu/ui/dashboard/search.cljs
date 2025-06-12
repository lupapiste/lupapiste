(ns lupapalvelu.ui.dashboard.search
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.dashboard.queries :refer [application-filters]]
            [lupapalvelu.ui.dashboard.state :as state]
            [lupapalvelu.ui2.button :refer [button]]
            [lupapalvelu.ui2.icons :as icons]
            [lupapalvelu.ui2.toggle-group :refer [toggle-group]]
            [reagent.core :refer [defc]]
            [reitit.frontend.easy :as rfe]))

(defc search-filter [{:filter/keys [label ui] :as v}]
  (let [label (if (fn? label)
                (label nil)
                label)
        current-value (<sub [::state/search-filter-value (:filter/type v)])]
    [:div.flex.flex-col.items-start
     [:label (loc label)]
     (case (:type ui)
       :toggle
       [toggle-group
        {:value current-value
         :on-change (fn [option _e]
                      (>evt [::state/set-search-filter (:filter/type v) (:value option)]))
         :options (into (mapv (fn [x]
                                (assoc x
                                       :key (:title x)
                                       :title (loc (:title x))))
                              (:options ui))
                        [{:key "empty"
                          :value nil
                          :title "-"}])}]

       (str "Missing: " (pr-str (:type ui))))]))

(defc search-box []
  (let [filters-open? (<sub [::state/filters-open?])]
    [:div.flex.flex-col.mb-8.px-4.py-6
     {:class ["rounded-sm" "bg-surface-subtle"]}
     [:div.flex.flex-row.items-center.gap-8
      [:div.relative.flex-1.flex.flex-row
       [:input.flex-1
        {:class ["py-3" "border-1"
                 "pr-4" "pl-[calc(6*var(--spacing)+24px)]"
                 "border-ui-neutral-mid" "bg-ui-neutral-white"
                 "text-sm"
                 "rounded-lg"]
         :type "text"
         :placeholder (loc "dashboard.search.placeholder")
         :aria-label (loc "dashboard.search.placeholder")
         :on-change (fn [e] (>evt [::state/set-search-text (.. e -target -value)]))
         :value (<sub [::state/search-text])}]
       [icons/search
        {:size 24
         :class ["absolute"
                 "top-[50%]"
                 "mt-[-12px]"
                 "left-4"
                 "text-ui-neutral-dark"]}]]
      [:div.flex.flex-row.gap-4
       [button
        {:intent :secondary
         :size :md
         :class ["text-sm"]
         :on-click (fn [_e] (>evt [::state/toggle-filters]))}
        (loc "dashboard.search.filters")
        [icons/filter
         {:size 16}]]
       [button
        {:intent :primary
         :size :md
         :class ["text-sm"]
         :on-click (fn [_e] (rfe/push-state :dashboard-search))}
        (loc "dashboard.search.submit-search")]]]
     (when filters-open?
       [:div.pt-4
        [:div.flex.flex-row.gap-4.mb-4
         [:span "Hae: "]
         [:button.font-semibold
          "Hakemuksia"]]

        [:div.grid.grid-cols-2.lg:grid-cols-3.gap-2
         (for [x application-filters
               :when (:filter/ui x)]
           ^{:key (:filter/type x)}
           [search-filter x])]])]))

(defc search-results []
  [:div.flex.flex-col
   [:div.flex.flex-row
    [button
     {:intent :secondary
      :on-click (fn [_e] (rfe/push-state :dashboard))}
     "Takaisin"]]
   "Search results"])
