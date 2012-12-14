(ns lupapalvelu.proxy-services
  (:require [clj-http.client :as client]
            [noir.response :as resp]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as s]
            [lupapalvelu.wfs :as wfs])
  (:use [clojure.data.zip.xml]
        [lupapalvelu.log]
        [lupapalvelu.util :only [dissoc-in]]))

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
  (wfs/execute
    (wfs/query {"typeName" "oso:Osoitenimi"}
      (wfs/sort-by "oso:katunumero")
      (wfs/filter
        (wfs/and
          (wfs/property-is-like "oso:katunimi"     street)
          (wfs/property-is-like "oso:katunumero"   number)
          (wfs/or
            (wfs/property-is-like "oso:kuntanimiFin" city)
            (wfs/property-is-like "oso:kuntanimiSwe" city)))))))

(defn find-addresses [[street number city]]
  (wfs/execute
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
                                (wfs/property-is-like "oso:kuntanimiSwe" (str city "*")))
                              (wfs/property-is-equal "oso:jarjestysnumero" "1"))))
      :else (wfs/query {"typeName" "oso:Osoitenimi"}
              (wfs/sort-by "oso:katunumero")
              (wfs/filter
                (wfs/and
                  (wfs/property-is-like "oso:katunimi"     (str street "*"))
                  (wfs/property-is-like "oso:katunumero"   (str number "*"))
                  (wfs/or
                    (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                    (wfs/property-is-like "oso:kuntanimiSwe" (str city "*")))))))))

(defn get-addresses-proxy [request]
  (let [query (get (:query-params request) "query")
        address (parse-address query)
        [status response] (get-addresses address)]
    (if (= status :ok)
      (let [features (take 10 response)]
        (resp/json {:query query
                    :suggestions (map wfs/feature-to-address-string features)
                    :data (map wfs/feature-to-address features)}))
      (resp/status 503 "Service temporarily unavailable"))))

(defn find-addresses-proxy [request]
  (let [query (get (:query-params request) "query")
        address (parse-address query)
        [status response] (find-addresses address)]
    (if (= status :ok)
      (let [features (take 10 response)]
        (resp/json {:query query
                    :suggestions (map wfs/feature-to-address-string features)
                    :data (map wfs/feature-to-address features)}))
      (resp/status 503 "Service temporarily unavailable"))))

(def pointbykiinteistotunnus-template
  "<?xml version='1.0' encoding='UTF-8'?>
   <wfs:GetFeature version='1.1.0'
       xmlns:ktjkiiwfs='http://xml.nls.fi/ktjkiiwfs/2010/02' xmlns:wfs='http://www.opengis.net/wfs'
       xmlns:gml='http://www.opengis.net/gml' xmlns:ogc='http://www.opengis.net/ogc'
       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'
       xsi:schemaLocation='http://www.opengis.net/wfs
       http://schemas.opengis.net/wfs/1.1.0/wfs.xsd'>
     <wfs:Query typeName='ktjkiiwfs:PalstanTietoja' srsName='EPSG:3067'>
       <wfs:PropertyName>ktjkiiwfs:rekisteriyksikonKiinteistotunnus</wfs:PropertyName>
       <wfs:PropertyName>ktjkiiwfs:tunnuspisteSijainti</wfs:PropertyName>
       <ogc:Filter>
         <ogc:PropertyIsEqualTo>
           <ogc:PropertyName>ktjkiiwfs:rekisteriyksikonKiinteistotunnus</ogc:PropertyName>
           <ogc:Literal>%s</ogc:Literal>
         </ogc:PropertyIsEqualTo>
       </ogc:Filter>
     </wfs:Query>
   </wfs:GetFeature>")

(defn- feature-to-position [feature]
  (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")]
    {:x x :y y}))



(defn nls [request]
  (client/get "https://ws.nls.fi/rasteriaineistot/image"
    {:query-params (:query-params request)
     :headers {"accept-encoding" (get-in [:headers "accept-encoding"] request)}
     :basic-auth ["***REMOVED***" "***REMOVED***"]
     :as :stream}))

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

(def services {"nls" (secure nls)
               ; "pointbykiinteistotunnus" (secure pointbykiinteistotunnus)
               ; "kiinteistotunnusbypoint" (secure kiinteistotunnusbypoint)
               "osoite" (secure find-addresses)})
