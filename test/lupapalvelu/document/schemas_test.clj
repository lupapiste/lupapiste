(ns lupapalvelu.document.schemas-test
  (:use [lupapalvelu.document.schemas]
        [midje.sweet]))

(facts "body"
  (fact "flattens stuff into lists"    (body 1 2 [3 4] 5) => [1 2 3 4 5])
  (fact "does not flatten recursively" (body 1 2 [3 4 [5]]) => [1 2 3 4 [5]]))

(facts "repeatable"
  (fact (repeatable "beers" {:name :beer
                             :type :string}) => [{:name "beers"
                                                  :type :group
                                                  :repeating true
                                                  :body [{:name :beer
                                                          :type :string}]}]))


(def applivation-with-invalid-schema {:schema-version 1
                                      :auth [{:lastName "Panaani"
                                              :firstName "Pena"
                                              :username "pena"
                                              :type "owner"
                                              :role "owner"
                                              :id "777777777777777777000020"}]
                                      :state "draft"
                                      :location {:x 406174.92749023
                                                 :y 6683131.942749}
                                      :attachments [{:id "524128df03640915c932a645"
                                                     :type {:type-group "paapiirustus"
                                                            :type-id "asemapiirros"}
                                                     :modified 1380002015862
                                                     :locked false
                                                     :state "requires_user_action"
                                                     :target nil
                                                     :op {:id "524128df03640915c932a642"
                                                          :name "asuinrakennus"
                                                          :created 1380002015862}
                                                     :versions []} {:id "524128df03640915c932a646"
                                                                    :type {:type-group "paapiirustus"
                                                                           :type-id "pohjapiirros"}
                                                                    :modified 1380002015862
                                                                    :locked false
                                                                    :state "requires_user_action"
                                                                    :target nil
                                                                    :op {:id "524128df03640915c932a642"
                                                                         :name "asuinrakennus"
                                                                         :created 1380002015862}
                                                                    :versions []} {:id "524128df03640915c932a647"
                                                                                   :type {:type-group "hakija"
                                                                                          :type-id "valtakirja"}
                                                                                   :modified 1380002015862
                                                                                   :locked false
                                                                                   :state "requires_user_action"
                                                                                   :target nil
                                                                                   :op {:id "524128df03640915c932a642"
                                                                                        :name "asuinrakennus"
                                                                                        :created 1380002015862}
                                                                                   :versions []} {:id "524128df03640915c932a648"
                                                                                                  :type {:type-group "muut"
                                                                                                         :type-id "vaestonsuojasuunnitelma"}
                                                                                                  :modified 1380002015862
                                                                                                  :locked false
                                                                                                  :state "requires_user_action"
                                                                                                  :target nil
                                                                                                  :op {:id "524128df03640915c932a642"
                                                                                                       :name "asuinrakennus"
                                                                                                       :created 1380002015862}
                                                                                                  :versions []}]
                                      :organization "753-R"
                                      :title "Porvoonväylä 963"
                                      :operations [{:id "524128df03640915c932a642"
                                                    :name "asuinrakennus"
                                                    :created 1380002015862} {:id "524128f403640915c932a67a"
                                                                             :name "perus-tai-kant-rak-muutos"
                                                                             :created 1380002036931} {:id "524128f803640915c932a68c"
                                                                                                      :name "perus-tai-kant-rak-muutos"
                                                                                                      :created 1380002040687} {:id "5241290c03640915c932a6a2"
                                                                                                                               :name "purkaminen"
                                                                                                                               :created 1380002060590} {:id "5241291803640915c932a6ba"
                                                                                                                                                        :name "purkaminen"
                                                                                                                                                        :created 1380002072628} {:id "5241292a03640915c932a6d6"
                                                                                                                                                                                 :name "tontin-ajoliittyman-muutos"
                                                                                                                                                                                 :created 1380002090660}]
                                      :infoRequest false
                                      :openInfoRequest false
                                      :opened nil
                                      :created 1380002015862
                                      :propertyId "75342900030044"
                                      :documents [{:id "524128df03640915c932a644"
                                                   :schema-info {:approvable true
                                                                 :subtype "hakija"
                                                                 :name "hakija"
                                                                 :removable true
                                                                 :repeating true
                                                                 :version 1
                                                                 :type "party"
                                                                 :order 3}
                                                   :created 1380002015862
                                                   :data {:yritys {:yhteyshenkilo {:henkilotiedot {:etunimi {:modified 1380002015862
                                                                                                             :value "Pena"}
                                                                                                   :sukunimi {:modified 1380002015862
                                                                                                              :value "Panaani"}}
                                                                                   :yhteystiedot {:email {:modified 1380002015862
                                                                                                          :value "pena@example.com"}
                                                                                                  :puhelin {:modified 1380002015862
                                                                                                            :value "0102030405"}}}
                                                                   :osoite {:katu {:modified 1380002015862
                                                                                   :value "Paapankuja 12"}
                                                                            :postinumero {:modified 1380002015862
                                                                                          :value "010203"}
                                                                            :postitoimipaikannimi {:modified 1380002015862
                                                                                                   :value "Piippola"}}}
                                                          :henkilo {:userId {:modified 1380002015862
                                                                             :value "777777777777777777000020"}
                                                                    :henkilotiedot {:hetu {:modified 1380002015862
                                                                                           :value "010203-0405"}
                                                                                    :etunimi {:modified 1380002015862
                                                                                              :value "Pena"}
                                                                                    :sukunimi {:modified 1380002015862
                                                                                               :value "Panaani"}}
                                                                    :yhteystiedot {:email {:modified 1380002015862
                                                                                           :value "pena@example.com"}
                                                                                   :puhelin {:modified 1380002015862
                                                                                             :value "0102030405"}}
                                                                    :osoite {:katu {:modified 1380002015862
                                                                                    :value "Paapankuja 12"}
                                                                             :postinumero {:modified 1380002015862
                                                                                           :value "010203"}
                                                                             :postitoimipaikannimi {:modified 1380002015862
                                                                                                    :value "Piippola"}}}
                                                          :_selected {:value "henkilo"}}} {:id "524128df03640915c932a643"
                                                                                           :schema-info {:version 1
                                                                                                         :name "uusiRakennus"
                                                                                                         :approvable true
                                                                                                         :op {:id "524128df03640915c932a642"
                                                                                                              :name "asuinrakennus"
                                                                                                              :created 1380002015862}
                                                                                                         :removable true}
                                                                                           :created 1380002015862
                                                                                           :data {:huoneistot {:0 {:huoneistoTunnus {:huoneistonumero {:modified 1380002015862
                                                                                                                                                       :value "001"}}}}
                                                                                                  :kaytto {:kayttotarkoitus {:modified 1380002015862
                                                                                                                             :value "011 yhden asunnon talot"}}}} {:id "524128df03640915c932a649"
                                                                                                                                                                   :schema-info {:approvable true
                                                                                                                                                                                 :name "hankkeen-kuvaus"
                                                                                                                                                                                 :version 1
                                                                                                                                                                                 :order 1}
                                                                                                                                                                   :created 1380002015862
                                                                                                                                                                   :data {}} {:id "524128df03640915c932a64a"
                                                                                                                                                                              :schema-info {:approvable true
                                                                                                                                                                                            :name "maksaja"
                                                                                                                                                                                            :removable true
                                                                                                                                                                                            :repeating true
                                                                                                                                                                                            :version 1
                                                                                                                                                                                            :type "party"
                                                                                                                                                                                            :order 6}
                                                                                                                                                                              :created 1380002015862
                                                                                                                                                                              :data {}} {:id "524128df03640915c932a64b"
                                                                                                                                                                                         :schema-info {:approvable true
                                                                                                                                                                                                       :name "rakennuspaikka"
                                                                                                                                                                                                       :version 1
                                                                                                                                                                                                       :order 2}
                                                                                                                                                                                         :created 1380002015862
                                                                                                                                                                                         :data {}} {:id "524128df03640915c932a64c"
                                                                                                                                                                                                    :schema-info {:name "lisatiedot"
                                                                                                                                                                                                                  :version 1
                                                                                                                                                                                                                  :order 100}
                                                                                                                                                                                                    :created 1380002015862
                                                                                                                                                                                                    :data {}} {:id "524128df03640915c932a64d"
                                                                                                                                                                                                               :schema-info {:approvable true
                                                                                                                                                                                                                             :name "paasuunnittelija"
                                                                                                                                                                                                                             :removable false
                                                                                                                                                                                                                             :version 1
                                                                                                                                                                                                                             :type "party"
                                                                                                                                                                                                                             :order 4}
                                                                                                                                                                                                               :created 1380002015862
                                                                                                                                                                                                               :data {}} {:id "524128df03640915c932a64e"
                                                                                                                                                                                                                          :schema-info {:approvable true
                                                                                                                                                                                                                                        :name "suunnittelija"
                                                                                                                                                                                                                                        :removable true
                                                                                                                                                                                                                                        :repeating true
                                                                                                                                                                                                                                        :version 1
                                                                                                                                                                                                                                        :type "party"
                                                                                                                                                                                                                                        :order 5}
                                                                                                                                                                                                                          :created 1380002015862
                                                                                                                                                                                                                          :data {}} {:id "524128f403640915c932a67b"
                                                                                                                                                                                                                                     :schema-info {:version 1
                                                                                                                                                                                                                                                   :name "rakennuksen-muuttaminen"
                                                                                                                                                                                                                                                   :approvable true
                                                                                                                                                                                                                                                   :op {:id "524128f403640915c932a67a"
                                                                                                                                                                                                                                                        :name "perus-tai-kant-rak-muutos"
                                                                                                                                                                                                                                                        :created 1380002036931}
                                                                                                                                                                                                                                                   :removable true}
                                                                                                                                                                                                                                     :created 1380002036931
                                                                                                                                                                                                                                     :data {:muutostyolaji {:modified 1380002036931
                                                                                                                                                                                                                                                            :value "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"}}} {:id "524128f803640915c932a68d"
                                                                                                                                                                                                                                                                                                                                     :schema-info {:version 1
                                                                                                                                                                                                                                                                                                                                                   :name "rakennuksen-muuttaminen"
                                                                                                                                                                                                                                                                                                                                                   :approvable true
                                                                                                                                                                                                                                                                                                                                                   :op {:id "524128f803640915c932a68c"
                                                                                                                                                                                                                                                                                                                                                        :name "perus-tai-kant-rak-muutos"
                                                                                                                                                                                                                                                                                                                                                        :created 1380002040687}
                                                                                                                                                                                                                                                                                                                                                   :removable true}
                                                                                                                                                                                                                                                                                                                                     :created 1380002040687
                                                                                                                                                                                                                                                                                                                                     :data {:muutostyolaji {:modified 1380002040687
                                                                                                                                                                                                                                                                                                                                                            :value "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"}}} {:id "5241290c03640915c932a6a3"
                                                                                                                                                                                                                                                                                                                                                                                                                                     :schema-info {:version 1
                                                                                                                                                                                                                                                                                                                                                                                                                                                   :name "purku"
                                                                                                                                                                                                                                                                                                                                                                                                                                                   :approvable true
                                                                                                                                                                                                                                                                                                                                                                                                                                                   :op {:id "5241290c03640915c932a6a2"
                                                                                                                                                                                                                                                                                                                                                                                                                                                        :name "purkaminen"
                                                                                                                                                                                                                                                                                                                                                                                                                                                        :created 1380002060590}
                                                                                                                                                                                                                                                                                                                                                                                                                                                   :removable true}
                                                                                                                                                                                                                                                                                                                                                                                                                                     :created 1380002060590
                                                                                                                                                                                                                                                                                                                                                                                                                                     :data {}} {:id "5241291803640915c932a6bb"
                                                                                                                                                                                                                                                                                                                                                                                                                                                :schema-info {:version 1
                                                                                                                                                                                                                                                                                                                                                                                                                                                              :name "purku"
                                                                                                                                                                                                                                                                                                                                                                                                                                                              :approvable true
                                                                                                                                                                                                                                                                                                                                                                                                                                                              :op {:id "5241291803640915c932a6ba"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                   :name "purkaminen"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                   :created 1380002072628}
                                                                                                                                                                                                                                                                                                                                                                                                                                                              :removable true}
                                                                                                                                                                                                                                                                                                                                                                                                                                                :created 1380002072628
                                                                                                                                                                                                                                                                                                                                                                                                                                                :data {}} {:id "5241292a03640915c932a6d7"
                                                                                                                                                                                                                                                                                                                                                                                                                                                           :schema-info {:version 1
                                                                                                                                                                                                                                                                                                                                                                                                                                                                         :name "maisematyo"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                         :approvable true
                                                                                                                                                                                                                                                                                                                                                                                                                                                                         :op {:id "5241292a03640915c932a6d6"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                              :name "tontin-ajoliittyman-muutos"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                              :created 1380002090660}
                                                                                                                                                                                                                                                                                                                                                                                                                                                                         :removable true}
                                                                                                                                                                                                                                                                                                                                                                                                                                                           :created 1380002090660
                                                                                                                                                                                                                                                                                                                                                                                                                                                           :data {}}]
                                      :_software_version "1.0.5"
                                      :modified 1380002090660
                                      :allowedAttachmentTypes [["hakija" ["valtakirja" "ote_kauppa_ja_yhdistysrekisterista" "ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta"]] ["rakennuspaikan_hallinta" ["jaljennos_myonnetyista_lainhuudoista" "jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta" "rasitustodistus" "todistus_erityisoikeuden_kirjaamisesta" "jaljennos_vuokrasopimuksesta" "jaljennos_perunkirjasta"]] ["rakennuspaikka" ["ote_alueen_peruskartasta" "ote_asemakaavasta_jos_asemakaava_alueella" "ote_kiinteistorekisteristerista" "tonttikartta_tarvittaessa" "selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista" "kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma"]] ["paapiirustus" ["asemapiirros" "pohjapiirros" "leikkauspiirros" "julkisivupiirros"]] ["ennakkoluvat_ja_lausunnot" ["naapurien_suostumukset" "selvitys_naapurien_kuulemisesta" "elyn_tai_kunnan_poikkeamapaatos" "suunnittelutarveratkaisu" "ymparistolupa"]] ["muut" ["selvitys_rakennuspaikan_terveellisyydesta" "selvitys_rakennuspaikan_korkeusasemasta" "selvitys_liittymisesta_ymparoivaan_rakennuskantaan" "julkisivujen_varityssuunnitelma" "selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta" "piha_tai_istutussuunnitelma" "selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta" "selvitys_rakennuksen_kosteusteknisesta_toimivuudesta" "selvitys_rakennuksen_aaniteknisesta_toimivuudesta" "selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista" "energiataloudellinen_selvitys" "paloturvallisuussuunnitelma" "liikkumis_ja_esteettomyysselvitys" "kerrosalaselvitys" "vaestonsuojasuunnitelma" "rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo" "selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo" "selvitys_kiinteiston_jatehuollon_jarjestamisesta" "rakennesuunnitelma" "ilmanvaihtosuunnitelma" "lammityslaitesuunnitelma" "radontekninen_suunnitelma" "kalliorakentamistekninen_suunnitelma" "paloturvallisuusselvitys" "suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta" "merkki_ja_turvavalaistussuunnitelma" "sammutusautomatiikkasuunnitelma" "rakennusautomaatiosuunnitelma" "valaistussuunnitelma" "selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta" "selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta" "muu"]]]
                                      :comments []
                                      :address "Porvoonväylä 963"
                                      :permitType "R"
                                      :id "LP-753-2013-00010"
                                      :municipality "753"})

(def applivation-with-valid-schema {:schema-version 1
                                    :auth [{:lastName "Panaani"
                                            :firstName "Pena"
                                            :username "pena"
                                            :type "owner"
                                            :role "owner"
                                            :id "777777777777777777000020"}]
                                    :state "draft"
                                    :location {:x 406174.92749023
                                               :y 6683131.942749}
                                    :attachments [{:id "524128df03640915c932a645"
                                                   :type {:type-group "paapiirustus"
                                                          :type-id "asemapiirros"}
                                                   :modified 1380002015862
                                                   :locked false
                                                   :state "requires_user_action"
                                                   :target nil
                                                   :op {:id "524128df03640915c932a642"
                                                        :name "asuinrakennus"
                                                        :created 1380002015862}
                                                   :versions []} {:id "524128df03640915c932a646"
                                                                  :type {:type-group "paapiirustus"
                                                                         :type-id "pohjapiirros"}
                                                                  :modified 1380002015862
                                                                  :locked false
                                                                  :state "requires_user_action"
                                                                  :target nil
                                                                  :op {:id "524128df03640915c932a642"
                                                                       :name "asuinrakennus"
                                                                       :created 1380002015862}
                                                                  :versions []} {:id "524128df03640915c932a647"
                                                                                 :type {:type-group "hakija"
                                                                                        :type-id "valtakirja"}
                                                                                 :modified 1380002015862
                                                                                 :locked false
                                                                                 :state "requires_user_action"
                                                                                 :target nil
                                                                                 :op {:id "524128df03640915c932a642"
                                                                                      :name "asuinrakennus"
                                                                                      :created 1380002015862}
                                                                                 :versions []} {:id "524128df03640915c932a648"
                                                                                                :type {:type-group "muut"
                                                                                                       :type-id "vaestonsuojasuunnitelma"}
                                                                                                :modified 1380002015862
                                                                                                :locked false
                                                                                                :state "requires_user_action"
                                                                                                :target nil
                                                                                                :op {:id "524128df03640915c932a642"
                                                                                                     :name "asuinrakennus"
                                                                                                     :created 1380002015862}
                                                                                                :versions []}]
                                    :organization "753-R"
                                    :title "Porvoonväylä 963"
                                    :operations [{:id "524128df03640915c932a642"
                                                  :name "asuinrakennus"
                                                  :created 1380002015862} {:id "524128f403640915c932a67a"
                                                                           :name "perus-tai-kant-rak-muutos"
                                                                           :created 1380002036931} {:id "524128f803640915c932a68c"
                                                                                                    :name "perus-tai-kant-rak-muutos"
                                                                                                    :created 1380002040687} {:id "5241290c03640915c932a6a2"
                                                                                                                             :name "purkaminen"
                                                                                                                             :created 1380002060590} {:id "5241291803640915c932a6ba"
                                                                                                                                                      :name "purkaminen"
                                                                                                                                                      :created 1380002072628} {:id "5241292a03640915c932a6d6"
                                                                                                                                                                               :name "tontin-ajoliittyman-muutos"
                                                                                                                                                                               :created 1380002090660}]
                                    :infoRequest false
                                    :openInfoRequest false
                                    :opened nil
                                    :created 1380002015862
                                    :propertyId "75342900030044"
                                    :documents [{:id "524128df03640915c932a644"
                                                 :schema-info {:approvable true
                                                               :subtype "hakija"
                                                               :name "hakija"
                                                               :removable true
                                                               :repeating true
                                                               :version 1
                                                               :type "party"
                                                               :order 3}
                                                 :created 1380002015862
                                                 :data {:yritys {:yhteyshenkilo {:henkilotiedot {:etunimi {:modified 1380002015862
                                                                                                           :value "Pena"}
                                                                                                 :sukunimi {:modified 1380002015862
                                                                                                            :value "Panaani"}}
                                                                                 :yhteystiedot {:email {:modified 1380002015862
                                                                                                        :value "pena@example.com"}
                                                                                                :puhelin {:modified 1380002015862
                                                                                                          :value "0102030405"}}}
                                                                 :osoite {:katu {:modified 1380002015862
                                                                                 :value "Paapankuja 12"}
                                                                          :postinumero {:modified 1380002015862
                                                                                        :value "010203"}
                                                                          :postitoimipaikannimi {:modified 1380002015862
                                                                                                 :value "Piippola"}}}
                                                        :henkilo {:userId {:modified 1380002015862
                                                                           :value "777777777777777777000020"}
                                                                  :henkilotiedot {:hetu {:modified 1380002015862
                                                                                         :value "010203-0405"}
                                                                                  :etunimi {:modified 1380002015862
                                                                                            :value "Pena"}
                                                                                  :sukunimi {:modified 1380002015862
                                                                                             :value "Panaani"}}
                                                                  :yhteystiedot {:email {:modified 1380002015862
                                                                                         :value "pena@example.com"}
                                                                                 :puhelin {:modified 1380002015862
                                                                                           :value "0102030405"}}
                                                                  :osoite {:katu {:modified 1380002015862
                                                                                  :value "Paapankuja 12"}
                                                                           :postinumero {:modified 1380002015862
                                                                                         :value "010203"}
                                                                           :postitoimipaikannimi {:modified 1380002015862
                                                                                                  :value "Piippola"}}}
                                                        :_selected {:value "henkilo"}}} {:id "524128df03640915c932a643"
                                                                                         :schema-info {:version 1
                                                                                                       :name "uusiRakennus"
                                                                                                       :approvable true
                                                                                                       :op {:id "524128df03640915c932a642"
                                                                                                            :name "asuinrakennus"
                                                                                                            :created 1380002015862}
                                                                                                       :removable true}
                                                                                         :created 1380002015862
                                                                                         :data {:huoneistot {:0 {:huoneistoTunnus {:huoneistonumero {:modified 1380002015862
                                                                                                                                                     :value "001"}}}}
                                                                                                :kaytto {:kayttotarkoitus {:modified 1380002015862
                                                                                                                           :value "011 yhden asunnon talot"}}}} {:id "524128df03640915c932a649"
                                                                                                                                                                 :schema-info {:approvable true
                                                                                                                                                                               :name "hankkeen-kuvaus"
                                                                                                                                                                               :version 1
                                                                                                                                                                               :order 1}
                                                                                                                                                                 :created 1380002015862
                                                                                                                                                                 :data {}} {:id "524128df03640915c932a64a"
                                                                                                                                                                            :schema-info {:approvable true
                                                                                                                                                                                          :name "maksaja"
                                                                                                                                                                                          :removable true
                                                                                                                                                                                          :repeating true
                                                                                                                                                                                          :version 1
                                                                                                                                                                                          :type "party"
                                                                                                                                                                                          :order 6}
                                                                                                                                                                            :created 1380002015862
                                                                                                                                                                            :data {}} {:id "524128df03640915c932a64b"
                                                                                                                                                                                       :schema-info {:approvable true
                                                                                                                                                                                                     :name "rakennuspaikka"
                                                                                                                                                                                                     :version 1
                                                                                                                                                                                                     :order 2}
                                                                                                                                                                                       :created 1380002015862
                                                                                                                                                                                       :data {}} {:id "524128df03640915c932a64c"
                                                                                                                                                                                                  :schema-info {:name "lisatiedot"
                                                                                                                                                                                                                :version 1
                                                                                                                                                                                                                :order 100}
                                                                                                                                                                                                  :created 1380002015862
                                                                                                                                                                                                  :data {}} {:id "524128df03640915c932a64d"
                                                                                                                                                                                                             :schema-info {:approvable true
                                                                                                                                                                                                                           :name "paasuunnittelija"
                                                                                                                                                                                                                           :removable false
                                                                                                                                                                                                                           :version 1
                                                                                                                                                                                                                           :type "party"
                                                                                                                                                                                                                           :order 4}
                                                                                                                                                                                                             :created 1380002015862
                                                                                                                                                                                                             :data {}} {:id "524128df03640915c932a64e"
                                                                                                                                                                                                                        :schema-info {:approvable true
                                                                                                                                                                                                                                      :name "suunnittelija"
                                                                                                                                                                                                                                      :removable true
                                                                                                                                                                                                                                      :repeating true
                                                                                                                                                                                                                                      :version 1
                                                                                                                                                                                                                                      :type "party"
                                                                                                                                                                                                                                      :order 5}
                                                                                                                                                                                                                        :created 1380002015862
                                                                                                                                                                                                                        :data {}} {:id "524128f403640915c932a67b"
                                                                                                                                                                                                                                   :schema-info {:version 1
                                                                                                                                                                                                                                                 :name "rakennuksen-muuttaminen"
                                                                                                                                                                                                                                                 :approvable true
                                                                                                                                                                                                                                                 :op {:id "524128f403640915c932a67a"
                                                                                                                                                                                                                                                      :name "perus-tai-kant-rak-muutos"
                                                                                                                                                                                                                                                      :created 1380002036931}
                                                                                                                                                                                                                                                 :removable true}
                                                                                                                                                                                                                                   :created 1380002036931
                                                                                                                                                                                                                                   :data {:muutostyolaji {:modified 1380002036931
                                                                                                                                                                                                                                                          :value "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"}}} {:id "524128f803640915c932a68d"
                                                                                                                                                                                                                                                                                                                                   :schema-info {:version 1
                                                                                                                                                                                                                                                                                                                                                 :name "rakennuksen-muuttaminen"
                                                                                                                                                                                                                                                                                                                                                 :approvable true
                                                                                                                                                                                                                                                                                                                                                 :op {:id "524128f803640915c932a68c"
                                                                                                                                                                                                                                                                                                                                                      :name "perus-tai-kant-rak-muutos"
                                                                                                                                                                                                                                                                                                                                                      :created 1380002040687}
                                                                                                                                                                                                                                                                                                                                                 :removable true}
                                                                                                                                                                                                                                                                                                                                   :created 1380002040687
                                                                                                                                                                                                                                                                                                                                   :data {:muutostyolaji {:modified 1380002040687
                                                                                                                                                                                                                                                                                                                                                          :value "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"}}} {:id "5241290c03640915c932a6a3"
                                                                                                                                                                                                                                                                                                                                                                                                                                   :schema-info {:version 1
                                                                                                                                                                                                                                                                                                                                                                                                                                                 :name "purku"
                                                                                                                                                                                                                                                                                                                                                                                                                                                 :approvable true
                                                                                                                                                                                                                                                                                                                                                                                                                                                 :op {:id "5241290c03640915c932a6a2"
                                                                                                                                                                                                                                                                                                                                                                                                                                                      :name "purkaminen"
                                                                                                                                                                                                                                                                                                                                                                                                                                                      :created 1380002060590}
                                                                                                                                                                                                                                                                                                                                                                                                                                                 :removable true}
                                                                                                                                                                                                                                                                                                                                                                                                                                   :created 1380002060590
                                                                                                                                                                                                                                                                                                                                                                                                                                   :data {}} {:id "5241291803640915c932a6bb"
                                                                                                                                                                                                                                                                                                                                                                                                                                              :schema-info {:version 1
                                                                                                                                                                                                                                                                                                                                                                                                                                                            :name "purku"
                                                                                                                                                                                                                                                                                                                                                                                                                                                            :approvable true
                                                                                                                                                                                                                                                                                                                                                                                                                                                            :op {:id "5241291803640915c932a6ba"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                 :name "purkaminen"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                 :created 1380002072628}
                                                                                                                                                                                                                                                                                                                                                                                                                                                            :removable true}
                                                                                                                                                                                                                                                                                                                                                                                                                                              :created 1380002072628
                                                                                                                                                                                                                                                                                                                                                                                                                                              :data {}} {:id "5241292a03640915c932a6d7"
                                                                                                                                                                                                                                                                                                                                                                                                                                                         :schema-info {:version 1
                                                                                                                                                                                                                                                                                                                                                                                                                                                                       :name "maisematyo"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                       :approvable true
                                                                                                                                                                                                                                                                                                                                                                                                                                                                       :op {:id "5241292a03640915c932a6d6"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                            :name "tontin-ajoliittyman-muutos"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                            :created 1380002090660}
                                                                                                                                                                                                                                                                                                                                                                                                                                                                       :removable true}
                                                                                                                                                                                                                                                                                                                                                                                                                                                         :created 1380002090660
                                                                                                                                                                                                                                                                                                                                                                                                                                                         :data {}}]
                                    :_software_version "1.0.5"
                                    :modified 1380002090660
                                    :allowedAttachmentTypes [["hakija" ["valtakirja" "ote_kauppa_ja_yhdistysrekisterista" "ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta"]] ["rakennuspaikan_hallinta" ["jaljennos_myonnetyista_lainhuudoista" "jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta" "rasitustodistus" "todistus_erityisoikeuden_kirjaamisesta" "jaljennos_vuokrasopimuksesta" "jaljennos_perunkirjasta"]] ["rakennuspaikka" ["ote_alueen_peruskartasta" "ote_asemakaavasta_jos_asemakaava_alueella" "ote_kiinteistorekisteristerista" "tonttikartta_tarvittaessa" "selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista" "kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma"]] ["paapiirustus" ["asemapiirros" "pohjapiirros" "leikkauspiirros" "julkisivupiirros"]] ["ennakkoluvat_ja_lausunnot" ["naapurien_suostumukset" "selvitys_naapurien_kuulemisesta" "elyn_tai_kunnan_poikkeamapaatos" "suunnittelutarveratkaisu" "ymparistolupa"]] ["muut" ["selvitys_rakennuspaikan_terveellisyydesta" "selvitys_rakennuspaikan_korkeusasemasta" "selvitys_liittymisesta_ymparoivaan_rakennuskantaan" "julkisivujen_varityssuunnitelma" "selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta" "piha_tai_istutussuunnitelma" "selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta" "selvitys_rakennuksen_kosteusteknisesta_toimivuudesta" "selvitys_rakennuksen_aaniteknisesta_toimivuudesta" "selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista" "energiataloudellinen_selvitys" "paloturvallisuussuunnitelma" "liikkumis_ja_esteettomyysselvitys" "kerrosalaselvitys" "vaestonsuojasuunnitelma" "rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo" "selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo" "selvitys_kiinteiston_jatehuollon_jarjestamisesta" "rakennesuunnitelma" "ilmanvaihtosuunnitelma" "lammityslaitesuunnitelma" "radontekninen_suunnitelma" "kalliorakentamistekninen_suunnitelma" "paloturvallisuusselvitys" "suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta" "merkki_ja_turvavalaistussuunnitelma" "sammutusautomatiikkasuunnitelma" "rakennusautomaatiosuunnitelma" "valaistussuunnitelma" "selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta" "selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta" "muu"]]]
                                    :comments []
                                    :address "Porvoonväylä 963"
                                    :permitType "R"
                                    :id "LP-753-2013-00010"
                                    :municipality "753"})