(ns lupapalvelu.document.poikkeamis-canonical-test
  (:require [lupapalvelu.factlet :as fl]
            [midje.sweet :refer :all]
            [lupapalvelu.document.canonical-test-common :refer :all]
            [lupapalvelu.document.poikkeamis-canonical :as c]
            [lupapalvelu.document.poikkeamis-schemas]))


(def ^:private statements [{:given 1379423133068
                                     :id "52385377da063788effc1e93"
                                     :person {:text "Paloviranomainen"
                                              :name "Sonja Sibbo"
                                              :email "sonja.sibbo@sipoo.fi"
                                              :id "516560d6c2e6f603beb85147"}
                                     :requested 1379423095616
                                     :status "yes"
                                     :text "Lausunto liitteen\u00e4."}])

(def ^:private hakija {:id "523844e1da063788effc1c58"
                       :schema-info {:approvable true
                                     :subtype "hakija"
                                     :name "hakija"
                                     :removable true
                                     :repeating true
                                     :version 1
                                     :type "party"
                                     :order 3}
                       :created 1379419361123
                       :data {:yritys {:yhteyshenkilo {:henkilotiedot {:etunimi {:modified 1379419361123
                                                                                 :value "Pena"}
                                                                       :sukunimi {:modified 1379419361123
                                                                                  :value "Panaani"}
                                                                       :turvakieltoKytkin {:modified 1379419361123
                                                                                           :value true}}
                                                       :yhteystiedot {:email {:modified 1379419361123
                                                                              :value "pena@example.com"}
                                                                      :puhelin {:modified 1379419361123
                                                                                :value "0102030405"}}}
                                       :osoite {:katu {:modified 1379419361123
                                                       :value "Paapankuja 12"}
                                                :postinumero {:modified 1379419361123
                                                              :value "10203"}
                                                :postitoimipaikannimi {:modified 1379419361123
                                                                       :value "Piippola"}}}
                              :henkilo {:userId {:modified 1379419361123
                                                 :value "777777777777777777000020"}
                                        :henkilotiedot {:hetu {:modified 1379419361123
                                                               :value "210281-9988"}
                                                        :etunimi {:modified 1379419361123
                                                                  :value "Pena"}
                                                        :sukunimi {:modified 1379419361123
                                                                   :value "Panaani"}
                                                        :turvakieltoKytkin {:modified 1379419361123
                                                                            :value true}}
                                        :yhteystiedot {:email {:modified 1379419361123
                                                               :value "pena@example.com"}
                                                       :puhelin {:modified 1379419361123
                                                                 :value "0102030405"}}
                                        :osoite {:katu {:modified 1379419361123
                                                        :value "Paapankuja 12"}
                                                 :postinumero {:modified 1379419361123
                                                               :value "10203"}
                                                 :postitoimipaikannimi {:modified 1379419361123
                                                                        :value "Piippola"}}}
                              :_selected {:value "henkilo"}}})

(def ^:private uusi {:created 1379419361123
                     :data {:toimenpiteet {:0 {:Toimenpide {:modified 1379419797183
                                                            :value "uusi"}
                                               :huoneistoja {:modified 1379419800278
                                                             :value "1"}
                                               :kayttotarkoitus {:modified 1379419795277
                                                                 :value "011 yhden asunnon talot"}
                                               :kerroksia {:modified 1379419801660
                                                           :value "2"}
                                               :kerrosala {:modified 1379419803640
                                                           :value "200"}
                                               :kokonaisala {:modified 1379419807428
                                                             :value "220"}}
                                           :1 {:Toimenpide {:modified 1379419898562
                                                            :value "uusi"}
                                               :kayttotarkoitus {:modified 1379419887019
                                                                 :value "941 talousrakennukset"}
                                               :kerroksia {:modified 1379419905008
                                                           :value "1"}
                                               :kerrosala {:modified 1379419906955
                                                           :value "25"}
                                               :kokonaisala {:modified 1379419908874
                                                             :value "30"}}}}
                     :id "523844e1da063788effc1c57"
                     :schema-info {:order 50
                                   :version 1
                                   :name "rakennushanke"
                                   :op {:id "523844e1da063788effc1c56"
                                        :name "poikkeamis"
                                        :created 1379419361123}
                                   :removable true}})

(def ^:private hanke {:created 1379419361123
                      :data {:kuvaus {:modified 1379419749140
                                      :value "Omakotitalon ja tallin rakentaminen."}
                             :poikkeamat {:modified 1379419775530
                                          :value "Alueelle ei voimassa olevaa kaava."}}
                      :id "523844e1da063788effc1c59"
                      :schema-info {:approvable true
                                    :name "hankkeen-kuvaus"
                                    :version 1
                                    :order 1}})

(def ^:private maksaja {:created 1379419361123
                        :data {:_selected {:modified 1379484845705
                                           :value "yritys"}
                               :laskuviite {:modified 1379484949604
                                            :value "LVI99997"}
                               :yritys {:liikeJaYhteisoTunnus {:modified 1379484890671
                                                               :value "1743842-0"}
                                        :osoite {:katu {:modified 1379484901862
                                                        :value "Koivukuja 2"}
                                                 :postinumero {:modified 1379484904468
                                                               :value "23500"}
                                                 :postitoimipaikannimi {:modified 1379484907792
                                                                        :value "Helsinki"}}
                                        :yhteyshenkilo {:henkilotiedot {:etunimi {:modified 1379484913565
                                                                                  :value "Toimi"}
                                                                        :sukunimi {:modified 1379484915276
                                                                                   :value "Toimari"}}
                                                        :yhteystiedot {:email {:modified 1379484933582
                                                                               :value "paajehu@yit.foo"}
                                                                       :puhelin {:modified 1379484919565
                                                                                 :value "020202"}}}
                                        :yritysnimi {:modified 1379484849150
                                                     :value "YIT"}}}
                        :id "523844e1da063788effc1c5a"
                        :schema-info {:approvable true
                                      :name "maksaja"
                                      :removable true
                                      :repeating true
                                      :version 1
                                      :type "party"
                                      :order 6}})

(def ^:private rakennuspaikka {:created 1379419361123
                               :data {:hallintaperuste {:modified 1379419781683
                                                        :value "oma"}
                                      :kaavanaste {:modified 1379419785786
                                                   :value "ei kaavaa"}
                            :kiinteisto {:maaraalaTunnus {:modified 1379422953978
                                                          :value "0008"}
                                         :tilanNimi {:modified 1379422953979
                                                     :value "Omatila"}}}
                               :id "523844e1da063788effc1c5b"
                               :schema-info {:approvable true
                                           :name "rakennuspaikka"
                                           :version 1
                                           :order 2}})

(def ^:private lisatieto {:id "523844e1da063788effc1c5c"
                :schema-info {:name "lisatiedot"
                              :version 1
                              :order 100}
                :created 1379419361123
                :data {}})

(def ^:private paasuunnittelija {:created 1379419361123
                                 :data {:henkilotiedot {:etunimi {:modified 1379421445541
                                           :value "Pena"}
                                 :sukunimi {:modified 1379421445541
                                            :value "Panaani"}
                                 :hetu {:modified 1379421445541
                                        :value "210281-9988"}}
                 :osoite {:katu {:modified 1379421445541
                                 :value "Paapankuja 12"}
                          :postinumero {:modified 1379421453475
                                        :value "10203"}
                          :postitoimipaikannimi {:modified 1379421445541
                                                 :value "Piippola"}}
                 :patevyys {:koulutus {:modified 1379421460882
                                       :value "Arkkitehti"}
                            :patevyysluokka {:modified 1379421462104
                                             :value "AA"}}
                 :userId {:modified 1379421445564
                          :value "777777777777777777000020"}
                 :yhteystiedot {:email {:modified 1379421445541
                                        :value "pena@example.com"}
                                :puhelin {:modified 1379421445541
                                          :value "0102030405"}}}
          :id "523844e1da063788effc1c5d"
          :schema-info {:approvable true
                               :name "paasuunnittelija"
                               :removable false
                               :version 1
                               :type "party"
                               :order 4}})

(def ^:private suunnittelija {:id "523844e1da063788effc1c5e"
                                           :schema-info {:approvable true
                                                         :name "suunnittelija"
                                                         :removable true
                                                         :repeating true
                                                         :version 1
                                                         :type "party"
                                                         :order 5}
                                           :created 1379419361123
                                           :data {:henkilotiedot
                                                  {:etunimi {:modified 1380191655585 :value "Pena"}
                                                   :sukunimi {:modified 1380191655585 :value "Panaani"}
                                                   :hetu {:modified 1380191655585 :value "210281-9988"}}
                                                  :kuntaRoolikoodi {:modified 1380191654305 :value "KVV-suunnittelija"}
                                                  :osoite {:katu {:modified 1380191655585 :value "Paapankuja 12"}
                                                           :postinumero {:modified 1380191660158 :value "10203"}
                                                           :postitoimipaikannimi {:modified 1380191655585 :value "Piippola"}}
                                                  :patevyys {:koulutus {:modified 1380191688364 :value "El\u00e4m\u00e4n koulu"}
                                                             :patevyysluokka {:modified 1380191690366 :value "C"}}
                                                  :userId {:modified 1380191655618 :value "777777777777777777000020"}
                                                  :yhteystiedot {:email {:modified 1380191655585 :value "pena@example.com"}
                                                                 :puhelin {:modified 1380191655585 :value "0102030405"}}
                                                  :yritys {:liikeJaYhteisoTunnus {:modified 1380191678631 :value "1743842-0"}
                                                           :yritysnimi {:modified 1380191663668 :value "ewq"}}}})

(def ^:private tyonjohtaja {:id "523844e1da063788effc1c5e"
                            :schema-info {:approvable true
                                          :name "tyonjohtaja"
                                          :removable true
                                          :repeating true
                                          :version 1
                                          :type "party"
                                          :order 6}
                            :created 1379419361123
                            :data {:henkilotiedot
                                   {:etunimi {:modified 1380191655585 :value "Pena"}
                                    :sukunimi {:modified 1380191655585 :value "Panaani"}
                                    :hetu {:modified 1380191655585 :value "210281-9988"}}
                                   :kuntaRoolikoodi {:modified 1380191654305 :value "KVV-ty\u00f6njohtaja"}
                                   :osoite {:katu {:modified 1380191655585 :value "Paapankuja 12"}
                                            :postinumero {:modified 1380191660158 :value "10203"}
                                            :postitoimipaikannimi {:modified 1380191655585 :value "Piippola"}}
                                   :patevyys {:koulutus {:modified 1380191688364 :value "El\u00e4m\u00e4n koulu"}
                                              :patevyysvaatimusluokka {:modified 1380191690366 :value "AA"}
                                              :valmistumisvuosi {:modified 1380191690366 :value "2000"}}
                                   :userId {:modified 1380191655618 :value "777777777777777777000020"}
                                   :yhteystiedot {:email {:modified 1380191655585 :value "pena@example.com"}
                                                  :puhelin {:modified 1380191655585 :value "0102030405"}}
                                   :yritys {:liikeJaYhteisoTunnus {:modified 1380191678631 :value "1743842-0"}
                                            :yritysnimi {:modified 1380191663668 :value "ewq"}}}})

(def ^:private lisaosa {:created 1379419361123
                        :data {:kaavoituksen_ja_alueiden_tilanne {:rajoittuuko_tiehen {:modified 1379419814128
                                                                                       :value true}
                                                                  :tienkayttooikeus {:modified 1379419818358
                                                                                     :value true}}
                               :luonto_ja_kulttuuri {:kulttuurisesti_merkittava {:modified 1379419923400
                                                                                 :value true}}
                               :maisema {:metsan_reunassa {:modified 1379419918776
                                                           :value true}
                                         :metsassa {:modified 1379419917695
                                                    :value false}}
                               :merkittavyys {:rakentamisen_vaikutusten_merkittavyys {:modified 1379419957431
                                                                                      :value "Vain pient\u00e4 maisemallista haittaa."}}
                               :muut_vaikutukset {:etaisyys_viemariverkosta {:modified 1379419943875
                                                                             :value "2000"}
                                                  :pohjavesialuetta {:modified 1379419946000
                                                           :value true}}
                               :vaikutukset_yhdyskuntakehykselle {:etaisyys_kauppaan {:modified 1379419829980
                                                                                      :value "12"}
                                                                  :etaisyys_kuntakeskuksen_palveluihin {:modified 1379419841904
                                                                                                        :value "12"}
                                                                  :etaisyys_paivakotiin {:modified 1379419835022
                                                                                         :value "11"}
                                                                  :etaisyyys_kouluun {:modified 1379419824940
                                                                                      :value "10"}
                                                                  :muita_vaikutuksia {:modified 1379419877240
                                                                                      :value "Maisemallisesti talo tulee sijoittumaan m\u00e4en harjalle."}}
                               :virkistys_tarpeet {:ulkoilu_ja_virkistysaluetta_varattu {:modified 1379419934504
                                                                                         :value true}}}
              :id "523844e1da063788effc1c5f"
              :schema-info {:name "suunnittelutarveratkaisun-lisaosa"
                            :version 1
                            :order 52}})

(def ^:private documents [hakija
                uusi
                hanke
                maksaja
                rakennuspaikka
                lisatieto
                paasuunnittelija
                suunnittelija
                tyonjohtaja
                lisaosa])

(fact "Meta test: hakija"          hakija           => valid-against-current-schema?)
(fact "Meta test: uusi"            uusi             => valid-against-current-schema?)
(fact "Meta test: maksaja"         maksaja          => valid-against-current-schema?)
(fact "Meta test: rakennusapikka"  rakennuspaikka   => valid-against-current-schema?)
(fact "Meta test: lisatieto"       lisatieto        => valid-against-current-schema?)
(fact "Meta test: paasunnitelija"  paasuunnittelija => valid-against-current-schema?)
(fact "Meta test: suunnittelija"   suunnittelija    => valid-against-current-schema?)
(fact "Meta test: tyonjohtaja"     tyonjohtaja      => valid-against-current-schema?)
(fact "Meta test: lisaosa"         lisaosa          => valid-against-current-schema?)


(def ^:private poikkari-hakemus {:schema-version 1
                       :auth [{:lastName "Panaani"
                               :firstName "Pena"
                               :username "pena"
                               :type "owner"
                               :role "owner"
                               :id "777777777777777777000020"} {:id "777777777777777777000023"
                                                                :username "sonja"
                                                                :firstName "Sonja"
                                                                :lastName "Sibbo"
                                                                :role "writer"}]
                       :submitted 1379422973832
                       :state "submitted"
                       :location {:x 404174.92749023
                                  :y 6690687.4923706}
                       :attachments [{:id "52385207da063788effc1e24"
                                      :latestVersion {:fileId "52385207da063788effc1e21"
                                                      :version {:major 1
                                                                :minor 0}
                                                      :size 76202
                                                      :created 1379422727372
                                                      :filename "3171_001taksi.pdf"
                                                      :contentType "application/pdf"
                                                      :user {:role "applicant"
                                                             :lastName "Panaani"
                                                             :firstName "Pena"
                                                             :username "pena"
                                                             :id "777777777777777777000020"}
                                                      :stamped false
                                                      :accepted nil}
                                      :locked false
                                      :modified 1379422727372
                                      :op nil
                                      :state "requires_authority_action"
                                      :target nil
                                      :type {:type-group "paapiirustus"
                                             :type-id "asemapiirros"}
                                      :versions [{:fileId "52385207da063788effc1e21"
                                                  :version {:major 1
                                                            :minor 0}
                                                  :size 76202
                                                  :created 1379422727372
                                                  :filename "3171_001taksi.pdf"
                                                  :contentType "application/pdf"
                                                  :user {:role "applicant"
                                                         :lastName "Panaani"
                                                         :firstName "Pena"
                                                         :username "pena"
                                                         :id "777777777777777777000020"}
                                                  :stamped false
                                                  :accepted nil}]} {:id "5238538dda063788effc1eb2"
                                                                    :latestVersion {:fileId "5238538dda063788effc1eaf"
                                                                                    :version {:major 0
                                                                                              :minor 1}
                                                                                    :size 15199
                                                                                    :created 1379423117449
                                                                                    :filename "3112_001.pdf"
                                                                                    :contentType "application/pdf"
                                                                                    :user {:role "authority"
                                                                                           :lastName "Sibbo"
                                                                                           :firstName "Sonja"
                                                                                           :username "sonja"
                                                                                           :id "777777777777777777000023"}
                                                                                    :stamped false
                                                                                    :accepted nil}
                                                                    :locked true
                                                                    :modified 1379423117449
                                                                    :op nil
                                                                    :state "requires_authority_action"
                                                                    :target {:type "statement"
                                                                             :id "52385377da063788effc1e93"}
                                                                    :type {:type-group "muut"
                                                                           :type-id "muu"}
                                                                    :versions [{:fileId "5238538dda063788effc1eaf"
                                                                                :version {:major 0
                                                                                          :minor 1}
                                                                                :size 15199
                                                                                :created 1379423117449
                                                                                :filename "3112_001.pdf"
                                                                                :contentType "application/pdf"
                                                                                :user {:role "authority"
                                                                                       :lastName "Sibbo"
                                                                                       :firstName "Sonja"
                                                                                       :username "sonja"
                                                                                       :id "777777777777777777000023"}
                                                                                :stamped false
                                                                                :accepted nil}]}]
                       :statements statements
                       :organization "753-P"
                       :title "S\u00f6derkullantie 146"
                       :operations [{:id "523844e1da063788effc1c56"
                                     :name "poikkeamis"
                                     :created 1379419361123}]
                       :infoRequest false
                       :opened 1379422973832
                       :created 1379419361123
                       :propertyId "75342700020063"
                       :documents documents
                       :_statements-seen-by {:777777777777777777000023 1379423134104}
                       :_software_version "1.0.5"
                       :modified 1379423133065
                       :allowedAttachmentTypes [["hakija" ["valtakirja" "ote_kauppa_ja_yhdistysrekisterista" "ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta"]] ["rakennuspaikan_hallinta" ["jaljennos_myonnetyista_lainhuudoista" "jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta" "rasitustodistus" "todistus_erityisoikeuden_kirjaamisesta" "jaljennos_vuokrasopimuksesta" "jaljennos_perunkirjasta"]] ["rakennuspaikka" ["ote_alueen_peruskartasta" "ote_asemakaavasta_jos_asemakaava_alueella" "ote_kiinteistorekisteristerista" "tonttikartta_tarvittaessa" "selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista" "kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma"]] ["paapiirustus" ["asemapiirros" "pohjapiirros" "leikkauspiirros" "julkisivupiirros"]] ["ennakkoluvat_ja_lausunnot" ["naapurien_suostumukset" "selvitys_naapurien_kuulemisesta" "elyn_tai_kunnan_poikkeamapaatos" "suunnittelutarveratkaisu" "ymparistolupa"]] ["muut" ["selvitys_rakennuspaikan_terveellisyydesta" "selvitys_rakennuspaikan_korkeusasemasta" "selvitys_liittymisesta_ymparoivaan_rakennuskantaan" "julkisivujen_varityssuunnitelma" "selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta" "piha_tai_istutussuunnitelma" "selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta" "selvitys_rakennuksen_kosteusteknisesta_toimivuudesta" "selvitys_rakennuksen_aaniteknisesta_toimivuudesta" "selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista" "energiataloudellinen_selvitys" "paloturvallisuussuunnitelma" "liikkumis_ja_esteettomyysselvitys" "kerrosalaselvitys" "vaestonsuojasuunnitelma" "rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo" "selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo" "selvitys_kiinteiston_jatehuollon_jarjestamisesta" "rakennesuunnitelma" "ilmanvaihtosuunnitelma" "lammityslaitesuunnitelma" "radontekninen_suunnitelma" "kalliorakentamistekninen_suunnitelma" "paloturvallisuusselvitys" "suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta" "merkki_ja_turvavalaistussuunnitelma" "sammutusautomatiikkasuunnitelma" "rakennusautomaatiosuunnitelma" "valaistussuunnitelma" "selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta" "selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta" "muu"]]]
                       :comments [{:text ""
                                   :target {:type "attachment"
                                            :id "52385207da063788effc1e24"
                                            :version {:major 1
                                                      :minor 0}
                                            :filename "3171_001taksi.pdf"
                                            :fileId "52385207da063788effc1e21"}
                                   :created 1379422727372
                                   :to nil
                                   :user {:role "applicant"
                                          :lastName "Panaani"
                                          :firstName "Pena"
                                          :username "pena"
                                          :id "777777777777777777000020"}} {:text ""
                                                                            :target {:type "attachment"
                                                                                     :id "5238538dda063788effc1eb2"
                                                                                     :version {:major 0
                                                                                               :minor 1}
                                                                                     :filename "3112_001.pdf"
                                                                                     :fileId "5238538dda063788effc1eaf"}
                                                                            :created 1379423117449
                                                                            :to nil
                                                                            :user {:role "authority"
                                                                                   :lastName "Sibbo"
                                                                                   :firstName "Sonja"
                                                                                   :username "sonja"
                                                                                   :id "777777777777777777000023"}} {:text "Hakemukselle lis\u00e4tty lausunto."
                                                                                                                     :target {:type "statement"
                                                                                                                              :id "52385377da063788effc1e93"}
                                                                                                                     :created 1379423133065
                                                                                                                     :to nil
                                                                                                                     :user {:role "authority"
                                                                                                                            :lastName "Sibbo"
                                                                                                                            :firstName "Sonja"
                                                                                                                            :username "sonja"
                                                                                                                            :id "777777777777777777000023"}}]
                       :address "S\u00f6derkullantie 146"
                       :permitType "P"
                       :permitSubtype "poikkeamislupa"
                       :id "LP-753-2013-00001"
                       :municipality "753"})


(validate-all-documents documents)

(fl/fact*
  (let [canonical (c/poikkeus-application-to-canonical poikkari-hakemus "fi" ) => truthy

        Popast (:Popast canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Popast) => truthy
        aineistonnimi (:aineistonnimi toimituksenTiedot) => (:title poikkari-hakemus)
        aineistotoimittaja (:aineistotoimittaja toimituksenTiedot) => "lupapiste@solita.fi"
        tila (:tila toimituksenTiedot) => "keskener\u00e4inen"
        kuntakoodi (:kuntakoodi toimituksenTiedot) => (:municipality poikkari-hakemus)

        suunnittelutarveasia (:suunnittelutarveasia Popast) => nil
        poikkeamisasiatieto (:poikkeamisasiatieto Popast) => truthy
        Poikkeamisasia (:Poikkeamisasia poikkeamisasiatieto)
        ;abstarctPoikkeamistype
        kasittelynTilatieto (:kasittelynTilatieto Poikkeamisasia) => truthy
        Tilamuutos (:Tilamuutos kasittelynTilatieto) => truthy
        pvm (:pvm Tilamuutos) => "2013-09-17"

        kuntakoodi (:kuntakoodi Poikkeamisasia) => (:municipality poikkari-hakemus)

        luvanTunnistetiedot (:luvanTunnistetiedot Poikkeamisasia) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        tunnus (:tunnus MuuTunnus) => "LP-753-2013-00001"
        sovellus (:sovellus MuuTunnus) => "Lupapiste"

        osapuolettieto (:osapuolettieto Poikkeamisasia) => truthy
        Osapuolet (:Osapuolet osapuolettieto) => truthy
        osapuolitieto (:osapuolitieto Osapuolet) => truthy

        ;Maksaja
        maksaja (some #(when (= (get-in % [:Osapuoli :VRKrooliKoodi] %) "maksaja") %) osapuolitieto) => truthy
        Osapuoli (:Osapuoli maksaja) => truthy
        _ (:turvakieltoKytkin Osapuoli) => false
        henkilo (:henkilo Osapuoli) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Toimi"
        _ (get-in henkilo [:nimi :sukunimi]) => "Toimari"
        _ (:puhelin henkilo) => "020202"
        _ (:sahkopostiosoite henkilo) => "paajehu@yit.foo"
        yritys (:yritys Osapuoli) => truthy
        _ (:nimi yritys ) => "YIT"
        _ (:liikeJaYhteisotunnus yritys) => "1743842-0"
        postiosoite (:postiosoite yritys) => truthy
        _ (get-in postiosoite [:osoitenimi :teksti]) => "Koivukuja 2"
        _ (:postinumero postiosoite) => "23500"
        _ (:postitoimipaikannimi postiosoite) => "Helsinki"

        ;Hakija
        hakija (some #(when (= (get-in % [:Osapuoli :VRKrooliKoodi] %) "hakija") %) osapuolitieto) => truthy
        Osapuoli (:Osapuoli hakija) => truthy
        _ (:turvakieltoKytkin Osapuoli) => true
        henkilo (:henkilo Osapuoli) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:henkilotunnus henkilo) => "210281-9988"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        yritys (:yritys Osapuoli) => nil

        ;Paassuunnittelija
        suunnittelijatieto (:suunnittelijatieto Osapuolet) => truthy
        paasuunnittelija (some #(when (= (get-in % [:Suunnittelija :VRKrooliKoodi] %) "p\u00e4\u00e4suunnittelija") %) suunnittelijatieto) => truthy
        Suunnittelija (:Suunnittelija paasuunnittelija) => truthy
        henkilo (:henkilo Suunnittelija) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:henkilotunnus henkilo) => "210281-9988"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:koulutus Suunnittelija) => "Arkkitehti"
        _ (:patevyysvaatimusluokka Suunnittelija) => "AA"

        ;Suunnittelija
        suunnittelijatieto (:suunnittelijatieto Osapuolet) => truthy
        suunnittelija (some #(when (= (get-in % [:Suunnittelija :suunnittelijaRoolikoodi] %) "KVV-suunnittelija") %) suunnittelijatieto) => truthy
        Suunnittelija (:Suunnittelija suunnittelija) => truthy
        henkilo (:henkilo Suunnittelija) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:henkilotunnus henkilo) => "210281-9988"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:koulutus Suunnittelija) => "El\u00e4m\u00e4n koulu"
        _ (:patevyysvaatimusluokka Suunnittelija) => "C"

        ;Tyonjohtaja
        tyonjohtajatieto (:tyonjohtajatieto Osapuolet) => truthy
        tyonjohtaja (some #(when (= (get-in % [:Tyonjohtaja :tyonjohtajaRooliKoodi] %) "KVV-ty\u00f6njohtaja") %) tyonjohtajatieto) => truthy
        Tyonjohtaja (:Tyonjohtaja tyonjohtaja) => truthy
        henkilo (:henkilo Tyonjohtaja) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:henkilotunnus henkilo) => "210281-9988"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:koulutus Tyonjohtaja) => "El\u00e4m\u00e4n koulu"
        _ (:valmistumisvuosi Tyonjohtaja) => "2000"
        _ (:patevyysvaatimusluokka Tyonjohtaja) => "AA"

        rakennuspaikkatieto (:rakennuspaikkatieto Poikkeamisasia) => truthy
        Rakennuspaikkaf (first rakennuspaikkatieto) => truthy
        Rakennuspaikka (:Rakennuspaikka Rakennuspaikkaf) => truthy
        rakennuspaikanKiinteistotieto (:rakennuspaikanKiinteistotieto Rakennuspaikka) => truthy
        RakennuspaikanKiinteisto (:RakennuspaikanKiinteisto rakennuspaikanKiinteistotieto) => truthy
        kiinteistotieto (:kiinteistotieto RakennuspaikanKiinteisto) => truthy
        Kiinteisto (:Kiinteisto kiinteistotieto) => truthy
        tilannimi (:tilannimi Kiinteisto) => "Omatila"
        kiinteistotunnus (:kiinteistotunnus Kiinteisto) => "75342700020063"
        maaraalaTunnus (:maaraAlaTunnus Kiinteisto) => "M0008"
        kokotilaKytkin (:kokotilaKytkin RakennuspaikanKiinteisto) => false
        hallintaperuste (:hallintaperuste RakennuspaikanKiinteisto) => "oma"

;        toimenpidetieto (:toimenpidetieto Poikkeamisasia) => truthy
;        toimenpide-count (count toimenpidetieto) => 2
;        uusi (some #(when (= (get-in % [:Toimenpide :kuvausKoodi]) "uusi") %) toimenpidetieto)







        ;paaKayttotarkoitus ???
        ;rantaKytkin ???



        ;end of abstarctPoikkeamistype
        ;asianTiedot
        ;kaytttotapaus
        ;_ (clojure.pprint/pprint canonical)
        ]))





