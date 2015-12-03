(ns lupapalvelu.property-location
  (:require [monger.operators :refer :all]
             [lupapalvelu.mongo :as mongo]
             [lupapalvelu.wfs :as wfs]))

(defn property-location-info [property-id]
  (when-let [features (wfs/location-info-by-property-id property-id)]
    (map wfs/feature-to-location features)))
