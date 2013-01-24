(ns lupapalvelu.xml.krysp.reader
  (:use sade.xml)
  (:require [clojure.string :as s]
            [sade.client :as client]
            [clojure.walk :refer [postwalk]]
            [clj-http.client :as http]))

;;
;; Test urls
;;

(def logica-test-source "http://212.213.116.162/geoserver/wfs")
(def local-test-source  (client/uri "/krysp/building.xml"))

;;
;; Helpers
;;

(defn strip-key
  "removes namespacey part of a keyword key of a map entry"
  [[k v]] (if (keyword? k) [(-> k name (s/split #":") last keyword) v] [k v]))

(defn postwalk-map
  "traverses m and applies f to all maps within"
  [f m] (postwalk (fn [x] (if (map? x) (into {} (f x)) x)) m))

(defn strip-keys
  "removes recursively all namespacey parts from map keywords keys"
  [m] (postwalk-map (partial map strip-key) m))

(defn strip-nils
  "removes recursively all keys from map which have value of nil"
  [m] (postwalk-map (partial filter (comp not nil? val)) m))

(defn strip-empty-maps
  "removes recursively all keys from map which have empty map as value"
  [m] (postwalk-map (partial filter (comp (partial not= {}) val)) m))

(defn legacy-is-alive?
  "checks if the legacy system is Web Feature Service -enabled. kindof."
  [url] (try
          (-> url (http/get {:query-param {:request :GetCapabilities} :throw-exceptions false}) :status (= 200))
          (catch Exception e false)))

(defn building-info [server id]
  (let [url (str server "?request=GetFeature&typeName=rakval%3AValmisRakennus&outputFormat=KRYSP&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Erakval:rakennustieto/rakval:Rakennus/rakval:rakennuksenTiedot/rakval:rakennustunnus/rakval:kiinttun%3C/PropertyName%3E%3CLiteral%3E" id "%3C/Literal%3E%3C/PropertyIsEqualTo%3E")]
    (-> url parse xml->edn strip-keys)))

;;
;; Mappings from KRYSP to Lupapiste domain
;;

(defn building-document [xml]
  (let [data (get-in xml [:Rakennusvalvonta :valmisRakennustieto :ValmisRakennus :rakennustieto :Rakennus :rakennuksenTiedot :asuinhuoneistot])
        body {:huoneistoTunnus
              {:huoneistonumero nil, :jakokirjain nil, :porras nil},
              :huoneistonTyyppi
              {:huoneistoTyyppi (get-in data [:valmisHuoneisto :huoneistonTyyppi]),
               :huoneistoala (get-in data [:valmisHuoneisto :huoneistoala]),
               :huoneluku (get-in data [:valmisHuoneisto :huoneluku])},
              :keittionTyyppi nil,
              :varusteet
              {:ammeTaiSuihku (get-in data [:valmisHuoneisto :varusteet :ammeTaiSuihkuKytkin]),
               :lamminvesi (get-in data [:valmisHuoneisto :varusteet :lamminvesiKytkin]),
               :parvekeTaiTerassi (get-in data [:valmisHuoneisto :varusteet :parvekeTaiTerassiKytkin]),
               :sauna (get-in data [:valmisHuoneisto :varusteet :saunaKytkin]),
               :wc (get-in data [:valmisHuoneisto :varusteet :WCKytkin])}}]
    (-> body strip-nils strip-empty-maps)))
