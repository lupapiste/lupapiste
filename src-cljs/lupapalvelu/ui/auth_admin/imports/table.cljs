(ns lupapalvelu.ui.auth-admin.imports.table
  "Table for viewing and editing imported rows."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.imports.data.config :as dcfg]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.imports.data.core :as idata]
            [lupapalvelu.ui.imports.field :as ifield]
            [sade.strings :as ss]))

(defn row-toggle
  "Open/close row."
  [row-id]
  (let [open? (<sub [::dcfg/row-open? row-id])]
    [components/icon-button {:icon-only? true
                             :text-loc   (if open? :close :open)
                             :icon       (if open?
                                           :lupicon-chevron-small-up
                                           :lupicon-chevron-small-down)
                             :class      :tertiary
                             :on-click   #(>evt [::dcfg/toggle-row-open row-id (not open?)])}]))

(defn row-buttons
  "Buttons in the actions column. Edit/close, Delete/restore."
  [row-id]
  (let [editing? (<sub [::idata/editing? row-id])
        deleted  (ss/not-blank? (<sub [::idata/row-deleted-datestring row-id]))]
    [:div.flex.flex--gap2
     (when-not deleted
       [:button {:class    (if editing? :secondary :primary)
                 :on-click #(>evt [::idata/toggle-edit row-id
                                   (not editing?)])}
        (common/loc (if editing? :close :edit))])
     [:button {:class    (if deleted :secondary :primary)
               :on-click #(>evt [::idata/mark-row-deleted row-id
                                 (not deleted)])}
      (common/loc (if deleted :pate-restore :remove))]]))

(defn row-group
  "Table row(s) for an imported row. When opened, the original (excel) data is shown for
  each cell. Also the deleted/modifed datetime is shown. Cells are viewers/editors
  depending whether editing is active."
  [row-id i]
  (let [enabled? (not (<sub [::dcfg/disabled?]))
        editing? (<sub [::idata/editing? row-id])
        open?    (<sub [::dcfg/row-open? row-id])
        deleted  (some->> (<sub [::idata/row-deleted-datestring row-id])
                          (str (common/loc :imports.config.deleted) " "))
        added    (<sub [::idata/row-added-infostring row-id])
        cell-ids (<sub [::idata/row-cell-ids row-id])
        tr-style {:background-color (if (even? i) :white "#f7f7f7")}]
    [(cond-> (into [:tr {:style (cond-> tr-style
                                  open? (assoc :border-bottom :none))}
                    [:td.align-top [row-toggle row-id]]]
                   (for [cell-id cell-ids]
                      ^{:key cell-id}
                      [:td {:class (if editing? :align-top :align-middle)}
                       [ifield/field row-id cell-id]]))
       enabled?
       (conj [:td.align-top [row-buttons row-id]]))
     (when (and open? (not added))
       ;; Original values
       (conj (into [:tr {:style (assoc tr-style
                                       :border-top :none :border-bottom :none)}
                    [:td]]
                   (for [cell-id cell-ids]
                     [:td.align-middle.fg--gray.txt--small
                      (<sub [::idata/cell-original row-id cell-id])]))
             [:td]))
     (when open?
       ;; Modified/deleted timestamp
       (let [ts-string (or deleted
                           (some->> (<sub [::idata/row-modified-datestring row-id])
                                    (str (common/loc :pate.verdict-table.last-edit) " ")))]
         [:tr {:style (assoc tr-style :border-top :none)}
          [:td]
          [:td.txt--small {:col-span (count cell-ids)}
           (if added
             [:div.flex--between.flex--gap2
              [:span added] [:span ts-string]]
             [:div.txt-align-right ts-string])]
          [:td]]))]))

(defn- table-body []
  (let [cfg-id  (<sub [::dcfg/config-id])
        row-ids (<sub [::idata/config-row-ids cfg-id])]
    (into [:tbody]
          (mapcat row-group row-ids (range)))))

(defn pager
  "Paging UI support. Total row count and the current page/total pages is shown (when
  multiple pages). Navigation buttons for first, previous, next and last page are shown
  when prudent."
  []
  (let [row-total   (<sub [::dcfg/row-total])
        window      (<sub [::dcfg/window-index])
        first?      (<sub [::dcfg/first-window?])
        last?       (<sub [::dcfg/last-window?])
        last-window (<sub [::dcfg/last-window-index])]
    [:div.flex.flex--gap1.flex--align-center
     (when-not first?
       [components/icon-button {:icon-only? true
                                :icon       :lupicon-chevron-start
                                :title-loc  :imports.window.first
                                :class      :secondary.btn-small
                                :on-click   #(>evt [::dcfg/move-row-window 0])}])
     (when-not first?
       [components/icon-button {:icon-only? true
                                :icon       :lupicon-chevron-left
                                :title-loc  :previous
                                :class      :secondary.btn-small
                                :on-click   #(>evt [::dcfg/move-row-window (dec window)])}])
     [:div (cond-> (common/loc (if (= 1 row-total) :imports.rows.one :imports.rows.many)
                               row-total)
             (not (and first? last?))
             (str " " (common/loc :imports.page (inc window) (inc last-window))))]
     (when-not last?
       [components/icon-button {:icon-only? true
                                :icon       :lupicon-chevron-right
                                :title-loc  :next
                                :class      :secondary.btn-small
                                :on-click   #(>evt [::dcfg/move-row-window (inc window)])}])
     (when-not last?
       [components/icon-button {:icon-only? true
                                :icon       :lupicon-chevron-end
                                :title-loc  :imports.window.last
                                :class      :secondary.btn-small
                                :on-click   #(>evt [::dcfg/move-row-window last-window])}])]))


(defn tri-state-buttons
  "Tri-state (include, exclude, only) buttons for deleted and bad row filters."
  []
  [:div.flex.flex--gap2
   [components/tri-state-button (<sub [::dcfg/include-deleted-rows])
    {:text-loc :imports.rows.deleted
     :callback #(>evt [::dcfg/include-deleted-rows %])}]
   [components/tri-state-button (<sub [::dcfg/include-error-rows])
    {:text-loc :imports.rows.errors
     :callback #(>evt [::dcfg/include-error-rows %])}]
   [components/tri-state-button (<sub [::dcfg/include-added-rows])
    {:text-loc :imports.rows.added
     :callback #(>evt [::dcfg/include-added-rows %])}]])

(defn imports
  "Imported rows table view."
  []
  (let [imported? (<sub [::dcfg/imported?])
        deleted?  (<sub [::dcfg/deleted?])
        row-total (<sub [::dcfg/row-total])
        enabled?  (not (<sub [::dcfg/disabled?]))]
    (when (and (not deleted?) imported? row-total)
      (let [column-ids (<sub [::dcfg/sorted-column-ids])
            first?     (<sub [::dcfg/first-window?])
            last?      (<sub [::dcfg/last-window?])]
        [:div.flex--column.flex--gap1 {:style {:width :fit-content}}
         [:div.flex--between.flex--gap2
          [pager]
          [tri-state-buttons]]
         [:div {:style {:width :fit-content
                        :max-width  "100%"
                        :overflow-x :scroll}}
          [:table.size-width {:style {:margin-bottom 0}}
           [:thead
            (cond-> (into [:tr [:th]]
                          (for [col-id column-ids]
                            [:th (<sub [::dcfg/titlestring col-id])]))
              enabled? (conj [:th (common/loc :admin.actions)]))]
           [table-body]]]
         (when-not (and first? last?)
           [pager])]))))
