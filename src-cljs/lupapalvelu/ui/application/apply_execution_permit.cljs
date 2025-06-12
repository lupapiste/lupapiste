(ns lupapalvelu.ui.application.apply-execution-permit
  "Apply for execution permit shown in the application view on sijoittamislupa"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.application.data :as data]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]))

(defn view []
  (let [operations         (<sub [:value/get ::data/operations])
        selected-operation (<sub [::data/selected-operation])]
    [:div.operation-tree-container
     [:h2 (loc :application.applyExecutionPermit.title)]
     (->> operations
          (mapv (fn [op]
                  [:li
                   [:button.operation-tree-button
                    {:on-click #(>evt [::data/select-operation op])
                     :class    (when (= op selected-operation)
                                 "active")}
                    [:span (loc (str op "._group_label"))]]]))
          (into [:ul.operation-tree.top-marginM
                 {:style {:list-style  "none"
                          :margin-left "-30px"}}]))
     [:div.top-marginM.flex.flex--gap1
      [components/icon-button
       {:icon     :lupicon-chevron-left
        :class    :secondary
        :text-loc :cancel
        :on-click #(>evt [::data/cancel])}]
      [components/icon-button
       {:icon      :lupicon-circle-arrow-right
        :right?    true
        :class     :primary
        :text-loc  :application.applyExecutionPermit
        :disabled? (not selected-operation)
        :on-click  #(>evt [::data/create-operation])}]]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (>evt [::data/fetch-operations])
  (ui-dom/mount! mounter dom-id))
