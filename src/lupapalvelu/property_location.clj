(ns lupapalvelu.property-location
  (:require [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.wfs :as wfs]))

(defn- with-timestamp [property-info]
  (assoc property-info :created (java.util.Date.)))

(defn- without-mongo-meta [property-info]
  (dissoc property-info :id :_id :created))

(defn- property-location-from-cache [property-id]
  (seq (map without-mongo-meta (mongo/select :propertyCache {:kiinttunnus property-id}))))

(defn- property-location-from-wfs [property-id]
  (when-let [features (wfs/location-info-by-property-id property-id)]
    (let [location-infos (map wfs/feature-to-location features)]
      (mongo/insert-batch :propertyCache (map with-timestamp location-infos))
      location-infos)))

(defn property-location-info [property-id]
  (or
    (property-location-from-cache property-id)
    (property-location-from-wfs property-id)))
