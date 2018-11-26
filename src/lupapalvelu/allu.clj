(ns lupapalvelu.allu
  "Allu functionality that either a) supports frontend or b) is not
  directly related to the integration functionality."
  (:require [clojure.set :as set]
            [lupapalvelu.backing-system.allu.core :as allu-core]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.action :as action]
            [schema.core :as sc]))


(defn- site->drawing [kind {:keys [id geometry area]}]
  (let [from        (get-in geometry [:crs :properties :name])
        coordinates (get-in geometry [:geometries 0 :coordinates 0])]
    {:name           area
     :id             id
     :source         kind
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

(def kind-schema (apply sc/enum (keys allu-core/FIXED-LOCATION-TYPES)))

(sc/defn ^:always-validate fetch-fixed-locations
  [kind :- kind-schema]
  (when-let [drawings (some->> (kind allu-core/FIXED-LOCATION-TYPES)
                               allu-core/load-fixed-locations!
                               seq
                               (make-names-unique :area)
                               (sort-by :area)
                               (map (partial kind site->drawing)))]
    (mongo/update-by-id :allu-data
                        kind
                        {$set {:drawings drawings}}
                        :upsert true)))

(defn allu-drawings [{:keys [drawings]} kind]
  (filter #(util/=as-kw kind (:source %)) drawings))

(defn allu-ids-for-drawings [application kind]
  (some->> (allu-drawings application kind)
           (map :allu-id)
           set))



(defn site-list [application kind]
  (let [app-allu-ids (allu-ids-for-drawings application kind)]
    (some->> (mongo/select-one :allu-data {:_id kind} {:drawings.name   1
                                                       :drawings.source 1
                                                       :drawings.id     1})
             :drawings
             seq
             (remove #(contains? app-allu-ids (:id %))))))

(defn add-allu-drawing [application kind allu-id]
  (when-not (contains? (allu-ids-for-drawings application kind)
                       allu-id)
    (if-let [drawing (some->> (mongo/select-one :allu-data {:_id kind})
                              :drawings
                              (util/find-by-id allu-id))]
      {$push {:drawings (assoc drawing
                               :id (->> (:drawings application)
                                        (map :id)
                                        (cons 0)
                                        (apply max)
                                        (+ 1 (rand-int 100)))
                               :allu-id (:id drawing))}}
      :error.not-found)))

(defn remove-drawing [application drawing-id]
  {$pull {:drawings {:id drawing-id}}})
