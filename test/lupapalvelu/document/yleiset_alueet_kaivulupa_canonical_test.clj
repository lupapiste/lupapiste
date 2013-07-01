(ns lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test)

;; NOTE: Rakennuslupa-canonical-testistä poiketen otin "auth"-kohdan pois.

;; TODO: Pitäisikö "location"-kohta poistaa? Sillä ei kuulemma tehdä mitään.

;; TODO: Kopioi lausuntokohta, "statements", rakennusluvan puolelta.

;; TODO: Jossain itesteissä pitäisi testata seuraavat applicationin kohdat:
;;        - operations
;;        - allowedAttachmentTypes
;;        - organization ?

;; TODO: Applicationin "permitType"-kohta pitäisi poistaa, se on nyt kovakoodatusti "buildingPermit".



(def henkilo_hakija {:henkilotiedot {:etunimi {:value "Pena"},
                                     :hetu {:modified 1372342041161, :value "260886-027R"},
                                     :sukunimi {:value "Panaani"}},
                     :osoite {:katu {:value "Paapankuja 12"},
                              :postinumero {:modified 1372342044504, :value "44565"},
                              :postitoimipaikannimi {:value "Piippola"}},
                     :userId {:value "777777777777777777000020"},
                     :yhteystiedot {:email {:value "pena@example.com"},
                                    :puhelin {:value "0102030405"}}})

(def yritys_hakija {:liikeJaYhteisoTunnus {:modified 1372331320811, :value "2492773-2"},
                    :osoite {:katu {:value "Paapankuja 12"},
                             :postinumero {:modified 1372331328334, :value "33530"},
                             :postitoimipaikannimi {:value "Piippola"}},
                    :vastuuhenkilo {:henkilotiedot {:etunimi {:value "Pena"}, :sukunimi {:value "Panaani"}},
                                    :yhteystiedot {:email {:value "pena@example.com"},
                                                   :puhelin {:value "0102030405"}}},
                    :yritysnimi {:modified 1372331257700, :value "Yritys Oy Ab"}})

(def hakija {:id "51cc1cab23e74941fee4f498",
             :created 1372331179008,
             :schema {:info
                      {:name "hakija-public-area",
                       :removable true,
                       :repeating true,
                       :type "party",
                       :order 3}},
             :data {:_selected {:modified 1372342070624, :value "yritys"},
                    :henkilo henkilo_hakija,
                    :yritys yritys_hakija}})

(def henkilo_tyomaasta-vastaava {:henkilotiedot {:etunimi {:modified 1372341939920, :value "Pena"},
                                                 :hetu {:modified 1372341952297, :value "260886-027R"},
                                                 :sukunimi {:modified 1372341939920, :value "Panaani"}},
                                 :osoite {:katu {:modified 1372341939920, :value "Paapankuja 12"},
                                          :postinumero {:modified 1372341955504, :value "33456"},
                                          :postitoimipaikannimi {:modified 1372341939920, :value "Piippola"}},
                                 :userId {:modified 1372341939964, :value "777777777777777777000020"},
                                 :yhteystiedot {:email {:modified 1372341939920, :value "pena@example.com"},
                                                :puhelin {:modified 1372341939920, :value "0102030405"}}})

(def yritys_tyomaasta-vastaava {:liikeJaYhteisoTunnus {:modified 1372331412694, :value "2492773-2"},
                                :osoite {:katu {:modified 1372331429288, :value "Panaanikatu 6 C 33"},
                                         :postinumero {:modified 1372331432415, :value "33450"},
                                         :postitoimipaikannimi {:modified 1372331438285, :value "Ikaalinen"}},
                                :vastuuhenkilo {:henkilotiedot {:etunimi {:modified 1372331444068, :value "Jarmo"},
                                                                :sukunimi {:modified 1372331461059, :value "Panaaniberg"}},
                                                :yhteystiedot {:email {:modified 1372331491470, :value "jarmonen@pannu.fi"},
                                                               :puhelin {:modified 1372331474135, :value "0407890123"}}},
                                :yritysnimi {:modified 1372331404592, :value "Vastaava Yritys"}})

(def tyomaasta-vastaava {:id "51cc1cab23e74941fee4f496",
                         :created 1372331179008,
                         :schema {:info
                                  {:op {:id "51cc1cab23e74941fee4f495",
                                        :created 1372331179008,
                                        :name "yleiset-alueet-kaivuulupa",
                                        :operation-type "publicArea"},
                                   :name "tyomaastaVastaava",
                                   :removable true,
                                   :type "party",
                                   :order 61}},
                         :data {:_selected {:modified 1372342063565, :value "henkilo"},
                                :henkilo henkilo_tyomaasta-vastaava,
                                :yritys yritys_tyomaasta-vastaava}})

(def henkilo_maksaja {:henkilotiedot {:etunimi {:modified 1372341693897, :value "MaksajaPena"},
                                      :hetu {:modified 1372341552376, :value "260886-027R"},
                                      :sukunimi {:modified 1372331493576, :value "Panaani"}},
                      :osoite {:katu {:modified 1372331493576, :value "Paapankuja 12"},
                               :postinumero {:modified 1372341557255, :value "33455"},
                               :postitoimipaikannimi {:modified 1372331493576, :value "Piippola"}},
                      :userId {:modified 1372331493599, :value "777777777777777777000020"},
                      :yhteystiedot {:email {:modified 1372331493576, :value "pena@example.com"},
                                     :puhelin {:modified 1372331493576, :value "0102030405"}}})

(def yritys_maksaja {:liikeJaYhteisoTunnus {:modified 1372331520985, :value "2492773-2"},
                     :osoite {:katu {:modified 1372331533697, :value "Maksajakatu 1 a 2"},
                              :postinumero {:modified 1372331536989, :value "33459"},
                              :postitoimipaikannimi {:modified 1372331543428, :value "Maksajakunta"}},
                     :vastuuhenkilo {:henkilotiedot {:etunimi {:modified 1372331565465, :value "Vastuumo"},
                                                     :sukunimi {:modified 1372331561940, :value "Henkilonen"}},
                                     :yhteystiedot {:email {:modified 1372331587669, :value "maksaja@firma.fi"},
                                                    :puhelin {:modified 1372331574431, :value "0405678123"}}},
                     :yritysnimi {:modified 1372331517235, :value "Maksaja"}})

(def laskuviite {:modified 1372331605911, :value "1234567890"})

(def maksaja {:id "51cc1cab23e74941fee4f499",
              :created 1372331179008,
              :data {:_selected {:modified 1372341924880, :value "henkilo"},
                     :henkilo henkilo_maksaja,
                     :yritys yritys_maksaja,
                     :laskuviite laskuviite},
              :schema {:info {:name "yleiset-alueet-maksaja", :type "party", :order 62}}})

(def hankkeen-kuvaus {:id "51cc1cab23e74941fee4f49a",
                      :created 1372331179008,
                      :data {:kayttotarkoitus {:modified 1372331214906, :value "Ojankaivuu."},
                             :sijoitusLuvanTunniste {:modified 1372331243461, :value "LP-753-2013-00001"}},
                      :schema {:info {:name "yleiset-alueet-hankkeen-kuvaus-kaivulupa", :order 60}}})

(def tyoaika {:id "51cc1cab23e74941fee4f49b",
              :created 1372331179008,
              :schema {:info {:name "tyoaika", :type "group", :order 63}},
              :data {:tyoaika-alkaa-pvm
                     {:modified 1372331246482, :value "17.06.2013"},
                     :tyoaika-paattyy-pvm
                     {:modified 1372331248524, :value "20.06.2013"}}})

(def documents [hakija
                tyomaasta-vastaava
                maksaja
                hankkeen-kuvaus
                tyoaika])


(def municipality 753)

(def attachments [{:id "51cc1e7c23e74941fee4f519",
                   :modified 1372331643985,
                   :type {:type-group "yleiset-alueet",
                          :type-id "aiemmin-hankittu-sijoituspaatos"},
                   :state "requires_authority_action",
                   :target nil,
                   :op nil,
                   :locked false,
                   :latestVersion {:fileId "51cc1e7b23e74941fee4f516",
                                   :version {:major 1, :minor 0},
                                   :size 115496,
                                   :created 1372331643985,
                                   :filename "Screenshot_Lisaa_lausunnon_antaja.jpg",
                                   :contentType "image/jpeg",
                                   :stamped false,
                                   :accepted nil,
                                   :user {:id "777777777777777777000020",
                                          :role "applicant",
                                          :lastName "Panaani",
                                          :firstName "Pena",
                                          :username "pena"}},
                   :versions [{:fileId "51cc1e7b23e74941fee4f516",
                               :version {:major 1, :minor 0},
                               :size 115496,
                               :created 1372331643985,
                               :filename "Screenshot_Lisaa_lausunnon_antaja.jpg",
                               :contentType "image/jpeg",
                               :stamped false,
                               :accepted nil,
                               :user {:id "777777777777777777000020",
                                      :role "applicant",
                                      :lastName "Panaani",
                                      :firstName "Pena",
                                      :username "pena"}}]}])

(def application
  {:created 1372331179008,
   :opened 1372331643985,
   :modified 1372342070624,
   :state "open",
   :title "Latokuja 1",
   :address "Latokuja 1",
   :location {:x 404335.789, :y 6693783.426},
   :attachments attachments,
   :propertyId "75341600550007",
   :documents documents,
   :municipality municipality
   ;; Statements kopioitu Rakennuslupa_canonical_test.clj:sta, joka on identtinen yleisten lueiden puolen kanssa.
   :statements [{:id "518b3ee60364ff9a63c6d6a1"
                 :given 1368080324142
                 :person {:id "516560d6c2e6f603beb85147"
                          :text "Paloviranomainen"
                          :name "Sonja Sibbo"
                          :email "sonja.sibbo@sipoo.fi"}
                 :requested 1368080102631
                 :status "condition"
                 :text "Savupiippu pit\u00e4\u00e4 olla."}]})


