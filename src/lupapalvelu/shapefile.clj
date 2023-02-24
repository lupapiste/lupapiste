(ns lupapalvelu.shapefile
  "Shapefiles are used for uploading organization areas and application drawings."
  (:require [clojure.walk :refer [postwalk]]
            [lupapalvelu.drawing :refer [Drawing]]
            [lupapalvelu.geojson :as geo]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.json :as json]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [me.raynes.fs :as fs]
            [noir.response :as resp]
            [plumbing.core :refer [defnk]]
            [sade.core :refer [fail!]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :refer [error]])
  (:import [com.vividsolutions.jts.geom Geometry]
           [java.io File]
           [java.util ArrayList]
           [org.geotools.data FileDataStoreFinder DataUtilities]
           [org.geotools.data.simple SimpleFeatureCollection]
           [org.geotools.feature FeatureCollection]
           [org.geotools.feature.simple SimpleFeatureBuilder SimpleFeatureTypeBuilder]
           [org.geotools.geojson.feature FeatureJSON]
           [org.geotools.geojson.geom GeometryJSON]
           [org.geotools.geometry.jts JTS]
           [org.geotools.referencing CRS]
           [org.geotools.referencing.crs DefaultGeographicCRS]
           [org.opengis.feature Feature]
           [org.opengis.feature.simple SimpleFeature]))

(defschema FileInfo
  {:filename    ssc/NonBlankStr
   :contentType sc/Str
   :size        ssc/Nat
   :created     sc/Int})

(defn- ^SimpleFeatureCollection transform-crs-to-wgs84
  "Convert feature crs in collection to WGS84"
  [org-id existing-areas ^FeatureCollection collection]
  (let [existing-areas        (if (and org-id (not existing-areas))
                                (mongo/by-id :organizations org-id {:areas.features.id              1
                                                                    :areas.features.properties.nimi 1
                                                                    :areas.features.properties.NIMI 1})
                                {:areas existing-areas})
        map-of-existing-areas (into {} (map (fn [a]
                                              (let [properties (:properties a)
                                                    nimi       (if (contains? properties :NIMI)
                                                                 (:NIMI properties)
                                                                 (:nimi properties))]
                                                {nimi (:id a)})) (get-in existing-areas [:areas :features])))
        list                  (ArrayList.)]
    (with-open [iterator (.features collection)]
      (loop [^Feature feature (when (.hasNext iterator)
                                (.next iterator))]
        (when feature
          ;; Set CRS to WGS84 to bypass problems when converting to GeoJSON (CRS detection is skipped with WGS84).
          ;; Atm we assume only CRS EPSG:3067 is used.
          ;; Always give feature the same id if names match, so that search filters continue to work after reloading shp file
          ;; with same feature names
          ;;
          ;; Cheatsheet to understand naming conventions in Geotools (from http://docs.geotools.org/latest/userguide/tutorial/feature/csv2shp.html):
          ;; Java  | GeoSpatial
          ;; --------------
          ;; Object  Feature
          ;; Class   FeatureType
          ;; Field   Attribute
          (let [feature-type (DataUtilities/createSubType (.getType feature) nil DefaultGeographicCRS/WGS84)
                feature-name (or (some->
                                   (or ; try to get name of feature from these properties
                                     (.getProperty feature "nimi")
                                     (.getProperty feature "NIMI")
                                     (.getProperty feature "Nimi")
                                     (.getProperty feature "name")
                                     (.getProperty feature "NAME")
                                     (.getProperty feature "Name")
                                     (.getProperty feature "id")
                                     (.getProperty feature "ID")
                                     (.getProperty feature "Id"))
                                   .getValue)
                                 (.getID feature))]
            (when feature-name
              (let [^String id          (if (contains? map-of-existing-areas feature-name)
                                          (get map-of-existing-areas feature-name)
                                          (mongo/create-id))
                    type-builder        (doto (SimpleFeatureTypeBuilder.) ; FeatureType builder, 'nimi' property
                                          (.init feature-type)     ; Init with existing subtyped feature (correct CRS, no attributes)
                                          (.add "nimi" (.getClass String))) ; Add the attribute we are interested in
                    new-feature-type    (.buildFeatureType type-builder)
                    builder             (SimpleFeatureBuilder. new-feature-type) ; new FeatureBuilder with changed crs and new attribute
                    builder             (doto builder
                                          (.init feature)               ; init builder with original feature
                                          (.set "nimi" feature-name))       ; ensure 'nimi' property exists
                    transformed-feature (.buildFeature builder id)]
                (.add list transformed-feature)))))
        (when (.hasNext iterator)
          (recur (.next iterator))))
      (DataUtilities/collection list))))

(defn- transform-coordinates-to-wgs84
  "Convert feature coordinates in collection to WGS84 which is supported by mongo 2dsphere index"
  [^FeatureCollection collection]
  (let [schema    (.getSchema collection)
        crs       (.getCoordinateReferenceSystem schema)
        transform (CRS/findMathTransform crs DefaultGeographicCRS/WGS84 true)]
    (with-open [iterator (.features collection)]
      (let [feature (when (.hasNext iterator)
                      (.next iterator))
            list    (ArrayList.)]
        (loop [^SimpleFeature feature (cast SimpleFeature feature)]
          (when feature
            (when-let [^Geometry geometry (.getDefaultGeometry feature)]
              (let [transformed-geometry (JTS/transform geometry transform)]
                (.setDefaultGeometry feature transformed-geometry)
                (.add list feature))))
          (when (.hasNext iterator)
            (recur (.next iterator))))
        (DataUtilities/collection list)))))

(defn parse-areas
  "If `org-id` is given, the existing organization areas are taken into account. `tempfile` contains
  the zipped shapefile."
  ([^File tempfile]
   (parse-areas tempfile nil))
  ([^File tempfile org-id]
   (let [^File tmpdir (fs/temp-dir "areas")]
     (try
       (let [^File target-dir (util/unzip (.getPath tempfile) tmpdir)
             ^File shape-file (first (util/get-files-by-regex (.getPath target-dir) #"^.+\.shp$"))
             data-store       (FileDataStoreFinder/getDataStore shape-file)]
         (try
           (let [^SimpleFeatureCollection
                 new-collection       (some-> data-store
                                              .getFeatureSource
                                              .getFeatures
                                              ((partial transform-crs-to-wgs84 org-id nil)))
                 _                    (when (-> new-collection .getCount zero?)
                                        (fail! :error.no-features))
                 precision            13 ; FeatureJSON shows only 4 decimals by default
                 areas                (-> (FeatureJSON. (GeometryJSON. precision))
                                          (.toString new-collection)
                                          (json/decode true)
                                          (update :features (partial filter
                                                                     (comp some? :type :geometry))))
                 ensured-areas        (geo/ensure-features areas)
                 ^SimpleFeatureCollection
                 new-collection-wgs84 (some-> data-store
                                              .getFeatureSource
                                              .getFeatures
                                              transform-coordinates-to-wgs84
                                              ((partial transform-crs-to-wgs84 org-id ensured-areas)))
                 areas-wgs84          (json/decode (.toString (FeatureJSON. (GeometryJSON. precision)) new-collection-wgs84) true)
                 ensured-areas-wgs84  (geo/ensure-features areas-wgs84)]
             (when (geo/validate-features (:features ensured-areas))
               (fail! :error.coordinates-not-epsg3067))
             {:areas       ensured-areas
              :areas-wgs84 ensured-areas-wgs84})
           (finally
             (.dispose data-store))))
       (finally
         (fs/delete-dir tmpdir))))))

(defn command->fileinfo
  [{{[{:keys [filename size]}] :files} :data created :created}]
  (let [filename (mime/sanitize-filename filename)]
    (sc/validate FileInfo {:filename    (mime/sanitize-filename filename)
                           :contentType (mime/mime-type filename)
                           :size        size
                           :created     created})))

(defnk parse-and-respond
  "Shapefile convenience function. Options keys [optional]:

   `:file-info`: `FileInfo` for the uploaded file.
   `:tempfile`: Contains the file contents.
   `:respond-fn`: Function that gets `parse-areas` result as arguments and should return the action response
    map.
   [`:org-id`]: If given, the areas of the organization are taken into account (default nil)
   [`:lang`] Language to when localizing error messages (default `:fi`)

 Returns JSON HTTP response."
  [file-info tempfile respond-fn
   {org-id nil} {lang :fi}]
  (->> (try+
         (when-not (= (:contentType file-info) "application/zip")
           (fail! (i18n/localize lang :error.illegal-shapefile)))
         (respond-fn (parse-areas tempfile org-id))
         (catch [:sade.core/type :sade.core/fail] {:keys [text] :as all}
           (error "Failed to parse shapefile" text)
           {:ok false :text text})
         (catch Throwable t
           (error "Failed to parse shapefile" t)
           {:ok false :text (.getMessage t)}))
       (resp/json)
       (resp/status 200)))


(defn- coordinates->string [coords]
  (postwalk (fn [x]
              (cond
                (number? x) x
                (number? (first x)) (ss/join " " x)
                (string? (first x)) (format "(%s)" (ss/join ", " x))))
            coords))

(defn areas->drawings [{:keys [areas areas-wgs84]}]
  (let [features       (:features areas)
        features-wgs84 (:features areas-wgs84)]
    (for [i    (range (count features))
          :let [{:keys [geometry] :as feat} (nth features i)
                feat-wgs84 (nth features-wgs84 i)]]
      (sc/validate Drawing {:geometry-wgs84 (:geometry feat-wgs84)
                            :id             (str "feature-" (:id feat))
                            :name           (some-> feat :properties :nimi str)
                            :geometry       (str (ss/upper-case (:type geometry))
                                                 (coordinates->string (:coordinates geometry)))}))))
