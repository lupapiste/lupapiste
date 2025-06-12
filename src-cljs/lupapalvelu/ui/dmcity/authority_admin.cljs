(ns lupapalvelu.ui.dmcity.authority-admin
  "Authority admin can toggle dmcity configuration flags."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :refer [loc]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dmcity.shared :as dm-sh]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]))

;; ---------------------------
;; Events
;; ---------------------------

(rf/reg-event-db
  ::fetch-flags--success
  (fn [db [_ {:keys [flags]}]]
    (dm-sh/assoc-data db :flags flags)))

(rf/reg-event-fx
  ::fetch-flags
  (fn [{db :db} _]
    {:action/query {:name    :dmcity-configuration-flags
                    :params  {:organizationId (dm-sh/get-data db :organization-id)}
                    :success [::fetch-flags--success]}}))

(rf/reg-event-fx
  ::reset-organization-id
  (fn [{db :db} [_ organization-id]]
    {:db       (dm-sh/reset-data db :organization-id organization-id)
     :dispatch [::fetch-flags]}))

(rf/reg-event-db
  ::edit-flags
  (fn [db _]
    (dm-sh/assoc-data db :flags-edit (dm-sh/get-data db :flags))))

(rf/reg-event-fx
  ::save-flags
  (fn [{db :db} _]
    (let [{:keys [flags-edit
                  organization-id]} (dm-sh/get-data db)]
      {:action/command {:name                  :set-dmcity-configuration-flags
                        :params                {:organizationId organization-id
                                                :flags          flags-edit}
                        :show-saved-indicator? true
                        :success               #(>evt [::reset-organization-id organization-id])}})))

(rf/reg-event-db
  ::toggle-flag
  (fn [db [_ flag enabled?]]
    (dm-sh/update-data db assoc-in [:flags-edit flag] enabled?)))

(rf/reg-event-db
  ::cancel-edit-flags
  (fn [db _]
    (dm-sh/dissoc-data db :flags-edit)))

;; ---------------------------
;; Subs
;; ---------------------------

(rf/reg-sub
  ::flags
  :-> (dm-sh/access :flags))

(rf/reg-sub
  ::flags-edit
  :-> (dm-sh/access :flags-edit))

(rf/reg-sub
  ::active-flags
  :<- [::flags]
  :<- [::flags-edit]
  (fn [[flags flags-edit]]
    (or flags-edit flags)))

(rf/reg-sub
  ::flag-value
  :<- [::active-flags]
  (fn [flags [_ flag-key]]
    (get flags flag-key)))

(rf/reg-sub
  ::editing?
  :<- [::flags-edit]
  :-> some?)

;; ---------------------------
;; Components
;; ---------------------------

(defn- row [k]
  (let [value    (<sub [::flag-value k])
        editing? (<sub [::editing?])]
    (when (some? value)
      [:div.tabby__row
       [:div.tabby__cell (loc (dm-sh/flag-loc k))]
       [:div.tabby__cell.txt--center
        (cond
          editing? [components/toggle
                    value
                    {:text-loc :auth-admin.active
                     :callback #(>evt [::toggle-flag k %])}]
          value    [:i.fg--positive.lupicon-check
                    {:aria-label (loc :auth-admin.active)}])]])))

(def flag-keys (sort-by (comp loc dm-sh/flag-loc)
                        (conj dm-sh/permit-types :state
                              :building :address :link)))

(defn view []
  (let [flags    (<sub [::active-flags])
        editing? (<sub [::editing?])
        auth?    (<sub [:auth/global? :set-dmcity-configuration-flags])]
    (when flags
      [:div.gap--v6.flex--column
       [:h2.h2 (loc (<sub [::dm-sh/dmcity-product-loc]))]
       [:div.flex--column.flex--row-gap1
        [:div.flex--self-start.flex--column.flex--row-gap1
         [:div.dictionary.no-gap
          (->> flag-keys
               (keep row)
               (into [:div.tabby.bd--gray.gap--b0
                      [:div.tabby__row.dictionary--header
                       [:div.tabby__cell (loc :dmcity.usecase)]
                       [:div.tabby__cell (loc :auth-admin.active)]]]))]
         (cond
           editing?
           [:div.flex.flex--gap2.flex--right
            [components/icon-button {:text-loc :cancel
                                     :class    :tertiary
                                     :on-click #(>evt [::cancel-edit-flags])}]
            [components/icon-button {:text-loc :save
                                     :icon     :lupicon-save
                                     :class    :primary
                                     :on-click #(>evt [::save-flags])}]]

           auth?
           [components/icon-button {:text-loc :edit
                                    :icon     :lupicon-pen
                                    :class    :primary.flex--self-start
                                    :on-click #(>evt [::edit-flags])}])]]])))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id params]
  (>evt [::reset-organization-id (common/->cljs (common/oget params "organizationId"))])
  (ui-dom/mount! mounter dom-id))
