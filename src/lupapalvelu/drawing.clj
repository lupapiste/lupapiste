(ns lupapalvelu.drawing
  (:require [cljts.geom :as geom]
            [cljts.io :as jts]
            [lupapalvelu.backing-system.allu.schemas :refer [ApplicationKind FixedLocationId]]
            [lupapalvelu.document.allu-schemas :as da]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo]
            [sade.coordinate :as coord]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :refer [defschema] :as sc]
            [taoensso.timbre :as timbre])
  (:import [com.vividsolutions.jts.geom Polygon Geometry GeometryCollection Point Coordinate]))

(def ShapefileFeature #"^feature-\S+$")

(defschema DrawingId
  (sc/cond-pre
    FixedLocationId  ; Old-school Oskari drawing
    ssc/UUIDStr      ; Manual drawing
    ShapefileFeature ; Drawing generated from an uploaded shapefile feature.
    ssc/ObjectIdStr  ; Originally an "official" Allu location
    ))

(defschema Drawing
  {:id                             DrawingId
   :name                           sc/Str
   :geometry                       sc/Str
   :geometry-wgs84                 geo/SingleGeometry
   (sc/optional-key :desc)         sc/Str
   (sc/optional-key :height)       sc/Str
   (sc/optional-key :area)         sc/Str
   (sc/optional-key :category)     sc/Str
   (sc/optional-key :allu-id)      sc/Int                   ;; From Allu
   (sc/optional-key :allu-section) sc/Str
   (sc/optional-key :source)       (sc/conditional
                                     keyword? ApplicationKind
                                     :else (apply sc/enum (map name (keys da/application-kinds))))})

(defschema Feature
  {:id         DrawingId
   :type       (sc/eq "Feature")
   :geometry   geo/SingleGeometry
   :properties {:name                     sc/Str
                (sc/optional-key :desc)   sc/Str
                (sc/optional-key :area)   sc/Num
                (sc/optional-key :height) sc/Num
                (sc/optional-key :width)  sc/Num
                (sc/optional-key :length) sc/Num}})

(defn- get-pos [coordinates]
  (mapv (fn [^Coordinate c] [(.x c) (.y c)]) coordinates))

(defn- coordinates-too-close?
  "Check for coordinates that have same x or y and really close x' or y'"
  [[^double x1 ^double y1] [^double x2 ^double y2]]
  (and (or (= x1 x2) (= y1 y2))
       (< (Math/abs (- x1 x2)) 0.3)
       (< (Math/abs (- y1 y2)) 0.3)))

(defn- filter-coord-duplicates [coordinates]
  (reduce (fn [coords c]
            (let [last-coord (peek coords)]
              (if (and last-coord (coordinates-too-close? last-coord c))
                (assoc coords (dec (count coords)) c)       ; Replace the last coordinate to keep the ring correct
                (conj coords c))))
          [] coordinates))

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

(defn drawing->feature
  "Converts Lupapiste drawing to GeoJSON Feature"
  [drawing]
  (letfn [(to-double [props]
            (->> props
                 (reduce-kv
                   (fn [a k v]
                     (if (contains? #{:area :length :height :width} k)
                       (if (string? v)
                         (assoc a k (Double/parseDouble v))
                         (assoc a k (double v)))
                       (assoc a k v)))
                   {})))]
    (try
      {:id         (:id drawing)
       :type       "Feature"
       :geometry   (:geometry-wgs84 drawing)
       :properties (-> drawing
                       (select-keys [:name :desc :area :height :width :length])
                       (util/strip-blanks)
                       (to-double))}
      (catch Exception e
        (timbre/error e "Error when converting drawing to Feature")))))
