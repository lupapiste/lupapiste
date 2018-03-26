(ns lupapalvelu.property
  (:require [taoensso.timbre :refer [debugf infof warnf]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.property :as sprop]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.wfs :as wfs])
  (:import (com.mongodb WriteConcern)))

(defn property-info-from-wfs [property-id]
  (->> (wfs/get-property-location-info-by-property-id property-id)
       (wfs/location-feature-to-property-info)))

(defn location-data-by-property-id-from-wfs [property-id]
  "Queries KTJKii WFS for property location information."
  (if (env/feature? :disable-ktj-on-create)
    (infof "ktj-client is disabled - not getting municipality information for %s" property-id)
    (or
      (mongo/without-mongo-meta (mongo/select-one :propertyMunicipalityCache {:propertyId property-id} [:municipality]))
      (let [start  (double (now))
            result (property-info-from-wfs property-id)]
        (debugf "Municipality for property id from KTJKii took %.3f seconds" (/ (- (now) start) 1000))
        (when (:municipality result)
          (mongo/insert :propertyMunicipalityCache
                        (mongo/with-mongo-meta (merge result {:propertyId property-id}))
                        WriteConcern/UNACKNOWLEDGED)
          (debugf "Inserted property %s to :propertyMunicipalityCache" property-id))
        result))))


(defn location-by-property-id-from-wfs
  "Queries KTJKii WFS for property location information. Returns map with location information."
  [property-id]
  (if (and (string? property-id) (re-matches sprop/db-property-id-pattern property-id))
    (location-data-by-property-id-from-wfs property-id)
    (fail! :error.invalid-property-id :propertyId property-id)))

(defn municipality-by-property-id
  "Query KTJKii WFS for property location information and returns municipality code as string or nil if not found
  or property-id not in db-format.
  If no sufficient response from NLS, fallback to resolve municipality from property-id as string.

  Potentially slow as function performs HTTP requests."
  [property-id]
  (or (get (location-by-property-id-from-wfs property-id) :municipality)
      (sprop/municipality-id-by-property-id property-id)))
