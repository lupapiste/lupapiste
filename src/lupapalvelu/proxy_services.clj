(ns lupapalvelu.proxy-services
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [noir.response :as resp]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.find-address :as find-address]
            [clojure.data.zip.xml :refer :all]
            [sade.env :as env]
            [sade.util :refer [dissoc-in select]]))

;;
;; NLS:
;;

(defn- trim [s]
  (when-not (s/blank? s) (s/trim s)))

(defn- parse-address [query]
  (let [[[_ street number city]] (re-seq #"([^,\d]+)\s*(\d+)?\s*(?:,\s*(.+))?" query)
        street (trim street)
        city (trim city)]
    [street number city]))

(defn get-addresses [street number city]
  (wfs/post wfs/maasto
    (wfs/query {"typeName" "oso:Osoitenimi"}
      (wfs/ogc-sort-by ["oso:katunumero"])
      (wfs/ogc-filter
        (wfs/ogc-and
          (wfs/property-is-like "oso:katunimi"     street)
          (wfs/property-is-like "oso:katunumero"   number)
          (wfs/ogc-or
            (wfs/property-is-like "oso:kuntanimiFin" city)
            (wfs/property-is-like "oso:kuntanimiSwe" city)))))))

(defn get-addresses-proxy [request]
  (let [query (get (:params request) :query)
        address (parse-address query)
        response (apply get-addresses address)]
    (if response
      (let [features (take 10 response)]
        (resp/json {:query query
                    :suggestions (map wfs/feature-to-simple-address-string features)
                    :data (map wfs/feature-to-address features)}))
      (resp/status 503 "Service temporarily unavailable"))))

(defn find-addresses-proxy [request]
  (let [term (get (:params request) :term)]
    (if (string? term)
      (resp/json (or (find-address/search term) []))
      (resp/status 400 "Missing query param 'term'"))))

(defn point-by-property-id-proxy [request]
  (let [property-id (get (:params request) :property-id)
        features (wfs/point-by-property-id property-id)]
    (if features
      (resp/json {:data (map wfs/feature-to-position features)})
      (resp/status 503 "Service temporarily unavailable"))))

(defn area-by-property-id-proxy [request]
  (let [property-id (get (:params request) :property-id)
        features (wfs/area-by-property-id property-id)]
    (if features
      (resp/json {:data (map wfs/feature-to-area features)})
      (resp/status 503 "Service temporarily unavailable"))))

(defn property-id-by-point-proxy [request]
  (let [{x :x y :y} (:params request)
        features (wfs/property-id-by-point x y)]
    (if features
      (resp/json (:kiinttunnus (wfs/feature-to-property-id (first features))))
      (resp/status 503 "Service temporarily unavailable"))))

(defn address-by-point-proxy [request]
  (let [{x :x y :y} (:params request)
        features (wfs/address-by-point x y)]
    (if features
      (resp/json (wfs/feature-to-address-details (first features)))
      (resp/status 503 "Service temporarily unavailable"))))

(defn property-info-by-wkt-proxy [request]
  (let [{wkt :wkt} (:params request)
        type (re-find #"^POINT|^LINESTRING|^POLYGON" wkt)
        coords (s/replace wkt #"^POINT|^LINESTRING|^POLYGON" "")
        features (case type
                   "POINT" (let [[x y] (s/split (first (re-find #"\d+(\.\d+)* \d+(\.\d+)*" coords)) #" ")]
                             (wfs/property-info-by-point x y))
                   "LINESTRING" (wfs/property-info-by-line (s/split (s/replace coords #"[\(\)]" "") #","))
                   "POLYGON" (let [outterring (first (s/split coords #"\)" 1))] ;;; pudotetaan reiat pois
                               (wfs/property-info-by-polygon (s/split (s/replace outterring #"[\(\)]" "") #",")))
                   nil)]
    (if features
      (resp/json (map wfs/feature-to-property-info features))
      (resp/status 503 "Service temporarily unavailable"))))

(defn wms-capabilities-proxy [request]
  (let [capabilities (wfs/getcapabilities request)
        layers (wfs/capabilities-to-layers capabilities)]
    (if layers
      (resp/json (map wfs/layer-to-name layers))
      (resp/status 503 "Service temporarily unavailable"))))

(defn plan-urls-by-point-proxy [request]
  (let [{x :x y :y municipality :municipality} (:params request)
        response (wfs/plan-info-by-point x y municipality)
        k (keyword municipality)
        gfi-mapper (if-let [f-name (env/value :plan-info k :gfi-mapper)]
                     (resolve (symbol f-name))
                     wfs/gfi-to-features-sito)
        feature-mapper (if-let [f-name (env/value :plan-info k :feature-mapper)]
                         (resolve (symbol f-name))
                         wfs/feature-to-feature-info-sito)]
    (if response
      (resp/json (map feature-mapper (gfi-mapper response municipality)))
      (resp/status 503 "Service temporarily unavailable"))))

(defn general-plan-urls-by-point-proxy [request]
  (let [{x :x y :y} (:params request)
        response (wfs/general-plan-info-by-point x y)]
    (if response
      (resp/json (map wfs/general-plan-feature-to-feature-info (wfs/gfi-to-general-plan-features response)))
      (resp/status 503 "Service temporarily unavailable"))))

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
               "wms" (cache (* 3 60 60 24) (secure wfs/raster-images "wms"))
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
               "plandocument" (cache (* 3 60 60 24) (secure wfs/raster-images "plandocument"))})

