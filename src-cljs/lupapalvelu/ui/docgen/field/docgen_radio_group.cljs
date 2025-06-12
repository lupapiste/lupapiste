(ns lupapalvelu.ui.docgen.field.docgen-radio-group
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.field.input :as input]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]))

(defn render-radio-button [{:keys [app-id doc-id input-id path schema]} {:keys [name i18nkey]}]
  (let [validation-result-level (<sub [::validation/field-data-validation-result-level doc-id path])
        input-classes (di/input-classes schema :radiogroup validation-result-level)
        input-id-option (str input-id "-" name)
        translation-key (or (util/kw-path i18nkey)
                            (util/kw-path (:i18nkey schema) name))
        label (common/loc translation-key)
        input-attrs {:input-type :radio
                     :radio-label name
                     :app-id     app-id
                     :doc-id     doc-id
                     :input-id   input-id-option
                     :path       path
                     :schema     schema
                     :aria-label label}]
    [:div {:class (cons "radio-wrapper" input-classes)}
     [:f> input/render-input input-attrs]
     [:label.radio-label {:aria-hidden true
                          :for input-id-option}
      label]]))

(sc/defn render
  "Render radio group."
  [{:keys [doc-id schema path] :as args} :- schemas/DocgenFieldArguments]
  (let [input-id             (path/path->id path doc-id)
        aria-labelledby      (:labelled-by schema)
        aria-translation-key (:aria-label schema)
        radio-group-attrs    (cond-> {:role "radiogroup"}
                               aria-labelledby      (assoc :aria-labelledby aria-labelledby)
                               aria-translation-key (assoc :aria-text (common/loc aria-translation-key)))]
    [(di/container schema)
     [di/docgen-label-empty {:size (:size schema)}]
     [:div radio-group-attrs
      (->> (:body schema)
           (map (fn [option]
                  (let [button-params (assoc args
                                             :input-id input-id)]
                    ^{:key (path/path->str (conj (:path args) (:name option)))}
                    [render-radio-button button-params option])))
           doall)]]))
