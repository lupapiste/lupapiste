(ns lupapalvelu.ui.imports.view
  "Imports section view. Non-deleted rows for each active import configuration within the
  application organization are shown."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.imports.data.view :as dview]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.imports.field :as ifield]
            [lupapalvelu.ui.imports.data.core :as idata]
            [lupapalvelu.ui.components :as components]))

(defn can-edit? []
  (<sub [:auth/application? :edit-application-import-row]))

(defn editing? []
  (<sub [::dview/editing?]))

(defn delete-button [row-id]
  (let [deleted? (<sub [::idata/row-deleted? row-id])
        fun      #(>evt [::idata/mark-row-deleted row-id (not deleted?)])
        m        (if deleted?
                   {:icon      :lupicon-undo
                    :title-loc :pate-restore}
                   {:icon      :lupicon-remove
                    :title-loc :remove})]
    [components/icon-button (merge {:class      :tertiary.gap--t2
                                    :icon-only? true
                                    :on-click   fun}
                                   m)]))

(defn row [row-id]
  (cond-> (into [:div.flex--wrap.flex--column-gap3.flex--row-gap2]
                (for [cell-id (<sub [::idata/row-cell-ids row-id])]
                  [ifield/field row-id cell-id {:label? true}]))
    (editing?) (conj [delete-button row-id])))

(defn subsection [cfg-id]
  [:div.flex--column.flex--row-gap1.gap--t2
   [:h2 (<sub [::idata/config-title cfg-id])]
   (let [deleted? (<sub [::dview/show-deleted?])]
     (if-let [row-ids (<sub [::idata/config-row-ids cfg-id :include-deleted? deleted?])]
       (into [:div.flex--column.flex--row-gap1]
             (for [row-id row-ids]
               [row row-id]))
       [:span.fg--gray (common/loc :imports.section.no-rows)]))
   (when (editing?)
     [components/icon-button {:class    :primary.flex--self-start
                              :icon     :lupicon-circle-plus
                              :text-loc :imports.section.add-row
                              :on-click #(>evt [::dview/add-row cfg-id])}])])

(defn edit-button []
  (when (can-edit?)
    (let [editing? (editing?)
          opts     {:on-click #(>evt [::dview/toggle-edit (not editing?)])}]
      (if editing?
        [:button.secondary opts (common/loc :close)]
        [:button.primary opts (common/loc :edit)]))))

(defn show-deleted-toggle []
  (when (and (editing?) (<sub [::idata/deleted-rows?]))
    (let [show-deleted? (<sub [::dview/show-deleted?])]
      [components/toggle show-deleted? {:text-loc :handler-roles.show-all
                                        :callback #(>evt [::dview/toggle-show-deleted %])}])))

(defn last-modified []
  (when-let [ts (<sub [::idata/latest-cell-modified-ts])]
    [:span.fg--gray (str (common/loc :pate.verdict-table.last-edit) " "
                         (date/finnish-datetime ts))]))

(defn view []
  (when (<sub [::dview/visible?])
    [:div.flex--column.flex--row-gap1
     [:h1 (common/loc :imports.section.title)]
     [:div.flex--wrap.flex--align-center.flex--gap2
      [last-modified]
      [edit-button]
      [show-deleted-toggle]]
     (into [:div.flex--column.flex--row-gap4]
           (for [cfg-id (<sub [(if (editing?)
                                 ::idata/config-ids
                                 ::dview/row-config-ids
                                 )])]
             [subsection cfg-id]))]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
