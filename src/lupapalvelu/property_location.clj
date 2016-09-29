(ns lupapalvelu.property-location
  (:require [clojure.set :refer [difference]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.wfs :as wfs])
  (:import (com.mongodb WriteConcern)))


(defn- property-location-from-cache [property-id]
  (seq (map mongo/without-mongo-meta (mongo/select :propertyCache {:kiinttunnus property-id}))))

(defn- property-location-from-wfs [property-id]
  (when-let [features (wfs/location-info-by-property-id property-id)]
    (let [location-infos (map wfs/feature-to-location features)]
      (when (seq location-infos)
        (try
          ; Return right away, let cache be popupulated by chance
          (mongo/insert-batch :propertyCache (map mongo/with-mongo-meta location-infos) WriteConcern/UNACKNOWLEDGED)
          (catch com.mongodb.DuplicateKeyException ignored)))
      location-infos)))

(defn property-location-info
  "property-id can be a single id (string) or a collection of ids."
  [property-id]
  {:pre [(or (string? property-id) (coll? property-id))]}
  (if (string? property-id)
    (or
      (property-location-from-cache property-id)
      (property-location-from-wfs property-id))
    (let [property-ids (set property-id)
          cached-infos (map mongo/without-mongo-meta (mongo/select :propertyCache {:kiinttunnus {$in property-ids}}))
          found-ids    (set (map :kiinttunnus cached-infos))
          unfound-ids  (difference property-ids found-ids)]
      (if (zero? (count unfound-ids))
        cached-infos
        ; NLS WFS service does not support or-query, need to fetch areas one by one (in parallel)
        (let [missed-infos (pmap property-location-from-wfs unfound-ids)]
          (concat cached-infos (->> missed-infos flatten (remove nil?))))))))
