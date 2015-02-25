(ns lupapalvelu.xml.asianhallinta.asianhallinta_canonical_to_xml_test
  (:require [lupapalvelu.factlet :as fl]
            [midje.sweet :refer :all]
            [midje.util :as mu]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.asianhallinta_canonical :as ah]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.xml.asianhallinta.uusi_asia_mapping :as ua-mapping]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.validator :as validator]
            [clojure.data.xml :as xml]
            [sade.common-reader :as reader]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]))

(mu/testable-privates lupapalvelu.xml.asianhallinta.asianhallinta get-begin-of-link)

(defn- has-attachment-types [meta]
  (fact "type-group and type-id"
    (:Avain (sxml/get-text (first meta) )) => "type-group"
    (:Avain (second meta)) => "type-id"))

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
                         (ah/get-attachments-as-canonical application (get-begin-of-link)))
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

    (facts "UusiAsia XML elements"
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

        (fact "Hakija"
          (let [hakijat (sxml/select xml-parsed [:UusiAsia :Hakijat])
                hakijat-data (:hakija docs)]
            (fact "Corrent count" (count (sxml/select hakijat [:Hakija])) => (count hakijat-data))
            (fact "Hakija is Henkilo, not yritys"
              (:tag (sxml/get-text hakijat [:Hakija])) => :Henkilo)))

        (facts "Liitteet elements"
          (let [liitteet (sxml/select1 xml-parsed [:UusiAsia :Liitteet])
                liit1 (first (:content liitteet))
                liit2 (second (:content liitteet))]
            liitteet => truthy
            (fact "Two Liite elements" (count (:content liitteet)) => 2)
            (facts "1st Liite"
              (fact "Kuvaus" (sxml/get-text liit1 [:Kuvaus]) => (get-in attachments [0 :type :type-id]))
              (fact "Tyyppi" (sxml/get-text liit1 [:Tyyppi]) => (get-in attachments [0 :latestVersion :contentType]))
              (fact "LinkkiLiitteeseen"
                (sxml/get-text liit1 [:LinkkiLiitteeseen]) => (str
                                                                (get-begin-of-link)
                                                                (writer/get-file-name-on-server
                                                                  (get-in attachments [0 :latestVersion :fileId])
                                                                  (get-in attachments [0 :latestVersion :filename]))))
              (fact "Luotu" (sxml/get-text liit1 [:Luotu]) => (util/to-xml-date (get-in attachments [0 :modified])))
              (fact "Metatieto"
                (let [metas (:content (sxml/select1 liit1 [:Metatiedot]))]
                  (count metas) => 2
                  (fact "Type checks"
                    (sxml/get-text (:content (first metas)) [:Avain]) => "type-group"
                    (sxml/get-text (:content (first metas)) [:Arvo]) => (get-in attachments [0 :type :type-group])
                    (sxml/get-text (:content (second metas)) [:Avain]) => "type-id"
                    (sxml/get-text (:content (second metas)) [:Arvo]) => (get-in attachments [0 :type :type-id])))))

            (facts "2nd Liite"
              (fact "Kuvaus" (sxml/get-text liit2 [:Kuvaus]) => (get-in attachments [1 :type :type-id]))
              (fact "Tyyppi" (sxml/get-text liit2 [:Tyyppi]) => (get-in attachments [1 :latestVersion :contentType]))
              (fact "LinkkiLiitteeseen"
                (sxml/get-text liit2 [:LinkkiLiitteeseen]) => (str
                                                                (get-begin-of-link)
                                                                (writer/get-file-name-on-server
                                                                  (get-in attachments [1 :latestVersion :fileId])
                                                                  (get-in attachments [1 :latestVersion :filename]))))
              (fact "Luotu" (sxml/get-text liit2 [:Luotu]) => (util/to-xml-date (get-in attachments [1 :modified])))
              (fact "Metatieto"
                (let [metas (:content (sxml/select1 liit2 [:Metatiedot]))]
                  (count metas) => 3 ; has also operation meta
                  (fact "Type checks"
                    (sxml/get-text (:content (first metas)) [:Avain]) => "type-group"
                    (sxml/get-text (:content (first metas)) [:Arvo]) => (get-in attachments [1 :type :type-group])
                    (sxml/get-text (:content (second metas)) [:Avain]) => "type-id"
                    (sxml/get-text (:content (second metas)) [:Arvo]) => (get-in attachments [1 :type :type-id]))
                  (fact "Operation meta check"
                    (sxml/get-text (:content (last metas)) [:Avain]) => "operation"
                    (sxml/get-text (:content (last metas)) [:Arvo]) => (get-in attachments [1 :op :name])))))))

        (facts "Toimenpiteet"
          (let [operations (sxml/select1 xml-parsed [:UusiAsia :Toimenpiteet])
                op         (sxml/get-text xml-parsed [:UusiAsia :Toimenpiteet])
                ttunnus    (-> op :content first)
                tteksti    (-> op :content second)]
            (count (:content operations)) => 1
            (fact "Toimenpide has ToimenpideTunnus and ToimenpideTeksti"
              (sxml/get-text op [:Toimenpide :ToimenpideTunnus]) => (get-in application [:operations 0 :name])
              (sxml/get-text op [:Toimenpide :ToimenpideTeksti]) => (i18n/localize "fi"
                                                                      (str "operations."
                                                                        (get-in application [:operations 0 :name]))))))))
    ; TODO check xml elements, ie deep elements, document values with _selected are correct in xml etc..
    ))
