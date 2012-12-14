(ns lupapalvelu.proxy-services
  (:require [clj-http.client :as client]
            [noir.response :as resp]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as s]
            [lupapalvelu.wfs :as wfs])
  (:use [clojure.data.zip.xml]
        [lupapalvelu.log]
        [lupapalvelu.util :only [dissoc-in select]]))

;;
;; NLS:
;;

(defn- trim [s]
  (if (s/blank? s) nil (s/trim s)))

(defn- parse-address [query]
  (let [[[_ street number city]] (re-seq #"([^,\d]+)\s*(\d+)?\s*(?:,\s*(.+))?" query)
        street (trim street)
        city (trim city)]
    [street number city]))

(defn get-addresses [[street number city]]
  (wfs/execute wfs/maasto
    (wfs/query {"typeName" "oso:Osoitenimi"}
      (wfs/sort-by "oso:katunumero")
      (wfs/filter
        (wfs/and
          (wfs/property-is-like "oso:katunimi"     street)
          (wfs/property-is-like "oso:katunumero"   number)
          (wfs/or
            (wfs/property-is-like "oso:kuntanimiFin" city)
            (wfs/property-is-like "oso:kuntanimiSwe" city)))))))

(defn get-addresses-proxy [request]
  (let [query (get (:query-params request) "query")
        address (parse-address query)
        [status response] (get-addresses address)]
    (if (= status :ok)
      (let [features (take 10 response)]
        (resp/json {:query query
                    :suggestions (map wfs/feature-to-street-number-city features)
                    :data (map wfs/feature-to-address features)}))
      (resp/status 503 "Service temporarily unavailable"))))

(defn find-addresses [[street number city]]
  (wfs/execute wfs/maasto
    (cond
      (and (s/blank? number) (s/blank? city)) (wfs/query {"typeName" "oso:Osoitenimi"}
                                                (wfs/sort-by "oso:kuntanimiFin")
                                                (wfs/filter
                                                  (wfs/and
                                                    (wfs/property-is-like "oso:katunimi" (str street "*"))
                                                    (wfs/property-is-equal "oso:jarjestysnumero" "1"))))
      (s/blank? city) (wfs/query {"typeName" "oso:Osoitenimi"}
                        (wfs/sort-by "oso:kuntanimiFin")
                        (wfs/filter
                          (wfs/and
                            (wfs/property-is-like "oso:katunimi"   (str street "*"))
                            (wfs/property-is-like "oso:katunumero" (str number "*"))
                            (wfs/property-is-less "oso:jarjestysnumero" "10"))))
      (s/blank? number) (wfs/query {"typeName" "oso:Osoitenimi"}
                          (wfs/sort-by "oso:katunumero")
                          (wfs/filter
                            (wfs/and
                              (wfs/property-is-like "oso:katunimi" (str street "*"))
                              (wfs/or
                                (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                                (wfs/property-is-like "oso:kuntanimiSwe" (str city "*"))))))
      :else (wfs/query {"typeName" "oso:Osoitenimi"}
              (wfs/sort-by "oso:katunumero")
              (wfs/filter
                (wfs/and
                  (wfs/property-is-like "oso:katunimi"     (str street "*"))
                  (wfs/property-is-like "oso:katunumero"   (str number "*"))
                  (wfs/or
                    (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                    (wfs/property-is-like "oso:kuntanimiSwe" (str city "*")))))))))

(defn find-addresses-proxy [request]
  (let [query (get (:query-params request) "query")
        address (parse-address query)
        [status response] (find-addresses address)
        feature->string (wfs/feature-to-address-string address)]
    (if (= status :ok)
      (let [features (take 10 response)]
        (resp/json {:query query
                    :suggestions (map feature->string features)
                    :data (map wfs/feature-to-address features)}))
      (resp/status 503 "Service temporarily unavailable"))))

(defn- point-by-kiinteistotunnus [kiinteistotunnus]
  (wfs/execute wfs/ktjkii
    (wfs/query {"typeName" "ktjkiiwfs:PalstanTietoja" "srsName" "EPSG:3067"}
      (wfs/property-name "ktjkiiwfs:rekisteriyksikonKiinteistotunnus")
      (wfs/property-name "ktjkiiwfs:tunnuspisteSijainti")
      (wfs/filter
        (wfs/property-is-equal "ktjkiiwfs:rekisteriyksikonKiinteistotunnus" kiinteistotunnus)))))

; TODO: Error handling
(defn point-by-kiinteistotunnus-proxy [request]
  (let [[status features] (-> request (:query-params) (get "kiinteistotunnus") (point-by-kiinteistotunnus))]
    (if (= status :ok)
      (resp/json (map wfs/feature-to-position features))
      (do
        (error "Failed to get point by 'kiinteistotunnus': %s" features)
        (resp/status 503 "Service temporarily unavailable")))))

(defn- kiinteistotunnus-by-point [[x y]]
  (wfs/execute wfs/ktjkii
    (wfs/query {"typeName" "ktjkiiwfs:PalstanTietoja" "srsName" "EPSG:3067"}
      (wfs/property-name "ktjkiiwfs:rekisteriyksikonKiinteistotunnus")
      (wfs/property-name "ktjkiiwfs:tunnuspisteSijainti")
      (wfs/filter
        (wfs/intersects
          (wfs/property-name "ktjkiiwfs:sijainti")
          (wfs/point x y))))))

(defn kiinteistotunnus-by-point-proxy [request]
  (let [[status features] (-> request (:query-params) (select ["x" "y"]) (kiinteistotunnus-by-point))]
    (if (= status :ok)
      (resp/json (map wfs/feature-to-kiinteistotunnus features))
      (do
        (error "Failed to get 'kiinteistotunnus' by popint: %s" features)
        (resp/status 503 "Service temporarily unavailable")))))

;
; Utils:
;

(defn- secure
  "Takes a service function as an argument and returns a proxy function that invokes the original
  function. Proxy function returns what ever the service function returns, excluding some unsafe
  stuff. At the moment strips the 'Set-Cookie' headers."
  [f]
  (fn [request]
    (dissoc-in (f request) [:headers "set-cookie"])))

;;
;; Proxy services by name:
;;

(def services {"nls" (secure wfs/raster-images)
               "pointbykiinteistotunnus" (secure point-by-kiinteistotunnus-proxy)
               "kiinteistotunnusbypoint" (secure kiinteistotunnus-by-point-proxy)
               "find-address" (secure find-addresses-proxy)
               "get-address" (secure get-addresses-proxy)})
