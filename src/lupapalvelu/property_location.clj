(ns lupapalvelu.property-location
  (:require [clojure.set :refer [difference]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.wfs :as wfs]
            [monger.operators :refer :all])
  (:import [com.mongodb WriteConcern DuplicateKeyException]))


(defn- property-lots-from-cache [property-id]
  (seq (map mongo/without-mongo-meta (mongo/select :propertyCache {:kiinttunnus property-id}))))

(defn- property-lots-from-wfs [property-id]
  (when-let [features (wfs/lots-by-property-id-wfs2 property-id)]
    (let [location-infos (map wfs/lot-feature-to-location features)]
      (when (seq location-infos)
        (try
          ; Return right away, let cache be popupulated by chance
          (mongo/insert-batch :propertyCache (map mongo/with-mongo-meta location-infos) WriteConcern/UNACKNOWLEDGED)
          (catch DuplicateKeyException _)))
      location-infos)))

(defn property-lots-info
  "Return lots for proroperty. property-id can be a single id (string) or a collection of ids."
  [property-id]
  {:pre [(or (string? property-id) (coll? property-id))]}
  (if (string? property-id)
    (or
      (property-lots-from-cache property-id)
      (property-lots-from-wfs property-id))
    (let [property-ids (set property-id)
          cached-infos (map mongo/without-mongo-meta (mongo/select :propertyCache {:kiinttunnus {$in property-ids}}))
          found-ids    (set (map :kiinttunnus cached-infos))
          unfound-ids  (difference property-ids found-ids)]
      (if (zero? (count unfound-ids))
        cached-infos
        ; NLS WFS service does not support or-query, need to fetch areas one by one (in parallel)
        (let [missed-infos (pmap property-lots-from-wfs unfound-ids)]
          (concat cached-infos (->> missed-infos flatten (remove nil?))))))))

(defn all-property-id-muni-by-point
  "Finds properties for point, returns property-id and municipality."
  [x y]
  (->> (wfs/property-id-muni-by-point x y)
       (map (partial wfs/feature-to-property-id-municipality :ktjkiiwfs:RekisteriyksikonTietoja))))

(defn property-id-muni-by-point
  "Finds first property for point, returns property-id and municipality."
  [x y]
  (first (all-property-id-muni-by-point x y)))
