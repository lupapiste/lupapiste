(ns lupapalvelu.ui.docgen.field.huoneistot.cells
  "Render table cells"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.huoneistot.action-cells :as actions]
            [lupapalvelu.ui.docgen.field.huoneistot.cells-common :as cells-common]
            [lupapalvelu.ui.docgen.field.huoneistot.columns :as columns]
            [lupapalvelu.ui.docgen.field.huoneistot.common :as huoneistot-common]
            [lupapalvelu.ui.docgen.field.huoneistot.in-changes :as in-changes]
            [lupapalvelu.ui.docgen.field.huoneistot.muutostapa :as muutostapa]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-sub ::row-path->cell-paths
  (fn row-path-cell-paths-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::columns/columns doc-id (pop row-path)]))
  (fn [columns [_ _doc-id row-path]]
    (->> columns
         (remove :synthetic?)
         (map :column-name)
         (map (fn [cell] (conj row-path cell)))
         (into []))))

(rf/reg-event-fx ::change-immediate
  ;; Unfortunately we cannot leverage `docgen-common/make-on-change-immediate`
  ;; directly as we also have to check if the magical `muutostapa` field
  ;; should also be updated when the table data is changed.
  (fn change-immediate-eventfn [{:keys [db] :as cofx} [_ {:keys [app-id cell-path cell-paths change-permit? doc-id value]}]]
    {:pre [(string? app-id) (string? doc-id) (vector? cell-path) (vector? cell-paths) (boolean? change-permit?)]}
    (let [row-path            (pop cell-path)
          old-muutostapa      (muutostapa/db->current-muutostapa db doc-id row-path)
          ;; First we apply the edit as this affects the `muutostapa` calculation result
          db                  (value/fields-edited-db db {:doc-id      doc-id
                                                          :field-paths [cell-path]
                                                          :values      [value]})
          new-muutostapa      (muutostapa/db->new-muutostapa db (util/mmap doc-id row-path change-permit? cell-paths))
          ;; Check if this edit should also update the muutostapa field
          muutostapa-changed? (not= old-muutostapa new-muutostapa)
          ;; Note that we don't immediately write the new muutostapa to db,
          ;; it will be updated when the command callback finishes
          muutostapa-path     (conj row-path :muutostapa)
          field-paths         (cond-> [cell-path]
                                muutostapa-changed? (conj muutostapa-path))
          values              (cond-> [value]
                                muutostapa-changed? (conj new-muutostapa))
          callback-args       (util/mmap app-id doc-id field-paths values)]
      (nutil/merge-every-fn (assoc cofx :db db)
                            [[command/update-doc-fx
                              (assoc callback-args
                                     :on-success [::docgen-common/on-save-success callback-args]
                                     :on-error [::docgen-common/on-save-error callback-args])]]))))

;; Similar to `::change-immediate` but the db already contains the edits
(rf/reg-event-fx ::on-blur
  (fn on-blur-eventf [{:keys [db] :as cofx} [_ {:keys [app-id backend-value cell-path cell-paths change-permit? doc-id value]}]]
    {:pre [(string? app-id) (vector? cell-path) (vector? cell-paths) (boolean? change-permit?) (string? doc-id)]}
    (when (not= (ss/blank-as-nil value)
                (ss/blank-as-nil backend-value))
      (let [row-path            (pop cell-path)
            old-muutostapa      (muutostapa/db->current-muutostapa db doc-id row-path)
            new-muutostapa      (muutostapa/db->new-muutostapa db (util/mmap doc-id row-path change-permit? cell-paths))
            muutostapa-changed? (not= old-muutostapa new-muutostapa)
            muutostapa-path     (conj row-path :muutostapa)
            field-paths         (cond-> [cell-path]
                                  muutostapa-changed? (conj muutostapa-path))
            values              (cond-> [value]
                                  muutostapa-changed? (conj new-muutostapa))
            callback-args       (util/mmap app-id doc-id field-paths values)]
        (nutil/merge-every-fn (assoc cofx :db db)
                              [[command/update-doc-fx
                                (assoc callback-args
                                       :on-success [::docgen-common/on-save-success callback-args]
                                       :on-error [::docgen-common/on-save-error callback-args])]])))))

;; Checkbox cell

(defn- checkbox-cell
  "Render cell as a checkbox field"
  [{:keys [cell-path column-name disabled? doc-id row-id row-path]}]
  (let [change-permit? (<sub [::huoneistot-common/change-permit?])
        id             (str doc-id "-" (name column-name) "-" row-id)
        app-id         (<sub [::app/application-id])
        disabled?      (or disabled?
                           (<sub [::cells-common/cell-disabled? doc-id row-path]))
        title          (<sub [::cells-common/cell-tooltip doc-id cell-path :checkbox])]
    [:td.content-column
     [:div.checkbox-wrapper
      [ui-input/checkbox
       (cond-> {:id       id
                :class    (<sub [::cells-common/cell-css doc-id cell-path])
                :checked? (<sub [::value/current-field-value doc-id cell-path])}
         (not disabled?) (assoc :on-change (let [cell-paths (<sub [::row-path->cell-paths doc-id row-path])]
                                             (fn [value]
                                               (>evt [::change-immediate
                                                      (util/mmap app-id doc-id cell-path value change-permit? cell-paths)]))))
         disabled?       (assoc :disabled? true))]
      [:label.inline-label.checkbox-label
       {:for         id
        :aria-hidden true
        :title       title}]]]))

;; Label cell

(defn- label-cell
  "Render cell as a read-only label"
  [{:keys [cell-path doc-id]}]
  [:td.content-column
   [:span
    (or (<sub [::value/current-field-value doc-id cell-path])
        "")]])

;; Select cell

(defn- select-cell
  "Render cell as a select field"
  [{:keys [cell-path column-name disabled? doc-id row-id row-path]}]
  (let [change-permit? (<sub [::huoneistot-common/change-permit?])
        app-id         (<sub [::app/application-id])
        disabled?      (or disabled?
                           (<sub [::cells-common/cell-disabled? doc-id row-path]))
        title          (<sub [::cells-common/cell-tooltip doc-id cell-path :select])
        options        (<sub [::cells-common/select-cell-options doc-id cell-path])
        classes        (<sub [::cells-common/cell-css doc-id cell-path])
        focus-ref      (focus/use-focus doc-id cell-path)]
    [:td.content-column
     [:span.form-entry.form-input
      [:div.inline
       [ui-input/select
        (cond-> {:aria-label (common/loc cell-path)
                 :class      (into ["dropdown" "form-input" "short"] classes)
                 :disabled?  disabled?
                 :id         (str doc-id "-" (name column-name) "-" row-id)
                 :value      (or (<sub [::value/current-field-value doc-id cell-path])
                                 "")}
          (not disabled?) (assoc :on-change  (let [cell-paths (<sub [::row-path->cell-paths doc-id row-path])]
                                               (fn [value]
                                                 (>evt [::change-immediate
                                                        (util/mmap app-id doc-id cell-path cell-paths value change-permit?)]))))
          disabled?       (assoc :disabled? true)
          focus-ref       (assoc :ref focus-ref)
          title           (assoc :title title))
        options]]]]))

;; Text cell

(defn- text-cell
  "Render cell as a text-input field"
  [{:keys [cell-path column-name column-schema disabled? doc-id row-path row-id]}]
  (let [change-permit? (<sub [::huoneistot-common/change-permit?])
        app-id         (<sub [::app/application-id])
        disabled?      (or disabled?
                           (<sub [::cells-common/cell-disabled? doc-id row-path]))
        title          (<sub [::cells-common/cell-tooltip doc-id cell-path :text])
        aria-flags     (docgen-common/aria-flags
                         (<sub [::validation/field-data-validation-result-level doc-id cell-path])
                         ;; Error message id will point to the error displayed in `row-warnings`
                         {:error-message (path/path->warning-message-id cell-path doc-id)})
        backend-value  (<sub [::committed/backend-submitted-value doc-id cell-path])
        classes        (<sub [::cells-common/cell-css doc-id cell-path])
        focus-ref      (focus/use-focus doc-id cell-path)]
    [:td.content-column
     [:span.form-entry.form-input
      [:div.inline
       [ui-input/text-input
        (cond-> {:aria-label  (common/loc cell-path)
                 :class       (into ["lux" "form-input" "tiny"] classes)
                 :debounce-ms docgen-common/default-input-debounce-ms
                 :id          (str doc-id "-" (name column-name) "-" row-id)
                 ;; NOTE: The original docgen also sometimes supplied `min`/`max`,
                 ;;       but those don't apply to "text" inputs
                 :max-length  (or (:max-len column-schema) 255)
                 :value       (or (<sub [::value/current-field-value doc-id cell-path])
                                  "")}
          (seq aria-flags) (merge aria-flags)
          (not disabled?)  (assoc :on-change (docgen-common/make-on-change (util/mmap {:path cell-path} doc-id))
                                  :on-blur (let [cell-paths (<sub [::row-path->cell-paths doc-id row-path])]
                                             (fn [value]
                                               (>evt [::on-blur (util/mmap backend-value app-id doc-id
                                                                           cell-path value change-permit? cell-paths)]))))
          focus-ref        (assoc :ref focus-ref)
          disabled?        (assoc :disabled? true)
          title            (assoc :title title))]]]]))

;;; Table body

(defn table-cell
  [{:keys [cell-path column-name column-schema column-type
           disabled? doc-id removable? row-id row-path schema]}]
  (case column-type
    :actions    [actions/actions-cell (util/mmap doc-id removable? row-path schema)]
    :checkbox   [checkbox-cell (util/mmap cell-path column-name disabled? doc-id row-id row-path)]
    :label      [label-cell (util/mmap cell-path doc-id)]
    :select     [:f> select-cell (util/mmap cell-path column-name disabled? doc-id row-path row-id)]
    :text       [:f> text-cell (util/mmap cell-path column-name column-schema disabled? doc-id row-path row-id)]
    :in-changes [in-changes/row-checkbox (util/mmap doc-id row-id row-path disabled?)]))
