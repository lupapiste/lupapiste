(ns lupapalvelu.proxy-services
  (:require [clojure.data.zip.xml :refer :all]
            [taoensso.timbre :refer [debug debugf info warn error errorf]]
            [monger.operators :refer [$exists]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.coordinate :as coord]
            [sade.env :as env]
            [sade.http :as http]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [lupapalvelu.find-address :as find-address]
            [lupapalvelu.property :as prop]
            [lupapalvelu.property-location :as plocation]
            [lupapalvelu.user :as usr]
            [lupapalvelu.wfs :as wfs]))

(defn- trim [s]
  (when-not (ss/blank? s) (ss/trim s)))

(defn- parse-address [query]
  (let [[[_ street number city]] (re-seq #"([^,\d]+)\s*(\d+)?\s*(?:,\s*(.+))?" query)
        street (trim street)
        city (trim city)]
    [street number city]))

(defn- address->suggestion
  [{:keys [street number name]}]
  (format "%s %s, %s" street number (:fi name)))

(defn- respond-nls-address-suggestions [addresses]
  (let [{:keys [status data]} (::error addresses)]
    (case status
      :error (if (= :mml.geocoding/timeout data)
               (resp/status 503 "Service temporarily unavailable")
               (resp/status data "Request failed"))
      (let [top10-addresses (take 10 addresses)]
        (resp/json (if (empty? top10-addresses)
                     {:suggestions [] :data []}
                     {:suggestions (map address->suggestion top10-addresses)
                      :data        top10-addresses}))))))

(defn get-addresses-proxy [{{:keys [query lang]} :params}]
  ;; FIXME it seems that frontend is only interested in first item in `:data` key, and does not care about :suggestions
  ;; TODO: Can we optimize query: as frontend gets suggestions from `find-address`, is there a unique ID for selected address available?
  ;; If unique ID is available, we could just get that address with much more efficient query.
  ;; It also seems unnecessary to parse address here again from plain string, because the data frontend receives in previous step
  ;; is already partitioned to street/number/city.
  (if (ss/not-blank? query)
    (let [lang                 (or lang "fi")
          [street number city] (parse-address query)
          nls-query            (future (try
                                         (find-address/get-addresses lang street number city)
                                         (catch Exception e
                                           {::error (ex-data e)})))
          muni-codes           (find-address/municipality-codes city)
          muni-code            (first muni-codes)
          endpoint             (when (= 1 (count muni-codes)) (org/municipality-address-endpoint muni-code))]
      (if endpoint
        (if-let [address-from-muni (->> (find-address/get-addresses-from-municipality street number endpoint)
                                        (map (partial wfs/krysp-to-address-details lang))
                                        seq)]
          (do
            (future-cancel nls-query)
            (resp/json {:suggestions (map (fn [{:keys [street number]}] (str street \space number ", " (i18n/localize lang :municipality muni-code))) address-from-muni)
                        :data        (map (fn [m]
                                            (-> m
                                                (assoc :location (select-keys m [:x :y])
                                                       :name {:fi (i18n/localize :fi :municipality muni-code)
                                                              :sv (i18n/localize :sv :municipality muni-code)})
                                                (dissoc :x :y)))
                                          address-from-muni)}))
          (do
            (debug "Fallback to NSL address data - no address found from " (i18n/localize :fi :municipality muni-code))
            (respond-nls-address-suggestions @nls-query)))
        (respond-nls-address-suggestions @nls-query)))
    (resp/json {:suggestions [], :data []})))

(defn find-addresses-proxy [{{:keys [term lang]} :params}]
    (if (and (string? term) (string? lang) (not (ss/blank? term)))
      (let [normalized-term (ss/replace term #"\p{Punct}&&[-]" " ")]
        (resp/json (or (find-address/search normalized-term lang) [])))
      (resp/status 400 "Missing query parameters")))

(defn point-by-property-id-proxy [{{property-id :property-id} :params}]
  (if (and (string? property-id) (re-matches p/db-property-id-pattern property-id))
    (try
      (let [features (plocation/property-lots-info property-id)]
        (if features
          (resp/json {:data (map #(select-keys % [:x :y]) features)})
          (resp/status 404 "Not found")))
      (catch Exception e
        (error e "error in point-by-property-id-proxy" (.getMessage e))
        (resp/status 503 "Service temporarily unavailable")))
    (resp/status 400 "Bad Request")))

(defn location-by-property-id-proxy [{{property-id :property-id} :params}]
  (if (and (string? property-id) (re-matches p/db-property-id-pattern property-id))
    (try
      (let [point-features (future (plocation/property-lots-info property-id))
            location (future (prop/location-by-property-id-from-wfs property-id))
            backup-municipality (p/municipality-id-by-property-id property-id)
            found-points @point-features
            result-data (merge (select-keys (first found-points) [:x :y])
                               {:propertyId property-id
                                :lots found-points}
                               (or @location
                                   {:municipality backup-municipality
                                    :name {:fi (i18n/localize :fi "municipality" backup-municipality)
                                           :sv (i18n/localize :sv "municipality" backup-municipality)}}))]
        (if (seq found-points)
          (resp/json result-data)
          (resp/status 404 "Not found")))
      (catch Exception e
        (error e "error in location-by-property-id-proxy" (.getMessage e))
        (resp/status 503 "Service temporarily unavailable")))
    (resp/status 400 "Bad Request")))

(defn lots-by-property-id-proxy [{{property-id :property-id} :params}]
  (if (and (string? property-id) (re-matches p/db-property-id-pattern property-id))
    (resp/json (mapv wfs/lot-feature-to-location
                     (wfs/lots-by-property-id-wfs2 property-id)))
    (resp/status 400 "Bad request")))

(defn lot-property-id-by-point-proxy [{{x :x y :y} :params}]
  (if (and (coord/valid-x? x) (coord/valid-y? y))
    (try
      (if-let [features (wfs/lot-property-id-by-point-wfs2 x y)]
        (resp/json (:kiinttunnus (wfs/feature-to-property-id-wfs2 (first features))))
        (resp/status 404 "Not found"))
      (catch Exception e
        (error e "error in lot-property-id-by-point-proxy" (.getMessage e))
        (resp/status 503 "Service temporarily unavailable")))
    (resp/status 400 "Bad Request")))

(defn property-info-by-point-proxy [{{x :x y :y} :params}]
  (if (and (coord/valid-x? x) (coord/valid-y? y))
    (try
      (if-let [info (plocation/property-id-muni-by-point x y)]
        (resp/json (merge {:x x :y y} info))
        (resp/status 404 "Not found"))
      (catch Exception e
        (error e "error in property-info-by-point-proxy" (.getMessage e))
        (resp/status 503 "Service temporarily unavailable")))
    (resp/status 400 "Bad Request")))

(defn address-from-nls
  "Feature is from NLS nearestfeature. Try to parse address from feature.
   If no address available, address details are returned as empty strings.
   Municipality and propertyId are returned from property-info."
  [address x_d y_d property-info]
  (if address
    (merge (select-keys address [:street :number])
           (select-keys property-info [:municipality :name :propertyId])
           {:x x_d :y y_d})
    (merge                                                  ; fallback with default data
      (select-keys property-info [:municipality :name :propertyId])
      {:street "" :number "" :x x_d :y y_d})))

(defn- distance [^double x1 ^double y1 ^double x2 ^double y2]
  {:pre [(and x1 x2 y1 y2)]}
  (Math/sqrt (+ (Math/pow (- x2 x1) 2) (Math/pow (- y2 y1) 2))))

(defn address-by-point-proxy [{{:keys [x y lang]} :params}]
  (if (and (coord/valid-x? x) (coord/valid-y? y))
    (if-let [property (plocation/property-id-muni-by-point x y)]
      (let [lang                  (or lang "fi")
            municipality          (:municipality property)
            localize-municipality #(i18n/localize % :municipality municipality)
            x_d                   (util/->double x)
            y_d                   (util/->double y)
            nls-address-query     (future (geocoding/address-by-point! lang x_d y_d))]
        (if-let [endpoint (org/municipality-address-endpoint municipality)]
          (if-let [address-from-muni (->> (wfs/address-by-point-from-municipality x_d y_d endpoint)
                                          (map (partial wfs/krysp-to-address-details lang))
                                          (remove (fn [addr] (= {:x 0 :y 0} (select-keys addr [:x :y]))))
                                          (map (fn [{x2 :x y2 :y :as f}]
                                                 (-> f
                                                     (merge {:distance   (distance x_d y_d x2 y2)
                                                             :name       {:fi (localize-municipality :fi)
                                                                          :sv (localize-municipality :sv)}
                                                             :propertyId (:propertyId property)})
                                                     (update :municipality #(or % municipality)))))
                                          (sort-by :distance)
                                          first)]
            (do
              (future-cancel nls-address-query)
              (resp/json address-from-muni))
            (do
              (errorf "error.integration - Fallback to NSL address data - no addresses found from %s by x/y %s/%s" (i18n/localize :fi :municipality municipality) x y)
              (resp/json (address-from-nls @nls-address-query x_d y_d property))))
          (resp/json (address-from-nls @nls-address-query x_d y_d property))))
      (resp/status 404 (fail :error.property-not-found)))
    (resp/status 400 "Bad Request")))

(def wdk-type-pattern #"^POINT|^LINESTRING|^POLYGON")

(defn property-info-by-wkt-proxy [request] ;example: wkt=POINT(404271+6693892)&radius=100
  (if-let [wkt (get-in request [:params :wkt])]
    (let [{radius :radius} (:params request)
          type (re-find wdk-type-pattern wkt)
          coords (ss/replace wkt wdk-type-pattern "")
          features (case type
                     "POINT" (let [[x y] (ss/split (first (re-find #"\d+(\.\d+)* \d+(\.\d+)*" coords)) #" ")]
                               (if (and (ss/numeric? radius) (> (Long/parseLong radius) 10))
                                 (wfs/property-info-by-radius x y radius)
                                 (wfs/property-info-by-point x y)))
                     "LINESTRING" (wfs/property-info-by-line (ss/split (ss/replace coords #"[\(\)]" "") #","))
                     "POLYGON" (let [outterring (first (ss/split coords #"\)" 1))] ;;; pudotetaan reiat pois
                                 (wfs/property-info-by-polygon (ss/split (ss/replace outterring #"[\(\)]" "") #",")))
                     nil)]
      (if features
        (resp/json (map wfs/feature-to-property-info features))
        (resp/status 503 "Service temporarily unavailable")))
    (resp/status 400 "Missing required wkt parameter")))

(defn create-layer-object [[layer-name layer-title]]
  (let [layer-category (cond
                         (re-find #"^\d+_asemakaava$" layer-name) "asemakaava"
                         (re-find #"^\d+_kantakartta$" layer-name) "kantakartta"
                         :else "other")
        layer-id (case layer-category
                   "asemakaava"  "101"
                   "kantakartta" "102"
                   layer-name)]
    {:wmsName layer-name
     :wmsUrl "/proxy/wms"
     :name (case layer-category
             "asemakaava" {:fi "Asemakaava (kunta)" :sv "Detaljplan (kommun)" :en "Detailed Plan (municipality)"}
             "kantakartta" {:fi "Kantakartta" :sv "Baskarta" :en "City map"}
             {:fi layer-title :sv layer-title :en layer-title})
     :subtitle (case layer-category
                 "asemakaava" {:fi "Kunnan palveluun toimittama ajantasa-asemakaava" :sv "Detaljplan (kommun)" :en "Detailed Plan (municipality)"}
                 {:fi "" :sv "" :en ""})
     :id layer-id}))

(defn municipality-layers
  "Examines every organization belonging to the municipality and
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
  "Resolves layers for the given municipality from MongoDB and returns a list of
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
                          (get {"asemakaava"  "101"
                                "kantakartta" "102"}
                               (:name layer)
                               ;; Default index just in case. Should be never needed.
                               indexed)
                          indexed)))]
    (map-indexed (fn [index layer]
                   (let [{:keys [base id name org]} layer
                         layer-id (layer-id-fn layer index)]
                     {:wmsName (str "Lupapiste-" org ":" id)
                      :wmsUrl  "/proxy/kuntawms"
                      :name (names-fn name)
                      :subtitle {:fi "" :sv "" :en ""}
                      :id layer-id
                      ;; User layers should be visible even when zoomed out.
                      ;; The default Oskary value (159999) is quite small.
                      :minScale 400000})) layers)))


(defn our-wms-layers []
  (->> (wfs/get-our-capabilities)
       (wfs/capabilities-to-layers)
       (map wfs/layer-name-and-title)
       (map create-layer-object)))


(defn combine-municipality-layers
  "Retrieves municipality specific layers from mongo. Combines them with our-capabilities 'municipality' layers.
  If municipality is not given (or nil), our-capabilities is returned."
  ([our-layers municipality]
   (let [muni-layers (municipality-layer-objects municipality)
         muni-bases  (->> muni-layers (map :id) (filter number?) set)
         layers      (if (nil? municipality)
                       our-layers
                       (->> our-layers (filter #(= (re-find #"^\d+" (:wmsName %)) municipality))))
         layers      (->> layers
                          (filter (fn [{id :id}]
                                    (not-any? #(= id %) muni-bases))))]
     (concat layers muni-layers)))
  ([our-layers] (combine-municipality-layers our-layers nil))
  ([] (combine-municipality-layers (our-wms-layers) nil)))

(defn wms-capabilities-proxy [request]
  (->> (get-in request [:params :municipality])
       (combine-municipality-layers (our-wms-layers))
       resp/json))

(defmulti plan-infos-by-point-body (fn [endpoint & _] endpoint))

(defmethod plan-infos-by-point-body :default [& _])

(defmethod plan-infos-by-point-body :liiteri
  [_ x y municipality-or-liiteri type]
  (when-let [response (wfs/plan-info-by-point x y municipality-or-liiteri type)]
    (let [k              (keyword municipality-or-liiteri)
          t              (keyword type)
          gfi-mapper     (if-let [f-name (env/value t k :gfi-mapper)]
                           (resolve (symbol f-name))
                           wfs/gfi-to-features-sito)
          feature-mapper (if-let [f-name (env/value t k :feature-mapper)]
                           (resolve (symbol f-name))
                           wfs/feature-to-feature-info-sito)]
      (not-empty (map feature-mapper (gfi-mapper response municipality-or-liiteri))))))

(defmethod plan-infos-by-point-body :general
  [_ x y]
  (some->> (wfs/general-plan-info-by-point x y)
           wfs/gfi-to-general-plan-features
           (map wfs/general-plan-feature-to-feature-info)
           not-empty))

(defmethod plan-infos-by-point-body :trimble
  [_ x y municipality]
  (when (env/value :trimble-kaavamaaraykset (keyword municipality) :url)
    (let [[m & _ :as body] (wfs/trimble-kaavamaaraykset-by-point x y municipality)]
      ;; Trimble can return a "placeholder" plan info with empty fields.
      (when (and (map? m)
                 (some (comp ss/not-blank? ss/->plain-string) (vals m)))
        body))))

(defn plan-urls-by-point-proxy
  "The value of `municipality` is `liiteri` when searching from Liiteri and municipality code when
  searching from municipalities."
  [{{:keys [x y municipality type]} :params}]
  (let [municipality (trim municipality)
        type (if (ss/blank? type) "plan-info" type)]
    (if (and (coord/valid-x? x) (coord/valid-y? y) (or (= "liiteri" (ss/lower-case municipality)) (ss/numeric? municipality)))
      (if-let  [body (plan-infos-by-point-body :liiteri x y municipality type)]
        (resp/json body)
        (resp/status 503 "Service temporarily unavailable"))
      (resp/status 400 "Bad Request"))))

(defn general-plan-urls-by-point-proxy [{{x :x y :y} :params}]
  (if (and (coord/valid-x? x) (coord/valid-y? y))
    (if-let [body (plan-infos-by-point-body :general x y)]
      (resp/json body)
      (resp/status 503 "Service temporarily unavailable"))
    (resp/status 400 "Bad Request")))

(defn trimble-kaavamaaraykset-by-point-proxy [request]
  (let [{x :x y :y municipality :municipality} (:params request)]
    (if-let [body (plan-infos-by-point-body :trimble x y municipality)]
      (resp/json body)
      (resp/status 503 "Service temporarily unavailable"))))

(defn- safe-content-type?
  "The criteria for a safe `content-type` is the inability (or minuscule probability) of a
  cross-site scripting (XSS) attack."
  [content-type]
  (let [content-type (str content-type )]
    (and (re-matches #"(?i)^\s*(application/(json|pdf|xml)|text/xml|image/).*$"
                     content-type)
         (not (re-matches #"(?i)^\s*(image/svg).*$"
                          content-type)))))

(defn organization-map-server
  [request]
  (let [org-id   (-> request :params :lupapisteOrganizationId)
        user     (usr/current-user request)
        authed?  (usr/authority-admin-in? org-id user)
        response (when authed?
                   (some-> (org/query-organization-map-server
                             org-id
                             (dissoc (:params request) :lupapisteOrganizationId)
                             (:headers request))
                           (update :headers select-keys ["Content-Type" "Cache-Control"
                                                         "Pragma" "Date"])))]
    (cond
      (not authed?)
      (resp/status 401 "Unauthorized")

      (nil? response)
      (resp/status 503 "Service temporarily unavailable")

      (not (safe-content-type? (get-in response [:headers "Content-Type"])))
      (resp/status 415 "Unsupported media type")

      :else response)))
;
; Utils:
;

(defn- sanitize-parameters [request k]
  (if (contains? request k)
    (update request k util/convert-values ss/strip-non-printables)
    request))

(defn- sanitize-request [request]
  (-> request
    (sanitize-parameters :query-params)
    (sanitize-parameters :form-params)
    (sanitize-parameters :params)
    http/secure-headers))

(defn- secure
  "Takes a service function as an argument and returns a proxy function that invokes the original
  function. Proxy function returns what ever the service function returns, excluding some unsafe
  headers, such as cookie and host information. Request parameters are sanitized to contain
  only printable characters before invoking the wrapped function. For example,
  newlines are stripped."
  [f & args]
  (fn [request]
    (let [response (-> (apply f (cons (sanitize-request request) args))
                       http/secure-headers
                       (select-keys [:status :headers :body]))]
      (if (safe-content-type? (get-in response [:headers "Content-Type"]))
        response
        (resp/status 415 "Unsupported media type")))))

(defn- cache [max-age-in-s f]
  (let [cache-control (if (env/feature? :no-cache)
                        {"Cache-Control" "no-cache"}
                        {"Cache-Control" (str "public, max-age=" max-age-in-s)})]
    (fn [request]
      (let [response (f request)]
        (if (= 200 (:status response))
          (update response :headers merge cache-control)
          (update response :headers merge http/no-cache-headers))))))

(defn no-cache [f]
  (fn [request]
    (let [response (f request)]
      (update response :headers merge http/no-cache-headers))))

;;
;; Proxy services by name:
;;

(def services {"nls" (cache (* 3 60 60 24) (secure wfs/raster-images "nls"))
               "wms" (cache (* 3 60 60 24) (secure wfs/raster-images "wms"))
               "kuntawms" (cache (* 3 60 60 24) (secure #(wfs/raster-images %1 %2 org/query-organization-map-server) "wms" ))
               "wmts/maasto" (cache (* 3 60 60 24) (secure wfs/raster-images "wmts"))
               "wmts/kiinteisto" (cache (* 3 60 60 24) (secure wfs/raster-images "wmts"))
               "point-by-property-id" (cache (* 60 60 8) (secure point-by-property-id-proxy))
               "location-by-property-id" (cache (* 60 60 8) (secure location-by-property-id-proxy))
               "lots-by-property-id" (cache (* 60 60 8) (secure lots-by-property-id-proxy))
               "property-id-by-point" (cache (* 60 60 8) (secure lot-property-id-by-point-proxy))
               "property-info-by-point" (cache (* 60 60 8) (secure property-info-by-point-proxy))
               "address-by-point" (no-cache (secure address-by-point-proxy))
               "find-address" (no-cache (secure find-addresses-proxy))
               "get-address" (no-cache (secure get-addresses-proxy))
               "property-info-by-wkt" (cache (* 60 60 8) (secure property-info-by-wkt-proxy))
               "wmscap" (no-cache (secure wms-capabilities-proxy))
               "plan-urls-by-point" (no-cache (secure plan-urls-by-point-proxy))
               "general-plan-urls-by-point" (no-cache (secure general-plan-urls-by-point-proxy))
               "plandocument" (cache (* 3 60 60 24) (secure wfs/raster-images "plandocument"))
               "organization-map-server" (secure organization-map-server)
               "trimble-kaavamaaraykset-by-point" (no-cache (secure trimble-kaavamaaraykset-by-point-proxy))})
