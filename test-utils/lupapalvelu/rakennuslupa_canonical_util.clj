(ns lupapalvelu.rakennuslupa-canonical-util
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [sade.core :refer :all]
            [sade.schema-generators :as ssg]
            [sade.util :as util]
            [schema.core :as sc]))

(def- municipality "753")

(def- nimi {:etunimi {:value "Pena"} :sukunimi {:value "Penttil\u00e4"}})

(def- henkilotiedot (assoc nimi :hetu {:value "210281-9988"} :turvakieltoKytkin {:value true}))

(def- osoite {:katu {:value "katu"}
              :postinumero {:value "33800"} :postitoimipaikannimi {:value "Tuonela"}
              :maa {:value "CHN"}})

(def- henkilo
  {:henkilotiedot henkilotiedot
   :yhteystiedot {:puhelin {:value "+358401234567"}
                  :email {:value "pena@example.com"}}
   :osoite osoite})

(def- suunnittelija-henkilo
  (assoc henkilo :henkilotiedot (dissoc henkilotiedot :turvakieltoKytkin)))

(def- yritysnimi-ja-ytunnus
  {:yritysnimi {:value "Solita Oy"} :liikeJaYhteisoTunnus {:value "1060155-5"}})

(def- yritys
  (merge
    yritysnimi-ja-ytunnus
    {:osoite osoite
     :yhteyshenkilo {:henkilotiedot (dissoc henkilotiedot :hetu)
                     :yhteystiedot {:email {:value "yritys@example.com"}
                                    :puhelin {:value "03-389 1380"}}}}))

(def- hakija-henkilo
  {:id "hakija-henkilo" :schema-info {:name "hakija-r"
                                      :subtype "hakija"
                                      :version 1}
   :data {:henkilo (assoc henkilo :kytkimet {:vainsahkoinenAsiointiKytkin {:value true}
                                             :suoramarkkinointilupa {:value false}})}})
(def- asiamies-henkilo
  {:id "asiamies-henkilo" :schema-info {:name "hakijan-asiamies"
                                      :version 1}
   :data {:henkilo (assoc henkilo :kytkimet {:vainsahkoinenAsiointiKytkin {:value true}
                                             :suoramarkkinointilupa {:value false}})}})

(def- hakija-tj-henkilo
  (assoc-in hakija-henkilo [:schema-info :name] "hakija-tj"))

(def- hakija-yritys
  {:id "hakija-yritys" :schema-info {:name "hakija-r"
                                     :subtype "hakija"
                                     :version 1}
   :data {:_selected {:value "yritys"}
          :yritys (assoc-in yritys [:yhteyshenkilo :kytkimet] {:vainsahkoinenAsiointiKytkin {:value true}
                                                               :suoramarkkinointilupa {:value true}})}})

(def- paasuunnittelija
  {:id "50bc85e4ea3e790c9ff7cdb2"
   :schema-info {:name "paasuunnittelija"
                 :version 1}
   :data (merge
           suunnittelija-henkilo
           {:suunnittelutehtavanVaativuusluokka {:value "AA"}}
           {:patevyys {:koulutusvalinta {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                       :patevyysluokka {:value "ei tiedossa"}
                       :valmistumisvuosi {:value "2010"}
                       :kokemus {:value "5"}
                       :fise {:value "http://www.ym.fi"}
                       :fiseKelpoisuus {:value "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"}}}
           {:yritys yritysnimi-ja-ytunnus})})

(def- suunnittelija1
  {:id "suunnittelija1" :schema-info {:name "suunnittelija"
                                      :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "rakennusfysikaalinen suunnittelija"}}
                {:suunnittelutehtavanVaativuusluokka {:value "C"}}
                {:patevyys {:koulutusvalinta {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                            :patevyysluokka {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}
                            :fiseKelpoisuus {:value "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def- suunnittelija2
  {:id "suunnittelija2"  :schema-info {:name "suunnittelija"
                                       :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "GEO-suunnittelija"}}
                {:suunnittelutehtavanVaativuusluokka {:value "A"}}
                {:patevyys {:koulutusvalinta {:value "other"} :koulutus {:value "El\u00e4m\u00e4n koulu"}  ;; "Muu" option ( i.e. :other-key) is selected
                            :patevyysluokka {:value "AA"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}
                            :fiseKelpoisuus {:value "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def- suunnittelija3
  {:id "suunnittelija3" :schema-info {:name "suunnittelija"
                                      :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "other"}}
                {:muuSuunnittelijaRooli {:value "ei listassa -rooli"}}
                {:suunnittelutehtavanVaativuusluokka {:value "C"}}
                {:patevyys {:koulutusvalinta {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                            :patevyysluokka {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}
                            :fiseKelpoisuus {:value "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def- suunnittelija-old-schema-LUPA-771
  {:id "suunnittelija-old-schema-LUPA771" :schema-info {:name "suunnittelija"
                                                        :version 1}
   :data (merge suunnittelija-henkilo
                {:patevyys {:koulutusvalinta {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                            :kuntaRoolikoodi {:value "ARK-rakennussuunnittelija"}
                            :patevyysluokka {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}
                            :fiseKelpoisuus {:value "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"}}})})

(def- suunnittelija-blank-role
  {:id "suunnittelija-blank-role" :schema-info {:name "suunnittelija"
                                                :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value ""}}
                {:patevyys {:koulutusvalinta {:value "arkkitehti"} :koulutus {:value "Arkkitehti"}
                            :patevyysluokka {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}
                            :fiseKelpoisuus {:value "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def- maksaja-henkilo
  {:id "maksaja-henkilo" :schema-info {:name "maksaja"
                                       :version 1}
   :data {:henkilo henkilo}})

(def- maksaja-yritys
  {:id "maksaja-yritys" :schema-info {:name "maksaja" :version 1}
   :data {:_selected {:value "yritys"}
          :yritys (merge yritys
                         {:verkkolaskutustieto
                           {:ovtTunnus {:value "003712345671"}
                           :verkkolaskuTunnus {:value "laskutunnus-1234"}
                           :valittajaTunnus {:value "BAWCFI22"}}})}})

(def- tyonjohtaja
  {:id "tyonjohtaja"
   :schema-info {:name "tyonjohtaja", :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "KVV-ty\u00f6njohtaja"}
            :patevyys-tyonjohtaja {:koulutusvalinta {:value "other"}, :koulutus {:value "Koulutus"}   ;; "Muu" option ( i.e. :other-key) is selected
                                   :patevyysvaatimusluokka {:value "A"}
                                   :valmistumisvuosi {:value "2010"}
                                   :tyonjohtajaHakemusKytkin {:value "hakemus"}
                                   :kokemusvuodet {:value "3"}
                                   :valvottavienKohteidenMaara {:value "9"}}
            :vastattavatTyotehtavat {:kiinteistonVesiJaViemarilaitteistonRakentaminen {:value true}
                                     :kiinteistonilmanvaihtolaitteistonRakentaminen {:value true}
                                     :maanrakennustyo {:value true}
                                     :rakennelmaTaiLaitos {:value true}
                                     :muuMika {:value "Muu tyotehtava"}}
            :yritys yritysnimi-ja-ytunnus
            :sijaistus {:sijaistettavaHloEtunimi {:value "Jaska"}
                        :sijaistettavaHloSukunimi {:value "Jokunen"}
                        :alkamisPvm {:value "13.02.2014"}
                        :paattymisPvm {:value "20.02.2014"}}})})

(def- tyonjohtaja-blank-role-and-blank-qualification
  (-> tyonjohtaja
    (assoc-in [:data :kuntaRoolikoodi :value] nil)
    (assoc-in [:data :patevyys-tyonjohtaja :patevyysvaatimusluokka :value] "ei tiedossa")
    (assoc-in [:data :patevyys-tyonjohtaja :tyonjohtajaHakemusKytkin :value] "nimeaminen")))

(def- tyonjohtajan-sijaistus-blank-dates
  (-> tyonjohtaja
    (util/dissoc-in [:data :sijaistus :alkamisPvm])
    (assoc-in  [:data :sijaistus :paattymisPvm :value] "")))

(def- tyonjohtaja-v2
  {:id "tyonjohtaja-v2"
   :schema-info {:name "tyonjohtaja-v2"
                 :version 1
                 :op {:name "tyonjohtajan-nimeaminen-v2"}}
   :data (merge
           suunnittelija-henkilo
           (select-keys (:data tyonjohtaja) [:kuntaRoolikoodi :sijaistus :yritys])
           {:fillMyInfo {:value nil}
            :patevyysvaatimusluokka {:value "A"}
            :patevyys-tyonjohtaja {:koulutusvalinta {:value "arkkitehtiylioppilas"}
                                   :koulutus {:value ""}
                                   :valmistumisvuosi {:value "2010"}
                                   :kokemusvuodet {:value "3"}
                                   :valvottavienKohteidenMaara {:value "9"}}
            :vastattavatTyotehtavat {:rakennuksenPurkaminen {:value true}
                                     :ivLaitoksenKorjausJaMuutostyo {:value true}
                                     :uudisrakennustyoIlmanMaanrakennustoita {:value true}
                                     :maanrakennustyot {:value true}
                                     :uudisrakennustyoMaanrakennustoineen {:value false}
                                     :ulkopuolinenKvvTyo {:value false}
                                     :rakennuksenMuutosJaKorjaustyo {:value false}
                                     :linjasaneeraus {:value false}
                                     :ivLaitoksenAsennustyo {:value false}
                                     :sisapuolinenKvvTyo {:value true}
                                     :muuMika {:value "Muu tyotehtava"}}
            :muutHankkeet {:0 {:katuosoite {:value "katuosoite"}
                               :3kk {:value "1"}
                               :9kk {:value "3"}
                               :vaihe {:value "R"}
                               :6kk {:value "2"}
                               :autoupdated {:value false}
                               :rakennustoimenpide {:value "purkutoimenpide"}
                               :kokonaisala {:value "120"}
                               :12kk {:value "4"}
                               :luvanNumero {:value "123"}}}
            :tyonjohtajaHanketieto {:taysiaikainenOsaaikainen {:value "taysiaikainen"}
                                    :kaytettavaAika {:value "3"}
                                    :kayntienMaara {:value "3"}
                                    :hankeKesto {:value "3"}}
            :tyonjohtajanHyvaksynta {:tyonjohtajanHyvaksynta {:value true}
                                     :foremanHistory {:value nil}}})})

(def- rakennuspaikka
  {:id "rakennuspaikka" :schema-info {:name "rakennuspaikka"
                                      :version 1}
   :data {:kiinteisto {:tilanNimi {:value "Hiekkametsa"}
                       :maaraalaTunnus {:value ""}}
          :hallintaperuste {:value "oma"}
          :kaavatilanne {:value "oikeusvaikutteinen yleiskaava"}}})

(def- rakennuspaikka-ilman-ilmoitusta
  {:id "rakennuspaikka-ilman-ilmoitusta" :schema-info {:name "rakennuspaikka-ilman-ilmoitusta"
                                                       :version 1}
   :data {:kiinteisto {:tilanNimi {:value "Eramaa"}
                       :maaraalaTunnus {:value ""}}
          :hallintaperuste {:value "vuokra"}
          :kaavatilanne {:value "oikeusvaikutukseton yleiskaava"}}})

(def- common-rakennus
  {:rakennuksenOmistajat {:0 {:_selected {:value "henkilo"}
                              :henkilo henkilo
                              :omistajalaji {:value "muu yksityinen henkil\u00f6 tai perikunta"}}
                          :1 {:_selected {:value "yritys"}
                              :yritys yritys
                              :omistajalaji {:value "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"}}}
   :kaytto {:rakentajaTyyppi {:value "muu"}
            :kayttotarkoitus {:value "012 kahden asunnon talot"}}
   :mitat {:tilavuus {:value "1500"}
           :kokonaisala {:value "1000"}
           :kellarinpinta-ala {:value "100"}
           :kerrosluku {:value "2"}
           :kerrosala {:value "180"}
           :rakennusoikeudellinenKerrosala {:value "160"}}
   :rakenne {:rakentamistapa {:value "elementti"}
             :kantavaRakennusaine {:value "puu"}
             :muuRakennusaine {:value ""}
             :julkisivu {:value "puu"}}
   :lammitys {:lammitystapa {:value "vesikeskus"}
              :lammonlahde {:value "other"}
              :muu-lammonlahde {:value "polttopuillahan tuo"}}
   :varusteet {:hissiKytkin {:value true}
               :kaasuKytkin {:value true}
               :koneellinenilmastointiKytkin {:value true}
               :sahkoKytkin {:value true}
               :saunoja {:value "1"}
               :vaestonsuoja {:value "1"}
               :vesijohtoKytkin {:value true}
               :viemariKytkin {:value true}
               :lamminvesiKytkin {:value true}
               :aurinkopaneeliKytkin {:value true}
               :liitettyJatevesijarjestelmaanKytkin {:value true}}
   :verkostoliittymat {:kaapeliKytkin {:value true}
                       :maakaasuKytkin {:value true}
                       :sahkoKytkin {:value true}
                       :vesijohtoKytkin {:value true}
                       :viemariKytkin {:value true}}
   :luokitus {:paloluokka {:value "P1"}
              :energialuokka {:value "C"}
              :energiatehokkuusluku {:value "124"}
              :energiatehokkuusluvunYksikko {:value "kWh/m2"}}
   :huoneistot {:0 {:muutostapa {:value "lis\u00e4ys"}
                    :porras {:value "A"}
                    :huoneistonumero {:value "1"}
                    :jakokirjain {:value "a"}
                    :huoneistoTyyppi {:value "asuinhuoneisto"}
                    :huoneistoala {:value "56"}
                    :huoneluku {:value "66"}
                    :keittionTyyppi {:value "keittio"}
                    :parvekeTaiTerassiKytkin {:value true}
                    :WCKytkin {:value true}}
                :1 {:muutostapa {:value "muutos"}
                    :porras {:value "A"}
                    :huoneistonumero {:value "2"}
                    :jakokirjain {:value "a"}
                    :huoneistoTyyppi {:value "toimitila"}
                    :huoneistoala {:value "02"}
                    :huoneluku {:value "12"}
                    :keittionTyyppi {:value "keittokomero"}
                    :ammeTaiSuihkuKytkin {:value true}
                    :saunaKytkin {:value true}
                    :lamminvesiKytkin {:value true}}}})

(def document-building
  {:id "5bcdb8eaf70b0924847661f2"
   :vtj-prt "199887766E"
   :building {:kiinttun "75300301050006"
              :rakennuksenOmistajat {:0 {:_selected "yritys"
                                         :henkilo {:userId nil
                                                   :henkilotiedot {:etunimi ""
                                                                   :sukunimi ""
                                                                   :hetu nil
                                                                   :turvakieltoKytkin false}
                                                   :osoite {:katu ""
                                                            :postinumero ""
                                                            :postitoimipaikannimi ""
                                                            :maa "FIN"}
                                                   :yhteystiedot {:puhelin "" :email ""}
                                                   :kytkimet {:suoramarkkinointilupa false}}
                                         :yritys {:companyId nil
                                                  :yritysnimi "Testiyritys 9242"
                                                  :liikeJaYhteisoTunnus "1234567-1"
                                                  :osoite {:katu "Testikatu 1 A 9242"
                                                           :postinumero "00380"
                                                           :postitoimipaikannimi "HELSINKI"
                                                           :maa "FIN"}
                                                  :yhteyshenkilo {:henkilotiedot {:etunimi ""
                                                                                  :sukunimi ""
                                                                                  :turvakieltoKytkin false}
                                                                  :yhteystiedot {:puhelin "" :email ""}}}
                                         :omistajalaji nil
                                         :muu-omistajalaji ""}
                                     :1 {:_selected "yritys"
                                         :yritys {:liikeJaYhteisoTunnus "1234567-1"
                                                  :osoite {:katu "Testikatu 1 A 9240"
                                                           :postinumero "00380"
                                                           :postitoimipaikannimi "HELSINKI"}
                                                  :yritysnimi "Testiyritys 9240"}}
                                     :2 {:_selected "yritys"
                                         :yritys {:liikeJaYhteisoTunnus "1234567-1"
                                                  :osoite {:katu "Testikatu 1 A 9241"
                                                           :postinumero "00380"
                                                           :postitoimipaikannimi "HELSINKI"}
                                                  :yritysnimi "Testiyritys 9241"}}
                                     :3 {:_selected "yritys"
                                         :yritys {:liikeJaYhteisoTunnus "1234567-1"
                                                  :osoite {:katu "Testikatu 1 A 9239"
                                                           :postinumero "00380"
                                                           :postitoimipaikannimi "HELSINKI"}
                                                  :yritysnimi "Testiyritys 9239"}}}
              :varusteet {:viemariKytkin true
                          :saunoja ""
                          :vesijohtoKytkin true
                          :hissiKytkin false
                          :vaestonsuoja ""
                          :kaasuKytkin false
                          :aurinkopaneeliKytkin false
                          :liitettyJatevesijarjestelmaanKytkin false
                          :koneellinenilmastointiKytkin true
                          :sahkoKytkin true
                          :lamminvesiKytkin true}
              :rakennusnro "002"
              :verkostoliittymat {:viemariKytkin true
                                  :vesijohtoKytkin true
                                  :sahkoKytkin true
                                  :maakaasuKytkin false
                                  :kaapeliKytkin false}
              :kaytto {:rakentajaTyyppi nil :kayttotarkoitus "021 rivitalot"}
              :huoneistot {:0 {:WCKytkin true
                               :huoneistoTyyppi "asuinhuoneisto"
                               :keittionTyyppi "keittio"
                               :huoneistoala "108"
                               :huoneluku "4"
                               :jakokirjain ""
                               :ammeTaiSuihkuKytkin true
                               :saunaKytkin true
                               :huoneistonumero "001"
                               :porras "A"
                               :lamminvesiKytkin true
                               :parvekeTaiTerassiKytkin true}
                           :1 {:WCKytkin true
                               :huoneistoTyyppi "asuinhuoneisto"
                               :keittionTyyppi "keittio"
                               :huoneistoala "106"
                               :huoneluku "4"
                               :ammeTaiSuihkuKytkin true
                               :saunaKytkin true
                               :huoneistonumero "002"
                               :porras "A"
                               :lamminvesiKytkin true
                               :parvekeTaiTerassiKytkin true}}
              :lammitys {:lammitystapa "vesikeskus" :lammonlahde "kevyt polttoöljy" :muu-lammonlahde ""}
              :kunnanSisainenPysyvaRakennusnumero ""
              :rakenne {:rakentamistapa "paikalla"
                        :kantavaRakennusaine "tiili"
                        :muuRakennusaine ""
                        :julkisivu "puu"
                        :muuMateriaali ""}
              :osoite {:osoitenumero2 "5"
                       :huoneisto ""
                       :jakokirjain ""
                       :kunta "245"
                       :jakokirjain2 ""
                       :postinumero "04200"
                       :porras ""
                       :osoitenumero "3"
                       :postitoimipaikannimi "KERAVA"
                       :maa "FIN"
                       :lahiosoite "Kyllikintie"}
              :mitat {:tilavuus "837"
                      :kerrosala "281"
                      :rakennusoikeudellinenKerrosala ""
                      :kokonaisala "281"
                      :kerrosluku "2"
                      :kellarinpinta-ala ""}
              :manuaalinen_rakennusnro ""
              :luokitus {:energialuokka nil
                         :energiatehokkuusluku ""
                         :energiatehokkuusluvunYksikko "kWh/m2"
                         :paloluokka "P1"}
              :valtakunnallinenNumero "199887766E"}})

(def- uusi-rakennus
  {:id "uusi-rakennus"
   :created 2
   :schema-info {:name "uusiRakennus"
                 :version 1
                 :op {:name "kerrostalo-rivitalo"
                      :description "kerrostalo-rivitalo-kuvaus"
                      :id "kerrostalo-rivitalo-id"}}
   :data (assoc common-rakennus :tunnus {:value "A"})})

(def- rakennuksen-muuttaminen
  {:id "muuttaminen"
   :created 1
   :schema-info {:name "rakennuksen-muuttaminen"
                 :version 1
                 :op {:name "muu-laajentaminen"
                      :id "muu-laajentaminen-id"}}
   :data (conj
           common-rakennus
           {:rakennusnro {:value "001"}
            :buildingId {:value "000"}
            :valtakunnallinenNumero {:value "1234567892"}
            :perusparannuskytkin {:value true}
            :rakennustietojaEimuutetaKytkin {:value true}
            :muutostyolaji {:value "muut muutosty\u00f6t"}})})

(def- laajentaminen
  {:id "laajennus"
   :created 3
   :schema-info {:name "rakennuksen-laajentaminen"
                 :version 1
                 :op {:name "laajentaminen"
                      :id "laajentaminen-id"}}
   :data (conj
           common-rakennus
           {:rakennusnro {:value "001"}
            :buildingId {:value "000"}
            :manuaalinen_rakennusnro {:value "002"}
            :laajennuksen-tiedot {:perusparannuskytkin {:value true}
                                  :mitat {:tilavuus {:value "1500"}
                                          :kerrosala {:value "180"}
                                          :rakennusoikeudellinenKerrosala {:value "160"}
                                          :kokonaisala {:value "-10"}
                                          :huoneistoala {:0 {:pintaAla {:value "150"}
                                                             :kayttotarkoitusKoodi {:value "asuntotilaa(ei vapaa-ajan asunnoista)"}}
                                                         :1 {:pintaAla {:value "-10"}
                                                             :kayttotarkoitusKoodi {:value "varastotilaa"}}}}}})})

(def- purku {:id "purku"
                      :created 4
                      :schema-info {:name "purkaminen"
                                    :version 1
                                    :op {:name "purkaminen"
                                         :id "purkaminen-id"}}
                      :data (conj
                              (->
                                common-rakennus
                                (dissoc :huoneistot)
                                (dissoc :lammitys)
                                (dissoc :verkostoliittymat)
                                (dissoc :varusteet)
                                (dissoc :luokitus))
                              {:rakennusnro {:value "001"}
                               :buildingId {:value "000"}
                               :poistumanAjankohta {:value "17.04.2013"},
                               :poistumanSyy {:value "tuhoutunut"}})})

(def- aurinkopaneeli
  {:id "muu-rakentaminen-1"
   :schema-info {:name "kaupunkikuvatoimenpide"
                 :op {:id  "muu-rakentaminen-id"
                      :name "muu-rakentaminen"}
                 :version 1}
   :created 4
   :data {:tunnus                 {:value "muu2"}
          :kokonaisala            {:value "6"}
          :kayttotarkoitus        {:value "Aurinkopaneeli"}
          :kuvaus                 {:value "virtaa maailmaan"}
          :valtakunnallinenNumero {:value "1940427695"}}})

(def- aidan-rakentaminen {:data {:kokonaisala {:value "0"}
                                 :kayttotarkoitus {:value "Aita"}
                                          :kuvaus { :value "Aidan rakentaminen rajalle"}}
                                   :id "aidan-rakentaminen"
                                   :created 5
                                   :schema-info {:op {:id  "kaupunkikuva-id"
                                                      :name "aita"}
                                                 :name "kaupunkikuvatoimenpide-ei-tunnusta"
                                                 :version 1}})

(def- puun-kaataminen {:created 6
                                :data { :kuvaus {:value "Puun kaataminen"}}
                                :id "puun kaataminen"
                                :schema-info {:op {:id "5177ad63da060e8cd8348e32"
                                                   :name "puun-kaataminen"
                                                   :created  1366797667137}
                                              :name "maisematyo"
                                              :version 1}})

(def- hankkeen-kuvaus-minimum {:id "Hankkeen kuvaus"
                                        :schema-info {:name "hankkeen-kuvaus-minimum" :version 1 :order 1},
                                        :data {:kuvaus {:value "Uuden rakennuksen rakentaminen tontille."}}})

(def- hankkeen-kuvaus
  (-> hankkeen-kuvaus-minimum
    (assoc-in [:data :poikkeamat] {:value "Ei poikkeamisia"})
    (assoc-in [:schema-info :name] "hankkeen-kuvaus")))

(def- link-permit-data-kuntalupatunnus {:id "123-123-123-123" :type "kuntalupatunnus"})
(def- link-permit-data-lupapistetunnus {:id "LP-753-2013-00099" :type "lupapistetunnus"})
(def- app-linking-to-us {:id "LP-753-2013-00008"})

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
                purku])

(def documents-ilman-ilmoitusta [hankkeen-kuvaus
                                 hakija-henkilo
                                 asiamies-henkilo
                                 paasuunnittelija
                                 maksaja-yritys
                                 rakennuspaikka-ilman-ilmoitusta
                                 rakennuksen-muuttaminen]

  )

(defn op-info [doc]
  (select-keys (-> doc :schema-info :op) [:id :name :description]))

(defn- randomize-versions [a]
  (if (> 0.5 (rand))
    (assoc a :versions [])
    a))

(def application-rakennuslupa
  {:id "LP-753-2013-00001"
   :permitType "R"
   :schema-version 1
   :municipality municipality
   :auth [{:id "777777777777777777000020"
           :firstName "Pena"
           :lastName "Panaani"
           :username "pena"
           :type "owner"
           :role "writer"}]
   :state "submitted"
   :opened 1354532324658
   :submitted 1354532324658
   :location [408048 6693225]
   :handlers[{:firstName "Sonja"
              :lastName "Sibbo"
              :general true}]
   :attachments [(->> (ssg/generate (dissoc att/Attachment (sc/optional-key :metadata)))
                      randomize-versions
                      ((fn [a] (assoc a :latestVersion (-> a :versions last)))))]
   :title "s"
   :created 1354532324658
   :documents documents
   :propertyId "21111111111111"
   :modified 1354532324691
   :address "Katutie 54"
   :tags [{:id "tag1" :label "avainsana"}
          {:id "tag2" :label "toinen avainsana"}]
   :statements [{:given 1368080324142
                 :id "518b3ee60364ff9a63c6d6a1"
                 :person {:text "Paloviranomainen"
                          :name "Sonja Sibbo"
                          :email "sonja.sibbo@sipoo.fi"
                          :id "516560d6c2e6f603beb85147"}
                 :requested 1368080102631
                 :state "given"
                 :status "ehdollinen"
                 :text "Savupiippu pit\u00e4\u00e4 olla."}
                {:id "518b3ee60364ff9a63c6d6a2"
                 :person {:text "Paloviranomainen"
                          :name "Sonja Sibbo"
                          :email "sonja.sibbo@sipoo.fi"
                          :id "516560d6c2e6f603beb85147"}
                 :requested 1368080102631
                 :state "draft"
                 :status "ehdollinen"
                 :text "Lausunto tulossa..."}]
   :neighbors ctc/neighbors
   :primaryOperation (op-info rakennuksen-muuttaminen)
   :secondaryOperations (map op-info [uusi-rakennus
                                      laajentaminen
                                      aidan-rakentaminen
                                      puun-kaataminen
                                      purku])
   :tosFunction "00 00 00 01"
   :tosFunctionName "tos menettely"})
