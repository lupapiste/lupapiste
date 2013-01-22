(ns lupapalvelu.xml.krysp.reader
  (:use sade.xml)
  (:require [clojure.string :refer [split]]
            [clojure.walk :refer [postwalk]]))

(defn strip-key
  "removes namespacey part of a keyword key of a map entry"
  [[k v]] (if (keyword? k) [(-> k name (split #":") last keyword) v] [k v]))

(defn strip-keys
  "removes recursively all namespacey parts from map keywords keys"
  [m] (postwalk (fn [x] (if (map? x) (into {} (map strip-key x)) x)) m))

(defn strip-nils
  "removes recursively all keys from map which have value of nil"
  [m] (postwalk (fn [x] (if (map? x) (into {} (filter (comp not nil? val) x)) x)) m))

(defn strip-empty-maps
  "removes recursively all keys from map which have empty map as value"
  [m] (postwalk (fn [x] (if (map? x) (into {} (filter (comp (partial not= {}) val) x)) x)) m))

(defn building-info [id]
  (let [url (str "http://212.213.116.162/geoserver/wfs?request=GetFeature&typeName=rakval%3AValmisRakennus&outputFormat=KRYSP&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Erakval:rakennustieto/rakval:Rakennus/rakval:rakennuksenTiedot/rakval:rakennustunnus/rakval:kiinttun%3C/PropertyName%3E%3CLiteral%3E" id "%3C/Literal%3E%3C/PropertyIsEqualTo%3E")]
    (-> url parse xml->edn strip-keys)))

#_(-> "./dev-resources/public/krysp/building.xml" slurp parse xml->edn strip-keys)

;;
;; Mappings from KRYSP to Lupapiste domain
;;

(defn huoneisto-doc [xml]
  (let [data (get-in xml [:Rakennusvalvonta :valmisRakennustieto :ValmisRakennus :rakennustieto :Rakennus :rakennuksenTiedot :asuinhuoneistot])]
    (-> {:body
         {:huoneistoTunnus
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
           :wc (get-in data [:valmisHuoneisto :varusteet :WCKytkin])}}}
      strip-nils
      strip-empty-maps)))
