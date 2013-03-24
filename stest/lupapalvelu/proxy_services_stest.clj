(ns lupapalvelu.proxy-services-stest
  (:use [lupapalvelu.proxy-services]
        [midje.sweet])
  (:require [lupapalvelu.wfs :as wfs]
            [cheshire.core :as json]))

(facts "find-addresses-proxy"
  (let [response (find-addresses-proxy {:query-params {"query" "piiriniitynkatu 9, tampere"}})
        r (json/decode (:body response) true)]
    (fact (:query r) => "piiriniitynkatu 9, tampere")
    (fact (:suggestions r) => ["Piiriniitynkatu 9, Tampere"])
    (fact (:data r) => [{:katunimi "Piiriniitynkatu",
                         :katunumero "9",
                         :kuntanimiFin "Tampere",
                         :kuntanimiSwe "Tammerfors"
                         :kuntatunnus "837"
                         :x "320371.953"
                         :y "6825180.72"}]))
  (let [response (find-addresses-proxy {:query-params {"query" "piiriniitynkatu"}})
        r (json/decode (:body response) true)]
    (fact (:query r) => "piiriniitynkatu")
    (fact (:suggestions r) => ["Piiriniitynkatu, Tampere"])
    (fact (:data r) => [{:katunimi "Piiriniitynkatu",
                         :katunumero "1",
                         :kuntanimiFin "Tampere",
                         :kuntanimiSwe "Tammerfors"
                         :kuntatunnus "837"
                         :x "320531.265"
                         :y "6825180.25"}]))
  (let [response (get-addresses-proxy {:query-params {"query" "piiriniitynkatu 9, tampere"}})
        r (json/decode (:body response) true)]
    (fact (:query r) => "piiriniitynkatu 9, tampere")
    (fact (:suggestions r) => ["Piiriniitynkatu 9, Tampere"])
    (fact (:data r) => [{:katunimi "Piiriniitynkatu",
                         :katunumero "9",
                         :kuntanimiFin "Tampere",
                         :kuntanimiSwe "Tammerfors"
                         :kuntatunnus "837"
                         :x "320371.953"
                         :y "6825180.72"}]))
  (let [response (get-addresses-proxy {:query-params {"query" "piiriniitynkatu 19, tampere"}})
        r (json/decode (:body response) true)]
    (fact (:query r) => "piiriniitynkatu 19, tampere")
    (fact (:suggestions r) => ["Piiriniitynkatu 19, Tampere"])
    (fact (:data r) => [{:katunimi "Piiriniitynkatu",
                         :katunumero "19",
                         :kuntanimiFin "Tampere",
                         :kuntanimiSwe "Tammerfors"
                         :kuntatunnus "837"
                         :x "320193.034"
                         :y "6825190.138"}])))

(facts "point-by-property-id"
  (let [property-id "09100200990013"
        request {:query-params {"property-id" property-id}}
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
        request {:query-params {"x" x "y" y}}
        response (property-id-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact body => "09100200990013"))))

(facts "address-by-point"
  (let [x 333168
        y 6822000
        request {:query-params {"x" x "y" y}}
        response (address-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact (:katunimi body) => "Luhtaankatu")
      (fact (:katunumero body) => #"\d")
      (fact (:kuntanimiFin body) => "Tampere"))))

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
      (let [request {:query-params (merge base-params layer)
                     :headers {"accept-encoding" "gzip, deflate"}}
            response (wfs/raster-images request)]
        (println (get layer "LAYERS"))
        (:status response) => 200))))
