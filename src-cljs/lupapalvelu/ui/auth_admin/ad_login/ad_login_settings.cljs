(ns lupapalvelu.ui.auth-admin.ad-login.ad-login-settings
  "Authority-admin AD login settings; can only configure the organization's role-mapping.
  See `lupapalvelu.ui.admin.ad-login.ad-login-settings` for the admin config for the rest."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.ad-login.subs :as ad-login-subs]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as edit-authority-util]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]))

(def span-style {:style {:line-height "40px"}})

(defn role-mapping-rows [editing? roles allowed-roles]
  (into [:tbody]
        (for [[role title] (edit-authority-util/roles-with-titles roles)]
          [:tr
           [:td
            [:span span-style
             (str title (when-not (allowed-roles role)
                          (str " (" (loc "auth-admin.ad-login-settings.not-in-use") ")")))]]
           [:td.value
            (if editing?
              [components/text-edit (<sub [::ad-login-subs/role-mapping role])
               {:name     role
                :callback #(>evt [:value/set [::ad-login-subs/data :role-mapping role] %])}]
              [:span span-style
               (<sub [::ad-login-subs/role-mapping role])])]])))

(defn ad-login-settings []
  (let [all-roles           (<sub [::ad-login-subs/all-roles])
        allowed-roles       (<sub [::ad-login-subs/allowed-roles])
        all-oauth-roles     (<sub [::ad-login-subs/all-oauth-roles])
        allowed-oauth-roles (<sub [::ad-login-subs/allowed-oauth-roles])
        show-all-roles?     (<sub [::ad-login-subs/show-all-roles?])
        editing?            (<sub [::ad-login-subs/editing?])
        editing-allowed?    (<sub [:auth/global? :update-ad-login-role-mapping])]
    (when all-roles
      [:div
       [:h2 (loc "auth-admin.ad-login-settings.title")]
       [:div (loc "auth-admin.ad-login-settings.info-text")]
       (when (or (not= all-roles allowed-roles)
                 (not= all-oauth-roles allowed-oauth-roles))
         [:div.right [components/toggle show-all-roles?
                      {:id       "show-all-roles"
                       :text-loc :auth-admin.ad-login-settings.show-all-roles
                       :callback #(>evt [::ad-login-subs/toggle-show-all-roles %])}]])
       (when-not (<sub [::ad-login-subs/fetching?])
         [:<>
          [:table {:class "admin-settings"}
           [:thead
            [:tr
             [:th (loc "auth-admin.ad-login-settings.lp-role")]
             [:th (loc "auth-admin.ad-login-settings.ad-role")]]]
           [role-mapping-rows editing? (if show-all-roles? all-roles allowed-roles)
            allowed-roles]
           (when (or show-all-roles? (seq allowed-oauth-roles))
             [:tbody
              [:tr
               [:th (loc "auth-admin.ad-login-settings.oauth-role")]
               [:th]]])
           [role-mapping-rows editing? (if show-all-roles? all-oauth-roles allowed-oauth-roles)
            allowed-oauth-roles]]
          (if editing?
            [:div.flex.flex--gap1
             [components/icon-button {:icon     :lupicon-remove
                                      :text-loc :cancel
                                      :class    :secondary
                                      :on-click #(>evt [::ad-login-subs/cancel-editing])}]

             [components/icon-button {:icon     :lupicon-save
                                      :wait?    (<sub [::ad-login-subs/saving?])
                                      :on-click #(>evt [::ad-login-subs/save-role-mapping])
                                      :text-loc :save
                                      :class    :primary}]]
            (when editing-allowed?
              [components/icon-button {:icon     :lupicon-pen
                                       :text-loc :edit
                                       :class    :primary
                                       :on-click #(>evt [::ad-login-subs/start-editing])}]))])])))

(defonce mounter (ui-dom/init :reagent (fn [] ad-login-settings)))

(defn ^:export start [dom-id _params]
  (ui-dom/mount! mounter dom-id))
