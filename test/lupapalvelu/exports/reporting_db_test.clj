(ns lupapalvelu.exports.reporting-db-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.exports.reporting-db :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rakennuslupa-canonical-util :refer [application-rakennuslupa]]
            [lupapalvelu.pate.verdict-canonical-test :as vct]))

(def review {:data {:katselmus {:pitoPvm {:value 1354532324658}
                                :pitaja {:value "Reijo Revyy"}
                                :lasnaolijat {:value nil}
                                :poikkeamat {:value nil}
                                :tila {:value nil}
                                :tiedoksianto {:value nil}
                                :huomautukset []}
                    :katselmuksenLaji {:value "Aloitusilmoitus"}
                    :vaadittuLupaehtona {:value nil}
                    :rakennus {:0 {:tila {:tila {:value "osittainen"}}
                                   :rakennus {:rakennusnro {:value "002"}
                                              :jarjestysnumero {:value 1}
                                              :kiinttun {:value "21111111111111"}}}
                               :1 {:tila {:tila {:value "lopullinen"}}
                                   :rakennus {:rakennusnro {:value "003"}
                                              :jarjestysnumero {:value 3}
                                              :kiinttun {:value "21111111111111"}
                                              :valtakunnallinenNumero {:value "1234567892"}}}
                               :2 {:tila {:tila {:value ""}}
                                   :rakennus {:rakennusnro {:value "004"}
                                              :jarjestysnumero {:value 3}
                                              :kiinttun {:value "21111111111111"}
                                              :valtakunnallinenNumero {:value "1234567892"}}}}
                    :muuTunnus "review1"
                    :muuTunnusSovellus "RakApp"}
             :id "123"
             :taskname "Aloitusilmoitus 1"})

(def verdicts
  [{:kuntalupatunnus "2013-01"
    :paatokset
    [{:paivamaarat {:anto 1378339200000}
      :poytakirjat
      [{:paatoskoodi "myönnetty"
        :paatospvm 1377993600000
        :pykala 1
        :paatoksentekija "elmo viranomainen"
        :paatos "Päätös on nyt tämä."
        :status "1"}]
      :id "5c0a6a7127093589384e7cc5"}]
    :id "5c0a6a7127093589384e7cc2"
    :timestamp 1544186481204}
   {:kuntalupatunnus "13-0185-R"
    :paatokset
    [{:lupamaaraykset
      {:autopaikkojaRakennettu 0
       :autopaikkojaKiinteistolla 7
       :autopaikkojaEnintaan 10
       :autopaikkojaUlkopuolella 3
       :autopaikkojaVahintaan 1
       :vaaditutTyonjohtajat
       "Vastaava työnjohtaja Vastaava IV-työnjohtaja Työnjohtaja"
       :autopaikkojaRakennettava 2
       :vaaditutKatselmukset
       [{:katselmuksenLaji "aloituskokous"
         :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"
         :muuTunnus ""
         :muuTunnusSovellus ""}
        {:katselmuksenLaji "muu tarkastus"
         :tarkastuksenTaiKatselmuksenNimi "Käyttöönottotarkastus"
         :muuTunnus ""
         :muuTunnusSovellus ""}]
       :maaraykset
       [{:maaraysaika 1377648000000
         :sisalto "Radontekninen suunnitelma"
         :toteutusHetki 1377734400000}
        {:maaraysaika 1377820800000
         :sisalto "Ilmanvaihtosuunnitelma"
         :toteutusHetki 1377907200000}]
       :kokonaisala "110"
       :kerrosala "100"
       :rakennusoikeudellinenKerrosala "100.000"}
      :paivamaarat
      {:aloitettava 1377993600000
       :lainvoimainen 1378080000000
       :voimassaHetki 1378166400000
       :raukeamis 1378252800000
       :anto 1378339200000
       :viimeinenValitus 4123872000000
       :julkipano 1378512000000}
      :poytakirjat
      [{:paatoskoodi "myönnetty"
        :paatospvm 1375315200000
        :pykala 1
        :paatoksentekija "viranomainen"
        :paatos "Päätös 1"
        :status "1"
        :urlHash "236d9b2cfff88098d4f8ad532820c9fb93393237"}
       {:paatoskoodi "ehdollinen"
        :paatospvm 1375401600000
        :pykala 2
        :paatoksentekija "Mölli Keinonen"
        :paatos
        "Päätösteksti avaimen :paatos alla"
        :status "6"
        :urlHash "b55ae9c30533428bd9965a84106fb163611c1a7d"}]
      :id "5c0a6a7127093589384e7cc6"}
     {:lupamaaraykset
      {:vaaditutKatselmukset
       [{:katselmuksenLaji "loppukatselmus"
         :muuTunnus ""
         :muuTunnusSovellus ""}]
       :maaraykset [{:sisalto "Valaistussuunnitelma"}]}
      :paivamaarat {:anto 1378339200000}
      :poytakirjat
      [{:paatoskoodi "myönnetty"
        :paatospvm 1377993600000
        :paatoksentekija "johtava viranomainen"
        :paatos "Päätös 2"
        :status "1"}]
      :id "5c0a6a7127093589384e7ccb"}]
    :id "5c0a6a7127093589384e7cc3"
    :timestamp 1544186481204}])

(def pate-verdicts
  [vct/verdict])

(def application-rakennuslupa-with-verdicts
  (assoc application-rakennuslupa
         :verdicts verdicts
         :pate-verdicts pate-verdicts))

(facts "->reporting-result"
  (against-background
    (org/pate-scope? irrelevant) => false)
  (->reporting-result (assoc application-rakennuslupa
                             :tasks [review])
                      "fi")
  => (contains
      {:id (:id application-rakennuslupa)
       :address (:address application-rakennuslupa)
       :propertyId (:propertyId application-rakennuslupa)
       :organization (:organization application-rakennuslupa)
       :municipality (:municipality application-rakennuslupa)
       :location-etrs-tm35fin (:location application-rakennuslupa)
       :location-wgs84 (:location-wgs84 application-rakennuslupa)
       :operations [{:id "muu-laajentaminen-id"
                     :primary true
                     :kuvaus nil
                     :nimi "Muu rakennuksen muutostyö"
                     :rakennelma nil
                     :rakennus {:omistajat [{:VRKrooliKoodi "rakennuksen omistaja"
                                             :henkilo {:henkilotunnus "210281-9988"
                                                       :nimi {:etunimi "Pena"
                                                              :sukunimi "Penttilä"}
                                                       :osoite {:osoitenimi {:teksti "katu"}
                                                                :postinumero "33800"
                                                                :postitoimipaikannimi "Tuonela"
                                                                :ulkomainenLahiosoite "katu"
                                                                :ulkomainenPostitoimipaikka "Tuonela"
                                                                :valtioKansainvalinen "CHN"
                                                                :valtioSuomeksi "Kiina"}
                                                       :puhelin "+358401234567"
                                                       :sahkopostiosoite "pena@example.com"
                                                       :vainsahkoinenAsiointiKytkin false}
                                             :kuntaRooliKoodi "Rakennuksen omistaja"
                                             :omistajalaji {:omistajalaji "muu yksityinen henkilö tai perikunta"}
                                             :suoramarkkinointikieltoKytkin true
                                             :turvakieltoKytkin true}
                                            {:VRKrooliKoodi "rakennuksen omistaja"
                                             :henkilo {:nimi {:etunimi "Pena"
                                                              :sukunimi "Penttilä"}
                                                       :puhelin "03-389 1380"
                                                       :sahkopostiosoite "yritys@example.com"}
                                             :kuntaRooliKoodi "Rakennuksen omistaja"
                                             :omistajalaji {:omistajalaji "yksityinen yritys (osake-, avoin- tai kommandiittiyhtiö, osuuskunta)"}
                                             :suoramarkkinointikieltoKytkin true
                                             :turvakieltoKytkin true
                                             :yritys {:liikeJaYhteisotunnus "1060155-5"
                                                      :nimi "Solita Oy"
                                                      :postiosoite {:osoitenimi {:teksti "katu"}
                                                                    :postinumero "33800"
                                                                    :postitoimipaikannimi "Tuonela"
                                                                    :ulkomainenLahiosoite "katu"
                                                                    :ulkomainenPostitoimipaikka "Tuonela"
                                                                    :valtioKansainvalinen "CHN"
                                                                    :valtioSuomeksi "Kiina"}
                                                      :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                                                       :postinumero "33800"
                                                                                       :postitoimipaikannimi "Tuonela"
                                                                                       :ulkomainenLahiosoite "katu"
                                                                                       :ulkomainenPostitoimipaikka "Tuonela"
                                                                                       :valtioKansainvalinen "CHN"
                                                                                       :valtioSuomeksi "Kiina"}}
                                                      :puhelin "03-389 1380"
                                                      :sahkopostiosoite "yritys@example.com"
                                                      :vainsahkoinenAsiointiKytkin false}}]
                                :rakentajatyyppi "muu"
                                :tiedot {:asuinhuoneistot {:huoneisto [{:huoneistoala 56
                                                                        :huoneistonTyyppi "asuinhuoneisto"
                                                                        :huoneistotunnus {:huoneistonumero "001"
                                                                                          :jakokirjain "a"
                                                                                          :porras "A"}
                                                                        :huoneluku 66
                                                                        :keittionTyyppi "keittio"
                                                                        :muutostapa "lisäys"
                                                                        :varusteet {:WCKytkin true
                                                                                    :ammeTaiSuihkuKytkin false
                                                                                    :lamminvesiKytkin false
                                                                                    :parvekeTaiTerassiKytkin true
                                                                                    :saunaKytkin false}}
                                                                       {:huoneistoala 2
                                                                        :huoneistonTyyppi "toimitila"
                                                                        :huoneistotunnus {:huoneistonumero "002"
                                                                                          :jakokirjain "a"
                                                                                          :porras "A"}
                                                                        :huoneluku 12
                                                                        :keittionTyyppi "keittokomero"
                                                                        :muutostapa "muutos"
                                                                        :varusteet {:WCKytkin false
                                                                                    :ammeTaiSuihkuKytkin true
                                                                                    :lamminvesiKytkin true
                                                                                    :parvekeTaiTerassiKytkin false
                                                                                    :saunaKytkin true}}]}
                                         :energialuokka "C"
                                         :energiatehokkuusluku 124
                                         :energiatehokkuusluvunYksikko "kWh/m2"
                                         :julkisivu {:julkisivumateriaali "puu"}
                                         :kantavaRakennusaine {:rakennusaine "puu"}
                                         :kayttotarkoitus "012 kahden asunnon talot"
                                         :kellarinpinta-ala 100
                                         :kerrosala 180
                                         :kerrosluku 2
                                         :kokonaisala 1000
                                         :lammitystapa "vesikeskus"
                                         :lammonlahde {:muu "polttopuillahan tuo"}
                                         :liitettyJatevesijarjestelmaanKytkin true
                                         :paloluokka "P1"
                                         :rakennusoikeudellinenKerrosala 160
                                         :rakennustunnus {:jarjestysnumero 1
                                                          :kiinttun "21111111111111"
                                                          :muuTunnustieto [{:MuuTunnus {:sovellus "toimenpideId"
                                                                                        :tunnus "muu-laajentaminen-id"}}
                                                                           {:MuuTunnus {:sovellus "Lupapiste"
                                                                                        :tunnus "muu-laajentaminen-id"}}]
                                                          :rakennusnro "001"
                                                          :valtakunnallinenNumero "1234567892"}
                                         :rakentamistapa "elementti"
                                         :tilavuus 1500
                                         :varusteet {:aurinkopaneeliKytkin true
                                                     :hissiKytkin true
                                                     :kaasuKytkin true
                                                     :koneellinenilmastointiKytkin true
                                                     :lamminvesiKytkin true
                                                     :sahkoKytkin true
                                                     :saunoja 1
                                                     :vaestonsuoja 1
                                                     :vesijohtoKytkin true
                                                     :viemariKytkin true}
                                         :verkostoliittymat {:kaapeliKytkin true
                                                             :maakaasuKytkin true
                                                             :sahkoKytkin true
                                                             :vesijohtoKytkin true
                                                             :viemariKytkin true}}}}
                    {:id "kerrostalo-rivitalo-id"
                     :primary false
                     :kuvaus "kerrostalo-rivitalo-kuvaus"
                     :nimi "Asuinkerrostalon tai rivitalon rakentaminen"
                     :rakennelma nil
                     :rakennus {:omistajat [{:VRKrooliKoodi "rakennuksen omistaja"
                                             :henkilo {:henkilotunnus "210281-9988"
                                                       :nimi {:etunimi "Pena"
                                                              :sukunimi "Penttilä"}
                                                       :osoite {:osoitenimi {:teksti "katu"}
                                                                :postinumero "33800"
                                                                :postitoimipaikannimi "Tuonela"
                                                                :ulkomainenLahiosoite "katu"
                                                                :ulkomainenPostitoimipaikka "Tuonela"
                                                                :valtioKansainvalinen "CHN"
                                                                :valtioSuomeksi "Kiina"}
                                                       :puhelin "+358401234567"
                                                       :sahkopostiosoite "pena@example.com"
                                                       :vainsahkoinenAsiointiKytkin false}
                                             :kuntaRooliKoodi "Rakennuksen omistaja"
                                             :omistajalaji {:omistajalaji "muu yksityinen henkilö tai perikunta"}
                                             :suoramarkkinointikieltoKytkin true
                                             :turvakieltoKytkin true}
                                            {:VRKrooliKoodi "rakennuksen omistaja"
                                             :henkilo {:nimi {:etunimi "Pena"
                                                              :sukunimi "Penttilä"}
                                                       :puhelin "03-389 1380"
                                                       :sahkopostiosoite "yritys@example.com"}
                                             :kuntaRooliKoodi "Rakennuksen omistaja"
                                             :omistajalaji {:omistajalaji "yksityinen yritys (osake-, avoin- tai kommandiittiyhtiö, osuuskunta)"}
                                             :suoramarkkinointikieltoKytkin true
                                             :turvakieltoKytkin true
                                             :yritys {:liikeJaYhteisotunnus "1060155-5"
                                                      :nimi "Solita Oy"
                                                      :postiosoite {:osoitenimi {:teksti "katu"}
                                                                    :postinumero "33800"
                                                                    :postitoimipaikannimi "Tuonela"
                                                                    :ulkomainenLahiosoite "katu"
                                                                    :ulkomainenPostitoimipaikka "Tuonela"
                                                                    :valtioKansainvalinen "CHN"
                                                                    :valtioSuomeksi "Kiina"}
                                                      :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                                                       :postinumero "33800"
                                                                                       :postitoimipaikannimi "Tuonela"
                                                                                       :ulkomainenLahiosoite "katu"
                                                                                       :ulkomainenPostitoimipaikka "Tuonela"
                                                                                       :valtioKansainvalinen "CHN"
                                                                                       :valtioSuomeksi "Kiina"}}
                                                      :puhelin "03-389 1380"
                                                      :sahkopostiosoite "yritys@example.com"
                                                      :vainsahkoinenAsiointiKytkin false}}]
                                :rakentajatyyppi "muu"
                                :tiedot {:asuinhuoneistot {:huoneisto [{:huoneistoala 56
                                                                        :huoneistonTyyppi "asuinhuoneisto"
                                                                        :huoneistotunnus {:huoneistonumero "001"
                                                                                          :jakokirjain "a"
                                                                                          :porras "A"}
                                                                        :huoneluku 66
                                                                        :keittionTyyppi "keittio"
                                                                        :muutostapa "lisäys"
                                                                        :varusteet {:WCKytkin true
                                                                                    :ammeTaiSuihkuKytkin false
                                                                                    :lamminvesiKytkin false
                                                                                    :parvekeTaiTerassiKytkin true
                                                                                    :saunaKytkin false}}
                                                                       {:huoneistoala 2
                                                                        :huoneistonTyyppi "toimitila"
                                                                        :huoneistotunnus {:huoneistonumero "002"
                                                                                          :jakokirjain "a"
                                                                                          :porras "A"}
                                                                        :huoneluku 12
                                                                        :keittionTyyppi "keittokomero"
                                                                        :muutostapa "lisäys"
                                                                        :varusteet {:WCKytkin false
                                                                                    :ammeTaiSuihkuKytkin true
                                                                                    :lamminvesiKytkin true
                                                                                    :parvekeTaiTerassiKytkin false
                                                                                    :saunaKytkin true}}]}
                                         :energialuokka "C"
                                         :energiatehokkuusluku 124
                                         :energiatehokkuusluvunYksikko "kWh/m2"
                                         :julkisivu {:julkisivumateriaali "puu"}
                                         :kantavaRakennusaine {:rakennusaine "puu"}
                                         :kayttotarkoitus "012 kahden asunnon talot"
                                         :kellarinpinta-ala 100
                                         :kerrosala 180
                                         :kerrosluku 2
                                         :kokonaisala 1000
                                         :lammitystapa "vesikeskus"
                                         :lammonlahde {:muu "polttopuillahan tuo"}
                                         :liitettyJatevesijarjestelmaanKytkin true
                                         :paloluokka "P1"
                                         :rakennusoikeudellinenKerrosala 160
                                         :rakennustunnus {:jarjestysnumero 2
                                                          :kiinttun "21111111111111"
                                                          :muuTunnustieto [{:MuuTunnus {:sovellus "toimenpideId"
                                                                                        :tunnus "kerrostalo-rivitalo-id"}}
                                                                           {:MuuTunnus {:sovellus "Lupapiste"
                                                                                        :tunnus "kerrostalo-rivitalo-id"}}]
                                                          :rakennuksenSelite "A: kerrostalo-rivitalo-kuvaus"}
                                         :rakentamistapa "elementti"
                                         :tilavuus 1500
                                         :varusteet {:aurinkopaneeliKytkin true
                                                     :hissiKytkin true
                                                     :kaasuKytkin true
                                                     :koneellinenilmastointiKytkin true
                                                     :lamminvesiKytkin true
                                                     :sahkoKytkin true
                                                     :saunoja 1
                                                     :vaestonsuoja 1
                                                     :vesijohtoKytkin true
                                                     :viemariKytkin true}
                                         :verkostoliittymat {:kaapeliKytkin true
                                                             :maakaasuKytkin true
                                                             :sahkoKytkin true
                                                             :vesijohtoKytkin true
                                                             :viemariKytkin true}}}}
                    {:id "laajentaminen-id"
                     :primary false
                     :kuvaus nil
                     :nimi "Rakennuksen laajentaminen tai korjaaminen"
                     :rakennelma nil
                     :rakennus {:omistajat [{:VRKrooliKoodi "rakennuksen omistaja"
                                             :henkilo {:henkilotunnus "210281-9988"
                                                       :nimi {:etunimi "Pena"
                                                              :sukunimi "Penttilä"}
                                                       :osoite {:osoitenimi {:teksti "katu"}
                                                                :postinumero "33800"
                                                                :postitoimipaikannimi "Tuonela"
                                                                :ulkomainenLahiosoite "katu"
                                                                :ulkomainenPostitoimipaikka "Tuonela"
                                                                :valtioKansainvalinen "CHN"
                                                                :valtioSuomeksi "Kiina"}
                                                       :puhelin "+358401234567"
                                                       :sahkopostiosoite "pena@example.com"
                                                       :vainsahkoinenAsiointiKytkin false}
                                             :kuntaRooliKoodi "Rakennuksen omistaja"
                                             :omistajalaji {:omistajalaji "muu yksityinen henkilö tai perikunta"}
                                             :suoramarkkinointikieltoKytkin true
                                             :turvakieltoKytkin true}
                                            {:VRKrooliKoodi "rakennuksen omistaja"
                                             :henkilo {:nimi {:etunimi "Pena"
                                                              :sukunimi "Penttilä"}
                                                       :puhelin "03-389 1380"
                                                       :sahkopostiosoite "yritys@example.com"}
                                             :kuntaRooliKoodi "Rakennuksen omistaja"
                                             :omistajalaji {:omistajalaji "yksityinen yritys (osake-, avoin- tai kommandiittiyhtiö, osuuskunta)"}
                                             :suoramarkkinointikieltoKytkin true
                                             :turvakieltoKytkin true
                                             :yritys {:liikeJaYhteisotunnus "1060155-5"
                                                      :nimi "Solita Oy"
                                                      :postiosoite {:osoitenimi {:teksti "katu"}
                                                                    :postinumero "33800"
                                                                    :postitoimipaikannimi "Tuonela"
                                                                    :ulkomainenLahiosoite "katu"
                                                                    :ulkomainenPostitoimipaikka "Tuonela"
                                                                    :valtioKansainvalinen "CHN"
                                                                    :valtioSuomeksi "Kiina"}
                                                      :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                                                       :postinumero "33800"
                                                                                       :postitoimipaikannimi "Tuonela"
                                                                                       :ulkomainenLahiosoite "katu"
                                                                                       :ulkomainenPostitoimipaikka "Tuonela"
                                                                                       :valtioKansainvalinen "CHN"
                                                                                       :valtioSuomeksi "Kiina"}}
                                                      :puhelin "03-389 1380"
                                                      :sahkopostiosoite "yritys@example.com"
                                                      :vainsahkoinenAsiointiKytkin false}}]
                                :rakentajatyyppi "muu"
                                :tiedot {:asuinhuoneistot {:huoneisto [{:huoneistoala 56
                                                                        :huoneistonTyyppi "asuinhuoneisto"
                                                                        :huoneistotunnus {:huoneistonumero "001"
                                                                                          :jakokirjain "a"
                                                                                          :porras "A"}
                                                                        :huoneluku 66
                                                                        :keittionTyyppi "keittio"
                                                                        :muutostapa "lisäys"
                                                                        :varusteet {:WCKytkin true
                                                                                    :ammeTaiSuihkuKytkin false
                                                                                    :lamminvesiKytkin false
                                                                                    :parvekeTaiTerassiKytkin true
                                                                                    :saunaKytkin false}}
                                                                       {:huoneistoala 2
                                                                        :huoneistonTyyppi "toimitila"
                                                                        :huoneistotunnus {:huoneistonumero "002"
                                                                                          :jakokirjain "a"
                                                                                          :porras "A"}
                                                                        :huoneluku 12
                                                                        :keittionTyyppi "keittokomero"
                                                                        :muutostapa "muutos"
                                                                        :varusteet {:WCKytkin false
                                                                                    :ammeTaiSuihkuKytkin true
                                                                                    :lamminvesiKytkin true
                                                                                    :parvekeTaiTerassiKytkin false
                                                                                    :saunaKytkin true}}]}
                                         :energialuokka "C"
                                         :energiatehokkuusluku 124
                                         :energiatehokkuusluvunYksikko "kWh/m2"
                                         :julkisivu {:julkisivumateriaali "puu"}
                                         :kantavaRakennusaine {:rakennusaine "puu"}
                                         :kayttotarkoitus "012 kahden asunnon talot"
                                         :kellarinpinta-ala 100
                                         :kerrosala 180
                                         :kerrosluku 2
                                         :kokonaisala 1000
                                         :lammitystapa "vesikeskus"
                                         :lammonlahde {:muu "polttopuillahan tuo"}
                                         :liitettyJatevesijarjestelmaanKytkin true
                                         :paloluokka "P1"
                                         :rakennusoikeudellinenKerrosala 160
                                         :rakennustunnus {:jarjestysnumero 3
                                                          :kiinttun "21111111111111"
                                                          :muuTunnustieto [{:MuuTunnus {:sovellus "toimenpideId"
                                                                                        :tunnus "laajentaminen-id"}}
                                                                           {:MuuTunnus {:sovellus "Lupapiste"
                                                                                        :tunnus "laajentaminen-id"}}]
                                                          :rakennusnro "001"}
                                         :rakentamistapa "elementti"
                                         :tilavuus 1500
                                         :varusteet {:aurinkopaneeliKytkin true
                                                     :hissiKytkin true
                                                     :kaasuKytkin true
                                                     :koneellinenilmastointiKytkin true
                                                     :lamminvesiKytkin true
                                                     :sahkoKytkin true
                                                     :saunoja 1
                                                     :vaestonsuoja 1
                                                     :vesijohtoKytkin true
                                                     :viemariKytkin true}
                                         :verkostoliittymat {:kaapeliKytkin true
                                                             :maakaasuKytkin true
                                                             :sahkoKytkin true
                                                             :vesijohtoKytkin true
                                                             :viemariKytkin true}}}}
                    {:id "kaupunkikuva-id"
                     :primary false
                     :kuvaus nil
                     :nimi "Aidan rakentaminen"
                     :rakennelma {:kayttotarkoitus "Aita"
                                  :kiinttun "21111111111111"
                                  :kokonaisala "0"
                                  :kuvaus {:kuvaus "Aidan rakentaminen rajalle"}
                                  :tunnus {:jarjestysnumero 4}}
                     :rakennus nil}
                    {:id "5177ad63da060e8cd8348e32"
                     :primary false
                     :kuvaus nil
                     :nimi "Puiden kaataminen"
                     :rakennelma nil
                     :rakennus nil}
                    {:id "purkaminen-id"
                     :primary false
                     :kuvaus nil
                     :nimi "Rakennuksen purkaminen"
                     :rakennelma nil
                     :rakennus {:omistajat [{:VRKrooliKoodi "rakennuksen omistaja"
                                             :henkilo {:henkilotunnus "210281-9988"
                                                       :nimi {:etunimi "Pena"
                                                              :sukunimi "Penttilä"}
                                                       :osoite {:osoitenimi {:teksti "katu"}
                                                                :postinumero "33800"
                                                                :postitoimipaikannimi "Tuonela"
                                                                :ulkomainenLahiosoite "katu"
                                                                :ulkomainenPostitoimipaikka "Tuonela"
                                                                :valtioKansainvalinen "CHN"
                                                                :valtioSuomeksi "Kiina"}
                                                       :puhelin "+358401234567"
                                                       :sahkopostiosoite "pena@example.com"
                                                       :vainsahkoinenAsiointiKytkin false}
                                             :kuntaRooliKoodi "Rakennuksen omistaja"
                                             :omistajalaji {:omistajalaji "muu yksityinen henkilö tai perikunta"}
                                             :suoramarkkinointikieltoKytkin true
                                             :turvakieltoKytkin true}
                                            {:VRKrooliKoodi "rakennuksen omistaja"
                                             :henkilo {:nimi {:etunimi "Pena"
                                                              :sukunimi "Penttilä"}
                                                       :puhelin "03-389 1380"
                                                       :sahkopostiosoite "yritys@example.com"}
                                             :kuntaRooliKoodi "Rakennuksen omistaja"
                                             :omistajalaji {:omistajalaji "yksityinen yritys (osake-, avoin- tai kommandiittiyhtiö, osuuskunta)"}
                                             :suoramarkkinointikieltoKytkin true
                                             :turvakieltoKytkin true
                                             :yritys {:liikeJaYhteisotunnus "1060155-5"
                                                      :nimi "Solita Oy"
                                                      :postiosoite {:osoitenimi {:teksti "katu"}
                                                                    :postinumero "33800"
                                                                    :postitoimipaikannimi "Tuonela"
                                                                    :ulkomainenLahiosoite "katu"
                                                                    :ulkomainenPostitoimipaikka "Tuonela"
                                                                    :valtioKansainvalinen "CHN"
                                                                    :valtioSuomeksi "Kiina"}
                                                      :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                                                       :postinumero "33800"
                                                                                       :postitoimipaikannimi "Tuonela"
                                                                                       :ulkomainenLahiosoite "katu"
                                                                                       :ulkomainenPostitoimipaikka "Tuonela"
                                                                                       :valtioKansainvalinen "CHN"
                                                                                       :valtioSuomeksi "Kiina"}}
                                                      :puhelin "03-389 1380"
                                                      :sahkopostiosoite "yritys@example.com"
                                                      :vainsahkoinenAsiointiKytkin false}}]
                                :rakentajatyyppi "muu"
                                :tiedot {:julkisivu {:julkisivumateriaali "puu"}
                                         :kantavaRakennusaine {:rakennusaine "puu"}
                                         :kayttotarkoitus "012 kahden asunnon talot"
                                         :kellarinpinta-ala 100
                                         :kerrosala 180
                                         :kerrosluku 2
                                         :kokonaisala 1000
                                         :rakennusoikeudellinenKerrosala 160
                                         :rakennustunnus {:jarjestysnumero 5
                                                          :kiinttun "21111111111111"
                                                          :muuTunnustieto [{:MuuTunnus {:sovellus "toimenpideId"
                                                                                        :tunnus "purkaminen-id"}}
                                                                           {:MuuTunnus {:sovellus "Lupapiste"
                                                                                        :tunnus "purkaminen-id"}}]
                                                          :rakennusnro "001"}
                                         :rakentamistapa "elementti"
                                         :tilavuus 1500}}}]
       :permitType "R"
       :projectDescription "Uuden rakennuksen rakentaminen tontille.\n\nPuiden kaataminen:Puun kaataminen"
       :parties [{:VRKrooliKoodi "maksaja"
                  :henkilo {:nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                            :puhelin "03-389 1380"
                            :sahkopostiosoite "yritys@example.com"}
                  :kuntaRooliKoodi "Rakennusvalvonta-asian laskun maksaja"
                  :suoramarkkinointikieltoKytkin true
                  :turvakieltoKytkin true
                  :yritys {:liikeJaYhteisotunnus "1060155-5"
                           :nimi "Solita Oy"
                           :postiosoite {:osoitenimi {:teksti "katu"}
                                         :postinumero "33800"
                                         :postitoimipaikannimi "Tuonela"
                                         :ulkomainenLahiosoite "katu"
                                         :ulkomainenPostitoimipaikka "Tuonela"
                                         :valtioKansainvalinen "CHN"
                                         :valtioSuomeksi "Kiina"}
                           :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                            :postinumero "33800"
                                                            :postitoimipaikannimi "Tuonela"
                                                            :ulkomainenLahiosoite "katu"
                                                            :ulkomainenPostitoimipaikka "Tuonela"
                                                            :valtioKansainvalinen "CHN"
                                                            :valtioSuomeksi "Kiina"}}
                           :puhelin "03-389 1380"
                           :sahkopostiosoite "yritys@example.com"
                           :vainsahkoinenAsiointiKytkin false
                           :verkkolaskutustieto {:Verkkolaskutus {:ovtTunnus "003712345671"
                                                                  :valittajaTunnus "BAWCFI22"
                                                                  :verkkolaskuTunnus "laskutunnus-1234"}}}}
                 {:VRKrooliKoodi "maksaja"
                  :henkilo {:henkilotunnus "210281-9988"
                            :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                            :osoite {:osoitenimi {:teksti "katu"}
                                     :postinumero "33800"
                                     :postitoimipaikannimi "Tuonela"
                                     :ulkomainenLahiosoite "katu"
                                     :ulkomainenPostitoimipaikka "Tuonela"
                                     :valtioKansainvalinen "CHN"
                                     :valtioSuomeksi "Kiina"}
                            :puhelin "+358401234567"
                            :sahkopostiosoite "pena@example.com"
                            :vainsahkoinenAsiointiKytkin false}
                  :kuntaRooliKoodi "Rakennusvalvonta-asian laskun maksaja"
                  :suoramarkkinointikieltoKytkin true
                  :turvakieltoKytkin true}
                 {:VRKrooliKoodi "muu osapuoli"
                  :henkilo {:henkilotunnus "210281-9988"
                            :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                            :osoite {:osoitenimi {:teksti "katu"}
                                     :postinumero "33800"
                                     :postitoimipaikannimi "Tuonela"
                                     :ulkomainenLahiosoite "katu"
                                     :ulkomainenPostitoimipaikka "Tuonela"
                                     :valtioKansainvalinen "CHN"
                                     :valtioSuomeksi "Kiina"}
                            :puhelin "+358401234567"
                            :sahkopostiosoite "pena@example.com"
                            :vainsahkoinenAsiointiKytkin true}
                  :kuntaRooliKoodi "Hakijan asiamies"
                  :suoramarkkinointikieltoKytkin true
                  :turvakieltoKytkin true}
                 {:VRKrooliKoodi "hakija"
                  :henkilo {:henkilotunnus "210281-9988"
                            :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                            :osoite {:osoitenimi {:teksti "katu"}
                                     :postinumero "33800"
                                     :postitoimipaikannimi "Tuonela"
                                     :ulkomainenLahiosoite "katu"
                                     :ulkomainenPostitoimipaikka "Tuonela"
                                     :valtioKansainvalinen "CHN"
                                     :valtioSuomeksi "Kiina"}
                            :puhelin "+358401234567"
                            :sahkopostiosoite "pena@example.com"
                            :vainsahkoinenAsiointiKytkin true}
                  :kuntaRooliKoodi "Rakennusvalvonta-asian hakija"
                  :suoramarkkinointikieltoKytkin true
                  :turvakieltoKytkin true}
                 {:VRKrooliKoodi "hakija"
                  :henkilo {:nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                            :puhelin "03-389 1380"
                            :sahkopostiosoite "yritys@example.com"}
                  :kuntaRooliKoodi "Rakennusvalvonta-asian hakija"
                  :suoramarkkinointikieltoKytkin false
                  :turvakieltoKytkin true
                  :yritys {:liikeJaYhteisotunnus "1060155-5"
                           :nimi "Solita Oy"
                           :postiosoite {:osoitenimi {:teksti "katu"}
                                         :postinumero "33800"
                                         :postitoimipaikannimi "Tuonela"
                                         :ulkomainenLahiosoite "katu"
                                         :ulkomainenPostitoimipaikka "Tuonela"
                                         :valtioKansainvalinen "CHN"
                                         :valtioSuomeksi "Kiina"}
                           :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                            :postinumero "33800"
                                                            :postitoimipaikannimi "Tuonela"
                                                            :ulkomainenLahiosoite "katu"
                                                            :ulkomainenPostitoimipaikka "Tuonela"
                                                            :valtioKansainvalinen "CHN"
                                                            :valtioSuomeksi "Kiina"}}
                           :puhelin "03-389 1380"
                           :sahkopostiosoite "yritys@example.com"
                           :vainsahkoinenAsiointiKytkin true}}]
       :planners [{:FISEkelpoisuus "tavanomainen pääsuunnittelu (uudisrakentaminen)"
                   :FISEpatevyyskortti "http://www.ym.fi"
                   :VRKrooliKoodi "erityissuunnittelija"
                   :henkilo {:henkilotunnus "210281-9988"
                             :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                             :osoite {:osoitenimi {:teksti "katu"}
                                      :postinumero "33800"
                                      :postitoimipaikannimi "Tuonela"
                                      :ulkomainenLahiosoite "katu"
                                      :ulkomainenPostitoimipaikka "Tuonela"
                                      :valtioKansainvalinen "CHN"
                                      :valtioSuomeksi "Kiina"}
                             :puhelin "+358401234567"
                             :sahkopostiosoite "pena@example.com"}
                   :kokemusvuodet 5
                   :koulutus "arkkitehti"
                   :muuSuunnittelijaRooli "ei listassa -rooli"
                   :patevyysvaatimusluokka "B"
                   :suunnittelijaRoolikoodi "muu"
                   :vaadittuPatevyysluokka "C"
                   :valmistumisvuosi 2010
                   :yritys {:liikeJaYhteisotunnus "1060155-5"
                            :nimi "Solita Oy"
                            :postiosoite {:osoitenimi {:teksti "katu"}
                                          :postinumero "33800"
                                          :postitoimipaikannimi "Tuonela"
                                          :ulkomainenLahiosoite "katu"
                                          :ulkomainenPostitoimipaikka "Tuonela"
                                          :valtioKansainvalinen "CHN"
                                          :valtioSuomeksi "Kiina"}
                            :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                             :postinumero "33800"
                                                             :postitoimipaikannimi "Tuonela"
                                                             :ulkomainenLahiosoite "katu"
                                                             :ulkomainenPostitoimipaikka "Tuonela"
                                                             :valtioKansainvalinen "CHN"
                                                             :valtioSuomeksi "Kiina"}}}}
                  {:FISEkelpoisuus "tavanomainen pääsuunnittelu (uudisrakentaminen)"
                   :FISEpatevyyskortti "http://www.ym.fi"
                   :VRKrooliKoodi "erityissuunnittelija"
                   :henkilo {:henkilotunnus "210281-9988"
                             :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                             :osoite {:osoitenimi {:teksti "katu"}
                                      :postinumero "33800"
                                      :postitoimipaikannimi "Tuonela"
                                      :ulkomainenLahiosoite "katu"
                                      :ulkomainenPostitoimipaikka "Tuonela"
                                      :valtioKansainvalinen "CHN"
                                      :valtioSuomeksi "Kiina"}
                             :puhelin "+358401234567"
                             :sahkopostiosoite "pena@example.com"}
                   :kokemusvuodet 5
                   :koulutus "muu"
                   :patevyysvaatimusluokka "AA"
                   :suunnittelijaRoolikoodi "GEO-suunnittelija"
                   :vaadittuPatevyysluokka "A"
                   :valmistumisvuosi 2010
                   :yritys {:liikeJaYhteisotunnus "1060155-5"
                            :nimi "Solita Oy"
                            :postiosoite {:osoitenimi {:teksti "katu"}
                                          :postinumero "33800"
                                          :postitoimipaikannimi "Tuonela"
                                          :ulkomainenLahiosoite "katu"
                                          :ulkomainenPostitoimipaikka "Tuonela"
                                          :valtioKansainvalinen "CHN"
                                          :valtioSuomeksi "Kiina"}
                            :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                             :postinumero "33800"
                                                             :postitoimipaikannimi "Tuonela"
                                                             :ulkomainenLahiosoite "katu"
                                                             :ulkomainenPostitoimipaikka "Tuonela"
                                                             :valtioKansainvalinen "CHN"
                                                             :valtioSuomeksi "Kiina"}}}}
                  {:FISEkelpoisuus "tavanomainen pääsuunnittelu (uudisrakentaminen)"
                   :FISEpatevyyskortti "http://www.ym.fi"
                   :VRKrooliKoodi "erityissuunnittelija"
                   :henkilo {:henkilotunnus "210281-9988"
                             :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                             :osoite {:osoitenimi {:teksti "katu"}
                                      :postinumero "33800"
                                      :postitoimipaikannimi "Tuonela"
                                      :ulkomainenLahiosoite "katu"
                                      :ulkomainenPostitoimipaikka "Tuonela"
                                      :valtioKansainvalinen "CHN"
                                      :valtioSuomeksi "Kiina"}
                             :puhelin "+358401234567"
                             :sahkopostiosoite "pena@example.com"}
                   :kokemusvuodet 5
                   :koulutus "arkkitehti"
                   :patevyysvaatimusluokka "B"
                   :suunnittelijaRoolikoodi "rakennusfysikaalinen suunnittelija"
                   :vaadittuPatevyysluokka "C"
                   :valmistumisvuosi 2010
                   :yritys {:liikeJaYhteisotunnus "1060155-5"
                            :nimi "Solita Oy"
                            :postiosoite {:osoitenimi {:teksti "katu"}
                                          :postinumero "33800"
                                          :postitoimipaikannimi "Tuonela"
                                          :ulkomainenLahiosoite "katu"
                                          :ulkomainenPostitoimipaikka "Tuonela"
                                          :valtioKansainvalinen "CHN"
                                          :valtioSuomeksi "Kiina"}
                            :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                             :postinumero "33800"
                                                             :postitoimipaikannimi "Tuonela"
                                                             :ulkomainenLahiosoite "katu"
                                                             :ulkomainenPostitoimipaikka "Tuonela"
                                                             :valtioKansainvalinen "CHN"
                                                             :valtioSuomeksi "Kiina"}}}}
                  {:FISEkelpoisuus "tavanomainen pääsuunnittelu (uudisrakentaminen)"
                   :FISEpatevyyskortti "http://www.ym.fi"
                   :VRKrooliKoodi "pääsuunnittelija"
                   :henkilo {:henkilotunnus "210281-9988"
                             :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                             :osoite {:osoitenimi {:teksti "katu"}
                                      :postinumero "33800"
                                      :postitoimipaikannimi "Tuonela"
                                      :ulkomainenLahiosoite "katu"
                                      :ulkomainenPostitoimipaikka "Tuonela"
                                      :valtioKansainvalinen "CHN"
                                      :valtioSuomeksi "Kiina"}
                             :puhelin "+358401234567"
                             :sahkopostiosoite "pena@example.com"}
                   :kokemusvuodet 5
                   :koulutus "arkkitehti"
                   :patevyysvaatimusluokka "ei tiedossa"
                   :suunnittelijaRoolikoodi "pääsuunnittelija"
                   :vaadittuPatevyysluokka "AA"
                   :valmistumisvuosi 2010
                   :yritys {:liikeJaYhteisotunnus "1060155-5"
                            :nimi "Solita Oy"
                            :postiosoite {:osoitenimi {:teksti "katu"}
                                          :postinumero "33800"
                                          :postitoimipaikannimi "Tuonela"
                                          :ulkomainenLahiosoite "katu"
                                          :ulkomainenPostitoimipaikka "Tuonela"
                                          :valtioKansainvalinen "CHN"
                                          :valtioSuomeksi "Kiina"}
                            :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                             :postinumero "33800"
                                                             :postitoimipaikannimi "Tuonela"
                                                             :ulkomainenLahiosoite "katu"
                                                             :ulkomainenPostitoimipaikka "Tuonela"
                                                             :valtioKansainvalinen "CHN"
                                                             :valtioSuomeksi "Kiina"}}}}]
       :foremen [{:VRKrooliKoodi "työnjohtaja"
                  :alkamisPvm "2014-02-13"
                  :henkilo {:henkilotunnus "210281-9988"
                            :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                            :osoite {:osoitenimi {:teksti "katu"}
                                     :postinumero "33800"
                                     :postitoimipaikannimi "Tuonela"
                                     :ulkomainenLahiosoite "katu"
                                     :ulkomainenPostitoimipaikka "Tuonela"
                                     :valtioKansainvalinen "CHN"
                                     :valtioSuomeksi "Kiina"}
                            :puhelin "+358401234567"
                            :sahkopostiosoite "pena@example.com"}
                  :kokemusvuodet 3
                  :koulutus "muu"
                  :paattymisPvm "2014-02-20"
                  :patevyysvaatimusluokka "A"
                  :sijaistettavaHlo "Jaska Jokunen"
                  :sijaistukset [{:alkamisPvm "2014-02-13"
                                  :paattymisPvm "2014-02-20"
                                  :sijaistettavaHlo "Jaska Jokunen"
                                  :sijaistettavaRooli "KVV-työnjohtaja"}]
                  :tyonjohtajaHakemusKytkin true
                  :tyonjohtajaRooliKoodi "KVV-työnjohtaja"
                  :vaadittuPatevyysluokka "A"
                  :valmistumisvuosi 2010
                  :valvottavienKohteidenMaara 9
                  :vastattavatTyot ["Kiinteistön vesi- ja viemärilaitteiston rakentaminen"
                                    "Kiinteistön ilmanvaihtolaitteiston rakentaminen"
                                    "Maanrakennustyö"
                                    "Rakennelma tai laitos"
                                    "Muu tyotehtava"]
                  :yritys {:liikeJaYhteisotunnus "1060155-5"
                           :nimi "Solita Oy"
                           :postiosoite {:osoitenimi {:teksti "katu"}
                                         :postinumero "33800"
                                         :postitoimipaikannimi "Tuonela"
                                         :ulkomainenLahiosoite "katu"
                                         :ulkomainenPostitoimipaikka "Tuonela"
                                         :valtioKansainvalinen "CHN"
                                         :valtioSuomeksi "Kiina"}
                           :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                            :postinumero "33800"
                                                            :postitoimipaikannimi "Tuonela"
                                                            :ulkomainenLahiosoite "katu"
                                                            :ulkomainenPostitoimipaikka "Tuonela"
                                                            :valtioKansainvalinen "CHN"
                                                            :valtioSuomeksi "Kiina"}}}}]
       :reviews [{:type "ei tiedossa"
                  :reviewer "Reijo Revyy"
                  :date "2012-12-03"
                  :verottajanTvLl false}]
       :state "submitted"
       :stateChangeTs 12345
       :createdTs (:created application-rakennuslupa)
       :modifiedTs (:modified application-rakennuslupa)
       ;; Note that draft statement is not present!
       :statements [{:lausunto "Savupiippu pitää olla."
                     :lausuntoPvm "2013-05-09"
                     :puoltotieto {:puolto "ehdollinen"}
                     :viranomainen "Paloviranomainen"}]
       :araFunding false
       :verdicts []})

  (->reporting-result (update application-rakennuslupa
                              :documents
                              (partial map #(if (= (-> % :schema-info :name)
                                                   "hankkeen-kuvaus")
                                              (assoc-in % [:data :rahoitus :value] true)
                                              %)))
                      "fi")
  => (contains {:araFunding true})

  (->reporting-result application-rakennuslupa-with-verdicts "fi")
  => (contains {:verdicts
                [{:kuntalupatunnus nil
                  :lupamaaraykset {:autopaikkojaEnintaan nil
                                   :autopaikkojaKiinteistolla 2
                                   :autopaikkojaRakennettava 3
                                   :autopaikkojaRakennettu 1
                                   :autopaikkojaUlkopuolella nil
                                   :autopaikkojaVahintaan nil
                                   :kerrosala nil
                                   :kokonaisala nil
                                   :maaraystieto ["muut lupaehdot - teksti"
                                                  "toinen teksti"]
                                   :rakennusoikeudellinenKerrosala nil
                                   :vaadittuErityissuunnitelmatieto [{:toteutumisPvm nil
                                                                      :vaadittuErityissuunnitelma "Suunnitelmat"}
                                                                     {:toteutumisPvm nil
                                                                      :vaadittuErityissuunnitelma "Suunnitelmat2"}]
                                   :vaadittuTyonjohtajatieto ["erityisalojen työnjohtaja"
                                                              "IV-työnjohtaja"
                                                              "vastaava työnjohtaja"
                                                              "KVV-työnjohtaja"]
                                   :vaaditutKatselmukset [{:katselmuksenLaji "muu katselmus"
                                                           :muuTunnustieto []
                                                           :tarkastuksenTaiKatselmuksenNimi "Katselmus"}
                                                          {:katselmuksenLaji "rakennuksen paikan merkitseminen"
                                                           :muuTunnustieto []
                                                           :tarkastuksenTaiKatselmuksenNimi "Katselmus2"}]}
                  :paivamaarat {:aloitettavaPvm "2022-11-23"
                                :antoPvm "2017-11-20"
                                :julkipanoPvm "2017-11-24"
                                :lainvoimainenPvm "2017-11-27"
                                :raukeamisPvm nil
                                :viimeinenValitusPvm "2017-12-27"
                                :voimassaHetkiPvm "2023-11-23"}
                  :poytakirja {:paatoksentekija "Pate Paattaja (Viranhaltija)"
                               :paatos "päätös - teksti"
                               :paatoskoodi "myönnetty"
                               :paatospvm "2017-11-23"
                               :pykala "99"}}
                 {:kuntalupatunnus "2013-01"
                  :lupamaaraykset {:autopaikkojaEnintaan nil
                                   :autopaikkojaKiinteistolla nil
                                   :autopaikkojaRakennettava nil
                                   :autopaikkojaRakennettu nil
                                   :autopaikkojaUlkopuolella nil
                                   :autopaikkojaVahintaan nil
                                   :kerrosala nil
                                   :kokonaisala nil
                                   :maaraystieto []
                                   :rakennusoikeudellinenKerrosala nil
                                   :vaadittuErityissuunnitelmatieto []
                                   :vaadittuTyonjohtajatieto []
                                   :vaaditutKatselmukset []}
                  :paivamaarat {:aloitettavaPvm nil
                                :antoPvm "2013-09-05"
                                :julkipanoPvm nil
                                :lainvoimainenPvm nil
                                :raukeamisPvm nil
                                :viimeinenValitusPvm nil
                                :voimassaHetkiPvm nil}
                  :poytakirja {:paatoksentekija "elmo viranomainen"
                               :paatos "Päätös on nyt tämä."
                               :paatoskoodi nil
                               :paatospvm "2013-09-01"
                               :pykala 1}}
                 {:kuntalupatunnus "13-0185-R"
                  :lupamaaraykset {:autopaikkojaEnintaan 10
                                   :autopaikkojaKiinteistolla 7
                                   :autopaikkojaRakennettava 2
                                   :autopaikkojaRakennettu 0
                                   :autopaikkojaUlkopuolella 3
                                   :autopaikkojaVahintaan 1
                                   :kerrosala 100
                                   :kokonaisala 110
                                   :maaraystieto ["Radontekninen suunnitelma"
                                                  "Ilmanvaihtosuunnitelma"]
                                   :rakennusoikeudellinenKerrosala 100
                                   :vaadittuErityissuunnitelmatieto []
                                   :vaadittuTyonjohtajatieto []
                                   :vaaditutKatselmukset [{:katselmuksenLaji "aloituskokous"
                                                           :muuTunnustieto []
                                                           :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}
                                                          {:katselmuksenLaji "muu tarkastus"
                                                           :muuTunnustieto []
                                                           :tarkastuksenTaiKatselmuksenNimi "Käyttöönottotarkastus"}]}
                  :paivamaarat {:aloitettavaPvm "2013-09-01"
                                :antoPvm "2013-09-05"
                                :julkipanoPvm "2013-09-07"
                                :lainvoimainenPvm "2013-09-02"
                                :raukeamisPvm "2013-09-04"
                                :viimeinenValitusPvm nil
                                :voimassaHetkiPvm "2013-09-03"}
                  :poytakirja {:paatoksentekija "johtava viranomainen"
                               :paatos "Päätösteksti avaimen :paatos alla"
                               :paatoskoodi nil
                               :paatospvm "2013-09-01"
                               :pykala nil}}]}))
