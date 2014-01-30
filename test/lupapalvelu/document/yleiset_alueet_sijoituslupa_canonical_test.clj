(ns lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer :all]
            [lupapalvelu.factlet :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical]]
            [sade.util :refer :all]))


(def ^:private operation {:id "523ae9ba94a7542b3520e649",
                          :created 1379592634015,
                          :name "ya-sijoituslupa-maalampoputkien-sijoittaminen"})

(def ^:private  hankkeen-kuvaus-sijoituslupa
  {:id "523ae9ba94a7542b3520e64a"
   :created 1379592634015
   :schema-info {:order 65
                 :version 1
                 :repeating false
                 :removable false
                 :name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
                 :op operation}
   :data {:kayttotarkoitus {:value "Hankkeen kuvaus."}}})

(def ^:private sijoituksen-tarkoitus
  {:id "523ae9ba94a7542b3520e64c"
   :created 1379592634015
   :schema-info {:name "sijoituslupa-sijoituksen-tarkoitus"
                 :removable false
                 :repeating false
                 :version 1
                 :order 66}
   :data {:lisatietoja-sijoituskohteesta {:value "Lis\u00e4tietoja."}
          :sijoituksen-tarkoitus {:value "other"},
          ;; Huom: tama nakyy vain, jos yllaolevan :sijoituksen-tarkoitus:n value on "other"
          :muu-sijoituksen-tarkoitus {:value "Muu sijoituksen tarkoitus."}}})

(def ^:private  documents [hakija
                           maksaja
                           hankkeen-kuvaus-sijoituslupa
                           sijoituksen-tarkoitus])

(def sijoituslupa-application {:schema-version 1,
                               :id "LP-753-2013-00003",
                               :created 1379592634015,
                               :opened 1379592902883,
                               :modified 1379592969636,
                               :submitted 1379592916811,
                               :permitType "YA",
                               :organization "753-YA",
                               :infoRequest false,
                               :authority sonja,
                               :state "submitted",
                               :title "Hirvim\u00e4entie 112",
                               :address "Hirvim\u00e4entie 112",
                               :location location,
                               :attachments [],
                               :operations [operation],
                               :propertyId "75342300010054",
                               :documents documents,
                               ; :neighbors neighbors,
                               :municipality municipality,
                               :statements statements})

(testable-privates lupapalvelu.document.yleiset-alueet-canonical get-yritys-and-henkilo get-sijoituksen-tarkoitus)

(facts* "Sijoituslupa canonical model is correct"
  (let [canonical (application-to-canonical sijoituslupa-application "fi")
        YleisetAlueet (:YleisetAlueet canonical) => truthy
        yleinenAlueAsiatieto (:yleinenAlueAsiatieto YleisetAlueet) => truthy
        Sijoituslupa (:Sijoituslupa yleinenAlueAsiatieto) => truthy

        Kasittelytieto (-> Sijoituslupa :kasittelytietotieto :Kasittelytieto) => truthy
        Kasittelytieto-kasittelija-nimi (-> Kasittelytieto :kasittelija :henkilotieto :Henkilo :nimi) => truthy

        luvanTunnisteTiedot (:luvanTunnisteTiedot Sijoituslupa) => truthy
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy

        Sijoituslupa-kayttotarkoitus (:kayttotarkoitus Sijoituslupa) => truthy

        Sijainti-osoite (-> Sijoituslupa :sijaintitieto first :Sijainti :osoite) => truthy
        Sijainti-yksilointitieto (-> Sijainti-osoite :yksilointitieto) => truthy
        Sijainti-alkuHetki (-> Sijainti-osoite :alkuHetki) => truthy
        Sijainti-osoitenimi (-> Sijainti-osoite :osoitenimi :teksti) => truthy
        Sijainti-piste (-> Sijoituslupa :sijaintitieto first :Sijainti :piste :Point :pos) => truthy

        osapuolet-vec (-> Sijoituslupa :osapuolitieto) => truthy
        vastuuhenkilot-vec (-> Sijoituslupa :vastuuhenkilotieto) => truthy

        ;; maksajan yritystieto-osa
        Maksaja (-> Sijoituslupa :maksajatieto :Maksaja) => truthy
        maksaja-Yritys (-> Maksaja :yritystieto :Yritys) => truthy
        maksaja-Yritys-postiosoite (-> maksaja-Yritys :postiosoite) => truthy
        ;; maksajan henkilotieto-osa
        rooliKoodi-maksajan-vastuuhenkilo "maksajan vastuuhenkil\u00f6"
        maksaja-filter-fn #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-maksajan-vastuuhenkilo)
        maksaja-Vastuuhenkilo (:Vastuuhenkilo (first (filter maksaja-filter-fn vastuuhenkilot-vec)))
        maksaja-Vastuuhenkilo-osoite (-> maksaja-Vastuuhenkilo :osoitetieto :osoite) => truthy

        ;; Testataan muunnosfunktiota yksityisella maksajalla ("henkilo"-tyyppinen maksaja)
        maksaja-yksityinen (get-yritys-and-henkilo (assoc-in (:data maksaja) [:_selected :value] "henkilo") "maksaja")
        maksaja-yksityinen-Henkilo (-> maksaja-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
        maksaja-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
        maksaja-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

        ;; These (alkuPvm and loppuPvm) are not filled in the application, but are requested by schema
        alkuPvm (-> Sijoituslupa :alkuPvm) => truthy
        loppuPvm (-> Sijoituslupa :loppuPvm) => truthy

        lupaAsianKuvaus (:lupaAsianKuvaus Sijoituslupa) => truthy

        ;; hakijan yritystieto-osa
        rooliKoodi-Hakija "hakija"
        hakija-osapuoli-filter-fn #(= (-> % :Osapuoli :rooliKoodi) rooliKoodi-Hakija)
        hakija-Osapuoli (:Osapuoli (first (filter hakija-osapuoli-filter-fn osapuolet-vec)))
        hakija-Yritys (-> hakija-Osapuoli :yritystieto :Yritys) => truthy
        hakija-yritys-Postiosoite (-> hakija-Yritys :postiosoitetieto :Postiosoite) => truthy
        ;; hakijan henkilotieto-osa
        rooliKoodi-hankkeen-vastuuhenkilo "hankkeen vastuuhenkil\u00f6"
        hakija-vastuuhenkilo-filter-fn #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-hankkeen-vastuuhenkilo)
        hakija-Vastuuhenkilo (:Vastuuhenkilo (first (filter hakija-vastuuhenkilo-filter-fn vastuuhenkilot-vec)))
        hakija-Vastuuhenkilo-osoite (-> hakija-Vastuuhenkilo :osoitetieto :osoite) => truthy

        ;; Testataan muunnosfunktiota yksityisella hakijalla ("henkilo"-tyyppinen hakija)
        hakija-yksityinen (get-yritys-and-henkilo (assoc-in (:data maksaja) [:_selected :value] "henkilo") "hakija")
        hakija-yksityinen-Henkilo (-> maksaja-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
        hakija-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
        hakija-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

        lisatieto-vec (-> Sijoituslupa :lupakohtainenLisatietotieto) => truthy

        match-fn #(= "Sijoituksen tarkoitus" (-> % :LupakohtainenLisatieto :selitysteksti))
        sijoituksen-tarkoitus-Lisatieto (:LupakohtainenLisatieto (first (filter match-fn lisatieto-vec))) => truthy
        sijoituksen-tark (:arvo sijoituksen-tarkoitus-Lisatieto) => truthy

        ;; Testataan muunnosfunktiota muulla kuin "other" sijoituksen-tarkoituksella
        sijoituksen-tark-liikennevalo (get-sijoituksen-tarkoitus
                                        (assoc-in (:data sijoituksen-tarkoitus)
                                          [:sijoituksen-tarkoitus :value]
                                          "liikennevalo")) => truthy

        match-fn #(= "Lis\u00e4tietoja sijoituskohteesta" (-> % :LupakohtainenLisatieto :selitysteksti))
        lisatietoja-sijoituskohteesta-Lisatieto (:LupakohtainenLisatieto (first (filter match-fn lisatieto-vec))) => truthy
        lisatietoja-sijoituskohteesta(:arvo lisatietoja-sijoituskohteesta-Lisatieto) => truthy

        pinta-ala (:pintaala Sijoituslupa) => falsey]

;    (println "\n canonical:")
;    (clojure.pprint/pprint canonical)
;    (println "\n")

    (fact "contains nil" (contains-value? canonical nil?) => falsey)

    (fact "Kasittelytieto-muutosHetki" (:muutosHetki Kasittelytieto) => (to-xml-datetime (:modified sijoituslupa-application)))
    (fact "Kasittelytieto-hakemuksenTila" (:hakemuksenTila Kasittelytieto) => "vireill\u00e4")
    (fact "Kasittelytieto-asiatunnus" (:asiatunnus Kasittelytieto) => (:id sijoituslupa-application))
    (fact "Kasittelytieto-paivaysPvm" (:paivaysPvm Kasittelytieto) => (to-xml-date (:opened sijoituslupa-application)))
    (fact "Kasittelytieto-kasittelija-etunimi" (:etunimi Kasittelytieto-kasittelija-nimi) => (:firstName sonja))
    (fact "Kasittelytieto-kasittelija-sukunimi" (:sukunimi Kasittelytieto-kasittelija-nimi) => (:lastName sonja))

    (fact "Muu tunnus" (:tunnus MuuTunnus) => "LP-753-2013-00003")
    (fact "Sovellus" (:sovellus MuuTunnus) => "Lupapiste")

    (fact "Sijoituslupa-kayttotarkoitus" Sijoituslupa-kayttotarkoitus => ((keyword (:name operation)) ya-operation-type-to-usage-description))

    ;; Sijainti
    (fact "Sijainti-yksilointitieto" Sijainti-yksilointitieto => (:id sijoituslupa-application))
    (fact "Sijainti-osoitenimi" Sijainti-osoitenimi => (:address sijoituslupa-application))
    (fact "Sijainti-piste-xy" Sijainti-piste => (str (-> sijoituslupa-application :location :x) " " (-> sijoituslupa-application :location :y)))

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
    (fact "hakija-vastuuhenkilo-rooliKoodi" (:rooliKoodi hakija-Vastuuhenkilo) => rooliKoodi-hankkeen-vastuuhenkilo)
    (fact "hakija-henkilo-etunimi" (:etunimi hakija-Vastuuhenkilo) => (-> nimi :etunimi :value))
    (fact "hakija-henkilo-sukunimi" (:sukunimi hakija-Vastuuhenkilo) => (-> nimi :sukunimi :value))
    (fact "hakija-henkilo-sahkopostiosoite" (:sahkopostiosoite hakija-Vastuuhenkilo) => (-> yhteystiedot :email :value))
    (fact "hakija-henkilo-puhelinnumero" (:puhelinnumero hakija-Vastuuhenkilo) => (-> yhteystiedot :puhelin :value))
    (fact "hakija-henkilo-osoite-osoitenimi"
      (-> hakija-Vastuuhenkilo-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
    (fact "hakija-henkilo-osoite-postinumero"
      (:postinumero hakija-Vastuuhenkilo-osoite) => (-> osoite :postinumero :value))
    (fact "hakija-henkilo-osoite-postitoimipaikannimi"
      (:postitoimipaikannimi hakija-Vastuuhenkilo-osoite) => (-> osoite :postitoimipaikannimi :value))
    (fact "hakija-osapuoli-rooliKoodi" (:rooliKoodi hakija-Osapuoli) => rooliKoodi-Hakija)
    (fact "hakija-yritys-nimi" (:nimi hakija-Yritys) => (-> yritys-nimi-ja-tunnus :yritysnimi :value))
    (fact "hakija-yritys-liikeJaYhteisotunnus" (:liikeJaYhteisotunnus hakija-Yritys) => (-> yritys-nimi-ja-tunnus :liikeJaYhteisoTunnus :value))
    (fact "hakija-yritys-osoitenimi" (-> hakija-yritys-Postiosoite :osoitenimi :teksti) => (-> osoite :katu :value))
    (fact "hakija-yritys-postinumero" (:postinumero hakija-yritys-Postiosoite) => (-> osoite :postinumero :value))
    (fact "hakija-yritys-postitoimipaikannimi" (:postitoimipaikannimi hakija-yritys-Postiosoite) => (-> osoite :postitoimipaikannimi :value))

    ;; Hakija, yksityinen henkilo -> Tama on testattu jo kohdassa "Maksaja, yksityinen henkilo" (muunnos on taysin sama)

    (fact "lisatietoja-sijoituskohteesta" sijoituksen-tark => (-> sijoituksen-tarkoitus :data :sijoituksen-tarkoitus :value))
    (fact "lisatietoja-sijoituskohteesta-liikennevalo" (:arvo sijoituksen-tark-liikennevalo) => "liikennevalo")
    (fact "lisatietoja-sijoituskohteesta" lisatietoja-sijoituskohteesta => (-> sijoituksen-tarkoitus :data :lisatietoja-sijoituskohteesta :value))

    ;; Kayton alku/loppu pvm  (just something in Sijoituslupa, because schema requests it)
    (fact "alkuPvm" alkuPvm => truthy)
    (fact "loppuPvm" loppuPvm => truthy)

    ;; Hankkeen kuvaus
    (fact "lupaAsianKuvaus" lupaAsianKuvaus => (-> hankkeen-kuvaus-sijoituslupa :data :kayttotarkoitus :value))))
