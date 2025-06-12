(ns lupapalvelu.ui.docgen.component.building-identifier
  "Modify the building identifier for ARK permits.
  Quite similar to the `operation-editor` component.
  See `building-identifier-[template.html, model.js]`"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.component.primary-operation :as primary-operation]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [sade.util :as util]))

(defn- editor
  [{:keys [id doc-id path override-auth-source]}]
  (let [app-id    (<sub [::app/application-id])
        disabled? (<sub [::docs/doc-disabled? doc-id override-auth-source])]
    [ui-input/text-input
     (cond-> {:id          id
              :class       ["lux" "align--middle"]
              :debounce-ms docgen-common/default-input-debounce-ms
              :value       (<sub [::value/current-field-value doc-id path])}
       disabled?       (assoc :disabled? true)
       ;; The legacy implementation persisted the value (almost) immediately
       ;; so let's do that here too.
       (not disabled?) (assoc :on-change (docgen-common/make-on-change-immediate
                                           (util/mmap app-id doc-id path))))]))

(defn- label
  [{:keys [id]}]
  [:label.lux.lux--inline.gap--r1-half
   {:for   id}
   (common/loc :document.building.identifier)])

(defn render
  "Render the building identifier editor.
  Just a label and an input field for the building id,
  and a button for marking the operation as the primary operation."
  [{:keys [doc-id override-auth-source]}]
  (let [id   (str doc-id "-building-identifier-input")
        path [:valtakunnallinenNumero]]
    [:div
     [label (util/mmap id)]
     " "
     [editor (util/mmap id doc-id path)]
     ;; With ARK projects the `primary-operation` button is shown here and
     ;; not within the `operation-editor` component.
     [primary-operation/set-as-primary-button (util/mmap doc-id override-auth-source)]
     [ping/ping {:doc-id     doc-id
                 :field-path path}]]))
