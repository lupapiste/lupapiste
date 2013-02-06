(ns lupapalvelu.xml.krysp.reader
  (:use sade.xml)
  (:require [clojure.string :as s]
            [sade.client :as client]
            [clojure.walk :refer [postwalk postwalk-demo]]
            [lupapalvelu.document.schemas :as schema]
            [net.cgrand.enlive-html :as enlive]
            [clj-time.format :as timeformat]
            [clj-http.client :as http]))

;;
;; parsing time (TODO: might be copy-pasted from krysp)
;;

(defn parse-datetime [s]
  (timeformat/parse (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss'Z'") s))

(defn unparse-datetime [format dt]
  (timeformat/unparse (timeformat/formatters format) dt))

;;
;; Test urls
;;

(def logica-test-legacy "http://212.213.116.162/geoserver/wfs")
(def local-test-legacy  (client/uri "/krysp/building.xml"))

;;
;; Common
;;

(defn postwalk-map
  "traverses m and applies f to all maps within"
  [f m] (postwalk (fn [x] (if (map? x) (into {} (f x)) x)) m))

(defn strip-key
  "removes namespacey part of a keyword key"
  [k] (if (keyword? k) (-> k name (s/split #":") last keyword) k))

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
  "read one element from xml with enlive selector, converts to edn and strip namespaces."
  [xml selector] (-> (select1 xml selector) xml->edn strip-keys))

(defn all-of
  "read one element from xml with enlive selector, converts it's val to edn and strip namespaces."
  [xml selector] (-> xml (as-is selector) vals first))

(defn map-index
  "transform a collection into keyord-indexed map (starting from 0)."
  [c] (into {} (map (fn [[k v]] [(keyword (str k)) v]) (map-indexed vector c))))

(defn index-maps
  "transform a form with replacing all sequential collections with keyword-indexed maps."
  [m] (postwalk-map (partial map (fn [[k v]] [k (if (sequential? v) (map-index v) v)])) m))

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
  {:propertyId (get-in m [:Rakennus :rakennuksenTiedot :rakennustunnus :kiinttun])
   :buildingId (get-in m [:Rakennus :rakennuksenTiedot :rakennustunnus :rakennusnro])
   :usage      (get-in m [:Rakennus :rakennuksenTiedot :kayttotarkoitus])
   :created    (-> m (get-in [:Rakennus :alkuHetki]) parse-datetime (->> (unparse-datetime :year)))
   })

(defn ->buildings [xml]
  (-> xml (select [:rakval:Rakennus]) (->> (map (comp ->buildingIds strip-keys xml->edn)))))

;;
;; Mappings from KRYSP to Lupapiste domain
;;

(def ...notfound... nil)
(def ...notimplemented... nil)

(defn- ->rakennuksen-omistaja [omistaja]
  {:_selected "yritys"
   :yritys {:liikeJaYhteisoTunnus (-> omistaja (select1 [:tunnus]) text)
            :osoite {:katu (-> omistaja (select1 [:osoitenimi :teksti]) text)
                     :postinumero (-> omistaja (select1 [:postinumero]) text)
                     :postitoimipaikannimi (-> omistaja (select1 [:postitoimipaikannimi]) text)}
            :yhteyshenkilo {:henkilotiedot {:etunimi (-> omistaja (select1 [:henkilonnimi :etunimi]) text)       ;; does-not-exist in test
                                            :sukunimi (-> omistaja (select1 [:henkilonnimi :sukunimi]) text)     ;; does-not-exist in test
                            :yhteystiedot {:email ...notfound...
                                           :fax ...notfound...
                                           :puhelin ...notfound...}}}
            :yritysnimi (-> omistaja (select1 [:nimi]) text)}})

(defn ->rakennuksen-muuttaminen [xml buildingId]
  (let [stripped  (strip-xml-namespaces xml)
        rakennus  (select1 stripped [:rakennustieto :> (under [:rakennusnro (has-text buildingId)])])
        polished  (comp index-maps strip-empty-maps strip-nils convert-booleans)]
    (when rakennus
      (polished
        {:muutostyolaji ...notimplemented...
         :rakennusnro (-> rakennus (select1 [:rakennusnro]) text)
         :verkostoliittymat (-> rakennus (all-of [:verkostoliittymat]))
         :rakennuksenOmistajat (->>
                                 (select rakennus [:omistaja])
                                 (map ->rakennuksen-omistaja))
         :osoite {:kunta            (-> rakennus (select1 [:kunta]) text)
                  :lahiosoite       (-> rakennus (select1 [:osoitenimi :teksti]) text)
                  :osoitenumero     (-> rakennus (select1 [:osoitenumero]) text)
                  :osoitenumero2    (-> rakennus (select1 [:osoitenumero2]) text)
                  :jakokirjain      (-> rakennus (select1 [:jakokirjain]) text)
                  :jakokirjain2     (-> rakennus (select1 [:jakokirjain2]) text)
                  :porras           (-> rakennus (select1 [:porras]) text)
                  :huoneisto        (-> rakennus (select1 [:huoneisto]) text)
                  :postinumero      (-> rakennus (select1 [:postinumero]) text)
                  :postitoimipaikannimi (-> rakennus (select1 [:postitoimipaikannimi]) text)
                  :pistesijanti     ...notimplemented...}
         :kaytto {:kayttotarkoitus (-> rakennus (select1 [:kayttotarkoitus]) text)
                  :rakentajaTyyppi (-> rakennus (select1 [:rakentajaTyyppi]) text)}
         :luokitus {:energialuokka (-> rakennus (select1 [:energialuokka]) text)
                    :paloluokka (-> rakennus (select1 [:paloluokka]) text)}
         :mitat {:kellarinpinta-ala (-> rakennus (select1 [:kellarinpinta-ala]) text)
                 :kerrosala (-> rakennus (select1 [:kerrosala]) text)
                 :kerrosluku (-> rakennus (select1 [:kerrosluku]) text)
                 :kokonaisala (-> rakennus (select1 [:kokonaisala]) text)
                 :tilavuus (-> rakennus (select1 [:tilavuus]) text)}
         :rakenne {:julkisivu (-> rakennus (select1 [:julkisivumateriaali]) text)
                   :kantavaRakennusaine (-> rakennus (select1 [:rakennusaine]) text)
                   :rakentamistapa (-> rakennus (select1 [:rakentamistapa]) text)}
         :lammitys {:lammitystapa (-> rakennus (select1 [:lammitystapa]) text)
                    :lammonlahde (-> rakennus (select1 [:polttoaine]) text)}
         :varusteet (-> rakennus (all-of [:varusteet]))
         :huoneistot (->>
                       (select rakennus [:valmisHuoneisto])
                       (map (fn [huoneisto]
                              {:huoneistoTunnus {:huoneistonumero (-> huoneisto (select1 [:huoneistonumero]) text)
                                                 :jakokirjain (-> huoneisto (select1 [:jakokirjain]) text)
                                                 :porras (-> huoneisto (select1 [:porras]) text)}
                               :huoneistonTyyppi {:huoneistoTyyppi (-> huoneisto (select1 [:huoneistonTyyppi]) text)
                                                  :huoneistoala (-> huoneisto (select1 [:huoneistoala]) text)
                                                  :huoneluku (-> huoneisto (select1 [:huoneluku]) text)}
                               :keittionTyyppi (-> huoneisto (select1 [:keittionTyyppi]) text)
                               :varusteet (-> huoneisto (all-of [:varusteet]))})))}))))

;;
;; full mappings
;;

#_(defn ->rakennuksen-muuttaminen-old [propertyId buildingId xml]
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
                                                   :postitoimipaikannimi nil}
                                          :yhteystiedot {:email nil
                                                         :fax nil
                                                         :puhelin nil}}
                                :yritys {:liikeJaYhteisoTunnus nil
                                         :osoite {:katu nil
                                                  :postinumero nil
                                                  :postitoimipaikannimi nil}
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
                      :varusteet {:ammeTaiSuihkuKytkin nil
                                  :lamminvesiKytkin nil
                                  :parvekeTaiTerassiKytkin nil
                                  :saunaKytkin nil
                                  :WCKytkin nil}}}}))

