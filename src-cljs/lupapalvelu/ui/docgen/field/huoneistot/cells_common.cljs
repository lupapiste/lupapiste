(ns lupapalvelu.ui.docgen.field.huoneistot.cells-common
  "The business logic of rows and cells is quite interlinked,
  so this namespace provides subscriptions for both."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.feature.flags :as flags]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.huoneistot.columns :as columns]
            [lupapalvelu.ui.docgen.field.huoneistot.in-changes :as in-changes]
            [lupapalvelu.ui.docgen.field.huoneistot.muutostapa :as muutostapa]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::cell-disabled?
  (fn [[_ doc-id row-path]]
    [(rf/subscribe [::row-is-to-be-removed? doc-id row-path])
     (rf/subscribe [::in-changes/not-in-changes? doc-id row-path])])
  (fn [[to-be-removed-row? not-in-changes?]]
    (boolean (or to-be-removed-row?
                 not-in-changes?))))

(defn- empty-option
  []
  {:value ""
   :label (common/loc :selectone)})

(rf/reg-sub ::select-cell-options
  (fn [[_ doc-id cell-path]]
    (rf/subscribe [::field-schemas/field-schema doc-id cell-path]))
  (fn [{:keys [body]} [_ _doc-id cell-path]]
    (let [column-name (peek cell-path)]
      (->> body
           (filter flags/include-schema?)
           (map (fn [element]
                  (let [loc-key (or (:i18nkey element)
                                    (path/path->str [:huoneistot column-name (:name element)]))]
                    {:value (:name element)
                     :label (common/loc loc-key)})))
           (cons (empty-option))
           (into [])))))

(rf/reg-sub ::edited-cell?
  (fn edited-cell-inputfn [[_ doc-id cell-path]]
    {:pre [(string? doc-id) (vector? cell-path)]}
    [(rf/subscribe [::row-is-new? doc-id (pop cell-path)])
     (rf/subscribe [::field-schemas/field-schema doc-id (path/path-without-indexes cell-path)])
     (rf/subscribe [::source-value/source-value doc-id cell-path])
     (rf/subscribe [::value/current-field-value doc-id cell-path])])
  (fn [[row-is-new? column-schema source-value current-value]]
    (let [readonly?    (boolean (:readonly column-schema))
          source-value (docs/post-process-value source-value)]
      ;; See `isEditedCell`
      (and (not readonly?)
           (not row-is-new?)
           (not= source-value current-value)
           ;; Not edited when currentValue not set and source = false. I.e. boolean not switched ON
           (not (and (nil? current-value)
                     (false? source-value)))
           ;; Not edited when current value switched off and no source value.
           (not (and (false? current-value)
                     (nil? source-value)))))))

(rf/reg-sub ::cell-css
  (fn cell-info-inputfn [[_ doc-id cell-path]]
    {:pre [(string? doc-id) (vector? cell-path)]}
    [(rf/subscribe [::edited-cell? doc-id cell-path])
     (rf/subscribe [::validation/field-data-validation-result-level doc-id cell-path])])
  (fn [[edited-cell? validation-level]]
    (->> [validation-level
          (when edited-cell? "edited-cell")]
         (keep identity)
         (into []))))

(rf/reg-sub ::cell-tooltip
  (fn cell-info-inputfn [[_ doc-id cell-path column-type]]
    {:pre [(string? doc-id) (vector? cell-path) (keyword? column-type)]}
    (cond-> {:edited-cell? (rf/subscribe [::edited-cell? doc-id cell-path])
             :source-value (rf/subscribe [::source-value/source-value doc-id cell-path])}
      (= :select column-type)
      (assoc :select-cell-options (rf/subscribe [::select-cell-options doc-id cell-path]))))
  (fn [{:keys [edited-cell? source-value select-cell-options]}]
    (when edited-cell?
      (let [source-value (docs/post-process-value source-value)]
        (str (common/loc :huoneistotTable.cellTooltipText)
             ": " (cond
                    ;; Don't show the raw value for selects, instead lookup the label
                    select-cell-options
                    (:label (util/find-by-key :value source-value select-cell-options))

                    ;; Don't show `true`/`false` for booleans, use labels
                    (boolean? source-value)
                    (if source-value (common/loc :yes) (common/loc :no))

                    (nil? source-value)
                    (common/loc "<Arvo puuttuu>")

                    :else
                    source-value))))))

(rf/reg-sub ::cell-edited-flags
  (fn cell-edited-flags-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::columns/column-non-synthetic doc-id (pop row-path)]))
  (fn [columns [_ doc-id row-path]]
    (->> columns
         (mapv (fn [{:keys [column-name column-type]}]
                 (let [cell-path (conj row-path column-name)]
                   (<sub [::edited-cell? doc-id cell-path column-type])))))))

(rf/reg-sub ::cells-with-sources
  (fn cells-with-sources-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::columns/column-non-synthetic doc-id (pop row-path)]))
  (fn [columns [_ doc-id row-path]]
    (->> columns
         (mapv (fn [{:keys [column-name]}]
                 (let [cell-path (conj row-path column-name)]
                   (<sub [::source-value/has-source? doc-id cell-path])))))))

(rf/reg-sub ::row-is-new?
  (fn row-is-new-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::cells-with-sources doc-id row-path]))
  (fn [has-source-flags]
    ;; A row is considered new if none of its cells have a source value
    (every? false? has-source-flags)))

(rf/reg-sub ::row-is-edited?
  (fn row-is-edited-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::cell-edited-flags doc-id row-path]))
  (fn [cell-flags]
    (boolean (some true? cell-flags))))

(rf/reg-sub ::row-is-to-be-removed?
  (fn row-is-to-be-removed-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::muutostapa/current-muutostapa doc-id row-path]))
  (fn [muutostapa]
    (= muutostapa/removed muutostapa)))

(rf/reg-sub ::row-differs-from-original?
  (fn row-differs-from-original-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    [(rf/subscribe [::row-is-new? doc-id row-path])
     (rf/subscribe [::row-is-edited? doc-id row-path])
     (rf/subscribe [::row-is-to-be-removed? doc-id row-path])])
  (fn [[new? edited? removed?]]
    (boolean (or new? edited? removed?))))
