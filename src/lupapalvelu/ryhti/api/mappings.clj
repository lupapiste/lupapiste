(ns lupapalvelu.ryhti.api.mappings
  (:require [sade.strings :as ss]
            [sade.util :as su]
            [schema.core :as sc]))

;; NOTE: Use http://uri.suomi.fi/ URIs for the codesets,
;; instead of the UI address that will redirect to.

(def TEST-API-DOC
  "Suomi.fi Testiliityntäkatalogista poimittu OpenAPI service.json URI."
  "https://liityntakatalogi.test.suomi.fi/dataset/FI-TEST.GOV.0996189-5.Ryhti-Syke-service/resource/8b52edc0-fb92-44b5-a53a-395c6ef8a550/download/service.json")

(def API-DOC
  "Suomi.fi (tuotanto)liityntäkatalogista poimittu OpenAPI service.json URI."
  "https://liityntakatalogi.suomi.fi/dataset/FI.GOV.0996189-5.Ryhti-Syke-Service/resource/71eb272d-77d4-4891-9a8e-0db45e80e5d1/download/service.json")

;; NOTE: Ryhti has decided that they won't publish the whole production API until
;; municipalities actually use Ryhti. So for now we have to rely on the test api.
(def CURRENT-API TEST-API-DOC)

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi"}
  building-control-case
  "This code describes the building control case type"
  {:building       "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi/code/01"
   :demolition     "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi/code/02"
   :deviation      "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi/code/03"
   :landscape-work "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi/code/04"})

(def BuildingControlCase (apply sc/enum (keys building-control-case)))

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/Suhde_pintaan"}
  suhde-pintaan
  "Koodistolla kuvataan, sijoittuuko rakennuskohde maan pinnan ylle vai alle."
  {:kokonaan-pinnan-ylla              "http://uri.suomi.fi/codelist/rytj/Suhde_pintaan/code/01"
   :olennaisesti-kokonaan-pinnan-ylla "http://uri.suomi.fi/codelist/rytj/Suhde_pintaan/code/02"
   :pinnan-ylla-ja-alla               "http://uri.suomi.fi/codelist/rytj/Suhde_pintaan/code/03"
   :olennaisesti-kokonaan-pinnan-alla "http://uri.suomi.fi/codelist/rytj/Suhde_pintaan/code/04"
   :kokonaan-pinnan-alla              "http://uri.suomi.fi/codelist/rytj/Suhde_pintaan/code/05"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde"}
  sahkoenergianlahde
  {:sahkoliittyma       "http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde/code/01"
   :vesivoima           "http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde/code/02"
   :kaukolampo          "http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde/code/03"
   :aurinkosahko        "http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde/code/04"
   :dieselvoima         "http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde/code/05"
   :sahkoenergiavarasto "http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde/code/06"
   :muu                 "http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/energialuokkaAlaindeksi"}
  energialuokka-alaindeksi
  {:2007 "http://uri.suomi.fi/codelist/rytj/energialuokkaAlaindeksi/code/01"
   :2013 "http://uri.suomi.fi/codelist/rytj/energialuokkaAlaindeksi/code/02"
   :2018 "http://uri.suomi.fi/codelist/rytj/energialuokkaAlaindeksi/code/03"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka"}
  energiatehokkuusluokka
  {:A           "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/01"
   :B           "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/02"
   :C           "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/03"
   :D           "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/04"
   :E           "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/05"
   :F           "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/06"
   :G           "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/07"
   :ei-vaadittu "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/08"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/jaahdytystapa"}
  jaahdytystapa
  {:ilmajaahdytteinen             "http://uri.suomi.fi/codelist/rytj/jaahdytystapa/code/01"
   :vesijaahdytteinen             "http://uri.suomi.fi/codelist/rytj/jaahdytystapa/code/02"
   :ei-kiinteaa-jaahdytyslaitetta "http://uri.suomi.fi/codelist/rytj/jaahdytystapa/code/03"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/jaahdytys"}
  jaahdytysenergianlahde
  {:kompressori-kylmalaitos-ilmalauhdutteinen "http://uri.suomi.fi/codelist/rytj/jaahdytys/code/01"
   :kompressori-kylmalaitos-vesilauhdutteinen "http://uri.suomi.fi/codelist/rytj/jaahdytys/code/02"
   :vapaajaahdytys-liuosjaahdytin             "http://uri.suomi.fi/codelist/rytj/jaahdytys/code/03"
   :vapaajaahdytys-jaahdytystorni             "http://uri.suomi.fi/codelist/rytj/jaahdytys/code/04"
   :vapaajaahdytys-maaputkisto                "http://uri.suomi.fi/codelist/rytj/jaahdytys/code/05"
   :split-laitteet                            "http://uri.suomi.fi/codelist/rytj/jaahdytys/code/06"
   :kaukojaahdytys-lammonsiirrin              "http://uri.suomi.fi/codelist/rytj/jaahdytys/code/07"
   :absorptiojaahdytys                        "http://uri.suomi.fi/codelist/rytj/jaahdytys/code/08"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/lammitystapa"}
  lammitystapa
  "These codes contains ä and/or ö character to retain a 1:1 mapping with the older KuntaGML values"
  {:vesikeskus      "http://uri.suomi.fi/codelist/rytj/lammitystapa/code/01"
   :ilmakeskus      "http://uri.suomi.fi/codelist/rytj/lammitystapa/code/02"
   :suora-sähkö     "http://uri.suomi.fi/codelist/rytj/lammitystapa/code/03"
   :uuni            "http://uri.suomi.fi/codelist/rytj/lammitystapa/code/04"
   :aurinkolämmitys "http://uri.suomi.fi/codelist/rytj/lammitystapa/code/05"
   :ilmalämpöpumppu "http://uri.suomi.fi/codelist/rytj/lammitystapa/code/06"
   :ei-lämmitystä   "http://uri.suomi.fi/codelist/rytj/lammitystapa/code/07"
   :muu             "http://uri.suomi.fi/codelist/rytj/lammitystapa/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde"}
  lammonlahde
  "These codes contains ä and/or ö character to retain a 1:1 mapping with the older KuntaGML values"
  {:kauko-tai-aluelämpö   "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/01"
   :kevyt-polttoöljy      "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/02"
   :raskas-polttoöljy     "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/03"
   :sahkö                 "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/04"
   :kaasu                 "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/05"
   :kivihiili-koksi       "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/06"
   :puu                   "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/07"
   :turve                 "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/08"
   :maalämpö              "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/09"
   :aurinkoenergia        "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/10"
   :lämpöpumppu           "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/11"
   :maalämpopumppu        "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/1101"
   :ilmalämpöpumppu       "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/1102"
   :poistoilmalämpöpumppu "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/1103"
   :ilma-vesilämpöpumppu  "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/1104"
   :muu-lammonlahde       "http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa"}
  rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa
  "This code describes the building use in carbon assessment"
  {:rivitalo                          "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/1"
   :asuinkerrostalo                   "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/2"
   :toimistorakennus-ja-terveyskeskus "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/3"
   :liikerakennus-ym                  "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/4"
   :majoitusliikerakennus-ym          "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/5"
   :opetusrakennus-ja-paivakoti       "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/6"
   :liikuntahalli                     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/7"
   :sairaala                          "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/8"
   :varastorakennus-ym                "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/9"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/talousvesi"}
  talousvesityyppi
  {:verkostoliittymat "http://uri.suomi.fi/codelist/rytj/talousvesi/code/01"
   :oma-kaivo         "http://uri.suomi.fi/codelist/rytj/talousvesi/code/02"
   :rengaskaivo       "http://uri.suomi.fi/codelist/rytj/talousvesi/code/0201"
   :porakaivo         "http://uri.suomi.fi/codelist/rytj/talousvesi/code/0202"
   :siivilaputkikaivo "http://uri.suomi.fi/codelist/rytj/talousvesi/code/0203"
   :pintavesikaivo    "http://uri.suomi.fi/codelist/rytj/talousvesi/code/0204"
   :kantovesi         "http://uri.suomi.fi/codelist/rytj/talousvesi/code/03"
   :ei-talousvetta    "http://uri.suomi.fi/codelist/rytj/talousvesi/code/04"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/ilmanvaihto"}
  ilmanvaihtotavan-laji
  {:painovoimainen-ilmanvaihto            "http://uri.suomi.fi/codelist/rytj/ilmanvaihto/code/01"
   :koneellinen-ilmanvaihto               "http://uri.suomi.fi/codelist/rytj/ilmanvaihto/code/02"
   :koneellinen-tulo-ja-poistoilmanvaihto "http://uri.suomi.fi/codelist/rytj/ilmanvaihto/code/03"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/jatevesienkasittely"}
  jatevesienkasittely
  {:verkostoliittyma                    "http://uri.suomi.fi/codelist/rytj/jatevesienkasittely/code/01"
   :jatevesisailio                      "http://uri.suomi.fi/codelist/rytj/jatevesienkasittely/code/02"
   :maapuhdistamo                       "http://uri.suomi.fi/codelist/rytj/jatevesienkasittely/code/03"
   :laitepuhdistamo                     "http://uri.suomi.fi/codelist/rytj/jatevesienkasittely/code/04"
   :harmaavesipuhdistamo                "http://uri.suomi.fi/codelist/rytj/jatevesienkasittely/code/05"
   :ei-tarvetta-jatevesien-kasittelylle "http://uri.suomi.fi/codelist/rytj/jatevesienkasittely/code/06"
   :muu-kasittely                       "http://uri.suomi.fi/codelist/rytj/jatevesienkasittely/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/verkliit"}
  verkostoliittymat
  {:tietoliikennekaapeli  "http://uri.suomi.fi/codelist/rytj/verkliit/code/01"
   :talousvesi            "http://uri.suomi.fi/codelist/rytj/verkliit/code/02"
   :jatevesi              "http://uri.suomi.fi/codelist/rytj/verkliit/code/03"
   :sahko                 "http://uri.suomi.fi/codelist/rytj/verkliit/code/04"
   :kaukolampo            "http://uri.suomi.fi/codelist/rytj/verkliit/code/05"
   :kaasu                 "http://uri.suomi.fi/codelist/rytj/verkliit/code/06"
   :hulevesi              "http://uri.suomi.fi/codelist/rytj/verkliit/code/07"
   :kaukojaahdytys        "http://uri.suomi.fi/codelist/rytj/verkliit/code/08"
   :sammutusvesi          "http://uri.suomi.fi/codelist/rytj/verkliit/code/09"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/rakennusvaruste"}
  rakennusvaruste
  {:sahko                   "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/01"
   :kaasu                   "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/02"
   :jatevesiviemari         "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/03"
   :vesijohto               "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/04"
   :lammin-vesi             "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/05"
   :koneellinen-ilmastointi "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/06"
   :aurinkopaneeli          "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/07"
   :hissi                   "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/08"
   :uima-allas              "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/09"
   :sauna                   "http://uri.suomi.fi/codelist/rytj/RakennusVaruste/code/10"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine"}
  kantava-rakennusaine
  "These codes contains ä and/or ö character to retain a 1:1 mapping with the older KuntaGML values"
  {:ei-tiedossa "http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine/code/00"
   :betoni      "http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine/code/01"
   :tiili       "http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine/code/02"
   :teräs       "http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine/code/03"
   :puu         "http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine/code/04"
   :other       "http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine"}
  julkisivu
  {:ei-tiedossa "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine/code/00"
   :betoni      "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine/code/01"
   :tiili       "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine/code/02"
   :metallilevy "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine/code/03"
   :kivi        "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine/code/04"
   :puu         "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine/code/05"
   :lasi        "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine/code/06"
   :other       "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/vtj/Rake_runkotapa"}
  rakentamistapa
  {:elementti      "http://uri.suomi.fi/codelist/vtj/Rake_runkotapa/code/01"
   :paikalla-tehty "http://uri.suomi.fi/codelist/vtj/Rake_runkotapa/code/02"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/Paloluokka"}
  paloluokka
  {:P0 "http://uri.suomi.fi/codelist/rytj/Paloluokka/code/00"
   :P1 "http://uri.suomi.fi/codelist/rytj/Paloluokka/code/01"
   :P2 "http://uri.suomi.fi/codelist/rytj/Paloluokka/code/02"
   :P3 "http://uri.suomi.fi/codelist/rytj/Paloluokka/code/03"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/hissi"}
  hissin-laji
  "This code describes the type of elevator"
  {:henkilohissi "http://uri.suomi.fi/codelist/rytj/hissi/code/01"
   :pystyhissi   "http://uri.suomi.fi/codelist/rytj/hissi/code/02"
   :porrashissi  "http://uri.suomi.fi/codelist/rytj/hissi/code/03"
   :tavarahissi  "http://uri.suomi.fi/codelist/rytj/hissi/code/04"
   :muu          "http://uri.suomi.fi/codelist/rytj/hissi/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe"}
  elinkaaren-vaihe
  "This code describes the lifecycle state of a functional part of the structure, like an elevator or an entrance."
  {:suunnitteilla              "http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe/code/1"
   :rakenteilla                "http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe/code/2"
   :kaytossa                   "http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe/code/3"
   :tilapaisesti-pois-kaytosta "http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe/code/4"
   :poistettu-kaytosta         "http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe/code/5"
   :poistettu                  "http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe/code/6"
   :purettu                    "http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe/code/7"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/sisaankaynti"}
  sisaankaynnin-tyyppi
  {:paasisaankaynti-porrashuoneeseen  "http://uri.suomi.fi/codelist/rytj/sisaankaynti/code/01"
   :muu-sisaankaynti-porrashuoneeseen "http://uri.suomi.fi/codelist/rytj/sisaankaynti/code/02"
   :sisaankaynti-huoneistoon          "http://uri.suomi.fi/codelist/rytj/sisaankaynti/code/03"
   :paasisaankaynti-yleisolle         "http://uri.suomi.fi/codelist/rytj/sisaankaynti/code/04"
   :muu-sisaankaynti-yleisolle        "http://uri.suomi.fi/codelist/rytj/sisaankaynti/code/05"
   :tavaran-toimitus-tai-nouto        "http://uri.suomi.fi/codelist/rytj/sisaankaynti/code/06"
   :sisaankaynti-ajoneuvoille         "http://uri.suomi.fi/codelist/rytj/sisaankaynti/code/07"
   :muu-sisaankaynti                  "http://uri.suomi.fi/codelist/rytj/sisaankaynti/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka"} vaestonsuoja-luokka
  "Civil defence shelter classes"
  {:terasbetonisuojat                                     "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/01"
   :terasbetonisuojat-C59                                 "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0101"
   :terasbetonisuojat-C63                                 "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0102"
   :terasbetonisuojat-S1                                  "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0103"
   :terasbetonisuojat-K                                   "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0104"
   :raskaat-terasbetonisuojat                             "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/02"
   :raskaat-terasbetonisuojat-A                           "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0201"
   :raskaat-terasbetonisuojat-B59                         "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0202"
   :raskaat-terasbetonisuojat-B63                         "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0203"
   :raskaat-terasbetonisuojat-S3                          "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0204"
   :raskaat-terasbetonisuojat-S2                          "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0205"
   :kalliosuojat                                          "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/03"
   :kalliosuojat-kalliosuoja                              "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0301"
   :tilapaiset-suojat                                     "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/04"
   :tilapaiset-suojat-sirpaleilta-suojaava                "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0401"
   :tilapaiset-suojat-sateilyilta-ja-sirpaleilta-suojaava "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/0402"
   :muu-suoja-ei-tiedossa                                 "http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka/code/99"})

(def vaestonsuoja-luokka-grouped
  "Grouped version of vaestonsuoja-luokka for use with optgroup selects.
   Items with :optgroup true are group headers; subsequent items are grouped under them."
  [{:name :muu-suoja-ei-tiedossa}
   {:name :terasbetonisuojat :optgroup true}
   {:name :terasbetonisuojat-C59}
   {:name :terasbetonisuojat-C63}
   {:name :terasbetonisuojat-S1}
   {:name :terasbetonisuojat-K}
   {:name :raskaat-terasbetonisuojat :optgroup true}
   {:name :raskaat-terasbetonisuojat-A}
   {:name :raskaat-terasbetonisuojat-B59}
   {:name :raskaat-terasbetonisuojat-B63}
   {:name :raskaat-terasbetonisuojat-S3}
   {:name :raskaat-terasbetonisuojat-S2}
   {:name :kalliosuojat :optgroup true}
   {:name :kalliosuojat-kalliosuoja}
   {:name :tilapaiset-suojat :optgroup true}
   {:name :tilapaiset-suojat-sirpaleilta-suojaava}
   {:name :tilapaiset-suojat-sateilyilta-ja-sirpaleilta-suojaava}])

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne"}
  sijaintikerroksen-tarkenne
  {:kellari "http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne/code/01"
   :ullakko "http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne/code/02"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji"}
  huoneiston-muutoksen-laji
  "These codes contains ä and/or ö character to retain a 1:1 mapping with the older KuntaGML values"
  {:lisäys "http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji/code/01"
   :muutos "http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji/code/02"
   :poisto "http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji/code/03"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi"}
  huoneistotyyppi
  {:asuinhuoneisto "http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi/code/01"
   :toimitila      "http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi/code/02"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste"}
  huoneiston-varuste
  "If these change table columns needs to be added manually to `lupapalvelu.ui.docgen.field.huoneistot.columns`"
  {:wc          "http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste/code/01"
   :suihku      "http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste/code/02"
   :sauna       "http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste/code/03"
   :parveke     "http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste/code/04"
   :lammin-vesi "http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste/code/05"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi"}
  keittiotyyppi
  {:keittio      "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi/code/01"
   :keittokomero "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi/code/02"
   :keittotila   "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi/code/03"
   :tupakeittio  "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi/code/04"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/KatselmuksenTilanne"}
  katselmuksen-tilanne
  {:toimitettu         "http://uri.suomi.fi/codelist/rytj/KatselmuksenTilanne/code/01"
   :korjattava         "http://uri.suomi.fi/codelist/rytj/KatselmuksenTilanne/code/02"
   :toimitettu-ehdolla "http://uri.suomi.fi/codelist/rytj/KatselmuksenTilanne/code/03"})

;; NOTE: No edn and generated i18n for this
(def ^{:uri "http://uri.suomi.fi/codelist/rytj/vaativuusluokat"}
  vaativuusluokat
  "Should match `lupapalvelu.document.schemas/make-patevyysluokka`"
  {:AA  "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/1"
   :A+  "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/2"
   :A   "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/3"
   :B   "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/4"
   :C   "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/5"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli"}
  suunnittelijanrooli
  "Should match `lupapalvelu.document.schemas/kuntaroolikoodi`"
  {"pääsuunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/03"

   "LVI-suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/02"
   "IV-suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/08"
   "KVV-suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/09"
   "Vaikeiden töiden suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/06"

   ;; KRYSP yht 2.1.6
   "rakennussuunnittelija"  "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/05"
   "kantavien rakenteiden suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/07"
   "ilmanvaihdon suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/08" ;; FIXME: Same as IV-suunnittelija?
   "kiinteistön vesi- ja viemäröintilaitteiston suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/09"
   "rakennusfysikaalinen suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/10"
   "kosteusvaurion korjaustyön suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/11"

   ;; Removed in yht 2.1.11
   ;; TODO: Add logic to forbid these in new applications (>2025).
   "GEO-suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/99" ;; FIXME: Muu
   "RAK-rakennesuunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/99" ;; FIXME: Muu
   "ARK-rakennussuunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/99" ;; FIXME: Muu
   "pohjarakenteiden suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/01" ;; FIXME: same as new pohjarakennesuunnittelija

   ;; New in yht 2.1.11
   "rakennesuunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/04"
   "pohjarakennesuunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/01"
   "energiatodistuksen laatija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/12"
   "vastaava rakennesuunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/99" ;; FIXME: Muu
   "kosteudenhallintakoordinaattori" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/13"
   "palotekninen suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/14"
   "akustiikkasuunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/17"
   "maisemarakentamisen suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/16"

   "ei tiedossa" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/99"

   ;; FIXME: Missing from document.schemas
   "kalliorakenteiden suunnittelija" "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/15"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/Omistajalaji"}
  omistajalaji
  "Should match `lupapalvelu.document.schemas/omistajalajit`"
  {"yksityinen maatalousyrittäjä" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/01"
   "muu yksityinen henkilö tai perikunta" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/02"
   "asunto-oy tai asunto-osuuskunta" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/03"
   "kiinteistö oy" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/04"
   "yksityinen yritys (osake-, avoin- tai kommandiittiyhtiö, osuuskunta)" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/05"
   "valtio- tai kuntaenemmistöinen yritys" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/06"
   "kunnan liikelaitos" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/07"
   "valtion liikelaitos" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/08"
   "pankki tai vakuutuslaitos" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/09"
   "kunta tai kuntainliitto" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/10"
   "valtio" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/11"
   "sosiaaliturvarahasto" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/12"
   "uskonnollinen yhteisö, säätiö, puolue tai yhdistys" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/13"
   "ei tiedossa" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/14"

   ;; FIXME: Missing from document.schemas
   "hyvinvointialue" "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/15"})

(def ^{:uri "http://uri.suomi.fi/codelist/vtj/Rake_kaava"}
  kaavatilanne
  "Should match `lupapalvelu.document.schemas` kaavatilanne select fields (multiple)"
  {"maakuntakaava" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/06"
   "oikeusvaikutteinen yleiskaava" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/07"
   "oikeusvaikutukseton yleiskaava" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/08"
   "asemakaava" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/01"
   "ranta-asemakaava" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/09"
   "ei kaavaa" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/05"

   ;; FIXME: Missing from document.schemas
   "rakennuskaava" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/02"
   "rantakaava" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/03"
   "yleiskaava" "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/04"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/RakennuspaikanHallintaperuste"}
  rakennuspaikan-hallintaperuste
  {"oma" "http://uri.suomi.fi/codelist/rytj/RakennuspaikanHallintaperuste/code/1"
   "vuokra" "http://uri.suomi.fi/codelist/rytj/RakennuspaikanHallintaperuste/code/2"
   "muu oikeus" "http://uri.suomi.fi/codelist/rytj/RakennuspaikanHallintaperuste/code/3"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/hulevedenkasittely"}
  hulevedenkasittely
  {"imeyttaminen" "http://uri.suomi.fi/codelist/rytj/hulevedenkasittely/code/01"
   "viivyttaminen ennen pois johtamista" "http://uri.suomi.fi/codelist/rytj/hulevedenkasittely/code/02"
   "pois johtaminen ilman viivyttamista" "http://uri.suomi.fi/codelist/rytj/hulevedenkasittely/code/03"
   "jatevesiviemari" "http://uri.suomi.fi/codelist/rytj/hulevedenkasittely/code/04"})

(def ^{:uri "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi"}
  etvra-tyyppi
  {"aurinkopaneelikentta" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/01"
   "kaatopaikka" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/02"
   "puisto" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/03"
   "pysakointialue" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/04"
   "tapahtuma-alue" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/05"
   "tori" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/06"
   "urheilualue" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/07"
   "varastointialue" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/08"
   "muu-alue" "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli"}
  tyonjohtajan-rooli
  {"vastaava työnjohtaja" "http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli/code/04"
   "KVV-työnjohtaja" "http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli/code/01"
   "IV-työnjohtaja" "http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli/code/02"
   "erityisalojen työnjohtaja" "http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli/code/03"

   ;; In document.schemas, but no clear value in the codeset
   ;; vastaava työnjohtaja
   "työnjohtaja" "http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli/code/04"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus"}
  rakennelman-kayttotarkoitus
  {"Aallonmurtaja" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/001"
   "Aita" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/002"
   "Antenni" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/003"
   "Asuntovaunu" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/004"
   "Aurinkopaneeli" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/070"
   "Autosuoja" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/005"
   "Autotalli" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/006"
   "Ei tiedossa" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/007"
   "Hyppyrimäki" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/008"
   "Ikkuna" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/009"
   "Infotaulu (jalankulkuopastaulu)" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/010"
   "Jakokaappi" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/011"
   "Jalasmökki" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/012"
   "Jätekatos tai -aitaus" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/013"
   "Jätevesijärjestelmä" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/014"
   "Kasvihuone" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/015"
   "Katos/pergola" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/016"
   "Katsomo" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/017"
   "Katumainostaulu" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/018"
   "Kelluva rakennelma" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/019"
   "Kierrätyspiste" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/020"
   "Kioski" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/021"
   "Kolmiopilari" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/022"
   "Laituri" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/023"
   "Lastauslaituri" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/024"
   "Liikuteltava grillikioski" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/025"
   "Lipputankoryhmä" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/026"
   "Maakellari" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/027"
   "Maalämpöpumppujärjestelmä" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/028"
   "Mainoslaite" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/029"
   "Markiisi" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/030"
   "Masto" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/031"
   "Muu käyttö" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/032"
   "Muu rakennelma" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/033"
   "Muu toimenpide" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/034"
   "Muu vesirajalaite" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/035"
   "Muuntamo" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/036"
   "Muuri" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/037"
   "Näkötorni" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/038"
   "Odotuskatos" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/039"
   "Opaste" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/040"
   "Ovi" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/041"
   "Parvekelasitus" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/042"
   "Pihajärjestely" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/043"
   "Piippu" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/044"
   "Portti" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/045"
   "Puhelinkioski" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/046"
   "Pylväsbanderolli" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/047"
   "Pylvästaulu" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/048"
   "Pysäkkikatos" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/049"
   "Pysäköintialue" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/050"
   "Pyöreä mainospilari" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/051"
   "Rantamuuri" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/052"
   "Savupiippu" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/053"
   "Siirtopuutarhamökki" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/054"
   "Suurtaulu, sisältä valaistu" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/055"
   "Suurtaulu, ulkoa valaistu" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/056"
   "Taideteos" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/057"
   "Taksikatos" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/058"
   "Tuulivoimala" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/059"
   "Ulkomainoslaite" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/060"
   "Ulkotarjoilualue" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/061"
   "Vaja" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/062"
   "Valaisinpylväs" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/063"
   "Varasto" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/064"
   "Varastointialue" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/065"
   "Varastointisäiliö" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/069"
   "Viestintätorni" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/066"
   "Yleisöteltta" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/067"
   "Yleisö-WC" "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/068"
   })

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji"}
  lupamaarayksen-laji
  "Should match the list in lupapalvelu.pate.schema-helper/lupamaarayksen-lajit"
  {:katselmukset                                       "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/01"
   :aloittamisilmoitus                                 "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0101"
   :aloituskokous                                      "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0102"
   :pohjakatselmus                                     "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0103"
   :sijaintikatselmus                                  "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0104"
   :rakennekatselmus                                   "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0105"
   :lampolaitteiden-katselmus                          "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0106"
   :vesilaitteiden-katselmus                           "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0107"
   :ilmanvaihtolaitteiden-katselmus                    "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0108"
   :osittainen-loppukatselmus                          "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0109"
   :loppukatselmus                                     "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0110"
   :autopaikat                                         "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/02"
   :autopaikat-kiinteistolla                           "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0201"
   :autopaikat-kiinteiston-ulkopuolella                "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0202"
   :vaestonsuojat                                      "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/03"
   :vaestonsuojat-kiinteistolla                        "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0301"
   :vaestonsuojat-kiinteiston-ulkopuolella             "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0302"
   :erityissuunnitelmat                                "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/04"
   :rakennussuunnitelma                                "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0401"
   :kantavien-rakenteiden-suunnitelma                  "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0402"
   :pohjarakenteiden-ja-kalliorakenteiden-suunnitelma  "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0403"
   :ilmanvaihdon-suunnitelma                           "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0404"
   :kiinteiston-vesi-ja-viemarilaitteiston-suunnitelma "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0405"
   :rakennusfysikaalinen-suunnitelma                   "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0406"
   :kosteusvaurion-korjaustyon-suunnitelma             "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0407"
   :akustiikan-suunnitelma                             "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0408"
   :maisemarakentamisen-suunnitelma                    "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0409"
   :muu-suunnitelma                                    "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/0499"
   :muu-lupamaarays                                    "http://uri.suomi.fi/codelist/rytj/lupamaarayksenlaji/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide"}
  rakentamistoimenpide
  {:uusi-rakennus-tai-rakennelma                 "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/01"
   :laajentaminen                                "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/02"
   :uudelleen-rakentamiseen-verrattava-muutostyo "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/03"
   :muu-muutostyo                                "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/04"
   :purkaminen                                   "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/05"
   :rakennuksen-osittainen-purkaminen            "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/06"
   :kaupunkikuvatoimenpide                       "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/07"
   :maisemaa-muuttava-toimenpide                 "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/08"
   :rakennuksen-tai-rakennelman-paivitys         "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/09"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/ostoenergia"} ostoenergian-laji
  {:uusiutuva-polttoaine   "http://uri.suomi.fi/codelist/rytj/ostoenergia/code/01"
   :fossiilinen-polttoaine "http://uri.suomi.fi/codelist/rytj/ostoenergia/code/02"
   :kaukolampo             "http://uri.suomi.fi/codelist/rytj/ostoenergia/code/03"
   :kaukojaahdytys         "http://uri.suomi.fi/codelist/rytj/ostoenergia/code/04"
   :sahko                  "http://uri.suomi.fi/codelist/rytj/ostoenergia/code/05"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji"}
  ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji
  {:raja-arvo-ylittyy-enintaan-viisi-prosenttia                                                                      "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/01"
   :rakennuksen-ominaispiirteiden-takia                                                                              "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/11"
   :rakennuksen-kayttotarkoituksen-ominaispiirteiden-takia                                                           "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/12"
   :rakennuksen-sijaintipaikan-ominaispiirteiden-takia                                                               "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/13"
   :sellaisten-kaavamaaraysten-noudattamisen-takia-jotka-nostavat-merkittavasti-rakennuksen-hiilijalanjalkea         "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/14"
   :tavanomaisesta-rakentamisesta-poikkeavien-pitkaikaisyytta-edistavien-elinkaariominaisuuksien-toteuttamisen-takia "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/15"
   :raja-arvo-ylittyy-erityistilanne                                                                                 "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/02"
   :raja-arvon-alittaminen-on-erityisen-vaikeaa-rakennuksen-korkeuden-vuoksi                                         "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/21"
   :suunnittelu-ja-toteuttaminen-erityisen-vaikeaa-poikkeuksellisen-vaativan-sijaintipaikan-vuoksi                   "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/22"})

(def ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji-grouped
  [{:name :raja-arvo-ylittyy-enintaan-viisi-prosenttia :optgroup true}
   {:name :rakennuksen-ominaispiirteiden-takia}
   {:name :rakennuksen-kayttotarkoituksen-ominaispiirteiden-takia}
   {:name :rakennuksen-sijaintipaikan-ominaispiirteiden-takia}
   {:name :sellaisten-kaavamaaraysten-noudattamisen-takia-jotka-nostavat-merkittavasti-rakennuksen-hiilijalanjalkea}
   {:name :tavanomaisesta-rakentamisesta-poikkeavien-pitkaikaisyytta-edistavien-elinkaariominaisuuksien-toteuttamisen-takia}
   {:name :raja-arvo-ylittyy-erityistilanne :optgroup true}
   {:name :raja-arvon-alittaminen-on-erityisen-vaikeaa-rakennuksen-korkeuden-vuoksi}
   {:name :suunnittelu-ja-toteuttaminen-erityisen-vaikeaa-poikkeuksellisen-vaativan-sijaintipaikan-vuoksi}])

(def RakentamisToimenpide (apply sc/enum (keys rakentamistoimenpide)))

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/ravaasiaki"}
  asiakirjan-laji
  ;; NOTE: These cover about 95% of Lupapiste attachment types
  {;; 01 - Ennakkoluvat ja lausunnot
   :elyn_tai_kunnan_poikkeamapaatos                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0101"
   :naapurin_suostumus                                                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0102"
   :naapurin_kuuleminen                                                                                        "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0103"
   :naapurin_huomautus                                                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0104"
   :suunnittelutarveratkaisu                                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0105"
   :ymparistolupa                                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0106"
   :lausunto                                                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0107"
   :lausunnon_liite                                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0108"
   :ennakkoneuvottelumuistio                                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0109"
   :vesi_ja_viemariliitoslausunto_tai_kartta                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0110"
   :paatos_ajoliittymasta                                                                                      "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0111"
   :johtokartta                                                                                                "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0112"
   :maanalaisten_johtojen_sijaintiselvitys                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0112"
   ;; 02 - Hakija
   :osakeyhtion_perustamiskirja                                                                                "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0201"
   :ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta                                                 "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0202"
   :ote_kauppa_ja_yhdistysrekisterista                                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0203"
   :valtakirja                                                                                                 "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0204"
   ;; 03 - Osapuolet
   :cv                                                                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0301"
   :patevyystodistus                                                                                           "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0302"
   :suunnittelijan_tiedot                                                                                      "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0303"
   :tutkintotodistus                                                                                           "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0304"
   ;; 04 - Rakennuspaikan hallinta
   :todistus_hallintaoikeudesta                                                                                "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0401"
   :ote_yhtiokokouksen_poytakirjasta                                                                           "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0402"
   :rasitesopimus                                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0403"
   :rasitustodistus                                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0404"
   :todistus_erityisoikeuden_kirjaamisesta                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0405"
   :kiinteiston_lohkominen                                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0406"
   :sopimusjaljennos                                                                                           "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0407"
   ;; 05 - Rakennuspaikka
   :karttaaineisto                                                                                             "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0501"
   :karttaote                                                                                                  "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0502"
   :ote_alueen_peruskartasta                                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0503"
   :ote_asemakaavasta_jos_asemakaava_alueella                                                                  "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0504"
   :ote_kiinteistorekisteristerista                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0505"
   :ote_ranta-asemakaavasta                                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0506"
   :ote_yleiskaavasta                                                                                          "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0507"
   :perustamistapalausunto                                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0508"
   :pintavaaitus                                                                                               "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0509"
   :rakennusoikeuslaskelma                                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0510"
   :tonttikartta_tarvittaessa                                                                                  "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0511"
   :selvitys_rakennuspaikan_korkeusasemasta                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0512"
   ;; 06 - Selvitykset
   :yhteistilat                                                                                                "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0601"
   :energiataloudellinen_selvitys                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0602"
   :energiatodistus                                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0603"
   :haittaaineselvitys                                                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0604"
   :kokoontumishuoneisto                                                                                       "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0605"
   :kosteudenhallintaselvitys                                                                                  "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0606"
   :kuntotarkastusselvitys                                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0607"
   :laadunvarmistusselvitys                                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0608"
   :liikkumis_ja_esteettomyysselvitys                                                                          "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0609"
   :lomarakennuksen_muutos_asuinrakennukseksi_selvitys_maaraysten_toteutumisesta                               "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0610"
   :maalampo_porausraportti                                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0611"
   :maalampo_rakennettavuusselvitys                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0612"
   :meluselvitys                                                                                               "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0613"
   :rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0614"
   :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta                                                 "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0615"
   :rakennuttajan_valvojaa_koskeva_selvitys                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0616"
   :rakenteellisen_turvallisuuden_alustava_riskiarvio                                                          "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0617"
   :rh_tietolomake                                                                                             "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0618"
   :riskianalyysi                                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0619"
   :selvitys_kiinteiston_jatehuollon_jarjestamisesta                                                           "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0620"
   :selvitys_liittymisesta_ymparoivaan_rakennuskantaan                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0621"
   :selvitys_rakennuksen_kunnosta                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0622"
   :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0623"
   :selvitys_rakennuksen_terveellisyydesta                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0624"
   :selvitys_rakennuksen_aaniteknisesta_toimivuudesta                                                          "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0625"
   :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0626"
   :selvitys_rakennuspaikan_terveellisyydesta                                                                  "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0627"
   :selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista                                        "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0628"
   :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta                                                "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0629"
   :tarinaselvitys                                                                                             "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0630"
   :muu_selvitys                                                                                               "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0631"
   ;; 07 - Suunnitelmat
   :hankeselvitys                                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0701"
   :julkisivujen_varityssuunnitelma                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0702"
   :jatevesijarjestelman_suunnitelma                                                                           "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0703"
   :selvitys_rakennuksen_kosteusteknisesta_toimivuudesta                                                       "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0704"
   :mainoslaitesuunnitelma                                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0705"
   :opastesuunnitelma                                                                                          "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0706"
   :piha_tai_istutussuunnitelma                                                                                "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0707"
   :valaistussuunnitelma                                                                                       "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0708"
   :muu_suunnitelma                                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0709"
   ;; 08 - Pelastusviranomaiselle esitettävät suunnitelmat
   :merkki_ja_turvavalaistussuunnitelma                                                                        "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0801"
   :palotekninen_lausunto                                                                                      "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0802"
   :paloturvallisuussuunnitelma                                                                                "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0803"
   :savunpoistosuunnitelma                                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0804"
   :sammutusautomatiikkasuunnitelma                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0805"
   :suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0806"
   :turvallisuusselvitys                                                                                       "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0807"
   :ilmoitus_vaestonsuojasta                                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0808"
   :vaestonsuojasuunnitelma                                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0809"
   :muu_pelastusviranomaisen_suunnitelma                                                                       "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0810"
   ;; 09 - Päätöksenteko
   :hakemus                                                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0901"
   :ilmoitus                                                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0902"
   :paatoksen_liite                                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0903"
   :paatos                                                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0904"
   :paatosote                                                                                                  "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0905"
   :valitusosoitus                                                                                             "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0906"
   :poytakirjaote                                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0907"
   :muistio                                                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0908"
   :paatosehdotus                                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/0909"
   ;; 10 - Muutoksenhaku
   :huomautus                                                                                                  "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1001"
   :valitus                                                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1002"
   :oikaisuvaatimus                                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1003"
   ;; 11 - Katselmukset ja tarkastukset
   :aloituskokouksen_poytakirja                                                                                "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1101"
   :katselmuksen_liite                                                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1102"
   :katselmuksen_tai_tarkastuksen_poytakirja                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1103"
   :kayttoonottokatselmuksen_poytakirja                                                                        "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1104"
   :loppukatselmuksen_poytakirja                                                                               "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1105"
   :tarkastusasiakirja                                                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1106"
   :tarkastusasiakirjan_yhteeveto                                                                              "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1107"
   ;; 12 - Muut
   :erityismenettelyasiakirja                                                                                  "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1201"
   :luonnos                                                                                                    "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1202"
   :piirustusluettelo                                                                                          "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1203"
   :sijoituslupaasiakirja                                                                                      "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1204"
   :suorituskyvyttomyysvakuusasiakirja                                                                         "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1205"
   :tutkimus                                                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1206"
   :vakuusasiakirja                                                                                            "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1207"
   :valokuva                                                                                                   "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1208"
   :keskustelu                                                                                                 "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1209"
   :kuvaus                                                                                                     "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1210"
   :muu                                                                                                        "http://uri.suomi.fi/codelist/rytj/ravaasiaki/code/1211"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/julkisuus"}
  asiakirjan-julkisuusluokka
  {:julkinen                              "http://uri.suomi.fi/codelist/rytj/julkisuus/code/1"
   :turvaluokiteltu                       "http://uri.suomi.fi/codelist/rytj/julkisuus/code/2"
   :salassapidettava                      "http://uri.suomi.fi/codelist/rytj/julkisuus/code/3"
   :tiedon-jakaminen-vaatii-tarkistamista "http://uri.suomi.fi/codelist/rytj/julkisuus/code/4"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila"}
  rakvalv-asian-elinkaaren-tila
  {:ennakkoneuvottelu-tai-valmistelu "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/1"
   :vireilla                         "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/2"
   :kasittely-keskeytetty            "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/3"
   :paatos-annettu                   "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/4"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/rakennuksentietomallinlaji"}
  rakennuksentietomallinlaji
  {:suunnitelmamalli "http://uri.suomi.fi/codelist/rytj/rakennuksentietomallinlaji/code/01"
   :toteumamalli     "http://uri.suomi.fi/codelist/rytj/rakennuksentietomallinlaji/code/02"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/sailytysaika"}
  sailytysaika
  {:sailytetaan_pysyvasti                                         "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/01"
   :sailytetaan_maaraajan                                         "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/02"
   :sailytetaan_maaraajan_2_vuotta                                "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0201"
   :sailytetaan_maaraajan_2_vuotta_paatoksen_lainvoimaisuuspaiva  "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020101"
   :sailytetaan_maaraajan_2_vuotta_paatoksen_antopaiva            "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020102"
   :sailytetaan_maaraajan_2_vuotta_rakennuksen_valmistuminen      "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020103"
   :sailytetaan_maaraajan_5_vuotta                                "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0202"
   :sailytetaan_maaraajan_5_vuotta_paatoksen_lainvoimaisuuspaiva  "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020201"
   :sailytetaan_maaraajan_5_vuotta_paatoksen_antopaiva            "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020202"
   :sailytetaan_maaraajan_5_vuotta_rakennuksen_valmistuminen      "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020203"
   :sailytetaan_maaraajan_10_vuotta                               "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0203"
   :sailytetaan_maaraajan_10_vuotta_paatoksen_lainvoimaisuuspaiva "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020301"
   :sailytetaan_maaraajan_10_vuotta_paatoksen_antopaiva           "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020302"
   :sailytetaan_maaraajan_10_vuotta_rakennuksen_valmistuminen     "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020303"
   :sailytetaan_maaraajan_15_vuotta                               "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0204"
   :sailytetaan_maaraajan_15_vuotta_paatoksen_lainvoimaisuuspaiva "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020401"
   :sailytetaan_maaraajan_15_vuotta_paatoksen_antopaiva           "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020402"
   :sailytetaan_maaraajan_15_vuotta_rakennuksen_valmistuminen     "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/020403"
   :sailytetaan_maaraajan_rakennuksen_purkamiseen_asti            "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0205"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto"}
  henkilotietosisalto
  {:ei_sisalla_henkilotietoja                             "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/1"
   :sisaltaa_nimitietoja                                  "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/2"
   :sisaltaa_henkilotietoa_josta_henkilo_tunnistettavissa "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/3"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/ryhtikielet"}
  ryhtikielet
  {:fi-suomi        "http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/fi"
   :sv-ruotsi       "http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/sv"
   :en-englanti     "http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/en"
   :smn-inarinsaame "http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/smn"
   :sms-koltansaame "http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/sms"
   :se-pohjoissaame "http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/se"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija"}
  paatoksen-tekija
  {:viranhaltija                    "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/01"
   :monijaseninen_paatoksentekoelin "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/02"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji"}
  poikkeamisen-laji
  {:asuntojen-lukumaara               "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/01"
   :autopaikat                        "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/02"
   :rakennussuojelu                   "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/03"
   :rakennuskielto                    "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/04"
   :rakennusalan-ylittaminen          "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/05"
   :rakennusjarjestys                 "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/06"
   :rakennuspaikan-minimikokovaatimus "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/07"
   :rantarakentaminen                 "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/08"
   :rakennuksen-kayttotarkoitus       "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/09"
   :rakennusoikeus                    "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/10"
   :kerrosluku                        "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/11"
   :tontin-lohkomattomuus             "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/12"
   :muu-poikkeus                      "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/13"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/raksuunlajit"}
  raksuunlajit
  {:asemapiirros       "http://uri.suomi.fi/codelist/rytj/raksuunlajit/code/01"
   :julkisivupiirustus "http://uri.suomi.fi/codelist/rytj/raksuunlajit/code/02"
   :leikkauspiirustus  "http://uri.suomi.fi/codelist/rytj/raksuunlajit/code/03"
   :pohjapiirustus     "http://uri.suomi.fi/codelist/rytj/raksuunlajit/code/04"
   :muu-paapiirustus   "http://uri.suomi.fi/codelist/rytj/raksuunlajit/code/99"})

(def ^{:uri "http://uri.suomi.fi/codelist/rytj/erityissuun"}
  erityissuun
  {:hulevesisuunnitelma            "http://uri.suomi.fi/codelist/rytj/erityissuun/code/01"
   :iv-suunnitelma                 "http://uri.suomi.fi/codelist/rytj/erityissuun/code/02"
   :julkisivu-ja-ikkunadetaljit    "http://uri.suomi.fi/codelist/rytj/erityissuun/code/03"
   :kalliorakennesuunnitelma       "http://uri.suomi.fi/codelist/rytj/erityissuun/code/04"
   :kvv-suunnitelma                "http://uri.suomi.fi/codelist/rytj/erityissuun/code/05"
   :lammituslaitesuunnitelma       "http://uri.suomi.fi/codelist/rytj/erityissuun/code/06"
   :pinnan-tasaussuunnitelma       "http://uri.suomi.fi/codelist/rytj/erityissuun/code/07"
   :pohjarakennesuunnitelma        "http://uri.suomi.fi/codelist/rytj/erityissuun/code/08"
   :pohjaveden-hallintasuunnitelma "http://uri.suomi.fi/codelist/rytj/erityissuun/code/09"
   :radontekninen-suunnitelma      "http://uri.suomi.fi/codelist/rytj/erityissuun/code/10"
   :rakennesuunnitelma             "http://uri.suomi.fi/codelist/rytj/erityissuun/code/11"
   :sahkosuunnitelma               "http://uri.suomi.fi/codelist/rytj/erityissuun/code/12"
   :tulisija-ja-hormisuunnitelma   "http://uri.suomi.fi/codelist/rytj/erityissuun/code/13"
   :muu                            "http://uri.suomi.fi/codelist/rytj/erityissuun/code/99"})

(defn default-code-list-reader
  "Default function to read and filter code list data from an EDN resource.

   Parameters:
   - resource-path: Path to the EDN resource file"
  [resource-path]
  (->> (su/read-edn-resource resource-path)
       :codes
       (su/filter-by-key :status "VALID")))

;; Language Codes

(def language-data-resource-path "codes/languages.edn")

(defn language-code-list-reader
  "Initial assumption is to filter out all the dialect languages e.g English (American Samoa) :codeValue en-As

   Parameters:
   - resource-path: Path to the EDN resource file"
  [resource-path]
  (->> (su/read-edn-resource resource-path)
       :codes
       (su/filter-by-key :status "VALID")
       (su/filter-by-key :codeValue #(not (ss/includes? % "-")))))

(defn- main-languages []
  (language-code-list-reader language-data-resource-path))

(defn- main-language-codes-uncached []
  (mapv :codeValue (main-languages)))

(def main-language-codes (main-language-codes-uncached))

;; Postal Codes

(def postal-codes-data-resource-path "codes/postal-codes.edn")

(defn- main-postal-codes []
  (default-code-list-reader postal-codes-data-resource-path))

(defn- main-postal-codes-uncached []
  (mapv :codeValue (main-postal-codes)))

(def main-postal-codes-codes (main-postal-codes-uncached))

;; Rakennusluokitus / Building classifications

(def rakennusluokitus-data-resource-path "codes/rakennusluokitus.edn")

(defn rakennusluokitus-code-value [elem]
  (let [norm-label (-> elem
                       :prefLabel
                       :fi
                       (ss/replace #"å|ä" "a")         ; å ä -> a
                       (ss/replace #"ö" "o")           ; ö   -> o
                       ss/lower-case
                       (ss/replace #"[^a-z0-9]+" "-")  ; only letters and numbers, all else → "-"
                       (ss/replace #"^-+" "")          ; remove leading hyphens
                       (ss/replace #"-+$" "")          ; remove trailing hyphens
                       (ss/replace #"-{2,}" "-")       ; collapse multiple hyphens
                       )
        code-value (-> elem
                       :codeValue
                       name)]
    ;; Compose the keyword
    (keyword (str norm-label "-" code-value))))

(defn rakennusluokitus-data-reader [resource-path]
  (->> (su/read-edn-resource resource-path)
       (:codes)
       (su/filter-by-key :status "VALID")))

(defn- main-rakennusluokitus-uncached []
  (reduce (fn [acc elem] (assoc acc (rakennusluokitus-code-value elem) (:uri elem)))
          {}
          (rakennusluokitus-data-reader rakennusluokitus-data-resource-path)))

;; TODO: Is this useful for anything? Seems like using prefLabel is fragile solution.
(def main-rakennusluokitus (main-rakennusluokitus-uncached))

;; Just number -> uri
(def ^{:uri "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712"}
  rakennusluokitus-code->uri
  (reduce (fn [acc elem] (assoc acc (elem :codeValue name) (:uri elem)))
          {}
          (rakennusluokitus-data-reader rakennusluokitus-data-resource-path)))

(defn lupapiste-rakennusluokka->code [s]
  (when s
    (re-find #"^\d+" s)))

(defn lupapiste-rakennusluokka->uri [s]
  (get rakennusluokitus-code->uri (lupapiste-rakennusluokka->code s)))

(def mapping-vars
  "Vars in this ns, with :uri metadata"
  (->> (ns-interns *ns*)
       (vals)
       (filter (comp :uri meta))))

(def uri->mapping-name
  "For OpenAPI -> Malli generator, from URI -> symbol of the mapping var"
  (reduce (fn [acc v]
            (assoc acc (:uri (meta v)) (:name (meta v))))
          {}
          mapping-vars))
