(ns lupapalvelu.xml.krysp.reader-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.xml.krysp.reader :refer :all]
            [clj-time.coerce :as coerce]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]))

(defn- to-timestamp [yyyy-mm-dd]
  (coerce/to-long (coerce/from-string yyyy-mm-dd)))

(testable-privates lupapalvelu.xml.krysp.reader ->verdict ->ya-verdict)

(fact "property-equals returns url-encoded data"
  (property-equals "_a_" "_b_") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E_a_%3C%2FPropertyName%3E%3CLiteral%3E_b_%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(fact "property-equals returns url-encoded xml-encoded data"
  (property-equals "<a>" "<b>") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E%26lt%3Ba%26gt%3B%3C%2FPropertyName%3E%3CLiteral%3E%26lt%3Bb%26gt%3B%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(facts "KRYSP verdict"
  (let [xml (sade.xml/parse (slurp "resources/krysp/sample/verdict.xml"))
      cases (->verdicts xml :RakennusvalvontaAsia ->verdict)]

    (fact "xml is parsed" cases => truthy)
    (fact "xml has 2 cases" (count cases) => 2)
    (fact "second case has 2 verdicts" (-> cases last :paatokset count) => 2)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:autopaikkojaEnintaan lupamaaraykset) => 10
        (:autopaikkojaVahintaan lupamaaraykset) => 1
        (:autopaikkojaRakennettava lupamaaraykset) => 2
        (:autopaikkojaRakennettu lupamaaraykset) => 0
        (:autopaikkojaKiinteistolla lupamaaraykset) => 7
        (:autopaikkojaUlkopuolella lupamaaraykset) => 3
        (:kerrosala lupamaaraykset) => "100"
        (:kokonaisala lupamaaraykset) => "110"
        (:vaaditutTyonjohtajat lupamaaraykset) => "vastaava ylijohtaja, vastaava varajohtaja, altavastaava johtaja"
        (let [katselmukset (:vaaditutKatselmukset lupamaaraykset)
              maaraykset   (:maaraykset lupamaaraykset)]
          (facts "katselmukset"
            (count katselmukset) => 2
            (:katselmuksenLaji (first katselmukset)) => "aloituskokous"
            (:tarkastuksenTaiKatselmuksenNimi (last katselmukset)) => "katselmus2")
          (facts "m\u00e4\u00e4r\u00e4ykset"
            (count maaraykset) => 2
            (:sisalto (first maaraykset)) => "Maarays 1"
            (:maaraysaika (first maaraykset)) => (to-timestamp "2013-08-28")
            (:toteutusHetki (last maaraykset)) => (to-timestamp "2013-08-31")))

        (facts "second verdict"
          (let [katselmukset2 (-> cases last :paatokset last :lupamaaraykset :vaaditutKatselmukset)]
            (count katselmukset2) => 1
            katselmukset2 => sequential?)
          (let [maaraykset2 (-> cases last :paatokset last :lupamaaraykset :maaraykset)]
            (count maaraykset2) => 1
            maaraykset2 => sequential?))
        )

      (facts "paivamaarat data is correct"
        paivamaarat    => truthy
        (:aloitettava paivamaarat) => (to-timestamp "2013-09-01")
        (:lainvoimainen paivamaarat) => (to-timestamp "2013-09-02")
        (:voimassaHetki paivamaarat) => (to-timestamp "2013-09-03")
        (:raukeamis paivamaarat) => (to-timestamp "2013-09-04")
        (:anto paivamaarat) => (to-timestamp "2013-09-05")
        (:viimeinenValitus paivamaarat) => (to-timestamp "2013-09-06")
        (:julkipano paivamaarat) => (to-timestamp "2013-09-07"))

      (facts "p\u00f6yt\u00e4kirjat data is correct"
        poytakirjat    => truthy
        (count poytakirjat) => 2

        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:paatos pk1) => "P\u00e4\u00e4t\u00f6s 1"
          (:paatoskoodi pk1) => "my\u00f6nnetty"
          (:paatoksentekija pk1) => "viranomainen"
          (:paatospvm pk1) => (to-timestamp "2013-08-01")
          (:pykala pk1) => 1
          (:kuvaus liite) => "kuvaus 1"
          (:linkkiliitteeseen liite) => "http://localhost:8000/img/under-construction.gif"
          (:muokkausHetki liite) => (to-timestamp "2013-09-01T12:00:00")
          (:versionumero liite) => "1"
          (get-in liite [:tekija :henkilo :nimi :sukunimi]) => "Tarkkanen"
          (:tyyppi liite) => "tyyppi 1"
          (:metadata liite) => {:nimi "arvo"})

        (facts "second verdict"
          (let [poytakirjat2 (-> cases last :paatokset last :poytakirjat)]
            (count poytakirjat2) => 1
            poytakirjat2 => sequential?))))))

(facts "CGI sample verdict"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/cgi-verdict.xml"))
        cases (->verdicts xml :RakennusvalvontaAsia ->verdict)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "case has 1 verdict" (-> cases last :paatokset count) => 1)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")

    (let [verdict         (-> cases first :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)
          katselmukset   (:vaaditutKatselmukset lupamaaraykset)]

      (fact "paatos" verdict => truthy)
      (fact "lupamaaraykset is parsed" lupamaaraykset => truthy)

      (facts "katselmukset"
        (count katselmukset) => 1
        (:katselmuksenLaji (first katselmukset)) => "loppukatselmus")

      (facts "paivamaarat data is correct"
        paivamaarat    => truthy
        (:aloitettava paivamaarat) => pos?
        (:aloitettava paivamaarat) => (to-timestamp "2016-10-08")
        (:lainvoimainen paivamaarat) => (to-timestamp "2013-10-08")
        (:voimassaHetki paivamaarat) => (to-timestamp "2018-10-08")
        (:raukeamis paivamaarat) => nil
        (:anto paivamaarat) => (to-timestamp "2013-09-06")
        (:viimeinenValitus paivamaarat) => (to-timestamp "2013-10-07")
        (:julkipano paivamaarat) => (to-timestamp "2013-09-04"))


      (facts "p\u00f6yt\u00e4kirjat data is correct"
        poytakirjat    => truthy
        (count poytakirjat) => 1

        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:paatos pk1) => nil
          (:paatoskoodi pk1) => "my\u00f6nnetty"
          (:paatoksentekija pk1) => "Rakennuslautakunta"
          (:paatospvm pk1) => (to-timestamp "2013-09-03")
          (:pykala pk1) => 12
          (:kuvaus liite) => "P\u00e4\u00e4t\u00f6sote"
          (:linkkiliitteeseen liite) => "http://212.213.116.162:80/186/arkisto/2013/PAATOSOTE_13-0185-R_20130903152736270.rtf"
          (:muokkausHetki liite) => (to-timestamp "2013-09-03T15:27:46")
          (:tyyppi liite) => "P\u00e4\u00e4t\u00f6sote")))))

(facts "case not found"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/notfound.xml"))
        cases (->verdicts xml :RakennusvalvontaAsia ->verdict)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has no cases" (count cases) => 0)))

(facts "nil xml"
  (let [cases (->verdicts nil :RakennusvalvontaAsia ->verdict)]
    (seq cases) => nil
    (count cases) => 0))

(facts "no verdicts"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/no-verdicts.xml"))
        cases (->verdicts xml :RakennusvalvontaAsia ->verdict)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")
    (fact "case has no verdicts" (-> cases last :paatokset count) => 0)))

(facts "KRYSP yhteiset 2.1.0"
  (let [xml (sade.xml/parse (slurp "resources/krysp/sample/sito-porvoo-building.xml"))
        buildings (->buildings-summary xml)
        building1-id (:buildingId (first buildings))
        building2-id (:buildingId (last buildings))
        schema       (schemas/get-schema (schemas/get-latest-schema-version) "purku")]
    (fact "xml is parsed" buildings => truthy)
    (fact "xml has 2 buildings" (count buildings) => 2)
    (fact "Kiinteistotunnus" (:propertyId (first buildings)) => "63845900130022")
    (fact "Rakennustunnus" building1-id => "001")
    (fact "Kayttotarkoitus" (:usage (first buildings)) => "011 yhden asunnon talot")
    (fact "Alkuhetki year as created" (:created (first buildings)) => "2013")
    (let [building1  (dissoc (->rakennuksen-tiedot xml building1-id) :kiinttun)
          omistajat1 (:rakennuksenOmistajat building1)]

      (fact "Reader produces valid document (sans kiinttun)"
        (model/validate {:data (tools/wrapped building1)} schema) =not=> model/has-errors?)

      (fact "Has 2 owners" (count omistajat1) => 2)

      (let [owner1 (:0 omistajat1)
            owner2 (:1 omistajat1)]
        (get-in owner1 [:_selected]) => "henkilo"
        (get-in owner1 [:henkilo :henkilotiedot :etunimi]) => "Antero"
        (get-in owner1 [:henkilo :henkilotiedot :sukunimi]) => "Pekkala"
        (get-in owner1 [:henkilo :henkilotiedot :turvakieltoKytkin]) => true
        (get-in owner1 [:henkilo :osoite :katu]) => "Uuden-Saksalan tie 1"
        (get-in owner1 [:henkilo :osoite :postinumero]) => "06500"
        (get-in owner1 [:henkilo :osoite :postitoimipaikannimi]) => "PORVOO"

        (get-in owner1 [:_selected]) => "henkilo"
        (get-in owner2 [:henkilo :henkilotiedot :etunimi]) => "Pauliina"
        (get-in owner2 [:henkilo :henkilotiedot :sukunimi]) => "Pekkala"
        (get-in owner2 [:henkilo :osoite :katu]) => "Uuden-Saksalan tie 1"
        (get-in owner2 [:henkilo :osoite :postinumero]) => "06500"
        (get-in owner2 [:henkilo :osoite :postitoimipaikannimi]) => "PORVOO"
        (get-in owner2 [:henkilo :henkilotiedot :turvakieltoKytkin]) => nil))

    (let [building2  (dissoc (->rakennuksen-tiedot xml building2-id) :kiinttun)
          omistajat2 (:rakennuksenOmistajat building2)]

      (fact "Reader produces valid document (sans kiinttun)"
        (model/validate {:data (tools/wrapped building2)} schema) =not=> model/has-errors?)

      (fact "Has 2 owners" (count omistajat2) => 2)

      (let [owner1 (:0 omistajat2)
            owner2 (:1 omistajat2)]
        (get-in owner1 [:_selected]) => "henkilo"
        (get-in owner1 [:henkilo :henkilotiedot :sukunimi]) => "Pekkala"
        (get-in owner1 [:omistajalaji]) => nil
        (get-in owner1 [:muu-omistajalaji]) => ", wut?"

        (get-in owner2 [:_selected]) => "yritys"
        (get-in owner2 [:omistajalaji]) => "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"
        (get-in owner2 [:muu-omistajalaji]) => nil
        (get-in owner2 [:yritys :yhteyshenkilo :henkilotiedot :etunimi]) => "Paavo"
        (get-in owner2 [:yritys :yhteyshenkilo :henkilotiedot :sukunimi]) => "Pekkala"
        (get-in owner2 [:yritys :yhteyshenkilo :yhteystiedot :puhelin]) => "01"
        (get-in owner2 [:yritys :yhteyshenkilo :yhteystiedot :email]) => "paavo@example.com"
        (get-in owner2 [:yritys :yritysnimi]) => "Pekkalan Putki Oy"
        (get-in owner2 [:yritys :liikeJaYhteisoTunnus]) => "123"
        (get-in owner2 [:yritys :osoite :katu]) => "Uuden-Saksalan tie 1\u20132 d\u2013e A 1"
        (get-in owner2 [:yritys :osoite :postinumero]) => "06500"
        (get-in owner2 [:yritys :osoite :postitoimipaikannimi]) => "PORVOO"))))


;YA verdict

(facts "KRYSP ya-verdict"
  (let [xml (sade.xml/parse (slurp "resources/krysp/sample/yleiset alueet/ya-verdict.xml"))
        cases (->verdicts xml :yleinenAlueAsiatieto ->ya-verdict)]

    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 cases" (count cases) => 1)
    (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "422")

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:takuuaikaPaivat lupamaaraykset) => "760"
        (:muutMaaraykset lupamaaraykset) => "Viheralueet rakennettava, J\u00e4lkity\u00f6t teht\u00e4v\u00e4")

      (facts "paivamaarat data is correct"
        paivamaarat => truthy
        (:paatosdokumentinPvm paivamaarat) => (to-timestamp "2013-11-04"))

      (facts "p\u00f6yt\u00e4kirjat data is correct"
        poytakirjat => nil?))))


(facts "Buildings from verdict message"
  (let [xml (sade.xml/parse (slurp "resources/krysp/sample/sito-porvoo-LP-638-2013-00024-paatos-ilman-liitteita.xml"))
        buildings (->buildings xml)
        building1 (first buildings)]

    (count buildings) => 1
    (:jarjestysnumero building1) => "31216"
    (:kiinttun building1) => "63820130310000"
    (:rakennusnro building1) => "123"))

(facts "wfs-krysp-url works correctly"
  (fact "without ? returns url with ?" (wfs-krysp-url "http://localhost" case-type (property-equals "test" "lp-1")) =>  "http://localhost?request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")
  (fact "with ? returns url with ?" (wfs-krysp-url "http://localhost" case-type (property-equals "test" "lp-1")) =>  "http://localhost?request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")
  (fact "without extraparam returns correct" (wfs-krysp-url "http://localhost?output=KRYSP" case-type (property-equals "test" "lp-1")) =>  "http://localhost?output=KRYSP&request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E"))
