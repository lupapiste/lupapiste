(ns lupapalvelu.exports.reporting-db-schema
  ""
  (:require [lupapalvelu.application-schema :as app-schema]
            [lupapalvelu.document.schemas :as document-schemas]
            [lupapalvelu.drawing :as draw]
            [lupapalvelu.pate.schema-helper :refer [verdict-code-map]]
            [lupapalvelu.user-enums :as user-enums]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

;;
;; Report schema
;;
(def DateString (ssc/date-string "YYYY-MM-dd"))

(sc/defschema Link
  {:id sc/Str
   :permitType (sc/maybe sc/Str)})

(sc/defschema Review
  {:id             sc/Str
   :type           sc/Str
   :date           (sc/maybe DateString)
   :reviewer       (sc/maybe sc/Str)
   :verottajanTvLl (sc/maybe sc/Bool)
   :lasnaolijat    (sc/maybe sc/Str)
   :poikkeamat     (sc/maybe sc/Str)
   :huomautukset   (sc/maybe {:kuvaus sc/Str
                              (sc/optional-key :maaraAika) DateString
                              (sc/optional-key :toteaja) sc/Str
                              (sc/optional-key :toteamisHetki) DateString})
   :rakennukset [{(sc/optional-key :jarjestysnumero) sc/Str
                  (sc/optional-key :katselmusOsittainen) sc/Str
                  (sc/optional-key :kayttoonottoKytkin) sc/Bool
                  (sc/optional-key :kiinttun) sc/Str
                  (sc/optional-key :rakennusnro) sc/Str
                  (sc/optional-key :valtakunnallinenNumero) sc/Str
                  (sc/optional-key :kunnanSisainenPysyvaRakennusnumero) sc/Str
                  (sc/optional-key :toimenpideId) sc/Str
                  (sc/optional-key :rakennuksenSelite) sc/Str}]})

(sc/defschema Puolto
  ;; lupapalvelu.document.canonical-common/puolto-mapping contains a subset of values possible in KuntaGML
  (sc/enum "ei tiedossa"
           "ei puolla"
           "puoltaa"
           "ehdoilla"
           "jätetty pöydälle"
           "ei huomautettavaa"
           "ehdollinen"
           "puollettu"
           "ei puollettu"
           "ei lausuntoa"
           "lausunto"
           "kielteinen"
           "palautettu"
           "pöydälle"))

(sc/defschema Statement
  {:id sc/Str
   :antoTs ssc/Timestamp
   (sc/optional-key :antaja) {(sc/optional-key :nimi) sc/Str
                              (sc/optional-key :kuvaus) sc/Str
                              (sc/optional-key :email) sc/Str}
   (sc/optional-key :lausunto) sc/Str
   (sc/optional-key :puolto) sc/Str})

(sc/defschema KuntaRooliKoodi
  (sc/enum "Rakennuksen omistaja"
           "Rakennusvalvonta-asian hakija"
           "Rakennusvalvonta-asian laskun maksaja"
           "Rakennusvalvonta-asian lausunnon pyytäjä"
           "Jatkuvan valvonnan ilmoittaja"
           "Kehotuksen tms. saaja"
           "Uhkasakon saaja"
           "Rakennusvalvonta-asian vastineen antaja"
           "Työn suorittaja"
           "Pääsuunnittelija"
           "Rakennusvalvonnan asiamies"
           "Pöytäkirjan vastaanottaja"
           "Asian osapuoli"
           "Selityksen tai lausunnon pyytäjä"
           "Ilmoituksen tekijä"
           "Kehotuksen saaja"
           "Hakijan asiamies"
           "Rakennuksen laajarunkoisen osan arvioija"
           ;; from YA: 'rooliKoodi' values:
           "maksajan vastuuhenkilö"
           "lupaehdoista/työmaasta vastaava henkilö"
           "hankkeen vastuuhenkilö"
           "muu"
           "yhteyshenkilö"
           "työnsuorittaja"
           "hakija"

           "ei tiedossa"))

(sc/defschema VRKRooliKoodi
  (sc/enum "hakija"
           "rakennuspaikan omistaja"
           "rakennuksen omistaja"
           "lupapäätöksen toimittaminen"
           "naapuri"
           "maksaja"
           "lisätietojen antaja"
           "pääsuunnittelija"
           "rakennussuunnittelija"
           "erityissuunnittelija"
           "työnjohtaja"
           "muu osapuoli"
           "ei tiedossa"))

;; Should be (sc/constrained sc/Str (partial re-matches #"[0-9][0-9][0-9][0-9][0-9]") "Postinumero")
(def Postinumero sc/Str)

(sc/defschema Huoneistonumero
  (sc/constrained sc/Str (partial re-matches #"^[0-9][0-9][0-9]$") "Huoneistonumero"))

(sc/defschema Jakokirjain
  (sc/constrained sc/Str (partial re-matches #"^[a-zåäö]$") "Jakokirjain"))

;;(sc/defschema Porras
;;  (sc/constrained sc/Str (partial re-matches #"^[A-ZÅÄÖ]$") "Porras"))
(def Porras sc/Str)

(sc/defschema Postiosoite
  {(sc/optional-key :kunta) sc/Str
   (sc/optional-key :valtioSuomeksi) sc/Str
   (sc/optional-key :valtioKansainvalinen) sc/Str
   (sc/optional-key :osoitenimi) {:teksti sc/Str} ; Required by KuntaGML, but not always present
   (sc/optional-key :ulkomainenLahiosoite) sc/Str
   (sc/optional-key :osoitenumero) sc/Int
   (sc/optional-key :osoitenumero2) sc/Int
   (sc/optional-key :jakokirjain) Jakokirjain
   (sc/optional-key :jakokirjain2) Jakokirjain
   (sc/optional-key :porras) Porras
   (sc/optional-key :huoneisto) sc/Int
   (sc/optional-key :postinumero) Postinumero
   (sc/optional-key :postitoimipaikannimi) sc/Str
   (sc/optional-key :ulkomainenPostitoimipaikka) sc/Str
   (sc/optional-key :jarjestysnumero) sc/Int
   (sc/optional-key :pistesijainti) sc/Any ;; gml:PointPropertyType
   (sc/optional-key :aluesijainti) sc/Any ;; gml:SurfacePropertyType
   (sc/optional-key :viivasijainti) sc/Any ;; gml:CurvePropertyType
   })

(sc/defschema Yhteyshenkilo
  {:nimi {(sc/optional-key :etunimi) sc/Str
          :sukunimi sc/Str}
   (sc/optional-key :osoite) Postiosoite
   (sc/optional-key :sahkopostiosoite) sc/Str
   (sc/optional-key :faksinumero) sc/Str
   (sc/optional-key :puhelin) sc/Str ;; KuntaGML allows multiple phone numbers
   (sc/optional-key :henkilotunnus) sc/Str ;; KuntaGML: [0-9][0-9][0-9][0-9][0-9][0-9][-+A][0-9][0-9][0-9][A-Y0-9]
   (sc/optional-key :ulkomainenHenkilotunnus) sc/Str
   (sc/optional-key :vainsahkoinenAsiointiKytkin) sc/Bool})

(sc/defschema Yritys
  {(sc/optional-key :nimi) sc/Str
   (sc/optional-key :liikeJaYhteisotunnus) sc/Str ;; KuntaGML [0-9][0-9][0-9][0-9][0-9][0-9][0-9][-][0-9]
   (sc/optional-key :kayntiosoitetieto) {:kayntiosoite Postiosoite} ;; KuntaGML allows multiple addresses
   (sc/optional-key :postiosoitetieto) {:postiosoite Postiosoite} ;; KuntaGML allows multiple addresses
   (sc/optional-key :kotipaikka) sc/Str
   (sc/optional-key :faksinumero) sc/Str
   (sc/optional-key :puhelin) sc/Str ;; KuntaGML allows multiple phone numbers
   (sc/optional-key :www) sc/Str ;; URI
   (sc/optional-key :sahkopostiosoite) sc/Str
   (sc/optional-key :vainsahkoinenAsiointiKytkin) sc/Bool ;; Not present in 2.2.3
   (sc/optional-key :verkkolaskutustieto) sc/Any ;; KuntaGML yht:VerkkolaskutusTypeType
   (sc/optional-key :yhdistysRekisterinumero) sc/Str})

(sc/defschema Osapuoli
  {(sc/optional-key :kuntaRooliKoodi) KuntaRooliKoodi
   (sc/optional-key :VRKrooliKoodi) VRKRooliKoodi
   ;(sc/optional-key :rooliKoodi) sc/Str ; YA parties have just 'rooliKoodi'
   (sc/optional-key :henkilo) Yhteyshenkilo
   (sc/optional-key :yritys) Yritys
   (sc/optional-key :turvakieltoKytkin) sc/Bool
   (sc/optional-key :suoramarkkinointikieltoKytkin) sc/Bool
   (sc/optional-key :selite) sc/Str
   (sc/optional-key :postitetaanKytkin) sc/Bool
   (sc/optional-key :laskuviite) sc/Str})

#_(sc/defschema SuunnittelijaRoolikoodi
    (sc/enum "GEO-suunnittelija"
             "LVI-suunnittelija"
             "IV-suunnittelija"
             "KVV-suunnittelija"
             "pääsuunnittelija"
             "RAK-rakennesuunnittelija"
             "ARK-rakennussuunnittelija"
             "Vaikeiden töiden suunnittelija"
             "ei tiedossa"
             "rakennussuunnittelija"
             "kantavien rakenteiden suunnittelija"
             "pohjarakenteiden suunnittelija"
             "ilmanvaihdon suunnittelija"
             "kiinteistön vesi- ja viemäröintilaitteiston suunnittelija"
             "rakennusfysikaalinen suunnittelija"
             "kosteusvaurion korjaustyön suunnittelija"
             "muu"
             "Vastaava LVI-suunnittelija"))

;; Due to LP-167-2018-00873
(def SuunnittelijaRoolikoodi sc/Str)

(sc/defschema Patevyysluokka (sc/enum "AA" "A" "B" "C" "ei tiedossa" "1"))

(sc/defschema Suunnittelija
  {(sc/optional-key :suunnittelijaRoolikoodi) SuunnittelijaRoolikoodi
   (sc/optional-key :VRKrooliKoodi) VRKRooliKoodi
   (sc/optional-key :henkilo) Yhteyshenkilo
   (sc/optional-key :yritys) Yritys
   (sc/optional-key :patevyysvaatimusluokka) Patevyysluokka
   (sc/optional-key :koulutus) sc/Str
   (sc/optional-key :valmistumisvuosi) sc/Int
   (sc/optional-key :liitetieto) sc/Any
   (sc/optional-key :vaadittuPatevyysluokka) Patevyysluokka
   (sc/optional-key :toiminutVastaavissaTehtavissa) sc/Bool
   (sc/optional-key :kokemusvuodet) sc/Int
   (sc/optional-key :paatosPvm) DateString
   (sc/optional-key :paatostyyppi) (sc/enum "hyväksytty" "hylätty" "hakemusvaiheessa" "ilmoitus hyväksytty")
   (sc/optional-key :FISEpatevyyskortti) sc/Str ;; URI
   (sc/optional-key :FISEkelpoisuus) (apply sc/enum user-enums/fise-kelpoisuus-lajit)
   (sc/optional-key :muuSuunnittelijaRooli) sc/Str
   (sc/optional-key :postitetaanKytkin) sc/Bool})

(sc/defschema TyonjohtajaRooliKoodi
  (sc/enum "KVV-työnjohtaja"
           "IV-työnjohtaja"
           "erityisalojen työnjohtaja"
           "vastaava työnjohtaja"
           "työnjohtaja"
           "ei tiedossa"))

(sc/defschema Tyonjohtaja
  {(sc/optional-key :tyonjohtajaRooliKoodi) TyonjohtajaRooliKoodi
   (sc/optional-key :VRKrooliKoodi) VRKRooliKoodi
   (sc/optional-key :henkilo) Yhteyshenkilo
   (sc/optional-key :yritys) Yritys
   (sc/optional-key :patevyysvaatimusluokka) Patevyysluokka
   (sc/optional-key :vaadittuPatevyysluokka) Patevyysluokka
   (sc/optional-key :koulutus) sc/Str
   (sc/optional-key :valmistumisvuosi) sc/Int
   (sc/optional-key :liitetieto) sc/Any
   (sc/optional-key :alkamisPvm) DateString
   (sc/optional-key :paattymisPvm) DateString
   (sc/optional-key :hakemuksenSaapumisPvm) DateString
   (sc/optional-key :tyonjohtajaHakemusKytkin) sc/Bool
   (sc/optional-key :kokemusvuodet) sc/Int
   (sc/optional-key :vastaavaTyotieto) sc/Any
   (sc/optional-key :vainTamaHankeKytkin) sc/Bool
   (sc/optional-key :paatosPvm) DateString
   (sc/optional-key :paatostyyppi) (sc/enum "hyväksytty" "hylätty" "hakemusvaiheessa" "ilmoitus hyväksytty")
   (sc/optional-key :paatoksentekija) sc/Str
   (sc/optional-key :pykala) sc/Int
   (sc/optional-key :tyonjohtajaPaatosLiitetieto) sc/Any
   (sc/optional-key :sijaistettavaHlo) sc/Str
   (sc/optional-key :sijaistus) {(sc/optional-key :alkamisPvm) DateString
                                 (sc/optional-key :paattymisPvm) DateString
                                 (sc/optional-key :sijaistettavaHlo) sc/Str
                                 (sc/optional-key :sijaistettavaRooli) TyonjohtajaRooliKoodi}
   (sc/optional-key :valvottavienKohteidenMaara) sc/Int
   (sc/optional-key :vastattavatTyot) [sc/Str]})

(sc/defschema Verdict
  {:id ssc/ObjectIdStr
   (sc/optional-key :kuntalupatunnus) sc/Str
   :lupamaaraykset {(sc/optional-key :autopaikkojaEnintaan) sc/Int
                    (sc/optional-key :autopaikkojaVahintaan) sc/Int
                    (sc/optional-key :autopaikkojaRakennettava) sc/Int
                    (sc/optional-key :autopaikkojaRakennettu) sc/Int
                    (sc/optional-key :autopaikkojaKiinteistolla) sc/Int
                    (sc/optional-key :autopaikkojaUlkopuolella) sc/Int
                    (sc/optional-key :kerrosala) sc/Int
                    (sc/optional-key :kokonaisala) sc/Int
                    (sc/optional-key :rakennusoikeudellinenKerrosala) sc/Int
                    (sc/optional-key :kokoontumistilanHenkilomaara) sc/Int
                    ;; Note that KuntaGML enables more fields, these are the ones provided by Lupapiste
                    :vaaditutKatselmukset [{:katselmuksenLaji sc/Str ;; (apply sc/enum app-schema/task-data-katselmuksenLaji-values)
                                            (sc/optional-key :tarkastuksenTaiKatselmuksenNimi) sc/Str
                                            :muuTunnustieto [{:MuuTunnus {:tunnus sc/Str :sovellus sc/Str}}]
                                            }]
                    :maaraystieto [sc/Str]
                    :vaadittuErityissuunnitelmatieto [{:vaadittuErityissuunnitelma sc/Str}]

                    :vaadittuTyonjohtajatieto [sc/Str]} ;; TyonjohtajaRooliKoodi
   (sc/optional-key :paivamaarat) {(sc/optional-key :aloitettavaPvm) DateString
                                   (sc/optional-key :lainvoimainenPvm) DateString
                                   (sc/optional-key :voimassaHetkiPvm) DateString
                                   (sc/optional-key :raukeamisPvm) DateString
                                   (sc/optional-key :antoPvm) DateString
                                   (sc/optional-key :viimeinenValitusPvm) DateString
                                   (sc/optional-key :julkipanoPvm) DateString}
   :poytakirja {(sc/optional-key :paatos) sc/Str
                (sc/optional-key :paatoskoodi) (apply sc/enum (vals verdict-code-map))
                (sc/optional-key :paatoksentekija) sc/Str
                (sc/optional-key :paatospvm) DateString
                (sc/optional-key :pykala) sc/Str}
   })

(sc/defschema RakennusVarusteet
  {(sc/optional-key :aurinkopaneeliKytkin) sc/Bool
   (sc/optional-key :hissiKytkin) sc/Bool
   (sc/optional-key :kaasuKytkin) sc/Bool
   (sc/optional-key :koneellinenilmastointiKytkin) sc/Bool
   (sc/optional-key :lamminvesiKytkin) sc/Bool
   (sc/optional-key :parvekeTaiTerassiKytkin) sc/Bool
   (sc/optional-key :sahkoKytkin) sc/Bool
   (sc/optional-key :saunoja) (sc/maybe sc/Num)
   (sc/optional-key :uima-altaita) (sc/maybe sc/Num)
   (sc/optional-key :vaestonsuoja) (sc/maybe sc/Num)
   (sc/optional-key :vesijohtoKytkin) sc/Bool
   (sc/optional-key :viemariKytkin) sc/Bool})

(sc/defschema Verkostoliittymat
  {(sc/optional-key :kaapeliKytkin) sc/Bool
   (sc/optional-key :maakaasuKytkin) sc/Bool
   (sc/optional-key :sahkoKytkin) sc/Bool
   (sc/optional-key :vesijohtoKytkin) sc/Bool
   (sc/optional-key :viemariKytkin) sc/Bool})

(sc/defschema HuoneistoVarusteet
  {(sc/optional-key :ammeTaiSuihkuKytkin) sc/Bool
   (sc/optional-key :lamminvesiKytkin) sc/Bool
   (sc/optional-key :parvekeTaiTerassiKytkin) sc/Bool
   (sc/optional-key :saunaKytkin) sc/Bool
   (sc/optional-key :WCKytkin) sc/Bool})

(sc/defschema Huoneisto
  {(sc/optional-key :osoite) sc/Any ;; yht:PostiosoiteType
   (sc/optional-key :muutostapa) (sc/enum "ei tiedossa" "lisäys" "muutos" "poisto")
   (sc/optional-key :huoneluku) sc/Int
   (sc/optional-key :keittionTyyppi) (sc/enum "ei tiedossa" "keittio" "keittokomero" "keittotila" "tupakeittio")
   (sc/optional-key :huoneistoala) sc/Num
   :varusteet HuoneistoVarusteet
   (sc/optional-key :huoneistotunnus) {:huoneistonumero Huoneistonumero
                                       (sc/optional-key :jakokirjain) Jakokirjain
                                       (sc/optional-key :porras) Porras}
   (sc/optional-key :huoneistonTyyppi) (sc/enum "asuinhuoneisto" "ei tiedossa" "toimitila")
   (sc/optional-key :valtakunnallinenHuoneistotunnus) sc/Str})

;; Should be ssc/Rakennustunnus
(def ValtakunnallinenNumero sc/Str)

;; Should be ssc/Rakennusnumero
(def Rakennusnro sc/Str)

(sc/defschema Rakennustunnus
  {:jarjestysnumero sc/Int
   (sc/optional-key :valtakunnallinenNumero) ValtakunnallinenNumero
   (sc/optional-key :kunnanSisainenPysyvaRakennusnumero) sc/Str
   (sc/optional-key :kiinttun) sc/Str ;; app-schema/PropertyId
   (sc/optional-key :rakennusnro) Rakennusnro
   ; TODO: from canonical, this is always our operation-id
   :muuTunnustieto [{:MuuTunnus {:tunnus sc/Str
                                 :sovellus sc/Str}}]
   (sc/optional-key :rakennuksenSelite) sc/Str})

;; Should be
;; (sc/enum "palonkestävä" "paloapidättävä" "paloahidastava"
;;          "lähinnä paloakestävä" "lähinnä paloapidättävä" "lähinnä paloahidastava"
;;          "P0" "P1" "P2" "P3" "P1/P2" "P1/P3" "P2/P3" "P1/P2/P3")
(def Paloluokka sc/Str)

(sc/defschema RakennuksenTiedot
  {(sc/optional-key :rakennustunnus) Rakennustunnus
   (sc/optional-key :kayttotarkoitus) sc/Str ;; KuntaGML has the actual enumeration
   (sc/optional-key :rakennusluokka) sc/Str ;; KuntaGML has the actual enumeration
   (sc/optional-key :tilavuus) sc/Int
   (sc/optional-key :kokonaisala) sc/Int
   (sc/optional-key :kellarinpinta-ala) sc/Int
   (sc/optional-key :BIM) sc/Any
   (sc/optional-key :osoite) sc/Any ;; TODO
   (sc/optional-key :rinnakkaisosoite) sc/Any
   (sc/optional-key :kerrosluku) sc/Int
   (sc/optional-key :kerrosala) sc/Int
   (sc/optional-key :rakennusoikeudellinenKerrosala) sc/Int
   (sc/optional-key :rakentamistapa) (sc/enum "elementti" "paikalla" "ei tiedossa")
   (sc/optional-key :kantavaRakennusaine) {(sc/optional-key :rakennusaine) sc/Str}
   (sc/optional-key :julkisivu) {(sc/optional-key :julkisivumateriaali) sc/Str}
   (sc/optional-key :verkostoliittymat) Verkostoliittymat
   (sc/optional-key :energialuokka) (sc/enum "A" "B" "C" "D" "E" "F" "G")
   (sc/optional-key :energiatehokkuusluku) sc/Int
   (sc/optional-key :energiatehokkuusluvunYksikko) (sc/enum "kWh/m2" "kWh/brm2/vuosi")
   (sc/optional-key :paloluokka) Paloluokka
   (sc/optional-key :lammitystapa) (sc/enum "vesikeskus" "ilmakeskus" "suora sähkö" "uuni" "ei lämmitystä" "ei tiedossa")
   (sc/optional-key :lammonlahde) {(sc/optional-key :polttoaine) sc/Str}
   (sc/optional-key :varusteet) RakennusVarusteet ;; According to KuntaGML there could be many of these, but I don't grasp the meaning of having more than one
   (sc/optional-key :jaahdytysmuoto) sc/Str
   (sc/optional-key :asuinhuoneistot) {(sc/optional-key :asuntojenLkm) sc/Int
                                       (sc/optional-key :asuntojenPintaala) sc/Num
                                       (sc/optional-key :huoneisto) [Huoneisto]
                                       (sc/optional-key :jaahdytysmuoto) sc/Str}
   (sc/optional-key :liitettyJatevesijarjestelmaanKytkin) sc/Bool
})

(sc/defschema Omistajalaji
  (sc/enum "yksityinen maatalousyrittäjä"
           "muu yksityinen henkilö tai perikunta"
           "asunto-oy tai asunto-osuuskunta"
           "kiinteistö oy"
           "yksityinen yritys (osake-, avoin- tai kommandiittiyhtiö, osuuskunta)"
           "valtio- tai kuntaenemmistöinen yritys"
           "kunnan liikelaitos"
           "valtion liikelaitos"
           "pankki tai vakuutuslaitos"
           "kunta tai kuntainliitto"
           "valtio"
           "sosiaaliturvarahasto"
           "uskonnollinen yhteisö, säätiö, puolue tai yhdistys"
           "ei tiedossa"))

(sc/defschema Omistaja
  (assoc Osapuoli (sc/optional-key :omistajalaji)
         {(sc/optional-key :omistajalaji) Omistajalaji
          (sc/optional-key :muu) sc/Str}))

(sc/defschema Rakennus
  {:tiedot RakennuksenTiedot
   (sc/optional-key :rakentajatyyppi) (sc/enum "liiketaloudellinen" "muu" "ei tiedossa")
   (sc/optional-key :omistajat) [Omistaja]
   (sc/optional-key :tasosijainti) (sc/enum "maan alla" "maan tasalla" "maan yllä" "ei tiedossa")
   (sc/optional-key :piirustukset) sc/Any
   (sc/optional-key :kokoontumistilanHenkilomaara) sc/Int
   (sc/optional-key :tilapainenRakennusKytkin) sc/Bool
   (sc/optional-key :tilapainenRakennusvoimassaPvm) DateString
   (sc/optional-key :location-etrs-tm35fin) [(sc/one sc/Num "X") (sc/one sc/Num "Y")]
   (sc/optional-key :location-wgs84) [(sc/one sc/Num "X") (sc/one sc/Num "Y")]})

(sc/defschema RakennelmanKayttotarkoitus
  (apply sc/enum document-schemas/rakennelman-kayttotarkoitukset))

(sc/defschema Rakennelma
  {(sc/optional-key :kuvaus) {(sc/optional-key :kuvaus) (sc/maybe sc/Str)
                              (sc/optional-key :liite) sc/Any}
   (sc/optional-key :tunnus) {:jarjestysnumero sc/Int
                              sc/Any sc/Any}
   (sc/optional-key :kokonaisala) sc/Int
   (sc/optional-key :kiinttun) sc/Str ; app-schema/PropertyId
   (sc/optional-key :kayttotarkoitus) RakennelmanKayttotarkoitus
   (sc/optional-key :kokoontumistilanHenkilomaara) sc/Int
   (sc/optional-key :tilapainenRakennelmaKytkin) sc/Bool
   (sc/optional-key :tilapainenRakennelmavoimassaPvm) DateString})

(sc/defschema Operation
  {:id (sc/maybe sc/Str) ;; For the cases where primaryOperation is null. Yes.
   :operation app-schema/OperationName
   :primary sc/Bool
   :kuvaus (sc/maybe sc/Str)
   :nimi sc/Str
   :rakennus (sc/maybe Rakennus)
   :rakennelma (sc/maybe Rakennelma)})

(sc/defschema NewDimensions
  {(sc/optional-key :kerrosala)                      sc/Num
   (sc/optional-key :rakennusoikeudellinenKerrosala) sc/Num
   (sc/optional-key :kokonaisala)                    sc/Num
   (sc/optional-key :tilavuus)                       sc/Num})

(sc/defschema Handler ; Käsittelijä
  {:nimi  {:etunimi  sc/Str
           :sukunimi sc/Str}
   :rooli sc/Str})

(sc/defschema TyoAika
  {:startDate (sc/maybe DateString)
   :endDate (sc/maybe DateString)})

;; The keys in this map must be obligatory. The keys nested inside
;; the values can be optional. See the schema validation in
;; `lupapalvelu.exports.reporting-db/->reporting-result`.
(sc/defschema ApplicationReport
  {:id ssc/ApplicationId
   :araFunding sc/Bool
   :state app-schema/State
   :permitType (sc/enum "R" "P" "YA")
   :permitSubtype (sc/maybe sc/Str)
   :location-etrs-tm35fin [(sc/one sc/Num "X") (sc/one sc/Num "Y")] ;; [(sc/one ssc/LocationX "X") (sc/one ssc/LocationY "Y")]
   :location-wgs84 [(sc/one sc/Num "X") (sc/one sc/Num "Y")]
   :address sc/Str
   :propertyId sc/Str ;; app-schema/PropertyId
   :organization app-schema/Organization
   :municipality sc/Str
   :stateChangeTs (sc/maybe ssc/Timestamp)
   :submittedTs (sc/maybe ssc/Timestamp)
   :createdTs ssc/Timestamp
   :modifiedTs ssc/Timestamp
   :projectDescription (sc/maybe sc/Str)
   :features [draw/Feature]

   :reviews [Review]
   :statements [Statement]
   :parties [Osapuoli]
   :planners [Suunnittelija]
   :foremen [Tyonjohtaja]
   :handlers [Handler]
   :verdicts [Verdict]
   :operations [Operation]
   :links [Link]
   :poikkeamat (sc/maybe sc/Str)
   :newDimensions (sc/maybe NewDimensions)
   :tosFunction (sc/maybe sc/Str)
   :tosFunctionName (sc/maybe sc/Str)
   :reservedArea (sc/maybe sc/Str)
   :placementPermit (sc/maybe sc/Str)
   :workDates TyoAika})
