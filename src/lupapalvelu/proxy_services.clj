(ns lupapalvelu.proxy-services
  (:require [clojure.data.zip.xml :refer :all]
            [clojure.xml :as xml]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as user]
            [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [monger.operators :refer [$exists]]
            [noir.response :as resp]
            [sade.coordinate :as coord]
            [sade.env :as env]
            [sade.http :as http]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.municipality :as muni]
            [lupapalvelu.find-address :as find-address]
            [lupapalvelu.wfs :as wfs]))


(defn- municipality-index-for [lang]
  (map (fn [code] [(ss/lower-case (i18n/localize lang :municipality code)) code])
       muni/municipality-codes))

(def municipality-index
    (delay (reduce (fn [m lang] (assoc m lang (municipality-index-for lang))) {} i18n/supported-langs)))

(defn municipality-codes [municipality-name-starts lang]
  (let [index (get @municipality-index (keyword lang))
        n (ss/lower-case (ss/trim municipality-name-starts))]
    (when (not (ss/blank? n))
      (->> (filter #(ss/starts-with (first %) n) index)
           (map second)))))

(defn- trim [s]
  (when-not (ss/blank? s) (ss/trim s)))

(defn- parse-address [query]
  (let [[[_ street number city]] (re-seq #"([^,\d]+)\s*(\d+)?\s*(?:,\s*(.+))?" query)
        street (trim street)
        city (trim city)]
    [street number city]))

(defn- respond-nls-address-suggestions [features]
  (if features
    (let [top10-features (take 10 features)]
      (resp/json {:suggestions (map wfs/feature-to-simple-address-string top10-features)
                  :data (map wfs/feature-to-address top10-features)}))
    (resp/status 503 "Service temporarily unavailable")))

(defn get-addresses-proxy [{{:keys [query lang]} :params}]
  (let [[street number city] (parse-address query)
        nls-query (future (find-address/get-addresses street number city))
        muni-codes (municipality-codes city lang)
        muni-code  (first muni-codes)
        endpoint (when (= 1 (count muni-codes)) (org/municipality-address-endpoint muni-code))]
    (if endpoint
      (if-let [address-from-muni (->> (find-address/get-addresses-from-municipality street number endpoint)
                                        (map (partial wfs/krysp-to-address-details (or lang "fi"))))]
        (do
          (future-cancel nls-query)
          (resp/json {:suggestions (map (fn [{:keys [street number]}] (str street \space number ", " (i18n/localize lang :municipality muni-code))) address-from-muni)
                     :data (map (fn [m] (-> m (assoc :location (select-keys m [:x :y])) (dissoc :x :y))) address-from-muni)}))
        (do
          (debug "Fallback to NSL address data - no address found from " (i18n/localize :fi :municipality muni-code))
          (respond-nls-address-suggestions @nls-query)))
      (respond-nls-address-suggestions @nls-query))))

(defn find-addresses-proxy [{{:keys [term lang]} :params}]
    (if (and (string? term) (string? lang) (not (ss/blank? term)))
      (let [normalized-term (ss/replace term #"\p{Punct}" " ")]
        (resp/json (or (find-address/search normalized-term lang) [])))
      (resp/status 400 "Missing query parameters")))

(defn point-by-property-id-proxy [request]
  (let [property-id (get (:params request) :property-id)
        features (wfs/location-info-by-property-id property-id)]
    (if features
      (resp/json {:data (map wfs/feature-to-position features)})
      (resp/status 503 "Service temporarily unavailable"))))

(defn area-by-property-id-proxy [{{property-id :property-id} :params :as request}]
  (if (and (string? property-id) (re-matches p/db-property-id-pattern property-id) )
    (let [features (wfs/location-info-by-property-id property-id)]
      (if features
        (resp/json {:data (map wfs/feature-to-area features)})
        (resp/status 503 "Service temporarily unavailable")))
    (resp/status 400 "Bad Request")))

(defn property-id-by-point-proxy [{{x :x y :y} :params}]
  (if (and (coord/valid-x? x) (coord/valid-y? y))
    (let [features (wfs/property-id-by-point x y)]
      (if features
        (resp/json (:kiinttunnus (wfs/feature-to-property-id (first features))))
        (resp/status 503 "Service temporarily unavailable")))
    (resp/status 400 "Bad Request")))

(defn municipality-by-point [x y]
  (let [url (str (env/value :geoserver :host) (env/value :geoserver :kunta))
        query {:query-params {:x x, :y y}
               :conn-timeout 10000, :socket-timeout 10000
               :as :json}]
    (try
      (when-let [municipality (get-in (http/get url query) [:body :kuntanumero])]
        (ss/zero-pad 3 municipality))
      (catch Exception e
        (error e (str "Unable to resolve municipality by " x \/ y))))))

(defn- respond-nls-address [lang features]
  (if (seq features)
    (resp/json (wfs/feature-to-address-details (or lang "fi") (first features)))
    (resp/status 503 "Service temporarily unavailable")))

(defn- distance [^double x1 ^double y1 ^double x2 ^double y2]
  {:pre [(and x1 x2 y1 y2)]}
  (Math/sqrt (+ (Math/pow (- x2 x1) 2) (Math/pow (- y2 y1) 2))))

(defn address-by-point-proxy [{{:keys [x y lang]} :params}]
  (if (and (coord/valid-x? x) (coord/valid-y? y))
    (let [nls-address-query  (future (wfs/address-by-point x y))
          municipality (municipality-by-point x y)
          x_d (util/->double x)
          y_d (util/->double y)]
      (if-let [endpoint (org/municipality-address-endpoint municipality)]
        (if-let [address-from-muni (->> (wfs/address-by-point-from-municipality x y endpoint)
                                     (map (partial wfs/krysp-to-address-details (or lang "fi")))
                                     (map (fn [{x2 :x y2 :y :as f}] (assoc f :distance (distance x_d y_d x2 y2))))
                                     (sort-by :distance)
                                     first)]
          (do
            (future-cancel nls-address-query)
            (resp/json address-from-muni))
          (respond-nls-address lang @nls-address-query))
        (respond-nls-address lang @nls-address-query)))
    (resp/status 400 "Bad Request")))

(def wdk-type-pattern #"^POINT|^LINESTRING|^POLYGON")

(defn property-info-by-wkt-proxy [request] ;example: wkt=POINT(404271+6693892)&radius=100
  (let [{wkt :wkt radius :radius :or {wkt ""}} (:params request)
        type (re-find wdk-type-pattern wkt)
        coords (ss/replace wkt wdk-type-pattern "")
        features (case type
                   "POINT" (let [[x y] (ss/split (first (re-find #"\d+(\.\d+)* \d+(\.\d+)*" coords)) #" ")]
                             (if-not (ss/numeric? radius)
                               (wfs/property-info-by-point x y)
                               (wfs/property-info-by-radius x y radius)))
                   "LINESTRING" (wfs/property-info-by-line (ss/split (ss/replace coords #"[\(\)]" "") #","))
                   "POLYGON" (let [outterring (first (ss/split coords #"\)" 1))] ;;; pudotetaan reiat pois
                               (wfs/property-info-by-polygon (ss/split (ss/replace outterring #"[\(\)]" "") #",")))
                   nil)]
    (if features
      (resp/json (map wfs/feature-to-property-info features))
      (resp/status 503 "Service temporarily unavailable"))))

(defn create-layer-object [layer-name]
  (let [layer-category (cond
                         (re-find #"^\d+_asemakaava$" layer-name) "asemakaava"
                         (re-find #"^\d+_kantakartta$" layer-name) "kantakartta"
                         :else "other")
        layer-id (case layer-category
                   "asemakaava" "101"
                   "kantakartta" "102"
                   layer-name)]
    {:wmsName layer-name
     :wmsUrl "/proxy/wms"
     :name (case layer-category
             "asemakaava" {:fi "Asemakaava (kunta)" :sv "Detaljplan (kommun)" :en "Detailed Plan (municipality)"}
             "kantakartta" {:fi "Kantakartta" :sv "Baskarta" :en "City map"}
             {:fi layer-name :sv layer-name :en layer-name})
     :subtitle (case layer-category
                 "asemakaava" {:fi "Kunnan palveluun toimittama ajantasa-asemakaava" :sv "Detaljplan (kommun)" :en "Detailed Plan (municipality)"}
                 "kantakartta" {:fi "" :sv "" :en ""}
                 {:fi "" :sv "" :en ""})
     :id layer-id
     :baseLayerId layer-id
     :isBaseLayer (or (= layer-category "asemakaava") (= layer-category "kantakartta"))}))

(defn municipality-layers
  "Examines evey organization belonging to the municipality and
  returns list of resolved map layers (layer objects):
  :base true if the layer is base layer (asemakaava or kantakartta)
  :id   WMS layer name
  :name Friendly name. Either given by the user or
        predefined (asemakaava or kantakartta)
  :org  Organization id"
  [municipality]
  (letfn [(annotate-org-layer [{{:keys [layers server]} :map-layers org-id :id}]
            (map #(assoc % :server (:url server) :org org-id) layers))
          (layer-slot-filled? [layer slot]
            (let [{:keys [id name base server]} slot]
              (or
               (and base (:base layer) (= name (:name layer)))
               (and (= id (:id layer)) (= server (:server layer))))))]
    (->> (org/get-organizations {:scope.municipality municipality
                                 :map-layers.layers {$exists true}})
         ;; Add server and org information to layers
         (map annotate-org-layer)
         ;; All layers into one list
         flatten
         ;; Remove layers without WMS layer information
         (remove #(ss/blank? (:id %)))
         ;; The final list will have each base layer (asemakaava,
         ;; kantakartta) and WMS layer only once.
         (reduce (fn [slots layer]
                   (if (some (partial layer-slot-filled? layer) slots)
                     slots
                     (cons layer slots))) [])
         (map #(select-keys % [:base :id :name :org])))))

(defn municipality-layer-objects
  "Resolves layers for the given municipality and returns a list of
  Oskari layer objects."
  [municipality]
  (let [layers (municipality-layers municipality)
        names-fn (fn [name]
                (let [path (str "auth-admin.municipality-maps." name)]
                  (->> i18n/languages
                       (map #(when (i18n/has-term? % path)
                               {% (i18n/localize % path)}))
                       (cons {:fi name :sv name :en name})
                       (apply merge))))
        layer-id-fn (fn [layer index]
                      (let [indexed (str "Lupapiste-" index)]
                        (if (:base layer)
                          (get {"asemakaava"  101
                                "kantakartta" 102}
                               (:name layer)
                               ;; Default index just in case. Should be never needed.
                               indexed)
                          indexed)))]
    (map-indexed (fn [index layer]
                   (let [{:keys [base id name org]} layer
                         layer-id (layer-id-fn layer index)]
                     {:wmsName (str "Lupapiste-" org ":" id)
                      :wmsUrl  "/proxy/wms"
                      :name (names-fn name)
                      :subtitle {:fi "" :sv "" :en ""}
                      :id layer-id
                      :baseLayerId layer-id
                      :isBaseLayer base})) layers)))

(defn wms-capabilities-proxy [request]
  (let [{municipality :municipality} (:params request)
        muni-layers (municipality-layer-objects municipality)
        muni-bases (->> muni-layers (map :id) (filter number?) set)
        capabilities (wfs/get-our-capabilities)
        layers (or (wfs/capabilities-to-layers capabilities) [])
        layers (if (nil? municipality)
          (map create-layer-object (map wfs/layer-to-name layers))
          (filter
            #(= (re-find #"^\d+" (:wmsName %)) municipality)
            (map create-layer-object (map wfs/layer-to-name layers)))
          )
        layers (filter (fn [{id :id}]
                         (not-any? #(= id %) muni-bases)) layers)
        result (concat layers muni-layers)]
    (if (not-empty result)
      (resp/json result)
      (resp/status 503 "Service temporarily unavailable"))))

;; The value of "municipality" is "liiteri" when searching from Liiteri and municipality code when searching from municipalities.
(defn plan-urls-by-point-proxy [{{:keys [x y municipality]} :params}]
  (let [municipality (trim municipality)]
    (if (and (coord/valid-x? x) (coord/valid-y? y) (or (= "liiteri" (ss/lower-case municipality)) (ss/numeric? municipality)))
      (let [response (wfs/plan-info-by-point x y municipality)
            k (keyword municipality)
            gfi-mapper (if-let [f-name (env/value :plan-info k :gfi-mapper)]
                         (resolve (symbol f-name))
                         wfs/gfi-to-features-sito)
            feature-mapper (if-let [f-name (env/value :plan-info k :feature-mapper)]
                             (resolve (symbol f-name))
                             wfs/feature-to-feature-info-sito)]
        (if response
          (resp/json (map feature-mapper (gfi-mapper response municipality)))
          (resp/status 503 "Service temporarily unavailable")))
      (resp/status 400 "Bad Request"))))

(defn general-plan-urls-by-point-proxy [{{x :x y :y} :params}]
  (if (and (coord/valid-x? x) (coord/valid-y? y))
    (if-let [response (wfs/general-plan-info-by-point x y)]
      (resp/json (map wfs/general-plan-feature-to-feature-info (wfs/gfi-to-general-plan-features response)))
      (resp/status 503 "Service temporarily unavailable"))
    (resp/status 400 "Bad Request")))

(defn organization-map-server
  [request]
  (if-let [org-id (user/authority-admins-organization-id (user/current-user request))]
    (if-let [m (-> org-id org/get-organization :map-layers :server)]
      (let [{:keys [url username password]} m
            encoding (get-in request [:headers "accept-encoding"])
            response (http/get url
                               {:query-params (:params request)
                                :headers {"accept-encoding" encoding}
                                :basic-auth [username password]
                                :as :stream})]
        ;; The same precautions as in secure
        (if response
          (update-in response [:headers] dissoc "set-cookie" "server")
          (resp/status 503 "Service temporarily unavailable")))
      (resp/status 400 "Bad Request"))
    (resp/status 401 "Unauthorized")))
;
; Utils:
;

(defn- secure
  "Takes a service function as an argument and returns a proxy function that invokes the original
  function. Proxy function returns what ever the service function returns, excluding some unsafe
  stuff. At the moment strips the 'Set-Cookie' headers."
  [f service]
  (fn [request]
    (let [response (f request service)]
      (update-in response [:headers] dissoc "set-cookie" "server"))))

(defn- cache [max-age-in-s f]
  (let [cache-control {"Cache-Control" (str "public, max-age=" max-age-in-s)}]
    (fn [request]
      (let [response (f request)]
        (update-in response [:headers] merge cache-control)))))

;;
;; Proxy services by name:
;;

(def services {"nls" (cache (* 3 60 60 24) (secure wfs/raster-images "nls"))
               "wms" (cache (* 3 60 60 24) (secure #(wfs/raster-images %1 %2 org/query-organization-map-server) "wms" ))
               "wmts/maasto" (cache (* 3 60 60 24) (secure wfs/raster-images "wmts"))
               "wmts/kiinteisto" (cache (* 3 60 60 24) (secure wfs/raster-images "wmts"))
               "point-by-property-id" point-by-property-id-proxy
               "area-by-property-id" area-by-property-id-proxy
               "property-id-by-point" property-id-by-point-proxy
               "address-by-point" address-by-point-proxy
               "find-address" find-addresses-proxy
               "get-address" get-addresses-proxy
               "property-info-by-wkt" property-info-by-wkt-proxy
               "wmscap" wms-capabilities-proxy
               "plan-urls-by-point" plan-urls-by-point-proxy
               "general-plan-urls-by-point" general-plan-urls-by-point-proxy
               "plandocument" (cache (* 3 60 60 24) (secure wfs/raster-images "plandocument"))
               "organization-map-server" organization-map-server})
