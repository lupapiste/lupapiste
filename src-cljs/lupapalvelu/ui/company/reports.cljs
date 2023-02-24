(ns lupapalvelu.ui.company.reports
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.datepicker :refer [day-begin-ts day-end-ts]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]))

(rf/reg-event-db
  ::start-date
  (fn [db [_ date]]
    (assoc db ::start-ts (day-begin-ts date))))

(rf/reg-event-db
  ::end-date
  (fn [db [_ date]]
    (assoc db ::end-ts (day-end-ts date))))

(rf/reg-sub
  ::ts-range
  (fn [db _]
    [(::start-ts db) (::end-ts db)]))

(defn- download [start-ts end-ts]
  (let [url (str "/api/raw/company-report?startTs=" start-ts "&endTs=" end-ts)]
    (js/window.open url "_self")))

(defn- date-editor [loc-key ts evt bad?]
  (r/with-let [id (common/unique-id "company-report-")]
    [:div.flex--column
     [:label.lux
      {:for   id
       :class (common/css-flags :required (nil? ts))}
      (loc loc-key)]
     [components/day-edit
      ts
      {:id        id
       :callback  #(>evt [evt %])
       :class     :w--min-8em
       :enabled?  (<sub [:auth/global? :company-report])
       :required? true
       :invalid?  bad?}]]))

(defn report
  []

  (r/with-let [_ (>evt [:auth/refresh])]
    (let [[start-ts end-ts] (<sub [::ts-range])
          bad-range?        (and start-ts end-ts (> start-ts end-ts))]
      [:div
       [:h1
        (loc :company.reports.title)]
       [:div (loc :company.reports.help)]
       [:div.flex--wrap.flex--gap2.gap--v2
        [date-editor :company.reports.startdate.title
         start-ts ::start-date bad-range?]
        [date-editor :company.reports.enddate.title
         end-ts ::end-date bad-range?]]
       [:div
        [components/icon-button
         {:class    :primary
          :text-loc :company.reports.download.report
          :icon     :lupicon-download
          :enabled? (boolean (and (<sub [:auth/global? :company-report])
                                  (and start-ts end-ts
                                       (< start-ts end-ts))))
          :on-click #(download start-ts end-ts)}]]])))

(defn mount-component [dom-id]
  (rd/render [report] (.getElementById js/document dom-id)))

(defn ^:export start [dom-id]
  (mount-component dom-id))
