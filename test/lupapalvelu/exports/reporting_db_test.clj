(ns lupapalvelu.exports.reporting-db-test
  (:require [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test :as kaivu]
            [lupapalvelu.exports.reporting-db :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict :as pv]
            [lupapalvelu.pate.verdict-canonical-test :as vct]
            [lupapalvelu.rakennuslupa-canonical-util :refer [application-rakennuslupa]]
            [midje.sweet :refer :all]
            [sade.util :as util]))



(def review-empty {:data {:katselmus {:pitoPvm {:value nil} ; pitoPvm should be defined to be valid
                                      :pitaja {:value "Reijo Revyy"}
                                      :lasnaolijat {:value "Keijo Kevyt"}
                                      :poikkeamat {:value "Poikkeava roiskehana"}
                                      :tila {:value nil}
                                      :tiedoksianto {:value nil}
                                      :huomautukset {:kuvaus {:value "Huomautettavaa l\u00f6ytyi."}
                                                     :maaraAika {:value "27.11.2012"}
                                                     :toteaja {:value ""}
                                                     :toteamisHetki {:value ""}}}
                          :katselmuksenLaji {:value "Aloitusilmoitus"}
                          :vaadittuLupaehtona {:value nil}
                          :rakennus {:0 {:tila {:tila {:value "osittainen"}}
                                         :rakennus {:rakennusnro {:value "002"}
                                                    :jarjestysnumero {:value 1}
                                                    :kiinttun {:value "21111111111111"}}}}}
                   :id "666"
                   :taskname "Tyhjä katselmus 1"})

(def review {:data {:katselmus {:pitoPvm {:value 1354532324658}
                                :pitaja {:value "Reijo Revyy"}
                                :lasnaolijat {:value "Keijo Kevyt"}
                                :poikkeamat {:value "Poikkeava roiskehana"}
                                :tila {:value nil}
                                :tiedoksianto {:value nil}
                                :huomautukset {:kuvaus {:value "Huomautettavaa l\u00f6ytyi."}
                                               :maaraAika {:value "27.11.2012"}
                                               :toteaja {:value ""}
                                               :toteamisHetki {:value ""}}}
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
                                              :valtakunnallinenNumero {:value "1234567893"}}}}
                    :muuTunnus "review1"
                    :muuTunnusSovellus "RakApp"}
             :id "123"
             :taskname "Aloitusilmoitus 1"})

(def verdicts
  [{:kuntalupatunnus "2013-01"
    :paatokset
    [{:paivamaarat {:anto 1378339200000}
      :poytakirjat
      [{:paatoskoodi "my\u00f6nnetty"
        :paatospvm 1377993600000
        :pykala 1
        :paatoksentekija "elmo viranomainen"
        :paatos "Päät\u00f6s on nyt tämä."
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
       :kokoontumistilanHenkilomaara 15
       :autopaikkojaUlkopuolella 3
       :autopaikkojaVahintaan 1
       :vaaditutTyonjohtajat
       "Vastaava ty\u00f6njohtaja, Vastaava IV-ty\u00f6njohtaja, Ty\u00f6njohtaja"
       :autopaikkojaRakennettava 2
       :vaaditutKatselmukset
       [{:katselmuksenLaji "aloituskokous"
         :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"
         :muuTunnus ""
         :muuTunnusSovellus ""}
        {:katselmuksenLaji "muu tarkastus"
         :tarkastuksenTaiKatselmuksenNimi "Käytt\u00f6\u00f6nottotarkastus"
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
      [{:paatoskoodi "my\u00f6nnetty"
        :paatospvm 1375315200000
        :pykala 1
        :paatoksentekija "viranomainen"
        :paatos "Päät\u00f6s 1"
        :status "1"
        :urlHash "236d9b2cfff88098d4f8ad532820c9fb93393237"}
       {:paatoskoodi "ehdollinen"
        :paatospvm 1375401600000
        :pykala 2
        :paatoksentekija "M\u00f6lli Keinonen"
        :paatos
        "Päät\u00f6steksti avaimen :paatos alla"
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
      [{:paatoskoodi "my\u00f6nnetty"
        :paatospvm 1377993600000
        :paatoksentekija "johtava viranomainen"
        :paatos "Päät\u00f6s 2"
        :status "1"}]
      :id "5c0a6a7127093589384e7ccb"}]
    :id "5c0a6a7127093589384e7cc3"
    :timestamp 1544186481204}])

(def pate-verdicts
  [(:verdict
     (pv/finalize--verdict
       {:verdict vct/verdict ; the verdict here was draft, so we 'publish' it using finalize--verdict
        :command {:created 123
                  :user {:firstName "Rapsa" :lastName "Raportoija" :id "666" :username "rapsa"}
                  :application application-rakennuslupa}}))])

(def application-rakennuslupa-with-verdicts
  (-> application-rakennuslupa
      (assoc :verdicts verdicts
             :pate-verdicts pate-verdicts
             :buildings [{:propertyId   "21111111111111"
                          :nationalId   "1234567892"
                          :localShortId "002"
                          :description  "Eri talo"
                          :location [408048.1 6693225.1]
                          :location-wgs84 [25.33295 60.36502]}])))

(def application-rakennuslupa-with-tasks
  (-> application-rakennuslupa
      (update :history conj {:tosFunction {:code "00 00 00 01" :name "Rakennusmenettely"}})
      (assoc :tasks [review review-empty])))

(facts "->reporting-result"
  (against-background
    (org/pate-scope? irrelevant) => false
    (org/get-application-organization anything) => {})

  (fact "only specified fields are selected"
        (->reporting-result application-rakennuslupa
                        []
                        "fi"
                        [:permitSubtype])
        => {:id            (:id application-rakennuslupa)
            :permitSubtype (:permitSubtype application-rakennuslupa)}
    (->reporting-result application-rakennuslupa
                        []
                        "fi"
                        [:permitSubtype :permitType])
    => {:id            (:id application-rakennuslupa)
        :permitType    (:permitType application-rakennuslupa)
        :permitSubtype (:permitSubtype application-rakennuslupa)})

  (->reporting-result
    application-rakennuslupa-with-tasks
    [{:_id                                     irrelevant
      :link                                    [(:id application-rakennuslupa)
                                                "LP-000-2019-00000"]
      (keyword (:id application-rakennuslupa)) {:type       "linkpermit"
                                                :apptype    (:permitType application-rakennuslupa)
                                                :propertyId (:propertyId application-rakennuslupa)}
      :LP-000-2019-00000                       {:type           "linkpermit"
                                                :linkpermittype "lupapistetunnus"
                                                :apptype        "tyonjohtajan-nimeaminen-v2"}}]
    "fi"
    [])
  =>
  {:id                    (:id application-rakennuslupa)
   :address               (:address application-rakennuslupa)
   :propertyId            (:propertyId application-rakennuslupa)
   :organization          (:organization application-rakennuslupa)
   :municipality          (:municipality application-rakennuslupa)
   :location-etrs-tm35fin (:location application-rakennuslupa)
   :location-wgs84        (:location-wgs84 application-rakennuslupa)
   :handlers              [{:nimi {:etunimi "Sonja" :sukunimi "Sibbo"} :rooli "K\u00e4sittelij\u00e4"}]
   :tosFunction           "00 00 00 01"
   :tosFunctionName       "Rakennusmenettely"
   :links                 [{:id         "LP-000-2019-00000"
                            :permitType "tyonjohtajan-nimeaminen-v2"}]
   :operations
   [{:id         "muu-laajentaminen-id"
     :primary    true
     :operation  "muu-laajentaminen"
     :kuvaus     nil
     :nimi       "Muu rakennuksen muutosty\u00f6"
     :rakennelma nil
     :rakennus
     {:omistajat
      [{:VRKrooliKoodi                 "rakennuksen omistaja"
        :henkilo
        {:nimi                        {:etunimi  "Pena"
                                       :sukunimi "Penttilä"}
         :osoite                      {:osoitenimi                 {:teksti "katu"}
                                       :postinumero                "33800"
                                       :postitoimipaikannimi       "Tuonela"
                                       :ulkomainenLahiosoite       "katu"
                                       :ulkomainenPostitoimipaikka "Tuonela"
                                       :valtioKansainvalinen       "CHN"
                                       :valtioSuomeksi             "Kiina"}
         :puhelin                     "+358401234567"
         :sahkopostiosoite            "pena@example.com"
         :vainsahkoinenAsiointiKytkin false}
        :kuntaRooliKoodi               "Rakennuksen omistaja"
        :omistajalaji                  {:omistajalaji "muu yksityinen henkil\u00f6 tai perikunta"}
        :suoramarkkinointikieltoKytkin true
        :turvakieltoKytkin             true}
       {:VRKrooliKoodi                 "rakennuksen omistaja"
        :henkilo                       {:nimi             {:etunimi  "Pena"
                                                           :sukunimi "Penttilä"}
                                        :puhelin          "03-389 1380"
                                        :sahkopostiosoite "yritys@example.com"}
        :kuntaRooliKoodi               "Rakennuksen omistaja"
        :omistajalaji
        {:omistajalaji "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"}
        :suoramarkkinointikieltoKytkin true
        :turvakieltoKytkin             true
        :yritys
        {:liikeJaYhteisotunnus        "1060155-5"
         :nimi                        "Solita Oy"
         :postiosoitetieto            {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                                     :postinumero                "33800"
                                                     :postitoimipaikannimi       "Tuonela"
                                                     :ulkomainenLahiosoite       "katu"
                                                     :ulkomainenPostitoimipaikka "Tuonela"
                                                     :valtioKansainvalinen       "CHN"
                                                     :valtioSuomeksi             "Kiina"}}
         :puhelin                     "03-389 1380"
         :sahkopostiosoite            "yritys@example.com"
         :vainsahkoinenAsiointiKytkin false}}]
      :rakentajatyyppi               "muu"
      :tilapainenRakennusKytkin      true
      :tilapainenRakennusvoimassaPvm "2019-05-05"
      :kokoontumistilanHenkilomaara  10
      :tiedot
      {:asuinhuoneistot
       {:huoneisto [{:huoneistoala                    56
                     :huoneistonTyyppi                "asuinhuoneisto"
                     :huoneistotunnus                 {:huoneistonumero "001"
                                                       :jakokirjain     "a"
                                                       :porras          "A"}
                     :huoneluku                       66
                     :keittionTyyppi                  "keittio"
                     :muutostapa                      "lisäys"
                     :valtakunnallinenHuoneistotunnus "123456789"
                     :varusteet                       {:WCKytkin                true
                                                       :ammeTaiSuihkuKytkin     false
                                                       :lamminvesiKytkin        false
                                                       :parvekeTaiTerassiKytkin true
                                                       :saunaKytkin             false}}
                    {:huoneistoala                    2
                     :huoneistonTyyppi                "toimitila"
                     :huoneistotunnus                 {:huoneistonumero "002"
                                                       :jakokirjain     "a"
                                                       :porras          "A"}
                     :huoneluku                       12
                     :keittionTyyppi                  "keittokomero"
                     :muutostapa                      "lisäys"
                     :valtakunnallinenHuoneistotunnus "123"
                     :varusteet                       {:WCKytkin                false
                                                       :ammeTaiSuihkuKytkin     true
                                                       :lamminvesiKytkin        true
                                                       :parvekeTaiTerassiKytkin false
                                                       :saunaKytkin             true}}]}
       :energialuokka                       "C"
       :energiatehokkuusluku                124
       :energiatehokkuusluvunYksikko        "kWh/m2"
       :julkisivu                           {:julkisivumateriaali "puu"}
       :kantavaRakennusaine                 {:rakennusaine "puu"}
       :kayttotarkoitus                     "012 kahden asunnon talot"
       :kellarinpinta-ala                   100
       :kerrosala                           180
       :kerrosluku                          2
       :kokonaisala                         1000
       :lammitystapa                        "vesikeskus"
       :lammonlahde                         {:polttoaine "polttopuillahan tuo"}
       :liitettyJatevesijarjestelmaanKytkin true
       :paloluokka                          "P1"
       :rakennusoikeudellinenKerrosala      160
       :rakennustunnus
       {:jarjestysnumero        1
        :kiinttun               "21111111111111"
        :muuTunnustieto         [{:MuuTunnus {:sovellus "toimenpideId"
                                              :tunnus   "muu-laajentaminen-id"}}
                                 {:MuuTunnus {:sovellus "Lupapiste"
                                              :tunnus   "muu-laajentaminen-id"}}]
        :rakennusnro            "001"
        :valtakunnallinenNumero "1234567892"}
       :rakentamistapa                      "elementti"
       :tilavuus                            1500
       :varusteet                           {:aurinkopaneeliKytkin         true
                                             :hissiKytkin                  true
                                             :kaasuKytkin                  true
                                             :koneellinenilmastointiKytkin true
                                             :lamminvesiKytkin             true
                                             :sahkoKytkin                  true
                                             :saunoja                      1
                                             :vaestonsuoja                 1
                                             :vesijohtoKytkin              true
                                             :viemariKytkin                true}
       :verkostoliittymat                   {:kaapeliKytkin   true
                                             :maakaasuKytkin  true
                                             :sahkoKytkin     true
                                             :vesijohtoKytkin true
                                             :viemariKytkin   true}}}}
    {:id         "kerrostalo-rivitalo-id"
     :operation  "kerrostalo-rivitalo"
     :primary    false
     :kuvaus     "kerrostalo-rivitalo-kuvaus"
     :nimi       "Asuinkerrostalon tai rivitalon rakentaminen"
     :rakennelma nil
     :rakennus
     {:omistajat
      [{:VRKrooliKoodi                 "rakennuksen omistaja"
        :henkilo
        {:nimi                        {:etunimi  "Pena"
                                       :sukunimi "Penttilä"}
         :osoite                      {:osoitenimi                 {:teksti "katu"}
                                       :postinumero                "33800"
                                       :postitoimipaikannimi       "Tuonela"
                                       :ulkomainenLahiosoite       "katu"
                                       :ulkomainenPostitoimipaikka "Tuonela"
                                       :valtioKansainvalinen       "CHN"
                                       :valtioSuomeksi             "Kiina"}
         :puhelin                     "+358401234567"
         :sahkopostiosoite            "pena@example.com"
         :vainsahkoinenAsiointiKytkin false}
        :kuntaRooliKoodi               "Rakennuksen omistaja"
        :omistajalaji                  {:omistajalaji "muu yksityinen henkil\u00f6 tai perikunta"}
        :suoramarkkinointikieltoKytkin true
        :turvakieltoKytkin             true}
       {:VRKrooliKoodi                 "rakennuksen omistaja"
        :henkilo                       {:nimi             {:etunimi  "Pena"
                                                           :sukunimi "Penttilä"}
                                        :puhelin          "03-389 1380"
                                        :sahkopostiosoite "yritys@example.com"}
        :kuntaRooliKoodi               "Rakennuksen omistaja"
        :omistajalaji
        {:omistajalaji "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"}
        :suoramarkkinointikieltoKytkin true
        :turvakieltoKytkin             true
        :yritys
        {:liikeJaYhteisotunnus        "1060155-5"
         :nimi                        "Solita Oy"
         :postiosoitetieto            {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                                     :postinumero                "33800"
                                                     :postitoimipaikannimi       "Tuonela"
                                                     :ulkomainenLahiosoite       "katu"
                                                     :ulkomainenPostitoimipaikka "Tuonela"
                                                     :valtioKansainvalinen       "CHN"
                                                     :valtioSuomeksi             "Kiina"}}
         :puhelin                     "03-389 1380"
         :sahkopostiosoite            "yritys@example.com"
         :vainsahkoinenAsiointiKytkin false}}]
      :rakentajatyyppi               "muu"
      :tilapainenRakennusKytkin      true
      :tilapainenRakennusvoimassaPvm "2019-05-05"
      :kokoontumistilanHenkilomaara  10
      :tiedot
      {:asuinhuoneistot
       {:huoneisto [{:huoneistoala                    56
                     :huoneistonTyyppi                "asuinhuoneisto"
                     :huoneistotunnus                 {:huoneistonumero "001"
                                                       :jakokirjain     "a"
                                                       :porras          "A"}
                     :huoneluku                       66
                     :keittionTyyppi                  "keittio"
                     :muutostapa                      "lisäys"
                     :valtakunnallinenHuoneistotunnus "123456789"
                     :varusteet                       {:WCKytkin                true
                                                       :ammeTaiSuihkuKytkin     false
                                                       :lamminvesiKytkin        false
                                                       :parvekeTaiTerassiKytkin true
                                                       :saunaKytkin             false}}
                    {:huoneistoala                    2
                     :huoneistonTyyppi                "toimitila"
                     :huoneistotunnus                 {:huoneistonumero "002"
                                                       :jakokirjain     "a"
                                                       :porras          "A"}
                     :huoneluku                       12
                     :keittionTyyppi                  "keittokomero"
                     :muutostapa                      "lisäys"
                     :valtakunnallinenHuoneistotunnus "123"
                     :varusteet                       {:WCKytkin                false
                                                       :ammeTaiSuihkuKytkin     true
                                                       :lamminvesiKytkin        true
                                                       :parvekeTaiTerassiKytkin false
                                                       :saunaKytkin             true}}]}
       :energialuokka                       "C"
       :energiatehokkuusluku                124
       :energiatehokkuusluvunYksikko        "kWh/m2"
       :julkisivu                           {:julkisivumateriaali "puu"}
       :kantavaRakennusaine                 {:rakennusaine "puu"}
       :kayttotarkoitus                     "012 kahden asunnon talot"
       :kellarinpinta-ala                   100
       :kerrosala                           180
       :kerrosluku                          2
       :kokonaisala                         1000
       :lammitystapa                        "vesikeskus"
       :lammonlahde                         {:polttoaine "polttopuillahan tuo"}
       :liitettyJatevesijarjestelmaanKytkin true
       :paloluokka                          "P1"
       :rakennusoikeudellinenKerrosala      160
       :rakennustunnus
       {:jarjestysnumero   2
        :kiinttun          "21111111111111"
        :muuTunnustieto    [{:MuuTunnus {:sovellus "toimenpideId"
                                         :tunnus   "kerrostalo-rivitalo-id"}}
                            {:MuuTunnus {:sovellus "Lupapiste"
                                         :tunnus   "kerrostalo-rivitalo-id"}}]
        :rakennuksenSelite "A: kerrostalo-rivitalo-kuvaus"}
       :rakentamistapa                      "elementti"
       :tilavuus                            1500
       :varusteet                           {:aurinkopaneeliKytkin         true
                                             :hissiKytkin                  true
                                             :kaasuKytkin                  true
                                             :koneellinenilmastointiKytkin true
                                             :lamminvesiKytkin             true
                                             :sahkoKytkin                  true
                                             :saunoja                      1
                                             :vaestonsuoja                 1
                                             :vesijohtoKytkin              true
                                             :viemariKytkin                true}
       :verkostoliittymat                   {:kaapeliKytkin   true
                                             :maakaasuKytkin  true
                                             :sahkoKytkin     true
                                             :vesijohtoKytkin true
                                             :viemariKytkin   true}}}}
    {:id         "laajentaminen-id"
     :operation  "laajentaminen"
     :primary    false
     :kuvaus     nil
     :nimi       "Rakennuksen laajentaminen tai korjaaminen"
     :rakennelma nil
     :rakennus
     {:omistajat
      [{:VRKrooliKoodi                 "rakennuksen omistaja"
        :henkilo
        {:nimi                        {:etunimi  "Pena"
                                       :sukunimi "Penttilä"}
         :osoite                      {:osoitenimi                 {:teksti "katu"}
                                       :postinumero                "33800"
                                       :postitoimipaikannimi       "Tuonela"
                                       :ulkomainenLahiosoite       "katu"
                                       :ulkomainenPostitoimipaikka "Tuonela"
                                       :valtioKansainvalinen       "CHN"
                                       :valtioSuomeksi             "Kiina"}
         :puhelin                     "+358401234567"
         :sahkopostiosoite            "pena@example.com"
         :vainsahkoinenAsiointiKytkin false}
        :kuntaRooliKoodi               "Rakennuksen omistaja"
        :omistajalaji                  {:omistajalaji "muu yksityinen henkil\u00f6 tai perikunta"}
        :suoramarkkinointikieltoKytkin true
        :turvakieltoKytkin             true}
       {:VRKrooliKoodi                 "rakennuksen omistaja"
        :henkilo                       {:nimi             {:etunimi  "Pena"
                                                           :sukunimi "Penttilä"}
                                        :puhelin          "03-389 1380"
                                        :sahkopostiosoite "yritys@example.com"}
        :kuntaRooliKoodi               "Rakennuksen omistaja"
        :omistajalaji
        {:omistajalaji "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"}
        :suoramarkkinointikieltoKytkin true
        :turvakieltoKytkin             true
        :yritys
        {:liikeJaYhteisotunnus        "1060155-5"
         :nimi                        "Solita Oy"
         :postiosoitetieto            {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                                     :postinumero                "33800"
                                                     :postitoimipaikannimi       "Tuonela"
                                                     :ulkomainenLahiosoite       "katu"
                                                     :ulkomainenPostitoimipaikka "Tuonela"
                                                     :valtioKansainvalinen       "CHN"
                                                     :valtioSuomeksi             "Kiina"}}
         :puhelin                     "03-389 1380"
         :sahkopostiosoite            "yritys@example.com"
         :vainsahkoinenAsiointiKytkin false}}]
      :rakentajatyyppi               "muu"
      :tilapainenRakennusKytkin      true
      :tilapainenRakennusvoimassaPvm "2019-05-05"
      :kokoontumistilanHenkilomaara  10
      :tiedot
      {:asuinhuoneistot
       {:huoneisto [{:huoneistoala                    56
                     :huoneistonTyyppi                "asuinhuoneisto"
                     :huoneistotunnus                 {:huoneistonumero "001"
                                                       :jakokirjain     "a"
                                                       :porras          "A"}
                     :huoneluku                       66
                     :keittionTyyppi                  "keittio"
                     :muutostapa                      "lisäys"
                     :valtakunnallinenHuoneistotunnus "123456789"
                     :varusteet                       {:WCKytkin                true
                                                       :ammeTaiSuihkuKytkin     false
                                                       :lamminvesiKytkin        false
                                                       :parvekeTaiTerassiKytkin true
                                                       :saunaKytkin             false}}
                    {:huoneistoala                    2
                     :huoneistonTyyppi                "toimitila"
                     :huoneistotunnus                 {:huoneistonumero "002"
                                                       :jakokirjain     "a"
                                                       :porras          "A"}
                     :huoneluku                       12
                     :keittionTyyppi                  "keittokomero"
                     :muutostapa                      "lisäys"
                     :valtakunnallinenHuoneistotunnus "123"
                     :varusteet                       {:WCKytkin                false
                                                       :ammeTaiSuihkuKytkin     true
                                                       :lamminvesiKytkin        true
                                                       :parvekeTaiTerassiKytkin false
                                                       :saunaKytkin             true}}]}
       :energialuokka                       "C"
       :energiatehokkuusluku                124
       :energiatehokkuusluvunYksikko        "kWh/m2"
       :julkisivu                           {:julkisivumateriaali "puu"}
       :kantavaRakennusaine                 {:rakennusaine "puu"}
       :kayttotarkoitus                     "012 kahden asunnon talot"
       :kellarinpinta-ala                   100
       :kerrosala                           180
       :kerrosluku                          2
       :kokonaisala                         1000
       :lammitystapa                        "vesikeskus"
       :lammonlahde                         {:polttoaine "polttopuillahan tuo"}
       :liitettyJatevesijarjestelmaanKytkin true
       :paloluokka                          "P1"
       :rakennusoikeudellinenKerrosala      160
       :rakennustunnus
       {:jarjestysnumero 3
        :kiinttun        "21111111111111"
        :muuTunnustieto  [{:MuuTunnus {:sovellus "toimenpideId"
                                       :tunnus   "laajentaminen-id"}}
                          {:MuuTunnus {:sovellus "Lupapiste"
                                       :tunnus   "laajentaminen-id"}}]
        :rakennusnro     "001"}
       :rakentamistapa                      "elementti"
       :tilavuus                            1500
       :varusteet                           {:aurinkopaneeliKytkin         true
                                             :hissiKytkin                  true
                                             :kaasuKytkin                  true
                                             :koneellinenilmastointiKytkin true
                                             :lamminvesiKytkin             true
                                             :sahkoKytkin                  true
                                             :saunoja                      1
                                             :vaestonsuoja                 1
                                             :vesijohtoKytkin              true
                                             :viemariKytkin                true}
       :verkostoliittymat                   {:kaapeliKytkin   true
                                             :maakaasuKytkin  true
                                             :sahkoKytkin     true
                                             :vesijohtoKytkin true
                                             :viemariKytkin   true}}}}
    {:id         "kaupunkikuva-id"
     :operation  "aita"
     :primary    false
     :kuvaus     nil
     :nimi       "Aidan rakentaminen"
     :rakennelma {:kayttotarkoitus "Aita"
                  :kiinttun        "21111111111111"
                  :kokonaisala     0
                  :kuvaus          {:kuvaus "Aidan rakentaminen rajalle"}
                  :tunnus          {:jarjestysnumero 4}}
     :rakennus   nil}
    {:id         "5177ad63da060e8cd8348e32"
     :operation  "puun-kaataminen"
     :primary    false
     :kuvaus     nil
     :nimi       "Puiden kaataminen"
     :rakennelma nil
     :rakennus   nil}
    {:id         "purkaminen-id"
     :operation  "purkaminen"
     :primary    false
     :kuvaus     nil
     :nimi       "Rakennuksen purkaminen"
     :rakennelma nil
     :rakennus
     {:omistajat
      [{:VRKrooliKoodi                 "rakennuksen omistaja"
        :henkilo
        {:nimi                        {:etunimi  "Pena"
                                       :sukunimi "Penttilä"}
         :osoite                      {:osoitenimi                 {:teksti "katu"}
                                       :postinumero                "33800"
                                       :postitoimipaikannimi       "Tuonela"
                                       :ulkomainenLahiosoite       "katu"
                                       :ulkomainenPostitoimipaikka "Tuonela"
                                       :valtioKansainvalinen       "CHN"
                                       :valtioSuomeksi             "Kiina"}
         :puhelin                     "+358401234567"
         :sahkopostiosoite            "pena@example.com"
         :vainsahkoinenAsiointiKytkin false}
        :kuntaRooliKoodi               "Rakennuksen omistaja"
        :omistajalaji                  {:omistajalaji "muu yksityinen henkil\u00f6 tai perikunta"}
        :suoramarkkinointikieltoKytkin true
        :turvakieltoKytkin             true}
       {:VRKrooliKoodi                 "rakennuksen omistaja"
        :henkilo                       {:nimi             {:etunimi  "Pena"
                                                           :sukunimi "Penttilä"}
                                        :puhelin          "03-389 1380"
                                        :sahkopostiosoite "yritys@example.com"}
        :kuntaRooliKoodi               "Rakennuksen omistaja"
        :omistajalaji
        {:omistajalaji "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"}
        :suoramarkkinointikieltoKytkin true
        :turvakieltoKytkin             true
        :yritys
        {:liikeJaYhteisotunnus        "1060155-5"
         :nimi                        "Solita Oy"
         :postiosoitetieto
         {:postiosoite {:osoitenimi                 {:teksti "katu"}
                        :postinumero                "33800"
                        :postitoimipaikannimi       "Tuonela"
                        :ulkomainenLahiosoite       "katu"
                        :ulkomainenPostitoimipaikka "Tuonela"
                        :valtioKansainvalinen       "CHN"
                        :valtioSuomeksi             "Kiina"}}
         :puhelin                     "03-389 1380"
         :sahkopostiosoite            "yritys@example.com"
         :vainsahkoinenAsiointiKytkin false}}]
      :rakentajatyyppi               "muu"
      :tilapainenRakennusKytkin      true
      :tilapainenRakennusvoimassaPvm "2019-05-05"
      :tiedot
      {:julkisivu                      {:julkisivumateriaali "puu"}
       :kantavaRakennusaine            {:rakennusaine "puu"}
       :kayttotarkoitus                "012 kahden asunnon talot"
       :kellarinpinta-ala              100
       :kerrosala                      180
       :kerrosluku                     2
       :kokonaisala                    1000
       :rakennusoikeudellinenKerrosala 160
       :rakennustunnus
       {:jarjestysnumero 5
        :kiinttun        "21111111111111"
        :muuTunnustieto  [{:MuuTunnus {:sovellus "toimenpideId"
                                       :tunnus   "purkaminen-id"}}
                          {:MuuTunnus {:sovellus "Lupapiste"
                                       :tunnus   "purkaminen-id"}}]
        :rakennusnro     "001"}
       :rakentamistapa                 "elementti"
       :tilavuus                       1500}}}]
   :permitType            "R"
   :permitSubtype         nil
   :reservedArea          nil
   :placementPermit       nil
   :workDates             {:endDate nil :startDate nil}
   :features              []
   :projectDescription
   "Uuden rakennuksen rakentaminen tontille.\n\nPuiden kaataminen:Puun kaataminen"
   :parties
   [{:VRKrooliKoodi                 "maksaja"
     :henkilo                       {:nimi             {:etunimi "Pena" :sukunimi "Penttilä"}
                                     :puhelin          "03-389 1380"
                                     :sahkopostiosoite "yritys@example.com"}
     :kuntaRooliKoodi               "Rakennusvalvonta-asian laskun maksaja"
     :suoramarkkinointikieltoKytkin true
     :turvakieltoKytkin             true
     :yritys
     {:liikeJaYhteisotunnus        "1060155-5"
      :nimi                        "Solita Oy"
      :postiosoitetieto            {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                                  :postinumero                "33800"
                                                  :postitoimipaikannimi       "Tuonela"
                                                  :ulkomainenLahiosoite       "katu"
                                                  :ulkomainenPostitoimipaikka "Tuonela"
                                                  :valtioKansainvalinen       "CHN"
                                                  :valtioSuomeksi             "Kiina"}}
      :puhelin                     "03-389 1380"
      :sahkopostiosoite            "yritys@example.com"
      :vainsahkoinenAsiointiKytkin false
      :verkkolaskutustieto         {:Verkkolaskutus {:ovtTunnus         "003712345671"
                                                     :valittajaTunnus   "BAWCFI22"
                                                     :verkkolaskuTunnus "laskutunnus-1234"}}}}
    {:VRKrooliKoodi                 "maksaja"
     :henkilo
     {:nimi                        {:etunimi "Pena" :sukunimi "Penttilä"}
      :osoite                      {:osoitenimi                 {:teksti "katu"}
                                    :postinumero                "33800"
                                    :postitoimipaikannimi       "Tuonela"
                                    :ulkomainenLahiosoite       "katu"
                                    :ulkomainenPostitoimipaikka "Tuonela"
                                    :valtioKansainvalinen       "CHN"
                                    :valtioSuomeksi             "Kiina"}
      :puhelin                     "+358401234567"
      :sahkopostiosoite            "pena@example.com"
      :vainsahkoinenAsiointiKytkin false}
     :kuntaRooliKoodi               "Rakennusvalvonta-asian laskun maksaja"
     :suoramarkkinointikieltoKytkin true
     :turvakieltoKytkin             true}
    {:VRKrooliKoodi                 "muu osapuoli"
     :henkilo
     {:nimi                        {:etunimi "Pena" :sukunimi "Penttilä"}
      :osoite                      {:osoitenimi                 {:teksti "katu"}
                                    :postinumero                "33800"
                                    :postitoimipaikannimi       "Tuonela"
                                    :ulkomainenLahiosoite       "katu"
                                    :ulkomainenPostitoimipaikka "Tuonela"
                                    :valtioKansainvalinen       "CHN"
                                    :valtioSuomeksi             "Kiina"}
      :puhelin                     "+358401234567"
      :sahkopostiosoite            "pena@example.com"
      :vainsahkoinenAsiointiKytkin true}
     :kuntaRooliKoodi               "Hakijan asiamies"
     :suoramarkkinointikieltoKytkin true
     :turvakieltoKytkin             true}
    {:VRKrooliKoodi                 "hakija"
     :henkilo
     {:nimi                        {:etunimi "Pena" :sukunimi "Penttilä"}
      :osoite                      {:osoitenimi                 {:teksti "katu"}
                                    :postinumero                "33800"
                                    :postitoimipaikannimi       "Tuonela"
                                    :ulkomainenLahiosoite       "katu"
                                    :ulkomainenPostitoimipaikka "Tuonela"
                                    :valtioKansainvalinen       "CHN"
                                    :valtioSuomeksi             "Kiina"}
      :puhelin                     "+358401234567"
      :sahkopostiosoite            "pena@example.com"
      :vainsahkoinenAsiointiKytkin true}
     :kuntaRooliKoodi               "Rakennusvalvonta-asian hakija"
     :suoramarkkinointikieltoKytkin true
     :turvakieltoKytkin             true}
    {:VRKrooliKoodi                 "hakija"
     :henkilo
     {:nimi             {:etunimi "Pena" :sukunimi "Penttilä"}
      :puhelin          "03-389 1380"
      :sahkopostiosoite "yritys@example.com"}
     :kuntaRooliKoodi               "Rakennusvalvonta-asian hakija"
     :suoramarkkinointikieltoKytkin false
     :turvakieltoKytkin             true
     :yritys
     {:liikeJaYhteisotunnus        "1060155-5"
      :nimi                        "Solita Oy"
      :postiosoitetieto            {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                                  :postinumero                "33800"
                                                  :postitoimipaikannimi       "Tuonela"
                                                  :ulkomainenLahiosoite       "katu"
                                                  :ulkomainenPostitoimipaikka "Tuonela"
                                                  :valtioKansainvalinen       "CHN"
                                                  :valtioSuomeksi             "Kiina"}}
      :puhelin                     "03-389 1380"
      :sahkopostiosoite            "yritys@example.com"
      :vainsahkoinenAsiointiKytkin true}}]
   :planners
   [{:FISEkelpoisuus          "tavanomainen pääsuunnittelu (uudisrakentaminen)"
     :FISEpatevyyskortti      "http://www.ym.fi"
     :VRKrooliKoodi           "erityissuunnittelija"
     :henkilo                 {:nimi             {:etunimi "Pena" :sukunimi "Penttilä"}
                               :osoite           {:osoitenimi                 {:teksti "katu"}
                                                  :postinumero                "33800"
                                                  :postitoimipaikannimi       "Tuonela"
                                                  :ulkomainenLahiosoite       "katu"
                                                  :ulkomainenPostitoimipaikka "Tuonela"
                                                  :valtioKansainvalinen       "CHN"
                                                  :valtioSuomeksi             "Kiina"}
                               :puhelin          "+358401234567"
                               :sahkopostiosoite "pena@example.com"}
     :kokemusvuodet           5
     :koulutus                "arkkitehti"
     :muuSuunnittelijaRooli   "ei listassa -rooli"
     :patevyysvaatimusluokka  "B"
     :suunnittelijaRoolikoodi "muu"
     :vaadittuPatevyysluokka  "C"
     :valmistumisvuosi        2010
     :yritys
     {:liikeJaYhteisotunnus "1060155-5"
      :nimi                 "Solita Oy"
      :postiosoitetieto     {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                           :postinumero                "33800"
                                           :postitoimipaikannimi       "Tuonela"
                                           :ulkomainenLahiosoite       "katu"
                                           :ulkomainenPostitoimipaikka "Tuonela"
                                           :valtioKansainvalinen       "CHN"
                                           :valtioSuomeksi             "Kiina"}}}}
    {:FISEkelpoisuus          "tavanomainen pääsuunnittelu (uudisrakentaminen)"
     :FISEpatevyyskortti      "http://www.ym.fi"
     :VRKrooliKoodi           "erityissuunnittelija"
     :henkilo                 {:nimi             {:etunimi "Pena" :sukunimi "Penttilä"}
                               :osoite           {:osoitenimi                 {:teksti "katu"}
                                                  :postinumero                "33800"
                                                  :postitoimipaikannimi       "Tuonela"
                                                  :ulkomainenLahiosoite       "katu"
                                                  :ulkomainenPostitoimipaikka "Tuonela"
                                                  :valtioKansainvalinen       "CHN"
                                                  :valtioSuomeksi             "Kiina"}
                               :puhelin          "+358401234567"
                               :sahkopostiosoite "pena@example.com"}
     :kokemusvuodet           5
     :koulutus                "muu"
     :patevyysvaatimusluokka  "AA"
     :suunnittelijaRoolikoodi "GEO-suunnittelija"
     :vaadittuPatevyysluokka  "A"
     :valmistumisvuosi        2010
     :yritys
     {:liikeJaYhteisotunnus "1060155-5"
      :nimi                 "Solita Oy"
      :postiosoitetieto     {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                           :postinumero                "33800"
                                           :postitoimipaikannimi       "Tuonela"
                                           :ulkomainenLahiosoite       "katu"
                                           :ulkomainenPostitoimipaikka "Tuonela"
                                           :valtioKansainvalinen       "CHN"
                                           :valtioSuomeksi             "Kiina"}}}}
    {:FISEkelpoisuus          "tavanomainen pääsuunnittelu (uudisrakentaminen)"
     :FISEpatevyyskortti      "http://www.ym.fi"
     :VRKrooliKoodi           "erityissuunnittelija"
     :henkilo                 {:nimi             {:etunimi "Pena" :sukunimi "Penttilä"}
                               :osoite           {:osoitenimi                 {:teksti "katu"}
                                                  :postinumero                "33800"
                                                  :postitoimipaikannimi       "Tuonela"
                                                  :ulkomainenLahiosoite       "katu"
                                                  :ulkomainenPostitoimipaikka "Tuonela"
                                                  :valtioKansainvalinen       "CHN"
                                                  :valtioSuomeksi             "Kiina"}
                               :puhelin          "+358401234567"
                               :sahkopostiosoite "pena@example.com"}
     :kokemusvuodet           5
     :koulutus                "arkkitehti"
     :patevyysvaatimusluokka  "B"
     :suunnittelijaRoolikoodi "rakennusfysikaalinen suunnittelija"
     :vaadittuPatevyysluokka  "C"
     :valmistumisvuosi        2010
     :yritys
     {:liikeJaYhteisotunnus "1060155-5"
      :nimi                 "Solita Oy"
      :postiosoitetieto     {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                           :postinumero                "33800"
                                           :postitoimipaikannimi       "Tuonela"
                                           :ulkomainenLahiosoite       "katu"
                                           :ulkomainenPostitoimipaikka "Tuonela"
                                           :valtioKansainvalinen       "CHN"
                                           :valtioSuomeksi             "Kiina"}}}}
    {:FISEkelpoisuus          "tavanomainen pääsuunnittelu (uudisrakentaminen)"
     :FISEpatevyyskortti      "http://www.ym.fi"
     :VRKrooliKoodi           "pääsuunnittelija"
     :henkilo                 {:nimi             {:etunimi "Pena" :sukunimi "Penttilä"}
                               :osoite           {:osoitenimi                 {:teksti "katu"}
                                                  :postinumero                "33800"
                                                  :postitoimipaikannimi       "Tuonela"
                                                  :ulkomainenLahiosoite       "katu"
                                                  :ulkomainenPostitoimipaikka "Tuonela"
                                                  :valtioKansainvalinen       "CHN"
                                                  :valtioSuomeksi             "Kiina"}
                               :puhelin          "+358401234567"
                               :sahkopostiosoite "pena@example.com"}
     :kokemusvuodet           5
     :koulutus                "arkkitehti"
     :patevyysvaatimusluokka  "ei tiedossa"
     :suunnittelijaRoolikoodi "pääsuunnittelija"
     :vaadittuPatevyysluokka  "AA"
     :valmistumisvuosi        2010
     :yritys
     {:liikeJaYhteisotunnus "1060155-5"
      :nimi                 "Solita Oy"
      :postiosoitetieto     {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                           :postinumero                "33800"
                                           :postitoimipaikannimi       "Tuonela"
                                           :ulkomainenLahiosoite       "katu"
                                           :ulkomainenPostitoimipaikka "Tuonela"
                                           :valtioKansainvalinen       "CHN"
                                           :valtioSuomeksi             "Kiina"}}}}]
   :foremen
   [{:VRKrooliKoodi              "ty\u00f6njohtaja"
     :alkamisPvm                 "2014-02-13"
     :henkilo                    {:nimi             {:etunimi "Pena" :sukunimi "Penttilä"}
                                  :osoite           {:osoitenimi                 {:teksti "katu"}
                                                     :postinumero                "33800"
                                                     :postitoimipaikannimi       "Tuonela"
                                                     :ulkomainenLahiosoite       "katu"
                                                     :ulkomainenPostitoimipaikka "Tuonela"
                                                     :valtioKansainvalinen       "CHN"
                                                     :valtioSuomeksi             "Kiina"}
                                  :puhelin          "+358401234567"
                                  :sahkopostiosoite "pena@example.com"}
     :kokemusvuodet              3
     :koulutus                   "muu"
     :paattymisPvm               "2014-02-20"
     :patevyysvaatimusluokka     "A"
     :sijaistettavaHlo           "Jaska Jokunen"
     :sijaistus                  {:alkamisPvm         "2014-02-13"
                                  :paattymisPvm       "2014-02-20"
                                  :sijaistettavaHlo   "Jaska Jokunen"
                                  :sijaistettavaRooli "KVV-ty\u00f6njohtaja"}
     :tyonjohtajaHakemusKytkin   true
     :tyonjohtajaRooliKoodi      "KVV-ty\u00f6njohtaja"
     :vaadittuPatevyysluokka     "A"
     :valmistumisvuosi           2010
     :valvottavienKohteidenMaara 9
     :vastattavatTyot            ["Kiinteist\u00f6n vesi- ja viemärilaitteiston rakentaminen"
                                  "Kiinteist\u00f6n ilmanvaihtolaitteiston rakentaminen"
                                  "Maanrakennusty\u00f6"
                                  "Rakennelma tai laitos"
                                  "Muu tyotehtava"]
     :yritys
     {:liikeJaYhteisotunnus "1060155-5"
      :nimi                 "Solita Oy"
      :postiosoitetieto     {:postiosoite {:osoitenimi                 {:teksti "katu"}
                                           :postinumero                "33800"
                                           :postitoimipaikannimi       "Tuonela"
                                           :ulkomainenLahiosoite       "katu"
                                           :ulkomainenPostitoimipaikka "Tuonela"
                                           :valtioKansainvalinen       "CHN"
                                           :valtioSuomeksi             "Kiina"}}}}]
   :reviews               [{:id             "123"
                            :type           "ei tiedossa"
                            :reviewer       "Reijo Revyy"
                            :date           "2012-12-03"
                            :verottajanTvLl false
                            :huomautukset   {:kuvaus    "Huomautettavaa l\u00f6ytyi."
                                             :maaraAika "2012-11-27"}
                            :lasnaolijat    "Keijo Kevyt"
                            :poikkeamat     "Poikkeava roiskehana"
                            :rakennukset    [{:jarjestysnumero     "1"
                                              :katselmusOsittainen "osittainen"
                                              :kayttoonottoKytkin  false
                                              :kiinttun            "21111111111111"
                                              :rakennusnro         "002"}
                                             {:jarjestysnumero        "3"
                                              :katselmusOsittainen    "lopullinen"
                                              :kayttoonottoKytkin     false
                                              :kiinttun               "21111111111111"
                                              :rakennusnro            "003"
                                              :valtakunnallinenNumero "1234567892"}]}]
   :state                 "submitted"
   :stateChangeTs         12345
   :createdTs             (:created application-rakennuslupa)
   :submittedTs           (:submitted application-rakennuslupa)
   :modifiedTs            (:modified application-rakennuslupa)
   ;; Note that draft statement is not present!
   :statements            [{:id       "518b3ee60364ff9a63c6d6a1"
                            :lausunto "Savupiippu pitää olla."
                            :puolto   "ehdollinen"
                            :antoTs   1368080324142
                            :antaja   {:nimi   "Sonja Sibbo"
                                       :kuvaus "Paloviranomainen"
                                       :email  "sonja.sibbo@sipoo.fi"}}]
   :araFunding            false
   :verdicts              []
   :poikkeamat            "Ei poikkeamisia"
   :newDimensions         {:kerrosala                      180.0
                           :kokonaisala                    -10.0
                           :rakennusoikeudellinenKerrosala 160.0
                           :tilavuus                       1500.0}}

  (facts "with araFunding"
    (->reporting-result
      (update application-rakennuslupa
              :documents
              (partial map #(if (= (-> % :schema-info :name)
                                   "hankkeen-kuvaus")
                              (assoc-in % [:data :rahoitus :value] true)
                              %)))
      []
      "fi")
    => (contains {:araFunding true}))

  (fact "verdicts"
    (:verdicts (->reporting-result application-rakennuslupa-with-verdicts [] "fi"))
    => [{:id             "1a156dd40e40adc8ee064463"
         :lupamaaraykset {:autopaikkojaKiinteistolla       2
                          :autopaikkojaRakennettava        3
                          :autopaikkojaRakennettu          1
                          :kokoontumistilanHenkilomaara    6
                          :maaraystieto                    ["muut lupaehdot - teksti"
                                                            "toinen teksti"]
                          :vaadittuErityissuunnitelmatieto [{:vaadittuErityissuunnitelma "Suunnitelmat"}
                                                            {:vaadittuErityissuunnitelma "Suunnitelmat2"}]
                          :vaadittuTyonjohtajatieto        ["erityisalojen ty\u00f6njohtaja"
                                                            "IV-ty\u00f6njohtaja"
                                                            "vastaava ty\u00f6njohtaja"
                                                            "KVV-ty\u00f6njohtaja"]
                          :vaaditutKatselmukset
                          [{:katselmuksenLaji                "muu katselmus"
                            :muuTunnustieto                  [{:MuuTunnus {:tunnus "" :sovellus "Lupapiste"}}]
                            :tarkastuksenTaiKatselmuksenNimi "Katselmus"}
                           {:katselmuksenLaji                "rakennuksen paikan merkitseminen"
                            :muuTunnustieto                  [{:MuuTunnus {:tunnus "" :sovellus "Lupapiste"}}]
                            :tarkastuksenTaiKatselmuksenNimi "Katselmus2"}]}
         :paivamaarat    {:aloitettavaPvm      "2022-11-23"
                          :antoPvm             "2017-11-20"
                          :julkipanoPvm        "2017-11-24"
                          :lainvoimainenPvm    "2017-11-27"
                          :viimeinenValitusPvm "2017-12-27"
                          :voimassaHetkiPvm    "2023-11-23"}
         :poytakirja     {:paatoksentekija "Pate Paattaja"
                          :paatos          "päät\u00f6s - teksti"
                          :paatoskoodi     "my\u00f6nnetty"
                          :paatospvm       "2017-11-23"
                          :pykala          "99"}}
        {:id              "5c0a6a7127093589384e7cc2"
         :kuntalupatunnus "2013-01"
         :lupamaaraykset  {:maaraystieto                    []
                           :vaadittuErityissuunnitelmatieto []
                           :vaadittuTyonjohtajatieto        []
                           :vaaditutKatselmukset            []}
         :paivamaarat     {:antoPvm "2013-09-05"}
         :poytakirja      {:paatoksentekija "elmo viranomainen"
                           :paatoskoodi     "myönnetty"
                           :paatos          "Päät\u00f6s on nyt tämä."
                           :paatospvm       "2013-09-01"
                           :pykala          "1"}}
        {:id              "5c0a6a7127093589384e7cc3"
         :kuntalupatunnus "13-0185-R"
         :lupamaaraykset  {:autopaikkojaEnintaan            10
                           :autopaikkojaKiinteistolla       7
                           :autopaikkojaRakennettava        2
                           :autopaikkojaRakennettu          0
                           :autopaikkojaUlkopuolella        3
                           :autopaikkojaVahintaan           1
                           :kokoontumistilanHenkilomaara    15
                           :kerrosala                       100
                           :kokonaisala                     110
                           :maaraystieto                    ["Radontekninen suunnitelma"
                                                             "Ilmanvaihtosuunnitelma"]
                           :rakennusoikeudellinenKerrosala  100
                           :vaadittuErityissuunnitelmatieto []
                           :vaadittuTyonjohtajatieto        ["vastaava työnjohtaja"
                                                             "ei tiedossa"
                                                             "työnjohtaja"]
                           :vaaditutKatselmukset
                           [{:katselmuksenLaji                "aloituskokous"
                             :muuTunnustieto                  [{:MuuTunnus {:tunnus "" :sovellus "Lupapiste"}}]
                             :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}
                            {:katselmuksenLaji                "muu tarkastus"
                             :muuTunnustieto                  [{:MuuTunnus {:tunnus "" :sovellus "Lupapiste"}}]
                             :tarkastuksenTaiKatselmuksenNimi "Käytt\u00f6\u00f6nottotarkastus"}]}
         :paivamaarat     {:aloitettavaPvm   "2013-09-01"
                           :antoPvm          "2013-09-05"
                           :julkipanoPvm     "2013-09-07"
                           :lainvoimainenPvm "2013-09-02"
                           :raukeamisPvm     "2013-09-04"
                           :voimassaHetkiPvm "2013-09-03"}
         :poytakirja      {:paatoksentekija "johtava viranomainen"
                           :paatoskoodi     "myönnetty"
                           :paatos          "Päät\u00f6steksti avaimen :paatos alla"
                           :paatospvm       "2013-09-01"}}])

  (fact "rakennus"
    (-> (->reporting-result application-rakennuslupa-with-verdicts [] "fi")
        :operations (get 0) :rakennus)
    =>
    (contains {:location-etrs-tm35fin [408048.1 6693225.1]
               :location-wgs84        [25.33295 60.36502]}))

  (fact "reviews with operation ID in building (from buildings array)"
    (let [result (->reporting-result (assoc application-rakennuslupa-with-tasks
                                            :buildings
                                            [{:propertyId     "21111111111111"
                                              :nationalId     "1234567893"
                                              :localShortId   "002"
                                              :description    "Eri talo"
                                              :location       [408048.1 6693225.1]
                                              :location-wgs84 [25.33295 60.36502]
                                        ; if there is operationId defined for building, it will be MuuTunnus
                                        ; in generated KuntaGML review canonical -> ID is now added to reporting result
                                              :operationId    (get-in application-rakennuslupa [:primaryOperation :id])}])
                                     []
                                     "fi")]
      (fact "valid result"
        result => map?)
      (fact "building has toimenpideId"
        (->> (:reviews result)
             first
             :rakennukset
             (util/find-first #(= "002" (:rakennusnro %))))
        => (contains {:toimenpideId (get-in application-rakennuslupa [:primaryOperation :id])})))))

(defn mitat [kala rakala koala tila]
  {:mitat (util/assoc-when {}
                           :kerrosala kala
                           :rakennusoikeudellinenKerrosala rakala
                           :kokonaisala koala
                           :tilavuus tila)})

(fact "dimensions"
  (let [new-build1 {:schema-info {:name "uusiRakennus"}
                    :data        (mitat 1 2 3 4)}
        new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                    :data        (mitat 10 12 13 14)}
        extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                    :data        (assoc (mitat 1 2 3 4)
                                        :laajennuksen-tiedot (mitat 101 102 103 104))}
        extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                    :data        (assoc (mitat 1 2 3 4)
                                        :laajennuksen-tiedot (mitat 201 202 203 204))}
        purku      {:schema-info {:name "purkaminen"}
                    :data (mitat 1 2 3 4)}
        skip       {:schema-info {:name "onpahanVaanRakennus"}
                    :data        (mitat 1 2 3 4)}]
    (new-dimensions [new-build1 new-build2 extend1 extend2 skip])
    => {:kerrosala                      313.0 ; (+ 1 10 101 201)
        :rakennusoikeudellinenKerrosala 318.0 ; (+ 2 12 102 202)
        :kokonaisala                    322.0 ; (+ 3 13 103 203)
        :tilavuus                       326.0 ; (+ 4 14 104 204)
        }
    (new-dimensions [new-build1 new-build2 extend1
                     extend2 skip new-build1 new-build2
                     extend1 extend2 skip])
    => {:kerrosala                      626.0
        :rakennusoikeudellinenKerrosala 636.0
        :kokonaisala                    644.0
        :tilavuus                       652.0}
    (new-dimensions [skip]) => {}
    (new-dimensions [purku])
    => {:kerrosala -1.0
        :rakennusoikeudellinenKerrosala -2.0
        :kokonaisala -3.0
        :tilavuus -4.0}))

(fact "missing dimensions"
  (let [new-build1 {:schema-info {:name "uusiRakennus"}
                    :data        (mitat nil 2 3 4)}
        new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                    :data        (mitat 10 nil 13 14)}
        extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                    :data        (assoc (mitat 1 2 3 4)
                                        :laajennuksen-tiedot (mitat 101 102 nil 104))}
        extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                    :data        (assoc (mitat 1 2 3 4)
                                        :laajennuksen-tiedot (mitat 201 202 203 nil))}]
    (new-dimensions [new-build1 new-build2 extend1 extend2])
    => {:kerrosala                      312.0
        :rakennusoikeudellinenKerrosala 306.0
        :kokonaisala                    219.0
        :tilavuus                       122.0}))

(fact "bad dimensions"
  (let [new-build1 {:schema-info {:name "uusiRakennus"}
                    :data        (mitat "Bad" 2 3 4)}
        new-build2 {:schema-info {:name "uusi-rakennus-ei-huoneistoa"}
                    :data        (mitat 10 "to" 13 14)}
        extend1    {:schema-info {:name "rakennuksen-laajentaminen"}
                    :data        (assoc (mitat 1 2 3 4)
                                        :laajennuksen-tiedot (mitat 101 102 "the" 104))}
        extend2    {:schema-info {:name "rakennuksen-laajentaminen-ei-huoneistoja"}
                    :data        (assoc (mitat 1 2 3 4)
                                        :laajennuksen-tiedot (mitat 201 202 203 "bone"))}]
    (new-dimensions [new-build1 new-build2 extend1 extend2])
    => {:kerrosala                      312.0
        :rakennusoikeudellinenKerrosala 306.0
        :kokonaisala                    219.0
        :tilavuus                       122.0}))


(facts "muu->value"
  (let [no-muu {:foo "faa" :tiedot {:jaa "joo"}}
        muu    {:foo "faa" :tiedot {:fuu "jee"}}]
    (fact "no change"
      (muu->value no-muu [:tiedot] :not-found :new)
      => no-muu)
    (fact "key swapped"
      (muu->value muu [:tiedot] :fuu :new)
      => {:foo "faa" :tiedot {:new "jee"}})))

(facts "YA application"
  (let [result (->reporting-result kaivu/kaivulupa-application-with-review [] "fi")]
    (fact "no errors" =not=> nil)
    (fact "kuvaus"
      (:projectDescription result) => "YA Hankkeen kuvaus.")
    (fact "varattava pinta-ala"
      (:reservedArea result) => "333")
    (fact "sijoitusluvan tunniste"
      (:placementPermit result) => "LP-753-2013-00002")
    (fact "tyoaika"
      (:workDates result)
      => {:startDate "2013-09-18"
          :endDate   "2013-09-22"})
    (facts "features (drawings)"
      (:features result)
      => [{:geometry {:coordinates ['(27.60669052398 62.879528515014)
                                    '(27.607676695204 62.87991471303)
                                    '(27.607467431943 62.88001659168)
                                    '(27.606584939918 62.879652385577)
                                    '(27.606690570304 62.879530758748)]
                      :type "LineString"}
           :id 1
           :properties {:desc "alue 1" :height 1000.0 :length 1111.0 :name "alue"}
           :type "Feature"}
          {:geometry {:coordinates ['(27.606078028474 62.879861007095)
                                    '(27.607232107692 62.880046776375)
                                    '(27.60651862951 62.879773843204)
                                    '(27.606071910145 62.879802691132)]
                      :type "LineString"}
           :id 2
           :properties {:desc "Viiiva" :height 111.0 :length 1134.0 :name "Viiva"}
           :type "Feature"}
          {:geometry {:coordinates '(27.606593693788 62.880076451297) :type "Point"}
           :id 3
           :properties {:desc "Piste jutska" :height 345.0 :name "Piste"}
           :type "Feature"}
          {:geometry {:coordinates [['(27.606753990234 62.880221617311)
                                     '(27.606302634739 62.88002609259)
                                     '(27.607129353101 62.880069658157)
                                     '(27.606753990234 62.880221617311)]]
                      :type "Polygon"}
           :id 4
           :properties {:area 402.0
                        :desc "Alueen kuvaus"
                        :height 333.0
                        :name "Alueen nimi"}
           :type "Feature"}])
    (facts "reviews"
      (:reviews result)
      => (just [(contains
                  {:date           "1974-05-01"
                   :huomautukset   {:kuvaus        "huomautus - kuvaus"
                                    :maaraAika     "1974-06-02"
                                    :toteaja       "huomautus - viranomaisen nimi"
                                    :toteamisHetki "1974-05-02"}
                   :lasnaolijat    "paikallaolijat"
                   :poikkeamat     "jotain poikkeamia oli"
                   :rakennukset    []
                   :reviewer       "Viranomaisen nimi"
                   :type           "Muu valvontakäynti"
                   :verottajanTvLl nil})]))))
