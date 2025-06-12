(ns lupapalvelu.ui.application.building-product-catalog-dialog
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::add-reused-building-product-catalog.success
  (fn [_ [_ app-id]]
    {:fx [[:application/load app-id]
          [:dialog/close]]}))

(rf/reg-event-fx ::add-reused-building-product-catalog
  (fn [_ [_ app-id doc-id copy?]]
    {:action/command {:name    :add-building-product-catalog-reused
                      :params  {:id    app-id
                                :doc   doc-id
                                :copy? copy?}
                      :success #(>evt [::add-reused-building-product-catalog.success app-id])}}))

(defn view [{:keys [doc-id]}]
  (let [app-id (<sub [::app/application-id])]
    [:div
     [:div.flex-row (loc :rakennustuoteluettelo.hyodynnetyt.add.confirm)]
     [:div.mt-5.flex-row
      [:div.left
       [components/icon-button {:icon     :lupicon-copy
                                :class    :primary.close
                                :text-loc :rakennustuoteluettelo.hyodynnetyt.add.copy
                                :test-id  :invite-foreman
                                :on-click #(>evt [::add-reused-building-product-catalog app-id doc-id true])}]
       [components/icon-button {:icon     :lupicon-check
                                :class    :primary.close.ml-4
                                :text-loc :rakennustuoteluettelo.hyodynnetyt.add.new
                                :test-id  :invite-foreman
                                :on-click #(>evt [::add-reused-building-product-catalog app-id doc-id false])}]]
      [:div.right
       [components/icon-button {:icon     :lupicon-remove
                                :class    :secondary
                                :text-loc :cancel
                                :on-click #(>evt [:dialog/close])}]]]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id params]
  (ui-dom/mount! mounter dom-id (js->clj params :keywordize-keys true)))
