(ns lupapalvelu.ui.docgen.field.docgen-localized-string
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [schema.core :as sc]))

(sc/defn render
  "See `docgen-localized-string-template`"
  [{:keys [doc-id schema path]} :- schemas/DocgenFieldArguments]
  (let [value         (<sub [::value/current-field-value doc-id path])
        input-classes (di/input-classes schema :localized-string)]
    [(di/container schema)
     [:label {:aria-hidden true} common/nbsp]
     [:span {:class input-classes}
      (common/loc (when value
                    (str (when-let [p (:locPrefix schema)]
                           (str p "."))
                         value)))]]))
