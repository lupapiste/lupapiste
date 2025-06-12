(ns lupapalvelu.ui.docgen.feature.foreman-history
  (:require [re-frame.core :as rf]))

(rf/reg-event-db ::query-foreman-history.success
  (fn [db [_ resp doc-id]]
    (update-in db [::foreman-history doc-id]
               (fn [x]
                 (assoc x
                        :projects (:projects resp)
                        :show-check (or (:show-all x)
                                        (not (:all resp))))))))

(rf/reg-event-fx ::query-foreman-history
  (fn [_ [_ app-id doc-id all]]
    {:action/query {:name :foreman-history
                    :params {:id app-id
                             :docId doc-id
                             :all (boolean all)}
                    :success [::query-foreman-history.success doc-id]}}))

(rf/reg-sub ::view-data
  :-> ::foreman-history)

(rf/reg-sub ::projects
  :<- [::view-data]
  (fn [view-data [_ doc-id]]
    (:projects (get view-data doc-id))))

(rf/reg-sub ::show-all
  :<- [::view-data]
  (fn [view-data [_ doc-id]]
    (:show-all (get view-data doc-id))))

(rf/reg-sub ::show-check
  :<- [::view-data]
  (fn [view-data [_ doc-id]]
    (:show-check (get view-data doc-id) true)))

(rf/reg-event-db ::set-show-all
  (fn [db [_ doc-id x]]
    (assoc-in db [::foreman-history doc-id :show-all] x)))

;; Load triggered per document from the field component
(defn on-app-loaded [_ _]
  nil)

(defn on-unload [{:keys [db]}]
  {:db (dissoc db ::foreman-history)})
