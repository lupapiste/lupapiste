(ns lupapalvelu.ui.docgen.feature.climate-reports
  (:require [lupapalvelu.low-carbon-assessment.climate-report-utils :as climate-report-utils]
            [lupapalvelu.low-carbon-assessment.config :as low-carbon]
            [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.application :as application]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as documents]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::low-carbon-assessment-limits
  (fn limits-for-doc-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::documents/resolve-building-document doc-id])
     (rf/subscribe [::application/submitted-ts])])
  (fn [[{:keys [id]} submitted-ts]]
    (when-let [low-carbon-assessment-purpose (<sub [::value/current-field-value
                                                    id
                                                    low-carbon/building-doc-rakennusluokka-vahahiilisyys-path])]
      (climate-report-utils/low-carbon-assessment-limits (keyword low-carbon-assessment-purpose) submitted-ts))))

(rf/reg-sub ::carbon-footprint-over-limit?
  (fn carbon-footprint-over-limit-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::climate-report-data doc-id])
     (rf/subscribe [::low-carbon-assessment-limits doc-id])])
  (fn [[doc-data limits]]
    (let [limit (:limit limits)
          carbon-footprint (climate-report-utils/carbon-footprint doc-data)]
      (boolean (and carbon-footprint
                    limit
                    (> carbon-footprint limit))))))

(rf/reg-sub ::climate-report-data
  (fn climate-report-data-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::documents/document-data doc-id])
     (rf/subscribe [::committed/committed-changes doc-id])])
  (fn [[doc-data committed-changes]]
    ;; Merge committed changes into the document data structure
    (reduce-kv (fn [acc path value]
                 (assoc-in acc (conj path :value) value))
               doc-data
               committed-changes)))

(rf/reg-sub ::location-climate-report?
  (fn location-climate-report-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::documents/building-document doc-id]))
  (fn [doc]
    (= (-> doc :schema-info :type) "location")))

(rf/reg-sub ::net-heated-area
  (fn net-heated-area-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::location-climate-report? doc-id])
     (rf/subscribe [::net-heated-area-total-sum])
     (rf/subscribe [::value/current-field-value doc-id low-carbon/climate-report-lammitetty-nettoala-path])])
  (fn [[location-doc? total-net-heated-area net-heated-area]]
    (-> (if location-doc?
          total-net-heated-area
          net-heated-area)
        util/->double)))

;; This gets the total net heated area for all building documents
(rf/reg-sub ::net-heated-area-total-sum
  :<- [::documents/climate-reports]
  (fn [reports]
    (climate-report-utils/heated-net-area-total-sum reports)))
