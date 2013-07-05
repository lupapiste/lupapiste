(ns lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test
  (:use [lupapalvelu.factlet]
        [midje.sweet]
        [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical]
        [sade.util :only [contains-value?]]))

;; NOTE: Rakennuslupa-canonical-testistä poiketen otin "auth"-kohdan pois.

;; TODO: Pitäisikö "location"-kohta poistaa? Sillä ei kuulemma tehdä mitään.

;; TODO: Kopioi lausuntokohta, "statements", rakennusluvan puolelta.

;; TODO: Jossain itesteissä pitäisi testata seuraavat applicationin kohdat:
;;        - operations
;;        - allowedAttachmentTypes
;;        - organization ?

;; TODO: Applicationin "permitType"-kohta pitäisi poistaa, se on nyt kovakoodatusti "buildingPermit".


(def nimi {:etunimi {:modified 1372341939920, :value "Pena"},
           :sukunimi {:modified 1372341939920, :value "Panaani"}})

(def henkilotiedot (assoc nimi :hetu {:modified 1372341952297, :value "260886-027R"}))

(def osoite {:katu {:modified 1372341939920, :value "Paapankuja 12"},
             :postinumero {:modified 1372341955504, :value "33800"},
             :postitoimipaikannimi {:modified 1372341939920, :value "Piippola"}})

(def user-id {:modified 1372341939964, :value "777777777777777777000020"})

(def yhteystiedot {:email {:modified 1372341939920, :value "pena@example.com"},
                   :puhelin {:modified 1372341939920, :value "0102030405"}})

(def yritys-nimi-ja-tunnus {:yritysnimi {:modified 1372331257700, :value "Yritys Oy Ab"},
                            :liikeJaYhteisoTunnus {:modified 1372331320811, :value "2492773-2"}})


(def henkilo {:henkilotiedot henkilotiedot,
              :osoite osoite,
              :userId user-id,
              :yhteystiedot yhteystiedot})

(def yritys (merge
              {:osoite osoite,
               :vastuuhenkilo {:henkilotiedot nimi,
                               :yhteystiedot yhteystiedot}}
              yritys-nimi-ja-tunnus))

(def hakija {:id "51cc1cab23e74941fee4f498",
             :created 1372331179008,
             :schema {:info
                      {:name "hakija-public-area",
                       :removable true,
                       :repeating true,
                       :type "party",
                       :order 3}},
             :data {:_selected {:modified 1372342070624, :value "yritys"},
                    :henkilo henkilo,
                    :yritys yritys}})

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
                                :henkilo henkilo,
                                :yritys yritys}})

(def _laskuviite {:modified 1372331605911, :value "1234567890"})

(def maksaja {:id "51cc1cab23e74941fee4f499",
              :created 1372331179008,
              :schema {:info {:name "yleiset-alueet-maksaja",
                              :type "party",
                              :order 62}},
              :data {:_selected {:modified 1372341924880, :value "yritys"},
                     :henkilo henkilo,
                     :yritys yritys,
                     :laskuviite _laskuviite}})

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

(def statements [{:id "518b3ee60364ff9a63c6d6a1"
                  :given 1368080324142
                  :requested 1368080102631
                  :status "condition"
                  :person {:id "516560d6c2e6f603beb85147"
                           :text "Paloviranomainen"
                           :name "Sonja Sibbo"
                           :email "sonja.sibbo@sipoo.fi"}
                  :text "Savupiippu pit\u00e4\u00e4 olla."}])

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
   :statements statements})


(def get-henkilo-plus-yritys #'lupapalvelu.document.yleiset-alueet-kaivulupa-canonical/get-henkilo-plus-yritys)

(facts* "Canonical model is correct"
  (let [canonical (application-to-canonical application "fi")
       YleisetAlueet (:YleisetAlueet canonical) => truthy
       yleinenAlueAsiatieto (first (:yleinenAlueAsiatieto YleisetAlueet)) => truthy
       Tyolupa (:Tyolupa yleinenAlueAsiatieto) => truthy

       ;; TODO: Naita voi yhdistella perakkain '->':lla... Refaktoroi.
       Maksaja (-> Tyolupa :maksajatieto :Maksaja) => truthy
       maksaja-laskuviite (:laskuviite Maksaja) => (:value _laskuviite)
       maksaja-Henkilo (-> Maksaja :henkilotieto :Henkilo) => truthy  ;; kyseessa yrityksen vastuuhenkilo
       maksaja-Yritys (-> Maksaja :yritystieto :Yritys) => truthy
       maksaja-henkilo-nimi (:nimi maksaja-Henkilo) => truthy  ;(fn [tieto] (println "\n nimitieto: " tieto))
       ;; Maksajan vastuuhenkilolla ei ole osoitetta, mutta yrityksella on.
       maksaja-yritys-osoite (:postiosoite maksaja-Yritys) => truthy

       ;; Testataan muunnosfunktiota yksityisella maksajalla ("henkilo"-tyyppinen maksaja)
       maksaja-yksityinen (get-henkilo-plus-yritys
                            (assoc-in (:data maksaja) [:_selected :value] "henkilo") "maksaja")
       maksaja-yksityinen-henkilotieto (:henkilotieto maksaja-yksityinen) => truthy
       maksaja-yksityinen-Henkilo (:Henkilo maksaja-yksityinen-henkilotieto) => truthy
       maksaja-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
       maksaja-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

       Toimintajakso (-> Tyolupa :toimintajaksotieto :Toimintajakso) => truthy

       lupaAsianKuvaus (:lupaAsianKuvaus Tyolupa) => truthy
       Sijoituslupaviite (-> Tyolupa :sijoituslupaviitetieto :Sijoituslupaviite) => truthy

;       Vastuuhenkilo (-> Tyolupa :vastuuhenkilotieto :Vastuuhenkilo) => truthy
;       Vastuuhenkilo-osoitetieto (:osoitetieto Vastuuhenkilo) => truthy
;       Vastuuhenkilo-osoite (:osoite Vastuuhenkilo-osoitetieto) => truthy

       hakija-Osapuoli (-> Tyolupa :osapuolitieto :Osapuoli) => truthy
       hakija-Henkilo (-> hakija-Osapuoli :henkilotieto :Henkilo) => truthy  ;; kyseessa yrityksen vastuuhenkilo
       hakija-Yritys (-> hakija-Osapuoli :yritystieto :Yritys) => truthy
       hakija-henkilo-nimi (:nimi maksaja-Henkilo) => truthy
       hakija-yritys-Postiosoite (-> hakija-Yritys :postiosoitetieto :Postiosoite) => truthy
       ]

;    (println "\n canonical: ")
;    (clojure.pprint/pprint canonical)

    (fact "contains nil" (contains-value? canonical nil?) => falsey)

    ;; Maksajan tiedot
    (fact "maksaja-etunimi" (:etunimi maksaja-henkilo-nimi) => (-> nimi :etunimi :value))
    (fact "maksaja-sukunimi" (:sukunimi maksaja-henkilo-nimi) => (-> nimi :sukunimi :value))
    (fact "maksaja-sahkopostiosoite" (:sahkopostiosoite maksaja-Henkilo) => (-> yhteystiedot :email :value))
    (fact "maksaja-puhelin" (:puhelin maksaja-Henkilo) => (-> yhteystiedot :puhelin :value))
    (fact "maksaja-nimi" (:nimi maksaja-Yritys) => (-> yritys-nimi-ja-tunnus :yritysnimi :value))
    (fact "maksaja-liikeJaYhteisotunnus" (:liikeJaYhteisotunnus maksaja-Yritys) => (-> yritys-nimi-ja-tunnus :liikeJaYhteisoTunnus :value))
    (fact "maksaja-osoitenimi" (-> maksaja-yritys-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
    (fact "maksaja-postinumero" (:postinumero maksaja-yritys-osoite) => (-> osoite :postinumero :value))
    (fact "maksaja-postitoimipaikannimi" (:postitoimipaikannimi maksaja-yritys-osoite) => (-> osoite :postitoimipaikannimi :value))

    ;; Maksaja, yksityinen henkilo
    (fact "maksaja-yksityinen-etunimi" (:etunimi maksaja-yksityinen-nimi) => (-> nimi :etunimi :value))
    (fact "maksaja-yksityinen-sukunimi" (:sukunimi maksaja-yksityinen-nimi) => (-> nimi :sukunimi :value))
    (fact "maksaja-yksityinen-osoitenimi" (-> maksaja-yksityinen-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
    (fact "maksaja-yksityinen-postinumero" (:postinumero maksaja-yksityinen-osoite) => (-> osoite :postinumero :value))
    (fact "maksaja-yksityinen-postitoimipaikannimi" (:postitoimipaikannimi maksaja-yksityinen-osoite) => (-> osoite :postitoimipaikannimi :value))
    (fact "maksaja-yksityinen-sahkopostiosoite" (:sahkopostiosoite maksaja-yksityinen-Henkilo) => (-> yhteystiedot :email :value))
    (fact "maksaja-yksityinen-puhelin" (:puhelin maksaja-yksityinen-Henkilo) => (-> yhteystiedot :puhelin :value))
    (fact "maksaja-yksityinen-hetu" (:henkilotunnus maksaja-yksityinen-Henkilo) => (-> henkilotiedot :hetu :value))

     ;; Osapuoli: Hakija
    (fact "hakija-etunimi" (:etunimi hakija-henkilo-nimi) => (-> nimi :etunimi :value))
    (fact "hakija-sukunimi" (:sukunimi hakija-henkilo-nimi) => (-> nimi :sukunimi :value))
    (fact "hakija-sahkopostiosoite" (:sahkopostiosoite hakija-Henkilo) => (-> yhteystiedot :email :value))
    (fact "hakija-puhelin" (:puhelin hakija-Henkilo) => (-> yhteystiedot :puhelin :value))
    (fact "hakija-nimi" (:nimi hakija-Yritys) => (-> yritys-nimi-ja-tunnus :yritysnimi :value))
    (fact "hakija-liikeJaYhteisotunnus" (:liikeJaYhteisotunnus hakija-Yritys) => (-> yritys-nimi-ja-tunnus :liikeJaYhteisoTunnus :value))
    (fact "hakija-osoitenimi" (-> hakija-yritys-Postiosoite :osoitenimi :teksti) => (-> osoite :katu :value))
    (fact "hakija-postinumero" (:postinumero hakija-yritys-Postiosoite) => (-> osoite :postinumero :value))
    (fact "hakija-postitoimipaikannimi" (:postitoimipaikannimi hakija-yritys-Postiosoite) => (-> osoite :postitoimipaikannimi :value))
    (fact "Vastuuhenkilo-rooliKoodi" (:rooliKoodi hakija-Osapuoli) => "hakija")

     ;; Hakija, yksityinen henkilo -> Tama on testattu jo kohdassa "Maksaja, yksityinen henkilo" (muunnos on taysin sama)

     ;; Tyomaasta vastaava
     ;; ****** TODO: Muuta tama, kun tyomaastaVastaava muutetaan henkiloksi! ******
;     (fact "Vastuuhenkilo-etunimi" (:etunimi Vastuuhenkilo) => (-> tyomaasta-vastaava :data :yritys :vastuuhenkilo :henkilotiedot :etunimi :value))
;     (fact "Vastuuhenkilo-sukunimi" (:sukunimi Vastuuhenkilo) => (-> tyomaasta-vastaava :data :yritys :vastuuhenkilo :henkilotiedot :sukunimi :value))
;     (fact "Vastuuhenkilo-osoite-osoitenimi" (:osoitenimi Vastuuhenkilo-osoite) => (-> tyomaasta-vastaava :data :yritys :osoite :osoitenimi :value))
;     (fact "Vastuuhenkilo-osoite-postinumero" (:postinumero Vastuuhenkilo-osoite) => (-> tyomaasta-vastaava :data :yritys :osoite :postinumero :value))
;     (fact "Vastuuhenkilo-osoite-postitoimipaikannimi" (:postitoimipaikannimi Vastuuhenkilo-osoite) => (-> tyomaasta-vastaava :data :yritys :osoite :postitoimipaikannimi :value))
;     (fact "Vastuuhenkilo-puhelinnumero" (:puhelinnumero Vastuuhenkilo) => (-> tyomaasta-vastaava :data :yritys :vastuuhenkilo :yhteystiedot :puhelin :value))
;     (fact "Vastuuhenkilo-sahkopostiosoite" (:sahkopostiosoite Vastuuhenkilo) => (-> tyomaasta-vastaava :data :yritys :vastuuhenkilo :yhteystiedot :email :value))
;     (fact "Vastuuhenkilo-rooliKoodi" (:rooliKoodi Vastuuhenkilo) => "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")


;     (println "\n Toimintajakso: " Toimintajakso)
;     (println "\n Toimintajakso alkuHetki: " (:alkuHetki Toimintajakso))

     ;; Toimintajakso
     (fact "Toimintajakso-alkuHetki" (:alkuHetki Toimintajakso) => (to-xml-datetime-from-string (-> tyoaika :data :tyoaika-alkaa-pvm :value)))
     (fact "Toimintajakso-loppuHetki" (:loppuHetki Toimintajakso) => (to-xml-datetime-from-string (-> tyoaika :data :tyoaika-paattyy-pvm :value)))

     ;; Hankkeen kuvaus
     (fact "lupaAsianKuvaus" lupaAsianKuvaus => (-> hankkeen-kuvaus :data :kayttotarkoitus :value))
     (fact "vaadittuKytkin" (:vaadittuKytkin Sijoituslupaviite) => false)  ;; TODO: Onko tama checkki ok?
     (fact "Sijoituslupaviite" (:tunniste Sijoituslupaviite) => (-> hankkeen-kuvaus :data :sijoitusLuvanTunniste :value))))



