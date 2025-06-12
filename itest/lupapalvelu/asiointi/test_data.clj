(ns lupapalvelu.asiointi.test-data
  (:require [sade.util :as util]))

;; Similar to `lupapalvelu.submit-itest`
(defn- make-updates
  [m]
  (-> (util/kw-flatten-map m)
      (update-keys name)))

(defn- test-person
  [prefix]
  {:henkilotiedot {:etunimi  (str prefix ":" "Etunimi")
                   :hetu     "010106A923S"
                   :sukunimi (str prefix ":" "Sukunimi")}
   :osoite        {:katu                 "Testikatu 12"
                   :maa                  "FIN"
                   :postinumero          "33720"
                   :postitoimipaikannimi "Tampere"}
   :yhteystiedot  {:email   "testi@lupapiste.fi"
                   :puhelin "0501234567"}})

;;; Example forms

(def hakija
  (make-updates
    {:_selected "henkilo"
     :henkilo   (-> (test-person "H")
                    (assoc-in [:yhteystiedot :email] "pena@example.com"))}))

(def hankkeen-kuvaus
  (make-updates
    {:kuvaus     "tehdään jotain"
     :poikkeamat "en kerro"}))

(def laajennettuRakennusjateselvitys-v2
  (make-updates
    {}))

(def maksaja
  (make-updates
    {:_selected  "henkilo"
     :henkilo    (test-person "M")
     :laskuviite "123"}))

(def paasuunnittelija
  (make-updates
    (merge (test-person "PS")
           {:patevyys                           {:fise                "http://example.com"
                                                 :fiseKelpoisuus      "poikkeuksellisen vaativa akustiikkasuunnittelu (korjausrakentaminen)"
                                                 :kokemus             "10"
                                                 :koulutusvalinta     "kirvesmies"
                                                 :patevyydentoteaja   "kiwa"
                                                 :patevyys            "aivan mielettömän mukava jätkä"
                                                 :patevyysluokka      "B"
                                                 :patevyystodistusurl "https://example.com/patevyys"
                                                 :patevyysvoimassapvm "1.1.2070"
                                                 :valmistumisvuosi    "2020"}
            :suunnittelutehtavanVaativuusluokka "A"
            :vastuu                             {:vastuunAlkamisPvm   "1.1.2030"
                                                 :vastuunPaattymisPvm "31.12.2049"}})))

(def paatoksen-toimitus-rakval
  (make-updates
    {:henkilotiedot {:etunimi  "PT:Etunimi"
                     :sukunimi "PT:Sukunimi"}
     :osoite        {:katu                 "Testikatu 12"
                     :maa                  "FIN"
                     :postinumero          "33720"
                     :postitoimipaikannimi "Tampere"}}))

(def rakennuspaikka
  (make-updates
    {:hallintaperuste      "oma"
     :hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm "5.12.2020"}
     :huleveden-kasittely  "imeyttaminen"
     :kaavatilanne         "asemakaava"
     :kiinteisto           {:maaraalaTunnus "123"}}))

(def poikkeusasian-rakennuspaikka
  (make-updates
    {:hallintaperuste     "oma"
     :huleveden-kasittely "imeyttaminen"
     :kaavatilanne        "asemakaava"
     :kiinteisto          {:maaraalaTunnus "123"
                           :rantaKytkin    true}}))

(def suunnittelija
  (make-updates
    (merge (test-person "S")
           {:kuntaRoolikoodi                    "kantavien rakenteiden suunnittelija"
            :patevyys                           {:fise                "http://example.com"
                                                 :fiseKelpoisuus      "poikkeuksellisen vaativa akustiikkasuunnittelu (korjausrakentaminen)"
                                                 :kokemus             "10"
                                                 :koulutusvalinta     "kirvesmies"
                                                 :patevyydentoteaja   "kiwa"
                                                 :patevyys            "aivan mielettömän mukava jätkä"
                                                 :patevyysluokka      "B"
                                                 :patevyystodistusurl "https://example.com/patevyys"
                                                 :patevyysvoimassapvm "1.1.2070"
                                                 :valmistumisvuosi    "2020"}
            :suunnittelutehtavanVaativuusluokka "A"
            :vastuu                             {:vastuunAlkamisPvm   "1.1.2030"
                                                 :vastuunPaattymisPvm "31.12.2049"}})))

(def uusi-rakennuskohde-rakennus-minimal
  (make-updates
    ;; Just choose the `kohteen-laji` -> `:building` gets populated
    {:kaytto {:kohteen-laji               "rakennus"
              :nimi                       "minun taloni"
              :rakennusluokka             "0110 omakotitalot"
              :valiaikainenRakennusKytkin true}}))

(def uusi-rakennuskohde-rakennus-full
  (make-updates
    {:autopaikat                   {:kiinteiston              "1"
                                    :kiinteiston-ulkopuoliset "2"
                                    :rakennetut               "3"}
     :kaytto                       {:kohteen-laji               "rakennus"
                                    :nimi                       "minun taloni"
                                    :rakennusluokka             "0110 omakotitalot"
                                    :valiaikainenRakennusKytkin true}
     :latauspiste                  {:esikaapelointi           true
                                    :latauspisteidenMaara     "2"
                                    :muutJohtoreitit          true
                                    :polkupyorapaikkojenMaara "3"
                                    :putkitus                 true}
     :rakennuksenOmistajat         {:0 {:_selected    "henkilo"
                                        :omistajalaji "kiinteistö oy"
                                        :henkilo      (test-person "RO")}}
     :rakennuskohteen-osa          {:energiatiedot    {:jaahdytysenergianlahde   {:0 {:is-primary                   true
                                                                                      :jaahdytysenergianlahdetyyppi "kompressori-kylmalaitos-ilmalauhdutteinen"}}
                                                       :jaahdytystapa            {:0 {:is-primary          true
                                                                                      :jaahdytystapatyyppi "ilmajaahdytteinen"}}
                                                       :lammitystapa             {:0 {:is-primary         true
                                                                                      :lammitystapatyyppi "vesikeskus"}}
                                                       :lammonlahde              {:0 {:is-primary        true
                                                                                      :lammonlahdetyyppi "kauko tai aluelämpö"}}
                                                       :sahkoenergianlahde       {:0 {:is-primary               true
                                                                                      :sahkoenergianlahdetyyppi "sahkoliittyma"}}
                                                       :energialuokka            "A"
                                                       :energialuokan-alaindeksi "2018"}
                                    :hissit           {:0 {:hissin-tiedot {:elinkaaren-vaihe "suunnitteilla"
                                                                           :hissin-laji      "henkilohissi"
                                                                           :nimi             "eka hissi"
                                                                           :palomieshissi    "yes"}
                                                           :kapasiteetti  {:henkilomaara "5"
                                                                           :kantavuus    "100"}
                                                           :mitat         {:oviaukon-korkeus "200"
                                                                           :oviaukon-leveys  "300"
                                                                           :sisaleveys       "400"
                                                                           :sisapituus       "500"}}}
                                    :huoneistot       {:0 {:huoneistoTyyppi            "asuinhuoneisto"
                                                           :huoneistoala               "4.123"
                                                           :huoneistonumero            "1"
                                                           :huoneluku                  "2"
                                                           :jakokirjain                "b"
                                                           :keittionTyyppi             "keittio"
                                                           :lammin-vesi                "8"
                                                           :parveke                    "7"
                                                           :porras                     "a"
                                                           :sauna                      "6"
                                                           :sijaintikerroksen-tarkenne "ullakko"
                                                           :sijaintikerros             "3"
                                                           :suihku                     "9"
                                                           :wc                         "5"}}
                                    :kayttotiedot     {:kayttoika       "1"
                                                       :kayttajamaara   "3"
                                                       :kayttotarkoitus {:0 {:is-primary true
                                                                             :tyyppi     "012 kahden asunnon talot"}}}
                                    :kokoontumistilat {:0 {:nimi         "kokoontumistila 1"
                                                           :henkilomaara "2"}}
                                    :materiaalitiedot {:laajarunkoinen-rakennus true
                                                       :rakentamistapa          "elementti"
                                                       :paloluokka              "P0"
                                                       :julkisivu               {:0 {:is-primary          true
                                                                                     :julkisivumateriaali "betoni"}}
                                                       :kantavaRakennusaine     {:0 {:is-primary   true
                                                                                     :rakennusaine "betoni"}}}
                                    :sisaankaynti     {:0 {:esteeton       true
                                                           :hyokkausreitti true
                                                           :is-primary     true
                                                           :sijaintipiste  {:pohjoiskoordinaatti "60"
                                                                            :itakoordinaatti     "40"}
                                                           :tila           {:laji             "paasisaankaynti-porrashuoneeseen"
                                                                            :elinkaaren-vaihe "suunnitteilla"}}}
                                    :sisatilat        {:huoneistoala "20"
                                                       :ullakkoala   "21"
                                                       :kellariala   "22"}
                                    :sisaverkko       {:antenniverkko                       true
                                                       :olemassaOlevaValokuituverkko        true
                                                       :yleiskaapeloinninOptinenKaapelointi true
                                                       :yleiskaapeloinninParikaapelointi    true}
                                    :tekniset-tiedot  {:jatevesienkasittely   "verkostoliittyma"
                                                       :talousvesityyppi      "oma-kaivo"
                                                       :varuste               {:0 {:varustetyyppi "aurinkopaneeli"
                                                                                   :maara         "1"}}
                                                       :verkostoliittymat     {:0 {:liitetty               true
                                                                                   :verkostoliittymatyyppi "kaasu"}}
                                                       :ilmanvaihtotavan-laji {:0 {:is-primary        true
                                                                                   :ilmanvaihtotyyppi "painovoimainen-ilmanvaihto"}}}
                                    :ulkokuorentiedot {:kerrosala                    "11"
                                                       :tilavuus                     "10"
                                                       :kokonaisala                  "12"
                                                       :kerrosluku                   "13"
                                                       :korkeus                      "14"
                                                       :suhde-maan-pintaan           "kokonaan-pinnan-ylla"
                                                       :rakennusoikeusValinta        "harjakorkeus"
                                                       :rakennusoikeusKuutiometreina "15"
                                                       :harjanKorkeusrakennusalalla  "16"}
                                    :vaestonsuoja     {:0 {:kapasiteetti "1"
                                                           :luokka       "raskaat-terasbetonisuojat-A"
                                                           :nimi         "suoja"
                                                           :suoja-ala    "2"
                                                           :yhteinen     true}}}
     :rakennusluokka-vahahiilisyys {:rakennusluokka-vahahiilisyys "rivitalo"}
     :sijaintitiedot               {:osoite   {:katu        "joku katu"
                                               :postinumero "33720"}
                                    :sijainti {:hallinnollinen-sijainti-yksikko.kiinteistotunnus "753-423-12-1"}}}))

(def uusi-rakennuskohde-etvra-full
  (make-updates
    {:autopaikat           {:kiinteiston              "1"
                            :kiinteiston-ulkopuoliset "2"
                            :rakennetut               "3"}
     :kaytto               {:kohteen-laji               "etvra"
                            :erityista-toimintaa-tyyppi "kaatopaikka"
                            :nimi                       "minun kaatopaikkani"
                            :rakentaja-tyyppi           true
                            :valiaikainenRakennusKytkin true}
     :kuvaus               "Suomen hienoin kaatopaikka"
     :latauspiste          {:esikaapelointi           true
                            :latauspisteidenMaara     "2"
                            :muutJohtoreitit          true
                            :polkupyorapaikkojenMaara "3"
                            :putkitus                 true}
     :mitat                {:pintaAla "10"}
     :rakennuksenOmistajat {:0 {:_selected    "henkilo"
                                :omistajalaji "kiinteistö oy"
                                :henkilo      (test-person "RO")}}
     ;; NOTE: LPK-8355: ETVRA should not have `rakennuskohteen-osa` at all
     :rakennuskohteen-osa  {:kokoontumistilat {:0 {:nimi         "kokoontumistila 1"
                                                   :henkilomaara "2"}}}
     :sijaintitiedot       {:osoite   {:katu        "joku katu"
                                       :postinumero "33720"}
                            :sijainti {:hallinnollinen-sijainti-yksikko.kiinteistotunnus "753-423-12-1"}}}))

(def uusi-rakennuskohde-rakennelma-full
  (make-updates
    {:autopaikat           {:kiinteiston              "1"
                            :kiinteiston-ulkopuoliset "2"
                            :rakennetut               "3"}
     :kaytto               {:kohteen-laji                "rakennelma"
                            :rakennelman-kayttotarkoitus "Hyppyrimäki"
                            :nimi                        "Hervannan hyppyrimäki"
                            :rakentaja-tyyppi            true
                            :valiaikainenRakennusKytkin  true}
     :kuvaus               "Hervannan hienoin hyppyrimäki"
     :latauspiste          {:esikaapelointi           true
                            :latauspisteidenMaara     "2"
                            :muutJohtoreitit          true
                            :polkupyorapaikkojenMaara "3"
                            :putkitus                 true}
     :mitat                {:pintaAla "10"}
     :rakennuksenOmistajat {:0 {:_selected    "henkilo"
                                :omistajalaji "kiinteistö oy"
                                :henkilo      (test-person "RO")}}
     :rakennuskohteen-osa  {:energiatiedot    {:jaahdytysenergianlahde              {:0 {:is-primary                   true
                                                                                         :jaahdytysenergianlahdetyyppi "kompressori-kylmalaitos-ilmalauhdutteinen"}}
                                               :jaahdytystapa                       {:0 {:is-primary          true
                                                                                         :jaahdytystapatyyppi "ilmajaahdytteinen"}}
                                               :lammitystapa                        {:0 {:is-primary         true
                                                                                         :lammitystapatyyppi "vesikeskus"}}
                                               :lammonlahde                         {:0 {:is-primary        true
                                                                                         :lammonlahdetyyppi "kauko tai aluelämpö"}}
                                               :sahkoenergianlahde                  {:0 {:is-primary               true
                                                                                         :sahkoenergianlahdetyyppi "sahkoliittyma"}}
                                               :energialuokka                       "A"
                                               :energialuokan-alaindeksi            "2018"
                                               :laskennallinen-ostoenergian-kulutus {:ostoenergian-laji     "uusiutuva-polttoaine"
                                                                                     :energiamaara-vuodessa "1"}}
                            :kayttotiedot     {:kayttoika       "1"
                                               :kayttajamaara   "3"
                                               :kayttotarkoitus {:0 {:is-primary true
                                                                     :tyyppi     "012 kahden asunnon talot"}}}
                            :kokoontumistilat {:0 {:nimi         "kokoontumistila 1"
                                                   :henkilomaara "2"}}
                            :materiaalitiedot {:laajarunkoinen-rakennus true
                                               :rakentamistapa          "elementti"
                                               :paloluokka              "P0"
                                               :julkisivu               {:0 {:is-primary          true
                                                                             :julkisivumateriaali "betoni"}}
                                               :kantavaRakennusaine     {:0 {:is-primary   true
                                                                             :rakennusaine "betoni"}}}
                            :sisaankaynti     {:0 {:esteeton       true
                                                   :hyokkausreitti true
                                                   :is-primary     true
                                                   :sijaintipiste  {:pohjoiskoordinaatti "60"
                                                                    :itakoordinaatti     "40"}
                                                   :tila           {:laji             "paasisaankaynti-porrashuoneeseen"
                                                                    :elinkaaren-vaihe "suunnitteilla"}}}
                            :sisatilat        {:huoneistoala "20"
                                               :ullakkoala   "21"
                                               :kellariala   "22"}
                            :sisaverkko       {:antenniverkko                       true
                                               :olemassaOlevaValokuituverkko        true
                                               :yleiskaapeloinninOptinenKaapelointi true
                                               :yleiskaapeloinninParikaapelointi    true}
                            :tekniset-tiedot  {:jatevesienkasittely   "verkostoliittyma"
                                               :talousvesityyppi      "oma-kaivo"
                                               :varuste               {:0 {:varustetyyppi "aurinkopaneeli"
                                                                           :maara         "1"}}
                                               :verkostoliittymat     {:0 {:liitetty               true
                                                                           :verkostoliittymatyyppi "kaasu"}}
                                               :ilmanvaihtotavan-laji {:0 {:is-primary        true
                                                                           :ilmanvaihtotyyppi "painovoimainen-ilmanvaihto"}}}
                            :ulkokuorentiedot {:kerrosala                    "11"
                                               :tilavuus                     "10"
                                               :kokonaisala                  "12"
                                               :kerrosluku                   "13"
                                               :korkeus                      "14"
                                               :suhde-maan-pintaan           "kokonaan-pinnan-ylla"
                                               :rakennusoikeusValinta        "harjakorkeus"
                                               :rakennusoikeusKuutiometreina "15"
                                               :harjanKorkeusrakennusalalla  "16"}
                            :vaestonsuoja     {:0 {:kapasiteetti "1"
                                                   :luokka       "raskaat-terasbetonisuojat-A"
                                                   :nimi         "suoja"
                                                   :suoja-ala    "2"
                                                   :yhteinen     true}}}
     :sijaintitiedot       {:osoite   {:katu        "joku katu"
                                       :postinumero "33720"}
                            :sijainti {:hallinnollinen-sijainti-yksikko.kiinteistotunnus "753-423-12-1"}}}))

(def rakennushanke
  (make-updates
    {:kaytettykerrosala    {:pintaAla             "12"
                            :kayttotarkoitusKoodi "011 yhden asunnon talot"}
     :rakennuksenOmistajat {:0 {:_selected    "henkilo"
                                :omistajalaji "kiinteistö oy"
                                :henkilo      (test-person "RO")}}
     :sijaintitiedot       {:osoite   {:katu          "joku katu"
                                       :osoitenumero  "2"
                                       :osoitenumero2 "3"
                                       :postinumero   "33720"}
                            :sijainti {:hallinnollinen-sijainti-yksikko {:kiinteistotunnus "753-423-12-1"}}}
     :toimenpiteet         {:kayttotarkoitus "012 kahden asunnon talot"
                            :Toimenpide      "käyttötarkoitusmuutos"
                            :huoneistoja     "2"
                            :kerroksia       "3"
                            :kerrosala       "4"
                            :kokonaisala     "5"}}))

(def suunnittelutarveratkaisun-lisaosa
  (make-updates
    {:kaavoituksen_ja_alueiden_tilanne {:asemakaavaluonnos  true
                                        :rajoittuuko_tiehen true
                                        :tienkayttooikeus   true
                                        :yleiskaavaa        true
                                        :vesijohto          true
                                        :viemarijohto       true}
     :luonto_ja_kulttuuri              {:kulttuurisesti_merkittava true
                                        :suojelukohteita           true}
     :maisema                          {:metsan_reunassa                  true
                                        :metsassa                         true
                                        :nykyisen_rakennuspaikan_vieressa true
                                        :pellolla                         true
                                        :vanhalla_rakennuspaikalla        true}
     :merkittavyys                     {:rakentamisen_vaikutusten_merkittavyys "paljon"}
     :muut_vaikutukset                 {:etaisyys_viemariverkosta      "1"
                                        :liitytaanko_viemariverkostoon true
                                        :pohjavesialuetta              true}
     :vaikutukset_yhdyskuntakehykselle {:etaisyys_alakouluun                 "1"
                                        :etaisyys_kauppaan                   "2"
                                        :etaisyys_kuntakeskuksen_palveluihin "3"
                                        :etaisyys_paivakotiin                "4"
                                        :etaisyys_ylakouluun                 "5"
                                        :muita_vaikutuksia                   "kaikenlaista"
                                        :turvallinen_polkupyoratie_kouluun   true}
     :virkistys_tarpeet                {:ulkoilu_ja_virkistysaluetta_varattu true
                                        :vaikeuttaako_ulkoilureittia         true
                                        :virkistysalueella                   true}}))

(def tyonjohtaja-v3
  (make-updates
    (-> (test-person "TJ")
        (assoc-in [:yhteystiedot :email] "pena@example.com")
        (merge {:kuntaRoolikoodi        "työnjohtaja"
                :patevyys-tyonjohtaja   {:kokemusvuodet              "1"
                                         :koulutusvalinta            "diplomi-insinööri"
                                         :patevyydentoteaja          "seti" ;; 👽
                                         :patevyystodistusurl        "https://example.com"
                                         :patevyysvoimassapvm        "1.1.2040"
                                         :valmistumisvuosi           "1970"
                                         :valvottavienKohteidenMaara "2"}
                :patevyysvaatimusluokka "A"
                :tyonjohtajaHanketieto  {:hankeKesto     "10"
                                         :kayntienMaara  "12"
                                         :kaytettavaAika "11"}
                :vastattavatTyotehtavat {:rakennuksenMuutosJaKorjaustyo true}}))))
