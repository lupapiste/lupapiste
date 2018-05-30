(ns lupapalvelu.integrations.geojson-2008-schemas
  (:require [schema.core :refer [defschema Num Str
                                 eq conditional maybe constrained pred optional-key]]))

;;; TODO: DRY out the `(and (map? v) (= (:type v) ...`:s

(defn named-crs? [v]
  (and (map? v) (= (:type v) "name")))
(defschema NamedCRS
  {:type (eq "name")
   :properties {:name Str}})

(defn linked-crs? [v]
  (and (map? v) (= (:type v) "link")))
(defschema LinkedCRS
  {:type (eq "link")
   :properties {:href Str ; TODO: URI
                (optional-key :type) Str}})

(defschema CRS
  (maybe (conditional
           named-crs?  NamedCRS
           linked-crs? LinkedCRS)))

(defn- GeoJSON-object? [type v]
  (and (map? v) (= (:type v) type)))
(defn- make-Geometry [type Coordinates]
  {:type (eq type)
   :coordinates Coordinates
   (optional-key :crs) CRS})

(defschema Position (constrained [Num] #(>= (count %) 2)))

(defschema LineStringCoordinates (constrained [Position] #(>= (count %) 2)))

(defschema LinearRingCoordinates
  (constrained [Position] (fn [coords]
                            (and (>= (count coords) 4)
                                 (= (first coords) (peek coords))))))

(def point? (partial GeoJSON-object? "Point"))
(defschema Point (make-Geometry "Point" Position))

(def multi-point? (partial GeoJSON-object? "MultiPoint"))
(defschema MultiPoint (make-Geometry "MultiPoint" [Position]))

(def line-string? (partial GeoJSON-object? "LineString"))
(defschema LineString (make-Geometry "LineString" LineStringCoordinates))

(def multi-line-string? (partial GeoJSON-object? "MultiLineString"))
(defschema MultiLineString (make-Geometry "MultiLineString" [LineStringCoordinates]))

(def polygon? (partial GeoJSON-object? "Polygon"))
(defschema Polygon (make-Geometry "Polygon" [LinearRingCoordinates]))

(def multi-polygon? (partial GeoJSON-object? "MultiPolygon"))
(defschema MultiPolygon (make-Geometry "MultiPolygon" [[LinearRingCoordinates]]))

(defschema Geometry
  (conditional
    point?             Point
    multi-point?       MultiPoint
    line-string?       LineString
    multi-line-string? MultiLineString
    polygon?           Polygon
    multi-polygon?     MultiPolygon
    'GeoJSONGeometry))

(def geometry-collection? (partial GeoJSON-object? "GeometryCollection"))
(defschema GeometryCollection
  {:type (eq "GeometryCollection")
   :geometries [Geometry]
   (optional-key :crs) CRS})

(def feature? (partial GeoJSON-object? "Feature"))
(defschema Feature
  {:type (eq "Feature")
   :geometry Geometry
   :properties (maybe (pred map?))
   (optional-key :crs) CRS})

(def feature-collection? (partial GeoJSON-object? "FeatureCollection"))
(defschema FeatureCollection
  {:type (eq "FeatureCollection")
   :features [Feature]
   (optional-key :crs) CRS})

(defschema GeoJSON-2008
  (conditional
    point?             Point
    multi-point?       MultiPoint
    line-string?       LineString
    multi-line-string? MultiLineString
    polygon?           Polygon
    multi-polygon?     MultiPolygon

    geometry-collection? GeometryCollection

    feature?            Feature
    feature-collection? FeatureCollection
    'GeoJSONObject))
