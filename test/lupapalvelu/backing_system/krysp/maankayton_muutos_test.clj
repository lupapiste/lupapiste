(ns lupapalvelu.backing-system.krysp.maankayton-muutos-test
  (:require [clojure.data.xml :as cxml]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.maankayton-muutos-mapping :as mapping]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.data-schema :as doc-schema]
            [lupapalvelu.document.maankayton-muutos-canonical :as c]
            [lupapalvelu.operations :as op]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.xml :as xml]
            [schema.core :as sc]))

(def mm-op (get op/operations :tonttijako))

(def drawings [{:area           "99"
                :category       "123"
                :desc           ""
                :geometry
                "POLYGON((404374.429 6693819.7695,404386.054 6693817.7695,404388.179 6693810.0195,404379.679 6693807.8945,404374.429 6693819.7695))"
                :geometry-wgs84 {:coordinates [[[25.266089496212 60.369492694894]
                                                [25.266301124719 60.369477490689]
                                                [25.2663433303 60.369408438932]
                                                [25.266190302108 60.369387360732]
                                                [25.266089496212 60.369492694894]]]
                                 :type        "Polygon"}
                :height         ""
                :id             1
                :length         ""
                :name           "Test area"}
               {:area           ""
                :category       "123"
                :desc           ""
                :geometry       "POINT(404382.179 6693803.5195)"
                :geometry-wgs84 {:coordinates [25.266237694473 60.369348686989]
                                 :type        "Point"}
                :height         ""
                :id             2
                :length         ""
                :name           "Test point"}
               {:area           ""
                :category       "123"
                :desc           ""
                :geometry
                "LINESTRING(404390.054 6693803.2695,404395.304 6693806.8945,404395.179 6693817.2695,404391.054 6693821.7695)"
                :geometry-wgs84 {:coordinates [[25.2663805283 60.369348302779]
                                               [25.266473943162 60.369382075424]
                                               [25.266466731507 60.369475157877]
                                               [25.266389830428 60.369514569856]]
                                 :type        "LineString"}
                :height         ""
                :id             3
                :length         "23"
                :name           "Test line"}])

(def generated-attachments [(ssg/generate (dissoc att/Attachment (sc/optional-key :metadata)))])

(def primary-operation {:id   (ssg/generate ssc/ObjectIdStr)
                        :name "tonttijako"})

(def app {:id               (ssg/generate ssc/ApplicationId)
          :title            "MM testi"
          :address          "MM testi 123"
          :municipality     "753"
          :permitType       "MM"
          :propertyId       (ssg/generate ssc/Kiinteistotunnus)
          :location         [(ssg/generate Long) (ssg/generate Long)]
          :primaryOperation primary-operation
          :state            "submitted"
          :submitted        (now)
          :documents        [(assoc-in (ssg/generate (doc-schema/doc-data-schema (:schema mm-op) true))
                                       [:schema-info :op] primary-operation)
                             (ssg/generate (doc-schema/doc-data-schema (:applicant-doc-schema mm-op) true))]
          :attachments      (map (fn [{:keys [versions] :as a}]
                                   (assoc a :latestVersion
                                          (merge (last versions)
                                                 {:fileId   "test-id"
                                                  :filename (ssg/generate ssc/NonBlankStr)})))
                                 generated-attachments)
          :drawings         drawings})

(facts "canonical to xml"
  (let [canonical       (c/maankayton-muutos-canonical app "fi")
        attachments     (attachments-canon/get-attachments-as-canonical app {} "sftp://testi/")
        muutos          (-> canonical :Maankaytonmuutos :maankayttomuutosTieto first key)
        final-canonical (assoc-in
                          canonical
                          [:Maankaytonmuutos :maankayttomuutosTieto muutos :liitetieto]
                          (mapping-common/add-generated-pdf-attachments app "sftp://testi" attachments "fi"))
        mapping_101     (mapping/get-mapping "1.0.1" muutos)
        xml_101         (emit/element-to-xml final-canonical mapping_101)
        xml_101_str     (cxml/indent-str xml_101)
        parsed-101      (cr/strip-xml-namespaces (xml/parse xml_101_str))
        mapping_103     (mapping/get-mapping "1.0.3" muutos)
        xml_103         (emit/element-to-xml final-canonical mapping_103)
        xml_103_str     (cxml/indent-str xml_103)
        parsed-103      (cr/strip-xml-namespaces (xml/parse xml_103_str))]
      muutos => :Tonttijako
      canonical => map?
      xml_103_str => string?
      (fact "valid 1.0.1" (validator/validate xml_101_str (:permitType app) "1.0.1") => nil)   ; throws exception if invalid+
      (fact "valid 1.0.3" (validator/validate xml_103_str (:permitType app) "1.0.3") => nil))) ; throws exception if invalid
