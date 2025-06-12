(ns lupapalvelu.low-carbon-assessment.climate-report-utils
  (:require [lupapalvelu.low-carbon-assessment.config :as low-carbon-config]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn carbon-footprint
  "kgCO2e emissions per m² of heated area per year (kgCO2e/m²/a)"
  [climate-report-data]
  (->> (get climate-report-data :hiilijalanjalki)
       vals
       (reduce (fn [acc {v :value}] (+ acc (util/->safe-double v)))
               0)))

(defn epbd-value
  "EPBD (European Performance of Buildings Directive) value:
  Carbon footprint per m² over the buildings lifespan (50-years) (kgCO2e/m²)"
  [climate-report-data]
  (* (carbon-footprint climate-report-data) low-carbon-config/BUILDING-LIFESPAN-YEARS))

(defn total-carbon-footprint
  "Total kgCO2e emissions over a buildings lifespan (50 years) for the entire buildings net heated area
  (EPBD value * total heated net area)"
  [climate-report-data net-heated-area]
  (* (epbd-value climate-report-data) net-heated-area))

(defn valid-climate-report?
  "To be considered a valid climate-report the `pvm` `laatija` `arvioinnissa-kaytetty-ohjelmisto` fields are required.
  The total carbon-footprint should also be > 0"
  [{data :data}]
  (let [climate-footprint (carbon-footprint data)
        field-values      (map (fn [field]
                                 (get-in data (conj field :value)))
                               low-carbon-config/climate-report-required-fields)]
    (and (pos? climate-footprint)
         (every? #(some? (ss/blank-as-nil %)) field-values))))

(defn heated-net-area-total-sum [climate-reports]
  (let [net-heated-area-path (concat [:data]
                                     low-carbon-config/climate-report-lammitetty-nettoala-path
                                     [:value])]
    (reduce (fn [acc [_id report]]
              (-> (get-in report net-heated-area-path 0)
                  util/->safe-double
                  (+ acc)))
            0
            climate-reports)))

(defn low-carbon-assessment-limits
  "Applications submitted before 2029 should use different limits than those submitted after 2029"
  [kayttotarkoitusluokka submitted-ts]
  (when-let [limit (if (< submitted-ts low-carbon-config/TIMESTAMP-2029)
                     (-> low-carbon-config/limits-2026-2028 kayttotarkoitusluokka)
                     (-> low-carbon-config/limits-2029 kayttotarkoitusluokka))]
    (low-carbon-config/with-epbd-limit limit)))
