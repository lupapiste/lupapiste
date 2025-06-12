(ns lupapalvelu.ui.docgen.field.docgen-review-officer
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- officer->option
  [{:keys [name code]}]
  {:value code :label name})

(defn- officer-has-names?
  "Returns true if officer has both firstName and lastName set."
  [{:keys [firstName lastName]}]
  (and (not (util/emptyish? firstName))
       (not (util/emptyish? lastName))))

(defn- update-ryhti-officer
  [app-id doc-id pitaja-path etunimi-path sukunimi-path schema officer]
  (let [etunimi       (or (:firstName officer) "")
        sukunimi      (or (:lastName officer) "")
        officer       (or officer "")
        field-paths   [pitaja-path etunimi-path sukunimi-path]
        values        [officer etunimi sukunimi]
        emit-data     (emit/schema->emit-data (util/mmap app-id doc-id schema))
        callback-args {:app-id      app-id
                       :doc-id      doc-id
                       :field-paths field-paths
                       :values      values
                       :emit-data   emit-data}]
    (>evt [::docgen-common/change-immediate
           (assoc callback-args
                  :on-success [::docgen-common/on-save-success callback-args]
                  :on-error [::docgen-common/on-save-error callback-args])])))

(defn- pitaja-field
  "Renders a text input for pitaja details (etunimi or sukunimi).
   Field param should be :etunimi or :sukunimi."
  [{:keys [app-id doc-id path-base field read-only?]}]
  (let [path                    (conj path-base :pitajaDetails field)
        validation-result-level (<sub [::validation/field-data-validation-result-level doc-id path])
        input-classes           (di/input-classes {:size "s"} :string validation-result-level)
        aria-flags              (docgen-common/aria-flags validation-result-level)
        input-id                (path/path->id path doc-id)
        value                   (<sub [::value/current-field-value doc-id path])
        text-value              (or value "")
        label                   (util/kw-path :task-katselmus.katselmus.pitajaDetails field)]
    [:div.form-entry
     [di/docgen-label {:id                      input-id
                       :label                   label
                       :required?               true
                       :validation-result-level validation-result-level}]
     [ui-input/text-input
      (cond-> {:id          input-id
               :class       (conj input-classes "lux")
               :value       text-value
               :disabled?   (boolean read-only?)
               :debounce-ms docgen-common/default-input-debounce-ms
               :on-change   (docgen-common/make-on-change (util/mmap doc-id path))
               :on-blur     (docgen-common/make-on-blur
                             (util/mmap {:emit-data     nil
                                         :backend-value (<sub [::committed/backend-submitted-value doc-id path])}
                                        app-id doc-id path))}
        (seq aria-flags) (merge aria-flags))]
     [ping/ping {:doc-id     doc-id
                 :field-path path
                 :mode       :icon}]]))

(defn- render-ryhti
  [{:keys [app-id doc-id schema path disabled?] :as args}]
  (let [path-base                  (pop path)
        etunimi-path               (conj path-base :pitajaDetails :etunimi)
        sukunimi-path              (conj path-base :pitajaDetails :sukunimi)
        {:keys [enabled officers]} (<sub [::app/review-officer-data])
        validation-result-level    (<sub [::validation/field-data-validation-result-level doc-id path])
        read-only?                 (or disabled?
                                       (docgen-common/is-read-only? (util/mmap doc-id path schema)))
        list-mode?                 (and enabled (not read-only?))
        doc-i18n-name              (<sub [::docs/i18n-name doc-id])
        i18npath                   (docgen-loc/loc-key doc-i18n-name path schema)

        filtered-officers          (if list-mode?
                                     (filter officer-has-names? officers)
                                     [])
        select-options             (if list-mode?
                                     (->> filtered-officers
                                          (map officer->option)
                                          (into [(docgen-common/empty-option i18npath)]))
                                     [])

        input-classes              (di/input-classes schema :string validation-result-level)
        pitaja-value               (<sub [::value/current-field-value doc-id path])
        pitaja-input-id            (path/path->id path doc-id)
        selected-value             (or (:code pitaja-value) pitaja-value "")
        {:keys [label]}            (di/args->label args)
        inputs-read-only?          (or read-only?
                                       (and list-mode? (seq filtered-officers)))]

    [:div
     (when list-mode?
       [:div.form-entry
        [di/docgen-label {:id                      pitaja-input-id
                          :label                   label
                          :validation-result-level validation-result-level}]
        [ui-input/select
         {:id        pitaja-input-id
          :class     (conj input-classes "dropdown")
          :disabled? read-only?
          :value     selected-value
          :on-change (fn [val]
                       (let [officer (some #(when (= (:code %) val) %) filtered-officers)]
                         (update-ryhti-officer app-id doc-id path etunimi-path sukunimi-path schema officer)))}
         select-options]
        (when (empty? filtered-officers)
          [:p.group-help-text
           (common/loc :task-katselmus.katselmus.pitaja.no-officers-with-names)])
        [ping/ping {:doc-id     doc-id
                    :field-path path
                    :mode       :icon}]])
     [:<>
      [pitaja-field {:app-id     app-id
                     :doc-id     doc-id
                     :path-base  path-base
                     :field      :etunimi
                     :read-only? inputs-read-only?}]
      [pitaja-field {:app-id     app-id
                     :doc-id     doc-id
                     :path-base  path-base
                     :field      :sukunimi
                     :read-only? inputs-read-only?}]]]))

(defn- render-legacy
  "Review officer field: dynamic select/text/disabled variants.
   If list mode, shows select. Else shows text input.
   Similar styling and ARIA behavior as other docgen inputs."
  [{:keys [app-id doc-id schema path disabled?] :as args}]
  (let [{:keys [size] :as full-schema} schema
        {:keys [enabled officers]}     (<sub [::app/review-officer-data])
        validation-result-level        (<sub [::validation/field-data-validation-result-level doc-id path])
        read-only?                     (or disabled?
                                           (docgen-common/is-read-only? (util/mmap doc-id path schema)))
        list-mode?                     (and enabled (not read-only?))
        input-id                       (path/path->id path doc-id)
        {:keys [label aria-label]}     (di/args->label args)
        doc-i18n-name                  (<sub [::docs/i18n-name doc-id])
        i18npath                       (docgen-loc/loc-key doc-i18n-name path schema)
        aria-flags                     (docgen-common/aria-flags validation-result-level)
        value                          (<sub [::value/current-field-value doc-id path])
        select-options                 (->> officers
                                            (map officer->option)
                                            (into [(docgen-common/empty-option i18npath)]))
        input-classes                  (di/input-classes full-schema :string validation-result-level)
        selected-value                 (or (:code value) value "")]
    [(di/container schema)
     [di/docgen-label {:id                      input-id
                       :label                   label
                       :size                    size
                       :validation-result-level validation-result-level}]
     (if list-mode?
       [ui-input/select
        (cond-> {:id input-id
                 :class (conj input-classes "dropdown")
                 :disabled? read-only?
                 :value selected-value
                 :on-change (fn [val]
                              (let [on-change-fn (docgen-common/make-on-change-immediate
                                                   (util/mmap {:emit-data
                                                               (emit/schema->emit-data
                                                                 (util/mmap app-id doc-id schema))}
                                                              app-id doc-id path))]
                                (on-change-fn
                                  (some #(when (= (:code %) val)
                                           %)
                                        officers))))}
          (seq aria-flags) (merge aria-flags)
          aria-label       (assoc :aria-label (common/loc aria-label)))
        select-options]
       (let [text-value (cond
                          (:code value) (-> (util/find-by-key :value (:code value) select-options)
                                            :label)
                          value         value
                          :else         "")]
         [ui-input/text-input
          (cond-> {:id          input-id
                   :class       (conj input-classes "lux")
                   :value       text-value
                   :disabled?   read-only?
                   :debounce-ms docgen-common/default-input-debounce-ms
                   :on-change   (docgen-common/make-on-change (util/mmap doc-id path))
                   :on-blur     (docgen-common/make-on-blur
                                  (util/mmap {:emit-data (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                             app-id doc-id path))}
            (seq aria-flags) (merge aria-flags)
            aria-label       (assoc :aria-label (common/loc aria-label)))]))
     [ping/ping {:doc-id     doc-id
                 :field-path path
                 :mode       :icon}]]))

(sc/defn render
  "Review officer field: dynamic select/text/disabled variants.
   If Ryhti 2026 flag is enabled, uses logic with separate name fields."
  [{:keys [schema] :as args} :- schemas/DocgenFieldArguments]
  (let [ryhti-data-2026? (<sub [::app/ryhti-data-2026])]
    (if ryhti-data-2026?
      (render-ryhti args)
      (render-legacy args))))
