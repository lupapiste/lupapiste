(ns lupapalvelu.allu
  "Allu functionality that either a) supports frontend or b) is not
  directly related to the integration functionality."
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [ApplicationType ApplicationKind LocationType
                                                             FixedLocation]]
            [lupapalvelu.document.allu-schemas :as da]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.drawing :refer [Drawing]]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [swiss.arrows :refer [-<>]]
            [taoensso.timbre :refer [error]]))



(sc/defn ^:private location-type :- LocationType
  "The location/drawings type used on the application ('fixed' or 'custom')."
  [{:keys [documents] :as application}]
  (case (allu/application-type application)
    "sijoitussopimus" "custom"
    "lyhytaikainen-maanvuokraus" (let [location-doc (first (filter #(= (doc-name %) "lmv-location") documents))]
                                   (-> location-doc :data :lmv-location :location-type :value))
    "promootio" (let [location-doc (first (filter #(= (doc-name %) "promootio-location") documents))]
                  (-> location-doc :data :promootio-location :location-type :value))))

;;; TODO: These GeoJSON functions could be moved to some less specific ns

(defn- GeoJSON-coordinates->WKT-args [source-crs target-crs coordinates]
  (letfn [(point? [coords] (not (seqable? (first coords))))
          (tuples [coords]
            (if-not (point? coords)
              (format "(%s)" (ss/join ", " (map tuples coords)))
              (->> (take 2 coords)                          ; Only use longitude and latitude
                   (coord/convert source-crs target-crs 3)
                   (ss/join " "))))]
    (cond->> (tuples coordinates)
      (point? coordinates) (format "(%s)"))))

(sc/defn ^:private GeoJSON-SingleGeometry->WKT :- sc/Str
  [source-crs target-crs {:keys [type coordinates]} :- geo/SingleGeometry]
  (format "%S%s" type (GeoJSON-coordinates->WKT-args source-crs target-crs coordinates)))

(sc/defn fixed-location->drawing :- (sc/maybe Drawing)
  [{:keys [applicationKind id geometry area section]} :- FixedLocation]
  (let [from (get-in geometry [:crs :properties :name] coord/WGS84-URN)
        first-geometry (get-in geometry [:geometries 0])]
    (try
      (-<> {:name           area
            :id             id
            :source         (get da/application-kinds-inverse applicationKind)
            :geometry       (GeoJSON-SingleGeometry->WKT from "EPSG:3067" first-geometry)
            :geometry-wgs84 (-> first-geometry
                                (select-keys [:type :coordinates])
                                (update :coordinates (partial coord/convert-tree from "WGS84" 8)))}
           (util/assoc-when :allu-section section)
           (sc/validate Drawing <>))
      (catch Exception err
        (error err applicationKind id)))))

(defn make-names-unique
  "Adds allu-id to each string k value that is not unique. Assumes
  that the original values do not include similar numbers."
  [k xs]
  (->> (group-by k xs)
       vals
       (mapcat (fn [vs]
                 (cond->> vs
                   (> (count vs) 1)
                   (map #(update % k str (format " (%s)" (:id %)))))))))

(sc/defn ^:always-validate fetch-application-kinds
  [application-kind :- ApplicationType]
  (let [m (zipmap (vals da/application-kinds)
                  (keys da/application-kinds))]
    (when-let [kinds (some->> application-kind
                              allu/load-application-kinds!
                              (map #(get m %))
                              seq)]
      (mongo/update-by-id :allu-data
                          :application-kinds
                          {$set {application-kind kinds}}
                          :upsert true))))

(sc/defn ^:always-validate application-kinds
  [application-type :- ApplicationType]
  (some-> (mongo/select-one :allu-data
                            {:_id :application-kinds}
                            {application-type 1})
          application-type))

(sc/defn ^:always-validate fetch-fixed-locations
  [kind :- ApplicationKind]
  (when-let [drawings (some->> kind
                               allu/load-fixed-locations!
                               seq
                               (make-names-unique :area)
                               (sort-by :area)
                               (map fixed-location->drawing)
                               (remove nil?)
                               seq)]
    (mongo/update-by-id :allu-data
                        :fixed-locations
                        {$set {(util/kw-path kind :drawings) drawings}}
                        :upsert true)))

(defn allu-drawings [{:keys [drawings]} kind]
  (filter #(util/=as-kw kind (:source %)) drawings))

(defn- allu-drawing-in-application? [{:keys [drawings]} allu-draw]
  (some (fn [draw]
          (and (util/=as-kw (:source draw) (:source allu-draw))
               (= (:allu-id draw) (:id allu-draw))))
        drawings))

(defn site-list [application & [kind]]
  (some->> (mongo/select-one :allu-data
                             {:_id :fixed-locations}
                             (cond-> {}
                               kind (assoc kind 1)))
           (reduce-kv (fn [acc k v]
                        (cond-> acc
                          (not= k :id) (concat (:drawings v))))
                      [])
           seq
           (map #(select-keys % [:source :name :id :allu-section]))
           (remove (partial allu-drawing-in-application? application))))

(defn filter-allu-drawings [{:keys [drawings]} kind]
  (when (some (fn [{:keys [allu-id source]}]
                (and allu-id (not= source kind)))
              drawings)
    {$pull {:drawings {:allu-id {$exists true}
                       :source  {$ne kind}}}}))

(defn add-allu-drawing [application kind allu-id]
  (let [kind (keyword kind)]
    (when-not (allu-drawing-in-application? application
                                            {:source kind :id allu-id})
      (if-let [drawing (some->> (mongo/select-one :allu-data
                                                  {:_id :fixed-locations}
                                                  {kind 1})
                                kind
                                :drawings
                                (util/find-by-id allu-id))]
        {$push {:drawings (assoc drawing
                            :id (mongo/create-id)
                            :allu-id (:id drawing))}}
        :error.not-found))))

(defn remove-drawing [_application drawing-id]
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

(defn clean-up-drawings-by-location-type
  "Delete FixedLocation drawings if [[location-type]] is 'custom' and custom drawings if it is 'fixed'."
  [{:keys [application] :as command}]
  (let [custom? (case (location-type application)
                  "fixed" false
                  "custom" true)]
    (action/update-application command {$pull {:drawings {:allu-id {$exists custom?}}}})))
