(ns lupapalvelu.ui.auth-admin.edit-authority.subs
  (:require [sade.strings :as ss]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as edit-authority-util]
            [lupapalvelu.ui.auth-admin.oauth.subs :as oauth-subs]
            [lupapalvelu.ui.util :as util]
            [re-frame.core :as rf]))

(rf/reg-sub ::loading?
  :<- [::loading-authority?]
  :<- [::loading-organization?]
  (fn [[loading-authority? loading-organization?] [_]]
    (or loading-authority? loading-organization?)))

(rf/reg-sub ::loading-authority?
  :-> ::loading-authority?)

(rf/reg-sub ::loading-organization?
  :-> ::loading-organization?)

(rf/reg-sub ::organization
  :-> ::organization)

(rf/reg-event-db ::fetch-organization.success
  (fn [db [_ {:keys [organization]}]]
    (-> db
        (dissoc ::loading-organization?)
        (assoc ::organization {:allowed-roles       (:allowedRoles organization)
                               :allowed-oauth-roles (:allowedOAuthRoles organization)
                               :org-id              (:id organization)}))))

(rf/reg-event-fx ::fetch-organization
  (fn [{:keys [db]} _]
    {:fx [[:action/query {:name    :organization-by-user
                          :success [::fetch-organization.success]}]]
     :db (assoc db ::loading-organization?? true)}))

(rf/reg-sub ::authority-id
  :-> ::authority-id)

(rf/reg-sub ::authorities
  :-> ::authorities)

(rf/reg-sub ::authority
  :<- [::authorities]
  :<- [::authority-id]
  (fn [[authorities authority-id] _]
    (get authorities authority-id)))

(rf/reg-sub ::multiple-org-authority?
  :<- [::authority]
  (fn [authority _]
    (-> authority
        :orgAuthz
        keys
        count
        (> 1))))

(rf/reg-sub ::different-domain-with-admin?
  :<- [::authority]
  (fn [authority _]
    (let [auth-admin-email    (util/get-user-field "email")
          authority-email     (:email authority)
          email-domain-picker (fn [email] (-> email (ss/split #"@") (last)))]
      (not= (email-domain-picker authority-email)
            (email-domain-picker auth-admin-email)))))

(rf/reg-sub ::authority-initial-roles
  :<- [::organization]
  :<- [::authority]
  (fn [[{:keys [org-id]} authority] _]
    (set (get-in authority [:orgAuthz (keyword org-id)]))))

(rf/reg-sub ::authority-initial-oauth-roles
  :<- [::organization]
  :<- [::authority]
  (fn [[{:keys [org-id]} authority] _]
    (edit-authority-util/user-oauth-roles-in-organization org-id authority)))

(rf/reg-sub ::authority-name
  :<- [::authority]
  :-> (comp ss/blank-as-nil (partial ss/join " ") (juxt :firstName :lastName)))

(rf/reg-event-db ::fetch-authority.success
  (fn [db [_ {:keys [data]} authority-id]]
    (-> db
        (dissoc ::loading-authority? ::edited-roles)
        (assoc-in [::authorities authority-id] data)
        (assoc-in [::authorities authority-id :edited-data] data))))

(rf/reg-event-fx ::fetch-authority
  (fn [{:keys [db]} [_ authority-id]]
    {:fx [[:action/query {:name    :user-for-edit-authority
                          :params  {:authority-id authority-id}
                          :success [::fetch-authority.success authority-id]}]]
     :db (-> db
             (assoc ::loading-authority? true
                    ::authority-id authority-id)
             (update ::authorities #(select-keys % [authority-id])))}))

(rf/reg-event-db ::clear-loading-authority
  (fn [db [_ _]]
    (dissoc db ::loading-authority?)))

(rf/reg-event-db ::toggle-roles
  (fn [db [_ role-type roles]]
    (assoc-in db [::authorities (-> db ::authority-id) :edited-roles role-type] roles)))

(rf/reg-sub ::edited-roles
  (fn [db [_ role-type]]
    (get-in db [::authorities (-> db ::authority-id) :edited-roles role-type])))

(rf/reg-event-fx ::update-roles
  (fn [{:keys [db]} [_ roles oauth-roles]]
    (let [authority-id (-> db ::authority-id)
          email        (get-in db [::authorities authority-id :email])]
      {:fx [[:action/command {:name                  :update-user-roles
                              :show-saved-indicator? true
                              :params                {:email       email
                                                      :roles       (vec roles)
                                                      :oauth-roles (vec oauth-roles)}
                              :error                 ::update-info.error
                              :success               (fn []
                                                       (>evt [::fetch-authority authority-id])
                                                       (>evt [::oauth-subs/fetch-users]))}]]
       :db (assoc db ::loading-authority? true)})))

(rf/reg-event-fx ::update-info.error
  (fn [{:keys [db]} [_ params]]
    {:fx [[:dialog/show {:ltitle :error.dialog.title
                         :ltext  (-> params :text)}]]
     :db (dissoc db ::loading-authority?)}))

(rf/reg-event-fx ::update-info
  (fn [{:keys [db]} _]
    (let [authority-id  (-> db ::authority-id)
          modified-info (get-in db [::authorities authority-id :edited-data])
          email         (get-in db [::authorities authority-id :email])]
      {:fx [[:action/command {:name                  :update-auth-info
                              :show-saved-indicator? true
                              :params                {:firstName (:firstName modified-info)
                                                      :lastName  (:lastName modified-info)
                                                      :email     email
                                                      :new-email (:email modified-info)}
                              :success               (fn []
                                                       (>evt [::fetch-authority authority-id])
                                                       (>evt [::oauth-subs/fetch-users]))
                              :error                 ::update-info.error}]]
       :db (assoc db ::loading-authority? true)})))

(rf/reg-sub ::edited-authority
  :<- [::authorities]
  :<- [::authority-id]
  (fn [[authorities authority-id] _]
    (get-in authorities [authority-id :edited-data])))

(rf/reg-sub ::ad-login?
  :<- [::authorities]
  :<- [::authority-id]
  (fn [[authorities authority-id] _]
    (-> authorities
        (get-in [authority-id :ad-login?])
        boolean)))

(rf/reg-sub ::current-org-roles-maintained-in-ad?
  :<- [::authorities]
  :<- [::authority-id]
  (fn [[authorities authority-id] _]
    (-> authorities
        (get-in [authority-id :current-org-roles-maintained-in-ad?])
        boolean)))
