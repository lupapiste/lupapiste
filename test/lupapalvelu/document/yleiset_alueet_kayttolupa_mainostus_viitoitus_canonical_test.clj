(ns lupapalvelu.document.yleiset-alueet-kayttolupa-mainostus-viitoitus-canonical-test
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical]]
            [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer :all]
            [lupapalvelu.factlet :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.util :as util]))


(def- operation {:id "523fea5694a7732d5096f25d",
                          :created 1379920470831,
                          :name "ya-kayttolupa-mainostus-ja-viitoitus"})

(def- tapahtuma-info
  {:id "523fea5694a7732d5096f25e",
   :created 1379920470831,
   :schema-info {:order 64,
                 :type "group",
                 :version 1,
                 :repeating false,
                 :op operation,
                 :name "mainosten-tai-viitoitusten-sijoittaminen"},
   :data {:_selected {:value "mainostus-tapahtuma-valinta"},
          :mainostus-tapahtuma-valinta {:mainostus-alkaa-pvm {:value "01.08.2013"},
                                        :mainostus-paattyy-pvm {:value "30.08.2013"},
                                        :tapahtuma-aika-alkaa-pvm {:value "13.09.2013"},
                                        :tapahtuma-aika-paattyy-pvm {:value "16.09.2013"},
                                        :tapahtuman-nimi {:value "Mainostettavan tapahtuman nimi"},
                                        :tapahtumapaikka {:value "Sipoon urheilukentt\u00e4"}
                                        :haetaan-kausilupaa {:value true}},
          :viitoitus-tapahtuma-valinta {:tapahtuma-aika-alkaa-pvm {:value "14.09.2013"},
                                        :tapahtuma-aika-paattyy-pvm {:value "17.09.2013"},
                                        :tapahtuman-nimi {:value "Viitoitettavan tapahtuman nimi"},
                                        :tapahtumapaikka {:value "Sipoon urheilukentt\u00e4"}}}})

(def- documents [hakija
                 maksaja
                 tapahtuma-info])

(def mainostus-application
  {:schema-version 1,
   :id "LP-753-2013-00004",
   :created 1379920470831,
   :opened 1379920714420,
   :modified 1379920824001,
   :submitted 1379920746800,
   :auth [{:lastName "Panaani",
             :firstName "Pena",
             :username "pena",
             :role "writer",
             :id "777777777777777777000020"}]
   :handlers [(assoc sonja :general true)],
   :permitType "YA",
   :organization "753-YA",
   :infoRequest false,
   :openInfoRequest false,
   :state "submitted",
   :title "Latokuja 1",
   :address "Latokuja 1",
   :location location,
   :attachments [],
   :primaryOperation operation,
   :secondaryOperations [],
   :propertyId "75341600550007",
   :documents documents,
   :municipality municipality,
   :statements statements
   :drawings ctc/drawings})

(ctc/validate-all-documents mainostus-application)

(def viitoitus-application (assoc
                             (assoc mainostus-application :documents
                               [hakija
                                maksaja
                                (assoc-in tapahtuma-info [:data :_selected :value] "viitoitus-tapahtuma-valinta")])
                             :id "LP-753-2013-00005"))

(ctc/validate-all-documents viitoitus-application)

(testable-privates lupapalvelu.document.yleiset-alueet-canonical get-yritys-and-henkilo get-hakija)

(facts* "Mainostus-viitoituslupa canonical model is correct"
  (let [canonical (application-to-canonical mainostus-application "fi")
        YleisetAlueet (:YleisetAlueet canonical) => truthy
        yleinenAlueAsiatieto (:yleinenAlueAsiatieto YleisetAlueet) => truthy
        Mainostuslupa (:Kayttolupa yleinenAlueAsiatieto) => truthy

        Kasittelytieto (-> Mainostuslupa :kasittelytietotieto :Kasittelytieto) => truthy
        Kasittelytieto-kasittelija-nimi (-> Kasittelytieto :kasittelija :henkilotieto :Henkilo :nimi) => truthy

        muu-tunnustieto (get-in Mainostuslupa [:luvanTunnisteTiedot :LupaTunnus :muuTunnustieto]) => seq

        Mainostuslupa-kayttotarkoitus (:kayttotarkoitus Mainostuslupa) => truthy

        Sijainti-osoite (-> Mainostuslupa :sijaintitieto first :Sijainti :osoite) => truthy
        Sijainti-yksilointitieto (-> Sijainti-osoite :yksilointitieto) => truthy
        _ (-> Sijainti-osoite :alkuHetki) => truthy
        Sijainti-osoitenimi (-> Sijainti-osoite :osoitenimi :teksti) => truthy
        Sijainti-piste (-> Mainostuslupa :sijaintitieto first :Sijainti :piste :Point :pos) => truthy

        vastuuhenkilot-vec (-> Mainostuslupa :vastuuhenkilotieto) => truthy

        ;; maksajan yritystieto-osa
        Maksaja (-> Mainostuslupa :maksajatieto :Maksaja) => truthy
        maksaja-Yritys (-> Maksaja :yritystieto :Yritys) => truthy
        maksaja-Yritys-postiosoite (-> maksaja-Yritys :postiosoite) => truthy
        ;; maksajan henkilotieto-osa
        rooliKoodi-maksajan-vastuuhenkilo "maksajan vastuuhenkil\u00f6"
        maksaja-filter-fn #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-maksajan-vastuuhenkilo)
        maksaja-Vastuuhenkilo (:Vastuuhenkilo (first (filter maksaja-filter-fn vastuuhenkilot-vec)))
        maksaja-Vastuuhenkilo-osoite (-> maksaja-Vastuuhenkilo :osoitetieto :osoite) => truthy

        ;; Testataan muunnosfunktiota yksityisella maksajalla ("henkilo"-tyyppinen maksaja)
        maksaja-yksityinen (get-yritys-and-henkilo
                             (tools/unwrapped
                               (assoc-in (:data maksaja) [:_selected :value] "henkilo")) "maksaja")
        maksaja-yksityinen-Henkilo (-> maksaja-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
        _ (:nimi maksaja-yksityinen-Henkilo) => truthy
        _ (:osoite maksaja-yksityinen-Henkilo) => truthy

        ;; Tapahtuman alkupvm ja loppupvm
        alkuPvm (-> Mainostuslupa :alkuPvm) => truthy
        loppuPvm (-> Mainostuslupa :loppuPvm) => truthy

        Toimintajakso (-> Mainostuslupa :toimintajaksotieto :Toimintajakso) => truthy
        mainostustapahtuma-alku-pvm (-> Toimintajakso :alkuHetki) => truthy
        mainostustapahtuma-loppu-pvm (-> Toimintajakso :loppuHetki) => truthy

        ;; :lupaAsianKuvaus and :sijoituslupaviitetieto do not appear
        _ (:lupaAsianKuvaus Mainostuslupa) => falsey
        _ (:sijoituslupaviitetieto Mainostuslupa) => falsey

        osapuolet-vec (-> Mainostuslupa :osapuolitieto) => truthy

;        ;; hakijan yritystieto-osa
;        rooliKoodi-Hakija "hakija"
;        hakija-osapuoli-filter-fn #(= (-> % :Osapuoli :rooliKoodi) rooliKoodi-Hakija)
;        hakija-Osapuoli (:Osapuoli (first (filter hakija-osapuoli-filter-fn osapuolet-vec)))
;        hakija-Yritys (-> hakija-Osapuoli :yritystieto :Yritys) => truthy
;        hakija-yritys-Postiosoite (-> hakija-Yritys :postiosoitetieto :Postiosoite) => truthy
;        ;; hakijan henkilotieto-osa
;        rooliKoodi-hankkeen-vastuuhenkilo "hankkeen vastuuhenkil\u00f6"
;        hakija-vastuuhenkilo-filter-fn #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-hankkeen-vastuuhenkilo)
;        hakija-Vastuuhenkilo (:Vastuuhenkilo (first (filter hakija-vastuuhenkilo-filter-fn vastuuhenkilot-vec)))
;        hakija-Vastuuhenkilo-osoite (-> hakija-Vastuuhenkilo :osoitetieto :osoite) => truthy

        rooliKoodi-Hakija "hakija"
        hakija-filter-fn #(= (-> % :Osapuoli :rooliKoodi) rooliKoodi-Hakija)
        hakija-Osapuoli (:Osapuoli (first (filter hakija-filter-fn osapuolet-vec)))
        hakija-Henkilo (-> hakija-Osapuoli :henkilotieto :Henkilo) => truthy  ;; kyseessa yrityksen vastuuhenkilo
        hakija-Yritys (-> hakija-Osapuoli :yritystieto :Yritys) => truthy
        hakija-henkilo-nimi (:nimi hakija-Henkilo) => truthy
        hakija-yritys-Postiosoite (-> hakija-Yritys :postiosoitetieto :Postiosoite) => truthy

        ;; Testataan muunnosfunktiota yksityisella hakijalla ("henkilo"-tyyppinen hakija)
        hakija-yksityinen (get-hakija
                            (tools/unwrapped
                              (assoc-in hakija [:data :_selected :value] "henkilo")))
        hakija-yksityinen-Henkilo (-> hakija-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
        hakija-yksityinen-nimi (:nimi hakija-yksityinen-Henkilo) => truthy
        hakija-yksityinen-osoite (:osoite hakija-yksityinen-Henkilo) => truthy

        lisatieto-vec (-> Mainostuslupa :lupakohtainenLisatietotieto) => truthy

        _ (:pintaala Mainostuslupa) => falsey

        match-fn #(= "Tapahtuman nimi" (-> % :LupakohtainenLisatieto :selitysteksti))
        tapahtuman-nimi-Lisatieto (:LupakohtainenLisatieto (first (filter match-fn lisatieto-vec))) => truthy
        tapahtuman-nimi (:arvo tapahtuman-nimi-Lisatieto) => truthy

        match-fn #(= "Tapahtumapaikka" (-> % :LupakohtainenLisatieto :selitysteksti))
        tapahtumapaikka-Lisatieto (:LupakohtainenLisatieto (first (filter match-fn lisatieto-vec))) => truthy
        tapahtumapaikka (:arvo tapahtumapaikka-Lisatieto) => truthy

        match-fn #(= "Haetaan kausilupaa" (-> % :LupakohtainenLisatieto :selitysteksti))
        haetaan-kausilupaa-Lisatieto (:LupakohtainenLisatieto (first (filter match-fn lisatieto-vec))) => truthy
        haetaan-kausilupaa (:arvo haetaan-kausilupaa-Lisatieto) => truthy

        ;;
        ;; Testataan muunnosfunktiota myos "viitoitus tapahtuma" valittuna
        ;;
        canonical-2 (application-to-canonical viitoitus-application "fi")
        Viitoituslupa (-> canonical-2 :YleisetAlueet :yleinenAlueAsiatieto :Kayttolupa) => truthy

        _ (:lupaAsianKuvaus Viitoituslupa) => falsey
        _ (:sijoituslupaviitetieto Viitoituslupa) => falsey
        _ (:toimintajaksotieto canonical-2) => falsey

        alkuPvm-2 (-> Viitoituslupa :alkuPvm) => truthy
        loppuPvm-2 (-> Viitoituslupa :loppuPvm) => truthy

        lisatieto-vec-2 (-> Viitoituslupa :lupakohtainenLisatietotieto) => truthy
        match-fn-2 #(= "Haetaan kausilupaa" (-> % :LupakohtainenLisatieto :selitysteksti))
        _ (:LupakohtainenLisatieto (first (filter match-fn-2 lisatieto-vec-2))) => falsey]

    (fact "contains nil" (util/contains-value? canonical nil?) => falsey)

    (fact "lupatunnus"
      (count muu-tunnustieto) => 1
      (-> muu-tunnustieto first :MuuTunnus :tunnus) => (:id mainostus-application)
      (-> muu-tunnustieto first :MuuTunnus :sovellus) => "Lupapiste")

    (fact "Kasittelytieto-muutosHetki" (:muutosHetki Kasittelytieto) => (date/xml-datetime (:modified mainostus-application)))
    (fact "Kasittelytieto-hakemuksenTila" (:hakemuksenTila Kasittelytieto) => "vireill\u00e4")
    (fact "Kasittelytieto-asiatunnus" (:asiatunnus Kasittelytieto) => (:id mainostus-application))
    (fact "Kasittelytieto-paivaysPvm" (:paivaysPvm Kasittelytieto) => (date/xml-date (:opened mainostus-application)))
    (fact "Kasittelytieto-kasittelija-etunimi" (:etunimi Kasittelytieto-kasittelija-nimi) => (:firstName sonja))
    (fact "Kasittelytieto-kasittelija-sukunimi" (:sukunimi Kasittelytieto-kasittelija-nimi) => (:lastName sonja))

    (fact "Mainostuslupa-kayttotarkoitus" Mainostuslupa-kayttotarkoitus => ((keyword (:name operation)) ya-operation-type-to-usage-description))

    ;; Sijainti
    (fact "Sijainti-yksilointitieto" Sijainti-yksilointitieto => (:id mainostus-application))
    (fact "Sijainti-osoitenimi" Sijainti-osoitenimi => (:address mainostus-application))
    (fact "Sijainti-piste-xy" Sijainti-piste => (str (-> mainostus-application :location first) " " (-> mainostus-application :location second)))

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

    ;; Osapuoli: Hakija
;    (fact "hakija-vastuuhenkilo-rooliKoodi" (:rooliKoodi hakija-Vastuuhenkilo) => rooliKoodi-hankkeen-vastuuhenkilo)
;    (fact "hakija-henkilo-etunimi" (:etunimi hakija-Vastuuhenkilo) => (-> nimi :etunimi :value))
;    (fact "hakija-henkilo-sukunimi" (:sukunimi hakija-Vastuuhenkilo) => (-> nimi :sukunimi :value))
;    (fact "hakija-henkilo-sahkopostiosoite" (:sahkopostiosoite hakija-Vastuuhenkilo) => (-> yhteystiedot :email :value))
;    (fact "hakija-henkilo-puhelinnumero" (:puhelinnumero hakija-Vastuuhenkilo) => (-> yhteystiedot :puhelin :value))
;    (fact "hakija-henkilo-osoite-osoitenimi"
;      (-> hakija-Vastuuhenkilo-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
;    (fact "hakija-henkilo-osoite-postinumero"
;      (:postinumero hakija-Vastuuhenkilo-osoite) => (-> osoite :postinumero :value))
;    (fact "hakija-henkilo-osoite-postitoimipaikannimi"
;      (:postitoimipaikannimi hakija-Vastuuhenkilo-osoite) => (-> osoite :postitoimipaikannimi :value))
;    (fact "hakija-osapuoli-rooliKoodi" (:rooliKoodi hakija-Osapuoli) => rooliKoodi-Hakija)
;    (fact "hakija-yritys-nimi" (:nimi hakija-Yritys) => (-> yritys-nimi-ja-tunnus :yritysnimi :value))
;    (fact "hakija-yritys-liikeJaYhteisotunnus" (:liikeJaYhteisotunnus hakija-Yritys) => (-> yritys-nimi-ja-tunnus :liikeJaYhteisoTunnus :value))
;    (fact "hakija-yritys-osoitenimi" (-> hakija-yritys-Postiosoite :osoitenimi :teksti) => (-> osoite :katu :value))
;    (fact "hakija-yritys-postinumero" (:postinumero hakija-yritys-Postiosoite) => (-> osoite :postinumero :value))
;    (fact "hakija-yritys-postitoimipaikannimi" (:postitoimipaikannimi hakija-yritys-Postiosoite) => (-> osoite :postitoimipaikannimi :value))
;
;    ;; Hakija, yksityinen henkilo -> Tama on testattu jo kohdassa "Maksaja, yksityinen henkilo" (muunnos on taysin sama)

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

    ;; Hakija, yksityinen henkilo
    (fact "hakija-yksityinen-etunimi" (:etunimi hakija-yksityinen-nimi) => (-> nimi :etunimi :value))
    (fact "hakija-yksityinen-sukunimi" (:sukunimi hakija-yksityinen-nimi) => (-> nimi :sukunimi :value))
    (fact "hakija-yksityinen-osoitenimi" (-> hakija-yksityinen-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
    (fact "hakija-yksityinen-postinumero" (:postinumero hakija-yksityinen-osoite) => (-> osoite :postinumero :value))
    (fact "hakija-yksityinen-postitoimipaikannimi" (:postitoimipaikannimi hakija-yksityinen-osoite) => (-> osoite :postitoimipaikannimi :value))
    (fact "hakija-yksityinen-sahkopostiosoite" (:sahkopostiosoite hakija-yksityinen-Henkilo) => (-> yhteystiedot :email :value))
    (fact "hakija-yksityinen-puhelin" (:puhelin hakija-yksityinen-Henkilo) => (-> yhteystiedot :puhelin :value))
    (fact "hakija-yksityinen-henkilotunnus" (:henkilotunnus hakija-yksityinen-Henkilo) => (-> henkilotiedot :hetu :value))

    ;; alku-/loppupvm
    ;;   Mainostustapahtuma
    (fact "alkuPvm mainostustapahtuma"
      alkuPvm => (date/xml-date "1.8.2013"))
    (fact "loppuPvm mainostustapahtuma"
      loppuPvm => (date/xml-date "30.8.2013"))
    ;;   Viitoitustapahtuma
    (fact "alkuPvm viitoitustapahtuma"
      alkuPvm-2 => (date/xml-date "14.9.2013"))
    (fact "loppuPvm viitoitustapahtuma"
      loppuPvm-2 => (date/xml-date "17.9.2013"))

    ;; Toimintajakso, mainostuksen alku- ja loppuhetki
    (fact "mainostustapahtuma-alku-pvm"
      mainostustapahtuma-alku-pvm => (date/xml-datetime "13.9.2013"))
    (fact "mainostustapahtuma-loppu-pvm"
      mainostustapahtuma-loppu-pvm => (date/xml-datetime "16.9.2013"))

    ;; Lisatiedot
    (fact "tapahtuman-nimi" tapahtuman-nimi => (-> tapahtuma-info :data :mainostus-tapahtuma-valinta :tapahtuman-nimi :value))
    (fact "tapahtumapaikka" tapahtumapaikka => (-> tapahtuma-info :data :mainostus-tapahtuma-valinta :tapahtumapaikka :value))
    (fact "haetaan-kausilupaa" haetaan-kausilupaa => (-> tapahtuma-info :data :mainostus-tapahtuma-valinta :haetaan-kausilupaa :value))))
