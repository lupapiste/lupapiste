(ns lupapalvelu.backing-system.krysp.kiinteistotoimitus-test
  (:require [clojure.data.xml :as cxml]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.kiinteistotoimitus-mapping :as mapping]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.kiinteistotoimitus-canonical :as c]
            [lupapalvelu.operations :as op]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.xml :as xml]
            [schema.core :as sc]))

(testable-privates lupapalvelu.backing-system.krysp.kiinteistotoimitus-mapping get-mapping)

(def kt-op (get op/operations :kiinteistonmuodostus))

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

(def generated-attachments (mapv #(assoc % :type {:type-group :muut :type-id :muu})
                                 [(ssg/generate (dissoc att/Attachment (sc/optional-key :metadata)))]))

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
          ;; Documents generated from schema but not dynamically since the resulting xml fails to pass randomly
          :documents        [{:id          "0dce0969a236b2a10a481580"
                              :schema-info {:name       "kiinteistonmuodostus"
                                            :approvable true
                                            :version    1}
                              :created     1449999999970
                              :data        {:kiinteistonmuodostus
                                            {:kiinteistonmuodostusTyyppi
                                             {:value "kiinteiston-tunnusmuutos"}
                                             :kuvaus {:value "2kWYax.s"}}}}
                             {:id          "27e8ead53f2d04e94975e014"
                              :schema-info {:group-help        nil
                                            :approvable        true
                                            :name              "hakija-kt"
                                            :section-help      nil
                                            :i18name           "osapuoli"
                                            :type              :party
                                            :last-removable-by :none
                                            :accordion-fields  [{:type   :selected
                                                                 :paths  [["henkilo" "henkilotiedot" "etunimi"]
                                                                          ["henkilo" "henkilotiedot" "sukunimi"]
                                                                          ["yritys" "yritysnimi"]]
                                                                 :format "- %s %s"}]
                                            :removable-by      :all
                                            :after-update      lupapalvelu.application-meta-fields/applicant-index-update
                                            :repeating         true
                                            :order             3
                                            :version           1
                                            :subtype           :hakija}
                              :created     1450000000036
                              :data        {:_selected {:value "henkilo"}
                                            :henkilo   {:userId        {:value "afdbbc820d12151d30b0b10d"}
                                                        :henkilotiedot {:etunimi                 {:value "8\\O' t"}
                                                                        :sukunimi                {:value "A6_L'>;c(7u"}
                                                                        :not-finnish-hetu        {:value false}
                                                                        :ulkomainenHenkilotunnus {:value "3p."}
                                                                        :hetu                    {:value "301269A996Y"}
                                                                        :turvakieltoKytkin       {:value false}}
                                                        :osoite        {:katu                 {:value "7qN.W3]"}
                                                                        :postinumero          {:value "90724"}
                                                                        :postitoimipaikannimi {:value "k-H:"}
                                                                        :maa                  {:value "BLM"}}
                                                        :yhteystiedot  {:puhelin {:value "35 504-"}
                                                                        :email   {:value "rct1bh@hp9wq.com"}}
                                                        :kytkimet      {:suoramarkkinointilupa       {:value false}
                                                                        :vainsahkoinenAsiointiKytkin {:value true}}}
                                            :yritys    {:companyId            {:value "be1d4813630a4d276d3d4e8a"}
                                                        :yritysnimi           {:value "snL{A]IY4@Q"}
                                                        :liikeJaYhteisoTunnus {:value "5807577-7"}
                                                        :osoite               {:katu                 {:value "W=$\"p2+a"}
                                                                               :postinumero          {:value "21584"}
                                                                               :postitoimipaikannimi {:value "ZtwIz=j"}
                                                                               :maa                  {:value "PAN"}}
                                                        :yhteyshenkilo        {:henkilotiedot {:etunimi           {:value "HJ"}
                                                                                               :sukunimi          {:value "W4Dbtyf"}
                                                                                               :turvakieltoKytkin {:value false}}
                                                                               :yhteystiedot  {:puhelin {:value "846"}
                                                                                               :email   {:value "t@ukgyck.com"}}
                                                                               :kytkimet      {:suoramarkkinointilupa       {:value true}
                                                                                               :vainsahkoinenAsiointiKytkin {:value false}}}}}}]
          :attachments      (map (fn [{:keys [versions] :as a}]
                                   (assoc a :latestVersion
                                          (assoc (last versions)
                                                 :fileId (ssg/generate ssc/UUIDStr)
                                                 :filename (ssg/generate ssc/NonBlankStr))))
                                 generated-attachments)
          :drawings         drawings})

(def app-with-ulkomainen-hetu
  (update-in app
             [:documents 1 :data :henkilo :henkilotiedot]
             merge
             {:not-finnish-hetu        {:modified 1 :value true}
              :ulkomainenHenkilotunnus {:modified 1 :value "hakija-123"}}))

(facts "canonical to xml"
  (let [canonical       (c/kiinteistotoimitus-canonical app "fi")
        attachments     (attachments-canon/get-attachments-as-canonical app {} "sftp://testi/")
        final-canonical (mapping/bind-attachments canonical
                                                  (mapping-common/add-generated-pdf-attachments app "sftp://testi" attachments "fi"))
        mapping_102     (get-mapping "1.0.2")
        xml_102         (emit/element-to-xml final-canonical mapping_102)
        xml_102_str     (cxml/indent-str xml_102)
        parsed-102      (cr/strip-xml-namespaces (xml/parse xml_102_str))

        mapping_105     (get-mapping "1.0.5")
        xml_105         (emit/element-to-xml final-canonical mapping_105)
        xml_105_str     (cxml/indent-str xml_105)
        parsed-105      (cr/strip-xml-namespaces (xml/parse xml_105_str))

        mapping_106     (get-mapping "1.0.6")
        xml_106         (emit/element-to-xml (c/kiinteistotoimitus-canonical app-with-ulkomainen-hetu "fi") mapping_106)
        xml_106_str     (cxml/indent-str xml_106)
        parsed-106      (cr/strip-xml-namespaces (xml/parse xml_106_str))]
    canonical => map?
    xml_105_str => string?
    (fact "valid 1.0.2" (validator/validate xml_102_str (:permitType app) "1.0.2") => nil) ; throws exception if invalid
    (fact "valid 1.0.5" (validator/validate xml_105_str (:permitType app) "1.0.5") => nil) ; throws exception if invalid
    (fact "valid 1.0.6" (validator/validate xml_106_str (:permitType app) "1.0.6") => nil) ; throws exception if invalid

    ; TODO more comprehensive tests
    (fact "1.0.2 some checks"
      (xml/get-text parsed-102 [:ToimituksenTiedot :aineistonnimi]) => (:title app))
    (fact "1.0.5 added kayttotapaus"
      (xml/get-text parsed-105 [:kayttotapaus]) => "Lupapiste kiinteist\u00f6toimitus")
    (fact "1.0.6 added ulkomainen hetu"
      (xml/get-text parsed-106 [:ulkomainenHenkilotunnus]) => "hakija-123")))
