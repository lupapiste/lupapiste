(ns lupapalvelu.allu
  "Allu functionality that either a) supports frontend or b) is not
  directly related to the integration functionality."
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.backing-system.allu.core :as allu-core]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :refer [defschema] :as sc]))

(def kind-schema (apply sc/enum (keys allu-core/FIXED-LOCATION-TYPES)))
(def drawing-id-schema (sc/conditional
                        int?  sc/Int
                        :else ssc/ObjectIdStr))

(defschema Drawing
  {;; Only the drawings originating from Allu have object string
   ;; ids. Thus, there should never be id conflicts between drawings
   ;; from different origins.
   :id                         drawing-id-schema
   :name                       sc/Str
   :geometry                   sc/Str
   :geometry-wgs84             {:coordinates [(sc/one [[(sc/one sc/Num "x") (sc/one sc/Num "y")]] "first")]
                                :type        (sc/enum "Polygon", "Point", "LineString")}
   (sc/optional-key :desc)     sc/Str
   (sc/optional-key :height)   sc/Str
   (sc/optional-key :area)     sc/Str
   (sc/optional-key :category) sc/Str
   (sc/optional-key :allu-id)  sc/Int ;; From Allu
   (sc/optional-key :source)   (sc/conditional
                                keyword? kind-schema
                                :else (apply sc/enum (map name (keys allu-core/FIXED-LOCATION-TYPES))))
   })

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


(defn make-names-unique
  "Adds number (1...) to each string k value that is not unique. Assumes
  that the original values do not include similar numbers."
  [k xs]
  (->> (group-by k xs)
       vals
       (mapcat (fn [vs]
                 (cond->> vs
                   (> (count vs) 1)
                   (map-indexed #(update %2 k str " " (inc %1))))))))

(sc/defn ^:always-validate fetch-fixed-locations
  [kind :- kind-schema]
  (when-let [drawings (some->> (kind allu-core/FIXED-LOCATION-TYPES)
                               allu-core/load-fixed-locations!
                               seq
                               (make-names-unique :area)
                               (sort-by :area)
                               (map (partial site->drawing kind)))]
    (mongo/update-by-id :allu-data
                        kind
                        {$set {:drawings drawings}}
                        :upsert true)))

(defn fetch-all-fixed-locations []
  (doseq [kind (keys allu-core/FIXED-LOCATION-TYPES)]
    (fetch-fixed-locations kind)))

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
                               :id (mongo/create-id)
                               :allu-id (:id drawing))}}
      :error.not-found)))

(defn remove-drawing [application drawing-id]
  {$pull {:drawings {:id drawing-id}}})

(defn merge-drawings
  "When Oskari 'saves' drawings to application, any external
  metadata (e.g., allu-id) is not present in the drawings to be
  saved. However, since the ids (and geometries) are untouched, we can
  manage the situation. This function returns map with two keys:

  allu-drawings: existing Allu-drawings with updated Oskari
  metadata (name, desc, ...). If an old drawing is not listed, it has
  been removed in Oskari.

  new-drawings: totally new or modified non-Allu drawings"
  [{old-drawings :drawings} new-drawings]
  (let [allu-draw-ids (set/intersection (->> (filter :allu-id old-drawings)
                                             (map :id)
                                             set)
                                        (set (map :id new-drawings)))]
    {:allu-drawings (map (fn [id]
                           (merge (util/find-by-id id old-drawings)
                                  (util/find-by-id id new-drawings)))
                         allu-draw-ids)
     :new-drawings  (remove #(contains? allu-draw-ids (:id %))
                            new-drawings)}))
