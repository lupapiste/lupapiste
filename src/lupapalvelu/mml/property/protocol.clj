(ns lupapalvelu.mml.property.protocol)

(defprotocol PropertyApi
  (-property-info-by-id [this property-id])
  (-property-infos-by-wkt [this wkt radius])
  (-property-infos-by-point [this x y])
  (-property-details-by-id [this property-id])
  (-lot-property-id-by-point [this x y])
  (-lots-by-property-id [this property-id]))
