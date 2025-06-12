(ns lupapalvelu.asiointi.api-read-rakennelma-itest
  "Create a R application with a rakennelma rakennuskohde and read result from AsiointiAPI"
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.asiointi.test-data :as test-data]
            [lupapalvelu.asiointi.test-utils :as asiointi-test]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest :refer [admin command query with-test-system]]
            [lupapalvelu.pate-itest-util :refer [fill-verdict]]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]
            [schema.test :as schema-test]))

(set! *warn-on-reflection* true)

(use-fixtures :once schema-test/validate-schemas)

(use-fixtures :each (fn [run-tests]
                      (with-test-system (-> {:fixture "pate-verdict"}
                                            test-system/with-remote-components
                                            test-system/with-mock-laundry)
                        (env/with-feature-value :new-foreman-invite true
                          (run-tests)))))

(def template-id
  "From Pate verdict fixture."
  "69aaaa39615f8072bec823be")

(def applicant itest/pena)
(def authority itest/sonja)

(def ^:private query-application (asiointi-test/query-application :building-permit))

(def ^:private pre-verdict-documents
  {"hakija-r"                           test-data/hakija
   "hankkeen-kuvaus"                    test-data/hankkeen-kuvaus
   "laajennettuRakennusjateselvitys-v2" test-data/laajennettuRakennusjateselvitys-v2
   "maksaja"                            test-data/maksaja
   "paasuunnittelija"                   test-data/paasuunnittelija
   "paatoksen-toimitus-rakval"          test-data/paatoksen-toimitus-rakval
   "rakennuspaikka"                     test-data/rakennuspaikka
   "suunnittelija"                      test-data/suunnittelija
   "uusiRakennuskohde"                  test-data/uusi-rakennuskohde-rakennelma-full})

(def ^:private post-verdict-documents
  {"tyonjohtaja-v3" test-data/tyonjohtaja-v3})

(defn- created-matcher
  [{:keys [permit-id]}]
  {:attachment                []
   :buildingPermitApplication [{:applicationContent            ""
                                :buildingPermitApplicationType "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/01"
                                :dateOfReception               nil
                                :lifeCycleState                "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1"
                                :permitApplicationKey          asiointi-test/uuid-string?
                                :permitApplicationType         "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"}]
   :buildingSite              {:buildingSiteAddress     [{:addressKey    asiointi-test/uuid-string?
                                                          :addressName   {:fin ""}
                                                          :addressNumber 1
                                                          :location      {:geometry {:coordinates [444444.0 6666666.0]
                                                                                     :type        "Point"}
                                                                          :srid     "3067"}
                                                          :postalCode    ""}]
                               :buildingSiteKey         asiointi-test/uuid-string?
                               :geometry                {:geometry {:coordinates [444444.0 6666666.0]
                                                                    :type        "Point"}
                                                         :srid     "3067"}
                               :name                    {:fin "foo 42, bar"}
                               :stormWaterTreatmentType nil
                               :tenure                  nil}
   :changePermit              []
   :constructionAction        [{:businessConstruction       false
                                :constructionActionKey      asiointi-test/uuid-string?
                                :constructionActionType     (:uusi-rakennus-tai-rakennelma ryhti-mappings/rakentamistoimenpide)
                                :descriptionOfAction        {:eng "Construction of a residential block of flats  or terraced house"
                                                             :fin "Asuinkerrostalon tai rivitalon rakentaminen"
                                                             :swe "Byggande av flervåningshus eller radhus"}
                                :renovation                 false
                                :statusOfConstructionAction "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/1"}]
   :constructionProject       {:constructionProjectKey asiointi-test/uuid-string?
                               :foreman                []
                               :planner                []}
   :dateOfInitiation          nil
   :decision                  nil
   :extensionDecision         []
   :lifeCycleState            (:ennakkoneuvottelu-tai-valmistelu ryhti-mappings/rakvalv-asian-elinkaaren-tila)
   :locationPermitObject      []
   :municipalityNumber        "753"
   :permanentPermitIdentifier permit-id})

(defn- filled-matcher
  [options]
  (-> (created-matcher options)
      (assoc-in [:buildingPermitApplication 0 :applicationContent] "tehdään jotain")
      (assoc-in [:buildingPermitApplication 0 :callForDeviation] [{:callForDeviationKey asiointi-test/uuid-string?
                                                                   :contentOfDeviation  {:fin "en kerro"}
                                                                   :deviationType       "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/13"}])
      (assoc-in [:buildingSite :area] 10)
      (assoc-in [:buildingSite :statusOfPlan] (get ryhti-mappings/kaavatilanne "asemakaava"))
      (assoc-in [:buildingSite :tenure] (get ryhti-mappings/rakennuspaikan-hallintaperuste "oma"))
      (assoc-in [:buildingSite :stormWaterTreatmentType] (get ryhti-mappings/hulevedenkasittely "imeyttaminen"))
      (assoc-in [:buildingSite :buildingSiteAddress 0 :addressName] {:fin "joku katu"})
      (assoc-in [:buildingSite :buildingSiteAddress 0 :postalCode] "33720")
      (assoc-in [:constructionProject :planner 0]
                {:familyName              "PS:Sukunimi"
                 :firstName               "PS:Etunimi"
                 :otherId                 ""
                 :personalIdentityCode    "010106A923S"
                 :plannerCompetence       (:B ryhti-mappings/vaativuusluokat)
                 :plannerKey              asiointi-test/uuid-string?
                 :plannerRole             (get ryhti-mappings/suunnittelijanrooli "pääsuunnittelija")
                 :requiredCompetence      (:A ryhti-mappings/vaativuusluokat)
                 :responsibilityEndDate   "2049-12-31"
                 :responsibilityStartDate "2030-01-01"})
      (assoc-in [:constructionProject :planner 1]
                {:familyName              "S:Sukunimi"
                 :firstName               "S:Etunimi"
                 :otherId                 ""
                 :personalIdentityCode    "010106A923S"
                 :plannerCompetence       (:B ryhti-mappings/vaativuusluokat)
                 :plannerKey              asiointi-test/uuid-string?
                 :plannerRole             (get ryhti-mappings/suunnittelijanrooli "kantavien rakenteiden suunnittelija")
                 :requiredCompetence      (:A ryhti-mappings/vaativuusluokat)
                 :responsibilityEndDate   "2049-12-31"
                 :responsibilityStartDate "2030-01-01"})
      (assoc-in [:constructionAction 0 :businessConstruction] true)
      (assoc-in [:constructionAction 0 :structure]
                {:address                            [{:addressKey    asiointi-test/uuid-string?
                                                       :addressName   {:fin "joku katu"}
                                                       :addressNumber 1
                                                       :location      {:geometry
                                                                       {:coordinates [444444.0 6666666.0]
                                                                        :type        "Point"}
                                                                       :srid "3067"}
                                                       :postalCode    "33720"}]
                 :administrativeLocationUnit         {:administrativeLocationUnitKey asiointi-test/uuid-string?
                                                      :propertyIdentifier            "75342300120001"}
                 :bikespaceCount                     3
                 :buildingObjectOwner                [{:buildingObjectOwnerKey asiointi-test/uuid-string?
                                                       :differentOwner         false
                                                       :ownerOperator          {:businessId           ""
                                                                                :familyName           "RO:Sukunimi"
                                                                                :firstName            "RO:Etunimi"
                                                                                :isPerson             true
                                                                                :operatorAddress      [{:addressKey         asiointi-test/uuid-string?
                                                                                                        :contactAddressType "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/01"
                                                                                                        :countryCode        "http://uri.suomi.fi/codelist/jhs/valtio_1_20120101/code/246"
                                                                                                        :postalAddress      {:fin "Testikatu 12"}
                                                                                                        :postalCode         "33720"
                                                                                                        :source             "http://uri.suomi.fi/codelist/rytj/tietolahde/code/01"}]
                                                                                :organizationName     ""
                                                                                :otherId              ""
                                                                                :personalIdentityCode "010106A923S"}
                                                       :ownerType              (get ryhti-mappings/omistajalaji "kiinteistö oy")}]
                 :buildingObjectType                 "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
                 :chargingPoint                      {:chargingPointCount 2
                                                      :chargingPointKey   asiointi-test/uuid-string?
                                                      :conduit            true
                                                      :otherCablingRoutes true
                                                      :preCabling         true}
                 :decisionAdministrativeLocationUnit {:administrativeLocationUnitKey asiointi-test/uuid-string?
                                                      :propertyIdentifier            "75342300120001"}
                 :description                        {:fin "Hervannan hienoin hyppyrimäki"}
                 :location                           {:buildingObjectLocationDataKey asiointi-test/uuid-string?
                                                      :pointLocation                 {:geometry {:coordinates [444444.0 6666666.0]
                                                                                                 :type        "Point"}
                                                                                      :srid     "3067"}
                                                      :votingDistrictNumber          ""}
                 :name                               {:fin "Hervannan hyppyrimäki"}
                 :numberOfStoreys                    13
                 :permanentStructureIdentifier       ""
                 :structureKey                       asiointi-test/uuid-string?
                 :structureMainPurpose               (get ryhti-mappings/rakennelman-kayttotarkoitus "Hyppyrimäki")
                 :structureSection                   [{:buildingServicesEngineeringData {:householdWaterType (:oma-kaivo ryhti-mappings/talousvesityyppi)
                                                                                         :networkConnection  [{:networkConnectionKey  asiointi-test/uuid-string?
                                                                                                               :networkConnectionType (:kaasu ryhti-mappings/verkostoliittymat)}]
                                                                                         :sewerageMethodType (:verkostoliittyma ryhti-mappings/jatevesienkasittely)
                                                                                         :ventilationMethod  [{:isPrimary             true
                                                                                                               :ventilationMethodKey  asiointi-test/uuid-string?
                                                                                                               :ventilationMethodType (:painovoimainen-ilmanvaihto ryhti-mappings/ilmanvaihtotavan-laji)}]}
                                                       :energyData                      {:coolingEnergySource    [{:coolingEnergySourceKey  asiointi-test/uuid-string?
                                                                                                                   :coolingEnergySourceType (:kompressori-kylmalaitos-ilmalauhdutteinen ryhti-mappings/jaahdytysenergianlahde)
                                                                                                                   :isPrimary               true}]
                                                                                         :coolingMethod          [{:coolingMethodKey  asiointi-test/uuid-string?
                                                                                                                   :coolingMethodType (:ilmajaahdytteinen ryhti-mappings/jaahdytystapa)
                                                                                                                   :isPrimary         true}]
                                                                                         :electricalEnergySource [{:electricalEnergySourceKey  asiointi-test/uuid-string?
                                                                                                                   :electricalEnergySourceType (:sahkoliittyma ryhti-mappings/sahkoenergianlahde)
                                                                                                                   :isPrimary                  true}]
                                                                                         :energyDataKey          asiointi-test/uuid-string?
                                                                                         :energyEfficiencyRating (:A ryhti-mappings/energiatehokkuusluokka)
                                                                                         :energyRatingSubindex   (:2018 ryhti-mappings/energialuokka-alaindeksi)
                                                                                         :heatingEnergySource    []
                                                                                         :heatingMethod          [{:heatingMethodKey  asiointi-test/uuid-string?
                                                                                                                   :heatingMethodType (:vesikeskus ryhti-mappings/lammitystapa)
                                                                                                                   :isPrimary         true}]}
                                                       :equipment                       [{:equipmentCount 1
                                                                                          :equipmentKey   asiointi-test/uuid-string?
                                                                                          :equipmentType  (:aurinkopaneeli ryhti-mappings/rakennusvaruste)}]
                                                       :exteriorData                    {:grossFloorArea        11
                                                                                         :height                14
                                                                                         :numberOfStoreys       13
                                                                                         :permittedRidgeHeight  16.0
                                                                                         :permittedVolume       15
                                                                                         :relationToGroundLevel (:kokonaan-pinnan-ylla ryhti-mappings/suhde-pintaan)
                                                                                         :totalArea             12
                                                                                         :volume                10}
                                                       :lifeCycleState                  "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/01"
                                                       :materialData                    {:buildingMaterialOfLoadBearingStructures [{:buildingMaterialOfLoadBearingStructuresKey  asiointi-test/uuid-string?
                                                                                                                                    :buildingMaterialOfLoadBearingStructuresType (:betoni ryhti-mappings/kantava-rakennusaine)
                                                                                                                                    :isPrimary                                   true}]
                                                                                         :constructionMethod                      (:elementti ryhti-mappings/rakentamistapa)
                                                                                         :facadeMaterial                          [{:facadeMaterialKey  asiointi-test/uuid-string?
                                                                                                                                    :facadeMaterialType (:betoni ryhti-mappings/julkisivu)
                                                                                                                                    :isPrimary          true}]
                                                                                         :wideBodiedBuilding                      true}
                                                       :partitionReason                 "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/1"
                                                       :structurePurpose                (get ryhti-mappings/rakennelman-kayttotarkoitus "Hyppyrimäki")
                                                       :structureSectionKey             asiointi-test/uuid-string?}]
                 :temporary                          true})))

(defn- attachment-matcher
  [options]
  (-> (filled-matcher options)
      (assoc-in [:attachment 0] {:accessibility         false
                                 :attachmentDocumentKey asiointi-test/uuid-string?
                                 :attachmentType        (:tutkintotodistus ryhti-mappings/asiakirjan-laji)
                                 :categoryOfPublicity   (:salassapidettava ryhti-mappings/asiakirjan-julkisuusluokka)
                                 :confirmationDate      asiointi-test/date-string?
                                 :documentDate          asiointi-test/date-string?
                                 :documentIdentifier    "CV-123"
                                 :fileKey               asiointi-test/uuid-string?
                                 :languages             ["http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/fi"]
                                 :name                  {:fin "Täs olis minun CV"}
                                 :personalDataContent   "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/1"
                                 :retentionTime         "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0205"})))

(defn- submitted-matcher
  [options]
  (-> (attachment-matcher options)
      (assoc :dateOfInitiation asiointi-test/date-string?)
      (assoc :lifeCycleState (:vireilla ryhti-mappings/rakvalv-asian-elinkaaren-tila))
      (assoc-in [:buildingPermitApplication 0 :dateOfReception] asiointi-test/date-string?)
      (assoc-in [:buildingPermitApplication 0 :lifeCycleState] "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2")))

(defn- issued-matcher
  [options]
  (let [verdict-attachment {:accessibility         false
                            :attachmentDocumentKey asiointi-test/uuid-string?
                            :attachmentType        (:paatos ryhti-mappings/asiakirjan-laji)
                            :categoryOfPublicity   (:julkinen ryhti-mappings/asiakirjan-julkisuusluokka)
                            :confirmationDate      asiointi-test/date-string?
                            :documentDate          asiointi-test/date-string?
                            :documentIdentifier    ""
                            :fileKey               asiointi-test/uuid-string?
                            :languages             ["http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/fi"]
                            :name                  {:fin "Päätös"}
                            :personalDataContent   "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/1"
                            :retentionTime         "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0205"}]
    (-> (submitted-matcher options)
        (assoc :lifeCycleState (:paatos-annettu ryhti-mappings/rakvalv-asian-elinkaaren-tila))
        (assoc :decision {:buildingPermitDecisionKey   asiointi-test/uuid-string?
                          :constructionToBeCompletedBy "2027-01-06"
                          :constructionToBeStartedBy   "2027-01-05"
                          :dateOfDecision              "2027-01-01"
                          :dateOfValidityOfDecision    "2027-01-04"
                          :decisionDate                "2027-01-01"
                          :decisionDocument            verdict-attachment
                          :decisionMakerFirstName      "Gi"
                          :decisionMakerName           {:fin "Ver"}
                          :decisionMakerType           "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/01"
                          :decisionText                {:fin "Täs olis lupa sulle."}
                          :engagingParty               [{:applicantContactOperator {:businessId           ""
                                                                                    :familyName           "H:Sukunimi"
                                                                                    :firstName            "H:Etunimi"
                                                                                    :isPerson             true
                                                                                    :operatorAddress      [{:addressKey         asiointi-test/uuid-string?
                                                                                                            :contactAddressType "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/01"
                                                                                                            :countryCode        "http://uri.suomi.fi/codelist/jhs/valtio_1_20120101/code/246"
                                                                                                            :postalAddress      {:fin "Testikatu 12"}
                                                                                                            :postalCode         "33720"
                                                                                                            :source             "http://uri.suomi.fi/codelist/rytj/tietolahde/code/01"}]
                                                                                    :organizationName     ""
                                                                                    :otherId              ""
                                                                                    :personalIdentityCode "010106A923S"}
                                                         :businessId               ""
                                                         :engagingPartyKey         asiointi-test/uuid-string?
                                                         :familyName               "H:Sukunimi"
                                                         :firstName                "H:Etunimi"
                                                         :isPerson                 true
                                                         :organizationName         ""
                                                         :otherId                  ""
                                                         :personalIdentityCode     "010106A923S"}]
                          :lifeCycleState              "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
                          :permitApplicationType       "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01"
                          :publicNoticeDate            "2027-01-02"
                          :relatedApplication          nil})
        (assoc-in [:attachment 1] verdict-attachment)
        (assoc-in [:attachment 2] {:accessibility         false
                                   :attachmentDocumentKey asiointi-test/uuid-string?
                                   ;; This is the kuulutettava asiakirja
                                   :attachmentType        (:muu ryhti-mappings/asiakirjan-laji)
                                   :categoryOfPublicity   (:julkinen ryhti-mappings/asiakirjan-julkisuusluokka)
                                   :confirmationDate      asiointi-test/date-string?
                                   :documentDate          asiointi-test/date-string?
                                   :documentIdentifier    ""
                                   :fileKey               asiointi-test/uuid-string?
                                   :languages             ["http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/fi"]
                                   :name                  {:fin "Päätös"}
                                   :personalDataContent   "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/1"
                                   :retentionTime         "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0205"}))))

(defn- foreman-matcher
  [options]
  (-> (issued-matcher options)
      (assoc-in [:constructionProject :foreman 0] {:buildingReference       []
                                                   :familyName              "TJ:Sukunimi"
                                                   :firstName               "TJ:Etunimi"
                                                   :foremanCompetence       (:A ryhti-mappings/vaativuusluokat)
                                                   :foremanKey              asiointi-test/uuid-string?
                                                   :foremanRole             (get ryhti-mappings/tyonjohtajan-rooli "työnjohtaja")
                                                   :otherId                 ""
                                                   :personalIdentityCode    "010106A923S"
                                                   :requiredCompetence      "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/3"
                                                   :responsibilityStartDate asiointi-test/date-string? ;; seems incorrect..
                                                   :specificAreaReference   []
                                                   :structureReference      []})))

(deftest read-application-test
  (let [organization-id "753-R"]
    (testing "configure target organization"
      ;; Make sure target organization generates Ryhti-applications
      (is (:ok (command admin :set-organization-ryhti-data-2026-start-ts
                        {:organizationId           organization-id
                         :ryhti-data-2026-start-ts 0})))
      ;; Make sure the target organization uses fresh KuntaGML for schemas
      (is (:ok (command admin :set-organization-permit-type-krysp-version
                        {:org-id     organization-id
                         :permitType "R"
                         :version    "2.2.6"})))
      ;; Make sure the target organization is configured to use the Ryhti API
      (is (:ok (command admin :set-ryhti-api-configuration
                        {:organizationId organization-id
                         :ryhtiConfig    [{:municipality  "753"
                                           :client-id     "42"
                                           :client-secret "hunter2"}]}))))
    (testing "add user attachments to applicant"
      (let [body (itest/upload-user-attachment applicant
                                               "osapuolet.tutkintotodistus"
                                               true)]
        (is (:ok body))))
    (let [application-id (:id (itest/create-application applicant))
          app            (let [resp (query applicant :application :id application-id)]
                           (is (:ok resp))
                           (:application resp))
          permit-id      (-> app :ryhti :permanentPermitIdentifier)
          context        {:username        "sipoo_asiointiapi"
                          :password        "sipoo"
                          :organization-id organization-id
                          :permit-id       permit-id}]
      (is (string? permit-id))

      (testing "read freshly created application"
        (is (match? (m/nested-equals (created-matcher {:permit-id permit-id}))
                    (query-application context))))

      (testing "fill-in documents before submitting"
        (let [documents          (:documents app)
              document->name     (fn [doc] (-> doc :schema-info :name))
              actual-by-name     (group-by document->name documents)
              expected-doc-names (->> pre-verdict-documents keys set)
              actual-doc-names   (->> actual-by-name keys set)]
          (is (= actual-doc-names expected-doc-names))
          (doseq [[doc-name doc-data] pre-verdict-documents]
            (let [{doc-id :id} (first (get actual-by-name doc-name))]
              (when (seq doc-data)
                (is (:ok (command applicant :update-doc
                                  {:id      application-id
                                   :doc     doc-id
                                   :updates (vec doc-data)}))))))))

      (testing "read filled-in application"
        (is (match? (m/nested-equals (filled-matcher {:permit-id permit-id}))
                    (query-application context))))

      (testing "copy applicant attachments to application"
        ;; `:firstName` and `:lastName` are mandatory, even though irrelevant for our purposes
        (is (:ok (command applicant :update-user {:firstName "Pena"
                                                  :lastName  "Panaani"
                                                  :architect true})))
        (is (:ok (command applicant :copy-user-attachments-to-application {:id (:id app)})))
        (let [{:keys [attachments]}              (let [resp (query applicant :application :id application-id)]
                                                   (is (:ok resp))
                                                   (:application resp))
              {:keys [id] :as tutkinto-todistus} (util/find-by-key :type
                                                                   {:type-id    "tutkintotodistus"
                                                                    :type-group "osapuolet"}
                                                                   attachments)]
          (is tutkinto-todistus)
          (testing "add some details to the CV"
            (is (:ok (command applicant :set-attachment-meta {:id           (:id app)
                                                              :attachmentId id
                                                              :meta         {:documentIdentifier "CV-123"
                                                                             :contents           "Täs olis minun CV"}})))
            (is (:ok (command applicant :set-attachment-visibility {:id           (:id app)
                                                                    :attachmentId id
                                                                    :value        "viranomainen"}))))))

      (testing "read application with attachments"
        (is (match? (m/nested-equals (attachment-matcher {:permit-id permit-id}))
                    (query-application context))))

      (testing "check that API returns constantly the same data"
        ;; This will fail if e.g. the LP->Ryhti conversion generates random UUIDs (`missing-uuid`)
        (is (= (query-application context)
               (query-application context))))

      (testing "submit and read again"
        (is (:ok (command applicant :submit-application {:id application-id})))
        (is (match? (m/nested-equals (submitted-matcher {:permit-id permit-id}))
                    (query-application context))))

      (testing "approve and read again"
        (is (:ok (command authority :update-app-bulletin-op-description {:id          application-id
                                                                         :description "toimenpide"})))
        (is (:ok (command authority :approve-application {:id   application-id
                                                          :lang "fi"})))
        (is (match? (m/nested-equals (submitted-matcher {:permit-id permit-id}))
                    (query-application context))))

      (testing "create verdict draft"
        (let [verdict-id   (let [resp (command authority :new-pate-verdict-draft
                                               {:id          application-id
                                                :template-id template-id})]
                             (is (:ok resp))
                             (:verdict-id resp))
              verdict-date (->  "1.1.2027" date/finnish-date date/timestamp)]
          (fill-verdict authority application-id verdict-id
                        :bulletin-op-description "Changed pate bulletin description"
                        :giver-first-name "Gi"
                        :giver-last-name "Ver"
                        :k-kuulutus (-> verdict-date (date/plus :days 1) date/timestamp)
                        :k-muutoksenhaku (-> verdict-date (date/plus :days 2) date/timestamp)
                        :k-lainvoimainen (-> verdict-date (date/plus :days 3) date/timestamp)
                        :k-aloitettava (-> verdict-date (date/plus :days 4) date/timestamp)
                        :k-voimassa (-> verdict-date (date/plus :days 5) date/timestamp)
                        :verdict-code "hyvaksytty"
                        :verdict-date verdict-date
                        :verdict-text "Täs olis lupa sulle.")

          ;; Nothing changes yet, as draft verdicts don't appear in the API
          ;; (because `:buildingPermitDecisionKey` is assigned when the verdict is issued)
          (is (match? (m/nested-equals (submitted-matcher {:permit-id permit-id}))
                      (query-application context)))

          (testing "issue verdict"
            ;; Takes a moment as we publish the PDFs..
            (is (:ok (command authority :publish-pate-verdict {:id         application-id
                                                               :verdict-id verdict-id})))

            (is (match? (m/nested-equals (issued-matcher {:permit-id permit-id}))
                        (query-application context))))))

      ;; Issuing the verdict also creates a foreman form
      (testing "fill foreman documents"
        (let [{:keys [documents]} (let [resp (query applicant :application :id application-id)]
                                    (is (:ok resp))
                                    (:application resp))
              document->name      (fn [doc] (-> doc :schema-info :name))
              actual-by-name      (group-by document->name documents)
              actual-doc-names    (->> actual-by-name keys set)
              already-filled      (->> pre-verdict-documents keys set)
              post-verdict        (->> post-verdict-documents keys set)]
          (is (= post-verdict
                 (set/difference actual-doc-names already-filled)))
          (doseq [[doc-name doc-data] post-verdict-documents]
            (let [{doc-id :id} (first (get actual-by-name doc-name))]
              (when (seq doc-data)
                (is (:ok (command applicant :update-doc
                                  {:id      application-id
                                   :doc     doc-id
                                   :updates (vec doc-data)}))))))
          (is (match? (m/nested-equals (foreman-matcher {:permit-id permit-id}))
                      (query-application context)))))

      (testing "give foreman consent"
        (let [doc-id (let [resp (query applicant :application :id application-id)]
                       (is (:ok resp))
                       (-> resp
                           :application
                           (domain/get-document-by-name "tyonjohtaja-v3")
                           :id))]
          (is doc-id)
          (is (:ok (command applicant :foreman-consent {:id             application-id
                                                        :foreman-doc-id doc-id
                                                        :consent        true})))

          ;; TODO: shouldn't this change the response? :shrug:
          (is (match? (m/nested-equals (foreman-matcher {:permit-id permit-id}))
                      (query-application context)))))

      (testing "check that API returns constantly the same data"
        ;; This will fail if e.g. the LP->Ryhti conversion generates random UUIDs (`missing-uuid`)
        (is (= (query-application context)
               (query-application context)))))))
