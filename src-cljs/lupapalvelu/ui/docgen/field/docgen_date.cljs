(ns lupapalvelu.ui.docgen.field.docgen-date
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defn render
  "See `docgen-date-template.html`"
  ([args] (render args nil))
  ([{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments {:keys [format]}]
   (let [validation-result-level (<sub [::validation/field-data-validation-result-level doc-id path])
         read-only?              (docgen-common/is-read-only? (util/mmap doc-id path schema))
         input-id                (path/path->id path doc-id)
         {:keys [label]}         (di/args->label args)
         flags                   (docgen-common/aria-flags validation-result-level)
         input-classes           (di/input-classes schema :date validation-result-level)
         focus-ref               (focus/use-focus doc-id path)]
     [(di/container schema)
      [di/docgen-label {:id                      input-id
                        :label                   label
                        :size                    (:size schema)
                        :validation-result-level validation-result-level}]
      [day-edit/day-edit
       (<sub [::value/current-field-value doc-id path])
       {:id        input-id
        :class     (into ["day-picker-input"] input-classes)
        :disabled? (or disabled? read-only?)
        :required? (:required? flags)
        :invalid?  (:aria-invalid? flags)
        :format    (or format :string)
        :callback  (docgen-common/make-on-change-immediate
                     (util/mmap {:emit-data (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                app-id doc-id path))
        :ref       focus-ref}]
      [ping/ping {:doc-id doc-id :field-path path :mode :icon}]])))
