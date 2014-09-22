(ns lupapalvelu.xml.krysp.reader
  (:require [taoensso.timbre :as timbre :refer [debug warn]]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk prewalk]]
            [clj-time.format :as timeformat]
            [net.cgrand.enlive-html :as enlive]
            [ring.util.codec :as codec]
            [sade.xml :refer :all]
            [sade.http :as http]
            [sade.util :as util]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.verdict :as verdict]))

;;
;; Read the Krysp from municipality Web Feature Service
;;

(defn wfs-is-alive?
  "checks if the given system is Web Feature Service -enabled. kindof."
  [url]
  (when-not (s/blank? url)
    (try
     (let [resp (http/get url {:query-params {:request "GetCapabilities"} :throw-exceptions false})]
       (or
         (and (= 200 (:status resp)) (ss/contains (:body resp) "<?xml "))
         (warn "Response not OK or did not contain XML. Response was: " resp)))
     (catch Exception e
       (warn (str "Could not connect to WFS: " url ", exception was " e))))))

;; Object types (URL encoded)
(def building-type    "typeName=rakval%3AValmisRakennus")
(def rakval-case-type "typeName=rakval%3ARakennusvalvontaAsia")
(def poik-case-type   "typeName=ppst%3APoikkeamisasia,ppst%3ASuunnittelutarveasia")
(def ya-type          "typeName=yak%3AYleisetAlueet")
(def yl-case-type     "typeName=ymy%3AYmparistolupa")
(def mal-case-type    "typeName=ymm%3AMaaAineslupaAsia")
(def vvvl-case-type   "typeName=ymv%3AVapautus")

;; Object types as enlive selector
(def case-elem-selector #{[:RakennusvalvontaAsia]
                          [:Poikkeamisasia]
                          [:Suunnittelutarveasia]
                          [:Sijoituslupa]
                          [:Kayttolupa]
                          [:Liikennejarjestelylupa]
                          [:Tyolupa]
                          [:Ilmoitukset]
                          [:Ymparistolupa]
                          [:MaaAineslupaAsia]
                          [:Vapautus]})

;; For building filters
(def ^:private yht-tunnus "yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus")

(def rakennuksen-kiinteistotunnus "rakval:rakennustieto/rakval:Rakennus/rakval:rakennuksenTiedot/rakval:rakennustunnus/rakval:kiinttun")
(def asian-lp-lupatunnus (str "rakval:luvanTunnisteTiedot/" yht-tunnus))
(def yleisten-alueiden-lp-lupatunnus (str "yak:luvanTunnisteTiedot/" yht-tunnus))
(def poik-lp-lupatunnus  (str "ppst:luvanTunnistetiedot/" yht-tunnus))
(def yl-lp-lupatunnus (str "ymy:luvanTunnistetiedot/" yht-tunnus))
(def mal-lp-lupatunnus (str "ymm:luvanTunnistetiedot/" yht-tunnus))
(def vvvl-lp-lupatunnus (str "ymv:luvanTunnistetiedot/" yht-tunnus))


(defn property-equals
  "Returns URL-encoded search parameter suitable for 'filter'"
  [property value]
  (codec/url-encode (str "<PropertyIsEqualTo><PropertyName>" (escape-xml property) "</PropertyName><Literal>" (escape-xml value) "</Literal></PropertyIsEqualTo>")))

(defn- post-body-for-ya-application [application-id]
  {:body (str "<wfs:GetFeature
      service=\"WFS\"
        version=\"1.1.0\"
        outputFormat=\"GML2\"
        xmlns:yak=\"http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus\"
        xmlns:wfs=\"http://www.opengis.net/wfs\"
        xmlns:ogc=\"http://www.opengis.net/ogc\"
        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
        <wfs:Query typeName=\"yak:Sijoituslupa,yak:Kayttolupa,yak:Liikennejarjestelylupa,yak:Tyolupa\">
          <ogc:Filter>
            <ogc:PropertyIsEqualTo>
              <ogc:PropertyName>yak:luvanTunnisteTiedot/yht:LupaTunnus/yht:muuTunnustieto/yht:MuuTunnus/yht:tunnus</ogc:PropertyName>
              <ogc:Literal>" application-id "</ogc:Literal>
            </ogc:PropertyIsEqualTo>
          </ogc:Filter>
         </wfs:Query>
       </wfs:GetFeature>")})

(defn wfs-krysp-url [server object-type filter]
  (let [server (if (.contains server "?")
                 (if (.endsWith server "&")
                   server
                   (str server "&"))
                 (str server "?"))]
    (str server "request=GetFeature&" object-type "&filter=" filter)))

(defn wfs-krysp-url-with-service [server object-type filter]
  (str (wfs-krysp-url server object-type filter) "&service=WFS"))

(defn building-xml [server id]
  (let [url (wfs-krysp-url server building-type (property-equals rakennuksen-kiinteistotunnus id))]
    (debug "Get building: " url)
    (cr/get-xml url)))

(defn- application-xml [type-name id-path server id raw?]
  (let [url (wfs-krysp-url-with-service server type-name (property-equals id-path id))
        credentials nil]
    (debug "Get application: " url)
    (cr/get-xml url credentials raw?)))

(defn rakval-application-xml [server id raw?] (application-xml rakval-case-type asian-lp-lupatunnus server id raw?))
(defn poik-application-xml [server id raw?] (application-xml poik-case-type poik-lp-lupatunnus server id raw?))
(defn yl-application-xml [server id raw?] (application-xml yl-case-type yl-lp-lupatunnus server id raw?))
(defn mal-application-xml [server id raw?] (application-xml mal-case-type mal-lp-lupatunnus server id raw?))
(defn vvvl-application-xml [server id raw?] (application-xml vvvl-case-type vvvl-lp-lupatunnus server id raw?))

(defn ya-application-xml [server id raw?]
  (let [options (post-body-for-ya-application id)
        credentials nil]
    (debug "Get application: " server " with post body: " options )
    (cr/get-xml-with-post server options credentials raw?)))

(permit/register-function permit/R  :xml-from-krysp rakval-application-xml)
(permit/register-function permit/P  :xml-from-krysp poik-application-xml)
(permit/register-function permit/YA :xml-from-krysp ya-application-xml)
(permit/register-function permit/YL :xml-from-krysp yl-application-xml)
(permit/register-function permit/MAL :xml-from-krysp mal-application-xml)
(permit/register-function permit/VVVL :xml-from-krysp vvvl-application-xml)

(defn- ->building-ids [id-container xml-no-ns]
  {:propertyId (get-text xml-no-ns id-container :kiinttun)
  :buildingId  (get-text xml-no-ns id-container :rakennusnro)
  :index       (get-text xml-no-ns id-container :jarjestysnumero)
  :usage       (or (get-text xml-no-ns :kayttotarkoitus) "")
  :area        (get-text xml-no-ns :kokonaisala)
  :created     (->> (get-text xml-no-ns :alkuHetki) cr/parse-datetime (cr/unparse-datetime :year))})

(defn ->buildings-summary [xml]
  (let [xml-no-ns (cr/strip-xml-namespaces xml)]
    (distinct
      (concat
        (map (partial ->building-ids :rakennustunnus) (select xml-no-ns [:Rakennus]))
        (map (partial ->building-ids :tunnus) (select xml-no-ns [:Rakennelma]))))))

;;
;; Mappings from KRYSP to Lupapiste domain
;;

(def ...notfound... nil)
(def ...notimplemented... nil)

(defn- str-or-nil [& v]
  (when-not (some nil? v) (reduce str v)))

(defn- get-updated-if [current to-add]
   (if to-add
    (str current to-add)
    current))

(defn get-osoite [osoite]
  (-> (get-text osoite :osoitenimi :teksti)
    (get-updated-if (str-or-nil " " (get-text osoite :osoitenumero)))
    (get-updated-if (str-or-nil "\u2013" (get-text osoite :osoitenumero2)));SFS4175 stardardin mukainen valiviiva
    (get-updated-if (str-or-nil " " (get-text osoite :jakokirjain)))
    (get-updated-if (str-or-nil "\u2013" (get-text osoite :jakokirjain2)))
    (get-updated-if (str-or-nil " " (get-text osoite :porras)))
    (get-updated-if (str-or-nil " " (get-text osoite :huoneisto)))))

(defn- ->henkilo [xml-without-ns]
  (let [henkilo (select1 xml-without-ns [:henkilo])]
    {:_selected "henkilo"
     :henkilo   {:henkilotiedot {:etunimi  (get-text henkilo :nimi :etunimi)
                                 :sukunimi (get-text henkilo :nimi :sukunimi)
                                 :hetu     (get-text henkilo :henkilotunnus)
                                 :turvakieltoKytkin (get-text xml-without-ns :turvakieltoKytkin)}
                 :yhteystiedot  {:email     (get-text henkilo :sahkopostiosoite)
                                 :puhelin   (get-text henkilo :puhelin)}
                 :osoite        {:katu         (get-osoite (select1 henkilo :osoite))
                                 :postinumero  (get-text henkilo :osoite :postinumero)
                                 :postitoimipaikannimi  (get-text henkilo :osoite :postitoimipaikannimi)}}
     :omistajalaji     (get-text xml-without-ns :omistajalaji :omistajalaji)
     :muu-omistajalaji (get-text xml-without-ns :omistajalaji :muu)}))

(defn- ->yritys [xml-without-ns]
  (let [yritys (select1 xml-without-ns [:yritys])]
    {:_selected "yritys"
     :yritys {:yritysnimi                             (get-text yritys :nimi)
              :liikeJaYhteisoTunnus                   (get-text yritys :liikeJaYhteisotunnus)
              :osoite {:katu                          (get-osoite (select1 yritys :postiosoite))
                       :postinumero                   (get-text yritys :postiosoite :postinumero)
                       :postitoimipaikannimi          (get-text yritys :postiosoite :postitoimipaikannimi)}
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

(def cleanup (comp util/strip-empty-maps util/strip-nils))

(def polished  (comp cr/index-maps cleanup cr/convert-booleans))

(defn ->rakennuksen-tiedot
  ([xml building-id]
    (let [stripped  (cr/strip-xml-namespaces xml)
          rakennus  (select1 stripped [:rakennustieto :> (under [:rakennusnro (has-text building-id)])])]
      (->rakennuksen-tiedot rakennus)))
  ([rakennus]
    (when rakennus
      (polished
        {:muutostyolaji                 ...notimplemented...
         :rakennusnro                   (get-text rakennus :rakennustunnus :rakennusnro)
         :jarjestysnumero               (get-text rakennus :rakennustunnus :jarjestysnumero)
         :kiinttun                      (get-text rakennus :rakennustunnus :kiinttun)
         :verkostoliittymat             (cr/all-of rakennus [:verkostoliittymat])
         :rakennuksenOmistajat          (->>
                                          (select rakennus [:omistaja])
                                          (map ->rakennuksen-omistaja))
         :osoite {:kunta                (get-text rakennus :osoite :kunta)
                  :lahiosoite           (get-text rakennus :osoite :osoitenimi :teksti)
                  :osoitenumero         (get-text rakennus :osoite :osoitenumero)
                  :osoitenumero2        (get-text rakennus :osoite :osoitenumero2)
                  :jakokirjain          (get-text rakennus :osoite :jakokirjain)
                  :jakokirjain2         (get-text rakennus :osoite :jakokirjain2)
                  :porras               (get-text rakennus :osoite :porras)
                  :huoneisto            (get-text rakennus :osoite :huoneisto)
                  :postinumero          (get-text rakennus :osoite :postinumero)
                  :postitoimipaikannimi (get-text rakennus :osoite :postitoimipaikannimi)}
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
         :varusteet                     (-> (cr/all-of rakennus :varusteet)
                                          (dissoc :uima-altaita)
                                          (merge {:liitettyJatevesijarjestelmaanKytkin (get-text rakennus :liitettyJatevesijarjestelmaanKytkin)}))
         :huoneistot (->>
                       (select rakennus [:valmisHuoneisto])
                       (map (fn [huoneisto]
                              {:huoneistonumero (get-text huoneisto :huoneistonumero)
                               :jakokirjain     (get-text huoneisto :jakokirjain)
                               :porras          (get-text huoneisto :porras)
                               :huoneistoTyyppi (get-text huoneisto :huoneistonTyyppi)
                               :huoneistoala    (get-text huoneisto :huoneistoala)
                               :huoneluku       (get-text huoneisto :huoneluku)
                               :keittionTyyppi  (get-text huoneisto :keittionTyyppi)
                               :WCKytkin                (get-text huoneisto :WCKytkin)
                               :ammeTaiSuihkuKytkin     (get-text huoneisto :ammeTaiSuihkuKytkin)
                               :lamminvesiKytkin        (get-text huoneisto :lamminvesiKytkin)
                               :parvekeTaiTerassiKytkin (get-text huoneisto :parvekeTaiTerassiKytkin)
                               :saunaKytkin             (get-text huoneisto :saunaKytkin)})))}))))

(defn ->buildings [xml]
  (map ->rakennuksen-tiedot (-> xml cr/strip-xml-namespaces (select [:Rakennus]))))

(defn- ->lupamaaraukset [paatos-xml-without-ns]
  (-> (cr/all-of paatos-xml-without-ns :lupamaaraykset)
    (cleanup)

    (cr/ensure-sequental :vaaditutKatselmukset)
    (#(assoc % :vaaditutKatselmukset (map :Katselmus (:vaaditutKatselmukset %))))

    ; KRYSP yhteiset 2.1.1+
    (cr/ensure-sequental :vaadittuTyonjohtajatieto)
    (update-in [:vaadittuTyonjohtajatieto] #(map (comp :tyonjohtajaLaji :VaadittuTyonjohtaja ) %))
    ; KRYSP yhteiset 2.1.0 and below have vaaditutTyonjohtajat key that contains the same data in a single string.
    ; Convert the new format to the old.
    (#(if (seq (:vaadittuTyonjohtajatieto %)) (assoc % :vaaditutTyonjohtajat (s/join ", " (:vaadittuTyonjohtajatieto %))) %))

    (cr/ensure-sequental :maarays)
    (#(if-let [maarays (:maarays %)] (assoc % :maaraykset (cr/convert-keys-to-timestamps maarays [:maaraysaika :toteutusHetki])) %))
    (dissoc :maarays)

    (cr/convert-keys-to-ints [:autopaikkojaEnintaan
                              :autopaikkojaVahintaan
                              :autopaikkojaRakennettava
                              :autopaikkojaRakennettu
                              :autopaikkojaKiinteistolla
                              :autopaikkojaUlkopuolella])))

(defn- ->lupamaaraukset-text [paatos-xml-without-ns]
  (let [lupaehdot (select paatos-xml-without-ns :lupaehdotJaMaaraykset)]
    (when (not-empty lupaehdot)
      (-> lupaehdot
        (cleanup)
        ((fn [maar] (map #(get-text % :lupaehdotJaMaaraykset) maar)))
        (cr/ensure-sequental :lupaehdotJaMaaraykset)))))

(defn- get-pvm-dates [paatos v]
  (into {} (map #(let [xml-kw (keyword (str (name %) "Pvm"))]
                   [% (cr/to-timestamp (get-text paatos xml-kw))]) v)))

(defn- ->liite [{:keys [metatietotieto] :as liite}]
  (-> liite
    (assoc  :metadata (into {} (map
                                 (fn [{meta :metatieto}]
                                   [(keyword (:metatietoNimi meta)) (:metatietoArvo meta)])
                                 (if (sequential? metatietotieto) metatietotieto [metatietotieto]))))
    (dissoc :metatietotieto)
    (cr/convert-keys-to-timestamps [:muokkausHetki])))

(defn- ->paatospoytakirja [paatos-xml-without-ns]
  (-> (cr/all-of paatos-xml-without-ns :poytakirja)
    (cr/convert-keys-to-ints [:pykala])
    (cr/convert-keys-to-timestamps [:paatospvm])
    (#(assoc % :status (verdict/verdict-id (:paatoskoodi %))))
    (#(update-in % [:liite] ->liite))))

(defn- ->standard-verdicts [xml-without-ns]
  (map (fn [paatos-xml-without-ns]
         (let [poytakirjat (map ->paatospoytakirja (select paatos-xml-without-ns [:poytakirja]))
               poytakirja-with-paatos-data (some #(when (and (:paatoskoodi %) (:paatoksentekija %) (:paatospvm %)) %) poytakirjat)]
           (when (and poytakirja-with-paatos-data (> (now) (:paatospvm poytakirja-with-paatos-data)))
             {:lupamaaraykset (->lupamaaraukset paatos-xml-without-ns)
              :paivamaarat    (get-pvm-dates paatos-xml-without-ns
                                [:aloitettava :lainvoimainen :voimassaHetki :raukeamis :anto :viimeinenValitus :julkipano])
              :poytakirjat    (seq poytakirjat)})))
    (select xml-without-ns [:paatostieto :Paatos])))

(defn- ->simple-verdicts [xml-without-ns]
  (let [app-state (->> (select xml-without-ns [:Kasittelytieto])
                    (map (fn [kasittelytieto] (-> (cr/all-of kasittelytieto) (cr/convert-keys-to-timestamps [:muutosHetki]))))
                    (filter :hakemuksenTila) ;; this because hakemuksenTila is optional in Krysp, and can be nil
                    (sort-by :muutosHetki)
                    last
                    :hakemuksenTila
                    ss/lower-case)]
    ;;
    ;; TODO: Ovatko nama tilat validit?
    ;;
    (when-not (#{nil "luonnos" "hakemus" "valmistelussa" "vastaanotettu" "tarkastettu, t\u00e4ydennyspyynt\u00f6"} app-state)
      (map (fn [paatos-xml-without-ns]
             (let [paatosdokumentinPvm-timestamp (cr/to-timestamp (get-text paatos-xml-without-ns :paatosdokumentinPvm))]
               (when (and paatosdokumentinPvm-timestamp (> (now) paatosdokumentinPvm-timestamp))
                 {:lupamaaraykset {:takuuaikaPaivat (get-text paatos-xml-without-ns :takuuaikaPaivat)
                                   :muutMaaraykset (->lupamaaraukset-text paatos-xml-without-ns)}
                  :paivamaarat    {:paatosdokumentinPvm paatosdokumentinPvm-timestamp}
                  :poytakirjat    (when-let [liitetiedot (seq (select paatos-xml-without-ns [:liitetieto]))]
                                    (map ->liite (map (fn [[k v]] {:liite v}) (cr/all-of liitetiedot))))})))
        (select xml-without-ns [:paatostieto :Paatos])))))

(permit/register-function permit/R :verdict-krysp-reader ->standard-verdicts)
(permit/register-function permit/P :verdict-krysp-reader ->standard-verdicts)
(permit/register-function permit/YA :verdict-krysp-reader ->simple-verdicts)
(permit/register-function permit/YL :verdict-krysp-reader ->simple-verdicts)
(permit/register-function permit/MAL :verdict-krysp-reader ->simple-verdicts)
(permit/register-function permit/VVVL :verdict-krysp-reader ->simple-verdicts)

(defn- ->kuntalupatunnus [asia]
  {:kuntalupatunnus (or (get-text asia [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus])
                        (get-text asia [:luvanTunnistetiedot :LupaTunnus :kuntalupatunnus]))})

(defn ->verdicts [xml ->function]
  (map
    (fn [asia]
      (let [verdict-model (->kuntalupatunnus asia)
            verdicts      (->> asia
                           (->function)
                           (cleanup)
                           (filter seq))]
        (if (seq verdicts)
          (assoc verdict-model :paatokset verdicts)
          verdict-model)))
    (enlive/select (cr/strip-xml-namespaces xml) case-elem-selector)))

(defn- buildings-summary-for-application [xml]
  (let [summary (->buildings-summary xml)]
    (when (seq summary)
      {:buildings summary})))

(permit/register-function permit/R :verdict-extras-krysp-reader buildings-summary-for-application)
