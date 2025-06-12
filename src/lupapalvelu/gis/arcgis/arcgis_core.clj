(ns lupapalvelu.gis.arcgis.arcgis-core
  "Namespace for fetching property information from ArcGIS services.
  Initially made for Esri ArcGIS integration for MATTI / dmCity / Tiera City"
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [lupapalvelu.gis.arcgis.geometry :as arcgis-geom]
            [lupapalvelu.gis.arcgis.tokens :as arcgis-tokens]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.json :as json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]
            [sade.core :refer [fail]]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(sc/defschema ArcGisService
  "These form the path to the configured service.
  Some of them may not be necessary, depending on the ArcGIS implementation"
  {(sc/optional-key :folder)       ssc/NonBlankStr
   (sc/optional-key :service)      ssc/NonBlankStr
   (sc/optional-key :service-type) ssc/NonBlankStr})

(sc/defschema ArcGisConfig
  "Schema for ArcGIS configuration.
  Includes the general server configurations, the point search configuration
  and an optional config for map layers to be displayed"
  {:server                   {(sc/optional-key :username)  ssc/NonBlankStr
                              (sc/optional-key :password)  ssc/NonBlankStr
                              (sc/optional-key :crypto-iv) ssc/NonBlankStr
                              :service-url                 ssc/NonBlankStr
                              (sc/optional-key :token-url) ssc/NonBlankStr}
   :search                   (st/merge ArcGisService
                                       {:layer-id sc/Int
                                        :fields   {:feature-id                    ssc/NonBlankStr
                                                   (sc/optional-key :property-id) ssc/NonBlankStr}})
   (sc/optional-key :layers) [(st/merge ArcGisService
                                        {:layer-ids [sc/Int]
                                         :title {(apply sc/enum i18n/supported-langs) ssc/NonBlankStr}})]})


(defn arcgis-config
  "Get the config directly from the database to access password and crypto-iv fields for token generation"
  [org-or-id]
  (let [org-id (if (string? org-or-id) org-or-id (-> org-or-id force :id))]
    (:arcgis-config (mongo/by-id :organizations org-id [:arcgis-config]))))

(defn check-arcgis-enabled
  "Returns true if ArcGIS integration can be used for the application"
  []
  (fn [{:keys [organization]}]
    (when-not (->> (force organization)
                   ((juxt :arcgis-map-enabled :arcgis-config))
                   (every? boolean))
      (fail :error.feature-not-enabled))))

(defn loc-layer-title [lang title]
  (get title
       (keyword lang)
       (some #(get title %) i18n/supported-langs)))

(defn- point-query [x y]
  {:geometryType "esriGeometryPoint"
   :geometry     (str x ", " y)})

(defn- id-query [key value]
  {:where (format "%s = '%s'" (name key) value)})

(defn- query-url
  "Returns URL for querying the service for data"
  [{:keys [server search]}]
  (str (get server :service-url)
       (->> [:folder :service :service-type :layer-id]
            (keep #(get search %))
            (ss/join "/"))
       "/query?"))

(defn- validate-body!
  "Throws an error if the ArcGIS response body contains an error"
  [resp-body url params]
  (when-let [error (-> resp-body :error :message)]
    (throw (ex-info error {:url           url
                           :params        params
                           :response-body resp-body}))))

(defn feature-by-point!
  "Returns the attributes of the first feature at the given point from ArcGIS"
  [x y {:keys [server search] :as config} system]
  (let [referer   (components/system->host system)
        url       (query-url config)
        token-map (arcgis-tokens/token server referer)
        params    (merge {:returnGeometry false
                          :inSR           "3067"
                          :outSR          "3067"
                          :outFields      "*"
                          :f              "json"}
                         (point-query x y)
                         token-map)
        resp      (http/get url {:query-params params
                                 :headers      {"referer" referer}})
        body      (-> resp :body (json/decode true))
        field-map (-> search :fields set/map-invert walk/keywordize-keys)]
    (validate-body! body url params)
    (some-> body
            :features
            first
            :attributes
            (set/rename-keys field-map)
            (select-keys (vals field-map)))))

(defn feature-geometries!
  "Returns the geometries of the feature in WKT format"
  [feature-id {:keys [server search] :as config} system]
  (let [referer   (components/system->host system)
        url       (query-url config)
        token-map (arcgis-tokens/token server referer)
        params    (merge {:returnGeometry    true
                          :inSR              "3067"
                          :outSR             "3067"
                          :outFields         "*"
                          :f                 "json"
                          :resultRecordCount 50}
                         (-> search :fields :feature-id (id-query feature-id))
                         token-map)
        resp      (http/get url {:query-params params
                                 :headers      {"referer" referer}})
        body      (-> resp :body (json/decode true))]
    (validate-body! body url params)
    (some->> (:features body)
             (map (comp arcgis-geom/esri->wkt :geometry))
             (reduce into))))

(defn arcgis-layers
  "Returns the configured ArcGIS map layers to be displayed in the map component"
  [lang organization application-id]
  (some->> (when (:arcgis-map-enabled organization)
             (:arcgis-config organization))
           :layers
           (map-indexed (fn [idx {:keys [title]}]
                          {:title       (loc-layer-title lang title)
                           :id          (str "arcgis-" idx)
                           :url         (format "/api/raw/arcgis-layer-tile?id=%s&layer=%s"
                                                application-id
                                                idx)
                           :format      "image/png"
                           :projection  "EPSG:3067"
                           :protocol    "WMS"}))))

(defn- tile-url
  "Returns the URL to fetch the graphic for a map layer tile"
  [server-config layer-config]
  (str (get server-config :service-url)
       (->> [:folder :service :service-type]
            (keep #(get layer-config %))
            (ss/join "/"))
       "/export"))

(defn arcgis-layer-tile!
  "Returns a single tile from the configured ArcGIS service.
  bbox-str is the bounding box for the displayed area on the map as a comma-separated string
  width and height are the desired dimensions of the image in pixels"
  [system server-config layer-config bbox-str transparent width height]
  (let [referer   (components/system->host system)
        url       (tile-url server-config layer-config)
        token-map (arcgis-tokens/token server-config referer)
        params    (merge {"dpi"         "96"
                          "transparent" (or transparent "true")
                          "format"      "png32"
                          "layers"      (->> (:layer-ids layer-config)
                                             (ss/join ",")
                                             (str "show:"))
                          "bbox"        bbox-str
                          "bboxSR"      "3067"
                          "imageSR"     "3067"
                          "size"        (str width "," height)
                          "f"           "image"}
                          token-map)]
    (-> (http/get url {:query-params params
                       :headers      {"referer"         referer
                                      "accept"          "image/apng"
                                      "accept-encoding" "gzip, deflate, br"}
                       :as           :stream})
        http/secure-headers
        (dissoc :cookies))))
