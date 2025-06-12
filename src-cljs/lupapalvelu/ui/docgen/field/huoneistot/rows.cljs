(ns lupapalvelu.ui.docgen.field.huoneistot.rows
  "Render table rows"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.huoneistot.cells :as cells]
            [lupapalvelu.ui.docgen.field.huoneistot.cells-common :as cells-common]
            [lupapalvelu.ui.docgen.field.huoneistot.columns :as columns]
            [lupapalvelu.ui.docgen.field.huoneistot.common :as huoneistot-common]
            [lupapalvelu.ui.docgen.field.huoneistot.highlight :as highlight]
            [lupapalvelu.ui.docgen.field.huoneistot.in-changes :as in-changes]
            [lupapalvelu.ui.docgen.field.huoneistot.muutostapa :as muutostapa]
            [lupapalvelu.ui.docgen.field.huoneistot.warnings :as warnings]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::row-visible?
  (fn row-differs-from-original-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    [(rf/subscribe [::highlight/show-only-edited? doc-id])
     (rf/subscribe [::cells-common/row-differs-from-original? doc-id row-path])])
  (fn [[show-only-edited? differs-from-original?]]
    (not (and show-only-edited?
              (not differs-from-original?)))))

(rf/reg-sub ::row-css
  (fn [[_ doc-id row-path allow-highlight-changes?]]
    [(rf/subscribe [::muutostapa/current-muutostapa doc-id row-path])
     (rf/subscribe [::in-changes/in-changes? doc-id row-path])
     (rf/subscribe [::highlight/highlight-changes? doc-id allow-highlight-changes?])])
  (fn [[muutostapa in-changes? highlight? ]]
    (when (and in-changes? highlight? muutostapa)
      [(condp = muutostapa
         muutostapa/added   "new-row"
         muutostapa/edited  "edited-row"
         muutostapa/removed "removed-row")])))

(defn- row
  [{:keys [disabled? doc-id removable? row-id row-path schema]}]
  (let [column-schemas           (huoneistot-common/schema->body-map schema)
        allow-highlight-changes? (:allow-highlight-changes? schema)]
    (when (<sub [::row-visible? doc-id row-path])
      [:<>
       [warnings/row-warnings (util/mmap doc-id row-path schema)]
       [:tr {:class (<sub [::row-css doc-id row-path allow-highlight-changes?])}
        (->> (<sub [::columns/columns doc-id (pop row-path)])
             (map (fn [{:keys [column-name column-type synthetic?]}]
                    (let [cell-path     (conj row-path column-name)
                          column-schema (when-not synthetic?
                                          (get column-schemas column-name))]
                      ^{:key (path/path->str cell-path)}
                      [cells/table-cell (util/mmap cell-path column-name column-schema column-type
                                                   disabled? doc-id removable? row-id row-path
                                                   schema)])))
             doall)]])))

(defn table-rows
  [{:keys [disabled? doc-id removable? schema table-path]}]
  [:tbody
   (->> (<sub [::docs/field-child-keys doc-id table-path])
        (map (fn [row-id]
               (let [row-path (conj table-path row-id)]
                 ^{:key (path/path->str row-path)}
                 [row (util/mmap disabled? doc-id removable? row-id row-path schema)])))
        doall)])
