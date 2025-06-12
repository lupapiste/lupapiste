(ns lupapalvelu.ui.docgen.field.huoneistot.headers
  "Render table headers"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.huoneistot.columns :as columns]
            [lupapalvelu.ui.docgen.field.huoneistot.common :as huoneistot-common]
            [lupapalvelu.ui.docgen.field.huoneistot.in-changes :as in-changes]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [sade.util :as util]))

(defn- column->header
  [{:keys [name->schema table-path doc-i18n-name]}
   {:keys [column-name column-type synthetic?]}]
  (let [{:keys [name required readonly] :as column-schema} (get name->schema column-name)]
    (cond
      ;; Column does not depend on the schema?
      synthetic?    {:name        column-name
                     :column-type column-type}
      ;; Column matches a child schema under the table?
      ;; If the table schema body does not contain the column, skip it.
      column-schema (let [path (conj table-path name)]
                      (cond-> {:name      (docgen-loc/loc-key doc-i18n-name path column-schema)}
                        (boolean required) (assoc :required? true)
                        (boolean readonly) (assoc :readonly? true))))))

(defn- render-header
  [{:keys [doc-id table-path disabled?]} {:keys [column-type name required? readonly?]}]
  (if (= :in-changes column-type)
    ;; The `in-changes` column is a bit different, it displays just a checkbox
    ^{:key name}
    [in-changes/all-checkbox (util/mmap doc-id table-path disabled?)]
    ;; The other columns display the name of the column
    ^{:key name}
    [:th
     [:span (common/loc name)]
     (when required?
       [:span {:class (when readonly? "readonly")} " *"])]))

(defn table-headers
  [{:keys [disabled? doc-id schema table-path]}]
  (let [doc-i18n-name (<sub [::docs/i18n-name doc-id])
        name->schema  (huoneistot-common/schema->body-map schema)]
    [:thead
     [:tr
      (->> (<sub [::columns/columns doc-id table-path])
           (keep (partial column->header (util/mmap name->schema table-path doc-i18n-name)))
           (map (partial render-header (util/mmap doc-id table-path disabled?)))
           doall)]]))
