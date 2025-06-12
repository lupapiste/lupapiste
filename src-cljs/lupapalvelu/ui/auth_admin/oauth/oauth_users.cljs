(ns lupapalvelu.ui.auth-admin.oauth.oauth-users
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-roles :as edit-authority-roles]
            [lupapalvelu.ui.auth-admin.edit-authority.subs :as edit-authority-subs]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as edit-authority-util]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.auth-admin.oauth.subs :as oauth-subs]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [reagent.core :as r]))

(def default-row-count 10)

(defn star-icon [position]
  [:i.lupicon-circle-star.primary
   {:aria-hidden true
    :style       (assoc {:font-size "0.8rem"
                         :position  :relative
                         :top       "-4px"}
                        (if (= position :right)
                          :margin-left
                          :margin-right)
                        "0.4rem")}])

(defn user-dialog [allowed-oauth-roles]
  (let [{:keys [email
                oauth-roles]} (<sub [:value/get ::oauth-subs/editing])
        invalid-data? (<sub [::oauth-subs/invalid-data?])]
    [:div
     [:form.form-grid.form-grid--no-border
      (common/loc :auth-admin.addUser.dialog.legend)
      [:div.row
       [:div.col
        [:label.required {:for "email"} (common/loc :userinfo.email)]
        [components/text-edit email {:id        "email"
                                     :callback  #(>evt [:value/set ::oauth-subs/editing :email %])
                                     :required? true}]]]
      (into [:div.row] (edit-authority-roles/checkbox-role-list
                         allowed-oauth-roles
                         oauth-roles
                         #(>evt [:value/set ::oauth-subs/editing :oauth-roles
                                 (edit-authority-util/set-toggle (or oauth-roles #{}) %)])
                         false))]
     [:div.button-group.flex.flex--gap1
      [:button.primary {:on-click (fn []
                                    (>evt [:dialog/close])
                                    (>evt [::oauth-subs/update-oauth-roles {:email email} oauth-roles false]))
                        :disabled invalid-data?}
       (common/loc :save)]
      [:button.secondary {:on-click #(>evt [:dialog/close])} (common/loc :cancel)]]]))

(defn user-row [user-id]
  (let [{:keys [org-id
                allowed-oauth-roles]} (<sub [::edit-authority-subs/organization])
        user                          (<sub [::oauth-subs/user-by-id user-id])
        roles-maintained-in-ad?       (:current-org-roles-maintained-in-ad? user)
        full-name                     (edit-authority-util/full-name user)
        oauth-roles                   (edit-authority-util/user-oauth-roles-in-organization org-id user)
        edit-allowed?                 (<sub [::oauth-subs/update-user-oauth-roles-allowed?])]
    [:tr
     [:td full-name (when roles-maintained-in-ad? (star-icon :right))]
     [:td (:email user)]
     (into [:td] (edit-authority-roles/checkbox-role-list allowed-oauth-roles oauth-roles
                                                          #(>evt [::oauth-subs/update-oauth-roles user
                                                                  (edit-authority-util/set-toggle oauth-roles %)
                                                                  false])
                                                          (or (not edit-allowed?) roles-maintained-in-ad?)))]))

(defn user-table []
  (r/with-let [_ (>evt [::edit-authority-subs/fetch-organization])
               _ (>evt [::oauth-subs/fetch-users])]
    (let [user-ids       (<sub [::oauth-subs/filtered-user-ids])
          organization   (<sub [::edit-authority-subs/organization])
          show-all?      (<sub [::oauth-subs/show-all?])
          users-count    (count user-ids)
          some-ad-users? (<sub [::oauth-subs/some-ad-users?])
          show-ad-users? (<sub [::oauth-subs/show-ad-users?])]
      (when (->> organization :allowed-oauth-roles seq)
        [:section
         [:div
          [:h2 (common/loc "auth-admin.external-system-users")]
          [:form {:auto-complete :off}
           [:div.flex--between
            [:div.users-search
             [components/text-edit "" {:id            "oauth-users-filter"
                                       :callback      #(>evt [::oauth-subs/set-users-filter-string %])
                                       :placeholder   (common/loc :auth-admin.users.search.placeholder)
                                       :class         "textinput textinput--big"
                                       :lux?          false}]]
            (when some-ad-users?
              [:div.right.flex--self-end
               [components/toggle show-ad-users?
                {:id       "show-ad-users"
                 :text-loc :auth-admin.external-system-users.show-ad-users
                 :callback #(>evt [::oauth-subs/toggle-show-ad-users %])}]])]]
          [:div.clear.spacerM]
          [:table.w--100.table.no-stripes.gap--b2
           [:thead
            [:tr.bg--gray
             [:th (common/loc :guest-authority.name)]
             [:th (common/loc :userinfo.email)]
             [:th (common/loc :auth-admin.edit-authority.roles-title)]]]
           (into [:tbody]
                 (for [user-id (cond->> user-ids
                                 (not show-all?) (take default-row-count))]
                   [user-row user-id]))]
          (let [col-style     {:style {:width "33.3333333333%"}}
                edit-allowed? (<sub [::oauth-subs/update-user-oauth-roles-allowed?])]
            [:div.dsp--flex.flex--column-gap2
             [:div col-style
              [components/icon-button {:class     :primary
                                       :text-loc  "auth-admin.addUser"
                                       :icon      :lupicon-circle-plus
                                       :disabled? (not edit-allowed?)
                                       :on-click  #(>evt [::oauth-subs/open-user-dialog user-dialog (:allowed-oauth-roles organization)])}]]
             [:div.dsp--flex.flex--center col-style
              (when (> users-count default-row-count)
                [components/icon-button {:class    :secondary
                                         :text     (if show-all? (common/loc "auth-admin.hide") (common/loc "auth-admin.show-all" users-count))
                                         :icon     (if show-all? :lupicon-arrow-up :lupicon-arrow-down)
                                         :on-click #(>evt [::oauth-subs/toggle-show-all])}])]
             [:div col-style
              (when show-ad-users?
                [:div.right
                 (star-icon :left)
                 (common/loc :auth-admin.edit-authority.cannot-edit-ad-user-roles)])]])]]))))

(defonce mounter (ui-dom/init :reagent (fn [] user-table)))

(defn ^:export start [dom-id _params]
  (ui-dom/mount! mounter dom-id))
