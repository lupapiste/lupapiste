(ns lupapalvelu.ui.application.low-carbon-assessment
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.document-building-reference :as document-building-reference]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.document-types :as doc-types]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.view :as docgen-view]
            [lupapalvelu.ui.dom :as ui-dom]
            [reagent.core :as r]))

;; Whenever kohteen-laji or rakennusluokka-vahahiilisyys changes we might need to show/hide
;; the Low-carbon assessment tab
(defmethod emit/do-emit :lowCarbonAssessmentCriteriaChanged
  [{:keys [app-id _]}]
  (fn [_ _]
    (when (common/feature? "building-product-catalog")
      {:fx [[:application/load app-id]]})))

(defn view []
  (r/with-let [_ (>evt [:auth/refresh])]
    (when (<sub [::app/application-id])
      [:div
       [:h1.mb-12 (common/loc :vahahiilisyyden-arviointi)]
       ;; Location-specific documents and reports
       (docgen-view/view {:doc-type :building-product-location-catalogs})
       (docgen-view/view {:doc-type :climate-reports-location})
       ;; For each building (operation document), show its building-product-catalog and climate-reports together
       (docgen-view/view {:doc-type :low-carbon-buildings})])))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
