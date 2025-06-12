(ns lupapalvelu.ui.statistics.data.summary
  "Data keys under the datapath (see `data`):

  `:processing`: Map with only `:days` key with processing map.

  `:editing`: Exists only when the processing difficulty (`:months` key) is being
  edited. The initial value is copied from `:processding.days.months`."
  (:require [lupapalvelu.data :as data]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.statistics.data.entriestitle :as entriestitle]
            [re-frame.core :as rf]))

(def set-d    (partial data/set-d ::data))
(def get-d    (partial data/get-d ::data))
(def unset-d  (partial data/unset-d ::data))

;; -------------------------
;; Events
;; -------------------------

(rf/reg-event-fx ::update-service
  (fn [{db :db}]
    {:fx [[:dispatch [::entriestitle/update-service (get-d db :processing :days)]]]}))

(rf/reg-event-fx ::set-processing-days
  (fn [{db :db} [_ processing-days]]
    {:db (if processing-days
           (set-d db :processing :days (stats-sc/->front-processing processing-days))
           (unset-d db :processing))
     :fx [[:dispatch [::update-service]]]}))

(rf/reg-event-fx ::fetch.complete
  (fn [_ [_ {:keys [processing-days]}]]
    {:fx [[:dispatch [::set-processing-days processing-days]]]}))

(rf/reg-event-fx ::fetch-processing-days
  (fn []
    {:action/query {:name    :processing-days
                    :params  {:id (common/application-id)}
                    :success ::fetch.complete
                    :error   ::fetch.complete}}))

(rf/reg-event-fx ::toggle-visible
  (fn [{db :db} [_ visible?]]
    (cond-> {:db (unset-d db :processing :days)
             :fx [[:dispatch [::update-service]]]}
      visible? (assoc :fx [[:dispatch [::fetch-processing-days]]
                           [:dispatch [:auth/refresh]]]))))

(rf/reg-event-db ::edit-complexity
  (fn [db [_ complexity]]
    (set-d db :editing :complexity complexity)))

(rf/reg-event-db ::toggle-edit
  (fn [db [_ edit?]]
    (if edit?
      (set-d db :editing :complexity (or (get-d db :processing :days :complexity) :special))
      (unset-d db :editing))))

(rf/reg-event-fx ::save.success
  (fn [_ [_ {:keys [processing-days]}]]
    {:fx                    [;; Update summary immediately
                             [:dispatch [::set-processing-days processing-days]]
                             ;; Fetch logdata (which in turn updates summary again)
                             [:dispatch [:lupapalvelu.ui.statistics.data.log/fetch-logdata]]]
     :application/lightload (common/application-id)}))

(rf/reg-event-fx ::save-edit
  (fn [{db :db}]
    {:db             (-> db
                         (unset-d :editing)
                         (unset-d :show-log?)
                         (unset-d :processing :log))
     :action/command {:name    :set-processing-complexity
                      :params  {:id     (common/application-id)
                                :complexity (get-d db :editing :complexity)}
                      :success ::save.success}}))

;; -------------------------
;; Subs
;; -------------------------

(rf/reg-sub ::data :-> get-d)

(rf/reg-sub ::editing
  :<- [::data]
  :-> :editing)

(rf/reg-sub ::editing?
  :<- [::editing]
  :-> boolean)

(rf/reg-sub ::editing-complexity
  :<- [::editing]
  :-> (comp name :complexity))

(rf/reg-sub ::processing
  :<- [::data]
  :-> :processing)

(rf/reg-sub ::processing-days
  :<- [::processing]
  :-> :days)

(rf/reg-sub ::remaining-days
  :<- [::processing-days]
  :-> :remaining)

(rf/reg-sub ::status
  :<- [::processing-days]
  :-> :status)

(rf/reg-sub ::complexity?
  :<- [::processing-days]
  :-> (comp boolean :complexity))
