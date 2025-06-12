(ns lupapalvelu.ryhti.api.datamodel-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ryhti.api.datamodel-test-util :as test-data]
            [lupapalvelu.ryhti.api.mappings :as mappings]
            [lupapalvelu.ryhti.api.validation :as validation]
            [lupapalvelu.ryhti.datamodel.application :refer [building-permit-application-mapping]]
            [lupapalvelu.ryhti.datamodel.building :as datamodel.building]
            [lupapalvelu.ryhti.datamodel.common :as d]
            [lupapalvelu.ryhti.datamodel.decision :refer [building-permit-decision-mapping]]
            [lupapalvelu.ryhti.datamodel.issue :refer [building-permit-issue-mapping]]
            [malli.core :as m]
            [matcher-combinators.test :refer [match?]]
            [sade.date :as date]))

(deftest unicode-trim-test
  (is (= "A" (d/unicode-trim " A ")))
  (is (= "A" (d/unicode-trim " A ")))
  (is (= "A" (d/unicode-trim "	  A  	"))))

(defn random-uuid?
  "Temporary random-uuids are used for values where we are still missing real Ryhti ID"
  [s]
  (and (string? s)
       (some? (parse-uuid s))))

(defn any-date?
  [s]
  (and (string? s)
       (date/zoned-parse s)))

(def ^:private asiakirjan-lajit-urls (set (vals mappings/asiakirjan-laji)))

(defn any-asiakirjan-laji?
  [s]
  (and (string? s)
       (contains? asiakirjan-lajit-urls s)))

(def ^:private asiakirjan-julkisuusluokka-urls (set (vals mappings/asiakirjan-julkisuusluokka)))

(defn any-asiakirjan-julkisuusluokka?
  [s]
  (and (string? s)
       (contains? asiakirjan-julkisuusluokka-urls s)))

(defn test-mapping [m ctx]
  (d/apply-mapping m (merge {:from :lupapiste
                             :to :ryhti
                             :transform :->ryhti
                             :->file-id (constantly 1)}
                            ctx)))

(deftest building-permit-application-state-test
  (testing "Peruutettu"
    (is (match? {:lifeCycleState "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/3"}
                (test-mapping building-permit-application-mapping {:application {:state "canceled"}}))))
  (testing "Jätetty"
    (is (match? {:lifeCycleState "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2"}
                (test-mapping building-permit-application-mapping {:application {:state "sent"}}))))
  (testing "Luonnos"
    (is (match? {:lifeCycleState "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1"}
                (test-mapping building-permit-application-mapping {:application {:state "open"}}))))

  (testing "tyyppi"
    (testing "Muutoslupahakemus & Toteuttamislupa"
      (is (match? {:buildingPermitApplicationType "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/05"
                   :permitApplicationType "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"}
                  (test-mapping building-permit-application-mapping {:application {:permitSubtype "muutoslupa"}}))))

    (testing "Rakentamislupahakemus & Sijoittamislupa"
      (is (match? {:buildingPermitApplicationType "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/01"
                   :permitApplicationType "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/02"}
                  (test-mapping building-permit-application-mapping {:application {:permitSubtype "sijoituslupa"}}))))))

(deftest application-state-test
  (testing "Luonnos"
    (is (match? {:lifeCycleState (:ennakkoneuvottelu-tai-valmistelu mappings/rakvalv-asian-elinkaaren-tila)}
                (test-mapping building-permit-issue-mapping {:application {:state "draft"}}))))

  (testing "Päätös annettu"
    (is (match? {:lifeCycleState (:paatos-annettu mappings/rakvalv-asian-elinkaaren-tila)}
                (test-mapping building-permit-issue-mapping {:application {:state "verdictGiven"}}))))

  (testing "Vireillä"
    (is (match? {:lifeCycleState (:vireilla mappings/rakvalv-asian-elinkaaren-tila)}
                (test-mapping building-permit-issue-mapping {:application {:state "submitted"}}))))

  (testing "Peruutettu"
    (is (match? {:lifeCycleState (:kasittely-keskeytetty mappings/rakvalv-asian-elinkaaren-tila)}
                (test-mapping building-permit-issue-mapping {:application {:state "canceled"}})))))

(deftest ryhti-uusi-rakennuskohde-app-with-etvra-test
  (let [ryhti-permit-application (validation/application->ryhti
                                   {}
                                   test-data/ryhti-uusi-rakennuskohde-app-with-etvra
                                   {:tempFileId (str (random-uuid))})]
    (is (match? {:locationPermitObject [],
                 :extensionDecision [],
                 :dateOfInitiation "2012-12-03",
                 :lifeCycleState "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/4",
                 :permanentPermitIdentifier "100000042-R",
                 :buildingPermitApplication [{:permitApplicationKey test-data/permit-application-key,
                                              :lifeCycleState "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2",
                                              :applicationContent "Uuden rakennuksen rakentaminen tontille.",
                                              :dateOfReception "2012-12-03",
                                              :callForDeviation [{:callForDeviationKey test-data/deviation-key
                                                                  :deviationType "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/13",
                                                                  :contentOfDeviation {:fin "Ei poikkeamisia"}}],
                                              :buildingPermitApplicationType "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/01",
                                              :permitApplicationType "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"}],
                 :constructionAction [{:descriptionOfAction {:fin "Asuinkerrostalon tai rivitalon rakentaminen"},
                                       :businessConstruction true,
                                       :renovation false,
                                       :statusOfConstructionAction "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/1",
                                       :areaToBeBuiltForSpecificActivities {:description {:fin "Uusi rakennuskohde kuvaus"},
                                                                            :address [{:addressKey test-data/rakennuskohde-address-key,
                                                                                       :addressName {:fin "Testitie 1"},
                                                                                       :postalCode "33800",
                                                                                       :addressNumber 1,
                                                                                       :location {:geometry {:type "Point", :coordinates [408048.0 6693225.0]},
                                                                                                  :srid "3067"}}],
                                                                            :specificActivitiesAreaType "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/01",
                                                                            :name {:fin "Kivimäki"},
                                                                            :areaToBeBuiltForSpecificActivitiesKey test-data/etvra-key,
                                                                            :administrativeLocationUnit {:administrativeLocationUnitKey test-data/admistrative-location-unit-key,
                                                                                                         :propertyIdentifier test-data/property-id},
                                                                            :specificActivitiesAreaArea 1000,
                                                                            :decisionAdministrativeLocationUnit {:administrativeLocationUnitKey test-data/admistrative-location-unit-key,
                                                                                                                 :propertyIdentifier test-data/property-id},
                                                                            :buildingObjectType "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1",
                                                                            :location {:buildingObjectLocationDataKey test-data/building-object-location-data-key,
                                                                                       :votingDistrictNumber "",
                                                                                       :pointLocation {:geometry {:type "Point", :coordinates [408048.0 6693225.0]},
                                                                                                       :srid "3067"}},
                                                                            :temporary true,
                                                                            :buildingObjectOwner []},
                                       :constructionActionKey test-data/construction-action-key
                                       :constructionActionType "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/01"}],
                 :constructionProject {:constructionProjectKey test-data/construction-project-key,
                                       :planner [{:requiredCompetence "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/3",
                                                  :firstName "Pena",
                                                  :plannerKey test-data/suunnittelija1-key,
                                                  :responsibilityStartDate "2026-01-16",
                                                  :familyName "Penttilä",
                                                  :personalIdentityCode "210281-9988",
                                                  :plannerRole "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli/code/02",
                                                  :plannerCompetence "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/1"}],
                                       :foreman [{:requiredCompetence "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/3",
                                                  :structureReference [],
                                                  :foremanKey test-data/tyonjohtaja-key,
                                                  :firstName "Pena",
                                                  :responsibilityStartDate any-date?,
                                                  :familyName "Penttilä",
                                                  :personalIdentityCode "210281-9988",
                                                  :foremanRole "http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli/code/01",
                                                  :specificAreaReference [],
                                                  :buildingReference [],
                                                  :foremanCompetence "http://uri.suomi.fi/codelist/rytj/vaativuusluokat/code/3"}]},
                 :decision {:publicNoticeDate any-date?,
                            :constructionToBeStartedBy any-date?,
                            :engagingParty [{:applicantContactOperator {:isPerson true,
                                                                        :operatorAddress [{:addressKey random-uuid?,
                                                                                           :postalCode "33800",
                                                                                           :countryCode "http://uri.suomi.fi/codelist/jhs/valtio_1_20120101/code/246",
                                                                                           :postalAddress {:fin "katu"},
                                                                                           :contactAddressType "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/01",
                                                                                           :source "http://uri.suomi.fi/codelist/rytj/tietolahde/code/01"}],
                                                                        :personalIdentityCode "210281-9988",
                                                                        :firstName "Pena",
                                                                        :familyName "Penttilä"},
                                             :engagingPartyKey test-data/hakija-key,
                                             :isPerson true,
                                             :firstName "Pena",
                                             :familyName "Penttilä",
                                             :personalIdentityCode "210281-9988"}],
                            :decisionMakerType "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/01",
                            :decisionDocument {:documentDate any-date?,
                                               :retentionTime "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0205",
                                               :documentIdentifier string?,
                                               :accessibility false,
                                               :name {:fin string?},
                                               :confirmationDate any-date?,
                                               :attachmentType any-asiakirjan-laji?,
                                               :fileKey random-uuid?,
                                               :attachmentDocumentKey random-uuid?,
                                               :personalDataContent "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/1",
                                               :languages ["http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/fi"],
                                               :categoryOfPublicity any-asiakirjan-julkisuusluokka?},
                            :constructionToBeCompletedBy any-date?,
                            :decisionText {:fin "Päätös"},
                            :lifeCycleState "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11",
                            :buildingPermitDecisionKey test-data/decision-key,
                            :decisionMakerName {:fin "Päättäjä"},
                            :decisionMakerFirstName "Pertti",
                            :decisionDate any-date?,
                            :permitApplicationType "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01",
                            :dateOfValidityOfDecision any-date?,
                            :dateOfDecision any-date?,
                            :relatedApplication nil},
                 :buildingSite {:stormWaterTreatmentType nil,
                                :name {:fin "Katutie 54"},
                                :geometry {:geometry {:type "Point", :coordinates [408048.0 6693225.0]},
                                           :srid "3067"},
                                :buildingSiteAddress [{:addressKey test-data/rakennuskohde-address-key,
                                                       :addressName {:fin "Testitie 1"},
                                                       :postalCode "33800",
                                                       :addressNumber 1,
                                                       :location {:geometry {:type "Point", :coordinates [408048.0 6693225.0]},
                                                                  :srid "3067"}}],
                                :buildingSiteKey test-data/building-site-key,
                                :area 1000,
                                :tenure "http://uri.suomi.fi/codelist/rytj/RakennuspaikanHallintaperuste/code/1",
                                :statusOfPlan "http://uri.suomi.fi/codelist/vtj/Rake_kaava/code/07"},
                 :attachment [{:documentDate any-date?,
                               :retentionTime "http://uri.suomi.fi/codelist/rytj/sailytysaika/code/0205",
                               :documentIdentifier string?,
                               :accessibility false,
                               :name {:fin string?},
                               :confirmationDate any-date?,
                               :attachmentType any-asiakirjan-laji?,
                               :fileKey test-data/attachment-file-key
                               :attachmentDocumentKey test-data/attachment-key
                               :personalDataContent "http://uri.suomi.fi/codelist/rytj/henkilotietosisalto/code/1",
                               :languages ["http://uri.suomi.fi/codelist/rytj/ryhtikielet/code/fi"],
                               :categoryOfPublicity any-asiakirjan-julkisuusluokka?}],
                 :municipalityNumber "753",
                 :changePermit []}
                ryhti-permit-application))

    ; (is (true? (validation/malli-validate ryhti-permit-application)))

    ;; Maybe just assert empty?
    ;; Right now this is probably useful for me to check which errors to fix
    (is (empty? (->> (m/explain validation/RootSchema ryhti-permit-application)
                     (:errors)
                     (map (fn [error] (dissoc error :schema))))))))

(deftest round-trip-test
  ;; Testing an idea that the mapping could be used to run transformations
  ;; to both directions. In more complex cases this doesn't really work,
  ;; but there is some support for this.
  (let [osoite {:ryhti {:addressKey {:value "1"}}
                :katu {:value "Testitie 1"}
                :postinumero {:value "00100"}}
        building-site-address {:addressKey "1"
                               :addressName {:fin "Testitie 1"}
                               :postalCode "00100"
                               :addressNumber 1
                               :location {:geometry {:type "Point"
                                                     :coordinates [1.0 2.0]}
                                          :srid "3067"}}
        to-ryhti (fn [osoite]
                   (d/apply-mapping datamodel.building/address-mapping
                                    {:osoite osoite
                                     :application {:location [1.0 2.0]}
                                     :from :lupapiste
                                     :to :ryhti
                                     :transform :->ryhti}))
        to-lupapiste (fn [building-site-address]
                       (d/apply-mapping datamodel.building/address-mapping
                                        ;; NOTE: This is a bit funny detail, the ryhti paths are all referring to the root of the "context"
                                        (merge building-site-address
                                               {:from :ryhti
                                                :to :lupapiste
                                                :transform :->lupapiste})))]
    (is (= building-site-address
           (to-ryhti osoite)))
    (is (= {:osoite osoite
            :application {:location [1.0 2.0]}}
           (to-lupapiste building-site-address)))))

(deftest decision-maker-test
  (testing "Viranhaltija verdict with giver-first-name and giver-last-name"
    (let [paatos {:template {:giver "viranhaltija"}
                  :data     {:giver-first-name "Pertti"
                             :giver-last-name  "Päättäjä"
                             :handler          "Fallback Handler"}}]
      (is (match? {:decisionMakerType      "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/01"
                   :decisionMakerFirstName "Pertti"
                   :decisionMakerName      {:fin "Päättäjä"}}
                  (test-mapping building-permit-decision-mapping {:paatos paatos})))))

  (testing "Viranhaltija verdict with giver as ID reference to verdict-givers"
    (let [giver-id "69d79b5411f26f66ceda22b7"
          paatos   {:template   {:giver "viranhaltija"}
                    :references {:verdict-givers [{:id         giver-id
                                                   :code       "1"
                                                   :first-name "Tep"
                                                   :last-name  "Tulip"}
                                                  {:id         "other-id"
                                                   :code       "2"
                                                   :first-name "Other"
                                                   :last-name  "Person"}]}
                    :data       {:giver            giver-id
                                 :giver-first-name nil
                                 :giver-last-name  nil
                                 :handler          "Fallback Handler"}}]
      (is (match? {:decisionMakerType      "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/01"
                   :decisionMakerFirstName "Tep"
                   :decisionMakerName      {:fin "Tulip"}}
                  (test-mapping building-permit-decision-mapping {:paatos paatos})))))

  (testing "Lautakunta (board) verdict with boardname"
    (let [paatos {:template   {:giver "lautakunta"}
                  :category   "r"
                  :references {:boardname "Rakennuslautakunta"}
                  :data       {:giver            nil
                               :giver-first-name "Should be ignored"
                               :giver-last-name  "Should be ignored"}}]
      (is (match? {:decisionMakerType      "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/02"
                   :decisionMakerFirstName nil
                   :decisionMakerName      {:fin "Rakennuslautakunta"}}
                  (test-mapping building-permit-decision-mapping {:paatos paatos})))))

  (testing "Lautakunta (board) verdict with giver field fallback"
    (let [paatos {:template {:giver "lautakunta"}
                  :category "r"
                  :data     {:giver "Rakennus- ja ympäristölautakunta"}}]
      (is (match? {:decisionMakerType      "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/02"
                   :decisionMakerFirstName nil
                   :decisionMakerName      {:fin "Rakennus- ja ympäristölautakunta"}}
                  (test-mapping building-permit-decision-mapping {:paatos paatos})))))

  (testing "Lautakunta (board) verdict with giver as ID reference to verdict-givers"
    (let [giver-id "69d79b5411f26f66ceda22b8"
          paatos   {:template   {:giver "lautakunta"}
                    :category   "r"
                    :references {:boardname      "Fallback Boardname"
                                 :verdict-givers [{:id giver-id
                                                   :fi "Kaupunkiympäristölautakunta"
                                                   :sv "Stadsplanering"
                                                   :en "City Planning Board"}
                                                  {:id "other-id"
                                                   :fi "Other Board"
                                                   :sv "Annan"
                                                   :en "Other"}]}
                    :data       {:giver giver-id}}]
      (is (match? {:decisionMakerType      "http://uri.suomi.fi/codelist/rytj/PaatoksenTekija/code/02"
                   :decisionMakerFirstName nil
                   :decisionMakerName      {:fin "Kaupunkiympäristölautakunta"}}
                  (test-mapping building-permit-decision-mapping {:paatos paatos}))))))
