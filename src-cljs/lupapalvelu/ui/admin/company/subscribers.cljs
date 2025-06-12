(ns lupapalvelu.ui.admin.company.subscribers
  "Admin may choose whom to notify about changes in company data"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.shared-schemas :as schemas]
            [sade.strings :as ss]
            [schema.core :as sc]))

(rf/reg-event-fx :company.subscribers/set-visible
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      {:db (assoc-in db [::data :visible?] true)
       :fx [[:action/query {:name    :company-subscribers
                            :success [::company-subscribers.success]}]]}
      {:db (dissoc db ::data)})))

(rf/reg-event-fx ::company-subscribers.success
  (fn [{:keys [db]} [_ {:keys [subscribers]}]]
    {:db (update db ::data (fn [data]
                             (-> data
                                 (assoc :subscribers subscribers)
                                 (dissoc :new-email))))}))

(rf/reg-event-fx ::add-subscriber
  (fn [{{{email :new-email-trimmed} ::data} :db} _]
    {:action/command {:name    :company-add-subscriber
                      :params  {:email email}
                      :success [::company-subscribers.success]}}))

(rf/reg-event-fx ::remove-subscriber
  (fn [_ [_ email]]
    {:action/command {:name    :company-remove-subscriber
                      :params  {:email email}
                      :success [::company-subscribers.success]}}))

(rf/reg-event-fx ::set-new-email
  (fn [{:keys [db]} [_ email]]
    {:db (-> db
             (assoc-in [::data :new-email] email)
             (assoc-in [::data :new-email-trimmed] (some-> email ss/trim ss/blank-as-nil)))}))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-sub ::subscribers
  :<- [::data]
  :-> (comp seq :subscribers))

(rf/reg-sub ::new-email
  :<- [::data]
  :-> :new-email)

(def ^:private valid-email? (comp nil? (sc/checker schemas/Email)))

(rf/reg-sub ::can-add?
  :<- [::data]
  :-> (comp valid-email? :new-email-trimmed))

(defn- subscriber-item
  [email]
  [:div
   email
   [components/icon-button
    {:class      :btn-small.tertiary
     :icon       :lupicon-trash
     :icon-only? true
     :on-click   (fn []
                   (components/confirm-dialog
                     :company.delete-subscriber
                     :areyousure
                     #(>evt [::remove-subscriber email])))}]])

(defn- subscribers-list
  []
  (if-let [subscribers (<sub [::subscribers])]
    (->> subscribers
         (map (fn [email] ^{:key email} [subscriber-item email]))
         (into [:<>]))
    (common/loc :admin.company-subscribers.no-subscribers)))

(defn- new-email-input
  []
  [components/text-edit
   (<sub [::new-email])
   {:id          "new-subscriber-email"
    :placeholder (common/loc :admin.company-subscribers.add)
    :callback    (fn [value] (>evt [::set-new-email value]))}])

(defn- new-email-button
  []
  [components/icon-button
   {:icon      :lupicon-save
    :class     :primary
    :text-loc  :add
    :disabled? (not (<sub [::can-add?]))
    :on-click  #(>evt [::add-subscriber])}])

(defn- new-email-controls
  []
  [:div.flex.flex--gap1
   [new-email-input]
   [new-email-button]])

(defn view []
  (when (<sub [::visible?])
    [:div.bottom-marginL.flex.flex--column.flex--gap1
     [:h2 (common/loc :admin.company-subscribers.title)]
     [subscribers-list]
     [new-email-controls]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
