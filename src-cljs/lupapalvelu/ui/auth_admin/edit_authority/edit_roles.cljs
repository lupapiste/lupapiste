(ns lupapalvelu.ui.auth-admin.edit-authority.edit-roles
  (:require [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.common :refer [loc command]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.common.hub :as hub]
            [rum.core :as rum]))


(defn- update-roles [roles*]
  (let [success-fn (fn []
                     (reset! roles* nil)
                     (reset! state/saving-roles? false)
                     (util/get-authority)
                     (hub/send "organization-user-change"))
        params {:command               "update-user-roles"
                :show-saved-indicator? true
                :success               success-fn}
        email (:email @state/authority)
        roles-as-vector (vec @roles*)]
    (reset! state/saving-roles? true)
    (command params :email email :roles roles-as-vector)))

(rum/defcs edit-authority-roles
  < (rum/local #{} :altered-roles*)
  [{:keys [altered-roles*]} current-authority org-id allowed-roles]
  (let [initial-roles              (set (get-in current-authority [:orgAuthz org-id]))
        digitization-project-user? (contains? initial-roles "digitization-project-user")
        roles                      (if (seq @altered-roles*)
                                     @altered-roles*
                                     initial-roles)]
    [:div.edit-authority-roles
     [:h2 (loc "auth-admin.edit-authority.roles-title")]
     ;; Auth admin cannot give or remove digitization-project-user user role
     (for [role allowed-roles
           :when (not= role "digitization-project-user")]
       [:div.checkbox-wrapper {:key (str "role-check-box-" role)}
        [:input {:type      "checkbox"
                 :checked   (contains? roles role)
                 :on-change (fn [_]
                              (if (seq @altered-roles*)
                                (swap! altered-roles* util/set-toggle role)
                                (->> (util/set-toggle initial-roles role)
                                     (reset! altered-roles*))))
                 :id        (str "role-check-box-" role)}]
        [:label.checkbox-label {:for (str "role-check-box-" role)}
         [:span (loc (str "authorityrole." role))]]])
     (components/icon-button {:icon      :lupicon-save
                              :wait?     state/saving-roles?
                              :disabled? (or digitization-project-user? (empty? @altered-roles*) (= @altered-roles* initial-roles))
                              :on-click  #(update-roles altered-roles*)
                              :text-loc  "save"
                              :test-id   "authz-roles-save"
                              :class     "primary"})]))
