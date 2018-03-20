(ns lupapalvelu.property
  (:require [taoensso.timbre :refer [debugf infof warnf]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.property :as sprop]
            [lupapalvelu.wfs :as wfs]))

(defn location-data-by-property-id-from-wfs [property-id]
  "Queries KTJKii WFS for property location information."
  (let [start  (double (now))
        result (->> (wfs/get-property-location-info-by-property-id property-id)
                    (wfs/location-feature-to-property-info))]
    (debugf "Municipality for property id from KTJKii took %.3f seconds" (/ (- (now) start) 1000))
    result))

(defn municipality-by-property-id-from-wfs
  "Queries KTJKii WFS for property location information. Returns municipality code as string."
  [property-id]
  (if (env/feature? :disable-ktj-on-create)
    (infof "ktj-client is disabled - not getting municipality information for %s" property-id)
    (if (and (string? property-id) (re-matches sprop/db-property-id-pattern property-id))
      (:municipality (location-data-by-property-id-from-wfs property-id))
      (warnf "Property ID not in db format: %s" property-id))))

(defn municipality-id-by-property-id
  "Query KTJKii WFS for property location information and returns municipality code as string or nil if not found
  or property-id not in db-format.
  If no sufficient response from NLS, fallback to resolve municipality from property-id as string.

  Potentially slow as function performs HTTP requests."
  [property-id]
  (or (municipality-by-property-id-from-wfs property-id)
      (sprop/municipality-id-by-property-id property-id)))
