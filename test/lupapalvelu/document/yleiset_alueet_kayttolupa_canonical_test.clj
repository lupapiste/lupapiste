(ns lupapalvelu.document.yleiset-alueet-kayttolupa-canonical-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.test-util :refer [xml-datetime-is-roughly?]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical]]
            [lupapalvelu.document.tools :as tools]
            [sade.util :as util]
            [sade.core :refer :all]))

(def- operation {:id "52380c6894a74fc25bb4ba46",
                 :created 1379404904514,
                 :name "ya-kayttolupa-terassit"})

(def- hankkeen-kuvaus {:id "52380c6894a74fc25bb4ba4a"
                       :created 1379404904514
                       :schema-info {:name "yleiset-alueet-hankkeen-kuvaus-kayttolupa"
                                     :removable false
                                     :repeating false
                                     :version 1
                                     :type "group"
                                     :order 60}
                       :data {:kayttotarkoitus {:value "Hankkeen kuvaus."}
;                                       :sijoitusLuvanTunniste {:value "LP-753-2013-00001"}
                              :varattava-pinta-ala {:value "333"}}})

(def- tyoaika-kayttolupa (assoc-in tyoaika [:schema-info :op] operation))

(def- documents [hakija
                 tyoaika-kayttolupa
                 maksaja
                 hankkeen-kuvaus])

(def kayttolupa-application {:schema-version 1,
                             :id "LP-753-2013-00002",
                             :created 1379404904514,
                             :opened 1379404981309,
                             :modified 1379405054747,
                             :submitted 1379405092649,
                             :auth [{:lastName "Panaani",
                                     :firstName "Pena",
                                     :username "pena",
                                     :type "owner",
                                     :role "owner",
                                     :id "777777777777777777000020"}]
                             :authority sonja,
                             :permitType "YA",
                             :organization "753-YA",
                             :infoRequest false,
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
                             :statements ctc/statements
                             :drawings ctc/drawings})

(ctc/validate-all-documents kayttolupa-application)

(testable-privates lupapalvelu.document.yleiset-alueet-canonical get-yritys-and-henkilo get-hakija)

(facts* "Kayttolupa canonical model is correct"
  (let [canonical (application-to-canonical kayttolupa-application "fi")
        YleisetAlueet (:YleisetAlueet canonical) => truthy
        yleinenAlueAsiatieto (:yleinenAlueAsiatieto YleisetAlueet) => truthy
        Kayttolupa (:Kayttolupa yleinenAlueAsiatieto) => truthy

        Kasittelytieto (-> Kayttolupa :kasittelytietotieto :Kasittelytieto) => truthy
        Kasittelytieto-kasittelija-nimi (-> Kasittelytieto :kasittelija :henkilotieto :Henkilo :nimi) => truthy

        muu-tunnustieto (get-in Kayttolupa [:luvanTunnisteTiedot :LupaTunnus :muuTunnustieto]) => seq

        Kayttolupa-kayttotarkoitus (:kayttotarkoitus Kayttolupa) => truthy

        Sijainti-osoite (-> Kayttolupa :sijaintitieto first :Sijainti :osoite) => truthy
        Sijainti-yksilointitieto (-> Sijainti-osoite :yksilointitieto) => truthy
        Sijainti-alkuHetki (-> Sijainti-osoite :alkuHetki) => truthy
        Sijainti-osoitenimi (-> Sijainti-osoite :osoitenimi :teksti) => truthy
        Sijainti-piste (-> Kayttolupa :sijaintitieto first :Sijainti :piste :Point :pos) => truthy
        PisteSijanti   (-> Kayttolupa :sijaintitieto second :Sijainti) => truthy
        LineString1    (-> Kayttolupa :sijaintitieto (nth 2) :Sijainti) => truthy
        LineString2    (-> Kayttolupa :sijaintitieto (nth 3) :Sijainti) => truthy
        Alue           (-> Kayttolupa :sijaintitieto (nth 4) :Sijainti) => truthy

        osapuolet-vec (-> Kayttolupa :osapuolitieto) => truthy
        vastuuhenkilot-vec (-> Kayttolupa :vastuuhenkilotieto) => truthy

        ;; maksajan yritystieto-osa
        Maksaja (-> Kayttolupa :maksajatieto :Maksaja) => truthy
        maksaja-Yritys (-> Maksaja :yritystieto :Yritys) => truthy
        maksaja-Yritys-postiosoite (-> maksaja-Yritys :postiosoite) => truthy
        ;; maksajan henkilotieto-osa
        rooliKoodi-maksajan-vastuuhenkilo "maksajan vastuuhenkil\u00f6"
        maksaja-filter-fn #(= (-> % :Vastuuhenkilo :rooliKoodi) rooliKoodi-maksajan-vastuuhenkilo)
        maksaja-Vastuuhenkilo (:Vastuuhenkilo (first (filter maksaja-filter-fn vastuuhenkilot-vec)))
        maksaja-Vastuuhenkilo-osoite (-> maksaja-Vastuuhenkilo :osoitetieto :osoite) => truthy

        ;; Testataan muunnosfunktiota myos yksityisella ("henkilo"-tyyppisella) maksajalla
        maksaja-yksityinen (get-yritys-and-henkilo
                             (tools/unwrapped
                               (assoc-in (:data maksaja) [:_selected :value] "henkilo")) "maksaja")
        maksaja-yksityinen-Henkilo (-> maksaja-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
        maksaja-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
        maksaja-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

        alkuPvm (-> Kayttolupa :alkuPvm) => truthy
        loppuPvm (-> Kayttolupa :loppuPvm) => truthy

        lupaAsianKuvaus (:lupaAsianKuvaus Kayttolupa) => truthy
        Sijoituslupaviite (-> Kayttolupa :sijoituslupaviitetieto :Sijoituslupaviite) => falsey

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
                              (assoc-in (:data hakija) [:_selected :value] "henkilo")))
        hakija-yksityinen-Henkilo (-> hakija-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
        hakija-yksityinen-nimi (:nimi hakija-yksityinen-Henkilo) => truthy
        hakija-yksityinen-osoite (:osoite hakija-yksityinen-Henkilo) => truthy

        pinta-ala (:pintaala Kayttolupa) => truthy]

    (fact "contains nil" (util/contains-value? canonical nil?) => falsey)

    (fact "lupatunnus"
      (count muu-tunnustieto) => 1
      (-> muu-tunnustieto first :MuuTunnus :tunnus) => (:id kayttolupa-application)
      (-> muu-tunnustieto first :MuuTunnus :sovellus) => "Lupapiste")

    (fact "Kasittelytieto-muutosHetki" (:muutosHetki Kasittelytieto) => (util/to-xml-datetime (:modified kayttolupa-application)))
    (fact "Kasittelytieto-hakemuksenTila" (:hakemuksenTila Kasittelytieto) => "vireill\u00e4")
    (fact "Kasittelytieto-asiatunnus" (:asiatunnus Kasittelytieto) => (:id kayttolupa-application))
    (fact "Kasittelytieto-paivaysPvm" (:paivaysPvm Kasittelytieto) => (util/to-xml-date (:opened kayttolupa-application)))
    (fact "Kasittelytieto-kasittelija-etunimi" (:etunimi Kasittelytieto-kasittelija-nimi) => (:firstName sonja))
    (fact "Kasittelytieto-kasittelija-sukunimi" (:sukunimi Kasittelytieto-kasittelija-nimi) => (:lastName sonja))

    (fact "Kayttolupa-kayttotarkoitus" Kayttolupa-kayttotarkoitus => ((keyword (:name operation)) ya-operation-type-to-usage-description))

    ;; Sijainti
    (fact "Sijainti-yksilointitieto" Sijainti-yksilointitieto => (:id kayttolupa-application))
    (fact "Sijainti-alkuHetki" Sijainti-alkuHetki => (partial xml-datetime-is-roughly? (util/to-xml-datetime (now))))
    (fact "Sijainti-osoitenimi" Sijainti-osoitenimi => (:address kayttolupa-application))
    (fact "Sijainti-piste-xy" Sijainti-piste => (str (-> kayttolupa-application :location first) " " (-> kayttolupa-application :location second)))
    (fact "PisteSijanti" PisteSijanti => {:piste {:Point {:pos "530851.15649413 6972373.1564941"}}
                                          :nimi "Piste"
                                          :kuvaus "Piste jutska"
                                          :korkeusTaiSyvyys "345"})
    (fact "LineString 1" LineString1 => {:viiva {:LineString {:pos '("530856.65649413 6972312.1564941"
                                                                     "530906.40649413 6972355.6564941"
                                                                     "530895.65649413 6972366.9064941"
                                                                     "530851.15649413 6972325.9064941"
                                                                     "530856.65649413 6972312.4064941")}},
                                         :nimi "alue"
                                         :kuvaus "alue 1"
                                         :korkeusTaiSyvyys "1000"})
    (fact "LineString 2" LineString2 => {:viiva {:LineString {:pos '("530825.15649413 6972348.9064941"
                                                                     "530883.65649413 6972370.1564941"
                                                                     "530847.65649413 6972339.4064941"
                                                                     "530824.90649413 6972342.4064941")}}
                                         :nimi "Viiva"
                                         :kuvaus "Viiiva"
                                         :korkeusTaiSyvyys "111"})
    (fact "Alue" Alue => {:alue {:Polygon {:exterior {:LinearRing {:pos '("530859.15649413 6972389.4064941"
                                                                          "530836.40649413 6972367.4064941"
                                                                          "530878.40649413 6972372.6564941"
                                                                          "530859.15649413 6972389.4064941")}}}}
                          :nimi "Alueen nimi"
                          :kuvaus "Alueen kuvaus"
                          :korkeusTaiSyvyys "333"
                          :pintaAla "402"})

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

    ;; Kayton alku/loppu pvm
    (fact "alkuPvm" alkuPvm => (util/to-xml-date (-> tyoaika :data :tyoaika-alkaa-ms :value)))
    (fact "loppuPvm" loppuPvm => (util/to-xml-date (-> tyoaika :data :tyoaika-paattyy-ms :value)))

    ;; Hankkeen kuvaus
    (fact "lupaAsianKuvaus" lupaAsianKuvaus => (-> hankkeen-kuvaus :data :kayttotarkoitus :value))
    (fact "varattava-pinta-ala" pinta-ala => (-> hankkeen-kuvaus :data :varattava-pinta-ala :value))))
