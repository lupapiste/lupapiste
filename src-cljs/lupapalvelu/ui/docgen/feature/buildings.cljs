(ns lupapalvelu.ui.docgen.feature.buildings
  (:require [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.docgen.feature.application :as application]
            [re-frame.core :as rf]))

(rf/reg-sub ::data
  :-> ::buildings)

(rf/reg-sub ::building-options
  :<- [::data]
  :-> :building-options)

(rf/reg-event-fx ::get-building-options
  (fn [{:keys [db]} [_ app-id]]
    (let [property-id    (-> db ::application/data :property-id)
          update-needed? (not= (:latest-property-id (::buildings db))
                               property-id)]
      (cond-> {:db (update db ::buildings assoc :latest-property-id property-id)
               :fx []}

        (and update-needed? (auth/application-auth? :get-building-info-from-wfs))
        (update :fx conj [:action/query {:name    :get-building-info-from-wfs
                                         :params  {:id app-id}
                                         :success [::get-building-options.success]}])))))

(rf/reg-event-db ::get-building-options.success
  (fn [app-db [_ resp]]
    (assoc-in app-db [::buildings :building-options] (:data resp))))

(defn on-unload [{:keys [db]}]
  ;; on-unload doesn't run on application/load etc., so on-app-loaded can still check
  ;; ::buildings from previous state on application data refresh.
  {:db (dissoc db ::buildings)})

(defn on-app-loaded
  [{:keys [db]} [_ {{:keys [id]} :app}]]
  {:db (assoc db ::buildings {:latest-property-id nil
                              :building-options nil})
   ;; Don't trigger the get-buildings-options unless we already have the options listed!
   ;; The buildingSelector component will trigger the initial load, so the options aren't
   ;; loaded for applications which don't contain documents using the component.
   :fx [(when (:latest-property-id (::buildings db))
          [:dispatch [::get-building-options id]])]})
