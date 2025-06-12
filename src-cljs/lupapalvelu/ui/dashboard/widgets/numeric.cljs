(ns lupapalvelu.ui.dashboard.widgets.numeric
  (:require [lupapalvelu.ui.dashboard.state :as state]
            [lupapalvelu.ui.dashboard.widgets.base :refer [base-widget]]
            [lupapalvelu.ui2.badge :refer [badge]]
            [lupapalvelu.ui2.card :as card]
            [lupapalvelu.ui2.status-dot :refer [status-dot]]
            [reagent.core :refer [defc]]
            [lupapalvelu.ui2.icons :as icons]))

(defn element [{:keys [value trend] :as props}]
  [base-widget props
   [card/title
    (or (:name (:options props))
        ;; (:name query)
        "Card title")]
   [card/content
    [:div.flex.flex-row.gap-4.items-center
     ;; TODO: Handle loading state?
     [card/huge
      value]
     (when (and trend (= "1" (:height props)))
       [:<>
        [badge
         {:intent :attention}
         [icons/arrow-down-right]
         trend]])]
    (when (and trend (= "2" (:height props)))
      [:<>
       [:div.flex-1]
       [:div.border-t.pt-2.mt-2.flex.flex-row.items-center
        {:class "border-ui-neutral-mid"}
        [:span.flex-1.flex.flex-row.items-center.gap-2
         [status-dot {:intent :info}]
         "Status text"]
        [badge
         {:intent :attention}
         [icons/arrow-down-right]
         trend]]])]])

(defc numeric-widget [props]
  (let [{:keys [data]} (state/use-query (:query (:options props)))
        {:keys [total]} data]
    [element
     (assoc props
            :value total
            :trend nil)]))

(defc details [props]
  (let [{:keys [data]} (state/use-query (:query (:options props)))]
    [:div "Hello from numeric widget: " (:total data)]))
