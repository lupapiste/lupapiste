(ns lupapalvelu.ui.admin.organization-settings.ryhti-config
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn on-visible
  [{:keys [db]} _]
  (when-let [org-id (router/path-parameter db :org-id)]
    {:fx [[:action/query {:name    :ryhti-api-configuration
                          :params  {:organizationId org-id}
                          :success [::fetch-configuration.success org-id]}]]}))

(defn on-hidden
  [{:keys [db]} _]
  {:db (dissoc db ::configurations)})

(rf/reg-event-db ::fetch-configuration.success
  (fn [db [_ data]]
    (assoc db ::configurations (get data :configuration []))))

(rf/reg-event-fx ::save
  (fn [{db :db} _]
    (let [org-id  (router/path-parameter db :org-id)]
      {:fx [[:action/command {:name                  :set-ryhti-api-configuration
                              :show-saved-indicator? true
                              :params                {:organizationId org-id
                                                      :ryhtiConfig    (get db ::configurations)}
                              :success               [::fetch-configuration.success org-id]}]]})))

(rf/reg-event-db ::add-new-municipality
  (fn [db _]
    (update db ::configurations conj {:municipality  ""
                                      :client-id     ""
                                      :client-secret ""})))

(rf/reg-event-db ::change
  (fn [db [_ {:keys [id value key]}]]
    (assoc-in db [::configurations key id] value)))

(rf/reg-event-fx ::delete
  (fn [{db :db} [_ idx]]
    {:db (update db ::configurations (fn [v] (util/drop-nth idx v)))
     :dispatch [::save]}))

(rf/reg-sub ::configurations
  :-> ::configurations)

(defn- config-input [idx {:keys [municipality client-id client-secret]}]
  [:tr
   [:td
    [:label.form-input.pb-1 {:for :ryhti-api-municipality-input}
     (common/loc :ryhti.configuration.municipality.label)]
    [:label.form-input.pb-1 {:for :ryhti-api-client-input}
     (common/loc :ryhti.configuration.client-id.label)]
    [:label.form-input.pb-1 {:for :ryhti-api-secret-input}
     (common/loc :ryhti.configuration.client-secret.label)]]
   [:td
    [:input {:id        :ryhti-api-municipality-input
             :class     "form-input text width-80"
             :value     municipality
             :on-change (fn [e] (>evt [::change {:id    :municipality
                                                 :key   idx
                                                 :value (util/tval e)}]))}]
    [:input {:id        :ryhti-api-client-input
             :class     "form-input text width-80"
             :value     client-id
             :on-change (fn [e] (>evt [::change {:id    :client-id
                                                 :key   idx
                                                 :value (util/tval e)}]))}]
    [:input {:id        :ryhti-api-secret-input
             :class     "form-input text width-80"
             :value     client-secret
             :on-change (fn [e] (>evt [::change {:id    :client-secret
                                                 :key   idx
                                                 :value (util/tval e)}]))}]]
   [:td
    [:button.primary {:on-click #(>evt [::delete idx])}
     (common/loc :ryhti.configuration.delete)]]])

(defn- view []
  (let [configurations (<sub [::configurations])]
    [:div.gap--v6
     [:h2.h2 (common/loc :ryhti.configuration.title)]
     [:table.table
      [:thead
       [:tr
        [:th {:style {:width "10%"}} (common/loc :auth-admin.parameter)]
        [:th (common/loc :auth-admin.value)]
        [:th {:style {:width "5%"}}]]]
      [:tbody
       (for [[idx config] (map-indexed vector configurations)]
         ^{:key (str "ryhti-config-" idx)} [config-input idx config])
       [:tr
        [:td
         [:button.primary {:on-click #(>evt [::add-new-municipality])}
          (common/loc :ryhti.configuration.add-new-municipality)]
         [:button.primary {:on-click #(>evt [::save])}
          (common/loc :save)]]]]]]))

(defonce mounter (ui-dom/init :reagent (fn[] view)))

(defn ^:export start [dom-id _params]
  (ui-dom/mount! mounter dom-id))
