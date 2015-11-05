(ns lupapalvelu.xml.krysp.poikkeamis-canonical-to-krysp-xml-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.document.poikkeamis-canonical :refer [poikkeus-application-to-canonical]]
            [lupapalvelu.document.poikkeamis-canonical-test :refer [poikkari-hakemus]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.krysp.poikkeamis-mapping :as mapping]
            [lupapalvelu.xml.validator :as  validator]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(fact ":tag is set, 2.1.2" (has-tag mapping/poikkeamis_to_krysp_212) => true)
(fact ":tag is set, 2.1.3" (has-tag mapping/poikkeamis_to_krysp_213) => true)
(fact ":tag is set, 2.1.4" (has-tag mapping/poikkeamis_to_krysp_214) => true)
(fact ":tag is set, 2.1.5" (has-tag mapping/poikkeamis_to_krysp_215) => true)
(fact ":tag is set, 2.2.0" (has-tag mapping/poikkeamis_to_krysp_220) => true)
(fact ":tag is set, 2.2.1" (has-tag mapping/poikkeamis_to_krysp_221) => true)

(facts "Poikkeuslupa to canonical and then to poikkeuslupa xml with schema validation"
  (let [canonical (poikkeus-application-to-canonical poikkari-hakemus "fi")
        xml_212 (element-to-xml canonical mapping/poikkeamis_to_krysp_212)
        xml_214 (element-to-xml canonical mapping/poikkeamis_to_krysp_214)
        xml_215 (element-to-xml canonical mapping/poikkeamis_to_krysp_215)
        xml_220 (element-to-xml canonical mapping/poikkeamis_to_krysp_220)
        xml_221 (element-to-xml canonical mapping/poikkeamis_to_krysp_221)
        xml_212_s (indent-str xml_212)
        xml_214_s (indent-str xml_214)
        xml_215_s (indent-str xml_215)
        xml_220_s (indent-str xml_220)
        xml_221_s (indent-str xml_221)]

    ; Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    (mapping-to-krysp/save-application-as-krysp poikkari-hakemus "fi" poikkari-hakemus {:krysp {:P {:ftpUser "dev_sipoo" :version "2.1.2"}}})
    (mapping-to-krysp/save-application-as-krysp poikkari-hakemus "fi" poikkari-hakemus {:krysp {:P {:ftpUser "dev_sipoo" :version "2.1.4"}}})
    (mapping-to-krysp/save-application-as-krysp poikkari-hakemus "fi" poikkari-hakemus {:krysp {:P {:ftpUser "dev_sipoo" :version "2.1.5"}}})
    (mapping-to-krysp/save-application-as-krysp poikkari-hakemus "fi" poikkari-hakemus {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.0"}}})
    (mapping-to-krysp/save-application-as-krysp poikkari-hakemus "fi" poikkari-hakemus {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.1"}}})

    (validator/validate xml_212_s (:permitType poikkari-hakemus) "2.1.2") ; throws exception
    (validator/validate xml_214_s (:permitType poikkari-hakemus) "2.1.4") ; throws exception
    (validator/validate xml_215_s (:permitType poikkari-hakemus) "2.1.5") ; throws exception
    (validator/validate xml_220_s (:permitType poikkari-hakemus) "2.2.0") ; throws exception
    (validator/validate xml_221_s (:permitType poikkari-hakemus) "2.2.1") ; throws exception

    (facts "212"
      (let [lp-xml    (cr/strip-xml-namespaces (xml/parse xml_212_s))]
        (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => "200"))

    (facts "214"
      (let [lp-xml    (cr/strip-xml-namespaces (xml/parse xml_214_s))]
        (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => nil
        (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosala]) => "200"))
    ))
