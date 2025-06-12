(ns lupapalvelu.ui.docgen.field.checkbox
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.input :as input]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defn render
  "See `buildCheckbox` in `docmodel.js`"
  [{:keys [doc-id schema path] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [label classPrefix]} schema
        validation-result-level     (<sub [::validation/field-data-validation-result-level doc-id path])
        source-value                (<sub [::source-value/source-value doc-id path])
        ;; Handle the title here separately so that we can attach it to the label
        ;; `input/render-input` will handle the classes
        title                       (<sub [::source-value/title doc-id path (if source-value
                                                                              (common/loc :selected)
                                                                              (common/loc :notSelected))])
        input-id                    (path/path->id path doc-id)
        input-args                  (-> args
                                        input/args->input-args
                                        (util/assoc-when :input-type :checkbox
                                                         :input-id input-id
                                                         :validation-result-level validation-result-level))]
    [docgen-common/entry-span args
     [docgen-common/make-wrapper (cond-> {:class-prefix classPrefix}
                                   label (assoc :label-opts {:doc-id                  doc-id
                                                             :input-id                input-id
                                                             :schema                  schema
                                                             :parent-type             :checkbox
                                                             :parent-path             path
                                                             :validation-result-level validation-result-level
                                                             :title                   title}))
      [ping/ping {:doc-id doc-id :field-path path}]
      [:f> input/render-input input-args]]]))
