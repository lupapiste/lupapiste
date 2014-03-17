(ns lupapalvelu.xml.krysp.poikkeamis_canonical_to_krysp_xml_test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.document.poikkeamis-canonical :refer [poikkeus-application-to-canonical]]
            [lupapalvelu.document.poikkeamis-canonical-test :refer [poikkari-hakemus]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.krysp.poikkeamis-mapping :as mapping]
            [lupapalvelu.xml.krysp.validator :as  validator]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(fact ":tag is set, 2.1.2" (has-tag mapping/poikkeamis_to_krysp_212) => true)
(fact ":tag is set, 2.1.3" (has-tag mapping/poikkeamis_to_krysp_213) => true)
(fact ":tag is set, 2.1.4" (has-tag mapping/poikkeamis_to_krysp_214) => true)

(facts "Poikkeuslupa to canonical and then to poikkeuslupa xml with schema validation"
  (let [canonical (poikkeus-application-to-canonical poikkari-hakemus "fi")
        xml_212 (element-to-xml canonical mapping/poikkeamis_to_krysp_212)
        xml_214 (element-to-xml canonical mapping/poikkeamis_to_krysp_214)
        xml_212_s (indent-str xml_212)
        xml_214_s (indent-str xml_214)]

    ; Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    (mapping-to-krysp/save-application-as-krysp poikkari-hakemus "fi" poikkari-hakemus {:krysp {:P {:ftpUser "dev_sipoo" :version "2.1.2"}}})

    (validator/validate xml_212_s (:permitType poikkari-hakemus) "2.1.2") ; throws exception
    (validator/validate xml_214_s (:permitType poikkari-hakemus) "2.1.4") ; throws exception

    (facts "212"
      (let [lp-xml    (cr/strip-xml-namespaces (xml/parse xml_212_s))]
        (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => "200"))

    (facts "214"
      (let [lp-xml    (cr/strip-xml-namespaces (xml/parse xml_214_s))]
        (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => nil
        (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosala]) => "200"))
    ))
