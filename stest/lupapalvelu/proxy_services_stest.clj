(ns lupapalvelu.proxy-services-stest
  (:require [lupapalvelu.proxy-services :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.mongo :as mongo]
            [sade.http :as http]
            [sade.env :as env]
            [cheshire.core :as json]))

; make sure proxies are enabled:
(http/post (str (server-address) "/api/proxy-ctrl/on"))

(defn- proxy-request [apikey proxy-name & args]
  (-> (http/post
        (str (server-address) "/proxy/" (name proxy-name))
        {:headers {"authorization" (str "apikey=" apikey)
                   "content-type" "application/json;charset=utf-8"}
         :socket-timeout 10000
         :conn-timeout 10000
         :body (json/encode (apply hash-map args))})
    decode-response
    :body))

(facts "find-addresses-proxy"
  (let [r (proxy-request mikko :find-address :term "piiriniitynkatu 9, tampere")]
    (fact r => [{:kind "address"
                 :type "street-number-city"
                 :street "Piiriniitynkatu"
                 :number "9"
                 :municipality "837"
                 :name {:fi "Tampere" :sv "Tammerfors"}
                 :location {:x "320371.953" :y "6825180.72"}}]))
  (let [r (proxy-request mikko :find-address :term "piiriniitynkatu")]
    (fact r => [{:kind "address"
                 :type "street"
                 :street "Piiriniitynkatu"
                 :number "1"
                 :name {:fi "Tampere" :sv "Tammerfors"}
                 :municipality "837"
                 :location {:x "320531.265" :y "6825180.25"}}]))
  (let [response (get-addresses-proxy {:params {:query "piiriniitynkatu 9, tampere"}})
        r (json/decode (:body response) true)]
    (fact (:query r) => "piiriniitynkatu 9, tampere")
    (fact (:suggestions r) => ["Piiriniitynkatu 9, Tampere"])
    (fact (:data r) => [{:street "Piiriniitynkatu",
                         :number "9",
                         :name {:fi "Tampere" :sv "Tammerfors"}
                         :municipality "837"
                         :location {:x "320371.953" :y "6825180.72"}}]))
  (let [response (get-addresses-proxy {:params {:query "piiriniitynkatu 19, tampere"}})
        r (json/decode (:body response) true)]
    (fact (:query r) => "piiriniitynkatu 19, tampere")
    (fact (:suggestions r) => ["Piiriniitynkatu 19, Tampere"])
    (fact (:data r) => [{:street "Piiriniitynkatu",
                         :number "19",
                         :name {:fi "Tampere" :sv "Tammerfors"}
                         :municipality "837"
                         :location {:x "320193.034" :y "6825190.138"}}])))

(facts "point-by-property-id"
  (let [property-id "09100200990013"
        request {:params {:property-id property-id}}
        response (point-by-property-id-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)
          data (:data body)]
      (fact data => vector?)
      (fact (count data) => 1)
      (fact (first data) => {:x "385628.416", :y "6672187.492"}))))

(facts "property-id-by-point"
  (let [x 385648
        y 6672157
        request {:params {:x x :y y}}
        response (property-id-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact body => "09100200990013"))))

(facts "address-by-point"
  (let [x 333168
        y 6822000
        request {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact (:street body) => "Luhtaankatu")
      (fact (:number body) => #"\d")
      (fact (:fi (:name body)) => "Tampere"))))

(facts "geoserver-layers"
  (let [base-params {"FORMAT" "image/png"
                     "SERVICE" "WMS"
                     "VERSION" "1.1.1"
                     "REQUEST" "GetMap"
                     "STYLES"  ""
                     "SRS"     "EPSG:3067"
                     "BBOX"   "444416,6666496,444672,6666752"
                     "WIDTH"   "256"
                     "HEIGHT" "256"}]
    (doseq [layer [{"LAYERS" "lupapiste:Mikkeli_Asemakaavat"
                    "BBOX"   "512000,6837760,514560,6840320"}
                   {"LAYERS" "lupapiste:Hameenlinna_Asemakaava"
                    "BBOX"   "358400,6758400,409600,6809600"}
                   {"LAYERS" "lupapiste:Hameenlinna_Kantakartta"
                    "BBOX"   "358400,6758400,409600,6809600"}
                   {"LAYERS" "lupapiste:Naantali_Asemakaavayhdistelma_Velkua"
                    "BBOX"   "208384,6715136,208640,6715392"}
                   {"LAYERS" "lupapiste:Naantali_Asemakaavayhdistelma_Naantali"
                    "BBOX"   "226816,6713856,227328,6714368"}]]
      (let [request {:query-params (merge base-params layer)
                     :headers {"accept-encoding" "gzip, deflate"}
                     :as :stream}]
        (println "Checking" (get layer "LAYERS"))
        (http/get (env/value :maps :geoserver) request) => http200?))))

(if (env/feature? :use-wmts-map)

  (facts "WMTS layers"
    (let [base-params {:FORMAT "image/png"
                       :SERVICE "WMTS"
                       :VERSION "1.0.0"
                       :REQUEST "GetTile"
                       :STYLE "default"
                       :TILEMATRIXSET "ETRS-TM35FIN"
                       :TILEMATRIX "11"
                       :TILEROW "1247"
                       :TILECOL "891"}]
      (doseq [layer [{:LAYER "taustakartta"}
                     {:LAYER "kiinteistojaotus"}
                     {:LAYER "kiinteistotunnukset"}]]
        (let [request {:params (merge base-params layer)
                       :headers {"accept-encoding" "gzip, deflate"}}]
          (println "Checking" (get layer :LAYER))
          (wfs/raster-images request "wmts") => http200?))))

  (facts "raster-images"
    (let [base-params {"FORMAT" "image/png"
                       "SERVICE" "WMS"
                       "VERSION" "1.1.1"
                       "REQUEST" "GetMap"
                       "STYLES"  ""
                       "SRS"     "EPSG:3067"
                       "BBOX"   "444416,6666496,444672,6666752"
                       "WIDTH"   "256"
                       "HEIGHT" "256"}]
      (doseq [layer [{"LAYERS" "taustakartta_5k"}
                     {"LAYERS" "taustakartta_10k"}
                     {"LAYERS" "taustakartta_20k"}
                     {"LAYERS" "taustakartta_40k"}
                     {"LAYERS" "ktj_kiinteistorajat" "TRANSPARENT" "TRUE"}
                     {"LAYERS" "ktj_kiinteistotunnukset" "TRANSPARENT" "TRUE"}]]
        (let [request {:params (merge base-params layer)
                       :headers {"accept-encoding" "gzip, deflate"}}]
          (println "Checking" (get layer "LAYERS"))
          (wfs/raster-images request "wms") => http200?))))
  )

