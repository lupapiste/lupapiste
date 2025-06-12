(ns lupapalvelu.backing-system.krysp.kiinteistotoimitus-test
  (:require [clojure.data.xml :as cxml]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.kiinteistotoimitus-mapping :as mapping]
            [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]
            [lupapalvelu.document.attachments-canonical :as attachments-canon]
            [lupapalvelu.document.kiinteistotoimitus-canonical :as c]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.xml :as xml]
            [schema.core :as sc]))

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
                                                                        :postinumero          {:value "90800"}
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

(def app-with-dmcity-info
  (merge app
         {:arcgis-features ["123456"]
          :statements      [{:given 1368080324142
                             :id "518b3ee60364ff9a63c6d6a1"
                             :person {:text "Paloviranomainen"
                                      :name "Sonja Sibbo"
                                      :email "sonja.sibbo@sipoo.fi"
                                      :id "516560d6c2e6f603beb85147"}
                             :requested 1368080102631
                             :state "given"
                             :status "ehdollinen"
                             :text "Savupiippu pitää olla."}]
          :linkPermitData  [{:id "123-456-789" :type nil :operation nil :permitSubtype ""}]}))

(defn make-canonical [application lang]
  (let [begin-of-link                 "sftp://testi/"
        organization                  {}
        canonical-without-attachments (c/kiinteistotoimitus-canonical application lang)
        attachments-canonical (attachments-canon/get-attachments-as-canonical application organization begin-of-link)]
    (->> (mapping-common/add-generated-pdf-attachments application begin-of-link attachments-canonical lang)
         (mapping/bind-attachments canonical-without-attachments))))

(deftest canonical-to-xml-test
  (let [canonical   (make-canonical app "fi")
        mapping_102 (mapping/get-mapping "1.0.2")
        xml_102     (emit/element-to-xml canonical mapping_102)
        xml_102_str (cxml/indent-str xml_102)
        parsed-102  (cr/strip-xml-namespaces (xml/parse xml_102_str))

        mapping_105 (mapping/get-mapping "1.0.5")
        xml_105     (emit/element-to-xml canonical mapping_105)
        xml_105_str (cxml/indent-str xml_105)
        parsed-105  (cr/strip-xml-namespaces (xml/parse xml_105_str))

        mapping_106 (mapping/get-mapping "1.0.6")
        xml_106     (emit/element-to-xml (make-canonical app-with-ulkomainen-hetu "fi") mapping_106)
        xml_106_str (cxml/indent-str xml_106)
        parsed-106  (cr/strip-xml-namespaces (xml/parse xml_106_str))

        mapping_108 (mapping/get-mapping "1.0.8")
        xml_108     (emit/element-to-xml (make-canonical app-with-ulkomainen-hetu "fi") mapping_108)
        xml_108_str (cxml/indent-str xml_108)
        _parsed-108 (cr/strip-xml-namespaces (xml/parse xml_108_str))]

    (testing "canonical is a map"
      (is (map? canonical)))

    (testing "xml string is generated"
      (is (string? xml_105_str)))

    ;; Throws exception if invalid
    (testing "valid 1.0.2"
      (is (nil? (validator/validate xml_102_str (:permitType app) "1.0.2"))))

    (testing "valid 1.0.5"
      (is (nil? (validator/validate xml_105_str (:permitType app) "1.0.5"))))

    (testing "valid 1.0.6"
      (is (nil? (validator/validate xml_106_str (:permitType app) "1.0.6"))))

    (testing "valid 1.0.8"
      (is (nil? (validator/validate xml_108_str (:permitType app) "1.0.8"))))

    (testing "1.0.2 some checks"
      (is (= (:title app) (xml/get-text parsed-102 [:ToimituksenTiedot :aineistonnimi]))))

    (testing "1.0.5 added kayttotapaus"
      (is (= "Lupapiste kiinteistötoimitus" (xml/get-text parsed-105 [:kayttotapaus]))))

    (testing "1.0.6 added ulkomainen hetu"
      (is (= "hakija-123" (xml/get-text parsed-106 [:ulkomainenHenkilotunnus]))))))

(deftest dmcity-canonical-to-xml-test
  (let [canonical       (make-canonical app-with-dmcity-info "fi")
        mapping_106-dm  (mapping/get-mapping "1.0.6-dmcity-lp")
        xml_106-dm      (emit/element-to-xml canonical mapping_106-dm)
        xml_106_str-dm  (cxml/indent-str xml_106-dm)
        parsed-106-dm   (cr/strip-xml-namespaces (xml/parse xml_106_str-dm))]
    ;; Throws exception if invalid
    (testing "valid 1.0.6-dmcity-lp"
      (is (nil? (validator/validate xml_106_str-dm (:permitType app) "1.0.6-dmcity-lp"))))

    (testing "1.0.6-dmcity-lp"
      (testing "has lp:lausuntotieto"
        (is (= "Paloviranomainen" (xml/get-text parsed-106-dm [:lp/lausuntotieto :viranomainen]))))

      (testing "has lp:viitelupatieto"
        (is (= "123-456-789" (xml/get-text parsed-106-dm [:lp/viitelupatieto :tunnus]))))

      (testing "has lp:liitetieto"
        (is (= "hakemus_taustajarjestelmaan_siirrettaessa" (xml/get-text parsed-106-dm [:lp/liitetieto :tyyppi]))))

      (testing "has lp:toimenpiteet"
        (is (= "Kiinteistönmuodostus" (xml/get-text parsed-106-dm [:lp/toimenpiteet :ToimenpideTeksti]))))

      (testing "has lp:kohteet"
        (is (= "123456" (xml/get-text parsed-106-dm [:lp/kohteet :lp/Kohde])))))))


(def app-with-kiinteistonmuodostus-as-primary
  {:id               "LP-753-2026-90001"
   :title            "KT testi primary operation order"
   :address          "Testikatu 1"
   :municipality     "753"
   :permitType       "KT"
   :propertyId       "75341600010023"
   :location         [404000 6690000]
   :primaryOperation {:id   "primary-op-id"
                      :name "kiinteistonmuodostus"}  ;; kiinteistonmuodostus is PRIMARY
   :secondaryOperations [{:id   "secondary-op-id"
                          :name "rasitetoimitus"}]  ;; rasitetoimitus is SECONDARY
   :state            "submitted"
   :submitted        (now)
   :documents        [{:id          "doc-kiinteistonmuodostus"
                       :schema-info {:name       "kiinteistonmuodostus"
                                     :approvable true
                                     :version    1
                                     :op         {:id   "primary-op-id"
                                                  :name "kiinteistonmuodostus"}}
                       :created     1449999999970
                       :data        {:kiinteistonmuodostus
                                     {:kiinteistonmuodostusTyyppi {:value "halkominen"}
                                      :kuvaus {:value "Primary operation test"}}}}
                      {:id          "doc-rasitetoimitus"
                       :schema-info {:name       "rasitetoimitus"
                                     :approvable true
                                     :version    1
                                     :op         {:id   "secondary-op-id"
                                                  :name "rasitetoimitus"}}
                       :created     1449999999980
                       :data        {:rasitetoimitus
                                     {:kayttooikeuslaji {:value "Tieoikeus"}
                                      :paattymispvm     {:value nil}}}}
                      {:id          "doc-kiinteisto"
                       :schema-info {:name "kiinteisto"}
                       :data        {:kiinteisto {:kiinteistoTunnus {:value "75341600010023"}}}}
                      {:id          "doc-secondary-kiinteistot"
                       :schema-info {:name "secondary-kiinteistot"}
                       :data        {:kiinteisto {:kiinteistoTunnus {:value "75341600010024"}}}}
                      ;; Party document required for valid XML
                      {:id          "doc-hakija"
                       :schema-info {:name    "hakija-kt"
                                     :type    :party
                                     :subtype :hakija
                                     :version 1}
                       :created     1450000000036
                       :data        {:_selected {:value "henkilo"}
                                     :henkilo   {:henkilotiedot {:etunimi           {:value "Testi"}
                                                                 :sukunimi          {:value "Hakija"}
                                                                 :hetu              {:value "010180-123A"}
                                                                 :turvakieltoKytkin {:value false}}
                                                 :osoite        {:katu                 {:value "Testikatu 1"}
                                                                 :postinumero          {:value "00100"}
                                                                 :postitoimipaikannimi {:value "Helsinki"}
                                                                 :maa                  {:value "FIN"}}
                                                 :yhteystiedot  {:puhelin {:value "0401234567"}
                                                                 :email   {:value "testi@example.com"}}
                                                 :kytkimet      {:vainsahkoinenAsiointiKytkin {:value false}}}}}]
   :attachments      []
   :drawings         []})

(def app-with-rasitetoimitus-as-primary
  "Derived from app-with-kiinteistonmuodostus-as-primary by swapping primary/secondary operations."
  (-> app-with-kiinteistonmuodostus-as-primary
      (assoc :primaryOperation {:id   "secondary-op-id"
                                :name "rasitetoimitus"})
      (assoc :secondaryOperations [{:id   "primary-op-id"
                                    :name "kiinteistonmuodostus"}])))

(defn- parse-first-toimitus-element
  "Parses the XML and returns the tag name keyword of the first toimitus element
   inside featureMembers (e.g., :Halkominen, :Rasitetoimitus)."
  [xml-str]
  (let [parsed          (cr/strip-xml-namespaces (xml/parse xml-str))
        feature-members (xml/select1 parsed [:Kiinteistotoimitus :featureMembers])]
    (some->> feature-members
             :content
             (filter map?)
             (some :tag))))

(defn- generate-xml-str
  "Generates XML string for the given application using the production KRYSP mapper."
  [application krysp-version]
  (let [{:keys [xml]} (permit/application-krysp-mapper
                        application {} "fi" krysp-version "sftp://testi/")]
    (cxml/indent-str xml)))

(deftest primary-operation-ordering-in-xml-output-test
  (testing "kiinteistonmuodostus as primary operation"
    (let [application         app-with-kiinteistonmuodostus-as-primary
          expected-primary    (mapping/primary-operation-name application)
          xml-str             (generate-xml-str application "1.0.8")
          first-element       (parse-first-toimitus-element xml-str)]

      (testing "expected primary element should be Halkominen"
        (is (= :Halkominen expected-primary)))

      (testing "XML is valid"
        (is (nil? (validator/validate xml-str (:permitType application) "1.0.8"))))

      (testing "primary operation element appears first in XML"
        (is (= expected-primary first-element)))))

  (testing "rasitetoimitus as primary operation"
    (let [application         app-with-rasitetoimitus-as-primary
          expected-primary    (mapping/primary-operation-name application)
          xml-str             (generate-xml-str application "1.0.8")
          first-element       (parse-first-toimitus-element xml-str)]

      (testing "expected primary element should be Rasitetoimitus"
        (is (= :Rasitetoimitus expected-primary)))

      (testing "XML is valid"
        (is (nil? (validator/validate xml-str (:permitType application) "1.0.8"))))

      (testing "primary operation element appears first in XML"
        (is (= expected-primary first-element))))))

(deftest lp-canonical-to-xml-test
  (let [canonical       (make-canonical app-with-dmcity-info "fi")
        mapping_108-lp  (mapping/get-mapping "1.0.8-lp")
        xml_108-lp      (emit/element-to-xml canonical mapping_108-lp)
        xml_108_str-lp  (cxml/indent-str xml_108-lp)
        parsed-108-lp   (cr/strip-xml-namespaces (xml/parse xml_108_str-lp))]
    ;; Throws exception if invalid
    (testing "valid 1.0.8-lp"
      (is (nil? (validator/validate xml_108_str-lp (:permitType app) "1.0.8-lp"))))

    (testing "1.0.8-lp"
      (testing "has lp:lausuntotieto"
        (is (= "Paloviranomainen" (xml/get-text parsed-108-lp [:lp/lausuntotieto :viranomainen]))))

      (testing "has lp:viitelupatieto"
        (is (= "123-456-789" (xml/get-text parsed-108-lp [:lp/viitelupatieto :tunnus]))))

      (testing "has lp:liitetieto"
        (is (= "hakemus_taustajarjestelmaan_siirrettaessa" (xml/get-text parsed-108-lp [:lp/liitetieto :tyyppi]))))

      (testing "has lp:toimenpiteet"
        (is (= "Kiinteistönmuodostus" (xml/get-text parsed-108-lp [:lp/toimenpiteet :ToimenpideTeksti]))))

      (testing "has lp:kohteet"
        (is (= "123456" (xml/get-text parsed-108-lp [:lp/kohteet :lp/Kohde])))))))

(def app-with-tilan-lohkominen
  "Application with tilan-lohkominen as primary operation.
   This operation type is only valid with LP extensions (1.0.8-lp)."
  {:id               "LP-753-2026-90002"
   :title            "Tilan lohkominen testi"
   :address          "Lohkomistie 1"
   :municipality     "753"
   :permitType       "KT"
   :propertyId       "75341600010025"
   :location         [404000 6690000]
   :primaryOperation {:id   "tilan-lohkominen-op-id"
                      :name "tilan-lohkominen"}
   :state            "submitted"
   :submitted        (now)
   :documents        [{:id          "doc-tilan-lohkominen"
                       :schema-info {:name       "kiinteistonmuodostus"
                                     :approvable true
                                     :version    1
                                     :op         {:id   "tilan-lohkominen-op-id"
                                                  :name "tilan-lohkominen"}}
                       :created     1449999999970
                       :data        {:kiinteistonmuodostus
                                     {:kiinteistonmuodostusTyyppi {:value "tilan-lohkominen"}
                                      :kuvaus {:value "Tilan lohkominen kuvaus"}}}}
                      {:id          "doc-kiinteisto"
                       :schema-info {:name "kiinteisto"}
                       :data        {:kiinteisto {:kiinteistoTunnus {:value "75341600010025"}}}}
                      {:id          "doc-hakija"
                       :schema-info {:name    "hakija-kt"
                                     :type    :party
                                     :subtype :hakija
                                     :version 1}
                       :created     1450000000036
                       :data        {:_selected {:value "henkilo"}
                                     :henkilo   {:henkilotiedot {:etunimi           {:value "Lohko"}
                                                                 :sukunimi          {:value "Mies"}
                                                                 :hetu              {:value "010180-123A"}
                                                                 :turvakieltoKytkin {:value false}}
                                                 :osoite        {:katu                 {:value "Lohkomistie 1"}
                                                                 :postinumero          {:value "00100"}
                                                                 :postitoimipaikannimi {:value "Helsinki"}
                                                                 :maa                  {:value "FIN"}}
                                                 :yhteystiedot  {:puhelin {:value "0401234567"}
                                                                 :email   {:value "lohko@example.com"}}
                                                 :kytkimet      {:vainsahkoinenAsiointiKytkin {:value false}}}}}]
   :attachments      []
   :drawings         []})

(def app-with-tontinosan-lunastus
  "Application with tontinosan-lunastus as primary operation.
   This operation type is only valid with LP extensions (1.0.8-lp)."
  (-> app-with-tilan-lohkominen
      (assoc :id "LP-753-2026-90003"
             :title "Tontinosan lunastus testi"
             :primaryOperation {:id   "tontinosan-lunastus-op-id"
                                :name "tontinosan-lunastus"})
      (assoc-in [:documents 0 :schema-info :op] {:id   "tontinosan-lunastus-op-id"
                                                  :name "tontinosan-lunastus"})
      (assoc-in [:documents 0 :data :kiinteistonmuodostus :kiinteistonmuodostusTyyppi :value]
                "tontinosan-lunastus")))

(deftest tilan-lohkominen-xml-test
  (testing "TilanLohkominen"
    (let [xml-str       (generate-xml-str app-with-tilan-lohkominen "1.0.8-lp")
          first-element (parse-first-toimitus-element xml-str)]

      (testing "XML is valid against 1.0.8-lp schema"
        (is (nil? (validator/validate xml-str "KT" "1.0.8-lp"))))

      (testing "TilanLohkominen element is present"
        (is (= :TilanLohkominen first-element)))))

  (testing "TontinosanLunastus"
    (let [xml-str       (generate-xml-str app-with-tontinosan-lunastus "1.0.8-lp")
          first-element (parse-first-toimitus-element xml-str)]

      (testing "XML is valid against 1.0.8-lp schema"
        (is (nil? (validator/validate xml-str "KT" "1.0.8-lp"))))

      (testing "TontinosanLunastus element is present"
        (is (= :TontinosanLunastus first-element))))))
