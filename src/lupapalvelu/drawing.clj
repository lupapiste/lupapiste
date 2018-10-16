(ns lupapalvelu.drawing
  (:require [sade.coordinate :as coord]
            [cljts.io :as jts]
            [cljts.geom :as geom]
            [taoensso.timbre :as timbre])
  (:import [com.vividsolutions.jts.geom Polygon Geometry GeometryCollection Point]))

(defn- get-pos [coordinates]
  (mapv (fn [c] [(.x c) (.y c)]) coordinates))

(defn- coordinates-to-close?
  "Check for coordinates that have same x or y and really close x' or y'"
  [[x1 y1] [x2 y2]]
  (and (or (= x1 x2) (= y1 y2))
       (< (Math/abs (- x1 x2)) 0.3)
       (< (Math/abs (- y1 y2)) 0.3)))

(defn- filter-coord-duplicates [coordinates]
  (reduce
    (fn [coords c]
      (if (or (= (last coords) c) (coordinates-to-close? (last coords) c))
        (concat (doall (drop-last coords)) [c]) ; Replace the last coordinate to keep the ring correct
        (concat coords [c])))
    []
    coordinates))

(defn- valid-polygon [coordinates]
  (let [c-objs (map (fn [coord] (geom/c (first coord) (second coord))) coordinates)
        linear-ring (geom/linear-ring c-objs)
        polygon (geom/polygon linear-ring [])]
    (if (.isValid ^Polygon polygon)
      coordinates
      (throw (IllegalArgumentException. "Invalid polygon")))))

(defn- valid-linestring [coordinates]
  ;; LineString must have at least two points
  (if (second coordinates)
    coordinates
    (throw (IllegalArgumentException. "LineString does not have at least two points"))))

(defn- convert-geometry [^Geometry geometry]
  (->> (.getCoordinates geometry)
       get-pos
       filter-coord-duplicates
       (mapv (fn [c] (coord/convert "EPSG:3067" "WGS84" 12 c)))))

(defn- parse-wkt-drawing [drawing]
  (try
    (let [parsed-wkt (-> drawing :geometry jts/read-wkt-str)
          geometry-type (.getGeometryType ^Geometry parsed-wkt)
          converted-coordinates (if (instance? GeometryCollection parsed-wkt)
                                  (->> (range (.getNumGeometries ^GeometryCollection parsed-wkt))
                                       (map #(.getGeometryN ^GeometryCollection parsed-wkt %))
                                       (map convert-geometry))
                                  (convert-geometry parsed-wkt))]
      (when (seq converted-coordinates)
        {:type geometry-type
         :coordinates (case geometry-type
                        "MultiPolygon" (->> (mapv valid-polygon converted-coordinates)
                                            (mapv vector))
                        "Polygon" (-> (valid-polygon converted-coordinates) vector)
                        "LineString" (valid-linestring converted-coordinates)
                        "Point" (first converted-coordinates)
                        converted-coordinates)}))
    (catch Exception e
      (timbre/warn "Invalid geometry:" (:geometry drawing) "(" (.getMessage e) ")")
      nil)))

(defn wgs84-geometry
  "Converts a WKT drawing to a valid GeoJSON object. Returns nil if the drawing can't be converted to valid GeoJSON."
  [drawing]
  (parse-wkt-drawing drawing))

(defn interior-point [geometry-string]
  (try
    (let [interior-point (.getInteriorPoint ^Geometry (jts/read-wkt-str geometry-string))
          x (.getX ^Point interior-point)
          y (.getY ^Point interior-point)]
      [x y])
    (catch Exception e
      (timbre/warn "Failed to resolve interior point for geometry:" geometry-string "(" (.getMessage e) ")")
      nil)))
