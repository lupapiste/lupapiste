(ns lupapalvelu.ui.docgen.feature.partial-review-buildings
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::view-state
  :-> ::view-state)

(rf/reg-sub ::selected-building-index
  (fn [db [_]]
   (get-in db [::view-state :selected-building-index])))

(rf/reg-sub ::selected-apartment-index
  :<- [::view-state]
  :-> :selected-apartment-index)

(rf/reg-sub ::side-panel-open?
  :<- [::view-state]
  (fn [{:keys [:side-panel-open?]}]
    (or side-panel-open? false)))

(rf/reg-event-db
 ::set-selected-building-index
  (fn [db [_ building-index]]
    (-> db
        (assoc-in [::view-state :selected-building-index] building-index)
        (update-in [::view-state] dissoc :selected-apartment-index))))

(rf/reg-event-db ::set-selected-apartment-index
  (fn [db [_ building-index]]
    (assoc-in db [::view-state :selected-apartment-index] building-index)))

(rf/reg-event-db
  ::close-side-panel
  (fn [db [_]]
    (assoc-in db [::view-state :side-panel-open?] false)))

(rf/reg-event-db
  ::open-side-panel
  (fn [db [_]]
    (assoc-in db [::view-state :side-panel-open?] true)))

(rf/reg-sub
  ::mobile-active-display
  :<- [::view-state]
  (fn [{:keys [selected-building-index selected-apartment-index side-panel-open?]}]
    (cond (every? nil? [selected-building-index selected-apartment-index]) :all-buildings
          (and selected-building-index (not side-panel-open?))             :building
          (and selected-apartment-index side-panel-open?)                  :apartment
          :else                                                            :all-buildings)))

(defn on-unload [{:keys [db]}]
  {:db (dissoc db ::view-state)})
