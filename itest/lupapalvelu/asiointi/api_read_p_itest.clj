(ns lupapalvelu.asiointi.api-read-p-itest
  "Create a P application and read result from AsiointiAPI"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.asiointi.test-data :as test-data]
            [lupapalvelu.asiointi.test-utils :as asiointi-test]
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
  "650d58aa13d3006be5aaa451")

(def applicant itest/pena)
(def authority itest/sonja)

(def ^:private query-application (asiointi-test/query-application :deviation-permit))

(def ^:private pre-verdict-documents
  {"hakija"                            test-data/hakija
   "hankkeen-kuvaus"                   test-data/hankkeen-kuvaus
   "maksaja"                           test-data/maksaja
   "paatoksen-toimitus-rakval"         test-data/paatoksen-toimitus-rakval
   "poikkeusasian-rakennuspaikka"      test-data/poikkeusasian-rakennuspaikka
   "rakennushanke"                     test-data/rakennushanke
   "suunnittelutarveratkaisun-lisaosa" test-data/suunnittelutarveratkaisun-lisaosa})

(defn- created-matcher
  [{:keys [permit-id]}]
  {:attachment                 []
   :buildingSite               {:buildingSiteAddress     [{:addressKey    asiointi-test/uuid-string?
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
   :dateOfInitiation           nil
   :deviationObject            []
   :deviationPermitApplication {:applicationContent            ""
                                :constructionDesign            []
                                :dateOfReception               nil
                                :deviationPermitApplicationKey asiointi-test/uuid-string?
                                :lifeCycleState                "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1"}
   :deviationPermitDecision    nil
   :lifeCycleState             (:ennakkoneuvottelu-tai-valmistelu ryhti-mappings/rakvalv-asian-elinkaaren-tila)
   :municipalityNumber         "753"
   :permanentPermitIdentifier  permit-id})

(defn- filled-matcher
  [options]
  (-> (created-matcher options)
      (assoc-in [:buildingSite :statusOfPlan] (get ryhti-mappings/kaavatilanne "asemakaava"))
      (assoc-in [:buildingSite :tenure] (get ryhti-mappings/rakennuspaikan-hallintaperuste "oma"))
      (assoc-in [:buildingSite :stormWaterTreatmentType] (get ryhti-mappings/hulevedenkasittely "imeyttaminen"))
      (assoc-in [:buildingSite :buildingSiteAddress 0 :addressName] {:fin "joku katu"})
      (assoc-in [:buildingSite :buildingSiteAddress 0 :postalCode] "33720")
      (assoc-in [:deviationPermitApplication :applicationContent] "tehdään jotain")
      (assoc-in [:deviationPermitApplication :callForDeviation] [{:callForDeviationKey asiointi-test/uuid-string?
                                                                  :contentOfDeviation  {:fin "en kerro"}
                                                                  :deviationType       "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/13"}])))

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
      (assoc-in [:deviationPermitApplication :dateOfReception] asiointi-test/date-string?)
      (assoc-in [:deviationPermitApplication :lifeCycleState] "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2")))

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
        (assoc :deviationPermitDecision {:dateOfDecision             "2027-01-01"
                                         :dateOfValidityOfDecision   nil
                                         :decisionDate               "2027-01-01"
                                         :decisionDocument           verdict-attachment
                                         :decisionInForceUntil       ""
                                         :decisionMakerFirstName     nil
                                         :decisionMakerName          {:fin "Lautakunta"}
                                         :decisionMakerType          "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/02"
                                         :decisionText               {:fin "Joo, menköön nyt tämän kerran."}
                                         :deviationPermitDecisionKey asiointi-test/uuid-string?
                                         :engagingParty              [{:applicantContactOperator {:businessId           ""
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
                                         :lifeCycleState             "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
                                         :publicNoticeDate           "2027-01-02"
                                         :relatedApplication         nil})
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
                                           :client-secret "hunter2"}]})))
      ;; Make sure PATE & bulletins are enabled
      (is (:ok (command admin :update-organization {:permitType       "P"
                                                    :municipality     "753"
                                                    :pateEnabled      true
                                                    :bulletinsEnabled true}))))
    (testing "add user attachments to applicant"
      (let [body (itest/upload-user-attachment applicant
                                               "osapuolet.tutkintotodistus"
                                               true)]
        (is (:ok body))))
    (let [application-id (:id (itest/create-application applicant :operation "poikkeamis"))
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
                        :giver "päättäjä"
                        :k-kuulutus (-> verdict-date (date/plus :days 1) date/timestamp)
                        :verdict-flag true
                        :verdict-section "3"
                        :verdict-code "hyvaksytty"
                        :verdict-date verdict-date
                        :verdict-text "Joo, menköön nyt tämän kerran.")

          ;; Nothing changes yet, as draft verdicts don't appear in the API
          ;; (because `:buildingPermitDecisionKey` is assigned when the verdict is issued)
          (is (match? (m/nested-equals (submitted-matcher {:permit-id permit-id}))
                      (query-application context)))

          (testing "issue verdict"
            ;; Takes a moment as we publish the PDFs..
            (is (:ok (command authority :publish-pate-verdict {:id         application-id
                                                               :verdict-id verdict-id})))

            (is (match? (m/nested-equals (issued-matcher {:permit-id permit-id}))
                        (query-application context)))))))))
