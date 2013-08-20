(ns lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test
  (:use [lupapalvelu.factlet]
        [midje.sweet]
        [lupapalvelu.document.canonical-common]
        [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical]
        [sade.util :only [contains-value?]]))

;; NOTE: Rakennuslupa-canonical-testista poiketen otin "auth"-kohdan pois.

;; TODO: Pitaisiko "location"-kohta poistaa? Silla ei kuulemma tehda mitaan.

;; TODO: Kopioi lausuntokohta, "statements", rakennusluvan puolelta.

;; TODO: Jossain itesteissa pitaisi testata seuraavat applicationin kohdat:
;;        - operations
;;        - allowedAttachmentTypes
;;        - organization ?


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


(def henkilo-without-hetu {:henkilotiedot nimi,
                           :osoite osoite,
                           :userId user-id,
                           :yhteystiedot yhteystiedot})

(def henkilo {:henkilotiedot henkilotiedot,
              :osoite osoite,
              :userId user-id,
              :yhteystiedot yhteystiedot})

(def yritys (merge
              {:osoite osoite,
               :yhteyshenkilo {:henkilotiedot nimi,
                               :yhteystiedot yhteystiedot}}
              yritys-nimi-ja-tunnus))

(def hakija {:id "51cc1cab23e74941fee4f498",
             :created 1372331179008,
             :schema {:info
                      {:name "hakija-ya",
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
                                        :name "yleiset-alueet-kaivuulupa"},
                                   :name "tyomaastaVastaava",
                                   :removable true,
                                   :type "party",
                                   :order 61}},
                         :data {:_selected {:modified 1372342063565, :value "yritys"},
                                :henkilo henkilo-without-hetu,
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


(def authority {:id "777777777777777777000023",
                :username "sonja",
                :firstName "Sonja",
                :lastName "Sibbo",
                :role "authority"})

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
  {:id "LP-753-2013-00001",
   :permitType "YA",
   :created 1372331179008,
   :opened 1372331643985,
   :modified 1372342070624,
   :authority authority,
   :state "open",
   :title "Latokuja 1",
   :address "Latokuja 1",
   :location {:x 404335.789, :y 6693783.426},
   :attachments [],
   :propertyId "75341600550007",
   :documents documents,
   :municipality municipality
   ;; Statements kopioitu Rakennuslupa_canonical_test.clj:sta, joka on identtinen yleisten alueiden puolen kanssa.
   :statements statements})


(def get-maksaja #'lupapalvelu.document.yleiset-alueet-kaivulupa-canonical/get-maksaja)
(def get-tyomaasta-vastaava #'lupapalvelu.document.yleiset-alueet-kaivulupa-canonical/get-tyomaasta-vastaava)

(facts* "Canonical model is correct"
  (let [canonical (application-to-canonical application "fi")
        YleisetAlueet (:YleisetAlueet canonical) => truthy
        yleinenAlueAsiatieto (:yleinenAlueAsiatieto YleisetAlueet) => truthy
        Tyolupa (:Tyolupa yleinenAlueAsiatieto) => truthy

        Kasittelytieto (-> Tyolupa :kasittelytietotieto :Kasittelytieto) => truthy
        Kasittelytieto-kasittelija (:kasittelija Kasittelytieto) => truthy
        Kasittelytieto-kasittelija-nimi (-> Kasittelytieto-kasittelija :henkilotieto :Henkilo :nimi) => truthy

        Tyolupa-kayttotarkoitus (:kayttotarkoitus Tyolupa) => truthy
        Tyolupa-Johtoselvitysviite (-> Tyolupa :johtoselvitysviitetieto :Johtoselvitysviite) => truthy

        Sijainti-osoite (-> Tyolupa :sijaintitieto :Sijainti :osoite) => truthy
        Sijainti-yksilointitieto (-> Sijainti-osoite :yksilointitieto) => truthy
        Sijainti-alkuHetki (-> Sijainti-osoite :alkuHetki) => truthy
        Sijainti-osoitenimi (-> Sijainti-osoite :osoitenimi :teksti) => truthy
        Sijainti-piste (-> Tyolupa :sijaintitieto :Sijainti :piste :Point :pos) => truthy

        osapuolet-vec (-> Tyolupa :osapuolitieto) => truthy
        vastuuhenkilot-vec (-> Tyolupa :vastuuhenkilotieto) => truthy

        ;; maksajan henkilotieto-osa
        rooliKoodi-maksajan-vastuuhenkilo "maksajan vastuuhenkil\u00f6"
        maksaja-Vastuuhenkilo (:Vastuuhenkilo (first (filter #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-maksajan-vastuuhenkilo) vastuuhenkilot-vec)))
        maksaja-Vastuuhenkilo-osoite (-> maksaja-Vastuuhenkilo :osoitetieto :osoite) => truthy
        ;; maksajan yritystieto-osa
        Maksaja (-> Tyolupa :maksajatieto :Maksaja) => truthy
        maksaja-Yritys (-> Maksaja :yritystieto :Yritys) => truthy
        maksaja-Yritys-postiosoite (-> maksaja-Yritys :postiosoite) => truthy

        ;; Testataan muunnosfunktiota yksityisella maksajalla ("henkilo"-tyyppinen maksaja)
        maksaja-yksityinen (get-maksaja
                             (assoc-in (:data maksaja) [:_selected :value] "henkilo"))
        maksaja-yksityinen-Henkilo (-> maksaja-yksityinen :henkilotieto :Henkilo) => truthy
        maksaja-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
        maksaja-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

        alkuPvm (-> Tyolupa :alkuPvm) => truthy
        loppuPvm (-> Tyolupa :loppuPvm) => truthy

        lupaAsianKuvaus (:lupaAsianKuvaus Tyolupa) => truthy
        Sijoituslupaviite (-> Tyolupa :sijoituslupaviitetieto :Sijoituslupaviite) => truthy

        ;; tyomaasta-vastaavan yritystieto-osa
        rooliKoodi-tyomaastavastaava "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6"
        rooliKoodi-tyonsuorittaja "ty\u00f6nsuorittaja"
        Vastuuhenkilo-yritys (:Osapuoli (first (filter #(= (-> % :Osapuoli :rooliKoodi) rooliKoodi-tyonsuorittaja) osapuolet-vec)))
        Vastuuhenkilo-yritys-Yritys (-> Vastuuhenkilo-yritys :yritystieto :Yritys) => truthy
        Vastuuhenkilo-yritys-Postiosoite (-> Vastuuhenkilo-yritys-Yritys :postiosoitetieto :Postiosoite) => truthy
        ;; tyomaasta-vastaavan henkilotieto-osa
        Vastuuhenkilo-henkilo (:Vastuuhenkilo (first (filter #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-tyomaastavastaava) vastuuhenkilot-vec)))
        Vastuuhenkilo-henkilo-osoite (-> Vastuuhenkilo-henkilo :osoitetieto :osoite) => truthy

        ;; Testataan muunnosfunktiota henkilo-tyyppisella tyomaasta-vastaavalla
        tyomaasta-vastaava-henkilo (get-tyomaasta-vastaava
                                     (assoc-in (:data tyomaasta-vastaava) [:_selected :value] "henkilo")) => truthy
        tyomaasta-vastaava-Vastuuhenkilo (-> tyomaasta-vastaava-henkilo :vastuuhenkilotieto :Vastuuhenkilo) => truthy
        tyomaasta-vastaava-Vastuuhenkilo-osoite (-> tyomaasta-vastaava-Vastuuhenkilo :osoitetieto :osoite) => truthy

        rooliKoodi-Hakija "hakija"
        hakija-Osapuoli (:Osapuoli (first (filter #(= (-> % :Osapuoli :rooliKoodi) rooliKoodi-Hakija) osapuolet-vec)))
        hakija-Henkilo (-> hakija-Osapuoli :henkilotieto :Henkilo) => truthy  ;; kyseessa yrityksen vastuuhenkilo
        hakija-Yritys (-> hakija-Osapuoli :yritystieto :Yritys) => truthy
        hakija-henkilo-nimi (:nimi hakija-Henkilo) => truthy
        hakija-yritys-Postiosoite (-> hakija-Yritys :postiosoitetieto :Postiosoite) => truthy]

;      (println "\n canonical: ")
;      (clojure.pprint/pprint canonical)

      (fact "contains nil" (contains-value? canonical nil?) => falsey)

      (fact "Kasittelytieto-muutosHetki" (:muutosHetki Kasittelytieto) => (to-xml-datetime (:modified application)))
      (fact "Kasittelytieto-hakemuksenTila" (:hakemuksenTila Kasittelytieto) => "vireill\u00e4")
      (fact "Kasittelytieto-asiatunnus" (:asiatunnus Kasittelytieto) => (:id application))
      (fact "Kasittelytieto-paivaysPvm" (:paivaysPvm Kasittelytieto) => (to-xml-date (:opened application)))
      (fact "Kasittelytieto-kasittelija-etunimi" (:etunimi Kasittelytieto-kasittelija-nimi) => (:firstName authority))
      (fact "Kasittelytieto-kasittelija-sukunimi" (:sukunimi Kasittelytieto-kasittelija-nimi) => (:lastName authority))

      (fact "Tyolupa-kayttotarkoitus" Tyolupa-kayttotarkoitus => "kaivu- tai katuty\u00f6lupa")
      (fact "Tyolupa-Johtoselvitysviite-vaadittuKytkin" (:vaadittuKytkin Tyolupa-Johtoselvitysviite) => false) ;; TODO: Onko tama checkki ok?

      ;; Sijainti
      (fact "Sijainti-yksilointitieto" Sijainti-yksilointitieto => (:id application))
;      (fact "Sijainti-alkuHetki" Sijainti-alkuHetki => <now??>)              ;; TODO: Mita tahan?
      (fact "Sijainti-osoitenimi" Sijainti-osoitenimi => (:address application))
      (fact "Sijainti-piste-xy" Sijainti-piste => (str (-> application :location :x) " " (-> application :location :y)))

      ;; Maksajan tiedot
      (fact "maksaja-laskuviite" (:laskuviite Maksaja) => (:value _laskuviite))
      (fact "maksaja-rooliKoodi" (:rooliKoodi maksaja-Vastuuhenkilo) => rooliKoodi-maksajan-vastuuhenkilo)
      (fact "maksaja-henkilo-etunimi" (:etunimi maksaja-Vastuuhenkilo) => (-> nimi :etunimi :value))
      (fact "maksaja-henkilo-sukunimi" (:sukunimi maksaja-Vastuuhenkilo) => (-> nimi :sukunimi :value))
      (fact "maksaja-henkilo-sahkopostiosoite" (:sahkopostiosoite maksaja-Vastuuhenkilo) => (-> yhteystiedot :email :value))
      (fact "maksaja-henkilo-puhelinnumero" (:puhelinnumero maksaja-Vastuuhenkilo) => (-> yhteystiedot :puhelin :value))
      (fact "maksaja-henkilo-osoite-osoitenimi"
        (-> maksaja-Vastuuhenkilo-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
      (fact "maksaja-henkilo-osoite-postinumero"
        (:postinumero maksaja-Vastuuhenkilo-osoite) => (-> osoite :postinumero :value))
      (fact "maksaja-henkilo-osoite-postitoimipaikannimi"
        (:postitoimipaikannimi maksaja-Vastuuhenkilo-osoite) => (-> osoite :postitoimipaikannimi :value))
      (fact "maksaja-yritys-nimi" (:nimi maksaja-Yritys) => (-> yritys-nimi-ja-tunnus :yritysnimi :value))
      (fact "maksaja-yritys-liikeJaYhteisotunnus" (:liikeJaYhteisotunnus maksaja-Yritys) => (-> yritys-nimi-ja-tunnus :liikeJaYhteisoTunnus :value))
      (fact "maksaja-yritys-osoitenimi" (-> maksaja-Yritys-postiosoite :osoitenimi :teksti) => (-> osoite :katu :value))
      (fact "maksaja-yritys-postinumero" (:postinumero maksaja-Yritys-postiosoite) => (-> osoite :postinumero :value))
      (fact "maksaja-yritys-postitoimipaikannimi" (:postitoimipaikannimi maksaja-Yritys-postiosoite) => (-> osoite :postitoimipaikannimi :value))

      ;; Maksaja, yksityinen henkilo
      (fact "maksaja-yksityinen-etunimi" (:etunimi maksaja-yksityinen-nimi) => (-> nimi :etunimi :value))
      (fact "maksaja-yksityinen-sukunimi" (:sukunimi maksaja-yksityinen-nimi) => (-> nimi :sukunimi :value))
      (fact "maksaja-yksityinen-osoitenimi" (-> maksaja-yksityinen-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
      (fact "maksaja-yksityinen-postinumero" (:postinumero maksaja-yksityinen-osoite) => (-> osoite :postinumero :value))
      (fact "maksaja-yksityinen-postitoimipaikannimi" (:postitoimipaikannimi maksaja-yksityinen-osoite) => (-> osoite :postitoimipaikannimi :value))
      (fact "maksaja-yksityinen-sahkopostiosoite" (:sahkopostiosoite maksaja-yksityinen-Henkilo) => (-> yhteystiedot :email :value))
      (fact "maksaja-yksityinen-puhelin" (:puhelin maksaja-yksityinen-Henkilo) => (-> yhteystiedot :puhelin :value))
      (fact "maksaja-yksityinen-henkilotunnus" (:henkilotunnus maksaja-yksityinen-Henkilo) => (-> henkilotiedot :hetu :value))

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
      (fact "hakija-rooliKoodi" (:rooliKoodi hakija-Osapuoli) => rooliKoodi-Hakija)

      ;; Hakija, yksityinen henkilo -> Tama on testattu jo kohdassa "Maksaja, yksityinen henkilo" (muunnos on taysin sama)

      ;; Tyomaasta vastaava, yritys-tyyppia
      (fact "Vastuuhenkilo-henkilo-rooliKoodi"
        (:rooliKoodi Vastuuhenkilo-henkilo) => rooliKoodi-tyomaastavastaava)
      (fact "Vastuuhenkilo-henkilo-etunimi"
        (:etunimi Vastuuhenkilo-henkilo) => (-> tyomaasta-vastaava :data :yritys :yhteyshenkilo :henkilotiedot :etunimi :value))
      (fact "Vastuuhenkilo-henkilo-sukunimi"
        (:sukunimi Vastuuhenkilo-henkilo) => (-> tyomaasta-vastaava :data :yritys :yhteyshenkilo :henkilotiedot :sukunimi :value))
      (fact "Vastuuhenkilo-henkilo-osoite-osoitenimi"
        (-> Vastuuhenkilo-henkilo-osoite :osoitenimi :teksti) => (-> tyomaasta-vastaava :data :yritys :osoite :katu :value))
      (fact "Vastuuhenkilo-henkilo-osoite-postinumero"
        (:postinumero Vastuuhenkilo-henkilo-osoite) => (-> tyomaasta-vastaava :data :yritys :osoite :postinumero :value))
      (fact "Vastuuhenkilo-henkilo-osoite-postitoimipaikannimi"
        (:postitoimipaikannimi Vastuuhenkilo-henkilo-osoite) => (-> tyomaasta-vastaava :data :yritys :osoite :postitoimipaikannimi :value))
      (fact "Vastuuhenkilo-henkilo-puhelinnumero"
        (:puhelinnumero Vastuuhenkilo-henkilo) => (-> tyomaasta-vastaava :data :yritys :yhteyshenkilo :yhteystiedot :puhelin :value))
      (fact "Vastuuhenkilo-henkilo-sahkopostiosoite"
        (:sahkopostiosoite Vastuuhenkilo-henkilo) => (-> tyomaasta-vastaava :data :yritys :yhteyshenkilo :yhteystiedot :email :value))

      (fact "Vastuuhenkilo-yritys-nimi"
        (:nimi Vastuuhenkilo-yritys-Yritys) => (-> tyomaasta-vastaava :data :yritys :yritysnimi :value))
      (fact "Vastuuhenkilo-yritys-liikeJaYhteisotunnus"
        (:liikeJaYhteisotunnus Vastuuhenkilo-yritys-Yritys) => (-> tyomaasta-vastaava :data :yritys :liikeJaYhteisoTunnus :value))
      (fact "Vastuuhenkilo-yritys-rooliKoodi"
        (:rooliKoodi Vastuuhenkilo-yritys) => rooliKoodi-tyonsuorittaja)
      (fact "Vastuuhenkilo-yritys-Postiosoite-osoitenimi"
        (-> Vastuuhenkilo-yritys-Postiosoite :osoitenimi :teksti) => (-> tyomaasta-vastaava :data :yritys :osoite :katu :value))
      (fact "Vastuuhenkilo-yritys-Postiosoite-postinumero"
        (:postinumero Vastuuhenkilo-yritys-Postiosoite) => (-> tyomaasta-vastaava :data :yritys :osoite :postinumero :value))
      (fact "Vastuuhenkilo-yritys-Postiosoite-postitoimipaikannimi"
        (:postitoimipaikannimi Vastuuhenkilo-yritys-Postiosoite) => (-> tyomaasta-vastaava :data :yritys :osoite :postitoimipaikannimi :value))

      ;; Tyomaasta vastaava, henkilo-tyyppia
      (fact "tyomaasta-vastaava-yksityinen-rooliKoodi" (:rooliKoodi tyomaasta-vastaava-Vastuuhenkilo) => rooliKoodi-tyomaastavastaava)
      (fact "tyomaasta-vastaava-yksityinen-etunimi" (:etunimi tyomaasta-vastaava-Vastuuhenkilo) => (-> nimi :etunimi :value))
      (fact "tyomaasta-vastaava-yksityinen-sukunimi" (:sukunimi tyomaasta-vastaava-Vastuuhenkilo) => (-> nimi :sukunimi :value))
      (fact "tyomaasta-vastaava-yksityinen-sahkopostiosoite" (:sahkopostiosoite tyomaasta-vastaava-Vastuuhenkilo) => (-> yhteystiedot :email :value))
      (fact "tyomaasta-vastaava-yksityinen-puhelin" (:puhelinnumero tyomaasta-vastaava-Vastuuhenkilo) => (-> yhteystiedot :puhelin :value))
      (fact "tyomaasta-vastaava-yksityinen-osoitenimi" (-> tyomaasta-vastaava-Vastuuhenkilo-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
      (fact "tyomaasta-vastaava-yksityinen-postinumero" (:postinumero tyomaasta-vastaava-Vastuuhenkilo-osoite) => (-> osoite :postinumero :value))
      (fact "tyomaasta-vastaava-yksityinen-postitoimipaikannimi" (:postitoimipaikannimi tyomaasta-vastaava-Vastuuhenkilo-osoite) => (-> osoite :postitoimipaikannimi :value))

      ;; Kayton alku/loppu pvm
      (fact "alkuPvm" alkuPvm => (to-xml-date-from-string (-> tyoaika :data :tyoaika-alkaa-pvm :value)))
      (fact "loppuPvm" loppuPvm => (to-xml-date-from-string (-> tyoaika :data :tyoaika-paattyy-pvm :value)))

      ;; Hankkeen kuvaus
      (fact "lupaAsianKuvaus" lupaAsianKuvaus => (-> hankkeen-kuvaus :data :kayttotarkoitus :value))
      (fact "vaadittuKytkin" (:vaadittuKytkin Sijoituslupaviite) => false)  ;; TODO: Onko tama checkki ok?
      (fact "Sijoituslupaviite" (:tunniste Sijoituslupaviite) => (-> hankkeen-kuvaus :data :sijoitusLuvanTunniste :value))))



