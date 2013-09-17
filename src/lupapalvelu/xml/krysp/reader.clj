(ns lupapalvelu.xml.krysp.reader
  (:require [clojure.string :as s]
            [clojure.walk :refer [postwalk prewalk]]
            [sade.xml :refer :all]
            [lupapalvelu.document.schemas :as schema]
            [net.cgrand.enlive-html :as enlive]
            [clj-time.format :as timeformat]
            [clj-http.client :as http]
            [sade.common-reader :as cr]))


;;
;; Test urls
;;

(def logica-test-legacy "http://212.213.116.162/geoserver/wfs")

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
  (let [url (str server "?request=GetFeature&typeName=rakval%3AValmisRakennus&outputFormat=KRYSP&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Erakval:rakennustieto/rakval:Rakennus/rakval:rakennuksenTiedot/rakval:rakennustunnus/rakval:kiinttun%3C/PropertyName%3E%3CLiteral%3E" id "%3C/Literal%3E%3C/PropertyIsEqualTo%3E")]
    (cr/get-xml url)))

(defn- ->buildingIds [m]
  {:propertyId (get-in m [:Rakennus :rakennuksenTiedot :rakennustunnus :kiinttun])
   :buildingId (get-in m [:Rakennus :rakennuksenTiedot :rakennustunnus :rakennusnro])
   :usage      (get-in m [:Rakennus :rakennuksenTiedot :kayttotarkoitus])
   :created    (-> m (get-in [:Rakennus :alkuHetki]) cr/parse-datetime (->> (cr/unparse-datetime :year)))
   })

(defn ->buildings [xml]
  (-> xml (select [:rakval:Rakennus]) (->> (map (comp ->buildingIds cr/strip-keys xml->edn)))))

;;
;; Mappings from KRYSP to Lupapiste domain
;;

(def ...notfound... nil)
(def ...notimplemented... nil)

(defn- ->rakennuksen-omistaja [omistaja]
  {:_selected "yritys"
   :yritys {:liikeJaYhteisoTunnus                     (get-text omistaja :tunnus)
            :osoite {:katu                            (get-text omistaja :osoitenimi :teksti)
                     :postinumero                     (get-text omistaja :postinumero)
                     :postitoimipaikannimi            (get-text omistaja :postitoimipaikannimi)}
            :yhteyshenkilo {:henkilotiedot {:etunimi  (get-text omistaja :henkilonnimi :etunimi)      ;; does-not-exist in test
                                            :sukunimi (get-text omistaja :henkilonnimi :sukunimi)     ;; does-not-exist in test
                            :yhteystiedot {:email     ...notfound...
                                           :fax       ...notfound...
                                           :puhelin   ...notfound...}}}
            :yritysnimi                               (get-text omistaja :nimi)}})

(defn ->rakennuksen-tiedot [xml buildingId]
  (let [stripped  (cr/strip-xml-namespaces xml)
        rakennus  (select1 stripped [:rakennustieto :> (under [:rakennusnro (has-text buildingId)])])
        polished  (comp cr/index-maps cr/strip-empty-maps cr/strip-nils cr/convert-booleans)]
    (when rakennus
      (polished
        {:muutostyolaji                 ...notimplemented...
         :rakennusnro                   (get-text rakennus :rakennusnro)
         :verkostoliittymat             (cr/all-of rakennus [:verkostoliittymat])
         :rakennuksenOmistajat          (->>
                                          (select rakennus [:omistaja])
                                          (map ->rakennuksen-omistaja))
         :osoite {:kunta                (get-text rakennus :kunta)
                  :lahiosoite           (get-text rakennus :osoitenimi :teksti)
                  :osoitenumero         (get-text rakennus :osoitenumero)
                  :osoitenumero2        (get-text rakennus :osoitenumero2)
                  :jakokirjain          (get-text rakennus :jakokirjain)
                  :jakokirjain2         (get-text rakennus :jakokirjain2)
                  :porras               (get-text rakennus :porras)
                  :huoneisto            (get-text rakennus :huoneisto)
                  :postinumero          (get-text rakennus :postinumero)
                  :postitoimipaikannimi (get-text rakennus :postitoimipaikannimi)
                  :pistesijanti         ...notimplemented...}
         :kaytto {:kayttotarkoitus      (get-text rakennus :kayttotarkoitus)
                  :rakentajaTyyppi      (get-text rakennus :rakentajaTyyppi)}
         :luokitus {:energialuokka      (get-text rakennus :energialuokka)
                    :paloluokka         (get-text rakennus :paloluokka)}
         :mitat {:kellarinpinta-ala     (get-text rakennus :kellarinpinta-ala)
                 :kerrosala             (get-text rakennus :kerrosala)
                 :kerrosluku            (get-text rakennus :kerrosluku)
                 :kokonaisala           (get-text rakennus :kokonaisala)
                 :tilavuus              (get-text rakennus :tilavuus)}
         :rakenne {:julkisivu           (get-text rakennus :julkisivumateriaali)
                   :kantavaRakennusaine (get-text rakennus :rakennusaine)
                   :rakentamistapa      (get-text rakennus :rakentamistapa)}
         :lammitys {:lammitystapa       (get-text rakennus :lammitystapa)
                    :lammonlahde        (get-text rakennus :polttoaine)}
         :varusteet                     (cr/all-of   rakennus :varusteet)
         :huoneistot (->>
                       (select rakennus [:valmisHuoneisto])
                       (map (fn [huoneisto]
                              {:huoneistoTunnus {:huoneistonumero  (get-text huoneisto :huoneistonumero)
                                                 :jakokirjain      (get-text huoneisto :jakokirjain)
                                                 :porras           (get-text huoneisto :porras)}
                               :huoneistonTyyppi {:huoneistoTyyppi (get-text huoneisto :huoneistonTyyppi)
                                                  :huoneistoala    (get-text huoneisto :huoneistoala)
                                                  :huoneluku       (get-text huoneisto :huoneluku)}
                               :keittionTyyppi                     (get-text huoneisto :keittionTyyppi)
                               :varusteet                          (cr/all-of   huoneisto :varusteet)})))}))))
