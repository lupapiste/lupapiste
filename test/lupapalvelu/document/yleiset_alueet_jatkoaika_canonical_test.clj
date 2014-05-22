(ns lupapalvelu.document.yleiset-alueet-jatkoaika-canonical-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer :all]
            [lupapalvelu.factlet :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [jatkoaika-to-canonical]]
            [sade.util :refer :all]))


(def ^:private operation {:id "52aab3daad59b51c3e6d4adb"
                          :created 1386918874564
                          :name "ya-jatkoaika"})

(def ^:private link-permit-data {:id "LP-753-2013-00003"
                                 :type "lupapistetunnus"
                                 :operation "ya-katulupa-vesi-ja-viemarityot"})

(def ^:private hankkeen-kuvaus-jatkoaika {:id "Hankkeen kuvaus"
                                          :schema-info {:name "hankkeen-kuvaus-jatkoaika" :version 1 :order 1}
                                          :data {:kuvaus {:value "Kaksi viikkoa lisaaikaa hakkeelle LP-753-2013-00017."}}})

(def ^:private tyoaika-jatkoaika {:id "52ab1eedad593ba3e388e9af"
                                  :created 1386946285742
                                  :schema-info {:name "tyo-aika-for-jatkoaika" :version 1 :order 63}
                                  :data {:tyoaika-alkaa-pvm {:value "28.12.2013"}
                                         :tyoaika-paattyy-pvm {:value "30.12.2013"}}})

(def ^:private  documents [hakija
                           maksaja
                           hankkeen-kuvaus-jatkoaika
                           tyoaika-jatkoaika])

(def jatkoaika-application {:id "LP-753-2013-00005"
                            :schema-version 1
                            :created 1386918874564
                            :opened 1386920752686
                            :modified 1386920727373
                            :submitted 1386920752686
                            :permitType "YA"
                            :organization "753-YA"
                            :linkPermitData [link-permit-data],
                            :infoRequest false
                            :openInfoRequest false
                            :authority sonja
                            :state "submitted"
                            :title "Latokuja 1"
                            :address "Latokuja 1"
                            :location location
                            :attachments []
                            :operations [operation]
                            :propertyId "75342300010054"
                            :documents documents
                            :municipality "753"
                            :statements statements})

;;*******************
;; TODO:
;;
;; Avaimien :continuation-period-end-date  ja  :continuation-period-description
;; canonical-testaukseen riittaa se, etta tekee sen jossain ya canonical_testissa, esim kaivuluvalla.
;; Eli tama ns nevadaan...
;;
;;*******************

(facts* "Jatkoaika canonical model is correct"
  (let [canonical (jatkoaika-to-canonical jatkoaika-application "fi")
        YleisetAlueet (:YleisetAlueet canonical) => truthy
        yleinenAlueAsiatieto (:yleinenAlueAsiatieto YleisetAlueet) => truthy
        Jatkoaika (:Tyolupa yleinenAlueAsiatieto) => truthy

        Kasittelytieto (-> Jatkoaika :kasittelytietotieto :Kasittelytieto) => truthy
        Kasittelytieto-kasittelija-nimi (-> Kasittelytieto :kasittelija :henkilotieto :Henkilo :nimi) => truthy

        luvanTunnisteTiedot (:luvanTunnisteTiedot Jatkoaika) => truthy
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        lp-muu-tunnus (:MuuTunnus (first muuTunnustieto)) => truthy
        viitelupatunnus (:MuuTunnus (second muuTunnustieto)) => truthy

        Jatkoaika-kayttotarkoitus (:kayttotarkoitus Jatkoaika) => truthy

        Sijainti-osoite (-> Jatkoaika :sijaintitieto first :Sijainti :osoite) => truthy
        Sijainti-yksilointitieto (-> Sijainti-osoite :yksilointitieto) => truthy
        Sijainti-alkuHetki (-> Sijainti-osoite :alkuHetki) => truthy
        Sijainti-osoitenimi (-> Sijainti-osoite :osoitenimi :teksti) => truthy
        Sijainti-piste (-> Jatkoaika :sijaintitieto first :Sijainti :piste :Point :pos) => truthy

        osapuolet-vec (-> Jatkoaika :osapuolitieto) => truthy
        vastuuhenkilot-vec (-> Jatkoaika :vastuuhenkilotieto) => truthy

        ;; maksajan yritystieto-osa
        Maksaja (-> Jatkoaika :maksajatieto :Maksaja) => truthy
        maksaja-Yritys (-> Maksaja :yritystieto :Yritys) => truthy
        maksaja-Yritys-postiosoite (-> maksaja-Yritys :postiosoite) => truthy
        ;; maksajan henkilotieto-osa
        rooliKoodi-maksajan-vastuuhenkilo "maksajan vastuuhenkil\u00f6"
        maksaja-filter-fn #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-maksajan-vastuuhenkilo)
        maksaja-Vastuuhenkilo (:Vastuuhenkilo (first (filter maksaja-filter-fn vastuuhenkilot-vec)))
        maksaja-Vastuuhenkilo-osoite (-> maksaja-Vastuuhenkilo :osoitetieto :osoite) => truthy

        Lisaaika (-> Jatkoaika :lisaaikatieto :Lisaaika) => truthy
        perustelu (:perustelu Lisaaika) => truthy
        alkuPvm (:alkuPvm Lisaaika) => truthy
        loppuPvm (:loppuPvm Lisaaika) => truthy

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

        lupakohtainenLisatietotieto (-> Jatkoaika :lupakohtainenLisatietotieto) => falsey

        pinta-ala (:pintaala Jatkoaika) => falsey]


    (fact "contains nil" (contains-value? canonical nil?) => falsey)

    (fact "Kasittelytieto-muutosHetki" (:muutosHetki Kasittelytieto) => (to-xml-datetime (:modified jatkoaika-application)))
    (fact "Kasittelytieto-hakemuksenTila" (:hakemuksenTila Kasittelytieto) => "vireill\u00e4")
    (fact "Kasittelytieto-asiatunnus" (:asiatunnus Kasittelytieto) => (:id jatkoaika-application))
    (fact "Kasittelytieto-paivaysPvm" (:paivaysPvm Kasittelytieto) => (to-xml-date (:opened jatkoaika-application)))
    (fact "Kasittelytieto-kasittelija-etunimi" (:etunimi Kasittelytieto-kasittelija-nimi) => (:firstName sonja))
    (fact "Kasittelytieto-kasittelija-sukunimi" (:sukunimi Kasittelytieto-kasittelija-nimi) => (:lastName sonja))

    (fact "LP-tunnus"
      (:tunnus lp-muu-tunnus) => (:id jatkoaika-application)
      (:sovellus lp-muu-tunnus) => "Lupapiste")

    (fact "Viitelupa"
      (:tunnus viitelupatunnus) => (:id link-permit-data)
      (:sovellus viitelupatunnus) => "Viitelupa")

    (fact "Jatkoaika-kayttotarkoitus" Jatkoaika-kayttotarkoitus => (ya-operation-type-to-usage-description
                                                                     (keyword (:operation link-permit-data))))

    ;; Sijainti
    (fact "Sijainti-yksilointitieto" Sijainti-yksilointitieto => (:id jatkoaika-application))
    (fact "Sijainti-osoitenimi" Sijainti-osoitenimi => (:address jatkoaika-application))
    (fact "Sijainti-piste-xy" Sijainti-piste => (str (-> jatkoaika-application :location :x) " " (-> jatkoaika-application :location :y)))

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

;    ;; Osapuoli: Hakija
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

    ;; Lisaaikatieto
    ;;
    ;;  *** TODO: Testaa paperisen version casea. ***
    ;;
    (fact "Lisaaika-perustelu" perustelu => (-> hankkeen-kuvaus-jatkoaika :data :kuvaus :value))
    (fact "Lisaaika-alkuPvm" alkuPvm => (to-xml-date-from-string (-> tyoaika-jatkoaika :data :tyoaika-alkaa-pvm :value)))
    (fact "Lisaaika-loppuPvm" loppuPvm => (to-xml-date-from-string (-> tyoaika-jatkoaika :data :tyoaika-paattyy-pvm :value)))
    ))

