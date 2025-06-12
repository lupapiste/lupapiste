(ns lupapalvelu.ui.docgen.field.docgen-checkbox-wrapper
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
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
  "See `docgen-checkbox-template.html`"
  [{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [validation-result-level    (<sub [::validation/field-data-validation-result-level doc-id path])
        read-only?                 (docgen-common/is-read-only? (util/mmap doc-id path schema))
        can-modify?                (and (not disabled?) (not read-only?))
        input-id                   (path/path->id path doc-id)
        {:keys [label aria-label]} (di/args->label args)
        value                      (<sub [::value/current-field-value doc-id path])
        input-classes              (di/input-classes schema :checkbox-wrapper validation-result-level)
        source-value               (<sub [::source-value/source-value doc-id path])
        title                      (<sub [::source-value/title doc-id path (if source-value
                                                                             (common/loc :selected)
                                                                             (common/loc :notSelected))])]
    [(di/container schema)
     [di/docgen-label-empty {:label label
                             :size  (:size schema)}]
     [:div.docgen-checkbox-wrapper
      {:class (cond-> input-classes
                (not can-modify?) (conj "wrapper--disabled"))}
      [ui-input/checkbox
       (cond-> {:id        input-id
                :checked?  value
                :disabled? (not can-modify?)}
         can-modify? (assoc :on-change (docgen-common/make-on-change-immediate
                                         (util/mmap {:emit-data (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                                    app-id doc-id path)))
         aria-label  (assoc :aria-label(common/loc aria-label)))]
      [:label.docgen-checkbox-label
       ;; TODO: How can this be colored?
       (util/assoc-when {:for         input-id
                         :aria-hidden true}
                        :title       title)
       (common/loc label)]]
     [ping/ping {:doc-id doc-id :field-path path :mode :icon}]]))
