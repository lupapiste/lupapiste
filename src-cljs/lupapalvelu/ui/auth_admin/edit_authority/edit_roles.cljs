(ns lupapalvelu.ui.auth-admin.edit-authority.edit-roles
  (:require [clojure.set :as set]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.edit-authority.subs :as edit-authority-subs]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as edit-authority-util]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]))

(defn checkbox-role-list [role-choices selected-roles on-change disabled?]
  ;; Auth admin cannot give or remove digitization-project-user user role
  (for [[role title] (edit-authority-util/roles-with-titles role-choices)
        :when (not= role "digitization-project-user")]
    [components/toggle
     (contains? selected-roles role)
     {:text        title
      :callback    #(on-change role)
      :controlled? true
      :disabled?   disabled?
      :prefix      "checkbox"
      :class       "wrapper--no-border"}]))

(defn info-box [localization-key]
  [:div.top-marginM.bottom-marginM.dsp--flex.flex--column-gap1.bg--gray
   {:style {:width   :max-content
            :padding "1em"}}
   [:div.lupicon-circle-attention.primary {:aria-hidden true}]
   [:div (if (keyword? localization-key) (common/loc localization-key) localization-key)]])

(defn edit-authority-roles []
  (let [loading?                            (<sub [::edit-authority-subs/loading?])
        {:keys [allowed-roles
                allowed-oauth-roles]}       (<sub [::edit-authority-subs/organization])
        initial-roles                       (<sub [::edit-authority-subs/authority-initial-roles])
        digitization-project-user?          (contains? initial-roles "digitization-project-user")
        initial-oauth-roles                 (<sub [::edit-authority-subs/authority-initial-oauth-roles])
        roles                               (or (<sub [::edit-authority-subs/edited-roles :roles])
                                                initial-roles)
        oauth-roles                         (or (<sub [::edit-authority-subs/edited-roles :oauth-roles])
                                                initial-oauth-roles)
        invalid-roles                       (set/difference (set initial-roles) (set allowed-roles))
        current-org-roles-maintained-in-ad? (<sub [::edit-authority-subs/current-org-roles-maintained-in-ad?])]
    [:div.edit-authority-roles
     [:h2 (common/loc "auth-admin.edit-authority.roles-title")]
     (when current-org-roles-maintained-in-ad?
       [info-box :auth-admin.edit-authority.cannot-edit-ad-user-roles])
     (into [:<>] (checkbox-role-list allowed-roles roles
                                     #(>evt [::edit-authority-subs/toggle-roles :roles
                                             (edit-authority-util/set-toggle roles %)])
                                     current-org-roles-maintained-in-ad?))
     (when (seq invalid-roles)
       [:<>
        [:h2 (common/loc "auth-admin.edit-authority.invalid-roles-title")]
        (into [:<>] (checkbox-role-list invalid-roles roles
                                        #(>evt [::edit-authority-subs/toggle-roles :roles
                                                (edit-authority-util/set-toggle roles %)])
                                        current-org-roles-maintained-in-ad?))])
     (when (seq allowed-oauth-roles)
       [:<>
        [:h3.top-marginM (common/loc "auth-admin.edit-authority.oauth-roles-title")]
        (into [:<>] (checkbox-role-list allowed-oauth-roles oauth-roles
                                        #(>evt [::edit-authority-subs/toggle-roles :oauth-roles
                                                (edit-authority-util/set-toggle oauth-roles %)])
                                        current-org-roles-maintained-in-ad?))])
     (when-not current-org-roles-maintained-in-ad?
       (components/icon-button {:icon      :lupicon-save
                                :wait?     loading?
                                :disabled? (or digitization-project-user?
                                               (empty? roles)
                                               (and (= oauth-roles initial-oauth-roles)
                                                    (= roles initial-roles)))
                                :on-click  #(>evt [::edit-authority-subs/update-roles roles oauth-roles])
                                :text-loc  "save"
                                :test-id   "authz-roles-save"
                                :class     "primary"}))]))
