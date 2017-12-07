(ns lupapalvelu.rest.config
  (:require [sade.municipality :as muni]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [schema.core :as sc]))

(defn values-schema [values]
  {:key (apply sc/enum values)
   :displayName (i18n/lenient-localization-schema sc/Str)})

(sc/defschema Configuration
  {:permitTypes    [(values-schema (map name (keys (permit/permit-types))))]
   :states         [(values-schema (map name states/all-states))]
   :municipalities [(values-schema muni/municipality-codes)]
   :operations     [(values-schema (map name (keys operations/operations)))]})

(defn current-configuration
  "Returns current Lupapiste configuration values for certain properties."
  []
  (letfn [(key-display-name-mapper-with-loc-prefix [loc-prefix value]
            (hash-map :key (name value) :displayName (i18n/to-lang-map (str loc-prefix (name value)))))
          (key-display-name-mapper [value]
            (key-display-name-mapper-with-loc-prefix nil value))]
    {:permitTypes    (map key-display-name-mapper (keys (permit/permit-types)))
     :states         (map key-display-name-mapper states/all-states)
     :municipalities (map (partial key-display-name-mapper-with-loc-prefix "municipality.") muni/municipality-codes)
     :operations     (map (partial key-display-name-mapper-with-loc-prefix "operations.") (keys operations/operations))}))
