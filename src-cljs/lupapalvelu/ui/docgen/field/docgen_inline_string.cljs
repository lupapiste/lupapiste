(ns lupapalvelu.ui.docgen.field.docgen-inline-string
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
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
  "See `docgen-inline-string-template.html`
   Quite similar to `lupapalvelu.ui.docgen.field.docgen-string`."
  [{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [validation-result-level    (<sub [::validation/field-data-validation-result-level doc-id path])
        read-only?                 (docgen-common/is-read-only? (util/mmap doc-id path schema))
        can-modify?                (and (not disabled?) (not read-only?))
        input-id                   (path/path->id path doc-id)
        {:keys [label aria-label]} (di/args->label args)
        flags                      (docgen-common/aria-flags validation-result-level)
        value                      (<sub [::value/current-field-value doc-id path])
        input-classes              (di/input-classes schema :inline-string validation-result-level)
        focus-ref                  (focus/use-focus doc-id path)]
    [(di/container schema)
     [di/docgen-label {:id                      input-id
                       :label                   label
                       :size                    (:size schema)
                       :validation-result-level validation-result-level}]
     (if read-only?
       [:span.lux {:class input-classes} value]
       [:<>
        [ui-input/text-input
         (cond-> (merge {:id          input-id
                         :class       (conj input-classes "lux")
                         :debounce-ms docgen-common/default-input-debounce-ms
                         :value       value}
                        (-> (di/schema->input-options schema)
                            (select-keys [:max-length :placeholder])))
           (seq flags) (merge flags)
           focus-ref   (assoc :ref focus-ref)
           aria-label  (assoc :aria-label (common/loc aria-label))
           read-only?  (assoc :read-only? true)
           disabled?   (assoc :disabled? true)
           can-modify? (assoc :on-change (docgen-common/make-on-change (util/mmap doc-id path))
                              :on-blur (docgen-common/make-on-blur
                                         (util/mmap {:backend-value (<sub [::committed/backend-submitted-value doc-id path])
                                                     :emit-data     (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                                    app-id doc-id path))))]
        [ping/ping {:doc-id doc-id :field-path path :mode :icon}]])]))
