(ns lupapalvelu.ui.conversion.view
  "Application conversion section."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.conversion.data :as data]
            [lupapalvelu.ui.conversion.layout :as layout]
            [lupapalvelu.ui.dom :as ui-dom]
            [sade.util :as util]))

(defn last-modified []
  [:span.fg--gray
   (js/util.finnishDateAndTime (<sub [::data/last-modified])
                               (common/loc :conversion.modified))])

(defn edit-button []
  (let [can-edit? (<sub [:auth/application? :edit-conversion-section])
        editing?  (<sub [::data/editing?])]
    (when can-edit?
      [:button.primary
       {:on-click #(>evt [::data/set-editing? (not editing?)])}
       (common/loc (util/kw-path :a11y.summary
                                 (if editing? :stop-edit :edit)))])))

(defn view []
  [:div.flex--column.flex--row-gap2
   [:div.flex--wrap.flex--between.flex--align-center
    [:h1 (common/loc :conversion.section)]
    [:div.flex--right.flex--align-center.flex--gap2
     [last-modified]
     [edit-button]]]
   [layout/sections]])


(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
