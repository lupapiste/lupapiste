(ns lupapalvelu.gis.gis-api
  "Common APIs for GIS and maps"
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.action :as action :refer [defquery]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.proxy-services :as proxy]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as s]
            [taoensso.timbre :refer [error]]))

(def PROJECTION "EPSG:3067")
(def TTL (* 4 60 60 1000)) ; 4 hours

(s/defschema Layer
  {:title                        s/Str ; Shown to the user
   (s/optional-key :subtitle)    s/Str ; Ditto
   :id                           s/Str
   :url                          s/Str
   (s/optional-key :layer)       s/Str
   (s/optional-key :matrixSet)   s/Str
   :format                       s/Str
   (s/optional-key :opacity)     s/Num
   :projection                   (s/enum PROJECTION)
   (s/optional-key :resolutions) [s/Num]
   :protocol                     s/Str})

(defn nls-base-layers [lang]
  [{:title       (i18n/localize lang :map.layer.taustakartta)
    :id          "mml-taustakartta"
    :url         (str (env/value :map :proxyserver-wmts) "/maasto")
    :layer       "taustakartta"
    :matrixSet   "ETRS-TM35FIN"
    :format      "image/png"
    :opacity     1.0
    :projection  PROJECTION
    :resolutions [8192 4096 2048 1024 512 256 128 64 32 16 8 4 2 1 0.5]
    :protocol    "WMTS"}
   {:title       (i18n/localize lang :map.layer.kiinteistojaotus)
    :id          "mml-kiinteistojaotus"
    :url         (str (env/value :map :proxyserver-wmts) "/kiinteisto")
    :layer       "kiinteistojaotus"
    :matrixSet   "ETRS-TM35FIN"
    :format      "image/png"
    :opacity     1.0
    :projection  PROJECTION
    :resolutions [4 2 1 0.5]
    :protocol    "WMTS"}
   {:title       (i18n/localize lang :map.layer.kiinteistotunnukset)
    :id          "mml-kiinteistotunnukset"
    :url         (str (env/value :map :proxyserver-wmts) "/kiinteisto")
    :layer       "kiinteistotunnukset"
    :matrixSet   "ETRS-TM35FIN"
    :format      "image/png"
    :opacity     1.0
    :projection  PROJECTION
    :resolutions [4 2 1 0.5]
    :protocol    "WMTS"}])

(assert (s/validate [Layer] (nls-base-layers :fi)) "Layers do not conform to schema")

(defn by-lang
  "Get localization from `m` for `lang` using `default-lang` as a fallback language.
  (by-lang {:fi 'moi' :en 'hi'} 'en' 'fi') -> 'hi
  (by-lang {:fi 'moi' :en 'hi'} :sv :fi) -> 'moi'"
  [m lang default-lang]
  (let [s (get m (util/make-kw lang))]
    (if (ss/blank? s)
      (get m (util/make-kw default-lang))
      s)))

(defn process-layer [lang {:keys [name subtitle wmsName wmsUrl] :as layer}]
  (try
    (s/validate Layer (util/filter-map-by-val ss/not-blank?
                                              {:id         wmsName
                                               :url        wmsUrl
                                               :title      (by-lang name lang :fi)
                                               :subtitle   (by-lang subtitle lang :fi)
                                               :format     "image/png"
                                               :projection PROJECTION
                                               :protocol   "WMS"}))
    (catch Exception _
      (error "Bad layer:" layer))))

(defn process-layers [lang layers-from-geoserver]
  (->> layers-from-geoserver
       (map (partial process-layer lang))
       (remove nil?)))

(defn- fixed-layer-group-layer?
  "Hardcoded predicate to remove some of the WMS layers away"
  [{:keys [id]}]
  (or (re-find #"^HY\." id) ; replaced by 'Rantaviiva' layer-group
      (re-find #"^PS\." id) ; replaced by 'SuojellutAlueet' layer group
      (re-find #".*tulva.*" id) ; replaced by 'Tulva-alueet' layer group
      ))


(defn shared-layers
  "Returns 'Non-municipality' layers from given Geoserver layers and adds some fixed layer groups."
  [layers lang]
  (->> layers
       (process-layers lang)
       (remove #(re-find #"^\d\d\d" (:id %))) ; municipality layers are fetched with other function
       (remove fixed-layer-group-layer?)
       (concat [(process-layer lang {:wmsName "SuojellutAlueet" :name {:fi "Suojellut alueet"} :wmsUrl "/proxy/wms"})
                (process-layer lang {:wmsName "Tulva-alueet" :name {:fi "Tulva-alueet"} :wmsUrl "/proxy/wms"})
                (process-layer lang {:wmsName "Rantaviiva" :name {:fi "Rantaviivat"} :wmsUrl "/proxy/wms"})])))


(defn municipality-layers [geoserver-layers lang municipality]
  (->> municipality
       (proxy/combine-municipality-layers geoserver-layers)
       (process-layers lang)))


(def cached-capabilities (if (env/feature? :no-cache)
                           proxy/our-wms-layers
                           (memo/ttl proxy/our-wms-layers :ttl/threshold TTL)))


(defn geoserver-layers [lang municipality]
  (try
    (let [cap-layers (cached-capabilities)]
      (concat
        (shared-layers cap-layers lang)
        (municipality-layers cap-layers lang municipality)))
    (catch Exception e
      (error e "error.integration - exception while fetching Geoserver + municipality layers")
      [])))


;; -----------------------
;; Queries
;; -----------------------

(defquery map-config
  {:description      "Initial base configuration for map"
   :permissions      [{:required [:global/map-config]}]
   :parameters       [lang]
   :input-validators [(partial action/non-blank-parameters [:lang])]}
  [{{:keys [lang]} :keys}]
  (ok :base-layers (nls-base-layers lang)))


(defquery application-gis-data
  {:description      "Application related GIS data query"
   :permissions      [{:required [:application/read]}]
   :parameters       [id lang]
   :input-validators [(partial action/non-blank-parameters [:lang])]}
  [{:keys [application] :as command}]
  (if-let [{:keys [drawings] :as application} application]
    (ok :application (-> application
                       (select-keys [:location :location-wgs84])
                       (assoc :drawings (or drawings [])))
        :layers (concat (nls-base-layers lang)
                        (geoserver-layers lang (:municipality application))))
    (fail :error.application-not-found)))

(defquery plan-document-infos
  {:description      "Plan documents (kaavamääräys) information for the given coordinates. The municipality is
  taken from the application. Thus, the results are not necessarily exact for the coordinates outside of the
  municipality."
   :permissions      [{:required [:application/read]}]
   :parameters       [:id x y]
   :input-validators [(partial action/non-blank-parameters [:id])
                      coord/validate-x
                      coord/validate-y]}
  [{{:keys [municipality]} :application data :data}]
  (if-let [body (or (proxy/plan-infos-by-point-body :liiteri x y municipality "plan-info")
                    (proxy/plan-infos-by-point-body :liiteri x y "liiteri" "plan-info")
                    (proxy/plan-infos-by-point-body :trimble x y municipality)
                    (proxy/plan-infos-by-point-body :general x y))]
    (ok :infos body)
    (fail :error.not-found)))
