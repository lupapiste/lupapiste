(ns lupapalvelu.property-api
  (:require [sade.core :refer :all]
            [sade.property :as p]
            [sade.validators :as v]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.property-location :as plocation]))

(defquery municipality-by-property-id
  {:parameters [propertyId]
   :user-roles #{:anonymous}}
  [_]
  (if-let [municipality (p/municipality-id-by-property-id propertyId)]
    (ok :municipality municipality)
    (fail :municipalitysearch.notfound)))

(defn- get-areas! [property-ids]
  (let [; NLS WFS service does not support or-query, need to fetch areas one by one
        features (map plocation/property-location-info property-ids)]
    (->> features flatten (remove nil?))))

(defquery property-borders
  {:parameters [propertyIds]
   :description "Returns property borders as POLYGON WKT strings"
   :user-roles #{:authority} ; At the time of writing the only use case is the neighbour map
   :input-validators [(partial action/vector-parameter-of :propertyIds v/kiinteistotunnus?)]}
  [_]
  (let [areas (get-areas! (set propertyIds))]
    (if (seq areas)
      (ok :wkts (map #(select-keys % [:kiinttunnus :wkt]) areas))
      (fail :error.ktj-down))))
