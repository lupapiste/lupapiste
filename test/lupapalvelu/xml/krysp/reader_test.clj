(ns lupapalvelu.xml.krysp.reader-test
  (:use [midje.sweet]
        [lupapalvelu.xml.krysp.reader])
  (:require [clj-time.coerce :as coerce]))

(defn- to-timestamp [yyyy-mm-dd]
  (coerce/to-long (coerce/from-string yyyy-mm-dd)))

(fact "property-equals returns url-encoded data"
  (property-equals "_a_" "_b_") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E_a_%3C%2FPropertyName%3E%3CLiteral%3E_b_%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(fact "property-equals returns url-encoded xml-encoded data"
  (property-equals "<a>" "<b>") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E%26lt%3Ba%26gt%3B%3C%2FPropertyName%3E%3CLiteral%3E%26lt%3Bb%26gt%3B%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(facts "KRYSP verdict"
  (let [xml (sade.xml/parse (slurp "resources/krysp/sample/verdict.xml"))
      cases (->verdicts xml)]

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
        (:vaaditutTyonjohtajat lupamaaraykset) => "Jokim\u00e4ki"
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
        cases (->verdicts xml)]
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
        cases (->verdicts xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has no cases" (count cases) => 0)))

(facts "nil xml"
  (let [cases (->verdicts nil)]
    (seq cases) => nil
    (count cases) => 0))

(facts "no verdicts"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/no-verdicts.xml"))
        cases (->verdicts xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")
    (fact "case has no verdicts" (-> cases last :paatokset count) => 0)))

(facts "Building from Sito"
  (let [xml (sade.xml/parse (slurp "resources/krysp/sample/sito-porvoo-building.xml"))
        buildings (->buildings xml)
        building  (first buildings)]
    (fact "xml is parsed" buildings => truthy)
    (fact "xml has 1 buildings" (count buildings) => 2)
    (fact "Kiinteistotunnus" (:propertyId building) => "63845900130022")
    (fact "Rakennustunnus" (:buildingId building) => "001")
    (fact "Kayttotarkoitus" (:usage building) => "011 yhden asunnon talot")
    (fact "Alkuhetki year as created" (:created building) => "2013")))
