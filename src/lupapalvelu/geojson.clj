(ns lupapalvelu.geojson
  (:require [clojure.walk :as walk]
            [sade.coordinate :as coord]
            [sade.core :refer [fail]]
            [monger.operators :refer [$geoWithin]]))

;; Resolve polygons from GeoJSON Features
(defmulti resolve-polygons (comp :type :geometry))

(defmethod resolve-polygons "Polygon" [feature]
  (get-in feature [:geometry :coordinates]))

(defmethod resolve-polygons "MultiPolygon" [feature]
  (map (partial apply concat) (get-in feature [:geometry :coordinates])))

(defmethod resolve-polygons "MultiLineString" [feature]
  (get-in feature [:geometry :coordinates]))

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

(defn- select-coordinates
  "If given coordinates are numbers, returns first two numbers,
   else returns the sequential untouched.
   [2 4 1] => [2 4]"
  [coordinates]
  (if (and (sequential? coordinates) (every? number? coordinates))
    (take 2 coordinates)
    coordinates))

(defn- ensure-feature-points [features]
  (map
    #(update-in % [:geometry :coordinates] (partial walk/prewalk select-coordinates))
    features))

(defn ensure-features [areas]
  (update-in areas [:features] ensure-feature-points))
