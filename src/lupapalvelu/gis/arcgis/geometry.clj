(ns lupapalvelu.gis.arcgis.geometry
  "Utility functions for translating Esri geometry types returned by ArcGIS
  into WKT usable by the map-component (via GeoJSON since that is already implemented)"
  (:require [lupapalvelu.mml.property.util :as property-util]))

(defn- esri-point->geojson-point [coordinates]
  {:type        "Point"
   :coordinates coordinates})

(defn- esri-paths->geojson-line [path]
  {:type        "LineString"
   :coordinates path})

(defn- esri-rings->geojson-polygon [ring]
  {:type        "Polygon"
   :coordinates ring})

(defn esri->wkt
  "No support for curves since they are not likely to be used and are coerced into polygons by default by ArcGIS.
  Also doesn't support envelopes since those are mainly used for searches and not results"
  [{:keys [x y points paths rings]}]
  (->> [(when (and x y) [(esri-point->geojson-point [x y])])
        (map esri-point->geojson-point points)
        (map esri-paths->geojson-line paths)
        (map esri-rings->geojson-polygon rings)]
       (reduce into)
       (map property-util/geojson->wkt)))
