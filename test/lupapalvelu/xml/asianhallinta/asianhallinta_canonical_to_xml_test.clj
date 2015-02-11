(ns lupapalvelu.xml.asianhallinta.asianhallinta_canonical_to_xml_test
  (:require [lupapalvelu.factlet :as fl]
            [midje.sweet :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.asianhallinta_canonical :as ah]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.xml.asianhallinta.uusi_asia_mapping :as ua-mapping]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.validator :as validator]
            [clojure.data.xml :as xml]
            [sade.common-reader :as reader]
            [sade.xml :as sxml]))

(fact ":tag is set for UusiAsia" (has-tag ua-mapping/uusi-asia) => true)

(fl/facts* "UusiAsia xml from poikkeus"
  (let [application    poikkeus-test/poikkari-hakemus
        canonical      (ah/application-to-asianhallinta-canonical application "fi") => truthy
        xml            (element-to-xml canonical ua-mapping/uusi-asia) => truthy
        xml-s          (xml/indent-str xml) => truthy
        permit-type    (:permitType application)
        schema-version "ah-1.1"
        docs           (common/documents-by-type-without-blanks (tools/unwrapped application))]
    (fact "Validator for Asianhallinta exists"
      ((keyword permit-type) validator/supported-versions-by-permit-type) => (contains schema-version))
    (fact "Validate UusiAsia XML"
      (validator/validate xml-s permit-type schema-version) => nil)
    (facts "XML elements"
      (let [xml-parsed (reader/strip-xml-namespaces (sxml/parse xml-s))]

        (fact "Maksaja is Yritys, Henkilo is not present"
          (let [maksaja (sxml/select xml-parsed [:UusiAsia :Maksaja :Yritys])
                maksaja-doc (first (:maksaja docs))]

            maksaja =not=> empty?
            (sxml/select xml-parsed [:UusiAsia :Maksaja :Henkilo]) => empty?

            (fact "<Yritys> fields are same as in application"
              (sxml/get-text maksaja [:Nimi]) => (get-in maksaja-doc [:data :yritys :yritysnimi])
              (sxml/get-text maksaja [:Ytunnus]) => (get-in maksaja-doc [:data :yritys :liikeJaYhteisoTunnus])
              (sxml/get-text maksaja [:Yhteystiedot :Jakeluosoite]) => (get-in maksaja-doc [:data :yritys :osoite :katu])
              (sxml/get-text maksaja [:Yhteystiedot :Postinumero]) => (get-in maksaja-doc [:data :yritys :osoite :postinumero])
              (sxml/get-text maksaja [:Yhteystiedot :Postitoimipaikka]) => (get-in maksaja-doc [:data :yritys :osoite :postitoimipaikannimi])
              (sxml/get-text maksaja [:Yhteyshenkilo :Etunimi]) => (get-in maksaja-doc [:data :yritys :yhteyshenkilo :henkilotiedot :etunimi])
              (sxml/get-text maksaja [:Yhteyshenkilo :Sukunimi]) => (get-in maksaja-doc [:data :yritys :yhteyshenkilo :henkilotiedot :sukunimi])
              (sxml/get-text maksaja [:Yhteyshenkilo :Yhteystiedot :Puhelinnumero]) => (get-in maksaja-doc [:data :yritys :yhteyshenkilo :yhteystiedot :puhelin])
              (sxml/get-text maksaja [:Yhteyshenkilo :Yhteystiedot :Email]) => (get-in maksaja-doc [:data :yritys :yhteyshenkilo :yhteystiedot :email])
              (sxml/get-text maksaja [:Yhteyshenkilo :Yhteystiedot :Email]) => (get-in maksaja-doc [:data :yritys :yhteyshenkilo :yhteystiedot :email]))))))
    ; TODO check xml elements, ie deep elements, document values with _selected are correct in xml etc..
    ))
