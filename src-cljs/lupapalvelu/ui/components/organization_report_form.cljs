(ns lupapalvelu.ui.components.organization-report-form
  "See `resources/private/ui-components/authority-admin/organization-report-form-model.js`
  for the original JS implementation. This CLJS version does not support every feature yet."
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as ui]
            [lupapalvelu.ui.components.day-edit :as day-edit]))

(defn report-form
  [{:keys [action org-id
           title-loc caption-loc button-loc
           start-date end-date start-date-loc end-date-loc
           start-ts end-ts
           on-start-date-change
           on-end-date-change]}]
  (let [date-range?      (and start-date end-date)
        timestamp-range? (and start-ts end-ts)
        invalid?         (and timestamp-range? (< end-ts start-ts))]
    [:<>
     (when title-loc
       [:h2 (common/loc title-loc)])
     (when caption-loc
       [:span (common/loc caption-loc)])
     [:form {:action (str "/api/raw/" (name action))
             :method :get}
      (when org-id
        [:input {:type  :hidden
                 :name  :organizationId
                 :value org-id}])
      (when timestamp-range?
        [:<>
         [:input {:type  :hidden
                  :name  :startTs
                  :value start-ts}]
         [:input {:type  :hidden
                  :name  :endTs
                  :value end-ts}]])
      [:div.labeled-input.row
       (when date-range?
         [:<>
          [:div.labeled-input.cell
           [:label (common/loc start-date-loc)]
           [day-edit/day-edit
            start-date
            {:callback on-start-date-change}]]
          [:div.labeled-input.cell
           [:label (common/loc end-date-loc)]
           [day-edit/day-edit
            end-date
            {:callback on-end-date-change}]]])
       [:div.labeled-input.cell
        [ui/icon-button
         {:class       :primary.outline
          :disabled?   invalid?
          :icon        :lupicon-download
          :text-loc    button-loc
          :button-type :submit}]]]]]))
