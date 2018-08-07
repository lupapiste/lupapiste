(ns lupapalvelu.xml.krysp.kiinteistotoimitus-test
  (:require [taoensso.timbre :refer [debug]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :as cxml]
            [schema.core :as sc]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]
            [sade.common-reader :as cr]
            [sade.xml :as xml]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.data-schema :as doc-schema]
            [lupapalvelu.document.kiinteistotoimitus-canonical :as c]
            [lupapalvelu.operations :as op]
            [lupapalvelu.xml.krysp.kiinteistotoimitus-mapping :as mapping]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]))

(testable-privates lupapalvelu.xml.krysp.kiinteistotoimitus-mapping get-mapping)

(def kt-op (get op/operations :kiinteistonmuodostus))

(def generated-attachments [(ssg/generate (dissoc att/Attachment (sc/optional-key :metadata)))])

(def app {:id               (ssg/generate ssc/ApplicationId)
          :title            "KT testi"
          :address          "KT testi 123"
          :municipality     "753"
          :permitType       "KT"
          :propertyId       "753123456"
          :location         [(ssg/generate Long) (ssg/generate Long)]
          :primaryOperation {:id   (ssg/generate ssc/ObjectIdStr)
                             :name "kiinteistonmuodostus"}
          :state            "submitted"
          :submitted        (now)
          :documents        [(ssg/generate (doc-schema/doc-data-schema (:schema kt-op) true))
                             (ssg/generate (doc-schema/doc-data-schema (:applicant-doc-schema kt-op) true))]
          :attachments      (map (fn [{:keys [versions] :as a}]
                                   (assoc a :latestVersion (last versions)))
                                 generated-attachments)})

(facts "canonical to xml"
  (let [canonical (c/kiinteistotoimitus-canonical app "fi")
        attachments (attachments-canon/get-attachments-as-canonical app "sftp://testi/")
        final-canonical (mapping/bind-attachments canonical
                                                  (mapping-common/add-generated-pdf-attachments app "sftp://testi" attachments "fi"))
        mapping_102 (get-mapping "1.0.2")
        xml_102       (emit/element-to-xml final-canonical mapping_102)
        xml_102_str   (cxml/indent-str xml_102)
        parsed-102    (cr/strip-xml-namespaces (xml/parse xml_102_str))
        mapping_105 (get-mapping "1.0.5")
        xml_105       (emit/element-to-xml final-canonical mapping_105)
        xml_105_str   (cxml/indent-str xml_105)
        parsed-105    (cr/strip-xml-namespaces (xml/parse xml_105_str))]
    canonical => map?
    xml_105_str => string?
    (fact "valid 1.0.2" (validator/validate xml_102_str (:permitType app) "1.0.2") => nil) ; throws exception if invalid+
    (fact "valid 1.0.5" (validator/validate xml_105_str (:permitType app) "1.0.5") => nil) ; throws exception if invalid

    (fact "1.0.2 some checks"                               ; TODO more comprehensive tests
      (xml/get-text parsed-102 [:ToimituksenTiedot :aineistonnimi]) => (:title app))
    (fact "1.0.5 added kayttotapaus"
      (xml/get-text parsed-105 [:kayttotapaus]) => "Lupapiste kiinteist\u00f6toimitus")))


