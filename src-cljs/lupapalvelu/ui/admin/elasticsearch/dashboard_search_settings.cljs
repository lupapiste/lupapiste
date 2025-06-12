(ns lupapalvelu.ui.admin.elasticsearch.dashboard-search-settings
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-event-fx ::init
  (fn [{db :db} [_ visible?]]
    (if visible?
      {:fx [[:action/query {:name    :dashboard-search-settings
                            :success [::dashboard-search-settings.success]}]]}
      {:db (dissoc db ::data)})))

(rf/reg-event-db ::dashboard-search-settings.success
  (fn [db [_ {:keys [settings] :as _response}]]
    (assoc db ::data settings)))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::setting
  :<- [::data]
  (fn [data [_ key]]
    (key data)))

(rf/reg-event-fx ::toggle-setting
  (fn [_ [_ setting value]]
    {:fx [[:action/command {:name                  :toggle-dashboard-search-setting
                            :show-saved-indicator? true
                            :params                (util/mmap setting value)
                            :success               [::dashboard-search-settings.success]}]]}))

(defn view []
  [:div
   [:h2.mt-2 "Työpöydän haku"]
   [:div.mt-4 "Käytä Elasticsearchia kaikkien käyttäjien hakuihin työpöydän osioissa:"]
   [:ul.twp.mt-4.mb-4
    [:li
     [components/toggle
      (<sub [::setting :application-search])
      {:id          "application-search-toggle"
       :callback    #(>evt [::toggle-setting :application-search %])
       :controlled? true
       :text        "Asiat ja Työnjohtajahankkeet"}]]
    [:li
     [components/toggle
      (<sub [::setting :foreman-search])
      {:id          "foreman-search-toggle"
       :callback    #(>evt [::toggle-setting :foreman-search %])
       :controlled? true
       :text        "Työnjohtajaosapuolet"}]]]])
