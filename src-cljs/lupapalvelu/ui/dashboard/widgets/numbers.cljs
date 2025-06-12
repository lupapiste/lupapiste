(ns lupapalvelu.ui.dashboard.widgets.numbers
   (:require [lupapalvelu.ui.common :refer [loc]]
             [lupapalvelu.ui.dashboard.queries :as queries]
             [lupapalvelu.ui.dashboard.widgets.base :refer [base-widget]]
             [lupapalvelu.ui2.button :as button]
             [lupapalvelu.ui2.card :as card]
             [lupapalvelu.ui2.toggle-group :refer [toggle-group]]
             [reagent.core :refer [defc]]
             [reagent.hooks :as hooks]))

(defn element [{:keys [values control-value control-on-change options] :as props}]
  [base-widget props
   [:div.flex.flex-row.items-center
    [card/title
     (or (:name (:options props))
         ;; (:name query)
         "Card title")]
    [:div.flex-1]
    (when-let [{:keys [options]} (:control options)]
      ;; TODO: Support other control types?
      ;; Maybe move the base-widget, or at least provide a generic component to use for controls.
      [toggle-group
       {:value control-value
        :options (map (fn [x]
                        (assoc x :key (:value x)))
                      options)
        :on-change (fn [option _e]
                     (control-on-change (:value option)))}])]
   [card/content
    [:div.grid.grid-cols-3.gap-y-6.-mx-4
     (for [{:keys [label value] :query/keys [id]} values]
       ^{:key id}
       [:div.flex.flex-col.gap-2
        {:class "border-card-divider border-r-1 nth-[3n]:border-r-0 last:border-r-0 px-4"}
        [:span.text-xl.font-mono
         ;; FIXME: Variable/theme
         {:class ["leading-[32px]"]}
         value]
        [:span.text-sm.leading-none
         ;; Don't allow more than one line
         {:class ["text-nowrap" "overflow-hidden" "text-ellipsis"]}
         label]])]]])

(defc widget [props]
  (let [[control-value set-control-value] (hooks/use-state (:value (first (:options (:control (:options props))))))]
    [element
     (assoc props
            :values (map (fn [x]
                           (assoc x :value 0))
                         (:queries (:options props)))
            :control-value control-value
            :control-on-change set-control-value)]))

(defc details [_props]
  [:div "Hello from numbers widget"])

(defc edit [props {:keys [_update-fn read-only?]}]
  [:<>
   [:div.flex.flex-row
    [:h2.text-lg "Arvot"]
    [:div.flex-1]
    [button/button
     {:size :sm
      :disabled (or read-only?
                    (>= (count (:queries (:options props)))
                        6))}
     "Uusi"]]
   [:div
    (for [{:keys [label] :query/keys [id filters] :as query} (:queries (:options props))]
      [:div.border-b-1.border-ui-neutral-mid.py-2
       {:key id}
       [:div.flex.flex-row
        [:h3.text-lg label]
        [:div.flex-1]
        [button/button
         {:size :sm
          :disabled read-only?
          :aria-label "Poista haku"}
         "×"]]

       ;; NOTE: Maybe just provide button to open "search filters" panel? Same as we will use for the search field.
       [:ul.pl-4
        (for [v filters
              :let [{:filter/keys [label]}
                    (-> queries/type->query
                        (get (:query/type query))
                        (:type->filter)
                        (get (:filter/type v)))]]
          [:li.my-1
           {:key (:filter/type v)}
           (loc label)
           ;; TODO: Filter options
           ;; boolean values?
           ;; multiple fields?
           ;; number values?
           [button/button
            {:size :xs
             :disabled read-only?
             :aria-label "Poista suodatin"}
            "×"]])]

       ])]])
