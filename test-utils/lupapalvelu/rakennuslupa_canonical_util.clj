(ns lupapalvelu.rakennuslupa-canonical-util
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.domain :as domain]
            [midje.sweet :refer [fact]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.schema-generators :as ssg]
            [sade.strings :as ss]
            [sade.util :as util :refer [random-uuid-str]]
            [schema.core :as sc]))

(def municipality "753")

(def organization "753-R")

(def nimi {:etunimi {:value "Pena"} :sukunimi {:value "Penttilä"}})

(def henkilotiedot (assoc nimi :hetu {:value "210281-9988"} :turvakieltoKytkin {:value true}))

(def osoite {:katu {:value "katu"}
              :postinumero {:value "33800"} :postitoimipaikannimi {:value "Tuonela"}
              :maa {:value "CHN"}})

(def henkilo
  {:henkilotiedot henkilotiedot
   :yhteystiedot {:puhelin {:value "+358401234567"}
                  :email {:value "pena@example.com"}}
   :osoite osoite})

(def suunnittelija-henkilo
  (assoc henkilo :henkilotiedot (dissoc henkilotiedot :turvakieltoKytkin)))

(def yritysnimi-ja-ytunnus
  {:yritysnimi {:value "Solita Oy"} :liikeJaYhteisoTunnus {:value "1060155-5"}})

(def yritys
  (merge
    yritysnimi-ja-ytunnus
    {:osoite osoite
     :yhteyshenkilo {:henkilotiedot (dissoc henkilotiedot :hetu)
                     :yhteystiedot {:email {:value "yritys@example.com"}
                                    :puhelin {:value "03-389 1380"}}}}))

(def hakija-henkilo
  {:id "hakija-henkilo" :schema-info {:name "hakija-r"
                                      :subtype "hakija"
                                      :version 1}
   :data {:henkilo (assoc henkilo :kytkimet {:vainsahkoinenAsiointiKytkin {:value true}
                                             :suoramarkkinointilupa {:value false}})}})

(def hakija-henkilo-using-ulkomainen-hetu
  (update-in hakija-henkilo [:data :henkilo :henkilotiedot] assoc :ulkomainenHenkilotunnus {:value "123456"} :not-finnish-hetu {:value true}))

(def hakija-henkilo-using-finnish-hetu
  (update-in hakija-henkilo-using-ulkomainen-hetu [:data :henkilo :henkilotiedot] assoc :not-finnish-hetu {:value false}))

(def asiamies-henkilo
  {:id "asiamies-henkilo" :schema-info {:name "hakijan-asiamies"
                                      :version 1}
   :data {:henkilo (assoc henkilo :kytkimet {:vainsahkoinenAsiointiKytkin {:value true}
                                             :suoramarkkinointilupa {:value false}})}})

(def hakija-tj-henkilo
  (assoc-in hakija-henkilo [:schema-info :name] "hakija-tj"))

(def hakija-yritys
  {:id "hakija-yritys" :schema-info {:name "hakija-r"
                                     :subtype "hakija"
                                     :version 1}
   :data {:_selected {:value "yritys"}
          :yritys (assoc-in yritys [:yhteyshenkilo :kytkimet] {:vainsahkoinenAsiointiKytkin {:value true}
                                                               :suoramarkkinointilupa {:value true}})}})

(def paasuunnittelija
  {:id "50bc85e4ea3e790c9ff7cdb2"
   :schema-info {:name "paasuunnittelija"
                 :version 1}
   :data (merge
           suunnittelija-henkilo
           {:suunnittelutehtavanVaativuusluokka {:value "AA"}}
           {:patevyys {:koulutusvalinta {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                       :patevyysluokka {:value "A"}
                       :valmistumisvuosi {:value "2010"}
                       :kokemus {:value "5"}
                       :fise {:value "http://www.ym.fi"}
                       :fiseKelpoisuus {:value "tavanomainen pääsuunnittelu (uudisrakentaminen)"}
                       :patevyydentoteaja {:value "safa"}
                       :patevyysvoimassapvm {:value "1.1.2045"}
                       :patevyystodistusurl {:value "http://example.com/1"}}}
           {:yritys yritysnimi-ja-ytunnus})})

(def suunnittelija1
  {:id   "suunnittelija1" :schema-info {:name    "suunnittelija"
                                        :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "rakennusfysikaalinen suunnittelija"}}
                {:suunnittelutehtavanVaativuusluokka {:value "C"}}
                {:patevyys {:koulutusvalinta  {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                            :patevyysluokka   {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus          {:value "5"}
                            :fise             {:value "http://www.ym.fi"}
                            :fiseKelpoisuus   {:value "tavanomainen pääsuunnittelu (uudisrakentaminen)"}
                            :patevyydentoteaja {:value "safa"}
                            :patevyysvoimassapvm {:value "1.1.2045"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def suunnittelija2
  {:id   "suunnittelija2" :schema-info {:name    "suunnittelija"
                                        :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "LVI-suunnittelija"}}
                {:suunnittelutehtavanVaativuusluokka {:value "A"}}
                {:patevyys {:koulutusvalinta  {:value "other"} :koulutus {:value "Elämän koulu"}  ;; "Muu" option ( i.e. :other-key) is selected
                            :patevyysluokka   {:value "AA"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus          {:value "5"}
                            :fise             {:value "http://www.ym.fi"}
                            :fiseKelpoisuus   {:value "tavanomainen pääsuunnittelu (uudisrakentaminen)"}
                            :patevyydentoteaja {:value "fise"}
                            :patevyysvoimassapvm {:value "1.1.2045"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def suunnittelija3
  {:id   "suunnittelija3" :schema-info {:name    "suunnittelija"
                                        :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "other"}}
                {:muuSuunnittelijaRooli {:value "ei listassa -rooli"}}
                {:suunnittelutehtavanVaativuusluokka {:value "C"}}
                {:patevyys {:koulutusvalinta  {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                            :patevyysluokka   {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus          {:value "5"}
                            :fise             {:value "http://www.ym.fi"}
                            :fiseKelpoisuus   {:value "tavanomainen pääsuunnittelu (uudisrakentaminen)"}
                            :patevyydentoteaja {:value "siirtymasaannos"}
                            :patevyysvoimassapvm {:value "1.1.2045"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def suunnittelija-old-schema-LUPA-771
  {:id   "suunnittelija-old-schema-LUPA771" :schema-info {:name    "suunnittelija"
                                                          :version 1}
   :data (merge suunnittelija-henkilo
                {:patevyys {:koulutusvalinta  {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                            :kuntaRoolikoodi  {:value "ARK-rakennussuunnittelija"}
                            :patevyysluokka   {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus          {:value "5"}
                            :fise             {:value "http://www.ym.fi"}
                            :fiseKelpoisuus   {:value "tavanomainen pääsuunnittelu (uudisrakentaminen)"}}})})

(def suunnittelija-blank-role
  {:id   "suunnittelija-blank-role" :schema-info {:name    "suunnittelija"
                                                  :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value ""}}
                {:patevyys {:koulutusvalinta  {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                            :patevyysluokka   {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus          {:value "5"}
                            :fise             {:value "http://www.ym.fi"}
                            :fiseKelpoisuus   {:value "tavanomainen pääsuunnittelu (uudisrakentaminen)"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def suunnittelija-with-ulkomainen-hetu
  (update-in suunnittelija1 [:data :henkilotiedot] assoc :ulkomainenHenkilotunnus {:value "123456"} :not-finnish-hetu {:value true}))

(def suunnitelija-using-finnish-hetu
  (update-in suunnittelija1 [:data :henkilotiedot] assoc :ulkomainenHenkilotunnus {:value "123456"} :not-finnish-hetu {:value false}))

(def maksaja-henkilo
  {:id   "maksaja-henkilo" :schema-info {:name    "maksaja"
                                         :version 1}
   :data {:henkilo henkilo}})

(def maksaja-yritys
  {:id   "maksaja-yritys" :schema-info {:name "maksaja" :version 1}
   :data {:_selected {:value "yritys"}
          :yritys    (merge yritys
                            {:verkkolaskutustieto
                             {:ovtTunnus         {:value "003712345671"}
                              :verkkolaskuTunnus {:value "laskutunnus-1234"}
                              :valittajaTunnus   {:value "BAWCFI22"}}})}})

(def tyonjohtaja
  {:id          "tyonjohtaja"
   :schema-info {:name "tyonjohtaja", :version 1}
   :data        (merge suunnittelija-henkilo
                       {:kuntaRoolikoodi        {:value "KVV-työnjohtaja"}
                        :patevyys-tyonjohtaja   {:koulutusvalinta            {:value "other"}, :koulutus {:value "Koulutus"}   ;; "Muu" option ( i.e. :other-key) is selected
                                                 :patevyysvaatimusluokka     {:value "A"}
                                                 :valmistumisvuosi           {:value "2010"}
                                                 :tyonjohtajaHakemusKytkin   {:value "hakemus"}
                                                 :kokemusvuodet              {:value "3"}
                                                 :valvottavienKohteidenMaara {:value "9"}
                                                 :patevyydentoteaja          {:value "seti"}
                                                 :patevyysvoimassapvm        {:value "1.1.2049"}}
                        :vastattavatTyotehtavat {:kiinteistonVesiJaViemarilaitteistonRakentaminen {:value true}
                                                 :kiinteistonilmanvaihtolaitteistonRakentaminen   {:value true}
                                                 :maanrakennustyo                                 {:value true}
                                                 :rakennelmaTaiLaitos                             {:value true}
                                                 :muuMika                                         {:value true}
                                                 :muuMikaValue                                    {:value "Muu tyotehtava"}}
                        :yritys                 yritysnimi-ja-ytunnus
                        :sijaistus              {:sijaistettavaHloEtunimi  {:value "Jaska"}
                                                 :sijaistettavaHloSukunimi {:value "Jokunen"}
                                                 :alkamisPvm               {:value "13.02.2014"}
                                                 :paattymisPvm             {:value "20.02.2014"}}})})

(def tyonjohtaja-blank-role-and-blank-qualification
  (-> tyonjohtaja
    (assoc-in [:data :kuntaRoolikoodi :value] nil)
    (assoc-in [:data :patevyys-tyonjohtaja :patevyysvaatimusluokka :value] "ei tiedossa")
    (assoc-in [:data :patevyys-tyonjohtaja :tyonjohtajaHakemusKytkin :value] "nimeaminen")))

(def tyonjohtajan-sijaistus-blank-dates
  (-> tyonjohtaja
    (util/dissoc-in [:data :sijaistus :alkamisPvm])
    (assoc-in  [:data :sijaistus :paattymisPvm :value] "")))

(def tyonjohtaja-v2
  {:id          "tyonjohtaja-v2"
   :schema-info {:name    "tyonjohtaja-v2"
                 :version 1
                 :op      {:name "tyonjohtajan-nimeaminen-v2"}}
   :data        (merge
                  suunnittelija-henkilo
                  (select-keys (:data tyonjohtaja) [:kuntaRoolikoodi :sijaistus :yritys])
                  {:fillMyInfo             {:value nil}
                   :patevyysvaatimusluokka {:value "A"}
                   :patevyys-tyonjohtaja   {:koulutusvalinta            {:value "arkkitehtiylioppilas"}
                                            :koulutus                   {:value ""}
                                            :valmistumisvuosi           {:value "2010"}
                                            :kokemusvuodet              {:value "3"}
                                            :valvottavienKohteidenMaara {:value "9"}}
                   :vastattavatTyotehtavat {:rakennuksenPurkaminen                  {:value true}
                                            :ivLaitoksenKorjausJaMuutostyo          {:value true}
                                            :uudisrakennustyoIlmanMaanrakennustoita {:value true}
                                            :maanrakennustyot                       {:value true}
                                            :uudisrakennustyoMaanrakennustoineen    {:value false}
                                            :ulkopuolinenKvvTyo                     {:value false}
                                            :rakennuksenMuutosJaKorjaustyo          {:value false}
                                            :linjasaneeraus                         {:value false}
                                            :ivLaitoksenAsennustyo                  {:value false}
                                            :sisapuolinenKvvTyo                     {:value true}
                                            :muuMika                                {:value true}
                                            :muuMikaValue                           {:value "Muu tyotehtava"}}
                   :muutHankkeet           {:0 {:katuosoite         {:value "katuosoite"}
                                                :3kk                {:value "1"}
                                                :9kk                {:value "3"}
                                                :vaihe              {:value "R"}
                                                :6kk                {:value "2"}
                                                :autoupdated        {:value false}
                                                :rakennustoimenpide {:value "purkutoimenpide"}
                                                :kokonaisala        {:value "120"}
                                                :12kk               {:value "4"}
                                                :luvanNumero        {:value "123"}}}
                   :tyonjohtajaHanketieto  {:taysiaikainenOsaaikainen {:value "taysiaikainen"}
                                            :kaytettavaAika           {:value "3"}
                                            :kayntienMaara            {:value "3"}
                                            :hankeKesto               {:value "3"}}
                   :tyonjohtajanHyvaksynta {:tyonjohtajanHyvaksynta {:value true}
                                            :foremanHistory         {:value nil}}})})

(def tyonjohtaja-v3
  {:id          "tyonjohtaja-v3"
   :schema-info {:name    "tyonjohtaja-v3"
                 :version 1
                 :op      {:name "tyonjohtajan-nimeaminen-v3"}}
   :data        (merge
                  suunnittelija-henkilo
                  (select-keys (:data tyonjohtaja-v2) [:kuntaRoolikoodi :sijaistus :yritys :vastattavatTyotehtavat
                                                       :tyonjohtajaHanketieto :muutHankkeet :tyonjohtajanHyvaksynta
                                                       :fillMyInfo :patevyysvaatimusluokka])
                  {:patevyys-tyonjohtaja {:koulutusvalinta            {:value "arkkitehtiylioppilas"}
                                          :koulutus                   {:value ""}
                                          :valmistumisvuosi           {:value "2010"}
                                          :patevyydentoteaja          {:value "fise"}
                                          :patevyysvoimassapvm        {:value "1.1.2049"}
                                          :patevyystodistusurl        {:value "http://example.com/todistus"}
                                          :kokemusvuodet              {:value "3"}
                                          :valvottavienKohteidenMaara {:value "9"}}
                   :foremanConsent       {:value true}
                   :foremanActions       {:value nil}})
   :tj-document true})

(def rakennuspaikka
  {:id   "rakennuspaikka" :schema-info {:name    "rakennuspaikka"
                                        :version 1
                                        :type    :location}
   :data {:kiinteisto      {:tilanNimi      {:value "Hiekkametsa"}
                            :maaraalaTunnus {:value ""}}
          :hallintaperuste {:value "oma"}
          :kaavatilanne    {:value "oikeusvaikutteinen yleiskaava"}}})

(def rakennuspaikka-ilman-ilmoitusta
  {:id   "rakennuspaikka-ilman-ilmoitusta" :schema-info {:name    "rakennuspaikka-ilman-ilmoitusta"
                                                         :version 1}
   :data {:kiinteisto      {:tilanNimi      {:value "Eramaa"}
                            :maaraalaTunnus {:value ""}}
          :hallintaperuste {:value "vuokra"}
          :kaavatilanne    {:value "oikeusvaikutukseton yleiskaava"}}})

(def common-rakennus
  {:rakennuksenOmistajat {:0 {:_selected    {:value "henkilo"}
                              :henkilo      henkilo
                              :omistajalaji {:value "muu yksityinen henkilö tai perikunta"}}
                          :1 {:_selected    {:value "yritys"}
                              :yritys       yritys
                              :omistajalaji {:value "yksityinen yritys (osake-, avoin- tai kommandiittiyhtiö, osuuskunta)"}}}
   :kaytto               {:rakentajaTyyppi                 {:value "muu"}
                          :kayttotarkoitus                 {:value "012 kahden asunnon talot"}
                          :tilapainenRakennusKytkin        {:value true}
                          :tilapainenRakennusvoimassaPvm   {:value "05.05.2019"}
                          :valiaikainenRakennusKytkin      {:value true}
                          :valiaikainenRakennusvoimassaPvm {:value "03.02.2024"}
                          :esteetonKytkin                  {:value true}}
   :mitat                {:tilavuus                       {:value "1500"}
                          :kokonaisala                    {:value "1000"}
                          :kellarinpinta-ala              {:value "100"}
                          :kerrosluku                     {:value "2"}
                          :kerrosala                      {:value "180"}
                          :rakennusoikeusValinta          {:value "kerrosala"}
                          :rakennusoikeudellinenKerrosala {:value "160"}
                          :rakennusoikeusKuutiometreina   {:value "123"}
                          :harjanKorkeusrakennusalalla    {:value "4.5678"}}
   :rakenne              {:rakentamistapa      {:value "elementti"}
                          :kantavaRakennusaine {:value "puu"}
                          :muuRakennusaine     {:value ""}
                          :julkisivu           {:value "puu"}}
   :lammitys             {:lammitystapa    {:value "vesikeskus"}
                          :lammonlahde     {:value "other"}
                          :muu-lammonlahde {:value "polttopuillahan tuo"}}
   :varusteet            {:hissiKytkin                         {:value true}
                          :kaasuKytkin                         {:value true}
                          :koneellinenilmastointiKytkin        {:value true}
                          :sahkoKytkin                         {:value true}
                          :saunoja                             {:value "1"}
                          :vaestonsuoja                        {:value "1"}
                          :vesijohtoKytkin                     {:value true}
                          :viemariKytkin                       {:value true}
                          :lamminvesiKytkin                    {:value true}
                          :aurinkopaneeliKytkin                {:value true}
                          :liitettyJatevesijarjestelmaanKytkin {:value true}
                          :kokoontumistilanHenkilomaara        {:value "10"}}
   :verkostoliittymat    {:kaapeliKytkin   {:value true}
                          :maakaasuKytkin  {:value true}
                          :sahkoKytkin     {:value true}
                          :vesijohtoKytkin {:value true}
                          :viemariKytkin   {:value true}}
   :luokitus             {:paloluokka                   {:value "P1"}
                          :energialuokka                {:value "C"}
                          :energiatehokkuusluku         {:value "124"}
                          :energiatehokkuusluvunYksikko {:value "kWh/m2"}}
   :huoneistot           {:0 {:muutostapa              {:value "lisäys"}
                              :porras                  {:value "A"}
                              :huoneistonumero         {:value "1"}
                              :jakokirjain             {:value "a"}
                              :huoneistoTyyppi         {:value "asuinhuoneisto"}
                              :huoneistoala            {:value "56"}
                              :huoneluku               {:value "66"}
                              :keittionTyyppi          {:value "keittio"}
                              :parvekeTaiTerassiKytkin {:value true}
                              :WCKytkin                {:value true}
                              :pysyvaHuoneistotunnus   {:value "123456789"}}
                          :1 {:muutostapa            {:value "muutos"}
                              :porras                {:value "A"}
                              :huoneistonumero       {:value "2"}
                              :jakokirjain           {:value "a"}
                              :huoneistoTyyppi       {:value "toimitila"}
                              :huoneistoala          {:value "02"}
                              :pysyvaHuoneistotunnus {:value "123"}
                              :huoneluku             {:value "12"}
                              :keittionTyyppi        {:value "keittokomero"}
                              :ammeTaiSuihkuKytkin   {:value true}
                              :saunaKytkin           {:value true}
                              :lamminvesiKytkin      {:value true}}}})

(def document-building
  {:id       "5bcdb8eaf70b0924847661f2"
   :vtj-prt  "199887766E"
   :building {:kiinttun             "75300301050006"
              :rakennuksenOmistajat {:0 {:_selected        "yritys"
                                         :henkilo          {:userId        nil
                                                            :henkilotiedot {:etunimi           ""
                                                                            :sukunimi          ""
                                                                            :hetu              nil
                                                                            :turvakieltoKytkin false}
                                                            :osoite        {:katu                 ""
                                                                            :postinumero          ""
                                                                            :postitoimipaikannimi ""
                                                                            :maa                  "FIN"}
                                                            :yhteystiedot  {:puhelin "" :email ""}
                                                            :kytkimet      {:suoramarkkinointilupa false}}
                                         :yritys           {:companyId            nil
                                                            :yritysnimi           "Testiyritys 9242"
                                                            :liikeJaYhteisoTunnus "1234567-1"
                                                            :osoite               {:katu                 "Testikatu 1 A 9242"
                                                                                   :postinumero          "00380"
                                                                                   :postitoimipaikannimi "HELSINKI"
                                                                                   :maa                  "FIN"}
                                                            :yhteyshenkilo        {:henkilotiedot {:etunimi           ""
                                                                                                   :sukunimi          ""
                                                                                                   :turvakieltoKytkin false}
                                                                                   :yhteystiedot  {:puhelin "" :email ""}}}
                                         :omistajalaji     nil
                                         :muu-omistajalaji ""}
                                     :1 {:_selected "yritys"
                                         :yritys    {:liikeJaYhteisoTunnus "1234567-1"
                                                     :osoite               {:katu                 "Testikatu 1 A 9240"
                                                                            :postinumero          "00380"
                                                                            :postitoimipaikannimi "HELSINKI"}
                                                     :yritysnimi           "Testiyritys 9240"}}
                                     :2 {:_selected "yritys"
                                         :yritys    {:liikeJaYhteisoTunnus "1234567-1"
                                                     :osoite               {:katu                 "Testikatu 1 A 9241"
                                                                            :postinumero          "00380"
                                                                            :postitoimipaikannimi "HELSINKI"}
                                                     :yritysnimi           "Testiyritys 9241"}}
                                     :3 {:_selected "yritys"
                                         :yritys    {:liikeJaYhteisoTunnus "1234567-1"
                                                     :osoite               {:katu                 "Testikatu 1 A 9239"
                                                                            :postinumero          "00380"
                                                                            :postitoimipaikannimi "HELSINKI"}
                                                     :yritysnimi           "Testiyritys 9239"}}}
              :varusteet            {:viemariKytkin                       true
                                     :saunoja                             ""
                                     :vesijohtoKytkin                     true
                                     :hissiKytkin                         false
                                     :vaestonsuoja                        ""
                                     :kaasuKytkin                         false
                                     :aurinkopaneeliKytkin                false
                                     :liitettyJatevesijarjestelmaanKytkin false
                                     :koneellinenilmastointiKytkin        true
                                     :sahkoKytkin                         true
                                     :lamminvesiKytkin                    true
                                     :kokoontumistilanHenkilomaara        ""}
              :rakennusnro          "002"
              :verkostoliittymat    {:viemariKytkin   true
                                     :vesijohtoKytkin true
                                     :sahkoKytkin     true
                                     :maakaasuKytkin  false
                                     :kaapeliKytkin   false}
              :kaytto               {:rakentajaTyyppi nil :kayttotarkoitus "021 rivitalot"}
              :huoneistot           {:0 {:WCKytkin                true
                                         :huoneistoTyyppi         "asuinhuoneisto"
                                         :keittionTyyppi          "keittio"
                                         :huoneistoala            "108"
                                         :huoneluku               ""
                                         :jakokirjain             ""
                                         :ammeTaiSuihkuKytkin     true
                                         :saunaKytkin             true
                                         :huoneistonumero         "001"
                                         :porras                  "A"
                                         :lamminvesiKytkin        true
                                         :parvekeTaiTerassiKytkin true}
                                     :1 {:WCKytkin                true
                                         :huoneistoTyyppi         "asuinhuoneisto"
                                         :keittionTyyppi          "keittio"
                                         :huoneistoala            "106"
                                         :huoneluku               "4"
                                         :ammeTaiSuihkuKytkin     true
                                         :saunaKytkin             true
                                         :huoneistonumero         "002"
                                         :porras                  "A"
                                         :lamminvesiKytkin        true
                                         :parvekeTaiTerassiKytkin true}}
              :lammitys             {:lammitystapa    "vesikeskus"
                                     :lammonlahde     "kevyt polttoöljy"
                                     :muu-lammonlahde ""}

              :kunnanSisainenPysyvaRakennusnumero ""

              :rakenne                 {:rakentamistapa      "paikalla"
                                        :kantavaRakennusaine "tiili"
                                        :muuRakennusaine     ""
                                        :julkisivu           "puu"
                                        :muuMateriaali       ""}
              :osoite                  {:osoitenumero2        "5"
                                        :huoneisto            ""
                                        :jakokirjain          ""
                                        :kunta                "245"
                                        :jakokirjain2         ""
                                        :postinumero          "04200"
                                        :porras               ""
                                        :osoitenumero         "3"
                                        :postitoimipaikannimi "KERAVA"
                                        :maa                  "FIN"
                                        :lahiosoite           "Kyllikintie"}
              :mitat                   {:tilavuus                       "837"
                                        :kerrosala                      "281"
                                        :rakennusoikeudellinenKerrosala ""
                                        :kokonaisala                    "281"
                                        :kerrosluku                     "2"
                                        :kellarinpinta-ala              ""}
              :manuaalinen_rakennusnro ""
              :luokitus                {:energialuokka                nil
                                        :energiatehokkuusluku         ""
                                        :energiatehokkuusluvunYksikko "kWh/m2"
                                        :paloluokka                   "P1"}
              :valtakunnallinenNumero  "199887766E"}})

(def uusi-rakennus
  {:id          "uusi-rakennus"
   :created     2
   :schema-info {:name    "uusiRakennus"
                 :version 1
                 :op      {:name        "kerrostalo-rivitalo"
                           :description "kerrostalo-rivitalo-kuvaus"
                           :id          "kerrostalo-rivitalo-id"}}
   :data        (assoc common-rakennus :tunnus {:value "A"})})

(def uusi-rakennus-with-rakennusluokka-and-kayttotarkoitus
  {:id          "uusi-rakennus"
   :created     2
   :schema-info {:name    "uusiRakennus"
                 :version 1
                 :op      {:name        "kerrostalo-rivitalo"
                           :description "kerrostalo-rivitalo-kuvaus"
                           :id          "kerrostalo-rivitalo-id"}}
   :data        (assoc common-rakennus :tunnus {:value "A"}
                       :kaytto {:kayttotarkoitus {:value "032 luhtitalot"}
                                :rakennusluokka  {:value "0110 omakotitalot"}})})

(def rakennuksen-muuttaminen
  {:id          "muuttaminen"
   :created     1
   :schema-info {:name    "rakennuksen-muuttaminen"
                 :version 1
                 :op      {:name "muu-laajentaminen"
                           :id   "muu-laajentaminen-id"}}
   :data        (conj
                  common-rakennus
                  {:rakennusnro                    {:value "001"}
                   :buildingId                     {:value "000"}
                   :valtakunnallinenNumero         {:value "1234567892"}
                   :perusparannuskytkin            {:value true}
                   :rakennustietojaEimuutetaKytkin {:value true}
                   :muutostyolaji                  {:value "muut muutostyöt"}})})

(def laajentaminen
  {:id          "laajennus"
   :created     3
   :schema-info {:name    "rakennuksen-laajentaminen"
                 :version 1
                 :op      {:name "laajentaminen"
                           :id   "laajentaminen-id"}}
   :data        (conj
                  common-rakennus
                  {:rakennusnro             {:value "001"}
                   :buildingId              {:value "000"}
                   :manuaalinen_rakennusnro {:value "002"}
                   :laajennuksen-tiedot     {:perusparannuskytkin {:value true}
                                             :mitat               {:tilavuus                       {:value "1500"}
                                                                   :kerrosala                      {:value "180"}
                                                                   :rakennusoikeusValinta          {:value "kerrosala"}
                                                                   :rakennusoikeudellinenKerrosala {:value "160"}
                                                                   :kokonaisala                    {:value "-10"}
                                                                   :huoneistoala                   {:0 {:pintaAla             {:value "150"}
                                                                                                        :kayttotarkoitusKoodi {:value "asuntotilaa(ei vapaa-ajan asunnoista)"}}
                                                                                                    :1 {:pintaAla             {:value "-10"}
                                                                                                        :kayttotarkoitusKoodi {:value "varastotilaa"}}}}}})})

(def purku {:id          "purku"
            :created     4
            :schema-info {:name    "purkaminen"
                          :version 1
                          :op      {:name "purkaminen"
                                    :id   "purkaminen-id"}}
            :data        (conj
                           (->
                             common-rakennus
                             (dissoc :huoneistot)
                             (dissoc :lammitys)
                             (dissoc :verkostoliittymat)
                             (dissoc :varusteet)
                             (dissoc :luokitus))
                           {:rakennusnro        {:value "001"}
                            :buildingId         {:value "000"}
                            :poistumanAjankohta {:value "17.04.2013"},
                            :poistumanSyy       {:value "tuhoutunut"}})})

(def etvra {:id          "erityista-toimintaa"
            :created     5
            :schema-info {:name    "erityista-toimintaa-varten-rakennettava-alue"
                          :version 1
                          :op      {:name "erityista-toimintaa-alue"
                                    :id   "etvra-id"}}
            :data        {:tyyppi   {:value "tori"}
                          :kuvaus   {:value "Torille!"}
                          :pintaAla {:value "123"}}})

(defn sijoittamislupa-base [name]
  {:id          "sijoittamislupa"
   :created     1
   :schema-info {:name    name
                 :version 1
                 :op      {:name        name
                           :description "Sijoittamislupa"
                           :id          "sijoittamislupa-id"}}
   :data        {:tunnus                                       {:value "sijoittaminen"}
                 :kuvaus                                       {:value "Sijoittamislupa"}
                 :hankkeen-tyyppi                              {:value "uusiRakennus"}
                 :uusiRakennus                                 common-rakennus
                 :rakennelma                                   {}
                 :erityista-toimintaa-varten-rakennettava-alue {}}})

(def sijoittamislupa-v2
  (-> (sijoittamislupa-base "sijoittamislupa-v2")
      ;; puhtaan-siirtyman-lupa section since it was included in v2
      (assoc-in [:data :puhtaan-siirtyman-lupa]
                {:puhtaan-siirtyman-lupa-enabled {:value false}
                 :tyyppi                         {:value nil}})))

(def sijoittamislupa-v3
  (sijoittamislupa-base "sijoittamislupa-v3"))

(def puhtaan-siirtyman-sijoittamislupa
  (-> (sijoittamislupa-base "puhtaan-siirtyman-sijoittamislupa")
      (assoc-in [:data :puhtaan-siirtyman-lupa]
                {:tyyppi {:value "maa-aines"}})))

(def aurinkopaneeli
  {:id          "muu-rakentaminen-1"
   :schema-info {:name    "kaupunkikuvatoimenpide"
                 :op      {:id   "muu-rakentaminen-id"
                           :name "muu-rakentaminen"}
                 :version 1}
   :created     4
   :data        {:tunnus                          {:value "muu2"}
                 :kokonaisala                     {:value "6"}
                 :kayttotarkoitus                 {:value "Aurinkopaneeli"}
                 :kuvaus                          {:value "virtaa maailmaan"}
                 :valtakunnallinenNumero          {:value "1940427695"}
                 :tilapainenRakennelmaKytkin      {:value true}
                 :tilapainenRakennelmavoimassaPvm {:value "10.11.2019"}}})

(def aidan-rakentaminen {:data        {:kokonaisala     {:value "0"}
                                       :kayttotarkoitus {:value "Aita"}
                                       :kuvaus          { :value "Aidan rakentaminen rajalle"}}
                         :id          "aidan-rakentaminen"
                         :created     5
                         :schema-info {:op      {:id   "kaupunkikuva-id"
                                                 :name "aita"}
                                       :name    "kaupunkikuvatoimenpide-ei-tunnusta"
                                       :version 1}})

(def puun-kaataminen {:created     6
                      :data        { :kuvaus {:value "Puun kaataminen"}}
                      :id          "puun kaataminen"
                      :schema-info {:op      {:id      "5177ad63da060e8cd8348e32"
                                              :name    "puun-kaataminen"
                                              :created 1366797667137}
                                    :name    "maisematyo"
                                    :version 1}})

(def hankkeen-kuvaus-minimum {:id          "Hankkeen kuvaus"
                              :schema-info {:name "hankkeen-kuvaus-minimum" :version 1 :order 1 :subtype :hankkeen-kuvaus},
                              :data        {:kuvaus {:value "Uuden rakennuksen rakentaminen tontille."}}})

(def hankkeen-kuvaus
  (-> hankkeen-kuvaus-minimum
      (assoc-in [:data :poikkeamat] {:value "Ei poikkeamisia"})
      (assoc-in [:schema-info :name] "hankkeen-kuvaus")))

(def link-permit-data-kuntalupatunnus {:id "123-123-123-123" :type "kuntalupatunnus"})
(def link-permit-data-lupapistetunnus {:id "LP-753-2013-00099" :type "lupapistetunnus"})
(def app-linking-to-us {:id "LP-753-2013-00008"})

(def documents [hankkeen-kuvaus
                hakija-henkilo
                asiamies-henkilo
                hakija-yritys
                paasuunnittelija
                suunnittelija1
                suunnittelija2
                suunnittelija3
                maksaja-henkilo
                maksaja-yritys
                tyonjohtaja
                rakennuspaikka
                rakennuksen-muuttaminen
                uusi-rakennus
                laajentaminen
                aidan-rakentaminen
                puun-kaataminen
                purku
                etvra])

(def documents-ilman-ilmoitusta [hankkeen-kuvaus
                                 hakija-henkilo
                                 asiamies-henkilo
                                 paasuunnittelija
                                 maksaja-yritys
                                 rakennuspaikka-ilman-ilmoitusta
                                 rakennuksen-muuttaminen])

(def documents-with-rakennusluokka-and-kayttotarkoitus
  (conj (filter #(not= "uusi-rakennus" (:id %)) documents)
        uusi-rakennus-with-rakennusluokka-and-kayttotarkoitus))

(defn op-info [doc]
  (select-keys (-> doc :schema-info :op) [:id :name :description]))

(defn- randomize-versions [a]
  (if (> 0.5 (rand))
    (assoc a :versions [])
    a))

(def application-rakennuslupa
  {:id                  "LP-753-2013-00001"
   :permitType          "R"
   :schema-version      1
   :organization        organization
   :municipality        municipality
   :auth                [{:id        "777777777777777777000020"
                          :firstName "Pena"
                          :lastName  "Panaani"
                          :username  "pena"
                          :type      "owner"
                          :role      "writer"}]
   :history             [{:state "open"
                          :ts    12340
                          :user  {:id        "777777777777777777000020"
                                  :firstName "Pena"
                                  :lastName  "Panaani"
                                  :username  "pena"
                                  :role      "applicant"}}
                         {:state "submitted"
                          :ts    12345
                          :user  {:id        "777777777777777777000020"
                                  :firstName "Pena"
                                  :lastName  "Panaani"
                                  :username  "pena"
                                  :role      "applicant"}}]
   :state               "submitted"
   :opened              1354532324658
   :submitted           1354532324658
   :location            [408048 6693225]
   :location-wgs84      [25.33294 60.36501]
   :handlers            [{:firstName "Sonja"
                          :lastName  "Sibbo"
                          :name      {:fi "Käsittelijä"
                                      :sv "Handläggare"
                                      :en "Handler"}
                          :general   true}]
   ;; NOTE: To test linkkiPatevyysrekisteriin attachment data on reporting_db_test
   ;; add a :target data to one of the attachments here, pointing to suunnittelija1?
   :attachments         [(->> (ssg/generate (dissoc att/Attachment (sc/optional-key :metadata)
                                                                   (sc/optional-key :archiveOnly)))
                              randomize-versions
                              ((fn [a] (assoc a :latestVersion (-> a :versions last)))))]
   :title               "s"
   :created             1354532324658
   :started             1364532324691
   :documents           documents
   :propertyId          "21111111111111"
   :modified            1354532324691
   :address             "Katutie 54"
   :tags                [{:id "tag1" :label "avainsana"}
                         {:id "tag2" :label "toinen avainsana"}]
   :statements          [{:given     1368080324142
                          :id        "518b3ee60364ff9a63c6d6a1"
                          :person    {:text  "Paloviranomainen"
                                      :name  "Sonja Sibbo"
                                      :email "sonja.sibbo@sipoo.fi"
                                      :id    "516560d6c2e6f603beb85147"}
                          :requested 1368080102631
                          :state     "given"
                          :status    "ehdollinen"
                          :text      "Savupiippu pitää olla."}
                         {:id        "518b3ee60364ff9a63c6d6a2"
                          :person    {:text  "Paloviranomainen"
                                      :name  "Sonja Sibbo"
                                      :email "sonja.sibbo@sipoo.fi"
                                      :id    "516560d6c2e6f603beb85147"}
                          :requested 1368080102631
                          :state     "draft"
                          :status    "ehdollinen"
                          :text      "Lausunto tulossa..."}]
   :neighbors           ctc/neighbors
   :primaryOperation    (op-info rakennuksen-muuttaminen)
   :secondaryOperations (map op-info [uusi-rakennus
                                      laajentaminen
                                      aidan-rakentaminen
                                      puun-kaataminen
                                      purku
                                      etvra])
   :tosFunction         "00 00 00 01"
   :tosFunctionName     "tos menettely"})

(defn drop-etvra
  "Remove traces of ETVRA fields from application as some tests test against earlier KuntaGML versions."
  [application]
  (-> application
      (update :documents
              (fn [docs]
                (filterv (fn [{:keys [id]}] (not= "erityista-toimintaa" id)) docs)))
      (update :secondaryOperations
              (fn [ops]
                (filterv (fn [{:keys [id]}] (not= "etvra-id" id)) ops)))))

(def application-rakennuslupa-with-tasks
  (merge application-rakennuslupa {:tasks [{:id          "5d19f6247ad0792efa41a554"
                                            :data        {:katselmuksenLaji {:value "rakennuksen paikan merkitseminen"}}
                                            :source      {:id "1a156dd40e40adc8ee064463"}
                                            :schema-info {:name "task-katselmus"}
                                            :taskname    "Katselmus2"}
                                           {:id          "5d19f6247ad0792efa41a555"
                                            :data        {:katselmuksenLaji {:value "muu katselmus"}}
                                            :source      {:id "1a156dd40e40adc8ee064463"}
                                            :schema-info {:name "task-katselmus"}
                                            :taskname    "Katselmus"}]}))

(fact "valid application-rakennuslupa"
  (ctc/validate-all-documents application-rakennuslupa))

(fact "valid application-rakennuslupa-with-tasks"
  (ctc/validate-all-documents application-rakennuslupa-with-tasks))

(def application-rakennuslupa-ilman-ilmoitusta (assoc application-rakennuslupa :documents documents-ilman-ilmoitusta))

(fact "valid application-rakennuslupa-ilman-ilmoitusta"
  (ctc/validate-all-documents application-rakennuslupa-ilman-ilmoitusta))

(def application-kayttotarkoitus-rakennusluokka
  (assoc application-rakennuslupa :documents documents-with-rakennusluokka-and-kayttotarkoitus))

(fact "valid application-rakennuslupa-ilman-ilmoitusta"
  (ctc/validate-all-documents application-rakennuslupa-ilman-ilmoitusta))

(def application-tyonjohtajan-nimeaminen
  (merge application-rakennuslupa {:id               "LP-753-2013-00002"
                                   :organization     "753-R"
                                   :state            "submitted"
                                   :submitted        1426247899490
                                   :primaryOperation {:name    "tyonjohtajan-nimeaminen"
                                                      :id      "5272668be8db5aaa01084601"
                                                      :created 1383229067483}
                                   :permitSubtype    "tyonjohtaja-hakemus"
                                   :documents        [hakija-henkilo
                                                      maksaja-henkilo
                                                      tyonjohtaja
                                                      hankkeen-kuvaus-minimum]
                                   :linkPermitData   [link-permit-data-kuntalupatunnus]
                                   :appsLinkingToUs  [app-linking-to-us]}))

(fact "valid application-tyonjohtajan-nimeaminen"
  (ctc/validate-all-documents application-tyonjohtajan-nimeaminen))


(def application-tyonjohtajan-nimeaminen-v2
  (merge application-tyonjohtajan-nimeaminen
         {:id               "LP-753-2013-00003"
          :submitted        1426247899491
          :primaryOperation {:name    "tyonjohtajan-nimeaminen-v2"
                             :id      "5272668be8db5aaa01084601"
                             :created 1383229067483}
          :documents        [hakija-tj-henkilo
                             maksaja-henkilo
                             tyonjohtaja-v2
                             hankkeen-kuvaus-minimum]}))

;; TJ-V3 messages are generated from regular R applications
;; see: main-app-to-foreman-v3-app
(def application-tyonjohtajan-nimeaminen-v3
  (merge application-rakennuslupa
         {:id               "LP-753-2013-00004"
          :submitted        1426247899492
          :documents        (->> documents
                                 (remove (fn [x] (= "tyonjohtaja" (:id x))))
                                 (into [tyonjohtaja-v3]))}))

(fact "valid application-tyonjohtajan-nimeaminen-v3"
  (ctc/validate-all-documents application-tyonjohtajan-nimeaminen-v3))

(fact "valid application-tyonjohtajan-nimeaminen-v2"
  (ctc/validate-all-documents application-tyonjohtajan-nimeaminen-v2))

(def application-suunnittelijan-nimeaminen
  (merge application-rakennuslupa
         {:id               "LP-753-2013-00003"
          :organization     "753-R"
          :state            "submitted"
          :submitted        1426247899490
          :propertyId       "75341600550007"
          :primaryOperation {:name    "suunnittelijan-nimeaminen"
                             :id      "527b3392e8dbbb95047a89de"
                             :created 1383805842761}
          :documents        [hakija-henkilo
                             maksaja-henkilo
                             suunnittelija1
                             suunnittelija-with-ulkomainen-hetu
                             suunnitelija-using-finnish-hetu
                             hankkeen-kuvaus-minimum]
          :linkPermitData   [link-permit-data-lupapistetunnus]
          :appsLinkingToUs  [app-linking-to-us]}))

(def application-suunnittelijan-nimeaminen-muu
  (merge application-rakennuslupa
         {:id               "LP-753-2013-00003"
          :organization     "753-R"
          :state            "submitted"
          :submitted        1426247899490
          :propertyId       "75341600550007"
          :primaryOperation {:name    "suunnittelijan-nimeaminen"
                             :id      "527b3392e8dbbb95047a89de"
                             :created 1383805842761}
          :documents        [hakija-henkilo
                             maksaja-henkilo
                             suunnittelija3
                             hankkeen-kuvaus-minimum]
          :linkPermitData   [link-permit-data-lupapistetunnus]
          :appsLinkingToUs  [app-linking-to-us]}))

(fact "valid application-suunnittelijan-nimeaminen"
  (ctc/validate-all-documents application-suunnittelijan-nimeaminen))

(def application-aurinkopaneeli
  (merge application-rakennuslupa
         {:primaryOperation    (op-info aurinkopaneeli)
          :secondaryOperations []
          :documents           [hankkeen-kuvaus
                                hakija-henkilo
                                paasuunnittelija
                                suunnittelija1
                                maksaja-henkilo
                                rakennuspaikka
                                aurinkopaneeli]}))

(defn application-sijoittamislupa [sijoittamislupa-operation]
  (merge application-rakennuslupa
         {:primaryOperation    (op-info sijoittamislupa-operation)
          :secondaryOperations []
          :documents           [hankkeen-kuvaus
                                hakija-henkilo
                                paasuunnittelija
                                suunnittelija1
                                maksaja-henkilo
                                rakennuspaikka
                                sijoittamislupa-operation]}))

(def application-sijoittamislupa-v2
  (application-sijoittamislupa sijoittamislupa-v2))

(def application-sijoittamislupa-v3
  (application-sijoittamislupa sijoittamislupa-v3))

(def application-puhtaan-siirtyman-sijoittamislupa
  (application-sijoittamislupa puhtaan-siirtyman-sijoittamislupa))

(def application-aurinkopaneeli-with-many-applicants
  (assoc application-aurinkopaneeli
         :documents [hankkeen-kuvaus
                     hakija-henkilo
                     hakija-henkilo-using-ulkomainen-hetu
                     hakija-henkilo-using-finnish-hetu
                     paasuunnittelija
                     suunnittelija1
                     maksaja-henkilo
                     rakennuspaikka
                     aurinkopaneeli]))

(fact "valid application-aurinkopaneeli"
  (ctc/validate-all-documents application-aurinkopaneeli))

(def authority-user-jussi {:id            "777777777777777777000017"
                           :email         "jussi.viranomainen@tampere.fi"
                           :enabled       true
                           :role          "authority"
                           :username      "jussi"
                           :organizations ["837-YA"]
                           :firstName     "Jussi"
                           :lastName      "Viranomainen"
                           :street        "Katuosoite 1 a 1"
                           :phone         "1231234567"
                           :zip           "33404"
                           :city          "Tampere"})

(def application-rakennuslupa-verdict-given
  (assoc application-rakennuslupa
    :state "verdictGiven"
    :verdicts [{:timestamp       (:modified application-rakennuslupa)
                :kuntalupatunnus "2013-01"}]
    :buildings [{:propertyId   "21111111111111"
                 :nationalId   "098098098"
                 :localShortId "001"
                 :description  "Talo"
                 :operationId  "op1"}
                {:propertyId   "21111111111111"
                 :nationalId   "1234567892"
                 :localShortId "002"
                 :description  "Eri talo"}
                {:propertyId   "21111111111111"
                 :nationalId   "098098098"
                 :localShortId "003"
                 :operationId  "op3"}
                {:propertyId   "21111111111111"
                 :nationalId   "098098098"
                 :localShortId "004"
                 :operationId  "op4"}]))

;Jatkolupa

(def jatkolupa-application
  {:schema-version      1
   :auth                [{:lastName  "Panaani"
                          :firstName "Pena"
                          :username  "pena"
                          :role      "writer"
                          :id        "777777777777777777000020"}]
   :submitted           1384167310181
   :state               "submitted"
   :permitSubtype       nil
   :location            [411063.82824707 6685145.8129883]
   :attachments         []
   :organization        "753-R"
   :title               "Itäinen Hangelbyntie 163"
   :primaryOperation    {:id      "5280b764420622588b2f04fc"
                         :name    "raktyo-aloit-loppuunsaat"
                         :created 1384167268234}
   :secondaryOperations []
   :infoRequest         false
   :openInfoRequest     false
   :opened              1384167310181
   :created             1384167268234
   :propertyId          "75340800010051"
   :documents           [{:created     1384167268234
                          :data        {:kuvaus                {:modified 1384167309006
                                                                :value    "Pari vuotta jatko-aikaa, että saadaan rakennettua loppuun."}
                                        :jatkoaika-paattyy     {:modified 1384167309006
                                                                :value    "31.12.2018"}
                                        :rakennustyo-aloitettu {:modified 1384167309006
                                                                :value    "7.6.2010"}}
                          :id          "5280b764420622588b2f04fd"
                          :schema-info {:order      1
                                        :version    1
                                        :name       "jatkoaika-hankkeen-kuvaus"
                                        :subtype    "hankkeen-kuvaus"
                                        :approvable true
                                        :op         {:id      "5280b764420622588b2f04fc"
                                                     :name    "raktyo-aloit-loppuunsaat"
                                                     :created 1384167268234}}}
                         hakija-henkilo]
   :_software_version   "1.0.5"
   :modified            1384167309006
   :comments            []
   :address             "Itäinen Hangelbyntie 163"
   :permitType          "R"
   :id                  "LP-753-2013-00005"
   :municipality        "753"
   :authority           {:id        "777777777777777777000023"
                         :username  "sonja"
                         :firstName "Sonja"
                         :lastName  "Sibbo"
                         :role      "authority"}
   :linkPermitData      [link-permit-data-lupapistetunnus]})

(fact "valid jatkolupa-application"
  (ctc/validate-all-documents jatkolupa-application))

(defn asiakirjat-toimitettu-checker
  "Checking depends on value of the generator above.
  Selects newest attachment version (if applicable), and checks if actual matches version's created."
  [actual]
  (if-let [version (->> (map :latestVersion (:attachments application-rakennuslupa))
                        (remove nil?)
                        (sort-by :created)
                        last)]
    (= actual (-> version (:created) (date/xml-date)))
    (ss/blank? actual)))

;Aloitusoikeus (Takuu) (tyonaloitus ennen kuin valitusaika loppunut luvan myontamisesta)

(def aloitusoikeus-hakemus
  (merge
    domain/application-skeleton
    {:linkPermitData      [link-permit-data-kuntalupatunnus]
     :schema-version      1
     :auth                [{:lastName  "Panaani"
                            :firstName "Pena"
                            :username  "pena"
                            :role      "writer"
                            :id        "777777777777777777000020"}]
     :submitted           1388665814105
     :state               "submitted"
     :location            [406390.19848633 6681812.5]
     :organization        "753-R"
     :title               "Vainuddintie 92"
     :primaryOperation    {:id      "52c5461042065cf9f379de8b"
                           :name    "aloitusoikeus"
                           :created 1388660240013}
     :secondaryOperations []
     :infoRequest         false
     :openInfoRequest     false
     :opened              1388665814105
     :created             1388660240013
     :propertyId          "75341900080007"
     :documents           [{:id          "537df18fbc454ac7ac9036c7"
                            :created     1400762767119
                            :schema-info {:approvable   true
                                          :subtype      "hakija"
                                          :name         "hakija-r"
                                          :after-update "applicant-index-update"
                                          :repeating    true
                                          :version      1
                                          :type         "party"
                                          :order        3}
                            :data        {:_selected {:value "henkilo"}
                                          :henkilo   {:henkilotiedot {:etunimi  {:modified 1400762778665
                                                                                 :value    "Pena"}
                                                                      :hetu     {:modified 1400762778665
                                                                                 :value    "010203-040A"}
                                                                      :sukunimi {:modified 1400762778665
                                                                                 :value    "Panaani"}}
                                                      :osoite        {:katu        {:modified 1400762778665
                                                                                    :value    "Paapankuja 12"}
                                                                      :postinumero {:modified 1400762778665
                                                                                    :value    "92620"}
                                                                      :postitoimipaikannimi
                                                                      {:modified 1400762778665
                                                                       :value    "Piippola"}}
                                                      :userId        {:modified 1400762778787
                                                                      :value    "777777777777777777000020"}
                                                      :yhteystiedot  {:email   {:modified 1400762778665
                                                                                :value    "pena@example.com"}
                                                                      :puhelin {:modified 1400762778665
                                                                                :value    "0102030405"}}}}}
                           {:id          "537df18fbc454ac7ac9036c6"
                            :created     1400762767119
                            :schema-info {:version    1
                                          :name       "aloitusoikeus"
                                          :approvable true
                                          :op         {:id      "537df18fbc454ac7ac9036c5"
                                                       :name    "aloitusoikeus"
                                                       :created 1400762767119}}
                            :data        {:kuvaus {:modified 1400762776200
                                                   :value    "Tarttis aloitta asp rakentaminen."}}}
                           {:id          "537df18fbc454ac7ac9036c8"
                            :created     1400762767119
                            :schema-info {:approvable true
                                          :name       "maksaja"
                                          :repeating  true
                                          :version    1
                                          :type       "party"
                                          :order      6}
                            :data        {:henkilo    {:henkilotiedot {:etunimi  {:modified 1400762782277
                                                                                  :value    "Pena"}
                                                                       :hetu     {:modified 1400762782277
                                                                                  :value    "010203-040A"}
                                                                       :sukunimi {:modified 1400762782277
                                                                                  :value    "Panaani"}}
                                                       :osoite        {:katu        {:modified 1400762782277
                                                                                     :value    "Paapankuja 12"}
                                                                       :postinumero {:modified 1400762782277
                                                                                     :value    "92620"}
                                                                       :postitoimipaikannimi
                                                                       {:modified 1400762782277
                                                                        :value    "Piippola"}}
                                                       :userId        {:modified 1400762782327
                                                                       :value    "777777777777777777000020"}
                                                       :yhteystiedot  {:email   {:modified 1400762782277
                                                                                 :value    "pena@example.com"}
                                                                       :puhelin {:modified 1400762782277 :value "0102030405"}}}
                                          :laskuviite {:modified 1400762796099
                                                       :value    "1234567890"}}}]
     :_statements-seen-by {:777777777777777777000020 1388664440961}
     :modified            1388667087403
     :address             "Vainuddintie 92"
     :permitType          "R"
     :id                  "LP-753-2014-00001"
     :municipality        "753"}))

(fact "valid aloitusoikeus-hakemus"
  (ctc/validate-all-documents aloitusoikeus-hakemus))

(def huoneistot {:0 {:muutostapa              "lisäys"
                     :porras                  "A"
                     :huoneistonumero         "1"
                     :jakokirjain             "a"
                     :huoneistoTyyppi         "asuinhuoneisto"
                     :huoneistoala            "56"
                     :huoneluku               "66"
                     :keittionTyyppi          "keittio"
                     :parvekeTaiTerassiKytkin true
                     :WCKytkin                true}
                 :1 {:muutostapa          "muutos"
                     :porras              "A"
                     :huoneistonumero     "2"
                     :jakokirjain         "a"
                     :huoneistoTyyppi     "toimitila"
                     :huoneistoala        "03"
                     :huoneluku           "12"
                     :keittionTyyppi      "keittokomero"
                     :ammeTaiSuihkuKytkin true
                     :saunaKytkin         true
                     :lamminvesiKytkin    true}
                 :2 {:porras              "A"
                     :huoneistonumero     "3"
                     :jakokirjain         "a"
                     :huoneistoTyyppi     "asuinhuoneisto"
                     :huoneistoala        "38.5"
                     :huoneluku           "12"
                     :keittionTyyppi      "keittokomero"
                     :ammeTaiSuihkuKytkin true
                     :saunaKytkin         true
                     :lamminvesiKytkin    true}})

(defn- entry [v]
  {:modified (now) :value v})

(def paperilupa-application
  (assoc application-rakennuslupa
         :primaryOperation {:created 12345
                            :name    "aiemmalla-luvalla-hakeminen"
                            :id      "5f917053d2049a33b5ae31fa" }
         :documents [hakija-henkilo
                     {:created     12345
                      :schema-info {:name    "aiemman-luvan-toimenpide"
                                    :version 1
                                    :op      {:created 12330
                                              :name    "aiemmalla-luvalla-hakeminen"
                                              :id      "5f917053d2049a33b5ae31fa"}}
                      :data        (assoc common-rakennus
                                          :tunnus (entry "ABC")
                                          :kuvaus (entry "Paper description")
                                          :poikkeamat  (entry "One more thing")
                                          :kuntagml-toimenpide {:toimenpide                     (entry "muuMuutosTyo")
                                                                :perusparannuskytkin            (entry true)
                                                                :rakennustietojaEimuutetaKytkin (entry true)
                                                                :muutostyolaji                  (entry "rakennuksen pääasiallinen käyttötarkoitusmuutos")})}
                     suunnittelija1
                     maksaja-yritys]))

(fact "valid paperilupa-application"
  (ctc/validate-all-documents paperilupa-application))

(def purkamisilmoitus-doc
  {:id          "purkamisilmoitus-id"
   :schema-info {:name "purkamisilmoitus" :version 1
                 :op {:id "5f917053d2049a33b5ae31fa"
                      :name "purkamisilmoitus"
                      :description nil
                      :created 1765907938733}}
   :data {:buildingDemolition {:value true
                               :modified 1765907979793
                               :source nil
                               :sourceValue true}}})

(def demolition-notice
  (assoc application-rakennuslupa
         :primaryOperation {:created 12345
                            :name    "purkamisilmoitus"
                            :id      "5f917053d2049a33b5ae31fa" }
         :documents [hakija-henkilo
                     purkamisilmoitus-doc
                     suunnittelija1
                     maksaja-yritys]))

(defn with-mitat
  [application building-changes]
  (assoc application
         :documents [hankkeen-kuvaus
                     hakija-henkilo
                     asiamies-henkilo
                     hakija-yritys
                     paasuunnittelija
                     suunnittelija1
                     suunnittelija2
                     suunnittelija3
                     maksaja-henkilo
                     maksaja-yritys
                     tyonjohtaja
                     rakennuspaikka
                     (update-in uusi-rakennus [:data :mitat] merge building-changes)]))

(def application-rakennusoikeus-tilavuus
  (-> application-rakennuslupa
      (assoc :primaryOperation {:id          "kerrostalo-rivitalo-id"
                                :name        "kerrostalo-rivitalo"
                                :description "kerrostalo-rivitalo-kuvaus"})
      (assoc :secondaryOperations [])
      (with-mitat {:rakennusoikeusValinta {:value "tilavuus"}})))

(fact "valid application-rakennusoikeus-tilavuus"
  (ctc/validate-all-documents application-rakennusoikeus-tilavuus))

(def application-rakennusoikeus-harjakorkeus
  (-> application-rakennuslupa
      (assoc :primaryOperation {:id          "kerrostalo-rivitalo-id"
                                :name        "kerrostalo-rivitalo"
                                :description "kerrostalo-rivitalo-kuvaus"})
      (assoc :secondaryOperations [])
      (with-mitat {:rakennusoikeusValinta {:value "harjakorkeus"}})))

(fact "valid application-rakennusoikeus-harjakorkeus"
  (ctc/validate-all-documents application-rakennusoikeus-harjakorkeus))

(def ryhti-uusi-rakennuskohde-data
  {:rakennuksenomistajat  (:rakennuksenOmistat common-rakennus)
   :sijantitiedot         {:osoite   {:addressKey           (random-uuid-str)
                                      :katu                 "Hämeentie 42"
                                      :postinumero          "000042"
                                      :postitoimipaikannimi "ITAPSA"}
                           :sijainti {:hallinnollinen-sijainti-yksikko {:kiinteistotunnnus "75341200070043"}}}
   :rakennuskohteen-osa   {:energiatiedot
                           {:lammonlahde
                            {:0 {:heatingEnergySourceKey {:value (random-uuid-str)}
                                 :is-primary             {:value true}
                                 :lammonlahdetyyppi      {:value "other"}
                                 :muu-lammonlahde        {:value "niin hyvää puuta"}}
                             :1 {:heatingEnergySourceKey {:value (random-uuid-str)}
                                 :is-primary             {:value false}
                                 :lammonlahdetyyppi      {:value "turve"}}}
                            :lammitystapa
                            {:0 {:heatingMethodKey   {:value (random-uuid-str)}
                                 :is-primary         {:value true}
                                 :lammitystapatyyppi {:value "vesikeskus"}}}
                            :jaahdytysenergianlahde
                            {:0 {:coolingEnergySourceKey       {:value (random-uuid-str)}
                                 :is-primary                   {:value true}
                                 :jaahdytysenergianlahdetyyppi {:value "absorptiojäähdytys"}}}
                            :jaahdytystapa
                            {:0 {:coolingMethodKey    {:value (random-uuid-str)}
                                 :is-primary          {:value true}
                                 :jaahdytystapatyyppi {:value "vesijäähdytteinen"}}}
                            :sahkoenergianlahde
                            {:0 {:electricalEnergySourceKey {:value (random-uuid-str)}
                                 :is-primary                {:value true}
                                 :sahkoenergian-lahdetyyppi {:value "dieselvoima"}}}
                            :energialuokka            {:value "E"}
                            :energialuokka-alaindeksi {:value "2013"}}
                           :kayttotiedot
                           {:kayttoika       {:value "42"}
                            :kayttajamaara   {:value "120"}
                            :kayttotarkoitus {:0 {:purposeKey {:value (random-uuid-str)}
                                                  :is-primary {:value true}
                                                  :tyyppi     {:value "012 kahden asunnon talot"}}}}
                           :ulkokuorentiedot
                           {:tilavuus                       {:value "42"}
                            :kerrosala                      {:value "24"}
                            :kerrosluku                     {:value "2" }
                            :kokonaisala                    {:value "6" }
                            :rakennusoikeudellinenKerrosala {:value "99"}
                            :suhde-maan-pintaan             {:value "olennaisesti-kokonaan-pinnan-ylla"}
                            :rakennusoikeusValinta          {:value "harjakorkeus"}
                            :harjanKorkeusrakennusalalla    {:value "4"}
                            :rakennusoikeusKuutiometreina   {:value "42"}}
                           :tekniset-tiedot
                           {:verkostoliittymat
                            {:0 {:networkConnectionKey   (random-uuid-str)
                                 :liitetty               true
                                 :verkostoliittymatyyppi {:value "kaasu"}}
                             :1 {:networkConnectionKey   (random-uuid-str)
                                 :liitetty               true
                                 :verkostoliittymatyyppi {:value "talousvesi"}}
                             :2 {:networkConnectionKey   (random-uuid-str)
                                 :liitetty               true
                                 :verkostoliittymatyyppi {:value "jatevesi"}}
                             :3 {:networkConnectionKey   (random-uuid-str)
                                 :liitetty               true
                                 :verkostoliittymatyyppi {:value "tietoliikennekaapeli"}}
                             :4 {:networkConnectionKey   (random-uuid-str)
                                 :liitetty               true
                                 :verkostoliittymatyyppi {:value "sahko"}}
                             :5 {:networkConnectionKey   (random-uuid-str)
                                 :liitetty               true
                                 :verkostoliittymatyyppi {:value "kaukolampo"}}}
                            :ilmanvaihtotavan-laji
                            {:0 {:ventilationMethodKey (random-uuid-str)
                                 :is-primary           true
                                 :ilmanvaihtotyyppi    {:value "painovoimainen-ilmanvaihto"}}}
                            :jatevesienkasittely {:value "verkostoliittyma"}
                            :talousvesityyppi    {:value "oma-kaivo"}
                            :varuste
                            {:0 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "2"}
                                 :varustetyyppi {:value "sahko"}}
                             :1 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "1"}
                                 :varustetyyppi {:value "kaasu"}}
                             :2 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "1"}
                                 :varustetyyppi {:value "jatevesiviemari"}}
                             :3 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "3"}
                                 :varustetyyppi {:value "vesijohto"}}
                             :4 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "1"}
                                 :varustetyyppi {:value "hissi"}}
                             :5 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "1"}
                                 :varustetyyppi {:value "koneellinen-ilmastointi"}}
                             :6 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "1"}
                                 :varustetyyppi {:value "lammin-vesi"}}
                             :7 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "4"}
                                 :varustetyyppi {:value "aurinkopaneeli"}}
                             :8 {:equipmentKey  {:value (random-uuid-str)}
                                 :maara         {:value "1"}
                                 :varustetyyppi {:value "sauna"}}}}
                           :materiaalitiedot
                           {:laajarunkoinen-rakennus {:value true}
                            :rakentamistapa          {:value "elementti"}
                            :paloluokka              {:value "P1/P2"}
                            :kantavaRakennusaine     {:0 {:buildingMaterialOfLoadBearingStructuresKey {:value (random-uuid-str)}
                                                          :is-primary                                 {:value true}
                                                          :rakennusaine                               {:value "tiili"}}
                                                      :1 {:buildingMaterialOfLoadBearingStructuresKey {:value (random-uuid-str)}
                                                          :is-primary                                 {:value false}
                                                          :rakennusaine                               {:value "other"}
                                                          :muuRakennusaine                            {:value "purukumia"}}
                                                      :2 {:buildingMaterialOfLoadBearingStructuresKey {:value (random-uuid-str)}
                                                          :is-primary                                 {:value true}
                                                          :rakennusaine                               {:value "teräs"}}}
                            :julkisivu               {:0 {:facadeMaterialKey   {:value (random-uuid-str)}
                                                          :is-primary          {:value true}
                                                          :julkisivumateriaali {:value "puu"}}
                                                      :1 {:facadeMaterialKey   {:value (random-uuid-str)}
                                                          :is-primary          {:value false}
                                                          :julkisivumateriaali {:value "tiili"}}
                                                      :2 {:facadeMaterialKey   {:value (random-uuid-str)}
                                                          :is-primary          {:value false}
                                                          :julkisivumateriaali {:value "other"}
                                                          :muuMateriaali       {:value "jesaria"}}}}
                           :huoneistot
                           {:0 {:apartmentKey               {:value (random-uuid-str)}
                                :huoneistoTyyppi            {:value "asuinhuoneisto"}
                                :porras                     {:value "4"}
                                :huoneistonumero            {:value "42"}
                                :jakokirjain                {:value "N"}
                                :sijaintikerros             {:value "2"}
                                :sijaintikerroksen-tarkenne {:value "ullakko"}
                                :huoneluku                  {:value "8"}
                                :keittionTyyppi             {:value "tupakeittio"}
                                :huoneistoala               {:value "39"}
                                :wc                         {:value "3"}
                                :suihku                     {:value "5"}
                                :sauna                      {:value "7"}
                                :parveke                    {:value "9"}
                                :lammin-vesi                {:value "11"}
                                :pysyvaHuoneistotunnus      {:value "123-456"}}
                            :1 {:apartmentKey               {:value (random-uuid-str)}
                                :huoneistoTyyppi            {:value "toimitila"}
                                :porras                     {:value "6"}
                                :huoneistonumero            {:value "43"}
                                :jakokirjain                {:value "C"}
                                :sijaintikerros             {:value "0"}
                                :sijaintikerroksen-tarkenne {:value "kellari"}
                                :huoneluku                  {:value "1"}
                                :keittionTyyppi             {:value "keittio"}
                                :huoneistoala               {:value "61"}
                                :wc                         {:value "1"}
                                :suihku                     {:value "0"}
                                :sauna                      {:value "1"}
                                :parveke                    {:value "0"}
                                :lammin-vesi                {:value "1"}
                                :pysyvaHuoneistotunnus      {:value "456-321"}}}
                           :vaestonsuoja
                           {:0 {:civilDefenceShelterKey {:value (random-uuid-str)}
                                :nimi                   {:value "Pääsuoja"}
                                :luokka                 {:value "P1"}
                                :kapasiteetti           {:value "50"}
                                :suoja-ala              {:value "150"}
                                :yhteinen               {:value true}}
                            :1 {:civilDefenceShelterKey {:value (random-uuid-str)}
                                :nimi                   {:value "Liittävä suoja"}
                                :luokka                 {:value "P2"}
                                :kapasiteetti           {:value "30"}
                                :suoja-ala              {:value "100"}
                                :yhteinen               {:value false}}}}
   :id                    {:value "42"}
   :pysyva-rakennustunnus {:value "100000042-R"}
   :kuvaus                {:value "Uusi rakennuskohde kuvaus"}
   :kaytto                {:kohteen-laji                {:value "rakennus"}
                           :rakentaja-tyyppi            {:value true}
                           :rakennusluokka              {:value "0110 omakotitalot"}
                           :rakennelman-kayttotarkoitus {:value nil}
                           :erityista-toimintaa-tyyppi  {:value nil}
                           :nimi                        {:value "Kivimäki"}
                           :valiaikainenRakennusKytkin  {:value true}
                           :perusparannusPaiva          {:value "12.12.2025"}}
   :mitat                 {}
   :ryhti                 {}})


(def ryhti-uusi-rakennuskohde-doc
  {:id          "uusiRakennuskohde"
   :created     2
   :schema-info {:name    "uusiRakennuskohde"
                 :version 1
                 :op      {:name        "kerrostalo-rivitalo"
                           :description "kerrostalo-rivitalo-kuvaus"
                           :id          "kerrostalo-rivitalo-id"}}
   :data        ryhti-uusi-rakennuskohde-data})

(def ryhti-uusi-rakennuskohde-app
  (assoc application-rakennuslupa
         :primaryOperation {:id          "kerrostalo-rivitalo-id"
                            :name        "kerrostalo-rivitalo"
                            :description "kerrostalo-rivitalo-kuvaus"}
         :ryhti-data-2026  true
         :documents        [hakija-henkilo
                            ryhti-uusi-rakennuskohde-doc]))

(def ryhti-uusi-rakennuskohde-app-with-rakennelma
  (let [op {:id          "auto-katos-id"
            :name        "auto-katos"
            :description "auto-katos-kuvaus"}]
    (assoc application-rakennuslupa
           :primaryOperation op
           :ryhti-data-2026  true
           :documents [hakija-henkilo
                       (-> ryhti-uusi-rakennuskohde-doc
                           (assoc-in [:schema-info :op] op)
                           (update-in [:data :kaytto] (fn [kaytto]
                                                        (assoc kaytto :kohteen-laji                {:value "rakennelma"}
                                                                      :rakennusluokka              {:value nil}
                                                                      :rakennelman-kayttotarkoitus {:value "Vaja"}))))])))

(def ryhti-uusi-rakennuskohde-app-with-etvra
  (let [op  {:id          "erityista-toimintaa-alue-id"
             :name        "erityista-toimintaa-alue"
             :description "erityista-toimintaa-alue-kuvaus"}]
    (assoc application-rakennuslupa
           :primaryOperation op
           :ryhti-data-2026  true
           :documents [hakija-henkilo
                       (-> ryhti-uusi-rakennuskohde-doc
                           (assoc-in [:schema-info :op] op)
                           (update-in [:data :mitat] assoc :pintaAla {:value "1000"})
                           (update-in [:data :kaytto] (fn [kaytto]
                                                        (assoc kaytto :kohteen-laji               {:value "etvra"}
                                                                      :rakennusluokka             {:value nil}
                                                                      :erityista-toimintaa-tyyppi {:value "aurinkopaneelikentta"}))))])))

(def ryhti-uusi-rakennuskohteen-purkaminen-app
  (let [op  {:id          "purkaminen-id"
             :name        "purkaminen"
             :description "purkaminen-kuvaus"}]
    (assoc application-rakennuslupa
           :primaryOperation op
           :ryhti-data-2026  true
           :documents [hakija-henkilo
                       (-> {:id          "rakennuskohteenPurkaminen"
                            :created     2
                            :schema-info {:name    "rakennuskohteenPurkaminen"
                                          :version 1
                                          :op      {:id          "purkaminen-id"
                                                    :name        "purkaminen"
                                                    :description "purkaminen-kuvaus"}}
                            :data        (assoc-in ryhti-uusi-rakennuskohde-data [:poistumanAjankohta] {:value "25.12.2025"})})])))
