(ns lupapalvelu.ui.suomifi-login.subs
  (:require [lupapalvelu.next.event :refer [>evt]]
            [re-frame.core :as rf]))

(rf/reg-event-db ::users-fetched
  (fn [db [_ accounts]]
    (-> db
        (assoc-in [::data :users] accounts)
        (assoc-in [::data :loading-users?] false))))

(rf/reg-event-fx ::fetch-users
  (fn [{:keys [db]} [_ {:keys [on-error on-success]}]]
    {:db           (assoc-in db [::data :loading-users?] true)
     :action/query {:name    :passwordless-login-accounts
                    :error   (fn [resp]
                               (>evt [::users-fetched nil])
                               (if on-error
                                 (on-error resp)
                                 (rf/dispatch [:route/push :suomifi-error])))
                    :success (fn [{:keys [accounts] :as resp}]
                               (>evt [::users-fetched accounts])
                               (cond
                                 (empty? accounts) (rf/dispatch [:route/push :suomifi-registration])
                                 on-success        (on-success resp)))}}))

(defn users?
  [db]
  (->> db ::data :users some?))

(rf/reg-sub ::data
  :-> ::data)

(rf/reg-sub ::users
  :<- [::data]
  :-> :users)

(rf/reg-sub ::loading-users?
  :<- [::data]
  :-> :loading-users?)
