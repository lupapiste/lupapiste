(ns sade.coordinate
  (:require [taoensso.timbre :as timbre :refer [debug info warn error]]
            [clojure.string :as s])
  (:import [org.geotools.referencing.crs DefaultGeographicCRS]
           [org.geotools.referencing CRS]
           [org.geotools.geometry GeneralDirectPosition]))

(defn round-to [n acc]
  (.setScale n acc BigDecimal/ROUND_HALF_UP))

(defn- resolve-crs [proj]
  (let [proj-name (s/lower-case (name proj))]
    (if (= "wgs84" proj-name) DefaultGeographicCRS/WGS84 (CRS/decode proj-name))))

(defn convert [source-projection target-projection result-accuracy coord-array]
  (info "Converting coordinates " coord-array " from projection " source-projection " to projection " target-projection)
  (let [source-CRS (resolve-crs source-projection)
        to-CRS     (resolve-crs target-projection)
        math-transform (CRS/findMathTransform source-CRS to-CRS true)
        direct-pos (->> coord-array
                     (map (comp #(.doubleValue %) bigdec))
                     (into-array Double/TYPE)
                     GeneralDirectPosition.)
        result-point  (. math-transform transform direct-pos nil)]
    (->> result-point
      .getCoordinate
      (map (comp #(.doubleValue %) #(round-to % result-accuracy) bigdec)))))