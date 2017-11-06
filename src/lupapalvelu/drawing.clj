(ns lupapalvelu.drawing
  (:require [sade.coordinate :as coord]
            [sade.strings :as sstr]
            [cljts.io :as jts]
            [cljts.geom :as geom]
            [taoensso.timbre :as timbre])
  (:import [com.vividsolutions.jts.geom Polygon]))

(defn- get-pos [coordinates]
  (mapv (fn [c] [(-> c .x) (-> c .y)]) coordinates))

(defn- parse-geometry [drawing]
  (try
     (get-pos (.getCoordinates (-> drawing :geometry jts/read-wkt-str)))
    (catch Exception e
      (timbre/warn "Invalid geometry, message is: " (:geometry drawing) (.getMessage e))
      nil)))

(defn- parse-type [drawing]
  (try
    (first (re-seq #"[^(]+" (:geometry drawing)))
    (catch Exception e
      (timbre/warn "Invalid geometry, message is: " (:geometry drawing) (.getMessage e))
      nil)))

(defn- wkt-type->geojson-type [wkt-type]
  (case wkt-type
    "LINESTRING" "LineString"
    "MULTILINESTRING" "LineString"
    "MULTIPOLYGON" "MultiPolygon"
    "MULTIPOINT" "MultiPoint"
    "GEOMETRYCOLLECTION" "GeometryCollection"
    (sstr/capitalize wkt-type)))

(defn- valid-polygon? [coordinates]
  (try
    (let [c-objs (map (fn [coord] (geom/c (first coord) (second coord))) coordinates)
          linear-ring (geom/linear-ring c-objs)
          polygon (geom/polygon linear-ring [])]
      (.isValid ^Polygon polygon))
    (catch Exception e
      (timbre/warn e "Polygon validation error"))))

(defn- filter-coord-duplicates [coordinates]
  (reduce
    (fn [coords c]
      (if-not (= (last coords) c)
        (concat coords [c])
        coords))
    []
    coordinates))

(defn wgs84-geometry
  "Converts WKT drawing to a valid GeoJSON object. Returns nil if the drawing can't be converted to valid GeoJSON."
  [drawing]
  (let [geojson-type (-> (parse-type drawing) wkt-type->geojson-type)
        is-polygon? (= "Polygon" geojson-type)
        coordinates (-> (parse-geometry drawing)
                        filter-coord-duplicates)]
    (when (and geojson-type
               (seq coordinates)
               (or (not is-polygon?) (valid-polygon? coordinates))
               (or (not= "LineString" geojson-type) (second coordinates))) ; LineString must have at least two points
      {:type geojson-type
       :coordinates (cond->> (mapv (fn [c] (coord/convert "EPSG:3067" "WGS84" 12 c)) coordinates)
                             (= "Point" geojson-type) first ; Point is a simple pair
                             is-polygon? (conj []))}))) ; A valid polygon has one more surrounding vector
