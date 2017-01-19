(ns lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.test-util :refer [xml-datetime-is-roughly? dummy-doc]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.tasks]
            [sade.util :as util]
            [sade.core :refer :all]))


(def- operation {:id "51cc1cab23e74941fee4f495",
                          :created 1372331179008,
                          :name "ya-katulupa-vesi-ja-viemarityot"})

(def- hankkeen-kuvaus {:id "52380c6894a74fc25bb4ba4a",
                                :created 1379404904514,
                                :schema-info {:name "yleiset-alueet-hankkeen-kuvaus-kaivulupa",
                                              :removable false,
                                              :repeating false,
                                              :version 1,
                                              :type "group",
                                              :order 60},
                                :data {:kayttotarkoitus {:value "Hankkeen kuvaus."},
                                       :sijoitusLuvanTunniste {:value "LP-753-2013-00002"}
                                       :varattava-pinta-ala {:value "333"}}})

(def- tyomaasta-vastaava-kaivulupa (assoc-in tyomaasta-vastaava [:schema-info :op] operation))

(def- documents [hakija
                 tyomaasta-vastaava-kaivulupa
                 maksaja
                 hankkeen-kuvaus
                 tyoaika])

(def kaivulupa-application {:id "LP-753-2013-00001"
                            :permitType "YA"
                            :created 1372331179008
                            :opened 1372331643985
                            :modified 1372342070624
                            :submitted 1379405092649
                            :auth [{:lastName "Panaani",
                                    :firstName "Pena",
                                    :username "pena",
                                    :type "owner",
                                    :role "owner",
                                    :id "777777777777777777000020"}]
                            :authority sonja
                            :state "submitted"
                            :title "Latokuja 1"
                            :address "Latokuja 1"
                            :location location
                            :attachments []
                            :primaryOperation operation
                            :secondaryOperations []
                            :propertyId "75341600550007"
                            :documents documents
                            :municipality municipality
                            :statements statements
                            :drawings ctc/drawings})

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

        luvanTunnisteTiedot (:luvanTunnisteTiedot Tyolupa) => truthy

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
        hakija-yksityinen (get-yritys-and-henkilo
                            (tools/unwrapped
                              (assoc-in (:data maksaja) [:_selected :value] "henkilo")) "hakija")
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
                              (assoc-in (:data hakija) [:_selected :value] "henkilo")))
        hakija-yksityinen-Henkilo (-> hakija-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
        hakija-yksityinen-nimi (:nimi hakija-yksityinen-Henkilo) => truthy
        hakija-yksityinen-osoite (:osoite hakija-yksityinen-Henkilo) => truthy

        pinta-ala (:pintaala Tyolupa) => truthy
        ]

      (fact "contains nil" (util/contains-value? canonical nil?) => falsey)

      (fact "Kasittelytieto-muutosHetki" (:muutosHetki Kasittelytieto) => (util/to-xml-datetime (:modified kaivulupa-application)))
      (fact "Kasittelytieto-hakemuksenTila" (:hakemuksenTila Kasittelytieto) => "vireill\u00e4")
      (fact "Kasittelytieto-asiatunnus" (:asiatunnus Kasittelytieto) => (:id kaivulupa-application))
      (fact "Kasittelytieto-paivaysPvm" (:paivaysPvm Kasittelytieto) => (util/to-xml-date (:submitted kaivulupa-application)))
      (fact "Kasittelytieto-kasittelija-etunimi" (:etunimi Kasittelytieto-kasittelija-nimi) => (:firstName sonja))
      (fact "Kasittelytieto-kasittelija-sukunimi" (:sukunimi Kasittelytieto-kasittelija-nimi) => (:lastName sonja))

      (fact "Tyolupa-kayttotarkoitus" Tyolupa-kayttotarkoitus => ((keyword (:name operation)) ya-operation-type-to-usage-description))
      (fact "Tyolupa-Johtoselvitysviite-vaadittuKytkin" (:vaadittuKytkin Tyolupa-Johtoselvitysviite) => false)

      ;; Sijainti
      (fact "Sijainti-yksilointitieto" Sijainti-yksilointitieto => (:id kaivulupa-application))
      (fact "Sijainti-alkuHetki" Sijainti-alkuHetki => (partial xml-datetime-is-roughly? (util/to-xml-datetime (now))))
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
      (fact "alkuPvm" alkuPvm => (util/to-xml-date (-> tyoaika :data :tyoaika-alkaa-ms :value)))
      (fact "loppuPvm" loppuPvm => (util/to-xml-date (-> tyoaika :data :tyoaika-paattyy-ms :value)))

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
  (let [canonical (katselmus-canonical kaivulupa-application-with-review katselmus "fi" {})
        review    (get-in canonical [:YleisetAlueet :yleinenAlueAsiatieto :Tyolupa :katselmustieto :Katselmus])
        {:keys [pitoPvm pitaja
                katselmuksenLaji
                vaadittuLupaehtonaKytkin
                huomautustieto
                katselmuspoytakirja
                tarkastuksenTaiKatselmuksenNimi
                lasnaolijat poikkeamat]}           review]

    review => map?

    ;(println review)

    (fact "pitaja" pitaja => "Viranomaisen nimi")
    (fact "pitoPvm" pitoPvm => "1974-05-01")
    (fact "katselmuksenLaji" katselmuksenLaji => "Muu valvontak\u00e4ynti")
    (fact "vaadittuLupaehtonaKytkin" vaadittuLupaehtonaKytkin => true)
    (fact "huomautustieto" huomautustieto  => {:Huomautus {:kuvaus "huomautus - kuvaus"
                                                           :maaraAika "1974-06-02"
                                                           :toteamisHetki "1974-05-02"
                                                           :toteaja "huomautus - viranomaisen nimi"}})
    (fact "katselmuspoytakirja" katselmuspoytakirja => nil)
    (fact "tarkastuksenTaiKatselmuksenNimi" tarkastuksenTaiKatselmuksenNimi => "testikatselmus")
    (fact "lasnaolijat" lasnaolijat => "paikallaolijat")
    (fact "poikkeamat" poikkeamat => "jotain poikkeamia oli")
    ))
