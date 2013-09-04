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

(let [xml (sade.xml/parse (slurp "resources/public/krysp/permit.xml"))
      cases (->permits xml)]

  (fact "xml is parsed" cases => truthy)
  (fact "xml has 2 cases" (count cases) => 2)
  (fact "second case has 2 permits" (-> cases last count) => 2)

  (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")

  (let [permit (first (:paatokset (last cases)))
        lupamaaraykset (:lupamaaraykset permit)
        paivamaarat    (:paivamaarat permit)
        poytakirjat    (:poytakirjat permit)]

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

      (facts "second permit"
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
      (count poytakirjat) => 2)
    )
  )
