(ns lupapalvelu.integrations.geojson-2008-schemas
  (:require [schema.core :refer [defschema Num Str Keyword Any
                                 eq conditional maybe constrained one optional optional-key]]
            [clojure.test.check.generators :as gen]

            [sade.core :refer [def-]]
            [sade.schema-generators :as ssg]))

;;; TODO: Combine with lupapalvelu.geojson
;;; TODO: DRY out the `(and (map? v) (= (:type v) ...`:s
;;; TODO: Bounding boxes

;;;; # Geometry "Primitives"

(defn- finite? [n] (Double/isFinite n))

(defschema FiniteNum (constrained Num finite?))

(defschema Position [(one FiniteNum "longitude") (one FiniteNum "latitude") (optional FiniteNum "altitude")])

(defschema LineStringCoordinates [(one Position "first") (one Position "second") Position])

(defschema ^:private LinearMaybeRingCoordinates
  [(one Position "coord1") (one Position "coord2") (one Position "coord3") (one Position "coord4") Position])

(defschema LinearRingCoordinates
  (constrained LinearMaybeRingCoordinates #(= (first %) (peek %))))

(ssg/register-generator LinearRingCoordinates (gen/fmap (fn [[coord :as coords]] (conj (pop coords) coord))
                                                        (ssg/generator LinearMaybeRingCoordinates)))

;;;; # Geometry Objects

(defn- GeoJSON-object? [type v]
  (and (map? v) (= (:type v) type)))
(defn- make-Geometry [type kvs]
  (merge {:type (eq type), Keyword Any} kvs))

(defn- make-SingleGeometry [type Coordinates]
  (make-Geometry type {:coordinates Coordinates}))

(def point? (partial GeoJSON-object? "Point"))
(defschema Point (make-SingleGeometry "Point" Position))

(def multi-point? (partial GeoJSON-object? "MultiPoint"))
(defschema MultiPoint (make-SingleGeometry "MultiPoint" [Position]))

(def line-string? (partial GeoJSON-object? "LineString"))
(defschema LineString (make-SingleGeometry "LineString" LineStringCoordinates))

(def multi-line-string? (partial GeoJSON-object? "MultiLineString"))
(defschema MultiLineString (make-SingleGeometry "MultiLineString" [LineStringCoordinates]))

(def polygon? (partial GeoJSON-object? "Polygon"))
(defschema Polygon (make-SingleGeometry "Polygon" [LinearRingCoordinates]))

(def multi-polygon? (partial GeoJSON-object? "MultiPolygon"))
(defschema MultiPolygon (make-SingleGeometry "MultiPolygon" [[LinearRingCoordinates]]))

(defschema SingleGeometry
  (conditional
    point? Point
    multi-point? MultiPoint
    line-string? LineString
    multi-line-string? MultiLineString
    polygon? Polygon
    multi-polygon? MultiPolygon
    'GeoJSONSingleGeometry))

(def geometry-collection? (partial GeoJSON-object? "GeometryCollection"))
(defschema GeometryCollection (make-Geometry "GeometryCollection" {:geometries [SingleGeometry]}))

(defschema Geometry
  (conditional
    point? Point
    multi-point? MultiPoint
    line-string? LineString
    multi-line-string? MultiLineString
    polygon? Polygon
    multi-polygon? MultiPolygon
    geometry-collection? GeometryCollection
    'GeoJSONGeometry))

;;;; # Features

(def feature? (partial GeoJSON-object? "Feature"))
(defschema Feature
  {:type       (eq "Feature")
   :geometry   Geometry
   :properties (maybe {Keyword Any})
   Keyword     Any})

(def feature-collection? (partial GeoJSON-object? "FeatureCollection"))
(defschema FeatureCollection
  {:type     (eq "FeatureCollection")
   :features [Feature]
   Keyword   Any})

;;;; # CRS

(defn named-crs? [v]
  (and (map? v) (= (:type v) "name")))
(defschema NamedCRS
  {:type       (eq "name")
   :properties {:name Str}})

(defn linked-crs? [v]
  (and (map? v) (= (:type v) "link")))
(defschema LinkedCRS
  {:type       (eq "link")
   :properties {:href                Str                    ; TODO: URI
                (optional-key :type) Str}})

(defschema CRS
  (maybe (conditional
           named-crs? NamedCRS
           linked-crs? LinkedCRS)))

(defn with-crs [obj-schema]
  (assoc obj-schema (optional-key :crs) CRS))

;;;; # Top Level GeoJSON Object

(defschema GeoJSON-2008
  (conditional
    point? (with-crs Point)
    multi-point? (with-crs MultiPoint)
    line-string? (with-crs LineString)
    multi-line-string? (with-crs MultiLineString)
    polygon? (with-crs Polygon)
    multi-polygon? (with-crs MultiPolygon)
    geometry-collection? (with-crs GeometryCollection)

    feature? (with-crs Feature)
    feature-collection? (with-crs FeatureCollection)
    'GeoJSONObject))
