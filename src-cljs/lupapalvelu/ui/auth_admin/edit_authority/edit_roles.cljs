(ns lupapalvelu.ui.auth-admin.edit-authority.edit-roles
  (:require [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.common :refer [loc query command]]
            [rum.core :as rum]))

(defn- role-checkbox [current-roles label]
  [:div {:key (str "role-check-box-" label)}
   [:label
    [:input {:type      "checkbox"
             :checked   (@current-roles label)
             :on-change #(swap! current-roles util/set-toggle label)}]
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
        current-roles-set (atom (current-authority-roles-as-set current-authority))
        disabled? (not= @current-roles-set
                        (-> current-authority :orgAuthz (get @state/org-id) (set)))
        saving? (rum/react state/saving-roles?)]
    [:div
     [:h2 (loc "auth-admin.edit-authority.roles-title")]
     (for [role (rum/react state/allowed-roles)]
       (role-checkbox current-roles-set role))
     [:button.primary {:disabled disabled?
                       :on-click #(update-roles current-roles-set)}
      (if saving?
        [:i.lupicon.wait.spin.lupicon-refresh]
        [:i.lupicon-save])
      [:span (loc "save")]]]))