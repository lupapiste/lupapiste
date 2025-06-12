(ns lupapalvelu.ui.docgen.field.date
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defn render
  "See `buildDate` in `docmodel.js`"
  ([args] (render args nil))
  ([{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments {:keys [format]}]
   (let [{:keys [label]}         schema
         validation-result-level (<sub [::validation/field-data-validation-result-level doc-id path])
         input-id                (path/path->id path doc-id)
         flags                   (docgen-common/aria-flags validation-result-level)
         read-only?              (docgen-common/is-read-only? (util/mmap doc-id path schema))
         source-value-modified?  (<sub [::source-value/source-value-modified? doc-id path])
         title                   (<sub [::source-value/title doc-id path])
         focus-ref               (focus/use-focus doc-id path)]
     [docgen-common/entry-span args
      (when label
        [docgen-common/make-label
         {:doc-id                  doc-id
          :input-id                input-id
          :schema                  schema
          :parent-type             :date
          :parent-path             path
          :validation-result-level validation-result-level
          :title                   title}])
      [:div {:class (-> [:pb-4]
                        (source-value/with-classes source-value-modified?))}
       [day-edit/day-edit
        (<sub [::value/current-field-value doc-id path])
        {:id        input-id
         :disabled? (or disabled? read-only?)
         :required? (:required? flags)
         :invalid?  (:aria-invalid? flags)
         :format    (or format :string)
         :callback  (docgen-common/make-on-change-immediate
                      (util/mmap {:emit-data (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                 app-id doc-id path))
         :title     title
         :ref       focus-ref}]]
      [ping/ping {:doc-id doc-id :field-path path}]])))
