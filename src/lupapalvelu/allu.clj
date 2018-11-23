(ns lupapalvelu.allu
  "Allu functionality that either a) supports frontend or b) is not
  directly related to the integration functionality."
  (:require [lupapalvelu.backing-system.allu.core :as allu-core]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))


(defn- site->drawing [{:keys [id geometry area]}]
  (let [from        (get-in geometry [:crs :properties :name])
        coordinates (get-in geometry [:geometries 0 :coordinates 0])]
    {:name           area
     :id             id
     :source         "allu"
     :geometry       (->> coordinates
                          (map #(let [[x y] (coord/convert from "EPSG:3067" 3 %)]
                                  (format "%s %s" x y)))
                          (ss/join ",")
                          (format "POLYGON((%s))"))
     :geometry-wgs84 {:type        "Polygon"
                      :coordinates [(map #(coord/convert from "WGS84" 8 %)
                                         coordinates)]}}))


(defn- make-names-unique
  "Adds number (1...) to each string k value that is not unique. Assumes
  that the original values do not include similar numbers."
  [k xs]
  (->> (group-by k xs)
       vals
       (mapcat (fn [vs]
                 (cond-> vs
                   (> (count vs) 1) (->> count range
                                         (map #(update (nth (vec vs) %)
                                                       k
                                                       str " " (inc %)))))))))

(sc/defn ^:always-validate fetch-fixed-locations
  [kind :- (apply sc/enum (keys allu-core/FIXED-LOCATION-TYPES))]
  (when-let [drawings (some->> (kind allu-core/FIXED-LOCATION-TYPES)
                               allu-core/load-fixed-locations!
                               seq
                               (make-names-unique :area)
                               (sort-by :area)
                               (map site->drawing))]
    (mongo/update-by-id :allu-data kind {$set {:drawings drawings}} :upsert true)))
