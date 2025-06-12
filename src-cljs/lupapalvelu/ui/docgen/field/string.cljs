(ns lupapalvelu.ui.docgen.field.string
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.input :as input]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defn render
  "See `buildString` in `docmodel.js`"
  [{:keys [doc-id schema path] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [label max-len
                subtype unit]}  schema
        validation-result-level (<sub [::validation/field-data-validation-result-level doc-id path])
        supported               #{:email :time}
        subtype                 (keyword subtype)
        input-type              (if (supported subtype)
                                  subtype :text)
        input-id                (path/path->id path doc-id)
        input-args              (util/assoc-when (input/args->input-args args)
                                                 :input-type input-type
                                                 :input-id input-id
                                                 :validation-result-level validation-result-level
                                                 :max-length max-len)]
    [docgen-common/entry-span args
     (when label
       [docgen-common/make-label {:doc-id                  doc-id
                                  :input-id                input-id
                                  :schema                  schema
                                  :parent-type             :text
                                  :parent-path             path
                                  :validation-result-level validation-result-level}])
     ;; `docmodel.js` also supported a `maaraala-tunnus` subtype. However, no actual
     ;; schema seems to use it (anymore) so we won't support it either.
     (cond
       unit  [:span.form-input-and-unit
              [:f> input/render-input input-args]
              [:span.form-string-unit
               (common/loc (util/kw-path :unit unit))]
              [ping/ping {:doc-id doc-id :field-path path}]]
       :else [:<>
              [:f> input/render-input input-args]
              [ping/ping {:doc-id doc-id :field-path path}]])]))
