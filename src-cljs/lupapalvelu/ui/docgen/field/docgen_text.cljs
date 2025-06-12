(ns lupapalvelu.ui.docgen.field.docgen-text
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
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
  "See `docgen-text-template.html`"
  [{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [validation-result-level    (<sub [::validation/field-data-validation-result-level doc-id path])
        read-only?                 (docgen-common/is-read-only? (util/mmap doc-id path schema))
        can-modify?                (and (not disabled?) (not read-only?))
        input-id                   (path/path->id path doc-id)
        {:keys [label aria-label]} (di/args->label args)
        flags                      (docgen-common/aria-flags validation-result-level)
        focus-ref                  (focus/use-focus doc-id path)
        source-value-modified?     (<sub [::source-value/source-value-modified? doc-id path])
        title                      (<sub [::source-value/title doc-id path])]
    [(di/container schema)
     [di/docgen-label {:id                      input-id
                       :label                   label
                       :size                    (:size schema)
                       :validation-result-level validation-result-level}]
     [ui-input/textarea
      (cond-> (merge {:id          input-id
                      :class (-> (di/input-classes schema :text validation-result-level)
                                 (conj "lux")
                                 (source-value/with-classes source-value-modified?))
                      :debounce-ms docgen-common/default-input-debounce-ms
                      :value       (<sub [::value/current-field-value doc-id path])}
                     (-> (di/schema->input-options schema)
                         (select-keys [:max-length :placeholder])))
        (seq flags) (merge flags)
        title       (assoc :title title)
        focus-ref   (assoc :ref focus-ref)
        aria-label  (assoc :aria-label (common/loc aria-label))
        read-only?  (assoc :read-only? true)
        disabled?   (assoc :disabled? true)
        can-modify? (assoc :on-change (docgen-common/make-on-change (util/mmap doc-id path))
                           :on-blur (docgen-common/make-on-blur
                                      (util/mmap {:backend-value (<sub [::committed/backend-submitted-value doc-id path])
                                                  :emit-data     (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                                 app-id doc-id path))))]
     [ping/ping {:doc-id doc-id :field-path path :mode :icon}]]))
