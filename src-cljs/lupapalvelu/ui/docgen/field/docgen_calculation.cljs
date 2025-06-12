(ns lupapalvelu.ui.docgen.field.docgen-calculation
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.component.footer-sum :as footer-sum]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [schema.core :as sc]))

(defn calculate-amount
  [{:keys [column-data subschema]}]
  (->> (:columns subschema)
       (map column-data)
       (map footer-sum/str->value)
       footer-sum/safe-sum))

(sc/defn render
  "See `docgen-calculation-template.html`"
  [{:keys [doc-id schema path]} :- schemas/DocgenFieldArguments]
  (let [parent-path (pop path)
        result      (calculate-amount
                      {:column-data (fn [column]
                                      ;; The columns in our schema refer to our sibling fields,
                                      ;; so let's access them through our parent path
                                      (let [path (conj parent-path (keyword column))]
                                        (<sub [::committed/backend-submitted-value doc-id path])))
                       :subschema   schema})]
    (if (= ::footer-sum/invalid result)
      [:i.calculation-error.lupicon-warning]
      [:span.calculation-result
       (di/truncate result)])))
