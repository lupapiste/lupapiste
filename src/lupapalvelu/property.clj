(ns lupapalvelu.property
  (:require [lupapalvelu.logging :refer [debugf infof]]
            [lupapalvelu.mml.property.api :as property-api]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [fail! now]]
            [sade.env :as env]
            [sade.property :as sprop])
  (:import [com.mongodb DuplicateKeyException WriteConcern]))

(defn insert-to-cache [result]
  (try
    (mongo/insert :propertyMunicipalityCache
                  (mongo/with-mongo-meta result)
                  WriteConcern/UNACKNOWLEDGED)
    (debugf "Inserted property %s to :propertyMunicipalityCache" (:propertyId result))
    (catch DuplicateKeyException _
      (debugf "Key alredy exists in :propertyMunicipalityCache: %s" (:propertyId result)))))

(defn- location-data-by-property-id-from-mml
  "Queries MML for property location information."
  [property-id]
  (if (env/feature? :disable-ktj-on-create)
    (infof "ktj-client is disabled - not getting municipality information for %s" property-id)
    (or
      (mongo/without-mongo-meta
        (mongo/select-one :propertyMunicipalityCache {:propertyId property-id} {:propertyId false}))
      (let [start  (double (now))
            result (dissoc (property-api/property-info-by-id! property-id) :propertyId)]
        (debugf "Municipality for property id from KTJKii took %.3f seconds" (/ (- (now) start) 1000))
        (when (:municipality result)
          (insert-to-cache (merge result {:propertyId property-id})))
        result))))


(defn location-by-property-id-from-mml
  "Queries MML for property location information.
  Returns map ([[lupapalvelu.mml.property.api/PropertyInfo]]) with location information."
  [property-id]
  (if (and (string? property-id) (re-matches sprop/db-property-id-pattern property-id))
    (location-data-by-property-id-from-mml property-id)
    (fail! :error.invalid-property-id :propertyId property-id)))

(defn municipality-by-property-id
  "Query MML property API for the location information and returns municipality code as string or nil if not found
  or property-id not in db-format.
  If no sufficient response from MML, fallback to resolve municipality from property-id as string.

  Potentially slow as function performs HTTP requests."
  [property-id]
  (or (get (location-by-property-id-from-mml property-id) :municipality)
      (sprop/municipality-id-by-property-id property-id)))
