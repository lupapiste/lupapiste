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
            [sade.strings :as ss]
            [sade.xml :as sxml]))

(def attachments [{:id :attachment1
                   :type {:type-group "paapiirustus"
                          :type-id    "asemapiirros"}
                   :latestVersion {:version { :major 1 :minor 0 }
                                   :fileId "file321"
                                   :filename "asemapiirros.pdf"
                                   :contentType "application/pdf"}
                   :modified 1424248442767}
                  {:id :attachment2
                   :type {:type-group "hakija"
                          :type-id    "valtakirja"}
                   :latestVersion {:version { :major 1 :minor 0 }
                                   :fileId "file123"
                                   :filename "valtakirja.pdf"
                                   :contentType "application/pdf"}
                   :op {:name "poikkeaminen"}
                   :modified 1424248442767}
                  {:id :attachment3
                   :type {:type-group "paapiirustus"
                          :type-id    "pohjapiirros"}
                   :versions []}])

(fact ":tag is set for UusiAsia" (has-tag ua-mapping/uusi-asia) => true)

(fl/facts* "UusiAsia xml from poikkeus"
  (let [application    (assoc poikkeus-test/poikkari-hakemus :attachments attachments)
        canonical      (ah/application-to-asianhallinta-canonical application "fi") => truthy
        canonical      (assoc-in canonical
                         [:UusiAsia :Liitteet :Liite]
                         (ah/get-attachments-as-canonical application "sftp://localhost/test/"))
        schema-version "ah-1.1"
        mapping        (ua-mapping/get-mapping (ss/suffix schema-version "-"))
        xml            (element-to-xml canonical mapping) => truthy
        xml-s          (xml/indent-str xml) => truthy
        permit-type    (:permitType application)
        docs           (common/documents-by-type-without-blanks (tools/unwrapped application))]
    (fact "Validator for Asianhallinta exists"
      ((keyword permit-type) validator/supported-versions-by-permit-type) => (contains schema-version))
    (fact "Validate UusiAsia XML"
      (validator/validate xml-s permit-type schema-version) => nil)
    (facts "XML elements"
      (let [xml-parsed (reader/strip-xml-namespaces (sxml/parse xml-s))]
        (fact "Attributes for UusiAsia"
          (fact "xmlns:ah"
            (sxml/select1-attribute-value xml-parsed [:UusiAsia] :xmlns:ah) => "http://www.lupapiste.fi/asianhallinta")
          (fact "version"
            (sxml/select1-attribute-value xml-parsed [:UusiAsia] :version) => (ss/suffix schema-version "-")))

        (fact "Maksaja is Yritys, Henkilo is not present"
          (let [maksaja (sxml/select xml-parsed [:UusiAsia :Maksaja :Yritys])
                maksaja-data (:data (first (:maksaja docs)))]

            maksaja =not=> empty?
            (sxml/select xml-parsed [:UusiAsia :Maksaja :Henkilo]) => empty?

            (fact "<Yritys> fields are same as in application"
              (sxml/get-text maksaja [:Nimi]) => (get-in maksaja-data [:yritys :yritysnimi])
              (sxml/get-text maksaja [:Ytunnus]) => (get-in maksaja-data [:yritys :liikeJaYhteisoTunnus])
              (sxml/get-text maksaja [:Yhteystiedot :Jakeluosoite]) => (get-in maksaja-data [:yritys :osoite :katu])
              (sxml/get-text maksaja [:Yhteystiedot :Postinumero]) => (get-in maksaja-data [:yritys :osoite :postinumero])
              (sxml/get-text maksaja [:Yhteystiedot :Postitoimipaikka]) => (get-in maksaja-data [:yritys :osoite :postitoimipaikannimi])
              (sxml/get-text maksaja [:Yhteyshenkilo :Etunimi]) => (get-in maksaja-data [:yritys :yhteyshenkilo :henkilotiedot :etunimi])
              (sxml/get-text maksaja [:Yhteyshenkilo :Sukunimi]) => (get-in maksaja-data [:yritys :yhteyshenkilo :henkilotiedot :sukunimi])
              (sxml/get-text maksaja [:Yhteyshenkilo :Yhteystiedot :Puhelinnumero]) => (get-in maksaja-data [:yritys :yhteyshenkilo :yhteystiedot :puhelin])
              (sxml/get-text maksaja [:Yhteyshenkilo :Yhteystiedot :Email]) => (get-in maksaja-data [:yritys :yhteyshenkilo :yhteystiedot :email])
              (sxml/get-text maksaja [:Yhteyshenkilo :Yhteystiedot :Email]) => (get-in maksaja-data [:yritys :yhteyshenkilo :yhteystiedot :email]))))
        (facts "Liitteet elements"
          (let [liitteet (sxml/select1 xml-parsed [:UusiAsia :Liitteet])
                att1 (first (:content liitteet))
                att2 (second (:content liitteet))]
            liitteet => truthy
            (fact "Two Liite elements" (count (:content liitteet)) => 2)
            ))))
    ; TODO check xml elements, ie deep elements, document values with _selected are correct in xml etc..
    ))
