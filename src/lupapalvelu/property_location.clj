(ns lupapalvelu.property-location
  (:require [clojure.set :refer [difference]]
            [lupapalvelu.mml.property.api :as property-api]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all])
  (:import [com.mongodb DuplicateKeyException WriteConcern]))


(defn property-lots-from-cache [property-id]
  (seq (map mongo/without-mongo-meta (mongo/select :propertyCache {:kiinttunnus property-id}))))

(defn- property-lots-from-mml [property-id]
  (when-let [location-infos (property-api/lots-by-property-id! property-id)]
    (when (seq location-infos)
      (try
                                        ; Return right away, let cache be popupulated by chance
        (mongo/insert-batch :propertyCache (map mongo/with-mongo-meta location-infos) WriteConcern/UNACKNOWLEDGED)
        (catch DuplicateKeyException _)))
    location-infos))

(defn property-lots-info
  "Return lots for property. property-id can be a single id (string) or a collection of ids."
  [property-id]
  {:pre [(or (string? property-id) (coll? property-id))]}
  (if (string? property-id)
    (or
      (property-lots-from-cache property-id)
      (property-lots-from-mml property-id))
    (let [property-ids (set property-id)
          cached-infos (map mongo/without-mongo-meta (mongo/select :propertyCache {:kiinttunnus {$in property-ids}}))
          found-ids    (set (map :kiinttunnus cached-infos))
          unfound-ids  (difference property-ids found-ids)]
      (if (zero? (count unfound-ids))
        cached-infos
        ; NLS/MML service does not support or-query, need to fetch areas one by one (in parallel)
        (let [missed-infos (pmap property-lots-from-mml unfound-ids)]
          (concat cached-infos (->> missed-infos flatten (remove nil?))))))))

(defn property-id-muni-by-point
  "Finds first property for point, returns property-id and municipality."
  ([x y]
   ;; TODO LPK-8166: With projected 3D properties, a single (x,y) coordinate will
   ;; return multiple properties! Here we randomly discard all but one.
   (first (property-api/property-infos-by-point! x y)))
  ([{:keys [x y]}] (property-id-muni-by-point x y)))
