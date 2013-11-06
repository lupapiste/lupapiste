(ns lupapalvelu.document.poikkeamis-schemas
  (:require [lupapalvelu.document.schemas :refer :all]))
;TODO: refactor kayttotarkoitus to use same as in schemas file has if this stays


(def rakennushanke {:info {:name "rakennushanke"
                           :order 50
                           :removable false}
                    :body [{:name "kaytettykerrosala" :type :group
                            :body [{:name "pintaAla" :type :string :size "s" :unit "m2" :subtype :number}
                                   {:name "kayttotarkoitusKoodi" :type :select
                                    :body [{:name yhden-asunnon-talot}
                                          {:name "012 kahden asunnon talot"}
                                          {:name "013 muut erilliset talot"}
                                          {:name "021 rivitalot"}
                                          {:name "022 ketjutalot"}
                                          {:name "032 luhtitalot"}
                                          {:name "039 muut asuinkerrostalot"}
                                          {:name vapaa-ajan-asuinrakennus}
                                          {:name "111 myym\u00e4l\u00e4hallit"}
                                          {:name "112 liike- ja tavaratalot, kauppakeskukset"}
                                          {:name "119 muut myym\u00e4l\u00e4rakennukset"}
                                          {:name "121 hotellit yms"}
                                          {:name "123 loma-, lepo- ja virkistyskodit"}
                                          {:name "124 vuokrattavat lomam\u00f6kit ja -osakkeet"}
                                          {:name "129 muut majoitusliikerakennukset"}
                                          {:name "131 asuntolat yms"}
                                          {:name "139 muut asuntolarakennukset"}
                                          {:name "141 ravintolat yms"}
                                          {:name "151 toimistorakennukset"}
                                          {:name "161 rautatie- ja linja-autoasemat, lento- ja satamaterminaalit"}
                                          {:name "162 kulkuneuvojen suoja- ja huoltorakennukset"}
                                          {:name "163 pys\u00e4k\u00f6intitalot"}
                                          {:name "164 tietoliikenteen rakennukset"}
                                          {:name "169 muut liikenteen rakennukset"}
                                          {:name "211 keskussairaalat"}
                                          {:name "213 muut sairaalat"}
                                          {:name "214 terveyskeskukset"}
                                          {:name "215 terveydenhuollon erityislaitokset"}
                                          {:name "219 muut terveydenhuoltorakennukset"}
                                          {:name "221 vanhainkodit"}
                                          {:name "222 lasten- ja koulukodit"}
                                          {:name "223 kehitysvammaisten hoitolaitokset"}
                                          {:name "229 muut huoltolaitosrakennukset"}
                                          {:name "231 lasten p\u00e4iv\u00e4kodit"}
                                          {:name "239 muualla luokittelemattomat sosiaalitoimen rakennukset"}
                                          {:name "241 vankilat"}
                                          {:name "311 teatterit, ooppera-, konsertti- ja kongressitalot"}
                                          {:name "312 elokuvateatterit"}
                                          {:name "322 kirjastot ja arkistot"}
                                          {:name "323 museot ja taidegalleriat"}
                                          {:name "324 n\u00e4yttelyhallit"}
                                          {:name "331 seura- ja kerhorakennukset yms"}
                                          {:name "341 kirkot, kappelit, luostarit ja rukoushuoneet"}
                                          {:name "342 seurakuntatalot"}
                                          {:name "349 muut uskonnollisten yhteis\u00f6jen rakennukset"}
                                          {:name "351 j\u00e4\u00e4hallit"}
                                          {:name "352 uimahallit"}
                                          {:name "353 tennis-, squash- ja sulkapallohallit"}
                                          {:name "354 monitoimihallit ja muut urheiluhallit"}
                                          {:name "359 muut urheilu- ja kuntoilurakennukset"}
                                          {:name "369 muut kokoontumisrakennukset"}
                                          {:name "511 yleissivist\u00e4vien oppilaitosten rakennukset"}
                                          {:name "521 ammatillisten oppilaitosten rakennukset"}
                                          {:name "531 korkeakoulurakennukset"}
                                          {:name "532 tutkimuslaitosrakennukset"}
                                          {:name "541 j\u00e4rjest\u00f6jen, liittojen, ty\u00f6nantajien yms opetusrakennukset"}
                                          {:name "549 muualla luokittelemattomat opetusrakennukset"}
                                          {:name "611 voimalaitosrakennukset"}
                                          {:name "613 yhdyskuntatekniikan rakennukset"}
                                          {:name "691 teollisuushallit"}
                                          {:name "692 teollisuus- ja pienteollisuustalot"}
                                          {:name "699 muut teollisuuden tuotantorakennukset"}
                                          {:name "711 teollisuusvarastot"}
                                          {:name "712 kauppavarastot"}
                                          {:name "719 muut varastorakennukset"}
                                          {:name "721 paloasemat"}
                                          {:name "722 v\u00e4est\u00f6nsuojat"}
                                          {:name "729 muut palo- ja pelastustoimen rakennukset"}
                                          {:name "811 navetat, sikalat, kanalat yms"}
                                          {:name "819 el\u00e4insuojat, ravihevostallit, maneesit yms"}
                                          {:name "891 viljankuivaamot ja viljan s\u00e4ilytysrakennukset"}
                                          {:name "892 kasvihuoneet"}
                                          {:name "893 turkistarhat"}
                                          {:name "899 muut maa-, mets\u00e4- ja kalatalouden rakennukset"}
                                          {:name "931 saunarakennukset"}
                                          {:name talousrakennus}
                                          {:name "999 muualla luokittelemattomat rakennukset"}
                                          {:name "ei tiedossa"}]}]}
                           {:name "toimenpiteet"
                            :type :group
                            :repeating true
                            :approvable true
                            :body [{:name "kayttotarkoitus" :type :select
                                    :body [{:name yhden-asunnon-talot}
                                          {:name "012 kahden asunnon talot"}
                                          {:name "013 muut erilliset talot"}
                                          {:name "021 rivitalot"}
                                          {:name "022 ketjutalot"}
                                          {:name "032 luhtitalot"}
                                          {:name "039 muut asuinkerrostalot"}
                                          {:name vapaa-ajan-asuinrakennus}
                                          {:name "111 myym\u00e4l\u00e4hallit"}
                                          {:name "112 liike- ja tavaratalot, kauppakeskukset"}
                                          {:name "119 muut myym\u00e4l\u00e4rakennukset"}
                                          {:name "121 hotellit yms"}
                                          {:name "123 loma-, lepo- ja virkistyskodit"}
                                          {:name "124 vuokrattavat lomam\u00f6kit ja -osakkeet"}
                                          {:name "129 muut majoitusliikerakennukset"}
                                          {:name "131 asuntolat yms"}
                                          {:name "139 muut asuntolarakennukset"}
                                          {:name "141 ravintolat yms"}
                                          {:name "151 toimistorakennukset"}
                                          {:name "161 rautatie- ja linja-autoasemat, lento- ja satamaterminaalit"}
                                          {:name "162 kulkuneuvojen suoja- ja huoltorakennukset"}
                                          {:name "163 pys\u00e4k\u00f6intitalot"}
                                          {:name "164 tietoliikenteen rakennukset"}
                                          {:name "169 muut liikenteen rakennukset"}
                                          {:name "211 keskussairaalat"}
                                          {:name "213 muut sairaalat"}
                                          {:name "214 terveyskeskukset"}
                                          {:name "215 terveydenhuollon erityislaitokset"}
                                          {:name "219 muut terveydenhuoltorakennukset"}
                                          {:name "221 vanhainkodit"}
                                          {:name "222 lasten- ja koulukodit"}
                                          {:name "223 kehitysvammaisten hoitolaitokset"}
                                          {:name "229 muut huoltolaitosrakennukset"}
                                          {:name "231 lasten p\u00e4iv\u00e4kodit"}
                                          {:name "239 muualla luokittelemattomat sosiaalitoimen rakennukset"}
                                          {:name "241 vankilat"}
                                          {:name "311 teatterit, ooppera-, konsertti- ja kongressitalot"}
                                          {:name "312 elokuvateatterit"}
                                          {:name "322 kirjastot ja arkistot"}
                                          {:name "323 museot ja taidegalleriat"}
                                          {:name "324 n\u00e4yttelyhallit"}
                                          {:name "331 seura- ja kerhorakennukset yms"}
                                          {:name "341 kirkot, kappelit, luostarit ja rukoushuoneet"}
                                          {:name "342 seurakuntatalot"}
                                          {:name "349 muut uskonnollisten yhteis\u00f6jen rakennukset"}
                                          {:name "351 j\u00e4\u00e4hallit"}
                                          {:name "352 uimahallit"}
                                          {:name "353 tennis-, squash- ja sulkapallohallit"}
                                          {:name "354 monitoimihallit ja muut urheiluhallit"}
                                          {:name "359 muut urheilu- ja kuntoilurakennukset"}
                                          {:name "369 muut kokoontumisrakennukset"}
                                          {:name "511 yleissivist\u00e4vien oppilaitosten rakennukset"}
                                          {:name "521 ammatillisten oppilaitosten rakennukset"}
                                          {:name "531 korkeakoulurakennukset"}
                                          {:name "532 tutkimuslaitosrakennukset"}
                                          {:name "541 j\u00e4rjest\u00f6jen, liittojen, ty\u00f6nantajien yms opetusrakennukset"}
                                          {:name "549 muualla luokittelemattomat opetusrakennukset"}
                                          {:name "611 voimalaitosrakennukset"}
                                          {:name "613 yhdyskuntatekniikan rakennukset"}
                                          {:name "691 teollisuushallit"}
                                          {:name "692 teollisuus- ja pienteollisuustalot"}
                                          {:name "699 muut teollisuuden tuotantorakennukset"}
                                          {:name "711 teollisuusvarastot"}
                                          {:name "712 kauppavarastot"}
                                          {:name "719 muut varastorakennukset"}
                                          {:name "721 paloasemat"}
                                          {:name "722 v\u00e4est\u00f6nsuojat"}
                                          {:name "729 muut palo- ja pelastustoimen rakennukset"}
                                          {:name "811 navetat, sikalat, kanalat yms"}
                                          {:name "819 el\u00e4insuojat, ravihevostallit, maneesit yms"}
                                          {:name "891 viljankuivaamot ja viljan s\u00e4ilytysrakennukset"}
                                          {:name "892 kasvihuoneet"}
                                          {:name "893 turkistarhat"}
                                          {:name "899 muut maa-, mets\u00e4- ja kalatalouden rakennukset"}
                                          {:name "931 saunarakennukset"}
                                          {:name talousrakennus}
                                          {:name "999 muualla luokittelemattomat rakennukset"}
                                          {:name "ei tiedossa"}]}
                            {:name "Toimenpide" :type :select
                             :body [{:name "uusi"}
                                    {:name "laajennus"}
                                    {:name "perustus"}
                                    {:name "perusparannus"}
                                    {:name "uudelleenrakentaminen"}
                                    {:name "purkaminen"}
                                    {:name "muu muutosty\u00f6"}
                                    {:name "k\u00e4ytt\u00f6tarkoitusmuutos"}
                                    {:name "loma-asunnon muuttaminen vakituiseksi"}]}
                            {:name "huoneistoja" :type "string" :subtype :number :size "s"}
                            {:name "kerroksia" :type "string" :subtype :number :size "s"}
                            {:name "kerrosala" :type "string" :subtype :number :unit "m2" :size "s"}
                            {:name "kokonaisala" :type "string" :subtype :number :unit "m2" :size "s"}]}]})

(def suunnittelutarveratkaisun-lisaosa {:info {:name "suunnittelutarveratkaisun-lisaosa"
             :order 52}
      :body [{:name "kaavoituksen_ja_alueiden_tilanne":type :group :layout :vertical
              :body [{:name "asemakaavaluonnos" :type :checkbox}
                     {:name "yleiskaavaa" :type :checkbox}
                     {:name "rajoittuuko_tiehen" :type :checkbox}
                     {:name "tienkayttooikeus" :type :checkbox}
                     {:name "vesijohto" :type :checkbox}
                     {:name "viemarijohto" :type :checkbox}]}

             {:name "vaikutukset_yhdyskuntakehykselle":type :group :layout :vertical
              :body [{:name "etaisyyys_kouluun" :type :string :subtype :number :unit "km" :size "s"}
                     {:name "turvallinen_polkupyoratie_kouluun" :type :checkbox}
                     {:name "etaisyys_kauppaan" :type :string :subtype :number :unit "km" :size "s"}
                     {:name "etaisyys_paivakotiin" :type :string :subtype :number :unit "km" :size "s"}
                     {:name "etaisyys_kuntakeskuksen_palveluihin" :type :string :subtype :number :unit "km" :size "s"}
                     {:name "muita_vaikutuksia" :type :text :max-len 4000 :layout :full-width}]}

             {:name "maisema":type :group :layout :vertical
              :body [{:name "pellolla" :type :checkbox}
                     {:name "metsassa" :type :checkbox}
                     {:name "metsan_reunassa" :type :checkbox}
                     {:name "nykyisen_rakennuspaikan_vieressa" :type :checkbox}
                     {:name "vanhalla_rakennuspaikalla" :type :checkbox}]}

             {:name "luonto_ja_kulttuuri":type :group :layout :vertical
              :body [{:name "kulttuurisesti_merkittava" :type :checkbox}
                     {:name "suojelukohteita" :type :checkbox}]}

             {:name "virkistys_tarpeet":type :group :layout :vertical
              :body [{:name "virkistysalueella" :type :checkbox}
                     {:name "vaikeuttaako_ulkoilureittia" :type :checkbox}
                     {:name "ulkoilu_ja_virkistysaluetta_varattu" :type :checkbox}]}

             {:name "muut_vaikutukset":type :group :layout :vertical
              :body [{:name "etaisyys_viemariverkosta"  :type :string :subtype :number :unit "m" :size "s"}
                     {:name "liitytaanko_viemariverkostoon" :type :checkbox}
                     {:name "pohjavesialuetta" :type :checkbox}]}

             {:name "merkittavyys":type :group
              :body [{:name "rakentamisen_vaikutusten_merkittavyys" :type :text :max-len 4000 :layout :full-width}]}]})

(def poikkeusasian-rakennuspaikka {:info {:name "poikkeusasian-rakennuspaikka" :i18name "rakennuspaikka" :approvable true
                                          :order 2}
                                   :body rakennuspaikka})

(defschemas
  1
  [rakennushanke
   suunnittelutarveratkaisun-lisaosa
   poikkeusasian-rakennuspaikka])
