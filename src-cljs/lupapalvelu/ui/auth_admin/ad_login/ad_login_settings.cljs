(ns lupapalvelu.ui.auth-admin.ad-login.ad-login-settings
  (:require [lupapalvelu.ui.auth-admin.ad-login.state :as state]
            [lupapalvelu.ui.common :refer [loc command]]
            [lupapalvelu.ui.components :as components]
            [rum.core :as rum]))

(defn init [init-state]
  (state/init-organization)
  init-state)

(defn role-key-textfield [role]
  (let [role-name (name role)]
    [:tr {:key (str "role-key-" role-name)}
     [:td (loc (str "authorityrole." role-name))]
     [:td {:class "value"}
      (components/text-edit (get (rum/react state/current-role-mapping) role)
                            {:name     role-name
                             :callback #(swap! state/current-role-mapping
                                               assoc role %)})]]))

(defn save-role-mapping []
  (command {:command "update-ad-login-role-mapping"
            :show-saved-indicator? true
            :success (fn [_]
                       (state/init-organization)
                       (reset! state/saving-info? false))
            :error (fn [call]
                     (reset! state/saving-info? false)
                     (.ajaxError js/notify (clj->js call)))}
           :organizationId @state/org-id
           :role-map @state/current-role-mapping))

(rum/defc ad-login-settings < rum/reactive
  {:init init
   :will-unmount (fn [& _] (reset! state/component-state {}))}
  []
  [:div
   [:h2 (loc "auth-admin.ad-login-settings.title")]
   [:div (loc "auth-admin.ad-login-settings.info-text")]

   [:table {:class "admin-settings"}
    [:thead
     [:tr
      [:th (loc "auth-admin.ad-login-settings.lp-role")]
      [:th (loc "auth-admin.ad-login-settings.ad-role")]]]

    [:tbody
     (for [role (rum/react state/allowed-roles)]
       (role-key-textfield role))]]
   (components/icon-button {:icon     :lupicon-save
                            :wait?    state/saving-info?
                            :on-click save-role-mapping
                            :text-loc "save"
                            :class    "positive"})])

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (ad-login-settings)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId]
  (swap! args assoc :dom-id (name domId))
  (mount-component))
