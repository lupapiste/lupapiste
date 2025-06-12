(ns lupapalvelu.ui.docgen.field.huoneistot.in-changes
  "The 'In changes' checkboxes

  For change permits we also list the original premises from the original
  application. The user can click row-specific checkboxes to include/exclude
  those imported rows from the change permit."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.huoneistot.common :as huoneistot-common]
            [lupapalvelu.ui.docgen.field.huoneistot.muutostapa :as muutostapa]
            [re-frame.core :as rf]
            [sade.util :as util]))

(def ^:const premises-note-id "change-permit-premises-note")

;; Similar to original `row.showInChanges()`
;; => Do we show the "in changes" checkbox or not
(rf/reg-sub ::show-in-changes?
  (fn show-in-changes-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    [(rf/subscribe [::huoneistot-common/change-permit?])
     (rf/subscribe [::source-value/has-source? doc-id (conj row-path :muutostapa)])])
  (fn [[change-permit? has-source?]]
    (and change-permit? has-source?)))

;; => Is the "in changes" checkbox enabled or not?
(rf/reg-sub ::in-changes?
  (fn in-changes-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::muutostapa/current-muutostapa doc-id row-path]))
  :-> some?)

(rf/reg-sub ::not-in-changes?
  (fn not-in-changes-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    [(rf/subscribe [::show-in-changes? doc-id row-path])
     (rf/subscribe [::in-changes? doc-id row-path])])
  (fn [[show-in-changes? in-changes?]]
    (and show-in-changes?
         (not in-changes?))))

(rf/reg-sub ::in-changes-rows
  (fn in-changes-rows-inputfn [[_ doc-id table-path]]
    {:pre [(string? doc-id) (vector? table-path)]}
    [(rf/subscribe [::huoneistot-common/change-permit?])
     (rf/subscribe [::docs/field-child-keys doc-id table-path])])
  (fn [[change-permit? row-ids] [_ doc-id table-path]]
    (when change-permit?
      (->> row-ids
           (map (fn [row-id] (conj table-path row-id)))
           (filter (fn [row-path] (<sub [::show-in-changes? doc-id row-path])))
           (into #{})))))

(rf/reg-sub ::all-in-changes?
  (fn all-in-changes-inputfn [[_ doc-id table-path]]
    {:pre [(string? doc-id) (vector? table-path)]}
    (rf/subscribe [::in-changes-rows doc-id table-path]))
  (fn [row-paths [_ doc-id _table-path]]
    ;; The column checkbox is checked if all the individual checkboxes are checked
    (every? (fn [row-path] (<sub [::in-changes? doc-id row-path]))
            row-paths)))

(rf/reg-sub ::show-premises-note?
  (fn show-premises-note-inputfn [_]
    (rf/subscribe [:auth/application? :change-permit-premises-note]))
  :-> boolean)

(defn premises-note
  []
  (when (<sub [::show-premises-note?])
    [:<>
     [:br]
     [:div.pate-note
      {:id premises-note-id}
      (common/loc :application.change-permit.premises.note)]]))

;;; "All in changes" checkbox
;; User can also use a checkbox in the column header to choose all the rows

(rf/reg-event-fx ::all-in-changes-clicked
  (fn all-in-changes-clicked-eventfn [{:keys [db] :as cofx} [_ app-id doc-id target-rows enabled?]]
    {:pre [(string? doc-id) (set? target-rows) (boolean? enabled?)]}
    ;; Mark all applicable rows as enabled (= restore original muutostapa)
    ;; or disabled (clear muutostapa)
    (let [field-paths   (->> target-rows
                             (mapv (fn [row-path] (conj row-path :muutostapa))))
          values        (->> field-paths
                             (mapv (fn [cell-path]
                                     ;; Either restore or clear the muutostapa
                                     (if enabled?
                                       (source-value/db->source-value db doc-id cell-path)
                                       ""))))
          callback-args (util/mmap app-id doc-id field-paths values)]
      (nutil/merge-every-fn (assoc cofx :db db)
                            [[command/update-doc-fx
                              (assoc callback-args
                                     :on-success [::docgen-common/on-save-success callback-args]
                                     :on-error [::docgen-common/on-save-error callback-args])]]))))

(defn all-checkbox
  "The header checkbox to toggle 'in-changes' for all rows"
  [{:keys [doc-id table-path disabled?]}]
  (when (<sub [::huoneistot-common/change-permit?])
    [:th
     (let [id          (str doc-id "-all-in-changes")
           app-id      (<sub [::app/application-id])
           target-rows (<sub [::in-changes-rows doc-id table-path])]
       [:div.checkbox-wrapper
        [ui-input/checkbox
         {:id              id
          :aria-labelledby premises-note-id
          :disabled?       disabled?
          :checked?        (<sub [::all-in-changes? doc-id table-path])
          :on-change       (fn [val] (>evt [::all-in-changes-clicked app-id doc-id target-rows (boolean val)]))}]
        [:label.checkbox-label
         {:for         id
          :aria-hidden true}]])]))

(defn row-checkbox
  "The 'in-changes' checkbox for a particular row."
  [{:keys [doc-id row-id row-path disabled?]}]
  [:td
   (when (<sub [::show-in-changes? doc-id row-path])
     (let [id                  (str doc-id "-huoneistot-muutos-" row-id)
           app-id              (<sub [::app/application-id])
           original-muutostapa (<sub [::muutostapa/original-muutostapa doc-id row-path])
           set-muutostapa      (muutostapa/make-set-muutostapa-callback app-id doc-id row-path)]
       [:div.checkbox-wrapper
        [ui-input/checkbox
         {:id              id
          :aria-labelledby premises-note-id
          :disabled?       disabled?
          :checked?        (<sub [::in-changes? doc-id row-path])
          :on-change       (fn [val]
                             (if val
                               ;; enabling => restore original muutostapa
                               (set-muutostapa original-muutostapa)
                               ;; clearing => clear muutostapa
                               (set-muutostapa "")))}]
        [:label.inline-label.checkbox-label
         {:for         id
          :aria-hidden true}]]))])
