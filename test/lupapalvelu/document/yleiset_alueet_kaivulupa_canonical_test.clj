(ns lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.pate.verdict-canonical :refer [verdict-canonical]]
            [lupapalvelu.tasks]
            [lupapalvelu.test-util :refer [xml-datetime-is-roughly? dummy-doc]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.util :as util]))


(def- operation {:id "51cc1cab23e74941fee4f495",
                 :created 1372331179008,
                 :name "ya-katulupa-vesi-ja-viemarityot"})

(def- hankkeen-kuvaus {:id "52380c6894a74fc25bb4ba4a",
                       :created 1379404904514,
                       :schema-info {:name "yleiset-alueet-hankkeen-kuvaus-kaivulupa",
                                     :repeating false,
                                     :version 1,
                                     :type "group",
                                     :order 60},
                       :data {:kayttotarkoitus {:value "YA Hankkeen kuvaus."},
                              :sijoitusLuvanTunniste {:value "LP-753-2013-00002"}
                              :varattava-pinta-ala {:value "333"}}})

(def- tyomaasta-vastaava-kaivulupa (assoc-in tyomaasta-vastaava [:schema-info :op] operation))

(def- documents [hakija
                 tyomaasta-vastaava-kaivulupa
                 maksaja
                 hankkeen-kuvaus
                 tyoaika])

(def kaivulupa-application
  (-> {:id             "LP-753-2013-00001"
       :operation-name "ya-katulupa-vesi-ja-viemarityot"
       :organization   sipoo-ya
       :location       location
       :address        "Latokuja 1"
       :propertyId     "75341600550007"
       :municipality   municipality}
      (app/make-application
        []
        {:lastName  "Panaani",
         :firstName "Pena",
         :username  "pena",
         :role      "applicant",
         :id        "777777777777777777000020"}
        1372331179008
        nil)
      (update :permitSubtype str)
      (assoc :documents documents
             :attachments []
             :secondaryOperations []
             :statements statements
             :drawings ctc/drawings
             :state "submitted"
             :handlers [(assoc sonja :general true)]
             :auth [{:lastName  "Panaani",
                     :firstName "Pena",
                     :username  "pena",
                     :role      "writer",
                     :id        "777777777777777777000020"}])))

(ctc/validate-all-documents kaivulupa-application)

(def- link-permit-data [{:id "LP-753-2013-00003"
                         :type "kuntalupatunnus"
                         :operation nil}
                        {:id "LP-753-2013-00006"
                         :type "kuntalupatunnus"
                         :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"}])


(testable-privates lupapalvelu.document.yleiset-alueet-canonical
  get-yritys-and-henkilo get-tyomaasta-vastaava get-hakija)

(facts link-permit-selector-value

  (fact "without linked sijoituslupa"
    (link-permit-selector-value {}
                                [{:id "LP-753-2013-00003"
                                  :type "kuntalupatunnus"
                                  :operation nil}]
                                [:yleiset-alueet-hankkeen-kuvaus-kaivulupa :sijoitusLuvanTunniste]) => nil)

  (fact "without linked sijoituslupa - with custom value"
    (link-permit-selector-value  {:sijoitusLuvanTunniste "tunnus123"}
                                 [{:id "LP-753-2013-00003"
                                   :type "kuntalupatunnus"
                                   :operation nil}]
                                 [:yleiset-alueet-hankkeen-kuvaus-kaivulupa :sijoitusLuvanTunniste]) => "tunnus123")

  (fact "with linked sijoituslupa"
    (link-permit-selector-value {}
                                link-permit-data
                                [:yleiset-alueet-hankkeen-kuvaus-kaivulupa :sijoitusLuvanTunniste]) => "LP-753-2013-00006")

  (fact "with linked sijoituslupa - link permit overrides custom value"
    (link-permit-selector-value {:sijoitusLuvanTunniste "tunnus123"}
                                link-permit-data
                                [:yleiset-alueet-hankkeen-kuvaus-kaivulupa :sijoitusLuvanTunniste]) => "LP-753-2013-00006"))


(facts* "Kaivulupa canonical model is correct"
  (let [canonical (application-to-canonical kaivulupa-application "fi")
        YleisetAlueet (:YleisetAlueet canonical) => truthy
        yleinenAlueAsiatieto (:yleinenAlueAsiatieto YleisetAlueet) => truthy
        Tyolupa (:Tyolupa yleinenAlueAsiatieto) => truthy

        Kasittelytieto (-> Tyolupa :kasittelytietotieto :Kasittelytieto) => truthy
        Kasittelytieto-kasittelija-nimi (-> Kasittelytieto :kasittelija :henkilotieto :Henkilo :nimi) => truthy

        _ (:luvanTunnisteTiedot Tyolupa) => truthy

        Tyolupa-kayttotarkoitus (:kayttotarkoitus Tyolupa) => truthy
        Tyolupa-Johtoselvitysviite (-> Tyolupa :johtoselvitysviitetieto :Johtoselvitysviite) => truthy

        Sijainti-osoite (-> Tyolupa :sijaintitieto first :Sijainti :osoite) => truthy
        Sijainti-yksilointitieto (-> Sijainti-osoite :yksilointitieto) => truthy
        Sijainti-alkuHetki (-> Sijainti-osoite :alkuHetki) => truthy
        Sijainti-osoitenimi (-> Sijainti-osoite :osoitenimi :teksti) => truthy
        Sijainti-piste (-> Tyolupa :sijaintitieto first :Sijainti :piste :Point :pos) => truthy

        osapuolet-vec (:osapuolitieto Tyolupa) => truthy
        vastuuhenkilot-vec (:vastuuhenkilotieto Tyolupa) => truthy

        ;; maksajan yritystieto-osa
        Maksaja (-> Tyolupa :maksajatieto :Maksaja) => truthy
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
        maksaja-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
        maksaja-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

        alkuPvm (-> Tyolupa :alkuPvm) => truthy
        loppuPvm (-> Tyolupa :loppuPvm) => truthy

        lupaAsianKuvaus (:lupaAsianKuvaus Tyolupa) => truthy
        Sijoituslupaviite (-> Tyolupa :sijoituslupaviitetieto :Sijoituslupaviite) => truthy

        ;; tyomaasta-vastaavan yritystieto-osa
        rooliKoodi-tyonsuorittaja "ty\u00f6nsuorittaja"
        Vastuuhenkilo-yritys (:Osapuoli (first (filter #(= (-> % :Osapuoli :rooliKoodi) rooliKoodi-tyonsuorittaja) osapuolet-vec)))
        Vastuuhenkilo-yritys-Yritys (-> Vastuuhenkilo-yritys :yritystieto :Yritys) => truthy
        Vastuuhenkilo-yritys-Postiosoite (-> Vastuuhenkilo-yritys-Yritys :postiosoitetieto :Postiosoite) => truthy
        ;; tyomaasta-vastaavan henkilotieto-osa
        rooliKoodi-tyomaastavastaava "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6"
        tyomaastavastaava-filter-fn #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-tyomaastavastaava)
        Vastuuhenkilo-henkilo (:Vastuuhenkilo (first (filter tyomaastavastaava-filter-fn vastuuhenkilot-vec)))
        Vastuuhenkilo-henkilo-osoite (-> Vastuuhenkilo-henkilo :osoitetieto :osoite) => truthy

        ;; Testataan muunnosfunktiota myos henkilo-tyyppisella tyomaasta-vastaavalla
        tyomaasta-vastaava-henkilo (get-tyomaasta-vastaava
                                     (tools/unwrapped
                                       (assoc-in (:data tyomaasta-vastaava) [:_selected :value] "henkilo"))) => truthy
        tyomaasta-vastaava-Vastuuhenkilo (-> tyomaasta-vastaava-henkilo :Vastuuhenkilo) => truthy
        tyomaasta-vastaava-Vastuuhenkilo-osoite (-> tyomaasta-vastaava-Vastuuhenkilo :osoitetieto :osoite) => truthy

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
;
;        ;; Testataan muunnosfunktiota yksityisella hakijalla ("henkilo"-tyyppinen hakija)
        _ (get-yritys-and-henkilo
            (tools/unwrapped (assoc-in (:data maksaja) [:_selected :value] "henkilo")) "hakija")
;        hakija-yksityinen-Henkilo (-> maksaja-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
;        hakija-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
;        hakija-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

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

        pinta-ala (:pintaala Tyolupa) => truthy]

      (fact "contains nil" (util/contains-value? canonical nil?) => falsey)

      (fact "Kasittelytieto-muutosHetki" (:muutosHetki Kasittelytieto) => (date/xml-datetime (:modified kaivulupa-application)))
      (fact "Kasittelytieto-hakemuksenTila" (:hakemuksenTila Kasittelytieto) => "vireill\u00e4")
      (fact "Kasittelytieto-asiatunnus" (:asiatunnus Kasittelytieto) => (:id kaivulupa-application))
      (fact "Kasittelytieto-paivaysPvm" (:paivaysPvm Kasittelytieto) => (date/xml-date (:submitted kaivulupa-application)))
      (fact "Kasittelytieto-kasittelija-etunimi" (:etunimi Kasittelytieto-kasittelija-nimi) => (:firstName sonja))
      (fact "Kasittelytieto-kasittelija-sukunimi" (:sukunimi Kasittelytieto-kasittelija-nimi) => (:lastName sonja))

      (fact "Tyolupa-kayttotarkoitus" Tyolupa-kayttotarkoitus => ((keyword (:name operation)) ya-operation-type-to-usage-description))
      (fact "Tyolupa-Johtoselvitysviite-vaadittuKytkin" (:vaadittuKytkin Tyolupa-Johtoselvitysviite) => false)

      ;; Sijainti
      (fact "Sijainti-yksilointitieto" Sijainti-yksilointitieto => (:id kaivulupa-application))
      (fact "Sijainti-alkuHetki" Sijainti-alkuHetki => (partial xml-datetime-is-roughly? (date/xml-datetime (now))))
      (fact "Sijainti-osoitenimi" Sijainti-osoitenimi => (:address kaivulupa-application))
      (fact "Sijainti-piste-xy" Sijainti-piste => (str (-> kaivulupa-application :location first) " " (-> kaivulupa-application :location second)))

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

;      ;; Osapuoli: Hakija
;      (fact "hakija-vastuuhenkilo-rooliKoodi" (:rooliKoodi hakija-Vastuuhenkilo) => rooliKoodi-hankkeen-vastuuhenkilo)
;      (fact "hakija-henkilo-etunimi" (:etunimi hakija-Vastuuhenkilo) => (-> nimi :etunimi :value))
;      (fact "hakija-henkilo-sukunimi" (:sukunimi hakija-Vastuuhenkilo) => (-> nimi :sukunimi :value))
;      (fact "hakija-henkilo-sahkopostiosoite" (:sahkopostiosoite hakija-Vastuuhenkilo) => (-> yhteystiedot :email :value))
;      (fact "hakija-henkilo-puhelinnumero" (:puhelinnumero hakija-Vastuuhenkilo) => (-> yhteystiedot :puhelin :value))
;      (fact "hakija-henkilo-osoite-osoitenimi"
;        (-> hakija-Vastuuhenkilo-osoite :osoitenimi :teksti) => (-> osoite :katu :value))
;      (fact "hakija-henkilo-osoite-postinumero"
;        (:postinumero hakija-Vastuuhenkilo-osoite) => (-> osoite :postinumero :value))
;      (fact "hakija-henkilo-osoite-postitoimipaikannimi"
;        (:postitoimipaikannimi hakija-Vastuuhenkilo-osoite) => (-> osoite :postitoimipaikannimi :value))
;      (fact "hakija-osapuoli-rooliKoodi" (:rooliKoodi hakija-Osapuoli) => rooliKoodi-Hakija)
;      (fact "hakija-yritys-nimi" (:nimi hakija-Yritys) => (-> yritys-nimi-ja-tunnus :yritysnimi :value))
;      (fact "hakija-yritys-liikeJaYhteisotunnus" (:liikeJaYhteisotunnus hakija-Yritys) => (-> yritys-nimi-ja-tunnus :liikeJaYhteisoTunnus :value))
;      (fact "hakija-yritys-osoitenimi" (-> hakija-yritys-Postiosoite :osoitenimi :teksti) => (-> osoite :katu :value))
;      (fact "hakija-yritys-postinumero" (:postinumero hakija-yritys-Postiosoite) => (-> osoite :postinumero :value))
;      (fact "hakija-yritys-postitoimipaikannimi" (:postitoimipaikannimi hakija-yritys-Postiosoite) => (-> osoite :postitoimipaikannimi :value))
;
;      ;; Hakija, yksityinen henkilo -> Tama on testattu jo kohdassa "Maksaja, yksityinen henkilo" (muunnos on taysin sama)

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
      (fact "alkuPvm" alkuPvm => (date/xml-date (-> tyoaika :data :tyoaika-alkaa-ms :value)))
      (fact "loppuPvm" loppuPvm => (date/xml-date (-> tyoaika :data :tyoaika-paattyy-ms :value)))

      ;; Hankkeen kuvaus
      (fact "lupaAsianKuvaus" lupaAsianKuvaus => (-> hankkeen-kuvaus :data :kayttotarkoitus :value))
      (fact "vaadittuKytkin" (:vaadittuKytkin Sijoituslupaviite) => false)
      (fact "Sijoituslupaviite" (:tunniste Sijoituslupaviite) => (-> hankkeen-kuvaus :data :sijoitusLuvanTunniste :value))
      (fact "varattava-pinta-ala" pinta-ala => (-> hankkeen-kuvaus :data :varattava-pinta-ala :value))))


(def kaivulupa-application-with-link-permit-data
  (merge kaivulupa-application {:linkPermitData link-permit-data}))

(ctc/validate-all-documents kaivulupa-application-with-link-permit-data)

(facts* "Kaivulupa canonical model is correct - with link permit data"
  (let [canonical (application-to-canonical kaivulupa-application-with-link-permit-data "fi")
        YleisetAlueet (:YleisetAlueet canonical) => truthy
        yleinenAlueAsiatieto (:yleinenAlueAsiatieto YleisetAlueet) => truthy
        Tyolupa (:Tyolupa yleinenAlueAsiatieto) => truthy
        luvanTunnisteTiedot (:luvanTunnisteTiedot Tyolupa) => truthy
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        lp-muu-tunnus (:MuuTunnus (first muuTunnustieto)) => truthy
        viitelupatunnus (:MuuTunnus (second muuTunnustieto)) => truthy
        Sijoituslupaviite (-> Tyolupa :sijoituslupaviitetieto :Sijoituslupaviite) => truthy]


    (fact "LP-tunnus"
      (:tunnus lp-muu-tunnus) => (:id kaivulupa-application-with-link-permit-data)
      (:sovellus lp-muu-tunnus) => "Lupapiste")

    (fact "Viitelupa"
      (:tunnus viitelupatunnus) => (:id (first link-permit-data))
      (:sovellus viitelupatunnus) => "Viitelupa")

    (fact "Sijoituslupaviite"
      (:tunniste Sijoituslupaviite) => (-> link-permit-data second :id))))


(def katselmus
  (-> (dummy-doc "task-katselmus-ya")
      (assoc :taskname "testikatselmus")
      (assoc-in [:data :katselmuksenLaji :value] "Muu valvontak\u00e4ynti")
      (assoc-in [:data :katselmus :pitaja :value] "Viranomaisen nimi")
      (assoc-in [:data :katselmus :pitoPvm :value] "01.05.1974")
      (assoc-in [:data :katselmus :huomautukset :kuvaus :value] "huomautus - kuvaus")
      (assoc-in [:data :katselmus :huomautukset :toteaja :value] "huomautus - viranomaisen nimi")
      (assoc-in [:data :katselmus :huomautukset :maaraAika :value] "02.06.1974")
      (assoc-in [:data :katselmus :lasnaolijat :value] "paikallaolijat")
      (assoc-in [:data :katselmus :poikkeamat :value] "jotain poikkeamia oli")
      ))

(def kaivulupa-application-with-review (assoc kaivulupa-application :tasks [katselmus]))

(ctc/validate-all-documents kaivulupa-application-with-review :tasks)

(facts "Katselmus canonical"
  (let [canonical (katselmus-canonical kaivulupa-application-with-review katselmus "fi")
        review    (get-in canonical [:YleisetAlueet :yleinenAlueAsiatieto :Tyolupa :katselmustieto :Katselmus])
        {:keys [pitoPvm pitaja
                katselmuksenLaji
                vaadittuLupaehtonaKytkin
                huomautustieto
                katselmuspoytakirja
                tarkastuksenTaiKatselmuksenNimi
                lasnaolijat poikkeamat]}           review]

    review => map?

    (fact "pitaja" pitaja => "Viranomaisen nimi")
    (fact "pitoPvm" pitoPvm => (date/xml-date "1974-05-01"))
    (fact "katselmuksenLaji" katselmuksenLaji => "Muu valvontak\u00e4ynti")
    (fact "vaadittuLupaehtonaKytkin" vaadittuLupaehtonaKytkin => true)
    (fact "huomautustieto" huomautustieto
          => {:Huomautus {:kuvaus "huomautus - kuvaus"
                          :maaraAika (date/xml-date "1974-06-02")
                          :toteamisHetki (date/xml-date "1974-05-02")
                          :toteaja "huomautus - viranomaisen nimi"}})
    (fact "katselmuspoytakirja" katselmuspoytakirja => nil)
    (fact "tarkastuksenTaiKatselmuksenNimi" tarkastuksenTaiKatselmuksenNimi => "testikatselmus")
    (fact "lasnaolijat" lasnaolijat => "paikallaolijat")
    (fact "poikkeamat" poikkeamat => "jotain poikkeamia oli")))


;;
;; Verdict canonical tests
;;

(def verdict
  {:category :ya,
   :id "5d919ca81bca460f42d495ca",
   :state {:_value "published"},
   :schema-version 1,
   :modified 1569823935901,
   :archive {:verdict-date 1569747600000,
             :anto 1570006800000,
             :verdict-giver "5d919ca81bca460f42d495c8 Esa Viranomainen"},
   :published {:attachment-id "531"
               :published 1569824187814,
               :tags "{:body ([:div.section {:class ()} [:div.row {:class [:bold :pad-after]} [:div.cell {:class (\"cell--30\" \"bold\")} \"Lupatunnus\"] [:div.cell {} \"LP-092-2019-90003\"]]] [:div.section {:class ()} [:div.row {:class []} [:div.cell {:class (\"cell--30\")} \"Kiinteist\u00f6tunnus\"] [:div.cell {} \"92-95-167-3\"]]] [:div.section {:class ()} [:div.row {:class []} [:div.cell {:class (\"cell--30\")} \"Hankkeen sijainti\"] [:div.cell {} \"L\u00e4nsim\u00e4entie 30\"]]] [:div.section {:class (:border-bottom)} [:div.row {:class [:pad-before]} [:div.cell {:class (\"cell--30\")} \"Hankkeeseen ryhtyv\u00e4\"] [:div.cell {} \"Esa Viranomainen\\nKatuosoite 1 a 1, 33456 Vantaa\"]]] [:div.section {:class ()} [:div.row {:class [:bold]} [:div.cell {:class (\"cell--30\" \"bold\")} \"Toimenpide\"] [:div.cell {} \"Alueen k\u00e4ytt\u00f6tarkoitus\"]]] [:div.section {:class ()} [:div.row {:class [:pad-before]} [:div.cell {:class (\"cell--30\")} \"L\u00e4hiseudun asukkaiden tiedottaminen\"] [:div.cell {} [:div.markup ([:span {} \"L\u00e4hiseudun asukkaiden tiedottaminen.\" [:br {}]])]]]] [:div.section {:class ()} [:div.row {:class [:bold :pad-before]} [:div.cell {:class (\"cell--30\" \"bold\")} \"Liitteet\"] [:div.cell [:div.section ([:div.row {:class nil} ([:div.cell {:class (\"nowrap\")} \"Muu liite\"] [:div.cell {:class (\"right\" \"nowrap\")} \"1 kpl\"] [:div.cell {:class (\"cell--100\")} \"\"])])]]]] [:div.section {:class ()} [:div.row {:class [:bold :pad-before]} [:div.cell {:class (\"cell--30\" \"bold\")} \"P\u00e4|u00e4t\u00f6s\"] [:div.cell {} \"Hyv\u00e4ksytty\"]]] [:div.section {:class ()} [:div.row {:class [:pad-before]} [:div.cell {:class (\"cell--30\")} \"\"] [:div.cell {} [:div.markup ([:span {} \"P\u00e4\u00e4t\u00f6ksen teksti.\" [:br {}]])]]]] [:div.section {:class ()} [:div.row {:class [:pad-before]} [:div.cell {:class (\"cell--30\")} \"Vaaditut katselmukset\"] [:div.cell [:div.section ([:div.row {:class nil} ([:div.cell {} \"Aloituskatselmus suomeksi\"])] [:div.row {:class nil} ([:div.cell {} \"Loppukatselmus suomeksi\"])] [:div.row {:class nil} ([:div.cell {} \"Muu valvontak\u00e4ynti suomeksi\"])])]]]] [:div.section {:class ()} [:div.row {:class []} [:div.cell {:class (\"cell--30\")} \"\"] [:div.cell {} [:div.markup ([:span {} \"Lis\u00e4ohje katselmuksille.\" [:br {}]])]]]] [:div.section {:class ()} [:div.row {:class [:pad-before]} [:div.cell {:class (\"cell--30\")} \"Vaadittu erityissuunnitelma\"] [:div.cell {} \"Suunnitelma suomeksi\"]]] [:div.section {:class ()} [:div.row {:class [:pad-before]} [:div.cell {:class (\"cell--30\")} \"Lupaehto\"] [:div.cell {} [:div.markup (([:span {} \"Lupaehto tai -m\u00e4\u00e4r\u00e4ys.\" [:br {}]]))]]]] [:div.section {:class ()} [:div.row {:class [:pad-before]} [:div.cell {:class (\"cell--30\")} \"\"] [:div.cell {} \"29.9.2019\"]]] [:div.section {:class ()} [:div.row {:class [:pad-before]} [:div.cell {:class (\"cell--30\")} \"K\u00e4sittelij\u00e4\"] [:div.cell {} \"Teht\u00e4v\u00e4nimike Esa Viranomainen\"]]] [:div.section {:class ()} [:div.row {:class []} [:div.cell {:class (\"cell--30\")} \"P\u00e4\u00e4tt\u00e4j\u00e4\"] [:div.cell {} \"Nimike P\u00e4\u00e4tt\u00e4j\u00e4\"]]] [:div.section {:class ()} [:div.row {:class [:pad-after]} [:div.cell {:class (\"cell--30\")} \"\"] [:div.cell {} \"Vantaa YA\"]]] [:div.section {:class ()} [:div.row {:class []} [:div.cell {:class (\"cell--30\")} \"P\u00e4\u00e4t\u00f6ksen antop\u00e4iv\u00e4\"] [:div.cell {} \"2.10.2019\"]]] [:div.section {:class ()} [:div.row {:class []} [:div.cell {:class (\"cell--30\")} \"P\u00e4\u00e4t\u00f6ksen voimassaolo\"] [:div.cell {} \"Lupa-aika alkaa 25.9.2019 ja p\u00e4\u00e4ttyy 31.10.2019.\"]]] [:div.section {:class (:page-break)} [:div.row {:class [:bold]} [:div.cell {:class (\"cell--30\" \"bold\")} \"Muutoksenhakuaika\"] [:div.cell {} [:div.markup ([:span {} \"Hakemuksen oheen tuleva muutoksenhakuohje.\" [:br {}]])]]]]), :header [:div.header [:div.section.header [:div.row.pad-after [:div.cell.cell--40 \"Vantaa YA\" nil] [:div.cell.cell--20.center [:div \"P\u00e4\u00e4t\u00f6s\"]] [:div.cell.cell--40.right [:div.permit \"Katulupa\"]]] [:div.row [:div.cell.cell--40 \"§7\"] [:div.cell.cell--20.center [:div \"29.9.2019\"]] [:div.cell.cell--40.right.page-number \"Sivu\" \" \" [:span#page-number \"\"]]]]], :footer [:div.footer [:div.section [:div.row.pad-after.pad-before [:cell.cell--100 {:dangerouslySetInnerHTML {:__html \"&nbsp;\"}}]]]]}"},
   :template {:giver "viranhaltija"
              :inclusions '("verdict-flag""application-id""inform-others""appeal""conditions-title""address""conditions.remove-condition""proposal-text""bulletin-op-description""end-date""verdict-date""verdict-text""anto""giver""attachments""operation""plans""verdict-code""property-title""add-property-id""verdict-type""giver-title""conditions.condition""plans-included""handler-title""language""propertyIds.property-id""propertyIds.remove-property-id""reviews""proposal-text-ref""start-date""handler""reviews-included""review-info""verdict-text-ref""upload""automatic-verdict-dates""statements""add-condition""bulletin-desc-as-operation":verdict-section)},
   :references {:verdict-code ["evatty"
                               "myonnetty-aloitusoikeudella"
                               "myonnetty"
                               "hyvaksytty"
                               "annettu-lausunto"],
                :organization-name "Vantaa YA",
                :date-deltas {:julkipano {:delta 1, :unit "days"},
                              :anto {:delta 2, :unit "days"},
                              :muutoksenhaku {:delta 3, :unit "days"},
                              :lainvoimainen {:delta 4, :unit "days"},
                              :aloitettava {:delta 5, :unit "years"},
                              :voimassa {:delta 0, :unit "days"}},
                :plans [{:fi "Suunnitelma suomeksi",
                         :sv "Suunnitelma p\u00e5 svenska",
                         :en "Suunnitelma in english",
                         :id "5d919ca81bca460f42d495c4"}],
                :reviews [{:fi "Muu valvontak\u00e4ynti suomeksi",
                           :sv "Muu valvontak\u00e4ynti p\u00e5 svenska",
                           :en "Muu valvontak\u00e4ynti in english",
                           :type "valvonta",
                           :id "5d919ca81bca460f42d495c7"}
                          {:fi "Loppukatselmus suomeksi",
                           :sv "Loppukatselmus  p\u00e5 svenska",
                           :en "Loppukatselmus in english",
                           :type "loppukatselmus",
                           :id "5d919ca81bca460f42d495c6"}
                          {:fi "Aloituskatselmus suomeksi",
                           :sv "Aloituskatselmus  p\u00e5 svenska",
                           :en "Aloituskatselmus in english",
                           :type "aloituskatselmus",
                           :id "5d919ca81bca460f42d495c5"}],
                :handler-titles [{:fi "Teht\u00e4v\u00e4nimike",
                                  :sv "Teht\u00e4v\u00e4nimike svenska",
                                  :en "Teht\u00e4v\u00e4nimike english",
                                  :id "5d919ca81bca460f42d495c8"}]},
   :data {:inform-others "L\u00e4hiseudun asukkaiden tiedottaminen.",
          :appeal "Hakemuksen oheen tuleva muutoksenhakuohje.",
          :address "L\u00e4nsim\u00e4entie 30",
          :selected-attachments ["5d919c1f1bca460f42d495bb"],
          :bulletin-op-description "Toimenpideteksti julkipanoon.",
          :start-date 1569358800000,
          :end-date 1572472800000,
          :anto 1570006800000,
          :handler-titles ["5d919ca81bca460f42d495c8"],
          :giver "P\u00e4\u00e4tt\u00e4j\u00e4",
          :attachments '({:type-group "muut", :type-id "muu", :amount 1}),
          :operation "Alueen k\u00e4ytt\u00f6tarkoitus",
          :plans ["5d919ca81bca460f42d495c4"],
          :verdict-date 1569747600000,
          :verdict-section "7",
          :verdict-text "P\u00e4\u00e4t\u00f6ksen teksti.",
          :verdict-code "hyvaksytty",
          :verdict-type "katulupa",
          :giver-title "Nimike",
          :conditions {:5d919ca81bca460f42d495c3 {:condition "Lupaehto tai -m\u00e4\u00e4r\u00e4ys."}},
          :plans-included true,
          :handler-title "5d919ca81bca460f42d495c8",
          :language "fi",
          :handler-titles-included true,
          :propertyIds {:5d919ca81bca460f42d495c9 {:property-id "92-95-167-3"}},
          :reviews-included true,
          :review-info "Lis\u00e4ohje katselmuksille.",
          :reviews ["5d919ca81bca460f42d495c7"
                    "5d919ca81bca460f42d495c6"
                    "5d919ca81bca460f42d495c5"],
          :handler "Esa Viranomainen",
          :automatic-verdict-dates false,
          :statements '()}})

(def pate-verdict
  {:category "ya",
   :id "5d919ca81bca460f42d495ca",
   :state {:_value "draft"}
   :schema-version 1,
   :published {:attachment-id "531"},
   :modified 1569823935901,
   :template {:giver "viranhaltija"
              :inclusions ["verdict-flag""application-id""inform-others""appeal""conditions-title""address""conditions.remove-condition""proposal-text""bulletin-op-description""end-date""verdict-date""verdict-text""anto""giver""attachments""operation""plans""verdict-code""property-title""add-property-id""verdict-type""giver-title""conditions.condition""plans-included""handler-title""language""propertyIds.property-id""propertyIds.remove-property-id""reviews""proposal-text-ref""start-date""handler""reviews-included""review-info""verdict-text-ref""upload""automatic-verdict-dates""statements""add-condition""bulletin-desc-as-operation"]},
   :data {:inform-others {:_value "L\u00e4hiseudun asukkaiden tiedottaminen."},
          :appeal {:_value "Hakemuksen oheen tuleva muutoksenhakuohje."},
          :address {:_value "L\u00e4nsim\u00e4entie 30"},
          :bulletin-op-description {:_value "Toimenpideteksti julkipanoon."},
          :end-date {:_value 1572472800000},
          :no-end-date {:_value false}
          :verdict-date {:_value 1569747600000},
          :handler-titles {:_value ["5d919ca81bca460f42d495c8"]},
          :verdict-text {:_value "P\u00e4\u00e4t\u00f6ksen teksti."},
          :anto {:_value 1570006800000},
          :giver {:_value "P\u00e4\u00e4tt\u00e4j\u00e4"},
          :attachments {:_value ["5d919c1f1bca460f42d495bb"]},
          :operation {:_value "Alueen k\u00e4ytt\u00f6tarkoitus"},
          :plans {:_value ["5d919ca81bca460f42d495c4"]},
          :verdict-code {:_value "hyvaksytty"},
          :verdict-type {:_value "katulupa"},
          :giver-title {:_value "Nimike"},
          :conditions {:5d919ca81bca460f42d495c3 {:condition {:_value "Lupaehto tai -m\u00e4\u00e4r\u00e4ys."}}},
          :plans-included {:_value true},
          :handler-title {:_value "5d919ca81bca460f42d495c8"},
          :language {:_value "fi"},
          :handler-titles-included {:_value true},
          :propertyIds {:5d919ca81bca460f42d495c9 {:property-id {:_value "92-95-167-3"}}},
          :reviews {:_value ["5d919ca81bca460f42d495c7"
                             "5d919ca81bca460f42d495c6"
                             "5d919ca81bca460f42d495c5"]},
          :start-date {:_value 1569358800000},
          :handler {:_value "Esa Viranomainen"},
          :reviews-included {:_value true},
          :review-info {:_value "Lis\u00e4ohje katselmuksille."},
          :automatic-verdict-dates {:_value false}},
   :references {:verdict-code ["evatty"
                               "myonnetty-aloitusoikeudella"
                               "myonnetty"
                               "hyvaksytty"
                               "annettu-lausunto"],
                :organization-name "Vantaa YA",
                :date-deltas {:julkipano {:delta 1, :unit "days"},
                              :anto {:delta 2, :unit "days"},
                              :muutoksenhaku {:delta 3, :unit "days"},
                              :lainvoimainen {:delta 4, :unit "days"},
                              :aloitettava {:delta 5, :unit "years"},
                              :voimassa {:delta 0, :unit "days"}},
                :plans [{:fi "Suunnitelma suomeksi",
                         :sv "Suunnitelma p\u00e5 svenska",
                         :en "Suunnitelma in english",
                         :id "5d919ca81bca460f42d495c4"}],
                :reviews [{:fi "Muu valvontak\u00e4ynti suomeksi",
                           :sv "Muu valvontak\u00e4ynti p\u00e5 svenska",
                           :en "Muu valvontak\u00e4ynti in english",
                           :type "valvonta",
                           :id "5d919ca81bca460f42d495c7"}
                          {:fi "Loppukatselmus suomeksi",
                           :sv "Loppukatselmus  p\u00e5 svenska",
                           :en "Loppukatselmus in english",
                           :type "loppukatselmus",
                           :id "5d919ca81bca460f42d495c6"}
                          {:fi "Aloituskatselmus suomeksi",
                           :sv "Aloituskatselmus  p\u00e5 svenska",
                           :en "Aloituskatselmus in english",
                           :type "aloituskatselmus",
                           :id "5d919ca81bca460f42d495c5"}],
                :handler-titles [{:fi "Teht\u00e4v\u00e4nimike",
                                  :sv "Teht\u00e4v\u00e4nimike svenska",
                                  :en "Teht\u00e4v\u00e4nimike english",
                                  :id "5d919ca81bca460f42d495c8"}]}})

(def verdict-tasks
  [{:taskname "Aloituskatselmus suomeksi",
    :id "5d919dbb1bca460f42d495cd",
    :source {:type "verdict", :id "5d919ca81bca460f42d495ca"},
    :closed nil,
    :created 1569824187814,
    :state :requires_user_action,
    :assignee {},
    :duedate nil,
    :schema-info {:name "task-katselmus-ya",
                  :type :task,
                  :subtype :review,
                  :order 1,
                  :section-help "authority-fills",
                  :i18name "task-katselmus",
                  :i18nprefix "task-katselmus.katselmuksenLaji",
                  :user-authz-roles #{},
                  :version 1},
    :data {:katselmuksenLaji {:value "Aloituskatselmus"},
           :vaadittuLupaehtona {:value true},
           :katselmus {:pitoPvm {:value nil},
                       :pitaja {:value nil},
                       :huomautukset
                       {:kuvaus {:value ""},
                        :maaraAika {:value nil},
                        :toteaja {:value ""},
                        :toteamisHetki {:value nil}},
                       :lasnaolijat {:value ""},
                       :poikkeamat {:value ""}},
           :muuTunnus {:value ""},
           :muuTunnusSovellus {:value ""}}}

   {:taskname "Loppukatselmus suomeksi",
    :id "5d919dbb1bca460f42d495cc",
    :source {:type "verdict", :id "5d919ca81bca460f42d495ca"},
    :closed nil,
    :created 1569824187814,
    :state :requires_user_action,
    :assignee {},
    :duedate nil,
    :schema-info {:name "task-katselmus-ya",
                  :type :task,
                  :subtype :review,
                  :order 1,
                  :section-help "authority-fills",
                  :i18name "task-katselmus",
                  :i18nprefix "task-katselmus.katselmuksenLaji",
                  :user-authz-roles #{},
                  :version 1},
    :data {:katselmuksenLaji {:value "Loppukatselmus"},
           :vaadittuLupaehtona {:value true},
           :katselmus {:pitoPvm {:value nil},
                       :pitaja {:value nil},
                       :huomautukset
                       {:kuvaus {:value ""},
                        :maaraAika {:value nil},
                        :toteaja {:value ""},
                        :toteamisHetki {:value nil}},
                       :lasnaolijat {:value ""},
                       :poikkeamat {:value ""}},
           :muuTunnus {:value ""},
           :muuTunnusSovellus {:value ""}}}

   {:taskname "Muu valvontak\u00e4ynti suomeksi",
    :id "5d919dbb1bca460f42d495cb",
    :source {:type "verdict", :id "5d919ca81bca460f42d495ca"},
    :closed nil,
    :created 1569824187814,
    :state :requires_user_action,
    :assignee {},
    :duedate nil,
    :schema-info {:name "task-katselmus-ya",
                  :type :task,
                  :subtype :review,
                  :order 1,
                  :section-help "authority-fills",
                  :i18name "task-katselmus",
                  :i18nprefix "task-katselmus.katselmuksenLaji",
                  :user-authz-roles #{},
                  :version 1},
    :data {:katselmuksenLaji {:value "Muu valvontak\u00e4ynti"},
           :vaadittuLupaehtona {:value true},
           :katselmus {:pitoPvm {:value nil},
                       :pitaja {:value nil},
                       :huomautukset
                       {:kuvaus {:value ""},
                        :maaraAika {:value nil},
                        :toteaja {:value ""},
                        :toteamisHetki {:value nil}},
                       :lasnaolijat {:value ""},
                       :poikkeamat {:value ""}},
           :muuTunnus {:value ""},
           :muuTunnusSovellus {:value ""}}}

   {:taskname "Suunnitelma suomeksi",
    :id "5d919dbb1bca460f42d495ce",
    :source {:type "verdict", :id "5d919ca81bca460f42d495ca"},
    :closed nil,
    :created 1569824187814,
    :state :requires_user_action,
    :assignee {},
    :duedate nil,
    :schema-info {:name "task-lupamaarays", :type :task, :order 20, :version 1},
    :data {:maarays {:value ""},
           :kuvaus {:value ""},
           :vaaditutErityissuunnitelmat {:value ""}}}

   {:taskname "Lupaehto tai -m\u00e4\u00e4r\u00e4ys.",
    :id "5d919dbb1bca460f42d495cf",
    :source {:type "verdict", :id "5d919ca81bca460f42d495ca"},
    :closed nil,
    :created 1569824187814,
    :state :requires_user_action,
    :assignee {},
    :duedate nil,
    :schema-info {:name "task-lupamaarays", :type :task, :order 20, :version 1},
    :data {:maarays {:value ""},
           :kuvaus {:value ""},
           :vaaditutErityissuunnitelmat {:value ""}}}])

(def kaivulupa-application-with-review-and-verdict
  (assoc kaivulupa-application-with-review
    :state :verdictGiven
    :verdicts '()
    :pate-verdicts [pate-verdict]
    :tasks verdict-tasks))

(ctc/validate-all-documents kaivulupa-application-with-review-and-verdict)

(facts "Verdict canonical"
       (let [canonical (verdict-canonical "fi" verdict kaivulupa-application-with-review-and-verdict)
             {:keys [paivamaarat
                     poytakirja
                     paatosdokumentinPvm] :as verdict} (:Paatos canonical)]

         (fact "sanity check " verdict => map?)

         (fact "paivamaarat: antoPvm" (:antoPvm paivamaarat)
               => (date/xml-date "2019-10-02"))

         (fact "poytakirja: paatospvm" (:paatospvm poytakirja)
               => (date/xml-date "2019-09-29"))

         (fact "paatosdokumentinPvm"
           paatosdokumentinPvm => (date/xml-date "2019-09-29"))))
