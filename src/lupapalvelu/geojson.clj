(ns lupapalvelu.geojson)

;; Resolve polygons from GeoJSON Features
(defmulti resolve-polygons (comp :type :geometry))

(defmethod resolve-polygons "Polygon" [feature]
  (get-in feature [:geometry :coordinates]))

(defmethod resolve-polygons "MultiPolygon" [feature]
  (map (partial apply concat) (get-in feature [:geometry :coordinates])))
