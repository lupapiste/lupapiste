(ns lupapalvelu.ui.docgen.field.input
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as docgen-schemas]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(defschema RenderInputOptions
  (st/merge
    {:app-id     sc/Str
     :doc-id     sc/Str
     :input-id   sc/Str
     :path       docgen-schemas/Path
     :input-type (sc/enum :checkbox :email :password :radio :search :tel :text :url)
     :schema     (sc/pred map?)}
    (st/optional-keys
      {:max-length              sc/Int
       :aria-label              sc/Str
       :disabled?               sc/Bool
       :override-auth-source    docgen-schemas/OverrideAuthSource
       :radio-label             sc/Str
       :validation-result-level docgen-schemas/ValidationResultLevel})))

(sc/defn args->input-args
  [args :- docgen-schemas/DocgenFieldArguments]
  (select-keys args [:app-id :disabled? :doc-id :override-auth-source :path :schema]))

(sc/defn render-input
  "See `makeInput` in `docmodel.js`"
  [{:keys [app-id aria-label disabled? doc-id input-id
           input-type max-length path radio-label
           schema validation-result-level]} :- RenderInputOptions]
  (let [{:keys [placeholder size]} schema
        size-class                 (docgen-common/sizes size)
        current-value              (<sub [::value/current-field-value doc-id path])
        read-only?                 (docgen-common/is-read-only? (util/mmap doc-id path schema))
        can-modify?                (and (not read-only?) (not disabled?))
        checkbox?                  (= :checkbox input-type)
        radio?                     (= :radio input-type)
        span?                      (and (not checkbox?)
                                        (not radio?)
                                        read-only?)
        placeholder?               (and placeholder
                                        (#{:text
                                           :email
                                           :search
                                           :url
                                           :tel
                                           :password} input-type)
                                        (not disabled?))
        flags                      (docgen-common/aria-flags
                                     validation-result-level
                                     {:error-message (path/path->warning-message-id path doc-id)})
        source-value-modified?     (<sub [::source-value/source-value-modified? doc-id path])
        title                      (when-not checkbox?
                                     ;; Checkboxes already handle the title separately
                                     (<sub [::source-value/title doc-id path]))
        common-options             (cond-> {:id    input-id
                                            :class (-> ["form-input" "lux" input-type size-class validation-result-level]
                                                       (source-value/with-classes source-value-modified?))}
                                     ;; `makeInput` sets `readOnly` according to `isInputReadOnly`
                                     read-only?        (assoc :read-only? true)
                                     ;; `buildSection` sets `readOnly` for inputs using jQuery if disabled
                                     disabled?         (assoc :read-only? true)
                                     ;; `buildSection` also sets `disabled` for checkboxes & radio buttons if disabled
                                     (and disabled?
                                          (or checkbox?
                                              radio?)) (assoc :disabled? true)
                                     placeholder?      (assoc :placeholder (common/loc placeholder))
                                     (seq flags)       (merge flags)
                                     aria-label        (assoc :aria-label aria-label)
                                     title             (assoc :title title))
        emit-data                  (emit/schema->emit-data (util/mmap app-id doc-id schema))
        focus-ref                  (focus/use-focus doc-id path)]
    (cond
      span?     [:span
                 {:id    input-id
                  :class ["form-input" "lux" input-type size-class]}
                 current-value]
      checkbox? [ui-input/checkbox
                 (cond-> common-options
                   true              (assoc :checked? current-value)
                   (not can-modify?) (assoc :disabled? true)
                   can-modify?       (assoc :on-change (docgen-common/make-on-change-immediate
                                                         (util/mmap app-id doc-id path emit-data))))]
      radio?    [ui-input/radio-button
                 (cond-> common-options
                   true        (assoc :value    radio-label
                                      :checked? (= radio-label current-value))
                   can-modify? (assoc :on-change (comp
                                                   (docgen-common/make-on-change-immediate
                                                     (util/mmap app-id doc-id path emit-data))
                                                   ;; when the radio button label is clicked,
                                                   ;; submit the name of the button choice
                                                   (constantly radio-label))))]
      :else     [ui-input/text-input
                 (cond-> common-options
                   true        (merge {:input-type  input-type
                                       :debounce-ms docgen-common/default-input-debounce-ms
                                       :value       current-value
                                       :max-length  (or max-length 255)})
                   focus-ref   (assoc :ref focus-ref)
                   can-modify? (assoc :on-change (docgen-common/make-on-change (util/mmap doc-id path))
                                      :on-blur (docgen-common/make-on-blur
                                                 (util/mmap {:backend-value (<sub [::committed/backend-submitted-value doc-id path])}
                                                            app-id doc-id path emit-data))))])))
