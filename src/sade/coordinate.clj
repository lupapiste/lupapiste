(ns sade.coordinate
  (:require [taoensso.timbre :refer [debug info warn error]]
            [clojure.string :as s]
            [clojure.core.memoize :as memo]
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

(defn validate-x
  "X coordinate input validator for actions"
  [{{:keys [x]} :data}]
  (when (and x (not (valid-x? x)))
    (fail :error.illegal-coordinates)))

(defn validate-y
  "Y coordinate input validator for actions"
  [{{:keys [y]} :data}]
  (when (and y (not (valid-y? y)))
    (fail :error.illegal-coordinates)))

(defn round-to [^BigDecimal n ^long acc]
  (.setScale n acc BigDecimal/ROUND_HALF_UP))

(def WGS84-URN
  "Uniform Resource Name for the WGS 84 coordinate system famous for GPS."
  "EPSG:4326")

(defn- resolve-crs [proj]
  (let [proj-name (s/lower-case (name proj))]
    (if (= "wgs84" proj-name) DefaultGeographicCRS/WGS84 (CRS/decode proj-name))))

(defn- do-convert [source-projection target-projection result-accuracy coord-array]
  (let [source-CRS (resolve-crs source-projection)
        to-CRS (resolve-crs target-projection)
        math-transform (CRS/findMathTransform source-CRS to-CRS true)
        direct-pos (->> coord-array
                        (map (comp #(.doubleValue ^BigDecimal %) bigdec))
                        ^doubles (into-array Double/TYPE)
                        GeneralDirectPosition.)
        result-point (. math-transform transform direct-pos nil)]
    (->> result-point
         .getCoordinate
         (map (comp #(.doubleValue ^BigDecimal %) #(round-to % result-accuracy) bigdec)))))

(def ^{:arglists '([source-projection target-projection result-accuracy coord-array])} convert
  "Convert `coord-array` (a seq of numbers representing a coordinate) from `source-projection` to `target-projection`
  with `result-accuracy`."
  (memo/lu do-convert :lu/threshold 200))

(defn convert-tree
  "Convert tree of coordinate seqs `coords` from `source-projection` to `target-projection` with `accuracy`,
  using [[convert]] for the leaves."
  [source-projection target-projection accuracy coords]
  (letfn [(tree-convert [coords]
            (if (seqable? (first coords))
              (mapv tree-convert coords)
              (convert source-projection target-projection accuracy coords)))]
    (tree-convert coords)))

(def known-bad-coordinates
  "Coordinates from KRYSP message that are known to be invalid."
  ; Used by Facta to mark an unknown location
  #{["3.12E7" "6600000.0"]
    ["3.02E7" "6600000.0"]
    ["2.92E7" "6600000.0"]
    ["2.82E7" "6600000.0"]
    ["2.72E7" "6600000.0"]
    ["2.62E7" "6600000.0"]
    ["2.52E7" "6600000.0"]
    ["2.42E7" "6600000.0"]
    ["2.32E7" "6600000.0"]
    ["2.22E7" "6600000.0"]
    ["2.12E7" "6600000.0"]
    ["2.02E7" "6600000.0"]
    ["1.92E7" "6600000.0"]})
