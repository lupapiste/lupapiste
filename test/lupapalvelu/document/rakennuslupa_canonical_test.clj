(ns lupapalvelu.document.rakennuslupa-canonical-test
  (:require [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.rakennuslupa-canonical :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :refer :all]
            [lupapalvelu.factlet :as fl]
            [sade.util :as util]
            [sade.core :refer :all]
            [clojure.data.xml :refer :all]
            [clj-time.core :refer [date-time]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(facts "Date format"
  (fact (util/to-xml-date (.getMillis (date-time 2012 1 14))) => "2012-01-14")
  (fact (util/to-xml-date (.getMillis (date-time 2012 2 29))) => "2012-02-29"))

(fact "get-rakennustunnus"
  (let [application {:primaryOperation {:id "1" :description "desc"}}
        document {:op {:id "1"}}
        base-result {:jarjestysnumero nil
                     :kiinttun nil
                     :muuTunnustieto [{:MuuTunnus {:sovellus "toimenpideId", :tunnus "1"}}
                                      {:MuuTunnus {:sovellus "Lupapiste", :tunnus "1"}}]}]
    (get-rakennustunnus {} {} document) => base-result
    (get-rakennustunnus {:tunnus "B"} application document) => (assoc base-result :rakennuksenSelite "B: desc")
    (get-rakennustunnus {:tunnus ""} application document) => (assoc base-result :rakennuksenSelite "desc")
    (get-rakennustunnus {:tunnus "B"} {} document) => (assoc base-result :rakennuksenSelite "B")))

(def- municipality 753)

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
                :1 {:muutostapa {:value "lis\u00e4ys"}
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

(def- aidan-rakentaminen {:data {:kokonaisala {:value "0"}
                                 :kayttotarkoitus {:value "Aita"}
                                          :kuvaus { :value "Aidan rakentaminen rajalle"}}
                                   :id "aidan-rakentaminen"
                                   :created 5
                                   :schema-info {:removable true
                                                 :op {:id  "kaupunkikuva-id"
                                                      :name "aita"}
                                                 :name "kaupunkikuvatoimenpide-ei-tunnusta"
                                                 :version 1}})

(def- puun-kaataminen {:created 6
                                :data { :kuvaus {:value "Puun kaataminen"}}
                                :id "puun kaataminen"
                                :schema-info {:removable true
                                              :op {:id "5177ad63da060e8cd8348e32"
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
    (assoc-in [:data :hankkeenVaativuus] {:value "A"})
    (assoc-in [:schema-info :name] "hankkeen-kuvaus-rakennuslupa")))

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
           :role "owner"}]
   :state "submitted"
   :opened 1354532324658
   :submitted 1354532324658
   :location [408048 6693225]
   :attachments [],
   :authority {:id "777777777777777777000023"
               :username "sonja"
               :firstName "Sonja"
               :lastName "Sibbo"
               :role "authority"}
   :title "s"
   :created 1354532324658
   :documents documents
   :propertyId "21111111111111"
   :modified 1354532324691
   :address "Katutie 54"
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
   :secondaryOperations (map op-info [uusi-rakennus laajentaminen
                                      aidan-rakentaminen puun-kaataminen
                                      purku])})

(ctc/validate-all-documents application-rakennuslupa)

(def application-rakennuslupa-ilman-ilmoitusta (assoc application-rakennuslupa :documents documents-ilman-ilmoitusta))

(ctc/validate-all-documents application-rakennuslupa-ilman-ilmoitusta)

(def application-tyonjohtajan-nimeaminen
  (merge application-rakennuslupa {:id "LP-753-2013-00002"
                                   :organization "753-R"
                                   :state "submitted"
                                   :submitted 1426247899490
                                   :primaryOperation {:name "tyonjohtajan-nimeaminen"
                                                      :id "5272668be8db5aaa01084601"
                                                      :created 1383229067483}
                                   :permitSubtype "tyonjohtaja-hakemus"
                                   :documents [hakija-henkilo
                                               maksaja-henkilo
                                               tyonjohtaja
                                               hankkeen-kuvaus-minimum]
                                   :linkPermitData [link-permit-data-kuntalupatunnus]
                                   :appsLinkingToUs [app-linking-to-us]}))

(ctc/validate-all-documents application-tyonjohtajan-nimeaminen)


(def application-tyonjohtajan-nimeaminen-v2
  (merge application-tyonjohtajan-nimeaminen  {:id "LP-753-2013-00003"
                                               :submitted 1426247899491
                                               :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"
                                                                  :id "5272668be8db5aaa01084601"
                                                                  :created 1383229067483}
                                               :documents [hakija-tj-henkilo
                                                           maksaja-henkilo
                                                           tyonjohtaja-v2
                                                           hankkeen-kuvaus-minimum]}))

(ctc/validate-all-documents application-tyonjohtajan-nimeaminen-v2)

(def application-suunnittelijan-nimeaminen
  (merge application-rakennuslupa {:id "LP-753-2013-00003"
                                   :organization "753-R"
                                   :state "submitted"
                                   :submitted 1426247899490
                                   :propertyId "75341600550007"
                                   :primaryOperation {:name "suunnittelijan-nimeaminen"
                                                      :id "527b3392e8dbbb95047a89de"
                                                      :created 1383805842761}
                                   :documents [hakija-henkilo
                                               maksaja-henkilo
                                               suunnittelija1
                                               hankkeen-kuvaus-minimum]
                                   :linkPermitData [link-permit-data-lupapistetunnus]
                                   :appsLinkingToUs [app-linking-to-us]}))

(ctc/validate-all-documents application-suunnittelijan-nimeaminen)


(defn- validate-minimal-person [person]
  (fact person => (contains {:nimi {:etunimi "Pena" :sukunimi "Penttil\u00e4"}})))

(defn- validate-address [address]
  (let [person-katu (:teksti (:osoitenimi address))
        person-postinumero (:postinumero address)
        person-postitoimipaikannimi (:postitoimipaikannimi address)
        person-maa (:valtioSuomeksi address)
        person-country (:valtioKansainvalinen address)
        person-address (:ulkomainenLahiosoite address)
        person-post (:ulkomainenPostitoimipaikka address)]
    (fact address => truthy)
    (fact person-katu => "katu")
    (fact person-postinumero =>"33800")
    (fact person-postitoimipaikannimi => "Tuonela")
    (fact person-maa => "Kiina")
    (fact person-country => "CHN")
    (fact person-address => "katu")
    (fact person-post => "Tuonela")))

(defn- validate-contact [m]
  (fact m => (contains {:puhelin "+358401234567"
                        :sahkopostiosoite "pena@example.com"})))

(defn- validate-person-wo-ssn [person]
  (validate-minimal-person person)
  (validate-contact person)
  (validate-address (:osoite person)))

(defn- validate-person [person]
  (validate-person-wo-ssn person)
  (fact (:henkilotunnus person) => "210281-9988"))

(defn- validate-minimal-company [company]
  (fact company => (contains {:nimi "Solita Oy" :liikeJaYhteisotunnus "1060155-5"}))
  ; postiosoite is required in KRYSP Rakennusvalvonta
  (validate-address (:postiosoite company)) ; up to 2.1.4
  (validate-address (get-in company [:postiosoitetieto :postiosoite]))) ; 2.1.5+

(defn- validate-company [company]
  (validate-minimal-company company)
  (fact "puhelin" (:puhelin company) => "03-389 1380")
  (fact "sahkopostiosoite" (:sahkopostiosoite company) => "yritys@example.com"))

(facts "Canonical hakija/henkilo model is correct"
  (let [osapuoli (tools/unwrapped (:data hakija-henkilo))
        hakija-model (get-osapuoli-data osapuoli (-> hakija-henkilo :schema-info :name keyword))
        henkilo (:henkilo hakija-model)
        ht (:henkilotiedot henkilo)
        yritys (:yritys hakija-model)]
    (fact "model" hakija-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi hakija-model) => "Rakennusvalvonta-asian hakija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi hakija-model) => "hakija")
    (fact "turvakieltoKytkin" (:turvakieltoKytkin hakija-model) => true)
    (fact "vainsahkoinenAsiointiKytkin" (:vainsahkoinenAsiointiKytkin henkilo) => true)
    (fact "suoramarkkinointikieltoKytkin" (:suoramarkkinointikieltoKytkin hakija-model) => true)
    (validate-person henkilo)
    (fact "yritys is nil" yritys => nil)))

(facts "Canonical asiamies-henkilo model is correct"
  (let [osapuoli (tools/unwrapped (:data asiamies-henkilo))
        asiamies-model (get-osapuoli-data osapuoli (-> asiamies-henkilo :schema-info :name keyword))
        henkilo (:henkilo asiamies-model)
        ht (:henkilotiedot henkilo)
        yritys (:yritys asiamies-model)]
    (fact "model" asiamies-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi asiamies-model) => "Hakijan asiamies")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi asiamies-model) => "muu osapuoli")
    (fact "turvakieltoKytkin" (:turvakieltoKytkin asiamies-model) => true)
    (fact "vainsahkoinenAsiointiKytkin" (:vainsahkoinenAsiointiKytkin henkilo) => true)
    (fact "suoramarkkinointikieltoKytkin" (:suoramarkkinointikieltoKytkin asiamies-model) => true)
    (validate-person henkilo)
    (fact "yritys is nil" yritys => nil)))

(facts "Canonical hakija/yritys model is correct"
  (let [osapuoli (tools/unwrapped (:data hakija-yritys))
        hakija-model (get-osapuoli-data osapuoli (-> hakija-yritys :schema-info :name keyword))
        henkilo (:henkilo hakija-model)
        yritys (:yritys hakija-model)]
    (fact "model" hakija-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi hakija-model) => "Rakennusvalvonta-asian hakija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi hakija-model) => "hakija")
    (fact "turvakieltoKytkin" (:turvakieltoKytkin hakija-model) => true)
    (fact "vainsahkoinenAsiointiKytkin" (:vainsahkoinenAsiointiKytkin yritys) => true)
    (fact "suoramarkkinointikieltoKytkin" (:suoramarkkinointikieltoKytkin hakija-model) => false)
    (validate-minimal-person henkilo)
    (validate-company yritys)))

(fact "Empty body"
  (empty? (get-parties-by-type
    {"paasuunnittelija" [{:data {}}]} :Suunnittelija ["paasuunnittelija"] get-suunnittelija-data)) => truthy)

(facts "Canonical paasuunnittelija/henkilo+yritys model is correct"
  (let [suunnittelija (tools/unwrapped (:data paasuunnittelija))
        suunnittelija-model (get-suunnittelija-data suunnittelija :paasuunnittelija)
        henkilo (:henkilo suunnittelija-model)
        yritys (:yritys suunnittelija-model)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "p\u00e4\u00e4suunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "p\u00e4\u00e4suunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "arkkitehti")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "ei tiedossa")
    (fact "vaadittuPatevyysluokka" (:vaadittuPatevyysluokka suunnittelija-model) => "AA")
    (fact "valmistumisvuosi" (:valmistumisvuosi suunnittelija-model) => "2010")
    (fact "kokemusvuodet" (:kokemusvuodet suunnittelija-model) => "5")
    (fact "FISEpatevyyskortti" (:FISEpatevyyskortti suunnittelija-model) => "http://www.ym.fi")
    (fact "FISEkelpoisuus" (:FISEkelpoisuus suunnittelija-model) => "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)")
    (validate-person henkilo)
    (validate-minimal-company yritys)))

(facts "Canonical suunnittelija1 model is correct"
  (let [suunnittelija (tools/unwrapped (:data suunnittelija1))
        suunnittelija-model (get-suunnittelija-data suunnittelija :suunnittelija)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "rakennusfysikaalinen suunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "erityissuunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "arkkitehti")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "B")
    (fact "vaadittuPatevyysluokka" (:vaadittuPatevyysluokka suunnittelija-model) => "C")
    (fact "valmistumisvuosi" (:valmistumisvuosi suunnittelija-model) => "2010")
    (fact "kokemusvuodet" (:kokemusvuodet suunnittelija-model) => "5")
    (fact "henkilo" (:henkilo suunnittelija-model) => truthy)
    (fact "yritys" (:yritys suunnittelija-model) => truthy)))

(facts "Canonical suunnittelija2 model is correct"
  (let [suunnittelija (tools/unwrapped (:data suunnittelija2))
        suunnittelija-model (get-suunnittelija-data suunnittelija :suunnittelija)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "GEO-suunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "erityissuunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "muu")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "AA")
    (fact "vaadittuPatevyysluokka" (:vaadittuPatevyysluokka suunnittelija-model) => "A")
    (fact "valmistumisvuosi" (:valmistumisvuosi suunnittelija-model) => "2010")
    (fact "kokemusvuodet" (:kokemusvuodet suunnittelija-model) => "5")
    (fact "henkilo" (:henkilo suunnittelija-model) => truthy)
    (fact "yritys" (:yritys suunnittelija-model) => truthy)))

(facts "Transforming old sunnittelija schema to canonical model is correct"
  (let [suunnittelija (tools/unwrapped (:data suunnittelija-old-schema-LUPA-771))
        suunnittelija-model (get-suunnittelija-data suunnittelija :suunnittelija)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "ARK-rakennussuunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "rakennussuunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "arkkitehti")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "B")
    (fact "valmistumisvuosi" (:valmistumisvuosi suunnittelija-model) => "2010")
    (fact "kokemusvuodet" (:kokemusvuodet suunnittelija-model) => "5")))

(facts "Canonical suunnittelija-blank-role model is correct"
  (let [suunnittelija (tools/unwrapped (:data suunnittelija-blank-role))
        suunnittelija-model (get-suunnittelija-data suunnittelija :suunnittelija)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "ei tiedossa")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "ei tiedossa")))

(facts "Canonical tyonjohtaja model is correct"
  (let [tyonjohtaja-unwrapped (tools/unwrapped (:data tyonjohtaja))
        tyonjohtaja-model (get-tyonjohtaja-data {} "fi" tyonjohtaja-unwrapped :tyonjohtaja)
        henkilo (:henkilo tyonjohtaja-model)
        yritys (:yritys tyonjohtaja-model)
        sijaistus-213 (get-in tyonjohtaja-model [:sijaistustieto :Sijaistus])]
    (fact "model" tyonjohtaja-model => truthy)
    (fact "VRKrooliKoodi" (:VRKrooliKoodi tyonjohtaja-model) => "ty\u00f6njohtaja")
    (fact "tyonjohtajaRooliKoodi" (:tyonjohtajaRooliKoodi tyonjohtaja-model) => (-> tyonjohtaja :data :kuntaRoolikoodi :value))
    (fact "no suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi tyonjohtaja-model) => nil)
    (fact "no FISEpatevyyskortti" (:FISEpatevyyskortti tyonjohtaja-model) => nil)
    (fact "no FISEkelpoisuus" (:FISEkelpoisuus tyonjohtaja-model) => nil)
    (fact "alkamisPvm" (:alkamisPvm tyonjohtaja-model) => "2014-02-13")
    (fact "paattymisPvm" (:paattymisPvm tyonjohtaja-model) => "2014-02-20")
    (fact "koulutus with 'Muu' selected" (:koulutus tyonjohtaja-model) => "muu")
    (fact "valmistumisvuosi" (:valmistumisvuosi tyonjohtaja-model) => (-> tyonjohtaja :data :patevyys-tyonjohtaja :valmistumisvuosi :value))
    (fact "patevyysvaatimusluokka (backwards compatibility)" (:patevyysvaatimusluokka tyonjohtaja-model) => (-> tyonjohtaja :data :patevyys-tyonjohtaja :patevyysvaatimusluokka :value))
    (fact "vaadittuPatevyysluokka" (:vaadittuPatevyysluokka tyonjohtaja-model) => (-> tyonjohtaja :data :patevyys-tyonjohtaja :patevyysvaatimusluokka :value))
    (fact "kokemusvuodet" (:kokemusvuodet tyonjohtaja-model) => (-> tyonjohtaja :data :patevyys-tyonjohtaja :kokemusvuodet :value))
    (fact "valvottavienKohteidenMaara" (:valvottavienKohteidenMaara tyonjohtaja-model) => (-> tyonjohtaja :data :patevyys-tyonjohtaja :valvottavienKohteidenMaara :value))
    (fact "tyonjohtajaHakemusKytkin" (:tyonjohtajaHakemusKytkin tyonjohtaja-model) => true)
    (fact "vastattavatTyotehtavat" (:vastattavatTyotehtavat tyonjohtaja-model) =>
      "kiinteistonVesiJaViemarilaitteistonRakentaminen,kiinteistonilmanvaihtolaitteistonRakentaminen,maanrakennustyo,rakennelmaTaiLaitos,Muu tyotehtava")
    (fact "henkilo" (:henkilo tyonjohtaja-model) => truthy)
    (fact "yritys" (:yritys tyonjohtaja-model) => truthy)
    (fact "sijaisuus" sijaistus-213 => truthy)
    (fact "sijaistettavan nimi 2.1.4" (:sijaistettavaHlo tyonjohtaja-model) => "Jaska Jokunen")
    (fact "sijaistettavan nimi 2.1.3" (:sijaistettavaHlo sijaistus-213) => "Jaska Jokunen")
    (fact "sijaistettava rooli" (:sijaistettavaRooli sijaistus-213) => (:tyonjohtajaRooliKoodi tyonjohtaja-model))
    (fact "sijaistettavan alkamisPvm" (:alkamisPvm sijaistus-213) => "2014-02-13")
    (fact "sijaistettavan paattymisPvm" (:paattymisPvm sijaistus-213) => "2014-02-20")
    (validate-person henkilo)
    (validate-minimal-company yritys)))

(facts "Canonical tyonjohtaja v2 model is correct"
  (let [tyonjohtaja-unwrapped (tools/unwrapped (:data tyonjohtaja-v2))
        tyonjohtaja-model (get-tyonjohtaja-v2-data {:permitSubtype "tyonjohtaja-hakemus"} "fi" tyonjohtaja-unwrapped :tyonjohtaja)]
    (fact "tyonjohtajanHyvaksynta (vainTamaHankeKytkin)" (:vainTamaHankeKytkin tyonjohtaja-model) => (-> tyonjohtaja-v2 :data :tyonjohtajanHyvaksynta :tyonjohtajanHyvaksynta :value))
    (fact "koulutus" (:koulutus tyonjohtaja-model) => (-> tyonjohtaja-v2 :data :patevyys-tyonjohtaja :koulutusvalinta :value))
    (fact "valmistumisvuosi" (:valmistumisvuosi tyonjohtaja-model) => (-> tyonjohtaja-v2 :data :patevyys-tyonjohtaja :valmistumisvuosi :value))
    (fact "patevyysvaatimusluokka (backwards compatibility)" (:patevyysvaatimusluokka tyonjohtaja-model) => (-> tyonjohtaja-v2 :data :patevyysvaatimusluokka :value))
    (fact "vaadittuPatevyysluokka" (:vaadittuPatevyysluokka tyonjohtaja-model) => (-> tyonjohtaja-v2 :data :patevyysvaatimusluokka :value))
    (fact "kokemusvuodet" (:kokemusvuodet tyonjohtaja-model) => (-> tyonjohtaja-v2 :data :patevyys-tyonjohtaja :kokemusvuodet :value))
    (fact "valvottavienKohteidenMaara" (:valvottavienKohteidenMaara tyonjohtaja-model) => (-> tyonjohtaja-v2 :data :patevyys-tyonjohtaja :valvottavienKohteidenMaara :value))
    (fact "tyonjohtajaHakemusKytkin" (:tyonjohtajaHakemusKytkin tyonjohtaja-model) => true)
    (fact "vastattavatTyotehtavat"
          (:vastattavatTyotehtavat tyonjohtaja-model) => "rakennuksenPurkaminen,ivLaitoksenKorjausJaMuutostyo,uudisrakennustyoIlmanMaanrakennustoita,maanrakennustyot,sisapuolinenKvvTyo,Muu tyotehtava")
    (fact "vastattavaTyo contents"
          (map (comp :vastattavaTyo :VastattavaTyo) (:vastattavaTyotieto tyonjohtaja-model)) => (just #{"Sis\u00e4puolinen KVV-ty\u00f6"
                                                                                                        "Muu tyotehtava"}))))

(facts "Canonical tyonjohtaja-blank-role-and-blank-qualification model is correct"
  (let [tyonjohtaja-unwrapped (tools/unwrapped (:data tyonjohtaja-blank-role-and-blank-qualification))
        tyonjohtaja-model (get-tyonjohtaja-data {} "fi" tyonjohtaja-unwrapped :tyonjohtaja)]
    (fact "model" tyonjohtaja-model => truthy)
    (fact "tyonjohtajaRooliKoodi" (:tyonjohtajaRooliKoodi tyonjohtaja-model) => "ei tiedossa")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi tyonjohtaja-model) => "ei tiedossa")
    (fact "patevyysvaatimusluokka (backwards compatibility)" (:patevyysvaatimusluokka tyonjohtaja-model) => "ei tiedossa")
    (fact "vaadittuPatevyysluokka" (:vaadittuPatevyysluokka tyonjohtaja-model) => "ei tiedossa")
    (fact "tyonjohtajaHakemusKytkin" (:tyonjohtajaHakemusKytkin tyonjohtaja-model) => false)))

(facts "Canonical tyonjohtajan sijaistus model is correct"
  (let [tyonjohtaja       (tools/unwrapped (:data tyonjohtajan-sijaistus-blank-dates))
        tyonjohtaja-model (get-tyonjohtaja-data {} "fi" tyonjohtaja :tyonjohtaja)
        sijaistus-213     (-> tyonjohtaja-model :sijaistustieto :Sijaistus)]
    (facts "model 2.1.3" sijaistus-213 => truthy
      (fact "missing alkamisPvm" (:alkamisPvm sijaistus-213) => nil)
      (fact "empty paattymisPvm" (:paattymisPvm sijaistus-213) => nil)
      (fact "sijaistettavaRooli" (:sijaistettavaRooli sijaistus-213) => "KVV-ty\u00f6njohtaja")
      (fact "sijaistettavaHlo"   (:sijaistettavaHlo sijaistus-213) => "Jaska Jokunen"))))

(facts "Canonical tyonjohtajan vastattavaTyotieto is correct"
  (let [tyonjohtaja       (-> tyonjohtaja :data (dissoc :sijaistus) tools/unwrapped)
        tyonjohtaja-model (get-tyonjohtaja-data {} "fi" tyonjohtaja :tyonjohtaja)
        sijaistus-213     (-> tyonjohtaja-model :sijaistustieto)]
    (:sijaistustieto tyonjohtaja-model) => nil
    (fact "no dates" (-> tyonjohtaja-model :vastattavaTyotieto first :VastattavaTyo keys) => [:vastattavaTyo])
    (fact "vastattavaTyo"
      (map (comp :vastattavaTyo :VastattavaTyo) (-> tyonjohtaja-model :vastattavaTyotieto))
      =>
      (just #{"Kiinteist\u00f6n vesi- ja viem\u00e4rilaitteiston rakentaminen"
              "Kiinteist\u00f6n ilmanvaihtolaitteiston rakentaminen"
              "Maanrakennusty\u00f6"
              "Muu tyotehtava"
              "Rakennelma tai laitos"}))))

(facts "Canonical maksaja/henkilo model is correct"
  (let [osapuoli (tools/unwrapped (:data maksaja-henkilo))
        maksaja-model (get-osapuoli-data osapuoli :maksaja)
        henkilo (:henkilo maksaja-model)
        yritys (:yritys maksaja-model)]
    (fact "model" maksaja-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi maksaja-model) => "Rakennusvalvonta-asian laskun maksaja")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi maksaja-model) => "maksaja")
    (fact "turvakieltoKytkin" (:turvakieltoKytkin maksaja-model) => true)
    (validate-person henkilo)
    (fact "yritys is nil" yritys => nil)))

(defn- validate-einvoice [einvoice]
  (fact "ovt-tunnus" (:ovtTunnus einvoice) => "003712345671")
  (fact "verkkolaskuTunnus" (:verkkolaskuTunnus einvoice) => "laskutunnus-1234")
  (fact "valittajaTunnus" (:valittajaTunnus einvoice) => "BAWCFI22"))

(facts "Canonical maksaja/yritys model is correct"
  (let [osapuoli (tools/unwrapped (:data maksaja-yritys))
        maksaja-model (get-osapuoli-data osapuoli :maksaja)
        henkilo (:henkilo maksaja-model)
        yritys (:yritys maksaja-model)
        verkkolaskutustieto (-> yritys :verkkolaskutustieto :Verkkolaskutus)]
    (fact "model" maksaja-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi maksaja-model) => "Rakennusvalvonta-asian laskun maksaja")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi maksaja-model) => "maksaja")
    (validate-minimal-person henkilo)
    (validate-company yritys)
    (validate-einvoice verkkolaskutustieto)))

(testable-privates lupapalvelu.document.canonical-common get-handler)

(facts "Handler is sonja"
  (let [handler (get-handler (tools/unwrapped application-rakennuslupa))
        name (get-in handler [:henkilo :nimi])]
    (fact "handler" handler => truthy)
    (fact "etunimi" (:etunimi name) => "Sonja")
    (fact "sukunimi" (:sukunimi name) => "Sibbo")))

(testable-privates lupapalvelu.document.rakennuslupa-canonical get-operations)

(facts "Toimenpiteet"
  (let [documents (by-type (:documents (tools/unwrapped application-rakennuslupa)))
        actions (get-operations documents (tools/unwrapped application-rakennuslupa))]
    (fact "actions" (seq actions) => truthy)))

(testable-privates lupapalvelu.document.rakennuslupa-canonical get-huoneisto-data)

(facts "Huoneisto is correct"
  (let [huoneistot (-> uusi-rakennus
                     (get-in [:data :huoneistot])
                     tools/unwrapped
                     get-huoneisto-data)
        h2 (last huoneistot), h1 (first huoneistot)]
    (fact "h1 huoneistot count" (count huoneistot) => 2)
    (fact "h1 muutostapa" (:muutostapa h1) => "lis\u00e4ys")
    (fact "h1 huoneluku" (:huoneluku h1) => "66")
    (fact "h1 keittionTyyppi" (:keittionTyyppi h1) => "keittio")
    (fact "h1 huoneistoala" (:huoneistoala h1) => "56")
    (fact "h1 huoneistonTyyppi" (:huoneistonTyyppi h1) => "asuinhuoneisto")
    (fact "h1 varusteet: WCKytkin" (-> h1 :varusteet :WCKytkin) => true)
    (fact "h1 varusteet: ammeTaiSuihkuKytkin" (-> h1 :varusteet :ammeTaiSuihkuKytkin) => false)
    (fact "h1 varusteet: saunaKytkin" (-> h1 :varusteet :saunaKytkin) => false)
    (fact "h1 varusteet: parvekeTaiTerassiKytkin" (-> h1 :varusteet :parvekeTaiTerassiKytkin) => true)
    (fact "h1 varusteet: lamminvesiKytkin" (-> h1 :varusteet :lamminvesiKytkin) => false)
    (fact "h1 huoneistotunnus" (:huoneistotunnus h1) => truthy)
    (fact "h1 huoneistotunnus: porras" (-> h1 :huoneistotunnus :porras) => "A")
    (fact "h1 huoneistotunnus: huoneistonumero" (-> h1 :huoneistotunnus :huoneistonumero) => "001")
    (fact "h1 huoneistotunnus: jakokirjain" (-> h1 :huoneistotunnus :jakokirjain) => "a")

    (fact "h2 muutostapa" (:muutostapa h2) => "lis\u00e4ys")
    (fact "h2 huoneluku" (:huoneluku h2) => "12")
    (fact "h2 keittionTyyppi" (:keittionTyyppi h2) => "keittokomero")
    (fact "h2 huoneistoala" (:huoneistoala h2) => "02")
    (fact "h2 huoneistonTyyppi" (:huoneistonTyyppi h2) => "toimitila")
    (fact "h2 varusteet: WCKytkin" (-> h2 :varusteet :WCKytkin) => false)
    (fact "h2 varusteet: ammeTaiSuihkuKytkin" (-> h2 :varusteet :ammeTaiSuihkuKytkin) => true)
    (fact "h2 varusteet: saunaKytkin" (-> h2 :varusteet :saunaKytkin) => true)
    (fact "h2 varusteet: parvekeTaiTerassiKytkin" (-> h2 :varusteet :parvekeTaiTerassiKytkin) => false)
    (fact "h2 varusteet: lamminvesiKytkin" (-> h2 :varusteet :lamminvesiKytkin) => true)
    (fact "h2 huoneistotunnus" (:huoneistotunnus h1) => truthy)
    (fact "h2 huoneistotunnus: porras" (-> h1 :huoneistotunnus :porras) => "A")
    (fact "h2 huoneistotunnus: huoneistonumero" (-> h1 :huoneistotunnus :huoneistonumero) => "001")
    (fact "h2 huoneistotunnus: jakokirjain" (-> h1 :huoneistotunnus :jakokirjain) => "a")))

(testable-privates lupapalvelu.document.rakennuslupa-canonical get-rakennus)

(facts "When muu-lammonlahde is empty, lammonlahde is used"
  (let [doc (tools/unwrapped {:data {:lammitys {:lammitystapa {:value nil}
                                               :lammonlahde  {:value "turve"}
                                               :muu-lammonlahde {:value nil}}}})
        rakennus (get-rakennus application-rakennuslupa doc)]
    (fact (:polttoaine (:lammonlahde (:rakennuksenTiedot rakennus))) => "turve")))

(fact "LPK-427: When energiatehokkuusluku is set, energiatehokkuusluvunYksikko is inluded"
  (let [doc (tools/unwrapped {:data {:luokitus {:energiatehokkuusluku {:value "124"}
                                                :energiatehokkuusluvunYksikko {:value "kWh/m2"}}}})
        rakennus (get-rakennus application-rakennuslupa doc)]
    (get-in rakennus [:rakennuksenTiedot :energiatehokkuusluvunYksikko]) => "kWh/m2"))

(fact "LPK-427: When energiatehokkuusluku is not set, energiatehokkuusluvunYksikko is excluded"
  (let [doc (tools/unwrapped {:data {:luokitus {:energiatehokkuusluku {:value ""}
                                               :energiatehokkuusluvunYksikko {:value "kWh/m2"}}}})
        rakennus (get-rakennus application-rakennuslupa doc)]
    (get-in rakennus [:rakennuksenTiedot :energiatehokkuusluvunYksikko]) => nil))

(facts "When muu-lammonlahde is specified, it is used"
  (let [doc (tools/unwrapped {:data {:lammitys {:lammitystapa {:value nil}
                                               :lammonlahde  {:value "other"}
                                               :muu-lammonlahde {:value "fuusioenergialla"}}}})
        rakennus (get-rakennus application-rakennuslupa doc)]
    (fact (:muu (:lammonlahde (:rakennuksenTiedot rakennus))) => "fuusioenergialla")))

(facts "rakennuksenTiedot"
  (let [doc (tools/unwrapped (assoc-in uusi-rakennus [:data :varusteet :liitettyJatevesijarjestelmaanKytkin :value] true))
        {tiedot :rakennuksenTiedot} (get-rakennus application-rakennuslupa doc)]

    (fact "liitettyJatevesijarjestelmaanKytkin"
      (:liitettyJatevesijarjestelmaanKytkin tiedot) => true)

    (fact "rakennustunnus"
      (:rakennustunnus tiedot) => {:jarjestysnumero nil,
                                   :kiinttun "21111111111111"
                                   :muuTunnustieto [{:MuuTunnus {:tunnus "kerrostalo-rivitalo-id" :sovellus "toimenpideId"}}
                                                    {:MuuTunnus {:tunnus "kerrostalo-rivitalo-id" :sovellus "Lupapiste"}}]
                                   :rakennuksenSelite "A: kerrostalo-rivitalo-kuvaus"})))

(facts ":Rakennuspaikka with :kaavanaste/:kaavatilanne"
  (let [rakennuspaikka (:rakennuspaikka (documents-by-type-without-blanks (tools/unwrapped application-rakennuslupa)))]

    (fact "When kaavatilanne is set, also kaavanaste is added to canonical"
      (let [result (first (get-bulding-places rakennuspaikka application-rakennuslupa))]

        (get-in result [:Rakennuspaikka :kaavatilanne]) => truthy
        (get-in result [:Rakennuspaikka :kaavanaste]) => truthy))

    (fact "If only kaavanaste is set, kaavatilanne is not in canonical"
      (let [rakennuspaikka (assoc-in
                             (util/dissoc-in (first rakennuspaikka) [:data :kaavatilanne])
                             [:data :kaavanaste]
                             "yleis")
            result (first (get-bulding-places [rakennuspaikka] application-rakennuslupa))]

        (get-in result [:Rakennuspaikka :kaavanaste]) => truthy
        (get-in result [:Rakennuspaikka :kaavatilanne]) => falsey))

    (fact "When no mapping from kaavatilanne value to kaavanaste exists, kaavanaste should be 'ei tiedossa'"
      (let [rakennuspaikka (assoc-in (first rakennuspaikka )[:data :kaavatilanne] "maakuntakaava")
            result (first (get-bulding-places [rakennuspaikka] application-rakennuslupa))]

        (get-in result [:Rakennuspaikka :kaavanaste]) => "ei tiedossa"))

    (fact "When kaavanaste/kaavatilanne are not in rakennuspaikka, they are not in canonical either"
      (let [rakennuspaikka (util/dissoc-in rakennuspaikka [:Rakennuspaikka :kaavatilanne])
            result (first (get-bulding-places [rakennuspaikka] application-rakennuslupa))]

        (get-in result [:Rakennuspaikka]) => truthy
        (get-in result [:Rakennuspaikka :kaavanaste]) => falsey
        (get-in result [:Rakennuspaikka :kaavatilanne]) => falsey))))

(fl/facts* "Canonical model is correct"
  (let [canonical (application-to-canonical application-rakennuslupa "sv") => truthy
        rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy

        toimituksenTiedot (:toimituksenTiedot rakennusvalvonta) => truthy
        aineistonnimi (:aineistonnimi toimituksenTiedot ) => "s"
        lausuntotieto (first (:lausuntotieto rakennusvalvontaasia))  => truthy
        Lausunto (:Lausunto lausuntotieto) => truthy
        viranomainen (:viranomainen Lausunto) => "Paloviranomainen"
        pyyntoPvm (:pyyntoPvm Lausunto) => "2013-05-09"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        LL (:Lausunto lausuntotieto) => truthy  ;Lausunto oli jo kaytossa, siksi LL
        viranomainen (:viranomainen LL) => "Paloviranomainen"
        lausunto (:lausunto LL) => "Savupiippu pit\u00e4\u00e4 olla."
        lausuntoPvm (:lausuntoPvm LL) => "2013-05-09"
        Puolto (-> LL :puoltotieto :Puolto) => truthy
        puolto (:puolto Puolto) => "ehdollinen"

        osapuolettieto (:osapuolettieto rakennusvalvontaasia) => truthy
        osapuolet (:Osapuolet osapuolettieto) => truthy
        osapuolitieto-hakija (first (:osapuolitieto osapuolet)) => truthy
        osapuolitieto-hakijan-asiamies (first (filter #(= (get-in % [:Osapuoli :kuntaRooliKoodi]) "Hakijan asiamies") (:osapuolitieto osapuolet))) => truthy
        hakija-osapuoli1 (:Osapuoli osapuolitieto-hakija) => truthy
        hakijan-asiamies1 (:Osapuoli osapuolitieto-hakijan-asiamies) => truthy
        suunnittelijat (:suunnittelijatieto osapuolet) => truthy
        paasuunnitelija (:Suunnittelija (last suunnittelijat)) => truthy
        tyonjohtajat (:tyonjohtajatieto osapuolet) => truthy
        tyonjohtajatieto (:Tyonjohtaja (last tyonjohtajat)) => truthy

        ;naapuritieto (:naapuritieto osapuolet) => truthy
        ;naapuricount (count naapuritieto) => 2
        ;naapuri (first naapuritieto) => truthy
        ;Naapuri (:Naapuri naapuri) => truthy
        ;naapuri-henkilo (:henkilo Naapuri) => "PORTAALIA TESTAA"
        ;kiiteistotunnus (:kiinteistotunnus Naapuri) => "75342600060211"
        ;hallintasuhde (:hallintasuhde Naapuri) => "Ei tiedossa"

        ;naapuri (last naapuritieto) => truthy
        ;Naapuri (:Naapuri naapuri) => truthy
        ;naapuri-henkilo (:henkilo Naapuri) => "L\u00f6nnqvist, Rauno Georg Christian"
        ;kiiteistotunnus (:kiinteistotunnus Naapuri) => "75342600090092"
        ;hallintasuhde (:hallintasuhde Naapuri) => "Ei tiedossa"

        sijaistus (:sijaistustieto tyonjohtajatieto) => truthy
        sijaistus (:Sijaistus (last sijaistus)) = truthy
        rakennuspaikkatiedot (:rakennuspaikkatieto rakennusvalvontaasia) => truthy
        rakennuspaikkatieto (first rakennuspaikkatiedot) => truthy
        rakennuspaikka (:Rakennuspaikka rakennuspaikkatieto) => truthy
        rakennuspaikanKiinteistotieto (:rakennuspaikanKiinteistotieto rakennuspaikka) => truthy
        RakennuspaikanKiinteistotieto (:RakennuspaikanKiinteisto rakennuspaikanKiinteistotieto) => truthy
        kiinteistotieto (:kiinteistotieto RakennuspaikanKiinteistotieto) => truthy
        Kiinteisto (:Kiinteisto kiinteistotieto) => truthy
        kaavatilanne (:kaavatilanne rakennuspaikka) => truthy
        toimenpiteet(:toimenpidetieto rakennusvalvontaasia) => truthy
        toimenpide (:Toimenpide (nth toimenpiteet 1)) => truthy
        muu-muutostyo (:Toimenpide (nth toimenpiteet 0)) => truthy
        laajennus-t (:Toimenpide (nth toimenpiteet 2)) => truthy
        purku-t (:Toimenpide (nth toimenpiteet 3)) => truthy
        kaupunkikuva-t (:Toimenpide (nth toimenpiteet 4)) => truthy
        rakennustieto (:rakennustieto toimenpide) => truthy
        rakennus (:Rakennus rakennustieto) => truthy
        rakennuksen-omistajatieto (:Omistaja(first (:omistajatieto rakennus))) => truthy
        rakennuksentiedot (:rakennuksenTiedot rakennus) => truthy
        lisatiedot (:lisatiedot rakennusvalvontaasia) => truthy
        Lisatiedot (:Lisatiedot lisatiedot) => truthy
        kayttotapaus (:kayttotapaus rakennusvalvontaasia) => truthy
        asianTiedot (:asianTiedot rakennusvalvontaasia) => truthy
        Asiantiedot (:Asiantiedot asianTiedot) => truthy
        vahainen-poikkeaminen (:vahainenPoikkeaminen Asiantiedot) => truthy
        rakennusvalvontasian-kuvaus (:rakennusvalvontaasianKuvaus Asiantiedot) => truthy
        luvanTunnisteTiedot (:luvanTunnisteTiedot rakennusvalvontaasia) => truthy
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        kasittelynTilatieto (:kasittelynTilatieto rakennusvalvontaasia) => truthy]

    (fact "contains nil" (util/contains-value? canonical nil?) => falsey)
    (fact "paasuunnitelija" paasuunnitelija => (contains {:suunnittelijaRoolikoodi "p\u00e4\u00e4suunnittelija"}))
    (fact "Osapuolien maara" (+ (count suunnittelijat) (count tyonjohtajat) (count (:osapuolitieto osapuolet))) => 9)
    (fact "rakennuspaikkojen maara" (count rakennuspaikkatiedot) => 1)
    (fact "tilanNimi" (:tilannimi Kiinteisto) => "Hiekkametsa")
    (fact "kiinteistotunnus" (:kiinteistotunnus Kiinteisto) => "21111111111111")
    (fact "maaraalaTunnus" (:maaraAlaTunnus Kiinteisto) => nil)
    (fact "kokotilakytkin" (:kokotilaKytkin RakennuspaikanKiinteistotieto) => truthy)
    (fact "hallintaperuste" (:hallintaperuste RakennuspaikanKiinteistotieto) => "oma")
    (facts "Kaavatilanne"
      (fact "is 'oikeusvaikutteinen yleiskaava'" kaavatilanne => "oikeusvaikutteinen yleiskaava")
      (fact "mapping has added correct :kaavanaste to canonical" (:kaavanaste rakennuspaikka) => "yleis"))

    (fact "Toimenpidetieto"  (count toimenpiteet) => 5)
    (fact "rakentajaTyyppi" (:rakentajatyyppi rakennus) => "muu")
    (fact "kayttotarkoitus" (:kayttotarkoitus rakennuksentiedot) => "012 kahden asunnon talot")
    (fact "rakentamistapa" (:rakentamistapa rakennuksentiedot) => "elementti")

    (fact "tilavuus" (:tilavuus rakennuksentiedot) => "1500")
    (fact "kokonaisala" (:kokonaisala rakennuksentiedot) => "1000")
    (fact "kellarinpinta-ala" (:kellarinpinta-ala rakennuksentiedot) => "100")
    (fact "kerrosluku" (:kerrosluku rakennuksentiedot) => "2")
    (fact "kerrosala" (:kerrosala rakennuksentiedot) => "180")
    (fact "rakennusoikeudellinenKerrosala" (:rakennusoikeudellinenKerrosala rakennuksentiedot) => "160")

    (fact "paloluokka" (:paloluokka rakennuksentiedot) => "P1")
    (fact "energialuokka" (:energialuokka rakennuksentiedot) => "C")
    (fact "energiatehokkuusluku" (:energiatehokkuusluku rakennuksentiedot) => "124")
    (fact "energiatehokkuusluvunYksikko" (:energiatehokkuusluvunYksikko rakennuksentiedot) => "kWh/m2")

    (fact "rakennuksen omistajalaji" (:omistajalaji (:omistajalaji rakennuksen-omistajatieto)) => "muu yksityinen henkil\u00f6 tai perikunta")
    (fact "KuntaRooliKoodi" (:kuntaRooliKoodi rakennuksen-omistajatieto) => "Rakennuksen omistaja")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi rakennuksen-omistajatieto) => "rakennuksen omistaja")
    (fact "Lisatiedot suoramarkkinointikielto" (:suoramarkkinointikieltoKytkin Lisatiedot) => nil?)
    (fact "vakuus" (:vakuus Lisatiedot) => nil)
    (fact "Lisatiedot asiointikieli" (:asioimiskieli Lisatiedot) => "ruotsi")
    (fact "rakennusvalvontasian-kuvaus" rakennusvalvontasian-kuvaus =>"Uuden rakennuksen rakentaminen tontille.\n\nPuun kaataminen:Puun kaataminen")
    (fact "kayttotapaus" kayttotapaus => "Uusi hakemus")

    (fact "Muu tunnus" (:tunnus MuuTunnus) => "LP-753-2013-00001")
    (fact "Sovellus" (:sovellus MuuTunnus) => "Lupapiste")
    (fact "Toimenpiteen kuvaus" (-> toimenpide :uusi :kuvaus) => "Asuinkerrostalon tai rivitalon rakentaminen")
    (fact "Toimenpiteen kuvaus" (-> muu-muutostyo :muuMuutosTyo :kuvaus) => "Muu rakennuksen muutosty\u00f6")
    (fact "Muu muutostyon perusparannuskytkin" (-> muu-muutostyo :muuMuutosTyo :perusparannusKytkin) => true)
    (fact "Muutostyon laji" (-> muu-muutostyo :muuMuutosTyo :muutostyonLaji) => "muut muutosty\u00f6t")
    (fact "valtakunnallinenNumero" (-> muu-muutostyo :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :valtakunnallinenNumero) => "1234567892")
    (fact "muu muutostyon rakennuksen tunnus" (-> muu-muutostyo :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :jarjestysnumero) => 2)
    (fact "Laajennuksen kuvaus" (-> laajennus-t :laajennus :kuvaus) => "Rakennuksen laajentaminen tai korjaaminen")
    (fact "Laajennuksen rakennuksen tunnus" (-> laajennus-t :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :jarjestysnumero) => 3)
    (fact "Laajennuksen rakennuksen kiintun" (-> laajennus-t :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :kiinttun) => "21111111111111")

    (fact "Laajennuksen pinta-alat"
      (let [huoneistoalat (get-in laajennus-t [:laajennus :laajennuksentiedot :huoneistoala])]
        (fact "x 2" (count huoneistoalat) => 2)
        (fact "Laajennuksen pintaala keys" (keys (first huoneistoalat)) => (just #{:kayttotarkoitusKoodi :pintaAla}))

        (fact "positive and negative numbers"
          (-> huoneistoalat first :pintaAla) => "150"
          (-> huoneistoalat second :pintaAla) => "-10")))

    (fact "Laajennuksen kerrosala" (get-in laajennus-t [:laajennus :laajennuksentiedot :kerrosala]) => "180")
    (fact "Laajennuksen kokonaisala" (get-in laajennus-t [:laajennus :laajennuksentiedot :kokonaisala]) => "-10")
    (fact "Laajennuksen rakennusoikeudellinenKerrosala" (get-in laajennus-t [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]) => "160")

    (fact "Purkamisen kuvaus" (-> purku-t :purkaminen :kuvaus) => "Rakennuksen purkaminen")
    (fact "Poistuma pvm" (-> purku-t :purkaminen :poistumaPvm) => "2013-04-17")
    (fact "Purku: syy" (-> purku-t :purkaminen :purkamisenSyy) => "tuhoutunut")
    (facts "Purku: rakennus"
      (let [rakennus (get-in purku-t [:rakennustieto :Rakennus])]
        (fact "omistaja" (-> rakennus :omistajatieto first :Omistaja :henkilo :sahkopostiosoite) => "pena@example.com")
        (fact "yksilointitieto" (-> rakennus :yksilointitieto) => "purkaminen-id")
        (fact "rakennusnro" (-> rakennus :rakennuksenTiedot :rakennustunnus :rakennusnro) => "001")
        (fact "kayttotarkoitus" (-> rakennus :rakennuksenTiedot :kayttotarkoitus) => "012 kahden asunnon talot")))


    (facts "Kaupunkikuvatoimenpide"
           (fact "Kaupunkikuvatoimenpiteen kuvaus" (-> kaupunkikuva-t :kaupunkikuvaToimenpide :kuvaus) => "Aidan rakentaminen")
           (fact "Kaupunkikuvatoimenpiteen rakennelman kuvaus" (-> kaupunkikuva-t :rakennelmatieto :Rakennelma :kuvaus :kuvaus) => "Aidan rakentaminen rajalle")
           (fact "Rakennelman yksilointitieto" (-> kaupunkikuva-t :rakennelmatieto :Rakennelma :yksilointitieto) => "kaupunkikuva-id"))

    (facts "Statement draft"
      (let [lausunnot (:lausuntotieto rakennusvalvontaasia)
            lausunto1 (:Lausunto (first lausunnot))
            lausunto2 (:Lausunto (second lausunnot))]
        (count lausunnot) => 2
        (fact "First is given statement, has lausuntotieto"
          (:lausuntotieto lausunto1) => truthy)
        (fact "Second is draft, does not have lausuntotieto"
          (:lausuntotieto lausunto2) => nil
          (keys lausunto2) => (just [:id :pyyntoPvm :viranomainen] :in-any-order))))))

(fl/facts* "Canonical model ilman ilmoitusta is correct"
  (let [canonical (application-to-canonical application-rakennuslupa-ilman-ilmoitusta "sv") => truthy
        rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy
        rakennuspaikkatiedot (:rakennuspaikkatieto rakennusvalvontaasia) => truthy
        rakennuspaikkatieto2 (first rakennuspaikkatiedot) => truthy
        rakennuspaikka2 (:Rakennuspaikka rakennuspaikkatieto2) => truthy
        rakennuspaikanKiinteistotieto2 (:rakennuspaikanKiinteistotieto rakennuspaikka2) => truthy
        RakennuspaikanKiinteistotieto2 (:RakennuspaikanKiinteisto rakennuspaikanKiinteistotieto2) => truthy
        kiinteistotieto2 (:kiinteistotieto RakennuspaikanKiinteistotieto2) => truthy
        Kiinteisto2 (:Kiinteisto kiinteistotieto2) => truthy
        kaavatilanne2 (:kaavatilanne rakennuspaikka2) => truthy]

    (fact "contains nil" (util/contains-value? canonical nil?) => falsey)
    (fact "rakennuspaikkojen maara" (count rakennuspaikkatiedot) => 1)
    (fact "tilanNimi (ilman ilmoitusta)" (:tilannimi Kiinteisto2) => "Eramaa")
    (fact "kiinteistotunnus (ilman ilmoitusta)" (:kiinteistotunnus Kiinteisto2) => "21111111111111")
    (fact "maaraalaTunnus (ilman ilmoitusta)" (:maaraAlaTunnus Kiinteisto2) => nil)
    (fact "kokotilakytkin (ilman ilmoitusta)" (:kokotilaKytkin RakennuspaikanKiinteistotieto2) => truthy)
    (fact "hallintaperuste (ilman ilmoitusta)" (:hallintaperuste RakennuspaikanKiinteistotieto2) => "vuokra")
    (facts "Kaavatilanne (ilman ilmoitusta)"
      (fact "is 'oikeusvaikutukseton yleiskaava'" kaavatilanne2 => "oikeusvaikutukseton yleiskaava")
      (fact "mapping has added correct :kaavanaste to canonical" (:kaavanaste rakennuspaikka2) => "ei tiedossa"))))


(fl/facts* "Canonical model has correct puolto"
  (let [application (assoc-in application-rakennuslupa [:statements 0 :status] "palautettu")
        canonical (application-to-canonical application "sv") => truthy
        rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy
        puolto (-> rakennusvalvontaasia :lausuntotieto first :Lausunto :lausuntotieto :Lausunto :puoltotieto :Puolto :puolto) => truthy]
    puolto => "palautettu"))

(defn check-common-tyonjohtaja-canonical [case application]
  (fact {:midje/description (str case " canonical")}
    (fl/facts* "Canonical model is correct"
      (let [canonical (application-to-canonical application "fi") => truthy
            rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
            rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
            rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy

            viitelupatieto (first (:viitelupatieto rakennusvalvontaasia)) => truthy
            viitelupatieto-LupaTunnus (:LupaTunnus viitelupatieto) => truthy
            viitelupatieto-MuuTunnus (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus) => falsey

            luvanTunnisteTiedot-MuuTunnus (-> rakennusvalvontaasia
                                              :luvanTunnisteTiedot
                                              :LupaTunnus
                                              :muuTunnustieto
                                              :MuuTunnus) => truthy

            osapuolet-vec (-> rakennusvalvontaasia :osapuolettieto :Osapuolet :osapuolitieto) => sequential?

            ;; henkilotyyppinen maksaja
            rooliKoodi-laskun-maksaja "Rakennusvalvonta-asian laskun maksaja"
            maksaja-filter-fn #(= (-> % :Osapuoli :kuntaRooliKoodi) rooliKoodi-laskun-maksaja)
            maksaja-Osapuoli (:Osapuoli (first (filter maksaja-filter-fn osapuolet-vec)))
            maksaja-Osapuoli-henkilo (:henkilo maksaja-Osapuoli)
            maksaja-Osapuoli-yritys (:yritys maksaja-Osapuoli)

            ;; henkilotyyppinen hakija
            rooliKoodi-hakija "Rakennusvalvonta-asian hakija"
            hakija-filter-fn #(= (-> % :Osapuoli :kuntaRooliKoodi) rooliKoodi-hakija)
            hakija-Osapuoli (:Osapuoli (first (filter hakija-filter-fn osapuolet-vec)))
            hakija-Osapuoli-henkilo (:henkilo hakija-Osapuoli)
            hakija-Osapuoli-yritys (:yritys hakija-Osapuoli)

            kayttotapaus (:kayttotapaus rakennusvalvontaasia) => truthy
            Asiantiedot (-> rakennusvalvontaasia :asianTiedot :Asiantiedot) => truthy
            vahainen-poikkeaminen (:vahainenPoikkeaminen Asiantiedot) => falsey
            rakennusvalvontasian-kuvaus (:rakennusvalvontaasianKuvaus Asiantiedot) => truthy

            viitelupatieto-LupaTunnus_2 (:LupaTunnus (get-viitelupatieto link-permit-data-lupapistetunnus))]

        (facts "Maksaja is correct"
          (fact "kuntaRooliKoodi" (:kuntaRooliKoodi maksaja-Osapuoli) => "Rakennusvalvonta-asian laskun maksaja")
          (fact "VRKrooliKoodi" (:VRKrooliKoodi maksaja-Osapuoli) => "maksaja")
          (fact "turvakieltoKytkin" (:turvakieltoKytkin maksaja-Osapuoli) => true)
          (validate-person maksaja-Osapuoli-henkilo)
          (fact "yritys is nil" maksaja-Osapuoli-yritys => nil))

        (facts "Hakija is correct"
          (fact "kuntaRooliKoodi" (:kuntaRooliKoodi hakija-Osapuoli) => "Rakennusvalvonta-asian hakija")
          (fact "VRKrooliKoodi" (:VRKrooliKoodi hakija-Osapuoli) => "hakija")
          (fact "turvakieltoKytkin" (:turvakieltoKytkin hakija-Osapuoli) => true)
          (validate-person hakija-Osapuoli-henkilo)
          (fact "yritys is nil" hakija-Osapuoli-yritys => nil))

        (facts "\"kuntalupatunnus\" type of link permit data"
          (fact "viitelupatieto-MuuTunnus-Tunnus" (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus :tunnus) => falsey)
          (fact "viitelupatieto-MuuTunnus-Sovellus" (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus :sovellus) => falsey)
          (fact "viitelupatieto-kuntalupatunnus" (:kuntalupatunnus viitelupatieto-LupaTunnus) => (:id link-permit-data-kuntalupatunnus))
          (fact "viitelupatieto-viittaus" (:viittaus viitelupatieto-LupaTunnus) => "edellinen rakennusvalvonta-asia"))

        (facts "\"lupapistetunnus\" type of link permit data"
          (fact "viitelupatieto-2-MuuTunnus-Tunnus" (-> viitelupatieto-LupaTunnus_2 :muuTunnustieto :MuuTunnus :tunnus) => (:id link-permit-data-lupapistetunnus))
          (fact "viitelupatieto-2-MuuTunnus-Sovellus" (-> viitelupatieto-LupaTunnus_2 :muuTunnustieto :MuuTunnus :sovellus) => "Lupapiste")
          (fact "viitelupatieto-2-kuntalupatunnus" (:kuntalupatunnus viitelupatieto-LupaTunnus_2) => falsey)
          (fact "viitelupatieto-2-viittaus" (:viittaus viitelupatieto-LupaTunnus_2) => "edellinen rakennusvalvonta-asia"))


        (fact "luvanTunnisteTiedot-MuuTunnus-Tunnus" (:tunnus luvanTunnisteTiedot-MuuTunnus) => (:id application))
        (fact "luvanTunnisteTiedot-MuuTunnus-Sovellus" (:sovellus luvanTunnisteTiedot-MuuTunnus) => "Lupapiste")

        (fact "rakennusvalvontasian-kuvaus" rakennusvalvontasian-kuvaus => "Uuden rakennuksen rakentaminen tontille.")
        (fact "kayttotapaus" kayttotapaus => "Uuden ty\u00f6njohtajan nime\u00e4minen")))))

(check-common-tyonjohtaja-canonical "Tyonjohtaja V1" application-tyonjohtajan-nimeaminen)
(check-common-tyonjohtaja-canonical "Tyonjohtaja V2" application-tyonjohtajan-nimeaminen-v2)


(fl/facts* "Canonical model for suunnittelijan nimeaminen is correct"
  (let [canonical (application-to-canonical application-suunnittelijan-nimeaminen "fi") => truthy
        rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy

        viitelupatieto (first (:viitelupatieto rakennusvalvontaasia)) => truthy
        viitelupatieto-LupaTunnus (:LupaTunnus viitelupatieto) => truthy
        viitelupatieto-MuuTunnus (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus) => truthy

        osapuolet-vec (-> rakennusvalvontaasia :osapuolettieto :Osapuolet :osapuolitieto) => truthy

        ;; henkilotyyppinen maksaja
        rooliKoodi-laskun-maksaja "Rakennusvalvonta-asian laskun maksaja"
        maksaja-filter-fn #(= (-> % :Osapuoli :kuntaRooliKoodi) rooliKoodi-laskun-maksaja)
        maksaja-Osapuoli (:Osapuoli (first (filter maksaja-filter-fn osapuolet-vec)))
        maksaja-Osapuoli-henkilo (:henkilo maksaja-Osapuoli)
        maksaja-Osapuoli-yritys (:yritys maksaja-Osapuoli)

        luvanTunnisteTiedot-MuuTunnus (-> rakennusvalvontaasia
                                        :luvanTunnisteTiedot
                                        :LupaTunnus
                                        :muuTunnustieto
                                        :MuuTunnus) => truthy

        kayttotapaus (:kayttotapaus rakennusvalvontaasia) => truthy
        Asiantiedot (-> rakennusvalvontaasia :asianTiedot :Asiantiedot) => truthy
        vahainen-poikkeaminen (:vahainenPoikkeaminen Asiantiedot) => falsey
        rakennusvalvontasian-kuvaus (:rakennusvalvontaasianKuvaus Asiantiedot) => truthy]

    (facts "Maksaja is correct"
      (fact "kuntaRooliKoodi" (:kuntaRooliKoodi maksaja-Osapuoli) => "Rakennusvalvonta-asian laskun maksaja")
      (fact "VRKrooliKoodi" (:VRKrooliKoodi maksaja-Osapuoli) => "maksaja")
      (fact "turvakieltoKytkin" (:turvakieltoKytkin maksaja-Osapuoli) => true)
      (validate-person maksaja-Osapuoli-henkilo)
      (fact "yritys is nil" maksaja-Osapuoli-yritys => nil))

    (facts "\"lupapistetunnus\" type of link permit data"
      (fact "viitelupatieto-MuuTunnus-Tunnus" (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus :tunnus) => (:id link-permit-data-lupapistetunnus))
      (fact "viitelupatieto-MuuTunnus-Sovellus" (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus :sovellus) => "Lupapiste")
      (fact "viitelupatieto-kuntalupatunnus" (:kuntalupatunnus viitelupatieto-LupaTunnus) => falsey)
      (fact "viitelupatieto-viittaus" (:viittaus viitelupatieto-LupaTunnus) => "edellinen rakennusvalvonta-asia"))


    (fact "luvanTunnisteTiedot-MuuTunnus-Tunnus" (:tunnus luvanTunnisteTiedot-MuuTunnus) => "LP-753-2013-00003")
    (fact "luvanTunnisteTiedot-MuuTunnus-Sovellus" (:sovellus luvanTunnisteTiedot-MuuTunnus) => "Lupapiste")

    (fact "rakennusvalvontasian-kuvaus" rakennusvalvontasian-kuvaus => "Uuden rakennuksen rakentaminen tontille.")
    (fact "kayttotapaus" kayttotapaus => "Uuden suunnittelijan nime\u00e4minen")))

(def- authority-user-jussi {:id "777777777777777777000017"
                                     :email "jussi.viranomainen@tampere.fi"
                                     :enabled true
                                     :role "authority"
                                     :username "jussi"
                                     :organizations ["837-YA"]
                                     :firstName "Jussi"
                                     :lastName "Viranomainen"
                                     :street "Katuosoite 1 a 1"
                                     :phone "1231234567"
                                     :zip "33456"
                                     :city "Tampere"})

(def application-rakennuslupa-verdict-given
  (assoc application-rakennuslupa
    :state "verdictGiven"
    :verdicts [{:timestamp (:modified application-rakennuslupa)
                :kuntalupatunnus "2013-01"}]))

(fl/facts* "Canonical model for aloitusilmoitus is correct"
  (let [application application-rakennuslupa-verdict-given
        canonical (katselmus-canonical
                    application
                    "sv"
                    "123"
                    "Aloitusilmoitus 1 "
                    1354532324658
                    [{:rakennus {:rakennusnro "002" :jarjestysnumero 1 :kiinttun "21111111111111" :valtakunnallinenNumero "1234567892"}}
                     {:rakennus {:rakennusnro "003" :jarjestysnumero 3 :kiinttun "21111111111111" :valtakunnallinenNumero "1234567892"}}]
                    authority-user-jussi
                    "Aloitusilmoitus"
                    :katselmus
                    ;osittainen pitaja lupaehtona huomautukset lasnaolijat poikkeamat tiedoksianto
                    nil nil nil nil nil nil nil)
        Rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Rakennusvalvonta) => truthy
        kuntakoodi (:kuntakoodi toimituksenTiedot) => truthy
        rakennusvalvontaAsiatieto (:rakennusvalvontaAsiatieto Rakennusvalvonta) => truthy
        RakennusvalvontaAsia (:RakennusvalvontaAsia rakennusvalvontaAsiatieto) => truthy
        kasittelynTilatieto (:kasittelynTilatieto RakennusvalvontaAsia)
        Tilamuutos (-> kasittelynTilatieto last :Tilamuutos) => truthy
        tila (:tila Tilamuutos) => "p\u00e4\u00e4t\u00f6s toimitettu"

        luvanTunnisteTiedot (:luvanTunnisteTiedot RakennusvalvontaAsia) => truthy
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        mt (:MuuTunnus muuTunnustieto) => truthy

        tunnus (:tunnus mt) => "LP-753-2013-00001"
        sovellus (:sovellus mt) => "Lupapiste"

        osapuolettieto (:osapuolettieto RakennusvalvontaAsia) => truthy
        Osapuolet (:Osapuolet osapuolettieto) => truthy
        osapuolitieto (:osapuolitieto Osapuolet) => truthy
        Osapuoli (:Osapuoli osapuolitieto) => truthy
        kuntaRooliKoodi (:kuntaRooliKoodi Osapuoli) => "Ilmoituksen tekij\u00e4"
        henkilo (:henkilo Osapuoli) => truthy
        nimi (:nimi henkilo) => truthy
        etunimi (:etunimi nimi) => "Jussi"
        sukunimi (:sukunimi nimi) => "Viranomainen"
        osoite (:osoite henkilo) => truthy
        osoitenimi (-> osoite :osoitenimi :teksti) => "Katuosoite 1 a 1"
        puhelin (:puhelin henkilo) => "1231234567"

        katselmustieto (:katselmustieto RakennusvalvontaAsia) => truthy
        Katselmus (:Katselmus katselmustieto) => truthy
        rakennustunnus (:rakennustunnus Katselmus) => map?]

      (:jarjestysnumero rakennustunnus) => 1
      (:valtakunnallinenNumero rakennustunnus) => "1234567892"
      (:rakennusnro rakennustunnus) => "002"
      (:kiinttun rakennustunnus) => "21111111111111"

    (:kayttotapaus RakennusvalvontaAsia) => "Aloitusilmoitus"
    (:katselmuksenLaji Katselmus)  => "ei tiedossa"
    (:pitoPvm Katselmus) => "2012-12-03"

    (fact "tarkastuksenTaiKatselmuksenNimi is trimmed (LPK-2082)"
      (:tarkastuksenTaiKatselmuksenNimi Katselmus) => "Aloitusilmoitus 1")

    (fact "KRYSP 2.1.3 data is present"
      (get-in katselmustieto [:Katselmus :muuTunnustieto :MuuTunnus]) => {:tunnus "123" :sovellus "Lupapiste"}
      (let [rakennukset (map :KatselmuksenRakennus (get-in katselmustieto [:Katselmus :katselmuksenRakennustieto]))]
        (fact "has 2 buildings" (count rakennukset) => 2)
        (fact "jarjestysnumero" (:jarjestysnumero (last rakennukset)) => 3)
        (fact "valtakunnallinenNumero" (:valtakunnallinenNumero (last rakennukset)) => "1234567892")
        (fact "rakennusnro" (:rakennusnro (last rakennukset)) => "003")
        (fact "kiinttun" (:kiinttun (last rakennukset)) => "21111111111111")))))

(fl/facts* "Canonical model for erityissuunnitelma is correct"
  (let [application application-rakennuslupa-verdict-given
        canonical (unsent-attachments-to-canonical application "sv")

        Rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Rakennusvalvonta) => truthy
        kuntakoodi (:kuntakoodi toimituksenTiedot) => truthy
        rakennusvalvontaAsiatieto (:rakennusvalvontaAsiatieto Rakennusvalvonta) => truthy
        RakennusvalvontaAsia (:RakennusvalvontaAsia rakennusvalvontaAsiatieto) => truthy
        kasittelynTilatieto (:kasittelynTilatieto RakennusvalvontaAsia)
        Tilamuutos (-> kasittelynTilatieto last :Tilamuutos) => truthy

        luvanTunnisteTiedot (:luvanTunnisteTiedot RakennusvalvontaAsia) => truthy
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        mt (:MuuTunnus muuTunnustieto) => truthy]

    (fact "tila" (:tila Tilamuutos) => "p\u00e4\u00e4t\u00f6s toimitettu")
    (fact "tunnus" (:tunnus mt) => "LP-753-2013-00001")
    (fact "sovellus" (:sovellus mt) => "Lupapiste")
    (fact "kayttotapaus" (:kayttotapaus RakennusvalvontaAsia) => "Liitetiedoston lis\u00e4ys")))



;Jatkolupa

(def jatkolupa-application
  {:schema-version 1,
   :auth [{:lastName "Panaani",
           :firstName "Pena",
           :username "pena",
           :type "owner",
           :role "owner",
           :id "777777777777777777000020"}],
   :submitted 1384167310181,
   :state "submitted",
   :permitSubtype nil,
   :location [411063.82824707 6685145.8129883],
   :attachments [],
   :organization "753-R",
   :title "It\u00e4inen Hangelbyntie 163",
   :primaryOperation {:id "5280b764420622588b2f04fc",
                      :name "jatkoaika",
                      :created 1384167268234}
   :secondaryOperations [],
   :infoRequest false,
   :openInfoRequest false,
   :opened 1384167310181,
   :created 1384167268234,
   :propertyId "75340800010051",
   :documents [{:created 1384167268234,
                :data {:kuvaus {:modified 1384167309006,
                                :value "Pari vuotta jatko-aikaa, ett\u00e4 saadaan rakennettua loppuun."}},
                :id "5280b764420622588b2f04fd",
                :schema-info {:order 1,
                              :version 1,
                              :name "hankkeen-kuvaus-minimum",
                              :approvable true,
                              :op {:id "5280b764420622588b2f04fc",
                                   :name "jatkoaika",
                                   :created 1384167268234},
                              :removable true}}
               hakija-henkilo],
   :_software_version "1.0.5",
   :modified 1384167309006,
   :comments [],
   :address "It\u00e4inen Hangelbyntie 163",
   :permitType "R",
   :id "LP-753-2013-00005",
   :municipality "753"
   :authority {:id "777777777777777777000023"
               :username "sonja"
               :firstName "Sonja"
               :lastName "Sibbo"
               :role "authority"}
     :linkPermitData [link-permit-data-lupapistetunnus]})

(ctc/validate-all-documents jatkolupa-application)


(fl/facts* "Canonical model for katselmus is correct"
           (let [canonical (katselmus-canonical
                             application-rakennuslupa-verdict-given
                             "fi"
                             "123"
                             "Pohjakatselmus 1"
                             1354532324658
                             [{:rakennus {:rakennusnro "002" :jarjestysnumero 1 :kiinttun "01234567891234"}
                               :tila     {:tila "pidetty" :kayttoonottava false}}]
                             authority-user-jussi
                             "pohjakatselmus"
                             :katselmus
                             "pidetty"
                             "Sonja Silja"
                             true
                             {:kuvaus "Saunan ovi pit\u00e4\u00e4 vaihtaa 900mm leve\u00e4ksi.\nPiha-alue siivottava v\u00e4litt\u00f6m\u00e4sti."
                              :maaraAika "05.5.2014"
                              :toteaja "Jussi"
                              :toteamisHetki "4.04.2014"}
                             "Tiivi Taavi, Hipsu ja Lala"
                             "Ei poikkeamisia"
                             false)

                 Rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
                 toimituksenTiedot (:toimituksenTiedot Rakennusvalvonta) => truthy
                 kuntakoodi (:kuntakoodi toimituksenTiedot) => truthy
                 rakennusvalvontaAsiatieto (:rakennusvalvontaAsiatieto Rakennusvalvonta) => truthy
                 RakennusvalvontaAsia (:RakennusvalvontaAsia rakennusvalvontaAsiatieto) => truthy
                 kasittelynTilatieto (:kasittelynTilatieto RakennusvalvontaAsia)
                 Tilamuutos (-> kasittelynTilatieto last :Tilamuutos) => map?
                 tila (:tila Tilamuutos) => "p\u00e4\u00e4t\u00f6s toimitettu"

                 luvanTunnisteTiedot (:luvanTunnisteTiedot RakennusvalvontaAsia) => truthy
                 LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
                 kuntalupatunnus (:kuntalupatunnus LupaTunnus) => "2013-01"
                 muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
                 mt (:MuuTunnus muuTunnustieto) => truthy

                 tunnus (:tunnus mt) => "LP-753-2013-00001"
                 sovellus (:sovellus mt) => "Lupapiste"

                 osapuolettieto (:osapuolettieto RakennusvalvontaAsia) => truthy
                 Osapuolet (:Osapuolet osapuolettieto) => truthy
                 osapuolitieto (:osapuolitieto Osapuolet) => truthy
                 Osapuoli (:Osapuoli osapuolitieto) => truthy
                 kuntaRooliKoodi (:kuntaRooliKoodi Osapuoli) => "Ilmoituksen tekij\u00e4"
                 henkilo (:henkilo Osapuoli) => truthy
                 nimi (:nimi henkilo) => truthy
                 etunimi (:etunimi nimi) => "Jussi"
                 sukunimi (:sukunimi nimi) => "Viranomainen"
                 osoite (:osoite henkilo) => truthy
                 osoitenimi (-> osoite :osoitenimi :teksti) => "Katuosoite 1 a 1"
                 puhelin (:puhelin henkilo) => "1231234567"

                 katselmustieto (:katselmustieto RakennusvalvontaAsia) => truthy
                 Katselmus (:Katselmus katselmustieto) => truthy
                 rakennustunnus (:rakennustunnus Katselmus) => truthy
                 jarjestysnumero (:jarjestysnumero rakennustunnus) => 1
                 rakennusnumero (:rakennusnro rakennustunnus) => "002"
                 kiinttun (:kiinttun rakennustunnus) => "01234567891234"
                 pitoPvm (:pitoPvm Katselmus) => "2012-12-03"
                 osittainen (:osittainen Katselmus) => "pidetty"
                 pitaja (:pitaja Katselmus) => "Sonja Silja"
                 huomautus (-> Katselmus :huomautukset :huomautus)
                 katselmuksenLaji (:katselmuksenLaji Katselmus) => "pohjakatselmus"
                 lasnaolijat (:lasnaolijat Katselmus ) => "Tiivi Taavi, Hipsu ja Lala"
                 poikkeamat (:poikkeamat Katselmus) => "Ei poikkeamisia"
                 tiedoksianto (:verottajanTvLlKytkin Katselmus) => false
                 tarkastuksenTaiKatselmuksenNimi (:tarkastuksenTaiKatselmuksenNimi Katselmus) => "Pohjakatselmus 1"
                 kayttotapaus (:kayttotapaus RakennusvalvontaAsia) => "Uusi katselmus"

                 rakennustieto (first (:katselmuksenRakennustieto Katselmus)) => truthy
                 rakennusOsittainen (get-in rakennustieto [:KatselmuksenRakennus :katselmusOsittainen]) => "pidetty"
                 rakennusKayttoonotto (get-in rakennustieto [:KatselmuksenRakennus :kayttoonottoKytkin]) => false]

             (:kuvaus huomautus) => "Saunan ovi pit\u00e4\u00e4 vaihtaa 900mm leve\u00e4ksi.\nPiha-alue siivottava v\u00e4litt\u00f6m\u00e4sti."
             (:maaraAika huomautus) => "2014-05-05"
             (:toteamisHetki huomautus) => "2014-04-04"
             (:toteaja huomautus) => "Jussi")

           (fact "If huomautus kuvaus is empty, a dash (-) is put as kuvaus value for huomautus"
             (let [katselmus-huomautus (katselmus-canonical
                                         application-rakennuslupa-verdict-given
                                         "fi"
                                         "123"
                                         "Pohjakatselmus 1"
                                         1354532324658
                                         [{:rakennus {:rakennusnro "002" :jarjestysnumero 1 :kiinttun "01234567891234"}
                                           :tila     {:tila "pidetty" :kayttoonottava false}}]
                                         authority-user-jussi
                                         "pohjakatselmus"
                                         :katselmus
                                         "pidetty"
                                         "Sonja Silja"
                                         true
                                         {:kuvaus ""}
                                         "Tiivi Taavi, Hipsu ja Lala"
                                         "Ei poikkeamisia"
                                         false)]
               (get-in katselmus-huomautus [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia
                                            :katselmustieto :Katselmus :huomautukset :huomautus :kuvaus]) => "-")))

(fl/facts* "Katselmus with empty buildings is OK (no buildings in canonical)"
  (let [canonical (katselmus-canonical
                   application-rakennuslupa-verdict-given
                   "fi"
                   "123"
                   "Pohjakatselmus 1"
                   1354532324658
                   []
                   authority-user-jussi
                   "pohjakatselmus"
                   :katselmus
                   "pidetty"
                   "Sonja Silja"
                   true
                   {:kuvaus "Saunan ovi pit\u00e4\u00e4 vaihtaa 900mm leve\u00e4ksi.\nPiha-alue siivottava v\u00e4litt\u00f6m\u00e4sti."
                    :maaraAika "05.5.2014"
                    :toteaja "Jussi"
                    :toteamisHetki "4.04.2014"}
                   "Tiivi Taavi, Hipsu ja Lala"
                   "Ei poikkeamisia"
                   false) => truthy
        Rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Rakennusvalvonta) => truthy
        kuntakoodi (:kuntakoodi toimituksenTiedot) => truthy
        rakennusvalvontaAsiatieto (:rakennusvalvontaAsiatieto Rakennusvalvonta) => truthy
        RakennusvalvontaAsia (:RakennusvalvontaAsia rakennusvalvontaAsiatieto) => truthy
        katselmustieto (:katselmustieto RakennusvalvontaAsia) => truthy
        Katselmus (:Katselmus katselmustieto) => truthy]
    (:rakennustunnus Katselmus) => nil
    (:katselmuksenRakennustieto Katselmus) => nil))


;Aloitusoikeus (Takuu) (tyonaloitus ennen kuin valitusaika loppunut luvan myontamisesta)

(def aloitusoikeus-hakemus
  (merge
    domain/application-skeleton
    {:linkPermitData [link-permit-data-kuntalupatunnus],
     :schema-version 1,
     :auth [{:lastName "Panaani",
             :firstName "Pena",
             :username "pena",
             :type "owner",
             :role "owner",
             :id "777777777777777777000020"}],
     :submitted 1388665814105,
     :state "submitted",
     :location [406390.19848633 6681812.5],
     :organization "753-R",
     :title "Vainuddintie 92",
     :primaryOperation {:id "52c5461042065cf9f379de8b",
                        :name "aloitusoikeus",
                        :created 1388660240013}
     :secondaryOperations [],
     :infoRequest false,
     :openInfoRequest false,
     :opened 1388665814105,
     :created 1388660240013,
     :propertyId "75341900080007",
     :documents [{:id "537df18fbc454ac7ac9036c7",
                  :created 1400762767119,
                  :schema-info {:approvable true,
                                :subtype "hakija",
                                :name "hakija-r",
                                :removable true,
                                :after-update "applicant-index-update",
                                :repeating true,
                                :version 1,
                                :type "party",
                                :order 3}
                  :data {:_selected {:value "henkilo"},
                         :henkilo {:henkilotiedot {:etunimi {:modified 1400762778665, :value "Pena"},
                                                   :hetu {:modified 1400762778665, :value "010203-040A"},
                                                   :sukunimi {:modified 1400762778665, :value "Panaani"}},
                                   :osoite {:katu {:modified 1400762778665, :value "Paapankuja 12"},
                                            :postinumero {:modified 1400762778665, :value "10203"},
                                            :postitoimipaikannimi
                                            {:modified 1400762778665, :value "Piippola"}},
                                   :userId {:modified 1400762778787, :value "777777777777777777000020"},
                                   :yhteystiedot {:email {:modified 1400762778665, :value "pena@example.com"},
                                                  :puhelin {:modified 1400762778665, :value "0102030405"}}}}}
                 {:id "537df18fbc454ac7ac9036c6",
                  :created 1400762767119,
                  :schema-info {:version 1,
                                :name "aloitusoikeus",
                                :approvable true,
                                :op {:id "537df18fbc454ac7ac9036c5",
                                     :name "aloitusoikeus",
                                     :created 1400762767119},
                                :removable false}
                  :data {:kuvaus {:modified 1400762776200, :value "Tarttis aloitta asp rakentaminen."}}}
                 {:id "537df18fbc454ac7ac9036c8",
                  :created 1400762767119,
                  :schema-info {:approvable true,
                                :name "maksaja",
                                :removable true,
                                :repeating true,
                                :version 1,
                                :type "party",
                                :order 6}
                  :data {:henkilo {:henkilotiedot {:etunimi {:modified 1400762782277, :value "Pena"},
                                                   :hetu {:modified 1400762782277, :value "010203-040A"},
                                                   :sukunimi {:modified 1400762782277, :value "Panaani"}},
                                   :osoite {:katu {:modified 1400762782277, :value "Paapankuja 12"},
                                            :postinumero {:modified 1400762782277, :value "10203"},
                                            :postitoimipaikannimi
                                            {:modified 1400762782277, :value "Piippola"}},
                                   :userId {:modified 1400762782327, :value "777777777777777777000020"},
                                   :yhteystiedot {:email {:modified 1400762782277, :value "pena@example.com"},
                                                  :puhelin {:modified 1400762782277, :value "0102030405"}}},
                         :laskuviite {:modified 1400762796099, :value "1234567890"}}}]
     :_statements-seen-by {:777777777777777777000020 1388664440961},
     :modified 1388667087403,
     :address "Vainuddintie 92",
     :permitType "R",
     :id "LP-753-2014-00001",
     :municipality "753"}))

(ctc/validate-all-documents aloitusoikeus-hakemus)

(fl/facts* "Canonical model for aloitusoikeus is correct"
  (let [canonical (application-to-canonical aloitusoikeus-hakemus "sv") => truthy
        rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy
        lupa-tunnus (get-in rakennusvalvontaasia [:luvanTunnisteTiedot :LupaTunnus]) => map?
        toimituksenTiedot (:toimituksenTiedot rakennusvalvonta) => truthy
        asianTiedot (:asianTiedot rakennusvalvontaasia) => truthy
        Asiantiedot (:Asiantiedot asianTiedot)
        lisatiedot (:lisatiedot rakennusvalvontaasia) => truthy
        Lisatiedot (:Lisatiedot lisatiedot) => truthy
        vakuus (:vakuus Lisatiedot) => nil?]

        (:aineistonnimi toimituksenTiedot ) => "Vainuddintie 92"
        (:rakennusvalvontaasianKuvaus Asiantiedot) => "Tarttis aloitta asp rakentaminen."

        (get-in lupa-tunnus [:muuTunnustieto :MuuTunnus]) => {:tunnus (:id aloitusoikeus-hakemus), :sovellus "Lupapiste"}

        (fact "SaapumisPvm = submitted date"
          (:saapumisPvm lupa-tunnus) => "2014-01-02")))
