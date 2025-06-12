(ns lupapalvelu.ui.assurances.data
  (:require [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.authorization :as auth]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::toggle-view
  (fn [{db :db} [_ visible?]]
    (if visible?
      {:fx [[:dispatch [::get-assurances]]]
       :db (assoc db ::visible visible?)}
      {:db (dissoc db ::assurances)})))

(rf/reg-event-fx ::get-assurances
  (fn [{db :db} _]
    (let [app-id (-> db (router/path-parameter :app-id))]
      (when app-id
        {:fx [[:action/query {:name    :get-assurances-for-application
                              :params  {:id app-id}
                              :success [::get-assurances.success]}]]}))))

(rf/reg-event-db ::get-assurances.success
  (fn [db [_ {data :data}]]
    (let [app-id (-> db (router/path-parameter :app-id))
          accordion-open? (get-in db
                                  [::assurances-accordion app-id]
                                  (auth/application-auth? :enable-accordions))]
      (-> db
          (assoc ::assurances data)
          (assoc-in [::assurances-accordion app-id] accordion-open?)))))

(rf/reg-event-db ::toggle-accordion
  (fn [db _]
    (update-in db [::assurances-accordion (-> db (router/path-parameter :app-id))] not)))

(rf/reg-sub ::assurances-data
  (fn [db _]
    (::assurances db)))

(rf/reg-sub ::assurances-accordion
  (fn [db _]
    (get-in db [::assurances-accordion (-> db (router/path-parameter :app-id))])))

(rf/reg-sub ::visible
  (fn [db _]
    (::visible db)))
