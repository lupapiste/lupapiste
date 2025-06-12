(ns lupapalvelu.ui.auth-admin.ad-login.subs
  (:require [lupapalvelu.next.event :refer [>evt]]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-event-fx :ad-login-settings/init
  (fn [{:keys [db]} [_ init?]]
    (if init?
      {:fx       [[:action/query {:name    :ad-login-role-mapping
                                  :success [::fetch-ad-login-role-mapping.success]}]]
       :dispatch [:auth/refresh]
       :db       (-> db
                     (assoc ::fetching? true
                            ::editing? false)
                     (dissoc ::data))}
      {:db (dissoc db ::data)})))

(defn- ->kw-set [coll]
  (->> coll
       (map keyword)
       set))

(rf/reg-event-db ::fetch-ad-login-role-mapping.success
  (fn [db [_ {:keys [allowed-roles allowed-oauth-roles all-roles all-oauth-roles role-mapping]}]]
    (-> db
        (assoc ::data {:allowed-roles       (->kw-set allowed-roles)
                       :allowed-oauth-roles (->kw-set allowed-oauth-roles)
                       :all-roles           (->kw-set all-roles)
                       :all-oauth-roles     (->kw-set all-oauth-roles)
                       :role-mapping        role-mapping}
               ::show-all-roles? false)
        (dissoc ::fetching?))))

(rf/reg-event-db ::save-ad-login-role-mapping.success
  (fn [db _]
    (-> db
        (dissoc ::saving? ::editing?)
        (util/dissoc-in [::data :original-role-mapping]))))

(rf/reg-event-db ::save-ad-login-role-mapping.error
  (fn [db _]
    (dissoc db ::saving?)))

(rf/reg-event-fx ::save-role-mapping
  (fn [{:keys [db]} _]
    {:fx [[:action/command {:name                  :update-ad-login-role-mapping
                            :show-saved-indicator? true
                            :params                {:role-map (-> db ::data :role-mapping)}
                            :success               ::save-ad-login-role-mapping.success
                            :error                 (fn [e]
                                                     (>evt [::save-ad-login-role-mapping.error])
                                                     (js/notify.ajaxError (clj->js e)))}]]
     :db (assoc db ::saving? true)}))

(rf/reg-sub ::saving?
  :-> (comp boolean ::saving?))

(rf/reg-sub ::fetching?
  :-> (comp boolean ::fetching?))

(rf/reg-sub ::data
  :-> ::data)

(rf/reg-sub ::all-roles
  :<- [::data]
  :-> :all-roles)

(rf/reg-sub ::allowed-roles
  :<- [::data]
  :-> :allowed-roles)

(rf/reg-sub ::all-oauth-roles
  :<- [::data]
  :-> :all-oauth-roles)

(rf/reg-sub ::allowed-oauth-roles
  :<- [::data]
  :-> :allowed-oauth-roles)

(rf/reg-sub ::role-mapping
  :<- [::data]
  (fn [data [_ role]]
    (-> data :role-mapping (get role))))

(rf/reg-event-db ::toggle-show-all-roles
  (fn [db [_ show-all-roles]]
    (assoc db ::show-all-roles? show-all-roles)))

(rf/reg-sub ::show-all-roles?
  :-> ::show-all-roles?)

(rf/reg-event-db ::start-editing
  (fn [db _]
    (-> db
        (assoc ::editing? true)
        (assoc-in [::data :original-role-mapping] (-> db ::data :role-mapping)))))

(rf/reg-event-db ::cancel-editing
  (fn [db _]
    (-> db
        (dissoc ::editing? true)
        (assoc-in [::data :role-mapping] (-> db ::data :original-role-mapping))
        (util/dissoc-in [::data :original-role-mapping]))))

(rf/reg-sub ::editing?
  :-> (comp boolean ::editing?))
