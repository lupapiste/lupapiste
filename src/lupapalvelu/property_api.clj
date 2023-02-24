(ns lupapalvelu.property-api
  (:require [sade.core :refer :all]
            [lupapalvelu.property :as prop]
            [sade.validators :as v]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.property-location :as plocation]))

(defquery municipality-for-property
  {:parameters [propertyId]
   :description "Returns municipality by property id from KTJKii"
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/property-id-parameters [:propertyId])]}
  [_]
  (ok :municipality (prop/municipality-by-property-id propertyId)))

(defquery property-borders
  {:parameters [propertyIds]
   :description "Returns property borders as POLYGON WKT strings"
   :user-roles #{:authority} ; At the time of writing the only use case is the neighbour map
   :input-validators [(partial action/vector-parameter-of :propertyIds v/kiinteistotunnus?)]}
  [_]
  (let [areas (plocation/property-lots-info propertyIds)]
    (if (seq areas)
      (ok :wkts (map #(select-keys % [:kiinttunnus :wkt]) areas))
      (fail :error.integration.ktj-down))))
