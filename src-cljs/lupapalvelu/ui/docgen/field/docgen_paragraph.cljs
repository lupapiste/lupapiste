(ns lupapalvelu.ui.docgen.field.docgen-paragraph
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [schema.core :as sc]))

;; This whole component could be done way differently as 
;; it's not an interactable component, but just a place to show the maarays text. 
;; This is just a quick implementation that matches the knockout version

(sc/defn render
  "See `docgen-paragraph-template.html`"
  [{:keys [doc-id schema path] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [label]} (di/args->label args)
        value (<sub [::value/current-field-value doc-id path])
        input-classes (di/input-classes schema :paragraph nil)
        label-id (path/path->id path doc-id)]
    [:div 
     [:p {:id label-id
          :class (into ["lux" "form-label" (docgen-common/sizes (:size schema))])}
      (common/loc label)]
     ; this is a span instead of a :p because form-grid 
     ; removes paddings with !important
     [:span {:class input-classes
             :aria-labelledby label-id}
      value]]))
