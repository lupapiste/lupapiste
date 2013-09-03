(ns lupapalvelu.xml.krysp.reader-test
  (:use [midje.sweet]
        [lupapalvelu.xml.krysp.reader]))

(fact "property-equals returns url-encoded data"
  (property-equals "_a_" "_b_") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E_a_%3C%2FPropertyName%3E%3CLiteral%3E_b_%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(fact "property-equals returns url-encoded xml-encoded data"
  (property-equals "<a>" "<b>") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E%26lt%3Ba%26gt%3B%3C%2FPropertyName%3E%3CLiteral%3E%26lt%3Bb%26gt%3B%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(let [xml (sade.xml/parse (slurp "resources/public/krysp/permit.xml"))
      permits (->permits xml)]

  (fact "xml is parsed" permits => truthy)
  (fact "xml has 2 cases" (count permits) => 2)
  (fact "second case has 2 permits" (-> permits last count) => 2)

  (fact "kuntalupatunnus" (:kuntalupatunnus (last permits)) => "13-0185-R")

  (let [permit (first (:paatokset (last permits)))
        lupamaaraykset (:lupamaaraykset permit)
        paivamaarat    (:paivamaarat permit)
        poytakirjat    (:poytakirjat permit)]



    (facts "permit sections are parsed"
      lupamaaraykset => truthy
      paivamaarat    => truthy
      poytakirjat    => truthy
      (count poytakirjat) => 2)

    (facts "lupamaaraukset data is correct"
      (:autopaikkojaEnintaan lupamaaraykset) => 10
      (:autopaikkojaVahintaan lupamaaraykset) => 1
      (:autopaikkojaRakennettava lupamaaraykset) => 2
      (:autopaikkojaRakennettu lupamaaraykset) => 0
      (:autopaikkojaKiinteistolla lupamaaraykset) => 7
      (:autopaikkojaUlkopuolella lupamaaraykset) => 3
      (:kerrosala lupamaaraykset) => "100"
      (:kokonaisala lupamaaraykset) => "110"

    )

    )




  )
