(ns lupapalvelu.ui.auth-admin.edit-authority.edit-roles
  (:require [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.components :as components]
            [rum.core :as rum]))

(defn- role-checkbox [current-roles label]
  [:div.checkbox-wrapper {:key (str "role-check-box-" label)}
   [:input {:type      "checkbox"
            :checked   (some? (@current-roles label))
            :on-change #(swap! current-roles util/set-toggle label)
            :id        (str "role-check-box-" label)}]
   [:label.checkbox-label {:for (str "role-check-box-" label)}

    [:span (loc (str "authorityrole." label))]]])

(defn- update-roles [roles]
  (let [success-fn (fn []
                     (reset! state/saving-roles? false)
                     (util/get-authority))
        params {:command               "update-user-roles"
                :show-saved-indicator? true
                :success               success-fn}
        email (:email @state/authority)
        roles-as-vector (vec @roles)]
    (reset! state/saving-roles? true)
    (command params :email email :roles roles-as-vector)))

(defn- current-authority-roles-as-set [user]
  (let [org-id @state/org-id]
    (-> user
        :orgAuthz
        (get org-id)
        (set))))

(rum/defc edit-authority-roles < rum/reactive
  []
  (let [current-authority (rum/react state/authority)
        current-roles-set (atom (current-authority-roles-as-set current-authority))]
    [:div.edit-authority-roles
     [:h2 (loc "auth-admin.edit-authority.roles-title")]
     (for [role (rum/react state/allowed-roles)]
       (role-checkbox current-roles-set role))
     (components/icon-button {:icon :lupicon-save
                              :wait? state/saving-roles?
                              :on-click #(update-roles current-roles-set)
                              :text-loc "save"
                              :class "primary"})]))