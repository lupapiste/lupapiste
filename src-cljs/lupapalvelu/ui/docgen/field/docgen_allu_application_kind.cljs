(ns lupapalvelu.ui.docgen.field.docgen-allu-application-kind
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.allu-application-kind :as allu-application-kind]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-field-common]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [schema.core :as sc]))

(def ^:private empty-option
  {:value ""
   :label (common/loc :selectone)})

(sc/defn render
  "Render allu-application-kind field."
  [{:keys [doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [application-kinds       (<sub [::allu-application-kind/allu-application-kind-options
                                       (:applicationType schema)])
        choices                 (into [empty-option]
                                      (mapv (fn [application-kind]
                                              {:label (common/loc (str "allu.application-kind." application-kind))
                                               :value application-kind})
                                            application-kinds))
        validation-result-level (<sub [::validation/field-data-validation-result-level doc-id path])
        input-id                (path/path->id path doc-id)
        current-value           (<sub [::value/current-field-value doc-id path])]
    [:<>
     [:label {:class ["lux" "form-label"]
              :for   input-id}
      (common/loc (:i18nkey schema))]
     (if disabled?
       [ui-input/text-input
        {:id        input-id
         :value     (common/loc (str "allu.application-kind." current-value))
         :disabled? disabled?}]
       [ui-input/select
        (merge {:id input-id
                :class ["dropdown"]
                :value (or current-value "")
                :on-change (docgen-field-common/make-on-change-immediate args)}
               (docgen-field-common/aria-flags validation-result-level))
        choices])
     [ping/ping {:doc-id doc-id :field-path path :mode :icon}]]))
