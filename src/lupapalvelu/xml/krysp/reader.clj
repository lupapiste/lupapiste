(ns lupapalvelu.xml.krysp.reader
  (:use sade.xml)
  (:require [clojure.string :as s]
            [sade.client :as client]
            [clojure.walk :refer [postwalk postwalk-demo]]
            [lupapalvelu.document.schemas :as schema]
            [net.cgrand.enlive-html :as enlive]
            [clj-http.client :as http]))

;;
;; Test urls
;;

(def logica-test-legacy "http://212.213.116.162/geoserver/wfs")
(def local-test-legacy  (client/uri "/krysp/building.xml"))

;;
;; Common
;;

(defn strip-key
  "removes namespacey part of a keyword key"
  [k] (if (keyword? k) (-> k name (s/split #":") last keyword) k))

(defn postwalk-map
  "traverses m and applies f to all maps within"
  [f m] (postwalk (fn [x] (if (map? x) (into {} (f x)) x)) m))

(defn strip-keys
  "removes recursively all namespacey parts from map keywords keys"
  [m] (postwalk-map (partial map (fn [[k v]] [(strip-key k) v])) m))

(defn strip-nils
  "removes recursively all keys from map which have value of nil"
  [m] (postwalk-map (partial filter (comp not nil? val)) m))

(defn strip-empty-maps
  "removes recursively all keys from map which have empty map as value"
  [m] (postwalk-map (partial filter (comp (partial not= {}) val)) m))

(defn to-boolean
  "converts 'true' and 'false' strings to booleans. returns others as-are."
  [v] (condp = v
        "true" true
        "false" false
        v))

(defn convert-booleans
  "changes recursively all stringy boolean values to booleans"
  [m] (postwalk-map (partial map (fn [[k v]] [k (to-boolean v)])) m))

(defn strip-xml-namespaces
  "strips namespace-part of xml-element-keys"
  [xml] (postwalk-map (partial map (fn [[k v]] [k (if (= :tag k) (strip-key v) v)])) xml))

(defn translate
  "translates a value against the dictionary. return nil if cant be translated."
  [dictionary k & {:keys [nils] :or {nils false}}]
  (or (dictionary k) (and nils k) nil))

(defn translate-keys
  "translates all keys against the dictionary. loses all keys without translation."
  [dictionary m] (postwalk-map (partial map (fn [[k v]] (when-let [translation (translate dictionary k)] [translation v]))) m))

(defn as-is
  "read stuff from xml with enlive selector, convert to edn and strip namespaces."
  [xml selector]
  (-> (select1 xml selector) xml->edn strip-keys))

;;
;; Read the Krysp from Legacy
;;

(defn legacy-is-alive?
  "checks if the legacy system is Web Feature Service -enabled. kindof."
  [url]
  (try
    (-> url (http/get {:query-param {:request :GetCapabilities} :throw-exceptions false}) :status (= 200))
    (catch Exception e false)))

(defn building-xml [server id]
  (let [url (str server "?request=GetFeature&typeName=rakval%3AValmisRakennus&outputFormat=KRYSP&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Erakval:rakennustieto/rakval:Rakennus/rakval:rakennuksenTiedot/rakval:rakennustunnus/rakval:kiinttun%3C/PropertyName%3E%3CLiteral%3E" id "%3C/Literal%3E%3C/PropertyIsEqualTo%3E")
        raw (:body (http/get url))
        xml (parse raw)]
    xml))

(defn- ->buildingIds [m]
  {:propertyId (get-in m [:rakennustunnus :kiinttun])
   :buildingId (get-in m [:rakennustunnus :rakennusnro])})

(defn ->buildings [xml]
  (-> xml (select [:rakval:rakennustunnus]) (->> (map (comp ->buildingIds strip-keys xml->edn)))))

;;
;; Mappings from KRYSP to Lupapiste domain
;;

(def ...notfound... nil)
(def ...notimplemented... nil)

(defn ->rakennuksen-muttaminen [xml buildingId]
  (let [rakennus (select1 xml [:rakval:rakennustieto :> (enlive/has [:rakval:rakennusnro (enlive/text-pred (partial = buildingId))])])
        polished (comp strip-empty-maps strip-nils convert-booleans (partial merge {}))]
    (when rakennus
      (polished
        (as-is rakennus [:rakval:verkostoliittymat])
        (as-is rakennus [:rakval:varusteet])
        {:rakennuksenOmistajat ...notimplemented...
         :kaytto {:kayttotarkoitus (-> rakennus (select1 [:rakval:kayttotarkoitus]) text)
                  :rakentajaTyyppi (-> rakennus (select1 [:rakval:rakentajaTyyppi]) text)}
         :luokitus {:energialuokka ...notfound...
                    :paloluokka ...notfound...}
         :mitat {:kellarinpinta-ala ...notfound...
                 :kerrosala (-> rakennus (select1 [:rakval:kerrosalas]) text)
                 :kerrosluku (-> rakennus (select1 [:rakval:kerrosluku]) text)
                 :kokonaisala (-> rakennus (select1 [:rakval:kokonaisala]) text)
                 :tilavuus (-> rakennus (select1 [:rakval:tilavuus]) text)}
         :rakenne {:julkisivu (-> rakennus (select1 [:rakval:julkisivumateriaali]) text)
                   :kantavaRakennusaine (-> rakennus (select1 [:rakval:rakennusaine]) text)
                   :rakentamistapa (-> rakennus (select1 [:rakval:rakentamistapa]) text)}
         :lammitys {:lammitystapa (-> rakennus (select1 [:rakval:lammitystapa]) text)
                    :lammonlahde ...notimplemented...}
         :muutostyolaji ...notimplemented...
         :huoneistot ...notimplemented...}))))

;;
;; full mappings
;;

#_(defn ->rakennuksen-muttaminen-old [propertyId buildingId xml]
  (let [data (select1 xml [:rakval:Rakennus])]
    {:verkostoliittymat {:kaapeliKytkin nil
                         :maakaasuKytkin nil
                         :sahkoKytkin nil
                         :vesijohtoKytkin nil
                         :viemariKytkin nil}
     :rakennuksenOmistajat {:0 {:_selected "henkilo"
                                :henkilo {:henkilotiedot {:etunimi nil
                                                          :hetu nil
                                                          :sukunimi nil}
                                          :osoite {:katu nil
                                                   :postinumero nil
                                                   :postitoimipaikka nil}
                                          :yhteystiedot {:email nil
                                                         :fax nil
                                                         :puhelin nil}}
                                :yritys {:liikeJaYhteisoTunnus nil
                                         :osoite {:katu nil
                                                  :postinumero nil
                                                  :postitoimipaikka nil}
                                         :yhteyshenkilo {:henkilotiedot {:etunimi nil
                                                                         :sukunimi nil}
                                                         :yhteystiedot {:email nil
                                                                        :fax nil
                                                                        :puhelin nil}}
                                         :yritysnimi nil}}}
     :kaytto {:kayttotarkoitus nil
              :rakentajaTyyppi nil}
     :luokitus {:energialuokka nil
                :paloluokka nil}
     :mitat {:kellarinpinta-ala nil
             :kerrosala nil
             :kerrosluku nil
             :kokonaisala nil
             :tilavuus nil}
     :rakenne {:julkisivu nil
               :kantavaRakennusaine nil
               :rakentamistapa nil}
     :lammitys {:lammitystapa nil
                :lammonlahde nil}
     :muutostyolaji nil
     :varusteet {:kaasuKytkin nil
                 :lamminvesiKytkin nil
                 :sahkoKytkin nil
                 :vaestonsuoja nil
                 :vesijohtoKytkin nil
                 :viemariKytkin nil
                 :saunoja nil
                 :hissiKytkin nil
                 :koneellinenilmastointiKytkin nil
                 :aurinkopaneeliKytkin nil}
     :huoneistot {:0 {:huoneistoTunnus {:huoneistonumero nil
                                        :jakokirjain nil
                                        :porras nil}
                      :huoneistonTyyppi {:huoneistoTyyppi nil
                                         :huoneistoala nil
                                         :huoneluku nil}
                      :keittionTyyppi nil
                      :varusteet {:ammeTaiSuihku nil
                                  :lamminvesi nil
                                  :parvekeTaiTerassi nil
                                  :sauna nil
                                  :wc nil}}}}))

