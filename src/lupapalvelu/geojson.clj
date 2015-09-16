(ns lupapalvelu.geojson
  (:require [sade.coordinate :as coord]))

;; Resolve polygons from GeoJSON Features
(defmulti resolve-polygons (comp :type :geometry))

(defmethod resolve-polygons "Polygon" [feature]
  (get-in feature [:geometry :coordinates]))

(defmethod resolve-polygons "MultiPolygon" [feature]
  (map (partial apply concat) (get-in feature [:geometry :coordinates])))


(defn validate-feature [feature]
  (reduce
    (fn [res polygon]
      (or res (coord/validate-coordinates polygon)))
    nil
    (apply concat (resolve-polygons feature))))

(defn validate-features [features]
  (reduce (fn [res feature] (or res (validate-feature feature))) nil features))

