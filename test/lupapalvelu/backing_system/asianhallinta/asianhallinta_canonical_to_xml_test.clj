(ns lupapalvelu.backing-system.asianhallinta.asianhallinta-canonical-to-xml-test
  (:require [clojure.data.xml :as xml]
            [lupapalvelu.factlet :as fl]
            [midje.sweet :refer :all]
            [midje.util :as mu]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.asianhallinta-canonical :as ah]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]
            [lupapalvelu.document.rakennuslupa-canonical-test :as rakennus-test]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.backing-system.asianhallinta.core]
            [lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping :as ua-mapping]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.validator :as validator]
            [sade.common-reader :as reader]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.property :as p]
            [sade.xml :as sxml]))

(mu/testable-privates lupapalvelu.backing-system.asianhallinta.core begin-of-link)
(mu/testable-privates lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping attachments-for-write)

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
                   :op [{:id "523844e1da063788effc1c56"}]
                   :modified 1424248442767}
                  {:id :attachment3
                   :type {:type-group "paapiirustus"
                          :type-id    "pohjapiirros"}
                   :versions []}])

(def- link-permit-data-kuntalupatunnus {:id "123-123-123-123" :type "kuntalupatunnus"})

(fact ":tag is set for UusiAsia" (has-tag ua-mapping/uusi-asia) => true)

(fl/facts*
  "UusiAsia xml from suunnittelija application"
  (let [application    (-> rakennus-test/application-suunnittelijan-nimeaminen
                         (assoc-in [:primaryOperation :name] "poikkeamis")
                         (assoc :permitType "P")
                         ua-mapping/enrich-application)
        canonical      (ah/application-to-asianhallinta-canonical application "fi") => truthy
        schema-version "ah-1.1"
        mapping        (ua-mapping/get-uusi-asia-mapping (ss/suffix schema-version "-"))
        xml            (element-to-xml canonical mapping) => truthy
        xml-s          (xml/indent-str xml) => truthy
        xml-parsed     (reader/strip-xml-namespaces (sxml/parse xml-s))]

    (fact "Validate UusiAsia XML"
      (validator/validate xml-s (:permitType application) schema-version) => nil)

    (facts "Viiteluvat"
      (let [links    (sxml/select1 xml-parsed [:UusiAsia :Viiteluvat])]
        (count (sxml/children links)) => 1
        (fact "Viiteluvat has MuuTunnus > (Tunnus and Sovellus)"
          (let [wrapper  (sxml/select1 links [:Viitelupa :MuuTunnus])
                tunnus   (sxml/get-text wrapper [:Tunnus]) #_(-> link :content first)
                sovellus (sxml/get-text wrapper [:Sovellus])]
            tunnus => (get-in application [:linkPermitData 0 :id])
            sovellus => "Lupapiste"))))))

(fl/facts*
  "UusiAsia xml from application with two link permits"
  (let [application    (-> rakennus-test/application-suunnittelijan-nimeaminen
                         (assoc-in [:primaryOperation :name] "poikkeamis")
                         (assoc :permitType "P")
                         (update-in [:linkPermitData] conj link-permit-data-kuntalupatunnus)
                         ua-mapping/enrich-application)
        canonical      (ah/application-to-asianhallinta-canonical application "fi") => truthy
        schema-version "ah-1.1"
        mapping        (ua-mapping/get-uusi-asia-mapping (ss/suffix schema-version "-"))
        xml            (element-to-xml canonical mapping) => truthy
        xml-s          (xml/indent-str xml) => truthy
        xml-parsed     (reader/strip-xml-namespaces (sxml/parse xml-s))]
    (fact "Validate UusiAsia XML"
      (validator/validate xml-s (:permitType application) schema-version) => nil)

    (facts "Viiteluvat"
      (let [links    (sxml/select1 xml-parsed [:UusiAsia :Viiteluvat])
            content  (sxml/children links)]
        (count content) => 2
        (fact "Lupapistetunnus has Tunnus and Sovellus"
          (sxml/get-text (first content) [:Tunnus]) => (get-in application [:linkPermitData 0 :id])
          (sxml/get-text (first content) [:Sovellus]) => "Lupapiste")

        (fact "Other link permit is in AsianTunnus field"
          (sxml/get-text (second content) [:AsianTunnus]) => (get-in application [:linkPermitData 1 :id]))))))

(fl/facts* "UusiAsia xml from poikkeus"
  (let [application    (ua-mapping/enrich-application
                         (assoc poikkeus-test/poikkari-hakemus :attachments attachments))
        canonical      (ah/application-to-asianhallinta-canonical application "fi") => truthy
        canonical      (assoc-in canonical
                         [:UusiAsia :Liitteet :Liite]
                         (ah/get-attachments-as-canonical (:attachments application) begin-of-link))
        schema-version "ah-1.1"
        mapping        (ua-mapping/get-uusi-asia-mapping (ss/suffix schema-version "-"))
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
            (fact "Correct count" (count (sxml/select hakijat [:Hakija])) => (count hakijat-data))
            (fact "Hakija is Henkilo, not yritys"
              (:tag (sxml/get-text hakijat [:Hakija])) => :Henkilo)))

        (facts "Liitteet elements"
          (let [liitteet (sxml/select1 xml-parsed [:UusiAsia :Liitteet])
                liit1 (first (:content liitteet))
                liit2 (second (:content liitteet))]
            liitteet => truthy
            (fact "Two Liite elements"
              (count (:content liitteet)) => 2
              (count (:content liitteet)) => (count (get-in canonical [:UusiAsia :Liitteet :Liite])))

            (facts "1st Liite"
              (fact "Kuvaus" (sxml/get-text liit1 [:Kuvaus]) => (get-in attachments [0 :type :type-id]))
              (fact "Tyyppi" (sxml/get-text liit1 [:Tyyppi]) => (get-in attachments [0 :latestVersion :contentType]))
              (fact "LinkkiLiitteeseen"
                (sxml/get-text liit1 [:LinkkiLiitteeseen]) => (str
                                                                begin-of-link
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
                                                                begin-of-link
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
                    (sxml/get-text (:content (last metas)) [:Arvo]) => (-> application :primaryOperation :name)))))))

        (facts "Toimenpiteet"
          (let [operations (sxml/select1 xml-parsed [:UusiAsia :Toimenpiteet])
                op         (sxml/get-text xml-parsed [:UusiAsia :Toimenpiteet])
                ttunnus    (-> op :content first)
                tteksti    (-> op :content second)]
            (count (:content operations)) => 1
            (fact "Toimenpide has ToimenpideTunnus and ToimenpideTeksti"
              (sxml/get-text op [:Toimenpide :ToimenpideTunnus]) => (:name (:primaryOperation application))
              (sxml/get-text op [:Toimenpide :ToimenpideTeksti]) => (i18n/localize "fi"
                                                                      (str "operations."
                                                                        (:name (:primaryOperation application)))))))

        (fact "Sijainti"
          (sxml/get-text xml-parsed [:UusiAsia :Sijainti :Sijaintipiste]) => (str (get-in application [:location 0]) " " (get-in application [:location 1])))
        (fact "Kiinteistotunnus"
          (sxml/get-text xml-parsed [:UusiAsia :Kiinteistotunnus]) => (p/to-human-readable-property-id (:propertyId application)))))))

(fl/facts* "Application with two multiple documents (Hakija, Maksaja)"
  (let [application    (assoc poikkeus-test/poikkari-hakemus :attachments attachments)
        henkilomaksaja (assoc-in ctc/henkilomaksaja [:schema-info :name] "maksaja")
        application    (assoc application :documents (conj (:documents application) ctc/yrityshakija henkilomaksaja))
        canonical      (ah/application-to-asianhallinta-canonical application "fi") => truthy
        canonical      (assoc-in canonical
                         [:UusiAsia :Liitteet :Liite]
                         (ah/get-attachments-as-canonical (:attachments application) begin-of-link))
        schema-version "ah-1.1"
        mapping        (ua-mapping/get-uusi-asia-mapping (ss/suffix schema-version "-"))
        xml            (element-to-xml canonical mapping) => truthy
        permit-type    (:permitType application)
        docs           (common/documents-by-type-without-blanks (tools/unwrapped application)) => truthy
        xml-parsed     (reader/strip-xml-namespaces (sxml/parse (xml/indent-str xml))) => truthy]

    (fact "Hakija"
      (let [hakijat (sxml/select xml-parsed [:UusiAsia :Hakijat :Hakija])
            hakijat-data (:hakija docs)]
        (fact "Correct count" (count hakijat) => (count hakijat-data))
        (fact "First Hakija is Henkilo, second is Yritys"
          (-> hakijat first :content first :tag) => :Henkilo
          (-> hakijat second :content first :tag) => :Yritys)
        (fact "Yritys has Yhteyshenkilo"
          (sxml/get-text hakijat [:Hakija :Yritys :Yhteyshenkilo]) => not-empty)))

    (fact "Only one Maksaja in XML"
      (count (:maksaja docs)) => 2
      (count (sxml/select xml-parsed [:UusiAsia :Maksaja])) => 1)

    (fact "Maksaja is Yritys 1743842-0 (first)"
      (sxml/get-text xml-parsed [:UusiAsia :Maksaja :Yritys :Ytunnus]) => (get-in docs [:maksaja 0 :data :yritys :liikeJaYhteisoTunnus]))))

(fl/facts* "Schema version 1.2"
  (let [application    (ua-mapping/enrich-application
                         (assoc poikkeus-test/poikkari-hakemus :attachments attachments))
        canonical      (ah/application-to-asianhallinta-canonical application "fi") => truthy
        canonical      (assoc-in canonical
                         [:UusiAsia :Liitteet :Liite]
                         (ah/get-attachments-as-canonical (:attachments application) begin-of-link))
        schema-version "ah-1.2"
        mapping        (ua-mapping/get-uusi-asia-mapping (ss/suffix schema-version "-"))
        xml            (element-to-xml canonical mapping) => truthy
        xml-s          (xml/indent-str xml) => truthy
        permit-type    (:permitType application)
        xml-parsed     (reader/strip-xml-namespaces (sxml/parse xml-s))]

    (fact "Validator for Asianhallinta exists"
      ((keyword permit-type) validator/supported-versions-by-permit-type) => (contains schema-version))

    (fact "Validate UusiAsia XML"
      (validator/validate xml-s permit-type schema-version) => nil)

    (facts "Liitteet has KuvausFi and KuvausSv added in schema 1.2"
      (let [liitteet (sxml/select1 xml-parsed [:UusiAsia :Liitteet])
            liit1 (first (:content liitteet))
            liit2 (second (:content liitteet))]
        liitteet => truthy
        (facts "1st Liite"
          (fact "KuvausFi" (sxml/get-text liit1 [:KuvausFi]) => "Asemapiirros")
          #_(fact "KuvausSv" (sxml/get-text liit1 [:KuvausSv]) => "Situationsplan"))
        (facts "2nd Liite"
          (fact "KuvausFi" (sxml/get-text liit2 [:KuvausFi]) => "Valtakirja")
          (fact "KuvausSv" (sxml/get-text liit2 [:KuvausSv]) => "Fullmakt"))))))


(facts "Unit tests - attachments-for-write"

  (fact "FileId and filename are returned"
    (map keys (attachments-for-write attachments)) => (has every? (just [:fileId :filename])))

  (fact "Only latestVersions are returned"
    (let [for-write-ids (set (map :fileId (attachments-for-write attachments)))]
      (some #(= % (-> attachments first :latestVersion :fileId)) for-write-ids) => true
      (some #(= % (-> attachments second :latestVersion :fileId)) for-write-ids) => true
      (some #(= % (-> attachments (nth 2) :latestVersion :fileId)) for-write-ids) => falsey))

  (facts "Statement and verdict targets are not included"

    (fact "Statement"
      (let [attachments (assoc-in attachments [0 :target :type] "statement")
           for-write-ids (set (map :fileId (attachments-for-write attachments)))]
       (some #(= % (-> attachments first :latestVersion :fileId)) for-write-ids) => falsey
       (some #(= % (-> attachments second :latestVersion :fileId)) for-write-ids) => true))

    (fact "Verdict"
      (let [attachments (assoc-in attachments [1 :target :type] "verdict")
           for-write-ids (set (map :fileId (attachments-for-write attachments)))]
       (some #(= % (-> attachments first :latestVersion :fileId)) for-write-ids) => true
       (some #(= % (-> attachments second :latestVersion :fileId)) for-write-ids) => falsey))

    (fact "Both"
      (let [attachments (assoc-in attachments [0 :target :type] "verdict")
            attachments (assoc-in attachments [1 :target :type] "statement")]
       (attachments-for-write {:attachments attachments}) => empty?))))
