(ns lupapalvelu.xml.krysp.reader
  (:require [taoensso.timbre :as timbre :refer [debug]]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk prewalk]]
            [sade.xml :refer :all]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.xml.krysp.verdict :as verdict]
            [net.cgrand.enlive-html :as enlive]
            [clj-time.format :as timeformat]
            [sade.http :as http]
            [ring.util.codec :as codec]
            [sade.common-reader :as cr]))

;;
;; Test urls
;;

(def logica-test-legacy "http://212.213.116.162/geos_facta/wfs")

;;
;; Read the Krysp from Legacy
;;

(defn legacy-is-alive?
  "checks if the legacy system is Web Feature Service -enabled. kindof."
  [url]
  (try
    (-> url (http/get {:query-params {:request "GetCapabilities"} :throw-exceptions false}) :status (= 200))
    (catch Exception e false)))

;; Object types (URL encoded)
(def building-type "typeName=rakval%3AValmisRakennus")
(def case-type     "typeName=rakval%3ARakennusvalvontaAsia")

;; For building filters
(def rakennuksen-kiinteistotunnus "rakval:rakennustieto/rakval:Rakennus/rakval:rakennuksenTiedot/rakval:rakennustunnus/rakval:kiinttun")
(def asian-lp-lupatunnus "rakval:luvanTunnisteTiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus")

(defn property-equals
  "Returns URL-encoded search parameter suitable for 'filter'"
  [property value]
  (codec/url-encode (str "<PropertyIsEqualTo><PropertyName>" (escape-xml property) "</PropertyName><Literal>" (escape-xml value) "</Literal></PropertyIsEqualTo>")))

(defn wfs-krysp-url [server object-type filter]
  (str server "?request=GetFeature&outputFormat=KRYSP&" object-type "&filter=" filter))

(defn building-xml [server id]
  (let [url (wfs-krysp-url server building-type (property-equals rakennuksen-kiinteistotunnus id))]
    (debug "Get building: " url)
    (cr/get-xml url)))

(defn application-xml [server id]
  (let [url (wfs-krysp-url server case-type (property-equals asian-lp-lupatunnus id))]
    (debug "Get application: " url)
    (cr/get-xml url)))


(defn- ->buildingIds [m]
  {:propertyId (get-in m [:Rakennus :rakennuksenTiedot :rakennustunnus :kiinttun])
   :buildingId (get-in m [:Rakennus :rakennuksenTiedot :rakennustunnus :rakennusnro])
   :usage      (get-in m [:Rakennus :rakennuksenTiedot :kayttotarkoitus])
   :created    (-> m (get-in [:Rakennus :alkuHetki]) cr/parse-datetime (->> (cr/unparse-datetime :year)))
   })

(defn ->buildings [xml]
  (-> xml cr/strip-xml-namespaces (select [:Rakennus]) (->> (map (comp ->buildingIds cr/strip-keys xml->edn)))))

;;
;; Mappings from KRYSP to Lupapiste domain
;;

(def ...notfound... nil)
(def ...notimplemented... nil)

(defn- ->henkilo [xml-without-ns]
  (let [henkilo (select1 xml-without-ns [:henkilo])]
    {:_selected "henkilo"
     :henkilo   {:henkilotiedot {:etunimi  (get-text henkilo :nimi :etunimi)
                                 :sukunimi (get-text henkilo :nimi :sukunimi)
                                 :hetu     (get-text henkilo :henkilotunnus)
                                 :turvakieltoKytkin (get-text xml-without-ns :turvakieltoKytkin)}
                 :yhteystiedot  {:email     (get-text henkilo :sahkopostiosoite)
                                 :puhelin   (get-text henkilo :puhelin)}
                 :osoite        {:katu         (get-text henkilo :osoite :osoitenimi :teksti)
                                 :postinumero  (get-text henkilo :osoite :postinumero)
                                 :postitoimipaikannimi  (get-text henkilo :osoite :postitoimipaikannimi)}}
     :omistajalaji     (get-text xml-without-ns :omistajalaji :omistajalaji)
     :muu-omistajalaji (get-text xml-without-ns :omistajalaji :muu)}))

(defn- ->yritys [xml-without-ns]
  (let [yritys (select1 xml-without-ns [:yritys])]
    {:_selected "yritys"
     :yritys {:yritysnimi                             (get-text yritys :nimi)
              :liikeJaYhteisoTunnus                   (get-text yritys :tunnus)
            :osoite {:katu                            (get-text yritys :postiosoite :osoitenimi :teksti)
                     :postinumero                     (get-text yritys :postiosoite :postinumero)
                     :postitoimipaikannimi            (get-text yritys :postiosoite :postitoimipaikannimi)}
            :yhteyshenkilo (-> (->henkilo xml-without-ns) :henkilo (dissoc :osoite))}
     :omistajalaji     (get-text xml-without-ns :omistajalaji :omistajalaji)
     :muu-omistajalaji (get-text xml-without-ns :omistajalaji :muu)}))

(defn- ->rakennuksen-omistaja-legacy-version [omistaja]
  {:_selected "yritys"
   :yritys {:liikeJaYhteisoTunnus                     (get-text omistaja :tunnus)
            :osoite {:katu                            (get-text omistaja :osoitenimi :teksti)
                     :postinumero                     (get-text omistaja :postinumero)
                     :postitoimipaikannimi            (get-text omistaja :postitoimipaikannimi)}
            :yhteyshenkilo {:henkilotiedot {:etunimi  (get-text omistaja :henkilonnimi :etunimi)      ;; does-not-exist in test
                                            :sukunimi (get-text omistaja :henkilonnimi :sukunimi)     ;; does-not-exist in test
                            :yhteystiedot {:email     ...notfound...
                                           :puhelin   ...notfound...}}}
            :yritysnimi                               (get-text omistaja :nimi)}})

(defn- ->rakennuksen-omistaja [omistaja]
  (cond
    (seq (select omistaja [:yritys])) (->yritys omistaja)
    (seq (select omistaja [:henkilo])) (->henkilo omistaja)
    :default (->rakennuksen-omistaja-legacy-version omistaja)))

(def cleanup (comp cr/strip-empty-maps cr/strip-nils))

(defn ->rakennuksen-tiedot [xml buildingId]
  (let [stripped  (cr/strip-xml-namespaces xml)
        rakennus  (select1 stripped [:rakennustieto :> (under [:rakennusnro (has-text buildingId)])])
        polished  (comp cr/index-maps cleanup cr/convert-booleans)]
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
                                        ; key :uima-altaita has been removed from lupapiste
         :varusteet                     (dissoc (cr/all-of rakennus :varusteet) :uima-altaita)
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


(defn ->lupamaaraukset [paatos-xml-without-ns]
  (-> (cr/all-of paatos-xml-without-ns :lupamaaraykset)
    (cleanup)
    (cr/ensure-sequental :vaaditutKatselmukset)
    (#(assoc % :vaaditutKatselmukset (map :Katselmus (:vaaditutKatselmukset %))))
    (cr/ensure-sequental :maarays)
    (#(if-let [maarays (:maarays %)] (assoc % :maaraykset (cr/convert-keys-to-timestamps maarays [:maaraysaika :toteutusHetki])) %))
    (dissoc :maarays)
    (cr/convert-keys-to-ints [:autopaikkojaEnintaan
                              :autopaikkojaVahintaan
                              :autopaikkojaRakennettava
                              :autopaikkojaRakennettu
                              :autopaikkojaKiinteistolla
                              :autopaikkojaUlkopuolella])))

(defn- get-pvm-dates [paatos v]
  (into {} (map #(let [xml-kw (keyword (str (name %) "Pvm"))]
                   [% (cr/to-timestamp (get-text paatos xml-kw))]) v)))

(defn ->liite [{:keys [metatietotieto] :as liite}]
  (-> liite
    (assoc  :metadata (into {} (map
                                 (fn [{meta :metatieto}]
                                   [(keyword (:metatietoNimi meta)) (:metatietoArvo meta)])
                                 (if (sequential? metatietotieto) metatietotieto [metatietotieto]))))
    (dissoc :metatietotieto)
    (cr/convert-keys-to-timestamps [:muokkausHetki])))

(defn ->paatospoytakirja [paatos-xml-without-ns]
  (-> (cr/all-of paatos-xml-without-ns :poytakirja)
    (cr/convert-keys-to-ints [:pykala])
    (cr/convert-keys-to-timestamps [:paatospvm])
    (#(assoc % :status (verdict/verdict-id (:paatoskoodi %))))
    (#(assoc % :liite  (->liite (:liite %))))))

(defn ->verdict [paatos-xml-without-ns]
  {:lupamaaraykset (->lupamaaraukset paatos-xml-without-ns)
   :paivamaarat    (get-pvm-dates paatos-xml-without-ns
                                  [:aloitettava :lainvoimainen :voimassaHetki :raukeamis :anto :viimeinenValitus :julkipano])
   :poytakirjat    (when-let [poytakirjat (seq (select paatos-xml-without-ns [:poytakirja]))]
                     (map ->paatospoytakirja poytakirjat))})

(defn ->verdicts [xml]
  (map
    (fn [asia]
      (let [verdict-model {:kuntalupatunnus (get-text asia [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus])}
            verdicts      (->> (select asia [:paatostieto :Paatos])
                           (map ->verdict)
                           (cleanup)
                           (filter seq))]
        (if (seq verdicts)
          (assoc verdict-model :paatokset verdicts)
          verdict-model)))
    (select (cr/strip-xml-namespaces xml) :RakennusvalvontaAsia)))

