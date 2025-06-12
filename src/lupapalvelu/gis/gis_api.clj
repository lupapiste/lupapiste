(ns lupapalvelu.gis.gis-api
  "Common APIs for GIS and maps"
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.action :as action :refer [defcommand defquery defraw]]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.gis.arcgis.arcgis-core :as arcgis]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :refer [error warn]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.proxy-services :as proxy]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def PROJECTION "EPSG:3067")
(def TTL (* 4 60 60 1000)) ; 4 hours

(sc/defschema Layer
  {:title                         sc/Str ; Shown to the user
   (sc/optional-key :subtitle)    sc/Str ; Ditto
   :id                            sc/Str
   :url                           sc/Str
   (sc/optional-key :layer)       sc/Str
   (sc/optional-key :matrixSet)   sc/Str
   :format                        sc/Str
   (sc/optional-key :opacity)     sc/Num
   :projection                    (sc/enum PROJECTION)
   (sc/optional-key :resolutions) [sc/Num]
   :protocol                      sc/Str})

(def ^:private layer-checker (sc/checker Layer))
(def ^:private layers-checker (sc/checker [Layer]))

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

(assert (nil? (layers-checker (nls-base-layers :fi))) "Layers do not conform to schema")

(defn by-lang
  "Get localization from `m` for `lang` using `default-lang` as a fallback language.
  (by-lang {:fi 'moi' :en 'hi'} 'en' 'fi') -> 'hi
  (by-lang {:fi 'moi' :en 'hi'} :sv :fi) -> 'moi'"
  [m lang default-lang]
  (let [s (get m (util/make-kw lang))]
    (if (ss/blank? s)
      (get m (util/make-kw default-lang))
      s)))

(defn process-layer [lang {:keys [name subtitle wmsName wmsUrl]}]
  (let [layer (util/filter-map-by-val ss/not-blank?
                                      {:id         wmsName
                                       :url        wmsUrl
                                       :title      (by-lang name lang :fi)
                                       :subtitle   (by-lang subtitle lang :fi)
                                       :format     "image/png"
                                       :projection PROJECTION
                                       :protocol   "WMS"})]
    (if-let [errors (layer-checker layer)]
      (warn "Bad layer:" layer errors)
      layer)))

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
   :input-validators [(partial action/non-blank-parameters [:id :lang])]}
  [{:keys [application organization] :as _command}]
  (if-let [{:keys [drawings] :as application} application]
    (ok :application (-> application
                       (select-keys [:location :location-wgs84])
                       (assoc :drawings (or drawings [])))
        :layers (reduce into
                        [(nls-base-layers lang)
                         (geoserver-layers lang (:municipality application))
                         (arcgis/arcgis-layers lang (force organization) id)]))
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

(defquery arcgis-feature-by-point
  {:description "Returns the property from the configured Esri ArcGIS service for the given coordinates.
  Only used when adding a property to the list on the map"
   :permissions      [{:required [:application/edit]}]
   :parameters       [:id x y]
   :input-validators [(partial action/non-blank-parameters [:id])
                      coord/validate-x
                      coord/validate-y]
  :pre-checks        [(arcgis/check-arcgis-enabled)]}
  [{:keys [organization system]}]
  (if-let [config (arcgis/arcgis-config organization)]
    (if-let [feature (arcgis/feature-by-point! x y config system)]
      (ok :feature feature)
      (fail :error.not-found))
    (fail :error.invalid-configuration)))

(defquery arcgis-feature-geometry
  {:description      "Returns the geometry for the given feature from the configured ArcGIS service"
   :permissions      [{:required [:application/read]}]
   :parameters       [:id feature-id]
   :input-validators [(partial action/non-blank-parameters [:id :feature-id])]
   :pre-checks       [(arcgis/check-arcgis-enabled)]}
  [{:keys [organization system]}]
  (if-let [config (arcgis/arcgis-config organization)]
    (if-let [geometries (arcgis/feature-geometries! feature-id config system)]
      (ok :geometries (map #(hash-map :wkt %) geometries))
      (fail :error.not-found))
    (fail :error.invalid-configuration)))

(defraw arcgis-layer-tile
  {:description          "Returns a graphical tile from the configured ArcGIS service"
   :permissions          [{:required [:application/read]}]
   :parameters           [:id layer BBOX TRANSPARENT WIDTH HEIGHT] ; these all-caps ones come from the map component
   :input-validators     [(partial action/non-blank-parameters [:id :layer :BBOX :TRANSPARENT :WIDTH :HEIGHT])]
   :pre-checks           [(arcgis/check-arcgis-enabled)]
   :allow-impersonation? true}
  [{:keys [organization system]}]
  (if-let [config (arcgis/arcgis-config organization)]
    (if-let [layer-config (some-> config :layers (get (util/->int layer nil)))]
      (arcgis/arcgis-layer-tile! system
                                 (:server config)
                                 layer-config
                                 BBOX
                                 TRANSPARENT
                                 WIDTH
                                 HEIGHT)
      (fail :error.invalid-value))
    (fail :error.invalid-configuration)))

(defquery arcgis-config
  {:description      "Returns the current ArcGIS map integration configuration for the given organization"
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :user-roles       #{:admin}}
  [_]
  (->> (org/get-organization organizationId [:arcgis-config]) ; Removes password and crypto-iv
       :arcgis-config
       (ok :arcgis-config)))

(defn- update-arcgis-credentials
  "See `save-arcgis-config` description below"
  [{{old-username :username :as old-server} :server :as _old-config}
   {{new-username :username new-password :password :as new-server} :server :as new-config}]
  (update new-config :server merge
          (cond
            (ss/not-blank? new-password)     (bs-sec/encode-credentials new-username new-password)
            (not= old-username new-username) (bs-sec/encode-credentials new-username (bs-sec/decode-password old-server))
            :else                            (select-keys old-server [:username :password :crypto-iv]))))

(def ^:private valid-arcgis-config! (sc/validator arcgis/ArcGisConfig))

(defcommand save-arcgis-config
  {:description      "Stores the given ArcGIS map integration configuration to the organization.
  The password is only updated if a new value has been explicitly given.
  If a new username is given but no password, the old password is used to generate new credentials."
   :parameters       [organizationId config]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :config         (st/assoc-in arcgis/ArcGisConfig [:server :password] sc/Str)}]
   :pre-checks       [(org/organization-flag-pre-check :arcgis-map-enabled true)]
   :user-roles       #{:admin}}
  [_]
  (let [old-config (arcgis/arcgis-config organizationId)
        new-config (->> config (update-arcgis-credentials old-config) util/strip-blanks)]
    (valid-arcgis-config! new-config)
    (if (pos? (mongo/update-by-query :organizations {:_id organizationId} {$set {:arcgis-config new-config}}))
      (ok)
      (fail :error.not-found))))
