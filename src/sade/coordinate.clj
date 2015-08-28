(ns sade.coordinate
  (:require [taoensso.timbre :as timbre :refer [debug info warn error]]
            [clojure.string :as s]
            [sade.util :as util]
            [sade.core :refer :all])
  (:import [org.geotools.referencing.crs DefaultGeographicCRS]
           [org.geotools.referencing CRS]
           [org.geotools.geometry GeneralDirectPosition]))

;;; ETRS-TM35FIN / EPSG:3067 coordinate validators
(defn valid-x? [x]
  (if x
    (< 10000 (util/->double x) 800000)
    false))

(defn valid-y? [y]
  (if y
    (<= 6610000 (util/->double y) 7779999)
    false))

(defn validate-coordinates [[x y]]
  (when-not (and (valid-x? x) (valid-y? y))
    (fail :error.illegal-coordinates)))



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