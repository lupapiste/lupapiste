(ns lupapalvelu.xml.asianhallinta.asianhallinta_canonical_to_xml_test
  (:require [lupapalvelu.factlet :as fl]
            [midje.sweet :refer :all]
            [lupapalvelu.document.asianhallinta_canonical :as ah]
            [lupapalvelu.xml.asianhallinta.uusi_asia_mapping :as ua-mapping]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.validator :as validator]
            [clojure.data.xml :as xml]
            [sade.common-reader :as reader]))

(fact ":tag is set for UusiAsia" (has-tag ua-mapping/uusi-asia) => true)

(fl/facts* "UusiAsia xml from poikkeus"
  (let [application poikkeus-test/poikkari-hakemus
        canonical (ah/application-to-asianhallinta-canonical application "fi") => truthy
        xml (element-to-xml canonical ua-mapping/uusi-asia) => truthy
        xml-s (xml/indent-str xml) => truthy
        permit-type (:permitType application)
        schema-version "ah-1.1"]
    (fact "Validator for Asianhallinta exists"
      ((keyword permit-type) validator/supported-versions-by-permit-type) => (contains schema-version))
    (fact "Validate UusiAsia XML"
      (validator/validate xml-s permit-type schema-version) => nil)
    (facts "XML elements"
      (let [xml-parsed (reader/strip-xml-namespaces (sade.xml/parse xml-s))]))
    ; TODO check xml elements, ie deep elements, document values with _selected are correct in xml etc..
    ))
