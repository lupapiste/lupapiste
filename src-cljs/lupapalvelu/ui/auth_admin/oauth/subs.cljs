(ns lupapalvelu.ui.auth-admin.oauth.subs
  (:require [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as edit-authority-util]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(rf/reg-sub ::update-user-oauth-roles-allowed?
  :<- [:auth/global? :update-user-oauth-roles]
  :-> identity)

(rf/reg-event-db ::fetch-users.success
  (fn [db [_ {:keys [users]}]]
    (assoc db ::users users)))

(rf/reg-sub ::users
  :-> ::users)

(rf/reg-sub ::user-by-id
  :<- [::users]
  (fn [users [_ user-id]]
    (->> users
         (filter #(-> % :id (= user-id)))
         first)))

(rf/reg-event-db ::set-users-filter-string
  (fn [db [_ filter-string]]
    (if (ss/blank? filter-string)
      (dissoc db ::users-filter-string)
      (assoc db ::users-filter-string filter-string))))

(rf/reg-sub ::users-filter-string
  :-> ::users-filter-string)

(def user-filter-data (juxt :email edit-authority-util/full-name))

(defn user-data-includes? [filter-string user]
  (->> user
       user-filter-data
       (ss/trim-join " ")
       (re-find (common/fuzzy-re filter-string))))

(rf/reg-sub ::filtered-user-ids
  :<- [::users]
  :<- [::users-filter-string]
  :<- [::show-ad-users?]
  (fn [[users filter-string show-ad-users?] _]
    (cond->> users
      (not show-ad-users?)          (remove #(-> % :current-org-roles-maintained-in-ad?))
      (ss/not-blank? filter-string) (filter (partial user-data-includes? filter-string))
      true                          (map :id))))

(rf/reg-event-fx ::update-oauth-roles.error
  (fn [_ [_ params]]
    {:dialog/show {:ltitle :error.dialog.title
                   :ltext  (-> params :text)}}))

(rf/reg-event-fx ::update-oauth-roles
  (fn [_ [_ user oauth-roles force]]
    ;; Show confirm dialog when removing user's last role
    (if (and (empty? oauth-roles)
             (not force))
      {:dialog/show {:ltitle   :areyousure
                     :text     (common/loc :oauth-user-list.delete.confirmation (edit-authority-util/full-name user))
                     :type     :yes-no
                     :callback #(>evt [::update-oauth-roles user oauth-roles true])}}
      {:action/command {:name                  :update-user-oauth-roles
                        :show-saved-indicator? true
                        :params                {:email       (:email user)
                                                :oauth-roles (vec oauth-roles)}
                        :error                 ::update-oauth-roles.error
                        :success               (fn []
                                                 (>evt [::fetch-users])
                                                 (js/hub.send "redraw-users-list"))}})))

(rf/reg-event-fx ::fetch-users
  (fn [_ _]
    {:action/query {:name    :get-oauth-users
                    :success [::fetch-users.success]}}))

(defn invalid-email? [email]
  (not (some-> email ss/blank-as-nil js/util.isValidEmailAddress)))

(rf/reg-sub ::invalid-data?
  :<- [:value/get ::editing]
  (fn [{:keys [email oauth-roles]}]
    (or (invalid-email? email)
        (empty? oauth-roles))))

(rf/reg-event-fx ::open-user-dialog
  (fn [_ [_ user-dialog-component allowed-oauth-roles]]
    {:dialog/show {:title-loc        :auth-admin.addUser
                   :size             :medium
                   :type             :react
                   :dialog-component [user-dialog-component allowed-oauth-roles]
                   :minContentHeight "220px"}
     :fx          [[:dispatch [:value/set ::editing {}]]]}))

(rf/reg-sub ::show-all?
  :-> ::show-all?)

(rf/reg-event-db ::toggle-show-all
  (fn [db _]
    (update db ::show-all? not)))

(rf/reg-event-db ::toggle-show-ad-users
  (fn [db _]
    (update db ::show-ad-users? not)))

(rf/reg-sub ::show-ad-users?
  :-> (comp boolean ::show-ad-users?))

(rf/reg-sub ::some-ad-users?
  :<- [::users]
  :-> (comp boolean (partial some :current-org-roles-maintained-in-ad?)))
