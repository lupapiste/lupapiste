(ns lupapalvelu.backing-system.krysp.ymparisto-ilmoitus-canonical-to-krysp-xml-test
  (:require [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical :refer [meluilmoitus-canonical]]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical-test :refer [meluilmoitus-yritys-application ]]
            [lupapalvelu.backing-system.krysp.ymparisto-ilmoitukset-mapping :refer [ymparistoilmoitus-element-to-xml ilmoitus_to_krysp_212 ilmoitus_to_krysp_221]]
            [lupapalvelu.xml.validator :refer [validate]]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.validator :refer :all :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(fact "2.1.2: :tag is set" (has-tag ilmoitus_to_krysp_212) => true)
(fact "2.2.1: :tag is set" (has-tag ilmoitus_to_krysp_221) => true)

(defn- do-test [application]
  (let [canonical (meluilmoitus-canonical application "fi")
        xml_212 (ymparistoilmoitus-element-to-xml canonical "2.1.2")
        xml_221 (ymparistoilmoitus-element-to-xml canonical "2.2.1")
        xml_212_s     (indent-str xml_212)
        xml_221_s     (indent-str xml_221)
        lp-xml_212    (cr/strip-xml-namespaces (xml/parse xml_212_s))
        lp-xml_221    (cr/strip-xml-namespaces (xml/parse xml_221_s))]

    (validator/validate (indent-str xml_212) (:permitType application) "2.1.2")
    (validator/validate (indent-str xml_221) (:permitType application) "2.2.1")

    (fact "toiminnan kesto"
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :alkuPvm]) => "2014-02-03"
      (xml/get-text lp-xml_212 [:Melutarina :toiminnanKesto :loppuPvm]) => "2014-02-07"
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
    (do-test meluilmoitus-yritys-application)))
