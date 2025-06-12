(ns lupapalvelu.ui.auth-admin.inspection-summary.view
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.inspection-summary.subs :as subs]
            [lupapalvelu.ui.auth-admin.inspection-summary.table :as table]
            [lupapalvelu.ui.auth-admin.org-subs :as org-subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]))

(rf/reg-sub ::visible?
  :<- [::subs/data]
  :-> (comp boolean :visible?))

(defn- enable-inspection-summaries-checkbox
  []
  (let [id "inspection-summaries-enabled"]
    [:div.blockbox-wrapper
     [ui-input/checkbox
      {:id        id
       :checked?  (<sub [::org-subs/inspection-summaries-enabled?])
       :disabled? (not (<sub [:auth/global? :set-organization-inspection-summaries]))
       :on-change (fn [checked?] (>evt [::subs/inspection-summaries-enabled-clicked checked?]))}]
     [:label {:for   id
              :class "blockbox-label"}
      (common/loc :auth-admin.inspection-summaries.enabled)]]))

(defn view
  []
  (when (<sub [::visible?])
    [:section
     [:h2 (common/loc :auth-admin.inspection-summaries)]
     [enable-inspection-summaries-checkbox]
     (when (<sub [::org-subs/inspection-summaries-enabled?])
       [:<>
        [:h3.top-marginM (common/loc :auth-admin.inspection-summaries.subtitle)]
        [table/inspection-summaries]])]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
