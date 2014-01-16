(ns lupapalvelu.xml.krysp.poikkeamis_canonical_to_krysp_xml_test
  (:use [midje.sweet]
        [lupapalvelu.document.poikkeamis-canonical :only [poikkeus-application-to-canonical]]
        [lupapalvelu.document.poikkeamis-canonical-test :only [poikkari-hakemus]]
        [lupapalvelu.xml.emit]
        [lupapalvelu.xml.krysp.poikkeamis-mapping :only [poikkeamis_to_krysp]]
        [lupapalvelu.xml.krysp.validator :only [validate]]
        [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :only [has-tag]]
        [clojure.data.xml]
        [clojure.java.io])
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]))

(fact ":tag is set" (has-tag poikkeamis_to_krysp) => true)

(facts "Poikkeuslupa to canonical and then to poikkeuslupa xml with schema validation"
  (let [canonical (poikkeus-application-to-canonical poikkari-hakemus "fi")
        xml (element-to-xml canonical poikkeamis_to_krysp)
        xml-s (indent-str xml)]

    ;Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    ;; TODO: own test
    (mapping-to-krysp/save-application-as-krysp poikkari-hakemus "fi" poikkari-hakemus {:krysp {:P {:ftpUser "sipoo"}}})


    ;(clojure.pprint/pprint rakennuslupa_to_krysp)
    ;(with-open [out-file (writer "/Users/terotu/example.xml" )]
    ;    (emit xml out-file))
    (fact "xml exist" xml => truthy)

   ; (validate xml-s)  ;; in lupapalvelu.xml.krysp.validator
    ))
