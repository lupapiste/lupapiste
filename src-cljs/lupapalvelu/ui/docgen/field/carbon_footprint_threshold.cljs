(ns lupapalvelu.ui.docgen.field.carbon-footprint-threshold
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.climate-reports :as climate-reports]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [schema.core :as sc]))

(sc/defn render
  [{:keys [doc-id schema render-fn] :as args} :- schemas/DocgenFieldArguments]
  (when (and (<sub [::climate-reports/carbon-footprint-over-limit? doc-id])
             (not (<sub [::climate-reports/location-climate-report? doc-id])))
    [:div.flex.flex-col
     [render-fn (:body schema) (dissoc args :schema)]]))
