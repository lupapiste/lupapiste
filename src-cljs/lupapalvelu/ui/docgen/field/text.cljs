(ns lupapalvelu.ui.docgen.field.text
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
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defn render
  "See `buildText` in `docmodel.js`"
  [{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [rows cols
                label placeholder]} schema
        validation-result-level     (<sub [::validation/field-data-validation-result-level doc-id path])
        input-id                    (path/path->id path doc-id)
        flags                       (docgen-common/aria-flags validation-result-level)
        can-modify?                 (and (not disabled?)
                                         (not (docgen-common/is-read-only? (util/mmap doc-id path schema))))
        source-value-modified?      (<sub [::source-value/source-value-modified? doc-id path])
        title                       (<sub [::source-value/title doc-id path])
        focus-ref                   (focus/use-focus doc-id path)]
    [docgen-common/entry-span args
     (when label
       [docgen-common/make-label
        {:doc-id                  doc-id
         :input-id                input-id
         :schema                  schema
         :parent-type             :text
         :parent-path             path
         :validation-result-level validation-result-level}])
     [ui-input/textarea
      (cond-> {:id          input-id
               :rows        (int (or rows "10"))
               :cols        (int (or cols "40"))
               :max-length  (docgen-common/max-length schema)
               :class       (-> ["form-input" "lux" "textarea" validation-result-level]
                                (source-value/with-classes source-value-modified?))
               :debounce-ms docgen-common/default-input-debounce-ms
               :value       (<sub [::value/current-field-value doc-id path])
               :placeholder (when (and placeholder (not disabled?))
                              (common/loc placeholder))}
        title             (assoc :title title)
        focus-ref         (assoc :ref focus-ref)
        (seq flags)       (merge flags)
        (not can-modify?) (assoc :read-only? true)
        can-modify?       (assoc :on-change (docgen-common/make-on-change (util/mmap doc-id path))
                                 :on-blur (docgen-common/make-on-blur
                                            (util/mmap {:backend-value (<sub [::committed/backend-submitted-value doc-id path])
                                                        :emit-data     (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                                       app-id doc-id path))))]
     [ping/ping {:doc-id doc-id :field-path path}]]))
