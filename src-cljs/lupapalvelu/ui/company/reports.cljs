(ns lupapalvelu.ui.company.reports
  (:require [rum.core :as rum]
            [cljs-time.coerce :as tc]
            [cljs-time.format :as tf]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.company.state :as state]))

(defonce args (atom {}))

(defn- download []
  (let [start-ts (tc/to-long (tf/parse common/fi-date-formatter @state/report-start-date))
        end-ts (tc/to-long (tf/parse common/fi-date-formatter @state/report-end-date))
        url (str "/api/raw/company-report?startTs=" start-ts "&endTs=" end-ts)]
    (js/window.open url "_self")))

(rum/defc report < rum/reactive
  []
  [:div
   [:h1
    (loc (str "company.reports.title"))]
   [:div (loc (str "company.reports.help"))]
   [:table.company-report-table
    [:thead
     [:tr
      [:th (loc (str "company.reports.startdate.title"))]
      [:th (loc (str "company.reports.enddate.title"))]]]
   [:tbody
    [:tr
     [:td (components/date-edit state/report-start-date {})]
     [:td (components/date-edit state/report-end-date {})]]
    ]]
   [:div.download
    [:button.positive
     {:on-click #(download)}
     [:i.lupicon-download.btn-small]
     [:span (loc (str "company.reports.download.report"))]]]])


(defn mount-component []
  (rum/mount (report)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId _]
  (swap! args assoc :dom-id (name domId))
  (mount-component))