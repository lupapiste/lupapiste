(ns lupapalvelu.integrations.geojson-2008-schemas
  (:require [schema.core :refer [defschema Num Str Keyword Any eq conditional maybe constrained one optional-key]]
            [clojure.test.check.generators :as gen]

            [sade.core :refer [def-]]
            [sade.schema-generators :as ssg]))

;;; FIXME: only allow :crs at top level
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

(defschema Position [(one Num "first") (one Num "second") Num])

(defschema LineStringCoordinates [(one Position "first") (one Position "second") Position])

(defschema ^:private LinearMaybeRingCoordinates
  [(one Position "coord1") (one Position "coord2") (one Position "coord3") (one Position "coord4") Position])

(defschema LinearRingCoordinates
  (constrained LinearMaybeRingCoordinates #(= (first %) (peek %))))

(ssg/register-generator LinearRingCoordinates (gen/fmap (fn [[coord :as coords]] (conj (pop coords) coord))
                                                        (ssg/generator LinearMaybeRingCoordinates)))

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
   :properties (maybe {Keyword Any})
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
