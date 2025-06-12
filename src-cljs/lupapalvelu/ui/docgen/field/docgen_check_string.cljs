(ns lupapalvelu.ui.docgen.field.docgen-check-string
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [schema.core :as sc]))

(sc/defn render
  "See `docgen-check-string-template`"
  [{:keys [doc-id schema path]} :- schemas/DocgenFieldArguments]
  (let [value         (<sub [::value/current-field-value doc-id path])
        doc-i18n-name (<sub [::docs/i18n-name doc-id])
        ;; `loc-key` doesn't quite handle this case, as the `i18nkey`
        ;; field may overwrite the `value` so we must do it manually here.
        loc-key       (str (docgen-loc/loc-key doc-i18n-name path schema)
                           "." (boolean value))
        input-classes (di/input-classes schema :check-string)]
    [(di/container schema)
     [:label {:aria-hidden true} common/nbsp]
     [:div {:class input-classes}
      (common/loc loc-key)]]))
