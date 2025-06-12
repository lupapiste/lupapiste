(ns lupapalvelu.backing-system.krysp.ymparisto-ilmoitus-canonical-to-krysp-xml-test
  (:require [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.backing-system.krysp.ymparisto-ilmoitukset-mapping :refer [ymparistoilmoitus-element-to-xml ilmoitus_to_krysp_212 ilmoitus_to_krysp_221 ilmoitus_to_krysp_223 ilmoitus_to_krysp_224]]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical :refer [koeluontoinen-canonical meluilmoitus-canonical]]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical-test :refer [meluilmoitus-yritys-application meluilmoitus-application meluilmoitus-application-v2]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [sade.common-reader :as cr]
            [sade.date :as date]
            [sade.xml :as xml]))

(fact "2.1.2: :tag is set" (has-tag ilmoitus_to_krysp_212) => true)
(fact "2.2.1: :tag is set" (has-tag ilmoitus_to_krysp_221) => true)
(fact "2.2.3: :tag is set" (has-tag ilmoitus_to_krysp_223) => true)
(fact "2.2.4: :tag is set" (has-tag ilmoitus_to_krysp_224) => true)

(defn- meluilmoitus-do-test [application]
  (let [canonical  (meluilmoitus-canonical application "fi")
        xml_212    (ymparistoilmoitus-element-to-xml canonical "2.1.2")
        xml_221    (ymparistoilmoitus-element-to-xml canonical "2.2.1")
        xml_212_s  (indent-str xml_212)
        xml_221_s  (indent-str xml_221)
        lp-xml_212 (cr/strip-xml-namespaces (xml/parse xml_212_s))
        lp-xml_221 (cr/strip-xml-namespaces (xml/parse xml_221_s))]

    (fact "valid xml 2.1.2"
      (validator/validate (indent-str xml_212) (:permitType application) "2.1.2") => nil)
    (fact "valid xml 2.2.1"
      (validator/validate (indent-str xml_221) (:permitType application) "2.2.1") => nil)

    (fact "toiminnan kesto"
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :alkuPvm]) => (date/xml-date "2014-02-03")
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :loppuPvm]) => (date/xml-date "2014-02-07")
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :arkiAlkuAika]) => "07:00:00"
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :arkiLoppuAika]) => "21:30:00"
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :lauantaiAlkuAika]) => "08:00:00"
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :lauantaiLoppuAika]) => "20:00:00.0"
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :sunnuntaiAlkuAika]) => "12:00:00"
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :sunnuntaiLoppuAika]) => "18:00:00")

    (fact "koneet"
      (xml/get-text lp-xml_212 [:Melutarina :koneidenLkm]) => "Murskauksen ja rammeroinnin vaatimat koneet, sek\u00e4 py\u00f6r\u00e4kuormaaja. ")

    (fact "ilmoittaja"
      (let [ilmoittaja (xml/select lp-xml_221 [:Melutarina :ilmoittaja])]
        (xml/get-text ilmoittaja [:yTunnus]) => "1060155-5"
        (fact "maa"
              (xml/get-text ilmoittaja [:osoitetieto :Osoite :valtioKansainvalinen]) => "FIN")
        (fact "direct marketing"
              (xml/get-text ilmoittaja [:suoramarkkinointikielto]) => "true")))
    ))


(facts "Meluilmoitus type of permits to canonical and then to xml with schema validation"

  (fact "Meluilmoitus yritys -> canonical -> xml"
    (meluilmoitus-do-test meluilmoitus-yritys-application)))

(facts "ilmoittaja uses (finnish) hetu"
  (let [canonical  (meluilmoitus-canonical meluilmoitus-application "fi")
        xml_224_s  (indent-str (ymparistoilmoitus-element-to-xml canonical "2.2.4"))
        lp-xml_224 (-> xml_224_s  xml/parse cr/strip-xml-namespaces)]
    (validator/validate xml_224_s (:permitType meluilmoitus-application) "2.2.4")
    (fact "ilmoittaja"
      (let [ilmoittaja (xml/select lp-xml_224 [:Melutarina :ilmoittaja])]
        (fact "sukunimi"
          (xml/get-text ilmoittaja [:sukunimi]) => "Borga")
        (fact "hetu"
          (xml/get-text ilmoittaja [:henkilotunnus]) => "210281-9988")))))

(facts "ilmoittaja uses ulkomainen hetu"
  (let [canonical  (meluilmoitus-canonical meluilmoitus-application-v2 "fi")
        xml_224_s  (indent-str (ymparistoilmoitus-element-to-xml canonical "2.2.4"))
        lp-xml_224 (-> xml_224_s  xml/parse cr/strip-xml-namespaces)]
    (validator/validate xml_224_s (:permitType meluilmoitus-application-v2) "2.2.4")
    (fact "ilmoittaja"
      (let [ilmoittaja (xml/select lp-xml_224 [:Melutarina :ilmoittaja])]
        (fact "sukunimi"
          (xml/get-text ilmoittaja [:sukunimi]) => "Borga")
        (fact "hetu"
          (xml/get-text ilmoittaja [:henkilotunnus]) => nil)
        (fact "ulkomainen hetu"
          (xml/get-text ilmoittaja [:ulkomainenHenkilotunnus]) => "ilmoittaja-123")))))

(facts "Koeluontoinentoiminta type of permits to canonical and then to xml with schema validation"
  (fact "Koeluontoinentoiminta type -> canonical -> xml"
    ;; Note: uses meluilmoitus-application, but it doesn't matter for this test
    (let [canonical  (koeluontoinen-canonical meluilmoitus-application "fi")
          xml_224_s  (indent-str (ymparistoilmoitus-element-to-xml canonical "2.2.4"))
          lp-xml_224 (-> xml_224_s  xml/parse cr/strip-xml-namespaces)]

      (fact "valid xml 2.2.4"
        (validator/validate xml_224_s "YI" "2.2.4") => nil)

      ;; Required by the schema but not supported by Lupapiste
      (fact "kuvausKoeluonteisestaToiminnasta"
        (xml/select lp-xml_224 [:kuvausKoeluonteisestaToiminnasta])
        => [{:attrs nil :content nil :tag :kuvausKoeluonteisestaToiminnasta}]))))
