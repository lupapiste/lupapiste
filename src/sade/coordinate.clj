(ns sade.coordinate
  (:require [clojure.string :as s])
  (:import [org.geotoolkit.referencing.crs DefaultGeographicCRS]
           [org.geotoolkit.referencing CRS]
           [org.geotoolkit.geometry GeneralDirectPosition]
           #_[org.opengis.referencing.operation MathTransform TransformException]
           #_[org.opengis.referencing.crs CoordinateReferenceSystem]
           ))

(defn round-to [n acc]
  (.setScale n acc BigDecimal/ROUND_HALF_UP))

(defn- resolve-crs-from-proj [proj]
  (let [proj-name (s/lower-case (name proj))]
    (if (= "wgs84" proj-name) DefaultGeographicCRS/WGS84 (CRS/decode proj-name))))

(defn convert [from-proj to-proj result-accuracy coord-array]

  (println "\n convert, from-proj: " (name from-proj) ", to-proj: " (name to-proj) ", coord-array: " coord-array "\n")

  (let [;coord-array (map bigdec coord-array)
        source-CRS (resolve-crs-from-proj from-proj)
        to-CRS     (resolve-crs-from-proj to-proj)
        math-transform (CRS/findMathTransform source-CRS to-CRS true)
        direct-pos (GeneralDirectPosition. (into-array Double/TYPE (map #(.doubleValue %) coord-array)))
        res-point  (. math-transform transform direct-pos nil)]
    (->> res-point
      .getCoordinate
      (map (comp #(round-to % result-accuracy) bigdec))
;      (map .doubleValue)
      )))