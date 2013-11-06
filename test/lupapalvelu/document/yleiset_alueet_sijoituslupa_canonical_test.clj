(ns lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer :all]
            [lupapalvelu.factlet :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical]]
            [sade.util :refer [contains-value?]]))


(def operation {:id "523ae9ba94a7542b3520e649",
                :created 1379592634015,
                :name "ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen"})

(def hankkeen-kuvaus-sijoituslupa {:id "523ae9ba94a7542b3520e64a",
                                   :created 1379592634015,
                                   :schema-info {:order 65,
                                                 :version 1,
                                                 :repeating false,
                                                 :removable false,
                                                 :name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa",
                                                 :op operation},
                                   :data {:kayttotarkoitus {:modified 1379592642729, :value "Hankkeen kuvaus."},
                                          :kaivuLuvanTunniste {:modified 1379592648571, :value "1234567890"}}})

(def sijoituksen-tarkoitus {:id "523ae9ba94a7542b3520e64c",
                            :created 1379592634015,
                            :schema-info {:name "sijoituslupa-sijoituksen-tarkoitus",
                                          :removable false,
                                          :repeating false,
                                          :version 1,
                                          :order 66},
                            :data {:lisatietoja-sijoituskohteesta {:modified 1379592841005, :value "Lis\u00e4tietoja."},
                                   :sijoituksen-tarkoitus {:modified 1379592651471, :value "other"},
                                   ;; Huom: tama nakyy vain, jos yllaolevan :sijoituksen-tarkoitus:n value on "other"
                                   :muu-sijoituksen-tarkoitus {:modified 1379592733099, :value "Muu sijoituksen tarkoitus."}}})

(def documents [hakija
                maksaja
                hankkeen-kuvaus-sijoituslupa
                sijoituksen-tarkoitus])

(def operation {:id "523ae9ba94a7542b3520e649",
                :created 1379592634015,
                :name "ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen"})

;(def neighbors {:523aeb5794a7542b3520e7e4
;                {:neighbor
;                 {:propertyId "75342300040104",
;                  :owner
;                  {:name "Esko Naapuri",
;                   :email "esko.naapuri@sipoo.fi",
;                   :address {:street "Osoite 1 a 1", :city "Sipoo", :zip "33580"}}},
;                 :status [{:state "open", :created 1379593047958}]}})

;(def allowedAttachmentTypes [["yleiset-alueet"
;                              ["aiemmin-hankittu-sijoituspaatos"
;                               "tilapainen-liikennejarjestelysuunnitelma"
;                               "tyyppiratkaisu"
;                               "tieto-kaivupaikkaan-liittyvista-johtotiedoista"
;                               "liitoslausunto"
;                               "asemapiirros"
;                               "rakennuspiirros"
;                               "suunnitelmakartta"]]
;                             ["muut" ["muu"]]])

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
                               ; :allowedAttachmentTypes allowedAttachmentTypes,
                               ; :neighbors neighbors,
                               :municipality municipality,
                               :statements statements})

(def get-maksaja #'lupapalvelu.document.yleiset-alueet-canonical/get-maksaja)
(def get-hakija #'lupapalvelu.document.yleiset-alueet-canonical/get-hakija)
(def get-sijoituksen-tarkoitus #'lupapalvelu.document.yleiset-alueet-canonical/get-sijoituksen-tarkoitus)

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

        Sijainti-osoite (-> Sijoituslupa :sijaintitieto :Sijainti :osoite) => truthy
        Sijainti-yksilointitieto (-> Sijainti-osoite :yksilointitieto) => truthy
        Sijainti-alkuHetki (-> Sijainti-osoite :alkuHetki) => truthy
        Sijainti-osoitenimi (-> Sijainti-osoite :osoitenimi :teksti) => truthy
        Sijainti-piste (-> Sijoituslupa :sijaintitieto :Sijainti :piste :Point :pos) => truthy

        osapuolet-vec (-> Sijoituslupa :osapuolitieto) => truthy
        vastuuhenkilot-vec (-> Sijoituslupa :vastuuhenkilotieto) => truthy

        ;; maksajan henkilotieto-osa
        rooliKoodi-maksajan-vastuuhenkilo "maksajan vastuuhenkil\u00f6"
        maksaja-filter-fn #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-maksajan-vastuuhenkilo)
        maksaja-Vastuuhenkilo (:Vastuuhenkilo (first (filter maksaja-filter-fn vastuuhenkilot-vec)))
        maksaja-Vastuuhenkilo-osoite (-> maksaja-Vastuuhenkilo :osoitetieto :osoite) => truthy
        ;; maksajan yritystieto-osa
        Maksaja (-> Sijoituslupa :maksajatieto :Maksaja) => truthy
        maksaja-Yritys (-> Maksaja :yritystieto :Yritys) => truthy
        maksaja-Yritys-postiosoite (-> maksaja-Yritys :postiosoite) => truthy

        ;; Testataan muunnosfunktiota yksityisella maksajalla ("henkilo"-tyyppinen maksaja)
        maksaja-yksityinen (get-maksaja
                             (assoc-in (:data maksaja) [:_selected :value] "henkilo"))
        maksaja-yksityinen-Henkilo (-> maksaja-yksityinen :henkilotieto :Henkilo) => truthy
        maksaja-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
        maksaja-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

        ;; These (alkuPvm and loppuPvm) are not filled in the application, but are requested by schema
        alkuPvm (-> Sijoituslupa :alkuPvm) => truthy
        loppuPvm (-> Sijoituslupa :loppuPvm) => truthy

        lupaAsianKuvaus (:lupaAsianKuvaus Sijoituslupa) => truthy

        Sijoituslupaviite (-> Sijoituslupa :sijoituslupaviitetieto :Sijoituslupaviite) => truthy

        rooliKoodi-Hakija "hakija"
        hakija-filter-fn #(= (-> % :Osapuoli :rooliKoodi) rooliKoodi-Hakija)
        hakija-Osapuoli (:Osapuoli (first (filter hakija-filter-fn osapuolet-vec)))
        hakija-Henkilo (-> hakija-Osapuoli :henkilotieto :Henkilo) => truthy  ;; kyseessa yrityksen vastuuhenkilo
        hakija-Yritys (-> hakija-Osapuoli :yritystieto :Yritys) => truthy
        hakija-henkilo-nimi (:nimi hakija-Henkilo) => truthy
        hakija-yritys-Postiosoite (-> hakija-Yritys :postiosoitetieto :Postiosoite) => truthy

        ;; Testataan muunnosfunktiota yksityisella hakijalla ("henkilo"-tyyppinen hakija)
        hakija-yksityinen (get-hakija
                            (assoc-in (:data hakija) [:_selected :value] "henkilo"))
        hakija-yksityinen-Henkilo (-> hakija-yksityinen :henkilotieto :Henkilo) => truthy
        hakija-yksityinen-nimi (:nimi hakija-yksityinen-Henkilo) => truthy
        hakija-yksityinen-osoite (:osoite hakija-yksityinen-Henkilo) => truthy

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
        lisatietoja-sijoituskohteesta(:arvo lisatietoja-sijoituskohteesta-Lisatieto) => truthy]

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

    (fact "lisatietoja-sijoituskohteesta" sijoituksen-tark => (-> sijoituksen-tarkoitus :data :sijoituksen-tarkoitus :value))
    (fact "lisatietoja-sijoituskohteesta-liikennevalo" (:arvo sijoituksen-tark-liikennevalo) => "liikennevalo")
    (fact "lisatietoja-sijoituskohteesta" lisatietoja-sijoituskohteesta => (-> sijoituksen-tarkoitus :data :lisatietoja-sijoituskohteesta :value))

    ;; Kayton alku/loppu pvm  (just something in Sijoituslupa, because schema requests it)
    (fact "alkuPvm" alkuPvm => truthy)
    (fact "loppuPvm" loppuPvm => truthy)

    ;; Hankkeen kuvaus
    (fact "lupaAsianKuvaus" lupaAsianKuvaus => (-> hankkeen-kuvaus :data :kayttotarkoitus :value))
    (fact "vaadittuKytkin" (:vaadittuKytkin Sijoituslupaviite) => false)
    (fact "Sijoituslupaviite" (:tunniste Sijoituslupaviite) => (-> hankkeen-kuvaus-sijoituslupa :data :kaivuLuvanTunniste :value))))
