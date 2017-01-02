(ns lupapalvelu.drawing
  (:require [sade.coordinate :as coord]
            [sade.strings :as sstr]
            [cljts.io :as jts]))

(defn- get-pos [coordinates]
  (mapv (fn [c] [(-> c .x) (-> c .y)]) coordinates))

(defn- parse-geometry [drawing]
  (try
     (get-pos (.getCoordinates (-> drawing :geometry jts/read-wkt-str)))
    (catch Exception e
      (println "Invalid geometry, message is: " (:geometry drawing) (.getMessage e))
      nil)))

(defn- parse-type [drawing]
  (try
    (first (re-seq #"[^(]+" (:geometry drawing)))
    (catch Exception e
      (println "Invalid geometry, message is: " (:geometry drawing) (.getMessage e))
      nil)))

(defn wgs84-geometry [drawing]
  (let [type (parse-type drawing)
        coordinates (parse-geometry drawing)]
    (if (and (some? type) (some? coordinates))
      {:type (sstr/capitalize type)
       :coordinates (mapv (fn [c] (coord/convert "EPSG:3067" "WGS84" 5 c)) coordinates)})))
