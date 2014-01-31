(ns lupapalvelu.document.yleiset-alueet-kayttolupa-canonical-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer :all]
            [lupapalvelu.factlet :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical]]
            [lupapalvelu.document.tools :as tools]
            [sade.util :refer :all]))


(def ^:private drawings [{:id 1,
                         :name "alue",
                         :desc "alue 1",
                         :category "123",
                         :geometry
                         "LINESTRING(530856.65649413 6972312.1564941,530906.40649413 6972355.6564941,530895.65649413 6972366.9064941,530851.15649413 6972325.9064941,530856.65649413 6972312.4064941)",
                         :area "",
                         :height "1000"}
                        {:id 2,
                         :name "Viiva",
                         :desc "Viiiva",
                         :category "123",
                         :geometry
                         "LINESTRING(530825.15649413 6972348.9064941,530883.65649413 6972370.1564941,530847.65649413 6972339.4064941,530824.90649413 6972342.4064941)",
                         :area "",
                         :height ""}
                        {:id 3,
                         :name "Piste",
                         :desc "Piste jutska",
                         :category "123",
                         :geometry "POINT(530851.15649413 6972373.1564941)",
                         :area "",
                         :height ""}
                        {:id 4
                         :name "Alueen nimi"
                         :desc "Alueen kuvaus"
                         :category "123"
                         :geometry "POLYGON((530859.15649413 6972389.4064941,530836.40649413 6972367.4064941,530878.40649413 6972372.6564941,530859.15649413 6972389.4064941))",
                         :area "402",
                         :height  ""
                         }])

(def ^:private operation {:id "52380c6894a74fc25bb4ba46",
                          :created 1379404904514,
                          :name "ya-kayttolupa-terassit"})

(def ^:private hankkeen-kuvaus {:id "52380c6894a74fc25bb4ba4a"
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

(def ^:private tyoaika-kayttolupa (assoc-in tyoaika [:schema-info :op] operation))

(def ^:private documents [hakija
                          tyoaika-kayttolupa
                          maksaja
                          hankkeen-kuvaus])

(def kayttolupa-application {:schema-version 1,
                             :id "LP-753-2013-00002",
                             :created 1379404904514,
                             :opened 1379404981309,
                             :modified 1379405054747,
                             :submitted 1379405092649,
                             :permitType "YA",
                             :organization "753-YA",
                             :infoRequest false,
                             :authority sonja,
                             :state "submitted",
                             :title "Latokuja 1",
                             :address "Latokuja 1",
                             :location location,
                             :attachments [],
                             :operations [operation],
                             :propertyId "75341600550007",
                             :documents documents,
                             :municipality municipality,
                             :statements statements
                             :drawings drawings})


(testable-privates lupapalvelu.document.yleiset-alueet-canonical get-yritys-and-henkilo)

(facts* "Kayttolupa canonical model is correct"
  (let [canonical (application-to-canonical kayttolupa-application "fi")
        YleisetAlueet (:YleisetAlueet canonical) => truthy
        yleinenAlueAsiatieto (:yleinenAlueAsiatieto YleisetAlueet) => truthy
        Kayttolupa (:Kayttolupa yleinenAlueAsiatieto) => truthy

        Kasittelytieto (-> Kayttolupa :kasittelytietotieto :Kasittelytieto) => truthy
        Kasittelytieto-kasittelija-nimi (-> Kasittelytieto :kasittelija :henkilotieto :Henkilo :nimi) => truthy

        luvanTunnisteTiedot (:luvanTunnisteTiedot Kayttolupa) => truthy
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy

        Kayttolupa-kayttotarkoitus (:kayttotarkoitus Kayttolupa) => truthy

        Sijainti-osoite (-> Kayttolupa :sijaintitieto first :Sijainti :osoite) => truthy
        Sijainti-yksilointitieto (-> Sijainti-osoite :yksilointitieto) => truthy
        Sijainti-alkuHetki (-> Sijainti-osoite :alkuHetki) => truthy
        Sijainti-osoitenimi (-> Sijainti-osoite :osoitenimi :teksti) => truthy
        Sijainti-piste (-> Kayttolupa :sijaintitieto first :Sijainti :piste :Point :pos) => truthy

        PisteSijanti (-> Kayttolupa :sijaintitieto second :Sijainti :piste :Point :pos) => "530851.15649413 6972373.1564941"

        LineString1 (-> Kayttolupa :sijaintitieto (nth 2) :Sijainti :viiva :LineString :pos) => '("530856.65649413 6972312.1564941"
                                                                                                   "530906.40649413 6972355.6564941"
                                                                                                   "530895.65649413 6972366.9064941"
                                                                                                   "530851.15649413 6972325.9064941"
                                                                                                   "530856.65649413 6972312.4064941")

        LineString2 (-> Kayttolupa :sijaintitieto (nth 3) :Sijainti :viiva :LineString :pos) => '("530825.15649413 6972348.9064941"
                                                                                                   "530883.65649413 6972370.1564941"
                                                                                                   "530847.65649413 6972339.4064941"
                                                                                                   "530824.90649413 6972342.4064941")


        Alue (-> Kayttolupa :sijaintitieto (nth 4) :Sijainti :alue :Polygon :exterior :LinearRing :pos) => '("530859.15649413 6972389.4064941"
                                                                                                              "530836.40649413 6972367.4064941"
                                                                                                              "530878.40649413 6972372.6564941"
                                                                                                              "530859.15649413 6972389.4064941")

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
        maksaja-yksityinen (tools/unwrapped
                             (get-yritys-and-henkilo (assoc-in (:data maksaja) [:_selected :value] "henkilo") "maksaja"))
        maksaja-yksityinen-Henkilo (-> maksaja-yksityinen :Osapuoli :henkilotieto :Henkilo) => truthy
        maksaja-yksityinen-nimi (:nimi maksaja-yksityinen-Henkilo) => truthy
        maksaja-yksityinen-osoite (:osoite maksaja-yksityinen-Henkilo) => truthy

        alkuPvm (-> Kayttolupa :alkuPvm) => truthy
        loppuPvm (-> Kayttolupa :loppuPvm) => truthy

        lupaAsianKuvaus (:lupaAsianKuvaus Kayttolupa) => truthy
        Sijoituslupaviite (-> Kayttolupa :sijoituslupaviitetieto :Sijoituslupaviite) => falsey

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

        pinta-ala (:pintaala Kayttolupa) => truthy]

;    (println "\n canonical:")
;    (clojure.pprint/pprint canonical)
;    (println "\n")

    (fact "contains nil" (contains-value? canonical nil?) => falsey)

    (fact "Kasittelytieto-muutosHetki" (:muutosHetki Kasittelytieto) => (to-xml-datetime (:modified kayttolupa-application)))
    (fact "Kasittelytieto-hakemuksenTila" (:hakemuksenTila Kasittelytieto) => "vireill\u00e4")
    (fact "Kasittelytieto-asiatunnus" (:asiatunnus Kasittelytieto) => (:id kayttolupa-application))
    (fact "Kasittelytieto-paivaysPvm" (:paivaysPvm Kasittelytieto) => (to-xml-date (:opened kayttolupa-application)))
    (fact "Kasittelytieto-kasittelija-etunimi" (:etunimi Kasittelytieto-kasittelija-nimi) => (:firstName sonja))
    (fact "Kasittelytieto-kasittelija-sukunimi" (:sukunimi Kasittelytieto-kasittelija-nimi) => (:lastName sonja))

    (fact "Muu tunnus" (:tunnus MuuTunnus) => "LP-753-2013-00002")
    (fact "Sovellus" (:sovellus MuuTunnus) => "Lupapiste")

    (fact "Kayttolupa-kayttotarkoitus" Kayttolupa-kayttotarkoitus => ((keyword (:name operation)) ya-operation-type-to-usage-description))

    ;; Sijainti
    (fact "Sijainti-yksilointitieto" Sijainti-yksilointitieto => (:id kayttolupa-application))
;    (fact "Sijainti-alkuHetki" Sijainti-alkuHetki => <now??>)              ;; TODO: Mita tahan?
    (fact "Sijainti-osoitenimi" Sijainti-osoitenimi => (:address kayttolupa-application))
    (fact "Sijainti-piste-xy" Sijainti-piste => (str (-> kayttolupa-application :location :x) " " (-> kayttolupa-application :location :y)))

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

    ;; Kayton alku/loppu pvm
    (fact "alkuPvm" alkuPvm => (to-xml-date-from-string (-> tyoaika :data :tyoaika-alkaa-pvm :value)))
    (fact "loppuPvm" loppuPvm => (to-xml-date-from-string (-> tyoaika :data :tyoaika-paattyy-pvm :value)))

    ;; Hankkeen kuvaus
    (fact "lupaAsianKuvaus" lupaAsianKuvaus => (-> hankkeen-kuvaus :data :kayttotarkoitus :value))
    (fact "varattava-pinta-ala" pinta-ala => (-> hankkeen-kuvaus :data :varattava-pinta-ala :value))))
