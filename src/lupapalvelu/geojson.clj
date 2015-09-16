(ns lupapalvelu.geojson
  (:require [sade.coordinate :as coord]
            [sade.core :refer [fail]]))

;; Resolve polygons from GeoJSON Features
(defmulti resolve-polygons (comp :type :geometry))

(defmethod resolve-polygons "Polygon" [feature]
  (get-in feature [:geometry :coordinates]))

(defmethod resolve-polygons "MultiPolygon" [feature]
  (map (partial apply concat) (get-in feature [:geometry :coordinates])))


(defn validate-polygon [polygon]
  (or
    (when-not (= (count polygon) 2) (fail :error.point-coordinate-wrong))
    (coord/validate-coordinates polygon)))

(defn validate-feature [feature]
  (reduce
    (fn [res polygon]
      (or res (validate-polygon polygon)))
    nil
    (apply concat (resolve-polygons feature))))

(defn validate-features [features]
  (reduce (fn [res feature] (or res (validate-feature feature))) nil features))

