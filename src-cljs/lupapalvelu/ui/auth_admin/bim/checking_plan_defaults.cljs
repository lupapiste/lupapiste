(ns lupapalvelu.ui.auth-admin.bim.checking-plan-defaults
  "Authority admin configuration for checking plans (Tarkastussuunnitelma).
  In practice involves selecting which checking plans (configured by the Lupapiste admin)
  are the defaults for which operations (toimenpide)."
  (:require [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.util :as util]))

(rf/reg-sub ::plans :-> ::plans)

(rf/reg-sub ::default-plans :-> ::default-plans)

(rf/reg-sub ::type->plans
  :<- [::plans]
  #(group-by :checking-type %))

(rf/reg-sub ::operations
  (fn [db _]
    (->> (::operations db)
         (map (fn [op]
                {:value op
                 :text  (loc (util/kw-path :operations op))}))
         (sort-by :text))))

(rf/reg-sub ::plan-selected?
  :<- [::default-plans]
  (fn [default-plans [_ plan-id operation]]
    (contains? (get default-plans (keyword operation)) plan-id)))

(rf/reg-event-fx ::fetch-plans
  (fn [_ [_ org-id]]
    {:action/query {:name    :bim-checking-plans-for-org
                    :params  {:organizationId org-id}
                    :success (fn [{:keys [bim-checking-plans operations default-plans]}]
                               (>evt [:value/set ::plans bim-checking-plans])
                               (>evt [:value/set ::operations operations])
                               (>evt [:value/set ::default-plans (util/map-values set default-plans)]))}}))

(rf/reg-event-fx ::toggle-plan
  (fn [_ [_ org-id operation plan-id enabled]]
    {:action/command {:name   :update-bim-checking-plan-for-operation
                      :params {:organizationId org-id
                               :operation      operation
                               :plan-id        plan-id
                               :enabled        enabled}}}))

(defn plan-toggle
  "A checkbox that is used to include/exclude the given checking plan from the default list of checking plans
  for the given operation"
  [org-id operation {:keys [enabled title] plan-id :id}]
  (let [value (<sub [::plan-selected? plan-id operation])]
    [components/toggle
     value
     {:text      title
      ;; Can't turn the default on if the plan has been disabled but can do the opposite
      :disabled? (and (not enabled) (not value))
      :callback  #(>evt [::toggle-plan org-id operation plan-id %])}]))

(defn operation-view
  "The configuration is based on operations where each operation has a list of enabled or disabled checking plans"
  [org-id operation]
  (let [type->plans (<sub [::type->plans])]
    [:div.pad--1
     [:h3 (:text operation)]
     (->> bim-cp-schema/checking-types
          (keep (fn [checking-type]
                  (when (seq (type->plans checking-type))
                    [:div.flex--column
                     [:span.txt--bold (->> checking-type (util/kw-path :application.bim.checking-type) loc)]
                     (->> (type->plans checking-type)
                          (map (fn [plan]
                                 [plan-toggle org-id (:value operation) plan]))
                          (into [:<>]))])))
          (into [:div.flex--wrap.flex--gap5]))]))

(defn view
  "A configuration view for setting the default checking plans for operations"
  [org-id]
  (r/with-let [_ (>evt [::fetch-plans org-id])]
    [:div
     [:h2 (loc :admin.bim.checking-plans.header)]
     [:table
      (->> (<sub [::operations])
           (map (fn [operation] [:tr [:td [operation-view org-id operation]]]))
           (into [:tbody]))]]))

(defonce args (atom {}))

(defonce mounter (ui-dom/init :reagent (constantly #(view (:orgId @args)))))

(defn ^:export start [dom-id params]
  (swap! args assoc :orgId ((common/oget params "organizationId")))
  (ui-dom/mount! mounter dom-id))
