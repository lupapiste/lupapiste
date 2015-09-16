(ns lupapalvelu.geojson
  (:require [sade.coordinate :as coord]
            [sade.core :refer [fail]]))

;; Resolve polygons from GeoJSON Features
(defmulti resolve-polygons (comp :type :geometry))

(defmethod resolve-polygons "Polygon" [feature]
  (get-in feature [:geometry :coordinates]))

(defmethod resolve-polygons "MultiPolygon" [feature]
  (map (partial apply concat) (get-in feature [:geometry :coordinates])))


(defn validate-point [[x y :as point]]
  (or
    (when-not (and (number? x) (number? y)) (fail :error.point-coordinate-not-number))
    (when-not (= (count point) 2) (fail :error.point-coordinate-wrong))
    (coord/validate-coordinates point)))

(defn validate-feature [feature]
  (reduce
    (fn [res point]
      (or res (validate-point point)))
    nil
    (apply concat (resolve-polygons feature))))

(defn validate-features [features]
  (reduce (fn [res feature] (or res (validate-feature feature))) nil features))

