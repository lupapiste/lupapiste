(ns lupapalvelu.document.yleiset-alueet-canonical-test-common)


(def municipality 753)

(def location [404335.789 6693783.426])


(def pena {:id "777777777777777777000020",
           :role "applicant",
           :firstName "Pena",
           :lastName "Panaani",
           :username "pena"})

(def sonja {:id "777777777777777777000023",
            :role "authority",
            :firstName "Sonja",
            :lastName "Sibbo",
            :username "sonja"})

(def statement-giver {:id "516560d6c2e6f603beb85147"
                       :text "Paloviranomainen"
                       :name "Sonja Sibbo"
                       :email "sonja.sibbo@sipoo.fi"})


;; Document: Hakija

(def nimi {:etunimi {:modified 1372341939920, :value "Pena"},
           :sukunimi {:modified 1372341939920, :value "Panaani"}})

(def henkilotiedot (merge
                     nimi
                     {:hetu {:modified 1372341952297, :value "010203-040A"}}))

(def osoite {:katu {:modified 1372341939920, :value "Paapankuja 12"},
             :postinumero {:modified 1372341955504, :value "33800"},
             :postitoimipaikannimi {:modified 1372341939920, :value "Piippola"}})

(def yhteystiedot {:email {:modified 1372341939920, :value "pena@example.com"},
                   :puhelin {:modified 1372341939920, :value "0102030405"}})

(def yritys-nimi-ja-tunnus {:yritysnimi {:modified 1372331257700, :value "Yritys Oy Ab"},
                            :liikeJaYhteisoTunnus {:modified 1372331320811, :value "2492773-2"}})

(def henkilo-without-hetu {:henkilotiedot nimi,
                           :osoite osoite,
                           :userId {:modified 1372341939964, :value "777777777777777777000020"},
                           :yhteystiedot yhteystiedot})

(def henkilo {:userId {:modified 1372341939964, :value "777777777777777777000020"},
              :henkilotiedot henkilotiedot,
              :osoite osoite,
              :yhteystiedot yhteystiedot})

(def yritys (merge
              yritys-nimi-ja-tunnus
              {:osoite osoite,
               :yhteyshenkilo {:henkilotiedot nimi,
                               :yhteystiedot yhteystiedot}}))

;; Document: Hakija

(def hakija {:id "52380c6894a74fc25bb4ba48",
             :created 1379404904514,
             :schema-info {:approvable true,
                           :subtype "hakija",
                           :name "hakija-ya",
                           :removable false,
                           :repeating false,
                           :version 1,
                           :type "party",
                           :order 3},
             :data {:_selected {:modified 1379405016674, :value "yritys"},
                    :henkilo henkilo,
                    :yritys yritys}})

;; Document: Tyomaasta vastaava

(def tyomaasta-vastaava {:id "51cc1cab23e74941fee4f496",
                         :created 1372331179008,
                         :schema-info {:name "tyomaastaVastaava"
                                       :version 1
                                       :removable true,
                                       :type "party",
                                       :order 61},
                         :data {:_selected {:modified 1372342063565, :value "yritys"},
                                :henkilo henkilo-without-hetu,
                                :yritys yritys}})

;; Document: Maksaja

(def yritys-with-verkkolaskutustieto
  (merge yritys
         {:verkkolaskutustieto {:ovtTunnus {:value "003712345671"}
                                :verkkolaskuTunnus {:value "laskutunnus-1234"}
                                :valittajaTunnus {:value "BAWCFI22"}}}))

(def _laskuviite {:modified 1379404963313, :value "1234567890"})

(def maksaja {:id "52380c6894a74fc25bb4ba49"
              :created "1379404904514"
              :schema-info {:name "yleiset-alueet-maksaja"
                            :removable false
                            :repeating false
                            :version 1
                            :type "party"
                            :order 62}
              :data {:_selected {:modified 1379405011475, :value "yritys"},
                     :henkilo henkilo
                     :yritys yritys-with-verkkolaskutustieto
                     :laskuviite _laskuviite}})

;; Document: Tyoaika

(def tyoaika {:id "52380c6894a74fc25bb4ba47",
              :created 1379404904514,
              :schema-info {:order 63,
                            :type "group",
                            :version 1,
                            :removable false,
                            :repeating false,
                            :name "tyoaika"},
              :data {:tyoaika-alkaa-ms {:modified 1379404916497, :value 1379500000000}
                     :tyoaika-paattyy-ms {:modified 1379404918106, :value 1379860000000}}})

;; Attachments

(def attachment-version-user {:fileId "52380cb594a74fc25bb4ba6d",
                              :version {:major 1, :minor 0},
                              :size 44755,
                              :created 1379404981309,
                              :filename "lupapiste-attachment-testi.pdf",
                              :contentType "application/pdf",
                              :stamped false,
                              :accepted nil,
                              :user pena})

;; Statements

(def statements [{:id "52382cea94a74fc25bb4be5d"
                  :given 1379415837074
                  :requested 1379413226349
                  :status "puoltaa"
                  :person statement-giver
                  :text "Annanpa luvan."}])

