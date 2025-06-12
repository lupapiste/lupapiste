(ns lupapalvelu.ryhti.api.malli-schema.generated
  "Automatically generated on 2026-05-28T11:14:13+03:00, do not modify manually"
  (:require [lupapalvelu.ryhti.api.mappings :as mappings]))

(def BuildingProductListNoPersonData
  [:map
   {:closed true}
   [:preparationDate {:optional true} :string]
   [:buildingSiteBuildingProduct
    {:optional true}
    [:vector [:ref :ryhti/BuildingSiteBuildingProduct]]]
   [:buildingProductListKey {:optional true} :string]
   [:buildingBuildingProduct
    {:optional true}
    [:vector [:ref :ryhti/BuildingBuildingProduct]]]])

(def GeoJsonPolygonGeometry
  [:map
   {:closed true, :description "GeoJSON Polygon"}
   [:coordinates [:vector [:vector [:vector :double]]]]
   [:type [:enum "Polygon"]]])

(def Elevator
  [:map
   {:closed true, :description "Hissi"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:passengerCapacity {:optional true} [:maybe :int]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:doorOpeningHeight {:optional true} [:maybe :double]]
   [:insideLength {:optional true} [:maybe :double]]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:loadCapacity {:optional true} [:maybe :int]]
   [:doorOpeningWidth {:optional true} [:maybe :double]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Rakennuksen toiminnallisen osan elinkaaren vaihe. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe\">http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe</a>"}]
      (sort (vals mappings/elinkaaren-vaihe)))]]
   [:fireFighterElevator {:optional true} [:maybe :boolean]]
   [:elevatorKey :string]
   [:insideWidth {:optional true} [:maybe :double]]
   [:elevatorType
    (into
     [:enum
      {:description "Hissin laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/hissi\">http://uri.suomi.fi/codelist/rytj/hissi</a>"}]
     (sort (vals mappings/hissin-laji)))]])

(def GeneralizedUsageData
  [:map
   {:closed true}
   [:plannedUserCount {:optional true} [:maybe :int]]
   [:purpose
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GeneralizedPurpose]]]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:usageStatus
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakennuksen käytössäolotilanne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne\">http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne</a>"}
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/01"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/02"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/03"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/04"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/05"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/06"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/07"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/08"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/09"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/10"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/11"]]]
   [:intendedWorkingLife {:optional true} [:maybe :int]]
   [:usefulFloorArea {:optional true} [:maybe :double]]])

(def BuildingPermitDecision
  [:map
   {:closed true, :description "Rakentamislupapäätös"}
   [:publicNoticeDate [:maybe :string]]
   [:constructionToBeStartedBy [:maybe :string]]
   [:engagingParty [:maybe [:vector [:ref :ryhti/EngagingParty]]]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    [:maybe
     [:ref
      {:description "Liiteasiakirja"}
      :ryhti/BuildingAttachmentDocument]]]
   [:constructionToBeCompletedBy [:maybe :string]]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:buildingPermitDecisionKey :string]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionToBeStartedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionDate [:maybe :string]]
   [:permitApplicationType
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamislupahakemuksen lupatyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/LuvanSisalto\">http://uri.suomi.fi/codelist/rytj/LuvanSisalto</a>"}
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/02"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/04"]]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:constructionToBeCompletedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def DeviationPermitIssueNoPersonData
  [:map
   {:closed true}
   [:deviationPermitApplication
    {:optional true}
    [:maybe
     [:ref
      {:description "Poikkeamislupahakemus ilman henkilötietoja"}
      :ryhti/DeviationPermitApplicationNoPersonData]]]
   [:deviationPermitDecision
    {:optional true}
    [:maybe [:ref :ryhti/DeviationPermitDecisionNoPersonData]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:deviationObject
    {:optional true}
    [:maybe [:vector [:ref :ryhti/DeviationObjectNoPersonData]]]]
   [:buildingSite
    [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]
   [:attachment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]
   [:municipalityNumber :string]])

(def CallForDeviation
  [:map
   {:closed true, :description "Määräyksestä poikkeaminen"}
   [:contentOfDeviation
    [:ref
     {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
     :ryhti/LanguageString]]
   [:deviationType
    (into
     [:enum
      {:description "Poikkeamisen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji\">http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji</a>"}]
     (sort (vals mappings/poikkeamisen-laji)))]
   [:callForDeviationKey :string]])

(def DeviationPermitDecisionNoPersonData
  [:map
   {:closed true}
   [:publicNoticeDate [:maybe :string]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    [:maybe [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]
   [:deviationPermitDecisionKey :string]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:decisionInForceUntil :string]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:decisionDate [:maybe :string]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:expiryDate {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def CalculatoryDeliveredEnergyUsage
  [:map
   {:closed true, :description "Energiankulutus"}
   [:energyAmountYearly [:maybe :int]]
   [:deliveredEnergyType
    (into
     [:enum
      {:description "Ostoenergian laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/ostoenergia\">http://uri.suomi.fi/codelist/rytj/ostoenergia</a>"}]
     (sort (vals mappings/ostoenergian-laji)))]
   [:calculatoryDeliveredEnergyUsageKey :string]])

(def ConstructionProject
  [:map
   {:closed true, :description "Rakentamishanke"}
   [:inspection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Inspection]]]]
   [:foreman
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Foreman]]]]
   [:startDate {:optional true} [:maybe :string]]
   [:descriptionOfConstructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:endDate {:optional true} [:maybe :string]]
   [:planner
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Planner]]]]
   [:constructionProjectKey :string]])

(def CreatePermanentBuildingIdentifierCommand
  [:map
   {:closed true,
    :description "Command for creating permanent building identifier."}
   [:propertyIdentifier {:optional true} :string]
   [:permanentPermitIdentifier {:optional true} [:maybe :string]]
   [:pointLocation {:optional true} [:ref :ryhti/RyhtiGeometry]]
   [:constructionAction
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/01"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/02"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/03"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/04"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/05"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/06"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/07"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/08"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/09"]]]
   [:typeOfPurpose
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/01"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/011"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0110"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0111"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0112"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/012"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0120"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0121"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/013"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0130"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/014"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0140"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/02"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/021"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0210"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0211"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/03"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/031"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0310"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0311"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0319"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/032"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0320"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0321"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0322"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0329"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/033"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0330"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/04"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/040"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0400"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/05"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/051"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0510"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0511"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0512"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0513"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0514"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/052"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0520"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0521"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/059"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0590"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/06"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/061"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0610"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0611"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0612"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0613"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0614"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0615"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0619"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/062"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0620"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0621"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/063"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0630"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/07"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/071"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0710"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0711"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0712"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0713"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0714"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/072"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0720"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/073"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0730"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0731"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0739"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/074"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0740"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0741"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0742"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0743"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0744"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0749"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/079"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0790"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/08"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/081"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0810"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/082"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0820"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/083"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0830"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/084"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0840"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0841"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/089"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0890"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0891"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/09"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/091"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0910"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0911"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0912"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0919"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/092"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0920"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/093"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0930"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0939"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/10"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/101"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1010"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1011"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/109"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1090"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1091"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/11"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/111"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1110"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/112"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1120"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/113"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1130"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/12"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/121"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1210"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1211"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1212"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1213"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1214"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1215"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/13"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/131"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1310"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1311"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1319"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/14"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/141"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1410"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1411"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1412"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1413"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1414"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1415"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1416"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1419"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/149"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1490"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1491"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1492"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1493"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1499"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/19"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/191"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1910"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1911"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1912"
     "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1919"]]
   [:dvvCaseIdentifier {:optional true} [:maybe :string]]
   [:municipalityNumber {:optional true} :string]])

(def ChangeInformationBuildingObjectOwner
  [:map
   {:closed true}
   [:buildingObjectOwnerKey {:optional true} :string]
   [:ownerOperator
    {:optional true}
    [:ref :ryhti/ChangeInformationOperator]]
   [:ownerSource
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/OmistajanLahde/code/01"
     "http://uri.suomi.fi/codelist/rytj/OmistajanLahde/code/02"
     "http://uri.suomi.fi/codelist/rytj/OmistajanLahde/code/03"
     "http://uri.suomi.fi/codelist/rytj/OmistajanLahde/code/04"]]
   [:ownerType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/06"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/07"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/08"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/09"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/10"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/11"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/12"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/13"
      "http://uri.suomi.fi/codelist/rytj/Omistajalaji/code/14"]]]
   [:differentOwner {:optional true} :boolean]
   [:ownerContactOperator
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationOperator]]]
   [:tenureStatus
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]])

(def FacadeMaterial
  [:map
   {:closed true, :description "Julkisivumateriaali"}
   [:isPrimary :boolean]
   [:facadeMaterialKey :string]
   [:facadeMaterialType
    (into
     [:enum
      {:description "Julkisivun rakennusaine. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine\">http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine</a>"}]
     (sort (vals mappings/julkisivu)))]])

(def LandscapeWorkPermitIssue
  [:map
   {:closed true, :description "Maisematyölupa-asia"}
   [:landscapeWorkPermitDecision
    [:ref
     {:description "Maisematyölupapäätös"}
     :ryhti/LandscapeWorkPermitDecision]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecision]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:landscapeWorkPermitApplication
    [:vector
     {:description "Maisematyölupahakemus"}
     [:ref :ryhti/LandscapeWorkPermitApplication]]]
   [:constructionAction [:vector [:ref :ryhti/ConstructionAction]]]
   [:constructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamishanke"}
      :ryhti/ConstructionProject]]]
   [:buildingSite
    [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermit]]]]])

(def EnergyDataNoPersonData
  [:map
   {:closed true}
   [:coolingEnergySource
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CoolingEnergySource]]]]
   [:heatedVolume {:optional true} [:maybe :int]]
   [:coolingMethod
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CoolingMethod]]]]
   [:energyEfficiencyRating
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/01"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/02"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/03"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/04"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/05"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/06"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/07"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/08"]]]
   [:electricalEnergySource
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ElectricalEnergySource]]]]
   [:netHeatedArea {:optional true} [:maybe :int]]
   [:computationalEnergyEfficiencyComparand
    {:optional true}
    [:maybe :int]]
   [:energyRatingSubindex {:optional true} [:maybe :string]]
   [:calculatoryDeliveredEnergyUsage
    {:optional true}
    [:maybe
     [:ref
      {:description "Energiankulutus"}
      :ryhti/CalculatoryDeliveredEnergyUsage]]]
   [:energyDataKey {:optional true} :string]
   [:heatingEnergySource
    {:optional true}
    [:maybe [:vector [:ref :ryhti/HeatingEnergySource]]]]
   [:heatingMethod
    {:optional true}
    [:maybe [:vector [:ref :ryhti/HeatingMethod]]]]
   [:attachment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]])

(def InspectionBuildingReference
  [:map
   {:closed true}
   [:buildingSectionReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:permanentBuildingIdentifierReference :string]])

(def BuildingBuildingProduct
  [:map
   {:closed true,
    :description "Rakennuksen rakennustuotteet ja niiden hyödyntäminen"}
   [:productUtilizationAmountUnit {:optional true} [:maybe :string]]
   [:buildingBuildingProductKey {:optional true} :string]
   [:productDescription {:optional true} [:maybe :string]]
   [:productAmount {:optional true} :int]
   [:productUtilizationAmount {:optional true} [:maybe :int]]
   [:productAmountUnit {:optional true} :string]
   [:buildingProductType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/01"
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/02"
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/03"
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/04"
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/05"
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/06"
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/07"
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/08"
     "http://uri.suomi.fi/codelist/rytj/RakennustuotteenLaji/code/09"]]])

(def GeoJsonMultiLineStringGeometry
  [:map
   {:closed true, :description "GeoJSON MultiLineString"}
   [:coordinates [:vector [:vector [:vector :double]]]]
   [:type [:enum "MultiLineString"]]])

(def BuildingCarbonHandprint
  [:map
   {:closed true, :description "Rakennuksen hiilikädenjälki"}
   [:carbonation {:optional true} [:maybe :double]]
   [:carbonStorageEffectOfProducts {:optional true} [:maybe :double]]
   [:buildingCarbonHandprintKey {:optional true} :string]
   [:amountOfReusableMaterial {:optional true} [:maybe :double]]
   [:recycling {:optional true} [:maybe :double]]
   [:excessRenewableEnergy {:optional true} [:maybe :double]]])

(def BuildingSite
  [:map
   {:closed true, :description "Rakennuspaikka"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:stormWaterTreatmentType
    [:maybe
     (into
      [:enum
       {:description "Huleveden käsittelyn laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/hulevedenkasittely\">http://uri.suomi.fi/codelist/rytj/hulevedenkasittely</a>"}]
      (sort (vals mappings/hulevedenkasittely)))]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:buildingSiteAddress [:maybe [:vector [:ref :ryhti/Address]]]]
   [:spatialPlan {:optional true} [:maybe :string]]
   [:buildingSiteKey :string]
   [:area {:optional true} [:maybe :int]]
   [:tenure
    [:maybe
     (into
      [:enum
       {:description "Hallintaperuste. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/RakennuspaikanHallintaperuste\">http://uri.suomi.fi/codelist/rytj/RakennuspaikanHallintaperuste</a>"}]
      (sort (vals mappings/rakennuspaikan-hallintaperuste)))]]
   [:statusOfPlan
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Kaavatilanne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_kaava\">http://uri.suomi.fi/codelist/vtj/Rake_kaava</a>"}]
      (sort (vals mappings/kaavatilanne)))]]])

(def ClimateReport
  [:map
   {:closed true, :description "Ilmastoselvitys"}
   [:preparationDate {:optional true} :string]
   [:assessmentPeriodLength {:optional true} [:maybe :int]]
   [:assessmentPeriodStartDate {:optional true} [:maybe :string]]
   [:buildingSiteCarbonHandprint
    {:optional true}
    [:ref
     {:description "Rakennuspaikan hiilikädenjälki"}
     :ryhti/BuildingSiteCarbonHandprint]]
   [:nameOfPreparer {:optional true} [:maybe :string]]
   [:buildingCarbonHandprint
    {:optional true}
    [:ref
     {:description "Rakennuksen hiilikädenjälki"}
     :ryhti/BuildingCarbonHandprint]]
   [:buildingSiteCarbonFootprint
    {:optional true}
    [:ref
     {:description "Rakennuspaikan hiilijalanjälki"}
     :ryhti/BuildingSiteCarbonFootprint]]
   [:assessmentPeriodEndDate {:optional true} [:maybe :string]]
   [:buildingCarbonFootprint
    {:optional true}
    [:ref
     {:description "Rakennuksen hiilijalanjälki"}
     :ryhti/BuildingCarbonFootprint]]
   [:usedCalculationSoftware {:optional true} :string]
   [:climateReportKey {:optional true} :string]])

(def GetForemanPlannerPermitsResponse
  [:map
   {:closed true}
   [:buildingPermitIssues
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ForemanPlannerPermitIssue]]]]
   [:demolitionPermitIssues
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ForemanPlannerPermitIssue]]]]
   [:landscapeWorkPermitIssues
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ForemanPlannerPermitIssue]]]]])

(def ForemanPlannerPermitIssue
  [:map
   {:closed true}
   [:dateOfInitiation {:optional true} [:maybe :string]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/4"]]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    {:optional true}
    [:vector [:ref :ryhti/ForemanPlannerConstructionAction]]]
   [:constructionProject
    {:optional true}
    [:maybe [:ref :ryhti/ForemanPlannerConstructionProject]]]
   [:municipalityNumber {:optional true} :string]])

(def ChangeInformationCivilDefenceShelter
  [:map
   {:closed true, :description "Väestönsuoja"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:civilDefenceShelterKey :string]
   [:civilDefenceShelterAdditionalInformation
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:civilDefenceShelterShared {:optional true} [:maybe :boolean]]
   [:civilDefenceShelterCapacity
    [:int {:description "Väestönsuojan suojapaikkojen määrä"}]]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:shelterArea {:optional true} :int]
   [:civilDefenceShelterClass
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Väestönsuojan luokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka\">http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka</a>"}]
      (sort (vals mappings/vaestonsuoja-luokka)))]]
   [:civilDefenceShelterOtherBuildings
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CivilDefenceShelterOtherBuildings]]]]])

(def ConstructionAction
  [:map
   {:closed true, :description "Rakentamistoimenpide"}
   [:building
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuksen tiedot."} :ryhti/Building]]]
   [:descriptionOfAction
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:businessConstruction {:optional true} [:maybe :boolean]]
   [:startDate {:optional true} [:maybe :string]]
   [:finishedStructure
    {:optional true}
    [:maybe [:ref {:description "Rakennelma"} :ryhti/Structure]]]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      {:description "Purkamisen syy. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/PurkamisenSyy\">http://uri.suomi.fi/codelist/rytj/PurkamisenSyy</a>"}
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:areaUndergoingChanges {:optional true} [:maybe :int]]
   [:finishedAreaToBeBuiltForSpecificActivities
    {:optional true}
    [:maybe
     [:ref
      {:description "Erityistä toimintaa varten rakennettava alue"}
      :ryhti/AreaToBeBuiltForSpecificActivities]]]
   [:renovation {:optional true} [:maybe :boolean]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:statusOfConstructionAction
    [:maybe
     [:enum
      {:description "Rakentamistoimenpiteen tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila\">http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/4"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/5"]]]
   [:structure
    {:optional true}
    [:maybe [:ref {:description "Rakennelma"} :ryhti/Structure]]]
   [:buildingObjectChange
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen muutos"}
      :ryhti/BuildingObjectChange]]]
   [:fullyApprovedForUse {:optional true} [:maybe :boolean]]
   [:areaToBeBuiltForSpecificActivities
    {:optional true}
    [:maybe
     [:ref
      {:description "Erityistä toimintaa varten rakennettava alue"}
      :ryhti/AreaToBeBuiltForSpecificActivities]]]
   [:changeType
    {:optional true}
    [:maybe
     [:enum
      {:description "Muutostyön tarkenne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/muutostyo\">http://uri.suomi.fi/codelist/rytj/muutostyo</a>"}
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/01"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/02"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/03"]]]
   [:renovationRate {:optional true} [:maybe :int]]
   [:expiryDate {:optional true} [:maybe :string]]
   [:finishedBuilding
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuksen tiedot."} :ryhti/Building]]]
   [:constructionActionKey :string]
   [:completionDate {:optional true} [:maybe :string]]
   [:dvvPermitIdentifier {:optional true} [:maybe :string]]
   [:constructionActionType
    [:maybe
     (into
      [:enum
       {:description "Rakentamistoimenpide. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide\">http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide</a>"}]
      (sort (vals mappings/rakentamistoimenpide)))]]])

(def RyhtiGeometry3d
  [:map
   {:closed true}
   [:srid3d
    [:enum
     {:description "Gauss Krüger projektio; SRID koodi; SRID nimi\r\n\r\n   19;3873;ETRS89 / GK19FIN EPSG:3873\r\n\r\n   20;3874;ETRS89 / GK20FIN EPSG:3874\r\n\r\n   21;3875;ETRS89 / GK21FIN EPSG:3875\r\n\r\n   22;3876;ETRS89 / GK22FIN EPSG:3876\r\n\r\n   23;3877;ETRS89 / GK23FIN EPSG:3877\r\n\r\n   24;3878;ETRS89 / GK24FIN EPSG:3878\r\n\r\n   25;3879;ETRS89 / GK25FIN EPSG:3879\r\n\r\n   26;3880;ETRS89 / GK26FIN EPSG:3880\r\n\r\n   27;3881;ETRS89 / GK27FIN EPSG:3881\r\n\r\n   28;3882;ETRS89 / GK28FIN EPSG:3882\r\n\r\n   29;3883;ETRS89 / GK29FIN EPSG:3883\r\n\r\n   30;3884;ETRS89 / GK30FIN EPSG:3884\r\n\r\n   31;3885;ETRS89 / GK31FIN EPSG:3885\r\n\r\n   3067 TM35FIN\r\n"}
     "3067"
     "3873"
     "3874"
     "3875"
     "3876"
     "3877"
     "3878"
     "3879"
     "3880"
     "3881"
     "3882"
     "3883"
     "3884"
     "3885"
     "4326"]]
   [:geometryData3d
    [:string {:description "3d-geometria wkt-string"}]]])

(def DemolitionPermitDecisionNoPersonData
  [:map
   {:closed true}
   [:publicNoticeDate [:maybe :string]]
   [:demolitionPermitDecisionKey :string]
   [:constructionToBeStartedBy {:optional true} [:maybe :string]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    [:maybe [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]
   [:constructionToBeCompletedBy {:optional true} [:maybe :string]]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionToBeStartedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionDate [:maybe :string]]
   [:permitApplicationType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/02"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/04"]]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:constructionToBeCompletedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def ChangePermit
  [:map
   {:closed true, :description "Muutospäätös"}
   [:publicNoticeDate [:maybe :string]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    [:maybe
     [:ref
      {:description "Liiteasiakirja"}
      :ryhti/BuildingAttachmentDocument]]]
   [:changePermitKey :string]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:decisionDate [:maybe :string]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def DemolitionPermitIssue
  [:map
   {:closed true, :description "Purkamislupa-asia"}
   [:demolitionPermitApplication
    [:maybe
     [:vector
      {:description "Purkamislupahakemus"}
      [:ref :ryhti/DemolitionPermitApplication]]]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecision]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionAction]]]]
   [:demolitionDecision
    [:maybe
     [:ref
      {:description "Purkamislupapäätös"}
      :ryhti/DemolitionPermitDecision]]]
   [:constructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamishanke"}
      :ryhti/ConstructionProject]]]
   [:buildingSite
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermit]]]]])

(def ClimateReportNoPersonData
  [:map
   {:closed true, :description "Ilmastoselvitys"}
   [:preparationDate {:optional true} :string]
   [:assessmentPeriodLength {:optional true} [:maybe :int]]
   [:assessmentPeriodStartDate {:optional true} [:maybe :string]]
   [:buildingSiteCarbonHandprint
    {:optional true}
    [:ref
     {:description "Rakennuspaikan hiilikädenjälki"}
     :ryhti/BuildingSiteCarbonHandprint]]
   [:buildingCarbonHandprint
    {:optional true}
    [:ref
     {:description "Rakennuksen hiilikädenjälki"}
     :ryhti/BuildingCarbonHandprint]]
   [:buildingSiteCarbonFootprint
    {:optional true}
    [:ref
     {:description "Rakennuspaikan hiilijalanjälki"}
     :ryhti/BuildingSiteCarbonFootprint]]
   [:assessmentPeriodEndDate {:optional true} [:maybe :string]]
   [:buildingCarbonFootprint
    {:optional true}
    [:ref
     {:description "Rakennuksen hiilijalanjälki"}
     :ryhti/BuildingCarbonFootprint]]
   [:usedCalculationSoftware {:optional true} :string]
   [:climateReportKey {:optional true} :string]])

(def ChangeInformationOperator
  [:map
   {:closed true}
   [:isPerson {:optional true} [:maybe :boolean]]
   [:deathDate {:optional true} [:maybe :string]]
   [:operatorAddress
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ContactAddress]]]]
   [:firstName {:optional true} [:maybe :string]]
   [:organizationName {:optional true} [:maybe :string]]
   [:otherId {:optional true} [:maybe :string]]
   [:familyName {:optional true} [:maybe :string]]
   [:personalIdentityCode {:optional true} [:maybe :string]]
   [:languageCode {:optional true} [:maybe :string]]
   [:businessId {:optional true} [:maybe :string]]
   [:nonDisclosureForSafety {:optional true} [:maybe :boolean]]
   [:nationalityCode {:optional true} [:maybe :string]]])

(def StructureNoPersonData
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:structureSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/StructureSectionNoPersonData]]]]
   [:address [:maybe [:vector [:ref :ryhti/Address]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:chargingPoint
    {:optional true}
    [:maybe [:ref {:description "Latauspiste"} :ryhti/ChargingPoint]]]
   [:structureMainPurpose
    (into
     [:enum
      {:description "Rakennelman käyttötarkoitus. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus\">http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus</a>"}]
     (sort (vals mappings/rakennelman-kayttotarkoitus)))]
   [:demolitionDeadline {:optional true} [:maybe :string]]
   [:permanentStructureIdentifier :string]
   [:administrativeLocationUnit
    {:optional true}
    [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]
   [:numberOfStoreys {:optional true} [:maybe :int]]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:buildingObjectType
    [:enum
     {:description "Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"}
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:temporary [:maybe :boolean]]
   [:structureKey :string]])

(def Building
  [:map
   {:closed true, :description "Rakennuksen tiedot."}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:buildingPurposeForLowCarbonAssessment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingPurposeForLowCarbonAssessment]]]]
   [:buildingSection
    [:maybe
     [:vector
      {:description "Rakennuksen osat"}
      [:ref :ryhti/BuildingSection]]]]
   [:address [:maybe [:vector [:ref :ryhti/Address]]]]
   [:mainPurpose
    [:maybe
     (into
      [:enum
       {:description "Pääasiallinen käyttötarkoitus. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712\">http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712</a>"}]
      (sort (vals mappings/rakennusluokitus-code->uri)))]]
   [:renovationDate {:optional true} [:maybe :string]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:chargingPoint
    {:optional true}
    [:maybe [:ref {:description "Latauspiste"} :ryhti/ChargingPoint]]]
   [:demolitionDeadline
    {:optional true}
    [:maybe [:string {:description "Demolition deadline"}]]]
   [:permanentBuildingIdentifier
    [:string {:description "Pysyvä rakennustunnus (PRT)"}]]
   [:administrativeLocationUnit
    [:ref
     {:description "Hallinnollinen sijaintiyksikkö"}
     :ryhti/AdministrativeLocationUnit]]
   [:numberOfStoreys [:maybe [:int {:description "Kerrosluku"}]]]
   [:buildingKey :string]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe
     [:ref
      {:description "Hallinnollinen sijaintiyksikkö"}
      :ryhti/AdministrativeLocationUnit]]]
   [:buildingProductList
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennustuoteluettelo"}
      :ryhti/BuildingProductList]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:addChangeEvent {:optional true} [:maybe :boolean]]
   [:buildingObjectType
    [:enum
     {:description "Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"}
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:temporary [:maybe [:boolean {:description "Onko väliaikainen"}]]]
   [:buildingObjectOwner
    [:maybe
     [:vector
      {:description "Rakennuskohteen omistaja"}
      [:ref :ryhti/BuildingObjectOwner]]]]])

(def BuildingSiteCarbonHandprint
  [:map
   {:closed true, :description "Rakennuspaikan hiilikädenjälki"}
   [:carbonation {:optional true} [:maybe :double]]
   [:carbonStorageEffectOfProducts {:optional true} [:maybe :double]]
   [:amountOfReusableMaterial {:optional true} [:maybe :double]]
   [:buildingSiteKey {:optional true} :string]
   [:buildingSiteCarbonHandprintKey {:optional true} :string]
   [:recycling {:optional true} [:maybe :double]]
   [:excessRenewableEnergy {:optional true} [:maybe :double]]])

(def BuildingCarbonFootprint
  [:map
   {:closed true, :description "Rakennuksen hiilijalanjälki"}
   [:c2TransportOfDemolitionWaste {:optional true} :double]
   [:c1Demolition {:optional true} :double]
   [:a4Transports {:optional true} :double]
   [:b4ProductReplacements {:optional true} :double]
   [:buildingCarbonFootprintKey {:optional true} :string]
   [:a5ConstructionSiteOperations {:optional true} :double]
   [:b2Maintenance {:optional true} :double]
   [:reasonForExceedingThreshold
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ReasonForExceedingThreshold]]]]
   [:c3TreatmentOfDemolitionWaste {:optional true} :double]
   [:b6EnergyUse {:optional true} :double]
   [:c4FinalDisposalOfDemolitionWaste {:optional true} :double]
   [:a1_A3ManufacturingOfBuildingProducts {:optional true} :double]
   [:totalCarbonFootprint {:optional true} :double]
   [:b1ProductUse {:optional true} :double]])

(def ChangeInformationBuildingSection
  [:map
   {:closed true}
   [:entranceInOtherBuildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/EntranceInOtherBuildingSection]]]]
   [:partitionReason
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/2"
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/3"]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:maybe
     [:vector
      [:ref :ryhti/DemolitionMaterialAndConstructionWasteReport]]]]
   [:interiorData
    {:optional true}
    [:maybe
     [:ref {:description "Sisätilojen tiedot"} :ryhti/InteriorData]]]
   [:exteriorData
    {:optional true}
    [:maybe
     [:ref {:description "Ulkokuoren tiedot"} :ryhti/ExteriorData]]]
   [:elevatorInOtherBuildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ElevatorInOtherBuildingSection]]]]
   [:relatedFinishedBuildingSection {:optional true} [:maybe :string]]
   [:partitionDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:materialData
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationMaterialData]]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesign]]]]
   [:protectionMethod {:optional true} [:maybe :string]]
   [:civilDefenceShelter
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/ChangeInformationCivilDefenceShelter]]]]
   [:equipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Equipment]]]]
   [:usageData
    {:optional true}
    [:ref {:description "Rakennuksen käyttötiedot"} :ryhti/UsageData]]
   [:demolitionDate {:optional true} [:maybe :string]]
   [:buildingSectionKey {:optional true} :string]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:lifeCycleState
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/01"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/02"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/03"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/04"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/05"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/06"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/07"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/08"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/99"]]]
   [:cultureHistoricalSignificance {:optional true} [:maybe :string]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesign]]]]
   [:elevator
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Elevator]]]]
   [:entrance
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Entrance]]]]
   [:apartment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangeInformationApartment]]]]
   [:assemblyFacility
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AssemblyFacility]]]]
   [:buildingServicesEngineeringData
    {:optional true}
    [:maybe
     [:ref
      {:description "Talotekniikkatiedot"}
      :ryhti/BuildingServicesEngineeringData]]]
   [:internalNetwork
    {:optional true}
    [:maybe [:ref :ryhti/InternalNetwork]]]
   [:buildingInformationModel
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingInformationModel]]]]
   [:energyData
    {:optional true}
    [:maybe [:ref {:description "Energiatiedot"} :ryhti/EnergyData]]]
   [:completionDate {:optional true} [:maybe :string]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def PermanentPermitIdentifierResponse
  [:map
   {:closed true}
   [:response {:optional true} [:maybe :string]]
   [:created {:optional true} :boolean]
   [:permanentPermitIdentifier {:optional true} :string]])

(def ChargingPoint
  [:map
   {:closed true, :description "Latauspiste"}
   [:chargingPointKey :string]
   [:chargingPointCount :int]
   [:conduit :boolean]
   [:otherCablingRoutes :boolean]
   [:preCabling :boolean]])

(def RyhtiGeometry
  [:map
   {:closed true}
   [:srid
    [:enum
     {:description "Gauss Krüger projektio; SRID koodi; SRID nimi\r\n\r\n   19;3873;ETRS89 / GK19FIN EPSG:3873\r\n\r\n   20;3874;ETRS89 / GK20FIN EPSG:3874\r\n\r\n   21;3875;ETRS89 / GK21FIN EPSG:3875\r\n\r\n   22;3876;ETRS89 / GK22FIN EPSG:3876\r\n\r\n   23;3877;ETRS89 / GK23FIN EPSG:3877\r\n\r\n   24;3878;ETRS89 / GK24FIN EPSG:3878\r\n\r\n   25;3879;ETRS89 / GK25FIN EPSG:3879\r\n\r\n   26;3880;ETRS89 / GK26FIN EPSG:3880\r\n\r\n   27;3881;ETRS89 / GK27FIN EPSG:3881\r\n\r\n   28;3882;ETRS89 / GK28FIN EPSG:3882\r\n\r\n   29;3883;ETRS89 / GK29FIN EPSG:3883\r\n\r\n   30;3884;ETRS89 / GK30FIN EPSG:3884\r\n\r\n   31;3885;ETRS89 / GK31FIN EPSG:3885\r\n\r\n   3067 TM35FIN\r\n"}
     "3067"
     "3873"
     "3874"
     "3875"
     "3876"
     "3877"
     "3878"
     "3879"
     "3880"
     "3881"
     "3882"
     "3883"
     "3884"
     "3885"
     "4326"]]
   [:geometry
    [:or
     {:description "Geometria GeoJSON rakenteella: https://geojson.org/"}
     [:ref :ryhti/GeoJsonPointGeometry]
     [:ref :ryhti/GeoJsonMultiPointGeometry]
     [:ref :ryhti/GeoJsonLineStringGeometry]
     [:ref :ryhti/GeoJsonMultiLineStringGeometry]
     [:ref :ryhti/GeoJsonPolygonGeometry]
     [:ref :ryhti/GeoJsonMultiPolygonGeometry]]]])

(def HeatingMethod
  [:map
   {:closed true, :description "Lämmitystapa"}
   [:heatingMethodKey :string]
   [:isPrimary [:maybe :boolean]]
   [:heatingMethodType
    [:maybe
     (into
      [:enum
       {:description "Lämmitystavan laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/lammitystapa\">http://uri.suomi.fi/codelist/rytj/lammitystapa</a>"}]
      (sort (vals mappings/lammitystapa)))]]])

(def CoolingEnergySource
  [:map
   {:closed true, :description "Jäähdytysenergian lähde"}
   [:coolingEnergySourceKey :string]
   [:isPrimary :boolean]
   [:coolingEnergySourceType
    (into
     [:enum
      {:description "Jäähdytysenergian lähteen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/jaahdytys\">http://uri.suomi.fi/codelist/rytj/jaahdytys</a>"}]
     (sort (vals mappings/jaahdytysenergianlahde)))]])

(def ConstructionDesign
  [:map
   {:closed true, :description "Rakennussuunnitelma"}
   [:constructionDesignKey :string]
   [:constructionDesignType
    (into
     [:enum
      {:description "Rakennusssuunnitelman laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/raksuunlajit\">http://uri.suomi.fi/codelist/rytj/raksuunlajit</a>"}]
     (sort (vals mappings/raksuunlajit)))]
   [:document [:vector [:ref :ryhti/BuildingAttachmentDocument]]]])

(def AdministrativeLocationUnit
  [:map
   {:closed true, :description "Hallinnollinen sijaintiyksikkö"}
   [:administrativeLocationUnitKey :string]
   [:unitIdentifier {:optional true} [:maybe :string]]
   [:unseparatedParcelIdentifier {:optional true} [:maybe :string]]
   [:propertyIdentifier :string]])

(def StructureSection
  [:map
   {:closed true, :description "Rakennelman osa"}
   [:partitionReason
    [:maybe
     [:enum
      {:description "Rakennelman osan osittelun laji. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji"}
      "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/1"
      "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/2"
      "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/3"]]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:maybe
     [:vector
      [:ref :ryhti/DemolitionMaterialAndConstructionWasteReport]]]]
   [:exteriorData
    {:optional true}
    [:maybe [:ref :ryhti/ExteriorData]]]
   [:structurePurpose
    (into
     [:enum
      {:description "Rakennelman käyttötarkoitus. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus"}]
     (sort (vals mappings/rakennelman-kayttotarkoitus)))]
   [:partitionDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:materialData
    {:optional true}
    [:maybe [:ref :ryhti/MaterialData]]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesign]]]]
   [:protectionMethod
    {:optional true}
    [:maybe
     [:any
      {:description "Missing codeset: Rakennelman osan suojatapa. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/suojtapa"}]]]
   [:structureSectionKey :string]
   [:equipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Equipment]]]]
   [:demolitionDate {:optional true} [:maybe :string]]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      {:description "Purkamisen syy. Käytetään koodistoa http://uri.suomi.fi/codelist/rytj/PurkamisenSyy"}
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Rakennelman elinkaaren vaihe. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rakrek/rakelinvaih"}
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/01"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/02"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/03"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/04"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/05"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/06"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/07"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/08"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/99"]]]
   [:cultureHistoricalSignificance
    {:optional true}
    [:maybe
     [:any
      {:description "Missing codeset: Rakennelman osan kulttuurihistoriallinen merkittävyys. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rakrek/kulthistmer"}]]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesign]]]]
   [:buildingServicesEngineeringData
    {:optional true}
    [:maybe [:ref :ryhti/BuildingServicesEngineeringData]]]
   [:buildingInformationModel
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingInformationModel]]]]
   [:energyData {:optional true} [:maybe [:ref :ryhti/EnergyData]]]
   [:relatedFinishedStructureSection
    {:optional true}
    [:maybe :string]]
   [:completionDate {:optional true} [:maybe :string]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def GeneralizedLocationPermitObjectNoPersonData
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:address {:optional true} [:vector [:ref :ryhti/Address]]]
   [:specificActivitiesAreaType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/01"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/02"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/03"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/04"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/05"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/06"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/07"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/08"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/99"]]]
   [:totalArea {:optional true} [:maybe :int]]
   [:facadeMaterial
    {:optional true}
    [:maybe [:vector [:ref :ryhti/FacadeMaterial]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:structureMainPurpose
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/001"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/002"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/003"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/004"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/005"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/006"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/007"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/008"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/009"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/010"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/011"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/012"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/013"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/014"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/015"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/016"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/017"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/018"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/019"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/020"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/021"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/022"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/023"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/024"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/025"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/026"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/027"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/028"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/029"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/030"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/031"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/032"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/033"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/034"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/035"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/036"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/037"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/038"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/039"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/040"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/041"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/042"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/043"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/044"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/045"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/046"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/047"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/048"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/049"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/050"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/051"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/052"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/053"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/054"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/055"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/056"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/057"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/058"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/059"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/060"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/061"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/062"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/063"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/064"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/065"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/066"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/067"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/068"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/069"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/070"]]]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:administrativeLocationUnit
    {:optional true}
    [:ref
     {:description "Hallinnollinen sijaintiyksikkö"}
     :ryhti/AdministrativeLocationUnit]]
   [:locationPermitObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/01"
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/02"
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/03"]]
   [:specificActivitiesAreaArea {:optional true} [:maybe :int]]
   [:volume {:optional true} [:maybe :int]]
   [:locationPermitObjectKey {:optional true} :string]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:ref
     {:description "Hallinnollinen sijaintiyksikkö"}
     :ryhti/AdministrativeLocationUnit]]
   [:buildingObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:ref
     {:description "Rakennuskohteen sijaintitiedot"}
     :ryhti/BuildingObjectLocationData]]
   [:buildingMainPurpose
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/01"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/011"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0110"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0111"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0112"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/012"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0120"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0121"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/013"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0130"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/014"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0140"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/02"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/021"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0210"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0211"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/03"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/031"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0310"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0311"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0319"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/032"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0320"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0321"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0322"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0329"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/033"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0330"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/04"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/040"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0400"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/05"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/051"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0510"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0511"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0512"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0513"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0514"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/052"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0520"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0521"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/059"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0590"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/06"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/061"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0610"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0611"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0612"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0613"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0614"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0615"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0619"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/062"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0620"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0621"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/063"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0630"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/07"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/071"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0710"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0711"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0712"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0713"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0714"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/072"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0720"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/073"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0730"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0731"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0739"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/074"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0740"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0741"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0742"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0743"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0744"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0749"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/079"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0790"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/08"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/081"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0810"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/082"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0820"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/083"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0830"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/084"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0840"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0841"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/089"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0890"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0891"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/09"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/091"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0910"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0911"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0912"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0919"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/092"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0920"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/093"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0930"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0939"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/10"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/101"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1010"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1011"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/109"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1090"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1091"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/11"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/111"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1110"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/112"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1120"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/113"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1130"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/12"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/121"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1210"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1211"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1212"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1213"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1214"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1215"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/13"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/131"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1310"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1311"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1319"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/14"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/141"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1410"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1411"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1412"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1413"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1414"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1415"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1416"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1419"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/149"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1490"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1491"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1492"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1493"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1499"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/19"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/191"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1910"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1911"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1912"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1919"]]]
   [:grossFloorArea {:optional true} [:maybe :int]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def NetworkConnection
  [:map
   {:closed true, :description "Verkostoliittymä"}
   [:connected {:optional true} [:maybe :boolean]]
   [:networkConnectionKey :string]
   [:connectionPoint
    {:optional true}
    [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:networkConnectionType
    [:maybe
     (into
      [:enum
       {:description "Verkostoliittymän laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/verkliit\">http://uri.suomi.fi/codelist/rytj/verkliit</a>"}]
      (sort (vals mappings/verkostoliittymat)))]]])

(def ProblemDetails
  [:map
   {:closed false}
   [:instance {:optional true} [:maybe :string]]
   [:type {:optional true} [:maybe :string]]
   [:title {:optional true} [:maybe :string]]
   [:status {:optional true} [:maybe :int]]
   [:detail {:optional true} [:maybe :string]]])

(def Purpose
  [:map
   {:closed true, :description "Käyttötarkoitus"}
   [:isPrimary :boolean]
   [:type
    (into
     [:enum
      {:description "Käyttötarkoitus. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712\">http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712</a>"}]
     (sort (vals mappings/rakennusluokitus-code->uri)))]
   [:purposeKey :string]])

(def BuildingObjectLocationData
  [:map
   {:closed true, :description "Rakennuskohteen sijaintitiedot"}
   [:geometry
    {:optional true}
    [:maybe [:vector [:ref :ryhti/RyhtiGeometry]]]]
   [:buildingObjectLocationDataKey :string]
   [:pointLocation [:ref :ryhti/RyhtiGeometry]]
   [:votingDistrictNumber {:optional true} :string]])

(def GeneralizedBuildingSection
  [:map
   {:closed true}
   [:entranceInOtherBuildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/EntranceInOtherBuildingSection]]]]
   [:partitionReason
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/2"
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/3"]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:vector
     [:ref :ryhti/DemolitionMaterialAndConstructionWasteReport]]]
   [:interiorData
    {:optional true}
    [:maybe
     [:ref {:description "Sisätilojen tiedot"} :ryhti/InteriorData]]]
   [:exteriorData
    {:optional true}
    [:maybe
     [:ref {:description "Ulkokuoren tiedot"} :ryhti/ExteriorData]]]
   [:elevatorInOtherBuildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ElevatorInOtherBuildingSection]]]]
   [:relatedFinishedBuildingSection {:optional true} [:maybe :string]]
   [:partitionDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:materialData
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationMaterialData]]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesign]]]]
   [:protectionMethod {:optional true} [:maybe :string]]
   [:civilDefenceShelter
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/ChangeInformationCivilDefenceShelter]]]]
   [:equipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Equipment]]]]
   [:usageData {:optional true} [:ref :ryhti/GeneralizedUsageData]]
   [:demolitionDate {:optional true} [:maybe :string]]
   [:buildingSectionKey {:optional true} :string]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:lifeCycleState
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/01"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/02"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/03"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/04"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/05"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/06"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/07"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/08"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/99"]]]
   [:cultureHistoricalSignificance {:optional true} [:maybe :string]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesign]]]]
   [:elevator
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Elevator]]]]
   [:entrance
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Entrance]]]]
   [:apartment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangeInformationApartment]]]]
   [:assemblyFacility
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AssemblyFacility]]]]
   [:buildingServicesEngineeringData
    {:optional true}
    [:maybe
     [:ref
      {:description "Talotekniikkatiedot"}
      :ryhti/BuildingServicesEngineeringData]]]
   [:internalNetwork
    {:optional true}
    [:maybe [:ref :ryhti/InternalNetwork]]]
   [:buildingInformationModel
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingInformationModel]]]]
   [:energyData
    {:optional true}
    [:maybe [:ref {:description "Energiatiedot"} :ryhti/EnergyData]]]
   [:completionDate {:optional true} [:maybe :string]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def ForemanStructureReference
  [:map
   {:closed true, :description "Rakennelmaviite"}
   [:structureSectionReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:permanentStructureIdentifierReference :string]])

(def DeviationObjectNoPersonData
  [:map
   {:closed true,
    :description "Poikkeamisen kohde ilman henkilötietoja"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:address [:vector [:ref :ryhti/Address]]]
   [:deviationObjectKey :string]
   [:totalArea {:optional true} [:maybe :int]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:administrativeLocationUnit
    {:optional true}
    [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:buildingObjectType
    [:enum
     {:description "Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"}
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:ref
     {:description "Rakennuskohteen sijaintitiedot"}
     :ryhti/BuildingObjectLocationData]]])

(def ChangeInformationAdministrativeLocationUnit
  [:map
   {:closed true}
   [:administrativeLocationUnitKey {:optional true} :string]
   [:unitIdentifier {:optional true} [:maybe :string]]
   [:unseparatedParcelIdentifier {:optional true} [:maybe :string]]
   [:property
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationProperty]]]
   [:propertyIdentifier {:optional true} :string]])

(def GetBuildingOwnerResponse
  [:map
   {:closed true}
   [:permanentBuildingIdentifier {:optional true} :string]
   [:buildingObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:buildingObjectOwner
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/ChangeInformationBuildingObjectOwner]]]]])

(def Entrance
  [:map
   {:closed true, :description "Sisäänkäynti"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:addressOfEntrance {:optional true} [:maybe :string]]
   [:isRescueDepartmentsAttackPassage
    {:optional true}
    [:maybe :boolean]]
   [:isPrimary :boolean]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:entranceType
    (into
     [:enum
      {:description "Sisäänkäynnin tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/sisaankaynti\">http://uri.suomi.fi/codelist/rytj/sisaankaynti</a>"}]
     (sort (vals mappings/sisaankaynnin-tyyppi)))]
   [:isAccessible :boolean]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Rakennuksen toiminnallisen osan elinkaaren vaihe. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe\">http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe</a>"}]
      (sort (vals mappings/elinkaaren-vaihe)))]]
   [:entranceKey :string]])

(def PermanentBuildingIdentifierResponse
  [:map
   {:closed true}
   [:response {:optional true} [:maybe :string]]
   [:created {:optional true} :boolean]
   [:permanentBuildingIdentifier {:optional true} [:maybe :string]]])

(def CustomValidationProblemDetails
  [:map
   {:closed false}
   [:errors
    {:optional true}
    [:vector [:ref :ryhti/CustomValidationError]]]
   [:instance {:optional true} [:maybe :string]]
   [:type {:optional true} [:maybe :string]]
   [:warnings
    {:optional true}
    [:vector [:ref :ryhti/CustomValidationError]]]
   [:title {:optional true} [:maybe :string]]
   [:status {:optional true} [:maybe :int]]
   [:detail {:optional true} [:maybe :string]]])

(def BuildingPermitApplication
  [:map
   {:closed true, :description "Rakentamislupahakemus"}
   [:buildingPermitApplicationType
    [:maybe
     [:enum
      {:description "Rakentamislupahakemuksen hakemustyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi\">http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi</a>"}
      "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/01"
      "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/02"
      "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/03"
      "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/04"
      "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/05"
      "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/06"
      "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/07"]]]
   [:permitApplicationKey :string]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/\">http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/</a>"}
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/3"]]]
   [:callForDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CallForDeviation]]]]
   [:permitApplicationType
    [:maybe
     [:enum
      {:description "Rakentamislupahakemuksen lupatyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/LuvanSisalto\">http://uri.suomi.fi/codelist/rytj/LuvanSisalto</a>"}
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/02"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/04"]]]
   [:applicationContent :string]
   [:dateOfReception [:maybe :string]]])

(def Structure
  [:map
   {:closed true, :description "Rakennelma"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:structureSection
    [:maybe [:vector [:ref :ryhti/StructureSection]]]]
   [:address [:maybe [:vector [:ref :ryhti/Address]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:chargingPoint
    {:optional true}
    [:maybe [:ref {:description "Latauspiste"} :ryhti/ChargingPoint]]]
   [:structureMainPurpose
    (into
     [:enum
      {:description "Rakennelman käyttötarkoitus. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus\">http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus</a>"}]
     (sort (vals mappings/rakennelman-kayttotarkoitus)))]
   [:demolitionDeadline {:optional true} [:maybe :string]]
   [:permanentStructureIdentifier :string]
   [:administrativeLocationUnit
    [:ref
     {:description "Hallinnollinen sijaintiyksikkö"}
     :ryhti/AdministrativeLocationUnit]]
   [:numberOfStoreys {:optional true} [:maybe :int]]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe
     [:ref
      {:description "Hallinnollinen sijaintiyksikkö"}
      :ryhti/AdministrativeLocationUnit]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:buildingObjectType
    [:enum
     {:description "Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"}
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:temporary [:maybe :boolean]]
   [:buildingObjectOwner
    [:maybe
     [:vector
      {:description "Rakennuskohteen omistaja"}
      [:ref :ryhti/BuildingObjectOwner]]]]
   [:structureKey :string]])

(def Foreman
  [:map
   {:closed true, :description "Työnjohtaja"}
   [:requiredCompetence
    (into
     [:enum
      {:description "Vaativuus- ja pätevyysluokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/vaativuusluokat\">http://uri.suomi.fi/codelist/rytj/vaativuusluokat</a>"}]
     (sort (vals mappings/vaativuusluokat)))]
   [:structureReference
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ForemanStructureReference]]]]
   [:foremanKey :string]
   [:firstName :string]
   [:otherId {:optional true} [:maybe :string]]
   [:responsibilityStartDate :string]
   [:responsibilityEndDate {:optional true} [:maybe :string]]
   [:familyName :string]
   [:personalIdentityCode {:optional true} [:maybe :string]]
   [:foremanRole
    (into
     [:enum
      {:description "Työnjohtajan rooli. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli\">http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli</a>"}]
     (sort (vals mappings/tyonjohtajan-rooli)))]
   [:specificAreaReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:buildingReference
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ForemanBuildingReference]]]]
   [:foremanCompetence
    (into
     [:enum
      {:description "Vaativuus- ja pätevyysluokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/vaativuusluokat\">http://uri.suomi.fi/codelist/rytj/vaativuusluokat</a>"}]
     (sort (vals mappings/vaativuusluokat)))]])

(def ForemanPlannerConstructionProject
  [:map
   {:closed true}
   [:foreman
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Foreman]]]]
   [:startDate {:optional true} [:maybe :string]]
   [:descriptionOfConstructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:endDate {:optional true} [:maybe :string]]
   [:planner
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Planner]]]]
   [:constructionProjectKey {:optional true} :string]])

(def CreatePermanentStructureIdentifierCommand
  [:map
   {:closed true,
    :description "Command for creating permanent structure identifier."}
   [:propertyIdentifier {:optional true} :string]
   [:permanentPermitIdentifier {:optional true} [:maybe :string]]
   [:pointLocation {:optional true} [:ref :ryhti/RyhtiGeometry]]
   [:purposeType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/001"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/002"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/003"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/004"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/005"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/006"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/007"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/008"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/009"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/010"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/011"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/012"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/013"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/014"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/015"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/016"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/017"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/018"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/019"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/020"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/021"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/022"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/023"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/024"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/025"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/026"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/027"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/028"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/029"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/030"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/031"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/032"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/033"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/034"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/035"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/036"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/037"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/038"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/039"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/040"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/041"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/042"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/043"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/044"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/045"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/046"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/047"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/048"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/049"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/050"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/051"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/052"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/053"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/054"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/055"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/056"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/057"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/058"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/059"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/060"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/061"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/062"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/063"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/064"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/065"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/066"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/067"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/068"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/069"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/070"]]
   [:constructionAction
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/01"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/02"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/03"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/04"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/05"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/06"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/07"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/08"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/09"]]]
   [:municipalityNumber {:optional true} :string]])

(def DemolitionMaterialAndConstructionWasteReportNoPersonData
  [:map
   {:closed true}
   [:demolitionMaterialAndConstructionWasteReportKey :string]
   [:demolitionMaterialRegisterIdentifier :string]
   [:demolitionProjectType [:maybe :string]]
   [:document
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]])

(def BuildingInformationModel
  [:map
   {:closed true, :description "Rakennustietomalli"}
   [:document
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:buildingInformationModelKey :string]
   [:referencePoint [:ref :ryhti/RyhtiGeometry]]
   [:buildingInformationModelType
    (into
     [:enum
      {:description "Rakennuksen tietomallin laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakennuksentietomallinlaji\">http://uri.suomi.fi/codelist/rytj/rakennuksentietomallinlaji</a>"}]
     (sort (vals mappings/rakennuksentietomallinlaji)))]])

(def LanguageString
  [:map
   {:closed true,
    :description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
   [:sme
    {:optional true}
    [:maybe [:string {:description "pohjoissaame"}]]]
   [:eng
    {:optional true}
    [:maybe [:string {:description "englanti"}]]]
   [:sms
    {:optional true}
    [:maybe [:string {:description "koltansaame"}]]]
   [:smn
    {:optional true}
    [:maybe [:string {:description "inarinsaame"}]]]
   [:swe {:optional true} [:maybe [:string {:description "ruotsi"}]]]
   [:fin {:optional true} [:maybe [:string {:description "suomi"}]]]])

(def GeneralizedBuildingNoPersonData
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:buildingPurposeForLowCarbonAssessment
    {:optional true}
    [:maybe
     [:vector
      [:ref :ryhti/BuildingPurposeForLowCarbonAssessmentNoPersonData]]]]
   [:buildingSection
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/GeneralizedBuildingSectionNoPersonData]]]]
   [:address
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Address]]]]
   [:mainPurpose {:optional true} [:maybe :string]]
   [:buildingProtectionMethod
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingProtectionMethod]]]]
   [:renovationDate {:optional true} [:maybe :string]]
   [:buildingCultureHistoricalSignificance
    {:optional true}
    [:maybe :string]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:chargingPoint
    {:optional true}
    [:maybe [:ref {:description "Latauspiste"} :ryhti/ChargingPoint]]]
   [:demolitionDeadline {:optional true} [:maybe :string]]
   [:permanentBuildingIdentifier {:optional true} :string]
   [:administrativeLocationUnit
    {:optional true}
    [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]
   [:numberOfStoreys {:optional true} [:maybe :int]]
   [:numberOfDeletedApartments {:optional true} [:maybe :int]]
   [:numberOfNewApartments {:optional true} [:maybe :int]]
   [:buildingKey {:optional true} :string]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:buildingProductList
    {:optional true}
    [:maybe [:ref :ryhti/BuildingProductListNoPersonData]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:addChangeEvent {:optional true} [:maybe :boolean]]
   [:buildingObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:publicUsage {:optional true} [:maybe :boolean]]
   [:temporary {:optional true} [:maybe :boolean]]
   [:mainPurpose1994 {:optional true} [:maybe :int]]])

(def BuildingServicesEngineeringData
  [:map
   {:closed true, :description "Talotekniikkatiedot"}
   [:stormWaterTreatmentType
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Huleveden käsittelyn laji. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/hulevedenkasittely"}]
      (sort (vals mappings/hulevedenkasittely)))]]
   [:networkConnection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/NetworkConnection]]]]
   [:ventilationMethod
    {:optional true}
    [:maybe [:vector [:ref :ryhti/VentilationMethod]]]]
   [:sewerageMethodType
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Jätevesien käsittelyn laji. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/jatevesienkasittely"}]
      (sort (vals mappings/jatevesienkasittely)))]]
   [:householdWaterType
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Talousveden laji. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/talousvesi"}]
      (sort (vals mappings/talousvesityyppi)))]]])

(def PermanentStructureIdentifierResponse
  [:map
   {:closed true}
   [:response {:optional true} [:maybe :string]]
   [:created {:optional true} :boolean]
   [:permanentStructureIdentifier {:optional true} :string]])

(def ForemenPlannersConstructionProject
  [:map
   {:closed true}
   [:foreman
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Foreman]]]]
   [:planner
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Planner]]]]])

(def GeneralizedPurpose
  [:map
   {:closed true}
   [:isPrimary {:optional true} [:maybe :boolean]]
   [:type {:optional true} [:maybe :string]]
   [:purposeKey {:optional true} :string]])

(def Address
  [:map
   {:closed true, :description "Osoite"}
   [:numberPartOfAddressNumber2 {:optional true} [:maybe :int]]
   [:subdivisionLetterOfAddressNumber2
    {:optional true}
    [:maybe :string]]
   [:numberPartOfAddressNumber {:optional true} [:maybe :int]]
   [:subdivisionLetterOfAddressNumber
    {:optional true}
    [:maybe :string]]
   [:postalCode
    [:maybe
     [:any
      {:description "Missing codeset: Postinumero. Käytetään koodiston code arvoa <a href=\"https://uri.suomi.fi/codelist/statbr/postitoimipaik_1_20160607\">https://uri.suomi.fi/codelist/statbr/postitoimipaik_1_20160607</a>"}]]]
   [:addressNumber :int]
   [:location {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:addressKey {:optional true} [:maybe :string]]
   [:addressName
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]])

(def GeoJsonLineStringGeometry
  [:map
   {:closed true, :description "GeoJSON LineString"}
   [:coordinates [:vector [:vector :double]]]
   [:type [:enum "LineString"]]])

(def ConstructionProjectNoPersonData
  [:map
   {:closed true}
   [:inspection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/InspectionNoPersonData]]]]
   [:startDate {:optional true} [:maybe :string]]
   [:descriptionOfConstructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:endDate {:optional true} [:maybe :string]]
   [:constructionProjectKey {:optional true} :string]])

(def PermanentApartmentIdentifierResponse
  [:map
   {:closed true}
   [:response {:optional true} [:maybe :string]]
   [:permanentApartmentIdentifier {:optional true} [:maybe :string]]
   [:created {:optional true} :boolean]])

(def ContactAddress
  [:map
   {:closed true, :description "Yhteysosoite"}
   [:postalAddress
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:numberPartOfAddressNumber2 {:optional true} [:maybe :int]]
   [:additionalForeignAddress {:optional true} [:maybe :string]]
   [:c_O {:optional true} [:maybe :string]]
   [:apartmentIdentifierNumber {:optional true} [:maybe :int]]
   [:apartmentIdentifierLetterSuffix
    {:optional true}
    [:maybe :string]]
   [:subdivisionLetterOfAddressNumber2
    {:optional true}
    [:maybe :string]]
   [:numberPartOfAddressNumber {:optional true} [:maybe :int]]
   [:foreignAddress
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:source
    [:enum
     {:description "Tietolähde. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/tietolahde\">http://uri.suomi.fi/codelist/rytj/tietolahde</a>"}
     "http://uri.suomi.fi/codelist/rytj/tietolahde/code/01"
     "http://uri.suomi.fi/codelist/rytj/tietolahde/code/02"
     "http://uri.suomi.fi/codelist/rytj/tietolahde/code/03"
     "http://uri.suomi.fi/codelist/rytj/tietolahde/code/04"
     "http://uri.suomi.fi/codelist/rytj/tietolahde/code/05"]]
   [:subdivisionLetterOfAddressNumber
    {:optional true}
    [:maybe :string]]
   [:apartmentIdentifierSubdivisionLetter
    {:optional true}
    [:maybe :string]]
   [:poBoxAddress
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:additionalAddressAttribute
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:postalCode
    [:maybe
     [:any
      {:description "Missing codeset: Postinumero. Käytetään koodiston code arvoa <a href=\"https://uri.suomi.fi/codelist/statbr/postitoimipaik_1_20160607\">https://uri.suomi.fi/codelist/statbr/postitoimipaik_1_20160607</a>"}]]]
   [:foreignPostalAddress {:optional true} [:maybe :string]]
   [:location {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:addressKey :string]
   [:contactAddressType
    [:enum
     {:description "Yhteysosoitteen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji\">http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji</a>"}
     "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/01"
     "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/02"
     "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/03"
     "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/04"
     "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/05"
     "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/06"
     "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/07"
     "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/08"]]
   [:countryCode
    {:optional true}
    [:maybe
     [:any
      {:description "Missing codeset: Maakoodi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/jhs/valtio_1_20120101\">http://uri.suomi.fi/codelist/jhs/valtio_1_20120101</a>"}]]]
   [:addressName
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]])

(def HealthReport
  [:map
   {:closed true}
   [:totalDuration {:optional true} :string]
   [:entries {:optional true} [:map {:closed false}]]
   [:status {:optional true} [:enum "Unhealthy" "Degraded" "Healthy"]]])

(def PermitRegulation
  [:map
   {:closed true, :description "Lupamääräys"}
   [:permitRegulationKey :string]
   [:textOfPermitRegulation
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:permitRegulationType
    [:enum
     {:description "Lupamääräys. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen\">http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen</a>"}
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M01"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0101"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0102"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0103"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0104"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0105"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0106"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0107"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0108"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0109"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0110"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M02"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0201"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0202"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M03"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0301"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M0302"
     "http://uri.suomi.fi/codelist/rytj/Lupamaarayshierarkinen/code/M04"]]])

(def DeviationPermitApplication
  [:map
   {:closed true, :description "Poikkeamislupahakemus"}
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/\">http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/</a>"}
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/3"]]]
   [:constructionDesign [:vector [:ref :ryhti/ConstructionDesign]]]
   [:callForDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CallForDeviation]]]]
   [:deviationPermitApplicationKey :string]
   [:applicationContent :string]
   [:dateOfReception [:maybe :string]]
   [:buildingInformationModel
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingInformationModel]]]]])

(def Descriptor
  [:map
   {:closed true}
   [:descriptor {:optional true} [:maybe :string]]
   [:descriptorIdentifier {:optional true} [:maybe :string]]
   [:vocabulary {:optional true} [:maybe :string]]])

(def ChangePermitNoPersonData
  [:map
   {:closed true}
   [:publicNoticeDate [:maybe :string]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    {:optional true}
    [:maybe [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]
   [:changePermitKey {:optional true} :string]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:decisionDate [:maybe :string]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def StructureSectionNoPersonData
  [:map
   {:closed true}
   [:partitionReason
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/2"
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/3"]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:maybe
     [:vector
      [:ref
       :ryhti/DemolitionMaterialAndConstructionWasteReportNoPersonData]]]]
   [:exteriorData
    {:optional true}
    [:maybe
     [:ref {:description "Ulkokuoren tiedot"} :ryhti/ExteriorData]]]
   [:structurePurpose
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/001"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/002"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/003"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/004"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/005"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/006"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/007"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/008"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/009"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/010"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/011"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/012"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/013"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/014"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/015"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/016"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/017"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/018"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/019"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/020"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/021"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/022"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/023"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/024"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/025"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/026"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/027"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/028"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/029"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/030"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/031"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/032"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/033"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/034"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/035"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/036"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/037"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/038"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/039"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/040"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/041"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/042"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/043"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/044"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/045"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/046"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/047"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/048"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/049"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/050"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/051"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/052"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/053"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/054"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/055"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/056"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/057"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/058"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/059"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/060"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/061"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/062"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/063"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/064"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/065"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/066"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/067"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/068"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/069"
     "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/070"]]
   [:partitionDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:materialData
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationMaterialData]]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesignNoPersonData]]]]
   [:protectionMethod {:optional true} [:maybe :string]]
   [:structureSectionKey {:optional true} :string]
   [:equipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Equipment]]]]
   [:demolitionDate {:optional true} [:maybe :string]]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:lifeCycleState
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/01"
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/02"
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/03"
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/04"
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/05"
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/06"
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/07"
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/08"
     "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/99"]]
   [:cultureHistoricalSignificance {:optional true} [:maybe :string]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesignNoPersonData]]]]
   [:buildingServicesEngineeringData
    {:optional true}
    [:maybe
     [:ref
      {:description "Talotekniikkatiedot"}
      :ryhti/BuildingServicesEngineeringData]]]
   [:buildingInformationModel
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingInformationModelNoPersonData]]]]
   [:energyData
    {:optional true}
    [:maybe [:ref :ryhti/EnergyDataNoPersonData]]]
   [:relatedFinishedStructureSection
    {:optional true}
    [:maybe :string]]
   [:completionDate {:optional true} [:maybe :string]]
   [:attachment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]])

(def InspectionNoPersonData
  [:map
   {:closed true}
   [:requiredByPermitRegulations {:optional true} :boolean]
   [:partiality {:optional true} :string]
   [:structureReference
    {:optional true}
    [:maybe [:vector [:ref :ryhti/InspectionStructureReference]]]]
   [:inspectionRemarks
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:buildingsAttachmentDocument
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]
   [:buildingObjectChange
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingObjectChange]]]]
   [:specificAreaReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:specificationOfObjectOfInspection
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:buildingReference
    {:optional true}
    [:maybe [:vector [:ref :ryhti/InspectionBuildingReference]]]]
   [:inspectionType
    {:optional true}
    [:enum
     {:description "Katselmuksen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Katselmuslaji\">http://uri.suomi.fi/codelist/rytj/Katselmuslaji</a>"}
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/01"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/02"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/03"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/04"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/05"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/06"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/07"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/08"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/09"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/10"]]
   [:inspectionDate {:optional true} :string]
   [:inspectionKey {:optional true} :string]
   [:inspectionStatus
    {:optional true}
    (into
     [:enum
      {:description "Katselmuksen tilanne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/KatselmuksenTilanne\">http://uri.suomi.fi/codelist/rytj/KatselmuksenTilanne</a>"}]
     (sort (vals mappings/katselmuksen-tilanne)))]])

(def Planner
  [:map
   {:closed true, :description "Suunnittelija"}
   [:requiredCompetence
    (into
     [:enum
      {:description "Vaativuus- ja pätevyysluokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/vaativuusluokat\">http://uri.suomi.fi/codelist/rytj/vaativuusluokat</a>"}]
     (sort (vals mappings/vaativuusluokat)))]
   [:structureReference
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PlannerStructureReference]]]]
   [:firstName :string]
   [:plannerKey :string]
   [:otherId {:optional true} [:maybe :string]]
   [:responsibilityStartDate :string]
   [:responsibilityEndDate {:optional true} [:maybe :string]]
   [:familyName :string]
   [:personalIdentityCode {:optional true} [:maybe :string]]
   [:specificAreaReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:plannerRole
    (into
     [:enum
      {:description "Suunnittelijan rooli. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli\">http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli</a>"}]
     (sort (vals mappings/suunnittelijanrooli)))]
   [:buildingReference
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PlannerBuildingReference]]]]
   [:plannerCompetence
    (into
     [:enum
      {:description "Vaativuus- ja pätevyysluokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/vaativuusluokat\">http://uri.suomi.fi/codelist/rytj/vaativuusluokat</a>"}]
     (sort (vals mappings/vaativuusluokat)))]])

(def BuildingPermitDecisionNoPersonData
  [:map
   {:closed true}
   [:publicNoticeDate [:maybe :string]]
   [:constructionToBeStartedBy {:optional true} [:maybe :string]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    {:optional true}
    [:maybe [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]
   [:constructionToBeCompletedBy {:optional true} [:maybe :string]]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:buildingPermitDecisionKey {:optional true} :string]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionToBeStartedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionDate [:maybe :string]]
   [:permitApplicationType
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamislupahakemuksen lupatyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/LuvanSisalto\">http://uri.suomi.fi/codelist/rytj/LuvanSisalto</a>"}
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/02"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/04"]]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:constructionToBeCompletedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def BuildingPermitIssue
  [:map
   {:closed true, :description "Rakentamislupa-asia"}
   [:locationPermitObject
    {:optional true}
    [:maybe [:vector [:ref :ryhti/LocationPermitObject]]]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecision]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamislupa-asian päivityksen tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne\">http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne</a>"}
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier :string]
   [:buildingPermitApplication
    [:maybe
     [:vector
      {:description "Rakentamislupahakemus"}
      [:ref :ryhti/BuildingPermitApplication]]]]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    [:maybe [:vector [:ref :ryhti/ConstructionAction]]]]
   [:constructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamishanke"}
      :ryhti/ConstructionProject]]]
   [:decision
    [:maybe
     [:ref
      {:description "Rakentamislupapäätös"}
      :ryhti/BuildingPermitDecision]]]
   [:buildingSite
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermit]]]]])

(def AssemblyFacility
  [:map
   {:closed true, :description "Kokoontumistila"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:assemblyFacilityKey :string]
   [:capacityOfAssemblyFacilities :int]])

(def CreatePermanentApartmentIdentifierCommand
  [:map
   {:closed true,
    :description "Command for creating permanent apartment identifier."}
   [:permanentPermitId {:optional true} [:maybe :string]]
   [:isWaterCloset {:optional true} [:maybe :boolean]]
   [:apartmentStorey {:optional true} [:maybe :int]]
   [:apartmentIdentifierLetterSuffix
    {:optional true}
    [:maybe :string]]
   [:apartmentSubdivisionLetter {:optional true} [:maybe :string]]
   [:buildingSectionKey {:optional true} [:maybe :string]]
   [:apartmentEquipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ApartmentEquipment]]]]
   [:kitchenType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi/code/01"
      "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi/code/02"
      "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi/code/03"
      "http://uri.suomi.fi/codelist/rytj/Keittiotyyppi/code/04"]]]
   [:floorArea {:optional true} [:maybe :double]]
   [:permanentBuildingId {:optional true} :string]
   [:specificationOfLocationFloor
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne/code/01"
      "http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne/code/02"]]]
   [:apartmentKey {:optional true} [:maybe :string]]
   [:addressNumber {:optional true} :int]
   [:changeType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji/code/03"]]]
   [:constructionAction
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/01"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/02"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/03"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/04"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/05"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/06"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/07"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/08"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/09"]]]
   [:apartmentType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi/code/01"
     "http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi/code/02"]]
   [:dvvCaseIdentifier {:optional true} [:maybe :string]]
   [:numberOfRooms {:optional true} [:maybe :int]]
   [:apartmentNumber {:optional true} :int]
   [:municipalityNumber {:optional true} :string]])

(def DeviationPermitDecision
  [:map
   {:closed true, :description "Poikkeamislupapäätös"}
   [:publicNoticeDate [:maybe :string]]
   [:engagingParty [:vector [:ref :ryhti/EngagingParty]]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    [:maybe [:ref :ryhti/BuildingAttachmentDocument]]]
   [:deviationPermitDecisionKey :string]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:decisionInForceUntil :string]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:decisionDate [:maybe :string]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:expiryDate {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def GeneralizedConstructionActionNoPersonData
  [:map
   {:closed true}
   [:locatedOnUnseparatedParcel {:optional true} [:maybe :boolean]]
   [:building
    {:optional true}
    [:maybe [:ref :ryhti/GeneralizedBuildingNoPersonData]]]
   [:descriptionOfAction
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:businessConstruction {:optional true} [:maybe :boolean]]
   [:startDate {:optional true} [:maybe :string]]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      {:description "Purkamisen syy. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/PurkamisenSyy\">http://uri.suomi.fi/codelist/rytj/PurkamisenSyy</a>"}
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:areaUndergoingChanges {:optional true} [:maybe :int]]
   [:renovation {:optional true} [:maybe :boolean]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:statusOfConstructionAction
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamistoimenpiteen tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila\">http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/4"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/5"]]]
   [:structure
    {:optional true}
    [:maybe [:ref :ryhti/StructureNoPersonData]]]
   [:buildingObjectChange
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen muutos"}
      :ryhti/BuildingObjectChange]]]
   [:fullyApprovedForUse {:optional true} [:maybe :boolean]]
   [:areaToBeBuiltForSpecificActivities
    {:optional true}
    [:maybe
     [:ref
      :ryhti/ChangeInformationAreaToBeBuiltForSpecificActivitiesNoPersonData]]]
   [:changeType
    {:optional true}
    [:maybe
     [:enum
      {:description "Muutostyön tarkenne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/muutostyo\">http://uri.suomi.fi/codelist/rytj/muutostyo</a>"}
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/01"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/02"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/03"]]]
   [:renovationRate {:optional true} [:maybe :int]]
   [:expiryDate {:optional true} [:maybe :string]]
   [:finishedBuilding
    {:optional true}
    [:maybe [:ref :ryhti/GeneralizedBuildingNoPersonData]]]
   [:constructionActionKey {:optional true} :string]
   [:completionDate {:optional true} [:maybe :string]]
   [:dvvPermitIdentifier {:optional true} [:maybe :string]]
   [:constructionActionType
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Rakentamistoimenpide. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide\">http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide</a>"}]
      (sort (vals mappings/rakentamistoimenpide)))]]])

(def EngagingParty
  [:map
   {:closed true, :description "Hankkeeseen ryhtyvä"}
   [:applicantContactOperator
    {:optional true}
    [:maybe [:ref :ryhti/Operator]]]
   [:engagingPartyKey :string]
   [:isPerson :boolean]
   [:deathDate {:optional true} [:maybe :string]]
   [:firstName {:optional true} [:maybe :string]]
   [:organizationName {:optional true} [:maybe :string]]
   [:otherId {:optional true} [:maybe :string]]
   [:familyName {:optional true} [:maybe :string]]
   [:personalIdentityCode {:optional true} [:maybe :string]]
   [:languageCode
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "IETF kielikoodi. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/interoperabilityplatform/languagecodes"}]
      mappings/main-language-codes)]]
   [:businessId {:optional true} [:maybe :string]]
   [:nationalityCode
    {:optional true}
    [:maybe
     [:any
      {:description "Missing codeset: Valtiokoodi. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/jhs/valtio_1_20120101"}]]]])

(def GeneralizedDemolitionPermitIssue
  [:map
   {:closed true}
   [:demolitionPermitApplication
    [:maybe
     [:vector
      {:description "Purkamislupahakemus"}
      [:ref :ryhti/DemolitionPermitApplication]]]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecision]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GeneralizedConstructionAction]]]]
   [:demolitionDecision
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamislupapäätös"}
      :ryhti/DemolitionPermitDecision]]]
   [:constructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamishanke"}
      :ryhti/ConstructionProject]]]
   [:buildingSite
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermit]]]]])

(def VentilationMethod
  [:map
   {:closed true, :description "Ilmanvaihtotapa"}
   [:isPrimary :boolean]
   [:ventilationMethodKey :string]
   [:ventilationMethodType
    (into
     [:enum
      {:description "Ilmanvaihtotavan laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/ilmanvaihto\">http://uri.suomi.fi/codelist/rytj/ilmanvaihto</a>"}]
     (sort (vals mappings/ilmanvaihtotavan-laji)))]])

(def GeneralizedBuilding
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:buildingPurposeForLowCarbonAssessment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingPurposeForLowCarbonAssessment]]]]
   [:buildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GeneralizedBuildingSection]]]]
   [:address
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Address]]]]
   [:mainPurpose {:optional true} [:maybe :string]]
   [:buildingProtectionMethod
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingProtectionMethod]]]]
   [:renovationDate {:optional true} [:maybe :string]]
   [:buildingCultureHistoricalSignificance
    {:optional true}
    [:maybe :string]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:chargingPoint
    {:optional true}
    [:maybe [:ref {:description "Latauspiste"} :ryhti/ChargingPoint]]]
   [:demolitionDeadline {:optional true} [:maybe :string]]
   [:permanentBuildingIdentifier {:optional true} :string]
   [:administrativeLocationUnit
    {:optional true}
    [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]
   [:numberOfStoreys {:optional true} [:maybe :int]]
   [:numberOfDeletedApartments {:optional true} [:maybe :int]]
   [:numberOfNewApartments {:optional true} [:maybe :int]]
   [:buildingKey {:optional true} :string]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:buildingProductList
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennustuoteluettelo"}
      :ryhti/BuildingProductList]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:addChangeEvent {:optional true} [:maybe :boolean]]
   [:buildingObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:publicUsage {:optional true} [:maybe :boolean]]
   [:temporary {:optional true} [:maybe :boolean]]
   [:buildingObjectOwner
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/ChangeInformationBuildingObjectOwner]]]]
   [:mainPurpose1994 {:optional true} [:maybe :int]]])

(def ExtensionDecision
  [:map
   {:closed true, :description "Jatkoaikapäätös"}
   [:publicNoticeDate [:maybe :string]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    [:maybe
     [:ref
      {:description "Liiteasiakirja"}
      :ryhti/BuildingAttachmentDocument]]]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:extensionDecisionKey :string]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:decisionDate [:maybe :string]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def DeviationPermitApplicationNoPersonData
  [:map
   {:closed true,
    :description "Poikkeamislupahakemus ilman henkilötietoja"}
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/\">http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/</a>"}
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/3"]]]
   [:constructionDesign
    [:vector [:ref :ryhti/ConstructionDesignNoPersonData]]]
   [:callForDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CallForDeviation]]]]
   [:deviationPermitApplicationKey :string]
   [:applicationContent :string]
   [:dateOfReception [:maybe :string]]
   [:buildingInformationModel
    {:optional true}
    [:vector [:ref :ryhti/BuildingInformationModelNoPersonData]]]])

(def BuildingObjectChange
  [:map
   {:closed true, :description "Rakennuskohteen muutos"}
   [:numberOfStoreysChange {:optional true} [:maybe :int]]
   [:floorAreaChange {:optional true} [:maybe :double]]
   [:changeInPermittedVolume {:optional true} [:maybe :int]]
   [:totalAreaChange {:optional true} [:maybe :int]]
   [:buildingObjectChangeKey :string]
   [:permittedBuildingAreaChange {:optional true} [:maybe :int]]
   [:volumeChange {:optional true} [:maybe :int]]
   [:grossFloorAreaChange {:optional true} [:maybe :int]]
   [:basementAreaChange {:optional true} [:maybe :int]]
   [:changeInPermittedRidgeHeight {:optional true} [:maybe :double]]])

(def BuildingSection
  [:map
   {:closed true, :description "Rakennuksen osa"}
   [:entranceInOtherBuildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/EntranceInOtherBuildingSection]]]]
   [:partitionReason
    [:maybe
     [:enum
      {:description "Rakennuksen osan osittelun laji. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji"}
      "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/1"
      "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/2"
      "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/3"]]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:maybe
     [:vector
      [:ref :ryhti/DemolitionMaterialAndConstructionWasteReport]]]]
   [:interiorData
    {:optional true}
    [:maybe [:ref :ryhti/InteriorData]]]
   [:exteriorData
    {:optional true}
    [:maybe [:ref :ryhti/ExteriorData]]]
   [:elevatorInOtherBuildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ElevatorInOtherBuildingSection]]]]
   [:relatedFinishedBuildingSection {:optional true} [:maybe :string]]
   [:partitionDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:materialData
    {:optional true}
    [:maybe [:ref :ryhti/MaterialData]]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesign]]]]
   [:protectionMethod
    {:optional true}
    [:maybe
     [:any
      {:description "Missing codeset: Rakennuksen osan suojatapa. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/suojtapa"}]]]
   [:civilDefenceShelter
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CivilDefenceShelter]]]]
   [:equipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Equipment]]]]
   [:usageData [:ref :ryhti/UsageData]]
   [:demolitionDate {:optional true} [:maybe :string]]
   [:buildingSectionKey :string]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      {:description "Purkamisen syy. Käytetään koodistoa http://uri.suomi.fi/codelist/rytj/PurkamisenSyy"}
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Rakennuksen osan elinkaaren vaihe. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rakrek/rakelinvaih"}
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/01"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/02"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/03"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/04"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/05"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/06"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/07"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/08"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/99"]]]
   [:cultureHistoricalSignificance
    {:optional true}
    [:maybe
     [:any
      {:description "Missing codeset: Rakennuksen osan kulttuurihistoriallinen merkittävyys. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rakrek/kulthistmer"}]]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesign]]]]
   [:elevator
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Elevator]]]]
   [:entrance
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Entrance]]]]
   [:apartment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Apartment]]]]
   [:assemblyFacility
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AssemblyFacility]]]]
   [:buildingServicesEngineeringData
    {:optional true}
    [:maybe [:ref :ryhti/BuildingServicesEngineeringData]]]
   [:internalNetwork
    {:optional true}
    [:maybe [:ref :ryhti/InternalNetwork]]]
   [:buildingInformationModel
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingInformationModel]]]]
   [:energyData {:optional true} [:maybe [:ref :ryhti/EnergyData]]]
   [:completionDate {:optional true} [:maybe :string]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def InspectionStructureReference
  [:map
   {:closed true}
   [:structureSectionReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:permanentStructureIdentifierReference :string]])

(def UsageData
  [:map
   {:closed true, :description "Rakennuksen käyttötiedot"}
   [:plannedUserCount {:optional true} [:maybe :int]]
   [:purpose [:maybe [:vector [:ref :ryhti/Purpose]]]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:usageStatus
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakennuksen käytössäolotilanne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne\">http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne</a>"}
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/01"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/02"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/03"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/04"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/05"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/06"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/07"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/08"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/09"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/10"
      "http://uri.suomi.fi/codelist/vtj/rake_kaytossaolotilanne/code/11"]]]
   [:intendedWorkingLife {:optional true} [:maybe :int]]
   [:usefulFloorArea {:optional true} [:maybe :double]]])

(def GeneralizedConstructionAction
  [:map
   {:closed true}
   [:locatedOnUnseparatedParcel {:optional true} [:maybe :boolean]]
   [:building
    {:optional true}
    [:maybe [:ref :ryhti/GeneralizedBuilding]]]
   [:descriptionOfAction
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:businessConstruction {:optional true} [:maybe :boolean]]
   [:startDate {:optional true} [:maybe :string]]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      {:description "Purkamisen syy. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/PurkamisenSyy\">http://uri.suomi.fi/codelist/rytj/PurkamisenSyy</a>"}
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:areaUndergoingChanges {:optional true} [:maybe :int]]
   [:renovation {:optional true} [:maybe :boolean]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:statusOfConstructionAction
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamistoimenpiteen tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila\">http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/4"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/5"]]]
   [:structure
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationStructure]]]
   [:buildingObjectChange
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen muutos"}
      :ryhti/BuildingObjectChange]]]
   [:fullyApprovedForUse {:optional true} [:maybe :boolean]]
   [:areaToBeBuiltForSpecificActivities
    {:optional true}
    [:maybe
     [:ref :ryhti/ChangeInformationAreaToBeBuiltForSpecificActivities]]]
   [:changeType
    {:optional true}
    [:maybe
     [:enum
      {:description "Muutostyön tarkenne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/muutostyo\">http://uri.suomi.fi/codelist/rytj/muutostyo</a>"}
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/01"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/02"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/03"]]]
   [:renovationRate {:optional true} [:maybe :int]]
   [:expiryDate {:optional true} [:maybe :string]]
   [:constructionActionKey {:optional true} :string]
   [:completionDate {:optional true} [:maybe :string]]
   [:dvvPermitIdentifier {:optional true} [:maybe :string]]
   [:constructionActionType
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Rakentamistoimenpide. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide\">http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide</a>"}]
      (sort (vals mappings/rakentamistoimenpide)))]]])

(def ExtensionDecisionNoPersonData
  [:map
   {:closed true}
   [:publicNoticeDate [:maybe :string]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    {:optional true}
    [:maybe [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:extensionDecisionKey {:optional true} :string]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:decisionDate [:maybe :string]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def BuildingInformationModelNoPersonData
  [:map
   {:closed true}
   [:document
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]
   [:buildingInformationModelKey :string]
   [:referencePoint [:ref :ryhti/RyhtiGeometry]]
   [:buildingInformationModelType
    (into
     [:enum
      {:description "Rakennuksen tietomallin laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakennuksentietomallinlaji\">http://uri.suomi.fi/codelist/rytj/rakennuksentietomallinlaji</a>"}]
     (sort (vals mappings/rakennuksentietomallinlaji)))]])

(def ChangeInformationProperty
  [:map
   {:closed true}
   [:propertyIdentifier {:optional true} :string]
   [:municipalityNumber {:optional true} :string]])

(def SpecialDesignNoPersonData
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:document
    {:optional true}
    [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]
   [:specialDesignKey {:optional true} :string]
   [:specialDesignType
    {:optional true}
    (into
     [:enum
      {:description "Erityissuunnitelman laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/erityissuun\">http://uri.suomi.fi/codelist/rytj/erityissuun</a>"}]
     (sort (vals mappings/erityissuun)))]])

(def LocationPermitObject
  [:map
   {:closed true, :description "SijoittamislupaKohde"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:address {:optional true} [:vector [:ref :ryhti/Address]]]
   [:specificActivitiesAreaType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/01"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/02"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/03"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/04"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/05"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/06"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/07"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/08"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/99"]]]
   [:totalArea {:optional true} [:maybe :int]]
   [:facadeMaterial
    {:optional true}
    [:maybe [:vector [:ref :ryhti/FacadeMaterial]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:structureMainPurpose
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/001"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/002"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/003"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/004"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/005"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/006"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/007"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/008"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/009"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/010"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/011"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/012"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/013"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/014"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/015"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/016"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/017"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/018"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/019"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/020"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/021"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/022"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/023"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/024"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/025"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/026"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/027"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/028"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/029"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/030"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/031"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/032"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/033"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/034"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/035"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/036"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/037"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/038"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/039"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/040"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/041"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/042"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/043"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/044"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/045"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/046"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/047"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/048"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/049"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/050"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/051"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/052"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/053"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/054"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/055"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/056"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/057"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/058"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/059"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/060"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/061"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/062"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/063"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/064"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/065"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/066"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/067"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/068"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/069"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/070"]]]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:administrativeLocationUnit
    {:optional true}
    [:ref :ryhti/AdministrativeLocationUnit]]
   [:locationPermitObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/01"
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/02"
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/03"]]
   [:specificActivitiesAreaArea
    {:optional true}
    [:maybe
     [:int {:description "Erityisen toiminnan alueen pinta-ala, m2"}]]]
   [:volume {:optional true} [:maybe :int]]
   [:locationPermitObjectKey {:optional true} :string]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:ref :ryhti/AdministrativeLocationUnit]]
   [:buildingObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:ref :ryhti/BuildingObjectLocationData]]
   [:buildingMainPurpose
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/01"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/011"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0110"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0111"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0112"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/012"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0120"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0121"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/013"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0130"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/014"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0140"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/02"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/021"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0210"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0211"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/03"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/031"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0310"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0311"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0319"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/032"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0320"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0321"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0322"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0329"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/033"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0330"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/04"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/040"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0400"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/05"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/051"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0510"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0511"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0512"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0513"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0514"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/052"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0520"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0521"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/059"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0590"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/06"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/061"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0610"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0611"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0612"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0613"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0614"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0615"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0619"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/062"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0620"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0621"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/063"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0630"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/07"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/071"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0710"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0711"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0712"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0713"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0714"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/072"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0720"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/073"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0730"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0731"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0739"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/074"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0740"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0741"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0742"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0743"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0744"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0749"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/079"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0790"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/08"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/081"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0810"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/082"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0820"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/083"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0830"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/084"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0840"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0841"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/089"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0890"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0891"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/09"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/091"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0910"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0911"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0912"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0919"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/092"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0920"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/093"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0930"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0939"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/10"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/101"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1010"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1011"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/109"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1090"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1091"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/11"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/111"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1110"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/112"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1120"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/113"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1130"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/12"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/121"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1210"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1211"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1212"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1213"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1214"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1215"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/13"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/131"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1310"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1311"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1319"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/14"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/141"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1410"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1411"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1412"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1413"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1414"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1415"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1416"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1419"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/149"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1490"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1491"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1492"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1493"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1499"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/19"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/191"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1910"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1911"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1912"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1919"]]]
   [:grossFloorArea {:optional true} [:maybe :int]]
   [:buildingObjectOwner
    {:optional true}
    [:vector [:ref :ryhti/BuildingObjectOwner]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def ChangeInformationDemolitionPermitIssue
  [:map
   {:closed true}
   [:demolitionPermitApplication
    [:maybe
     [:vector
      {:description "Purkamislupahakemus"}
      [:ref :ryhti/DemolitionPermitApplication]]]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecision]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/ChangeInformationConstructionAction]]]]
   [:demolitionDecision
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamislupapäätös"}
      :ryhti/DemolitionPermitDecision]]]
   [:constructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamishanke"}
      :ryhti/ConstructionProject]]]
   [:buildingSite
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermit]]]]])

(def LandscapeWorkPermitApplication
  [:map
   {:closed true, :description "Maisematyölupahakemus"}
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/\">http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/</a>"}
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/3"]]]
   [:constructionDesign
    {:optional true}
    [:vector [:ref :ryhti/ConstructionDesign]]]
   [:callForDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CallForDeviation]]]]
   [:applicationContent :string]
   [:dateOfReception [:maybe :string]]
   [:buildingInformationModel
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingInformationModel]]]]
   [:landscapeWorkPermitApplicationKey :string]])

(def BuildingObjectIssue
  [:map
   {:closed true, :description "Rakennuskohdeasia"}
   [:constructionAction
    [:ref
     {:description "Rakentamistoimenpide"}
     :ryhti/ConstructionAction]]
   [:buildingSite
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]
   [:buildingObjectIssueKey :string]])

(def GeoJsonPointGeometry
  [:map
   {:closed true, :description "GeoJSON Point"}
   [:coordinates [:vector :double]]
   [:type [:enum "Point"]]])

(def Apartment
  [:map
   {:closed true, :description "Huoneisto"}
   [:apartmentIdentifier :string]
   [:isWaterCloset {:optional true} [:maybe :boolean]]
   [:apartmentStorey {:optional true} [:maybe :int]]
   [:apartmentIdentifierLetterSuffix
    {:optional true}
    [:maybe :string]]
   [:apartmentSubdivisionLetter {:optional true} [:maybe :string]]
   [:apartmentEquipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ApartmentEquipment]]]]
   [:kitchenType
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Keittiötyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Keittiotyyppi\">http://uri.suomi.fi/codelist/rytj/Keittiotyyppi</a>"}]
      (sort (vals mappings/keittiotyyppi)))]]
   [:floorArea {:optional true} [:maybe :double]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Huoneiston elinkaaren vaihe. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe\">http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe</a>"}
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/1"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/2"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/3"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/4"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/5"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/6"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/7"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/8"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/9"]]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:specificationOfLocationFloor
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Sijaintikerroksen tarkenne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne\">http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne</a>"}]
      (sort (vals mappings/sijaintikerroksen-tarkenne)))]]
   [:apartmentKey :string]
   [:addressNumber :int]
   [:apartmentType
    (into
     [:enum
      {:description "Huoneistotyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi\">http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi</a>"}]
     (sort (vals mappings/huoneistotyyppi)))]
   [:apartmentChangeType
    [:maybe
     (into
      [:enum
       {:description "Huoneiston muutoksen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji\">http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji</a>"}]
      (sort (vals mappings/huoneiston-muutoksen-laji)))]]
   [:numberOfRooms [:maybe :int]]
   [:apartmentNumber {:optional true} :int]])

(def ChangeInformationBuildingPermitIssue
  [:map
   {:closed true}
   [:locationPermitObject
    {:optional true}
    [:maybe [:vector [:ref :ryhti/LocationPermitObject]]]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecision]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamislupa-asian päivityksen tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne\">http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne</a>"}
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:buildingPermitApplication
    {:optional true}
    [:maybe
     [:vector
      {:description "Rakentamislupahakemus"}
      [:ref :ryhti/BuildingPermitApplication]]]]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    {:optional true}
    [:vector [:ref :ryhti/ChangeInformationConstructionAction]]]
   [:constructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamishanke"}
      :ryhti/ConstructionProject]]]
   [:decision
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamislupapäätös"}
      :ryhti/BuildingPermitDecision]]]
   [:buildingSite
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermit]]]]])

(def BuildingPurposeForLowCarbonAssessmentNoPersonData
  [:map
   {:closed true,
    :description "Käyttötarkoitus vähähiilisyyden arvioinnissa"}
   [:buildingPurposeForLowCarbonAssessmentKey
    {:optional true}
    :string]
   [:climateReport
    {:optional true}
    [:maybe
     [:ref
      {:description "Ilmastoselvitys"}
      :ryhti/ClimateReportNoPersonData]]]
   [:isPrimary {:optional true} :boolean]
   [:buildingPurposeForLowCarbonAssessmentType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/2"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/3"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/4"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/5"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/6"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/7"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/8"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/9"]]])

(def ChangeInformationAreaToBeBuiltForSpecificActivitiesNoPersonData
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:address
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Address]]]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:maybe
     [:vector
      [:ref
       :ryhti/DemolitionMaterialAndConstructionWasteReportNoPersonData]]]]
   [:specificActivitiesAreaType {:optional true} [:maybe :string]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesignNoPersonData]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:demolitionDeadline {:optional true} [:maybe :string]]
   [:areaToBeBuiltForSpecificActivitiesKey {:optional true} :string]
   [:administrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:specificActivitiesAreaArea {:optional true} [:maybe :int]]
   [:networkConnection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/NetworkConnection]]]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesignNoPersonData]]]]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:buildingObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:temporary {:optional true} :boolean]
   [:buildingInformationModel
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingInformationModelNoPersonData]]]]
   [:completionDate {:optional true} [:maybe :string]]
   [:attachment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]])

(def BuildingObjectOwner
  [:map
   {:closed true, :description "Rakennuskohteen omistaja"}
   [:buildingObjectOwnerKey :string]
   [:ownerOperator [:ref :ryhti/Operator]]
   [:ownerType
    (into
     [:enum
      {:description "Rakennuskohteen omistajan laji. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/Omistajalaji"}]
     (sort (vals mappings/omistajalaji)))]
   [:differentOwner :boolean]
   [:ownerContactOperator
    {:optional true}
    [:maybe [:ref :ryhti/Operator]]]
   [:tenureStatus
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]])

(def DemolitionPermitApplication
  [:map
   {:closed true, :description "PurkamislupaHakemus"}
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/\">http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/</a>"}
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/3"]]]
   [:demolitionPermitApplicationKey :string]
   [:callForDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CallForDeviation]]]]
   [:permitApplicationType
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamislupahakemuksen lupatyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/LuvanSisalto\">http://uri.suomi.fi/codelist/rytj/LuvanSisalto</a>"}
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/02"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/04"]]]
   [:applicationContent :string]
   [:dateOfReception [:maybe :string]]])

(def BuildingProductList
  [:map
   {:closed true, :description "Rakennustuoteluettelo"}
   [:preparationDate {:optional true} :string]
   [:buildingSiteBuildingProduct
    {:optional true}
    [:vector [:ref :ryhti/BuildingSiteBuildingProduct]]]
   [:buildingProductListKey {:optional true} :string]
   [:nameOfPreparer {:optional true} :string]
   [:buildingBuildingProduct
    {:optional true}
    [:vector [:ref :ryhti/BuildingBuildingProduct]]]])

(def EntranceInOtherBuildingSection
  [:map
   {:closed true}
   [:entranceInOtherBuildingSectionKey :string]
   [:isPrimary :boolean]
   [:otherEntranceBuildingIdentifier :string]
   [:otherEntranceKey :string]])

(def ChangeInformationConstructionAction
  [:map
   {:closed true}
   [:locatedOnUnseparatedParcel {:optional true} [:maybe :boolean]]
   [:building
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationBuilding]]]
   [:descriptionOfAction
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:businessConstruction {:optional true} [:maybe :boolean]]
   [:startDate {:optional true} [:maybe :string]]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      {:description "Purkamisen syy. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/PurkamisenSyy\">http://uri.suomi.fi/codelist/rytj/PurkamisenSyy</a>"}
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:areaUndergoingChanges {:optional true} [:maybe :int]]
   [:renovation {:optional true} [:maybe :boolean]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:statusOfConstructionAction
    [:maybe
     [:enum
      {:description "Rakentamistoimenpiteen tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila\">http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/4"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/5"]]]
   [:structure
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationStructure]]]
   [:buildingObjectChange
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen muutos"}
      :ryhti/BuildingObjectChange]]]
   [:fullyApprovedForUse {:optional true} [:maybe :boolean]]
   [:areaToBeBuiltForSpecificActivities
    {:optional true}
    [:maybe
     [:ref :ryhti/ChangeInformationAreaToBeBuiltForSpecificActivities]]]
   [:changeType
    {:optional true}
    [:maybe
     [:enum
      {:description "Muutostyön tarkenne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/muutostyo\">http://uri.suomi.fi/codelist/rytj/muutostyo</a>"}
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/01"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/02"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/03"]]]
   [:renovationRate {:optional true} [:maybe :int]]
   [:expiryDate {:optional true} [:maybe :string]]
   [:constructionActionKey :string]
   [:completionDate {:optional true} [:maybe :string]]
   [:dvvPermitIdentifier {:optional true} [:maybe :string]]
   [:constructionActionType
    [:maybe
     (into
      [:enum
       {:description "Rakentamistoimenpide. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide\">http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide</a>"}]
      (sort (vals mappings/rakentamistoimenpide)))]]])

(def HeatingEnergySource
  [:map
   {:closed true, :description "Lämmitysenergianlähde"}
   [:isPrimary [:maybe :boolean]]
   [:heatingEnergySourceType
    [:maybe
     (into
      [:enum
       {:description "Lämmitysenergianlähteen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde\">http://uri.suomi.fi/codelist/rytj/lammitysenergianlahde</a>"}]
      (sort (vals mappings/lammonlahde)))]]
   [:heatingEnergySourceKey :string]])

(def GetForemenPlannersResponse
  [:map
   {:closed true}
   [:dateOfInitiation {:optional true} [:maybe :string]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/4"]]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionProject
    {:optional true}
    [:ref :ryhti/ForemenPlannersConstructionProject]]
   [:municipalityNumber {:optional true} :string]])

(def BuildingSiteCarbonFootprint
  [:map
   {:closed true, :description "Rakennuspaikan hiilijalanjälki"}
   [:c2TransportOfDemolitionWaste {:optional true} :double]
   [:buildingSiteCarbonFootprintKey {:optional true} :string]
   [:c1Demolition {:optional true} :double]
   [:a4Transports {:optional true} :double]
   [:b4ProductReplacements {:optional true} :double]
   [:a5ConstructionSiteOperations {:optional true} :double]
   [:b2Maintenance {:optional true} :double]
   [:buildingSiteKey {:optional true} :string]
   [:c3TreatmentOfDemolitionWaste {:optional true} :double]
   [:b6EnergyUse {:optional true} :double]
   [:c4FinalDisposalOfDemolitionWaste {:optional true} :double]
   [:a1_A3ManufacturingOfBuildingProducts {:optional true} :double]
   [:totalCarbonFootprint {:optional true} :double]
   [:b1ProductUse {:optional true} :double]])

(def ChangeInformationMaterialData
  [:map
   {:closed true}
   [:buildingMaterialOfLoadBearingStructures
    {:optional true}
    [:vector [:ref :ryhti/BuildingMaterialOfLoadBearingStructures]]]
   [:wideBodiedBuilding {:optional true} :boolean]
   [:facadeMaterial
    {:optional true}
    [:maybe [:vector [:ref :ryhti/FacadeMaterial]]]]
   [:reusableMaterialAmount {:optional true} [:maybe :double]]
   [:fireClass {:optional true} [:maybe :string]]
   [:constructionMethod
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/vtj/Rake_runkotapa/code/01"
      "http://uri.suomi.fi/codelist/vtj/Rake_runkotapa/code/02"]]]])

(def SpecialDesign
  [:map
   {:closed true, :description "Erityissuunnitelma"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:document [:vector [:ref :ryhti/BuildingAttachmentDocument]]]
   [:specialDesignKey :string]
   [:specialDesignType
    (into
     [:enum
      {:description "Erityissuunnitelman laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/erityissuun\">http://uri.suomi.fi/codelist/rytj/erityissuun</a>"}]
     (sort (vals mappings/erityissuun)))]])

(def ForemanBuildingReference
  [:map
   {:closed true, :description "Rakennusviite"}
   [:buildingSectionReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:permanentBuildingIdentifierReference :string]])

(def AreaToBeBuiltForSpecificActivities
  [:map
   {:closed true,
    :description "Erityistä toimintaa varten rakennettava alue"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:address [:maybe [:vector [:ref :ryhti/Address]]]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:maybe
     [:vector
      [:ref :ryhti/DemolitionMaterialAndConstructionWasteReport]]]]
   [:specificActivitiesAreaType
    [:maybe
     (into
      [:enum
       {:description "Erityistä toimintaa varten rakennettavan alueen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi\">http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi</a>"}]
      (sort (vals mappings/etvra-tyyppi)))]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesign]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:chargingPoint
    {:optional true}
    [:maybe [:ref {:description "Latauspiste"} :ryhti/ChargingPoint]]]
   [:demolitionDeadline {:optional true} [:maybe :string]]
   [:areaToBeBuiltForSpecificActivitiesKey :string]
   [:administrativeLocationUnit
    [:maybe
     [:ref
      {:description "Hallinnollinen sijaintiyksikkö"}
      :ryhti/AdministrativeLocationUnit]]]
   [:specificActivitiesAreaArea {:optional true} [:maybe :int]]
   [:networkConnection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/NetworkConnection]]]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesign]]]]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe
     [:ref
      {:description "Hallinnollinen sijaintiyksikkö"}
      :ryhti/AdministrativeLocationUnit]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:buildingObjectType
    [:enum
     {:description "Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"}
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:temporary :boolean]
   [:buildingInformationModel
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingInformationModel]]]]
   [:completionDate {:optional true} [:maybe :string]]
   [:buildingObjectOwner
    [:maybe
     [:vector
      {:description "Rakennuskohteen omistaja"}
      [:ref :ryhti/BuildingObjectOwner]]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def GeneralizedBuildingPermitIssue
  [:map
   {:closed true}
   [:locationPermitObject
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GeneralizedLocationPermitObject]]]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecision]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamislupa-asian päivityksen tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne\">http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne</a>"}
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:buildingPermitApplication
    {:optional true}
    [:maybe
     [:vector
      {:description "Rakentamislupahakemus"}
      [:ref :ryhti/BuildingPermitApplication]]]]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    {:optional true}
    [:vector [:ref :ryhti/GeneralizedConstructionAction]]]
   [:constructionProject
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamishanke"}
      :ryhti/ConstructionProject]]]
   [:decision
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamislupapäätös"}
      :ryhti/BuildingPermitDecision]]]
   [:buildingSite
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermit]]]]])

(def PlannerStructureReference
  [:map
   {:closed true}
   [:structureSectionReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:permanentStructureIdentifierReference :string]])

(def DeviationPermitIssue
  [:map
   {:closed true, :description "Poikkeamislupa-asia"}
   [:deviationPermitApplication
    [:ref
     {:description "Poikkeamislupahakemus"}
     :ryhti/DeviationPermitApplication]]
   [:deviationPermitDecision
    [:ref
     {:description "Poikkeamislupapäätös"}
     :ryhti/DeviationPermitDecision]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:deviationObject [:vector [:ref :ryhti/DeviationObject]]]
   [:buildingSite
    [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:municipalityNumber :string]])

(def ChangeInformationAreaToBeBuiltForSpecificActivities
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:address
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Address]]]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:maybe
     [:vector
      [:ref :ryhti/DemolitionMaterialAndConstructionWasteReport]]]]
   [:specificActivitiesAreaType {:optional true} [:maybe :string]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesign]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:demolitionDeadline {:optional true} [:maybe :string]]
   [:areaToBeBuiltForSpecificActivitiesKey {:optional true} :string]
   [:administrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:specificActivitiesAreaArea {:optional true} [:maybe :int]]
   [:networkConnection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/NetworkConnection]]]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesign]]]]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:buildingObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:temporary {:optional true} :boolean]
   [:buildingInformationModel
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingInformationModel]]]]
   [:completionDate {:optional true} [:maybe :string]]
   [:buildingObjectOwner
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/ChangeInformationBuildingObjectOwner]]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def GeoJsonMultiPolygonGeometry
  [:map
   {:closed true, :description "GeoJSON MultiPolygon"}
   [:coordinates [:vector [:vector [:vector [:vector :double]]]]]
   [:type [:enum "MultiPolygon"]]])

(def ConstructionDesignNoPersonData
  [:map
   {:closed true}
   [:constructionDesignKey :string]
   [:constructionDesignType
    (into
     [:enum
      {:description "Rakennusssuunnitelman laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/raksuunlajit\">http://uri.suomi.fi/codelist/rytj/raksuunlajit</a>"}]
     (sort (vals mappings/raksuunlajit)))]
   [:document
    {:optional true}
    [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]])

(def BuildingAttachmentDocumentNoPersonData
  [:map
   {:closed true}
   [:documentDate :string]
   [:arrivedDate {:optional true} [:maybe :string]]
   [:retentionTime
    (into
     [:enum
      {:description "Asiakirjan säilytysaika vuosina ennen sen hävittämistä. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/sailytysaika\">http://uri.suomi.fi/codelist/rytj/sailytysaika</a>"}]
     (sort (vals mappings/sailytysaika)))]
   [:documentIdentifier
    [:string
     {:description "Asiakirjan pysyvä tunnus, esim. diaarinumero tai muu dokumentinhallinnan tunnus."}]]
   [:accessibility {:optional true} [:maybe :boolean]]
   [:descriptors
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Descriptor]]]]
   [:name
    [:ref
     {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
     :ryhti/LanguageString]]
   [:confirmationDate {:optional true} [:maybe :string]]
   [:attachmentType
    {:optional true}
    (into
     [:enum
      {:description "Liiteasiakirjan laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/ravaasiaki\">http://uri.suomi.fi/codelist/rytj/ravaasiaki</a>"}]
     (sort (vals mappings/asiakirjan-laji)))]
   [:fileKey
    [:string
     {:description "Erillisen rajapinnan kautta tallennetun tiedoston avain."}]]
   [:attachmentDocumentKey
    [:string
     {:description "Tiedon tuottajatahon tietojärjestelmän generoima kohteen versioriippumaton tunnus"}]]
   [:personalDataContent
    (into
     [:enum
      {:description "Kuvaa asiakirjan henkilötietosisällön. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/henkilotietosisalto\">http://uri.suomi.fi/codelist/rytj/henkilotietosisalto</a>"}]
     (sort (vals mappings/henkilotietosisalto)))]
   [:languages
    [:vector
     {:description nil}
     (into
      [:enum
       {:description "Asiakirjan kieli tai sisältämät kielet. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/ryhtikielet\">http://uri.suomi.fi/codelist/rytj/ryhtikielet</a>"}]
      (sort (vals mappings/ryhtikielet)))]]
   [:categoryOfPublicity
    (into
     [:enum
      {:description "Kuvaa asiakirjan julkisuusluokan. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/julkisuus\">http://uri.suomi.fi/codelist/rytj/julkisuus</a>"}]
     (sort (vals mappings/asiakirjan-julkisuusluokka)))]])

(def DeviationObject
  [:map
   {:closed true, :description "Poikkeamisen kohde"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:address [:vector [:ref :ryhti/Address]]]
   [:deviationObjectKey :string]
   [:totalArea {:optional true} [:maybe :int]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:administrativeLocationUnit
    [:ref
     {:description "Hallinnollinen sijaintiyksikkö"}
     :ryhti/AdministrativeLocationUnit]]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe
     [:ref
      {:description "Hallinnollinen sijaintiyksikkö"}
      :ryhti/AdministrativeLocationUnit]]]
   [:buildingObjectType
    [:enum
     {:description "Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"}
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:ref
     {:description "Rakennuskohteen sijaintitiedot"}
     :ryhti/BuildingObjectLocationData]]
   [:buildingObjectOwner
    [:vector
     {:description "Rakennuskohteen omistaja"}
     [:ref :ryhti/BuildingObjectOwner]]]])

(def CoolingMethod
  [:map
   {:closed true, :description "Jäähdytystapa"}
   [:coolingMethodKey :string]
   [:isPrimary :boolean]
   [:coolingMethodType
    (into
     [:enum
      {:description "Jäähdytystavan laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/jaahdytystapa\">http://uri.suomi.fi/codelist/rytj/jaahdytystapa</a>"}]
     (sort (vals mappings/jaahdytystapa)))]])

(def EnergyData
  [:map
   {:closed true, :description "Energiatiedot"}
   [:coolingEnergySource
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CoolingEnergySource]]]]
   [:heatedVolume {:optional true} [:maybe :int]]
   [:coolingMethod
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CoolingMethod]]]]
   [:energyEfficiencyRating
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/01"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/02"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/03"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/04"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/05"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/06"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/07"
      "http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka/code/08"]]]
   [:electricalEnergySource
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ElectricalEnergySource]]]]
   [:netHeatedArea {:optional true} [:maybe :int]]
   [:computationalEnergyEfficiencyComparand
    {:optional true}
    [:maybe :int]]
   [:energyRatingSubindex
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Energiatehokkuusluokan alaindeksi. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/energialuokkaAlaindeksi"}]
      (sort (vals mappings/energialuokka-alaindeksi)))]]
   [:calculatoryDeliveredEnergyUsage
    {:optional true}
    [:maybe [:ref :ryhti/CalculatoryDeliveredEnergyUsage]]]
   [:energyDataKey :string]
   [:heatingEnergySource
    {:optional true}
    [:maybe [:vector [:ref :ryhti/HeatingEnergySource]]]]
   [:heatingMethod
    {:optional true}
    [:maybe [:vector [:ref :ryhti/HeatingMethod]]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def GeneralizedBuildingSectionNoPersonData
  [:map
   {:closed true}
   [:entranceInOtherBuildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/EntranceInOtherBuildingSection]]]]
   [:partitionReason
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/2"
     "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/3"]]
   [:demolitionMaterialAndConstructionWasteReport
    {:optional true}
    [:maybe
     [:vector
      [:ref
       :ryhti/DemolitionMaterialAndConstructionWasteReportNoPersonData]]]]
   [:interiorData
    {:optional true}
    [:maybe
     [:ref {:description "Sisätilojen tiedot"} :ryhti/InteriorData]]]
   [:exteriorData
    {:optional true}
    [:maybe
     [:ref {:description "Ulkokuoren tiedot"} :ryhti/ExteriorData]]]
   [:elevatorInOtherBuildingSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ElevatorInOtherBuildingSection]]]]
   [:relatedFinishedBuildingSection {:optional true} [:maybe :string]]
   [:partitionDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:materialData
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationMaterialData]]]
   [:specialDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/SpecialDesignNoPersonData]]]]
   [:protectionMethod {:optional true} [:maybe :string]]
   [:civilDefenceShelter
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/ChangeInformationCivilDefenceShelter]]]]
   [:equipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Equipment]]]]
   [:usageData {:optional true} [:ref :ryhti/GeneralizedUsageData]]
   [:demolitionDate {:optional true} [:maybe :string]]
   [:buildingSectionKey {:optional true} :string]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:lifeCycleState
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/01"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/02"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/03"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/04"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/05"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/06"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/07"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/08"
      "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/99"]]]
   [:cultureHistoricalSignificance {:optional true} [:maybe :string]]
   [:constructionDesign
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ConstructionDesignNoPersonData]]]]
   [:elevator
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Elevator]]]]
   [:entrance
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Entrance]]]]
   [:apartment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangeInformationApartment]]]]
   [:assemblyFacility
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AssemblyFacility]]]]
   [:buildingServicesEngineeringData
    {:optional true}
    [:maybe
     [:ref
      {:description "Talotekniikkatiedot"}
      :ryhti/BuildingServicesEngineeringData]]]
   [:internalNetwork
    {:optional true}
    [:maybe [:ref :ryhti/InternalNetwork]]]
   [:buildingInformationModel
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingInformationModelNoPersonData]]]]
   [:energyData
    {:optional true}
    [:maybe [:ref :ryhti/EnergyDataNoPersonData]]]
   [:completionDate {:optional true} [:maybe :string]]
   [:attachment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]])

(def BuildingMaterialOfLoadBearingStructures
  [:map
   {:closed true, :description "Kantavien rakenteiden rakennusaine"}
   [:isPrimary [:maybe :boolean]]
   [:buildingMaterialOfLoadBearingStructuresType
    (into
     [:enum
      {:description "Kantavan rakenteen rakennusaine. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine\">http://uri.suomi.fi/codelist/rytj/kantavanrakenteenrakennusaine</a>"}]
     (sort (vals mappings/kantava-rakennusaine)))]
   [:buildingMaterialOfLoadBearingStructuresKey :string]])

(def ApartmentEquipment
  [:map
   {:closed true, :description "Huoneiston varuste"}
   [:numberOfEquipment :int]
   [:apartmentEquipmentType
    [:maybe
     (into
      [:enum
       {:description "Huoneiston varusteen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste\">http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste</a>"}]
      (sort (vals mappings/huoneiston-varuste)))]]
   [:apartmentEquipmentKey :string]])

(def ElevatorInOtherBuildingSection
  [:map
   {:closed true}
   [:elevatorInOtherBuildingSectionKey :string]
   [:otherElevatorKey :string]
   [:otherElevatorBuildingIdentifier :string]])

(def Operator
  [:map
   {:closed true, :description "Toimija"}
   [:isPerson :boolean]
   [:deathDate {:optional true} [:maybe :string]]
   [:operatorAddress
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ContactAddress]]]]
   [:firstName {:optional true} [:maybe :string]]
   [:organizationName {:optional true} [:maybe :string]]
   [:otherId {:optional true} [:maybe :string]]
   [:familyName {:optional true} [:maybe :string]]
   [:personalIdentityCode {:optional true} [:maybe :string]]
   [:languageCode
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "IETF kielikoodi. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/interoperabilityplatform/languagecodes"}]
      mappings/main-language-codes)]]
   [:businessId {:optional true} [:maybe :string]]
   [:nationalityCode
    {:optional true}
    [:maybe
     [:any
      {:description "Missing codeset: Valtiokoodi. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/jhs/valtio_1_20120101"}]]]])

(def ForemanPlannerConstructionAction
  [:map
   {:closed true}
   [:descriptionOfAction
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:businessConstruction {:optional true} [:maybe :boolean]]
   [:startDate {:optional true} [:maybe :string]]
   [:reasonForDemolition
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/01"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/02"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/03"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/04"
      "http://uri.suomi.fi/codelist/rytj/PurkamisenSyy/code/05"]]]
   [:areaUndergoingChanges {:optional true} [:maybe :int]]
   [:renovation {:optional true} [:maybe :boolean]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:statusOfConstructionAction
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/4"
      "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/5"]]]
   [:fullyApprovedForUse {:optional true} [:maybe :boolean]]
   [:changeType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/01"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/02"
      "http://uri.suomi.fi/codelist/rytj/muutostyo/code/03"]]]
   [:renovationRate {:optional true} [:maybe :int]]
   [:expiryDate {:optional true} [:maybe :string]]
   [:constructionActionKey {:optional true} :string]
   [:completionDate {:optional true} [:maybe :string]]
   [:dvvPermitIdentifier {:optional true} [:maybe :string]]
   [:constructionActionType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/01"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/02"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/03"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/04"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/05"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/06"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/07"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/08"
      "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide/code/09"]]]])

(def GeneralizedBuildingPermitIssueNoPersonData
  [:map
   {:closed true}
   [:locationPermitObject
    {:optional true}
    [:maybe
     [:vector
      [:ref :ryhti/GeneralizedLocationPermitObjectNoPersonData]]]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecisionNoPersonData]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      {:description "Rakentamislupa-asian päivityksen tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne\">http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne</a>"}
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:buildingPermitApplication
    {:optional true}
    [:maybe
     [:vector
      {:description "Rakentamislupahakemus"}
      [:ref :ryhti/BuildingPermitApplication]]]]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    {:optional true}
    [:vector [:ref :ryhti/GeneralizedConstructionActionNoPersonData]]]
   [:constructionProject
    {:optional true}
    [:maybe [:ref :ryhti/ConstructionProjectNoPersonData]]]
   [:decision
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakentamislupapäätös"}
      :ryhti/BuildingPermitDecisionNoPersonData]]]
   [:buildingSite
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermitNoPersonData]]]]])

(def Equipment
  [:map
   {:closed true, :description "Varuste"}
   [:equipmentCount {:optional true} [:maybe :int]]
   [:equipmentType
    (into
     [:enum
      {:description "Rakennuksen varuste. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/rakennusvaruste"}]
     (sort (vals mappings/rakennusvaruste)))]
   [:equipmentKey :string]])

(def ChangeInformationBuilding
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:buildingPurposeForLowCarbonAssessment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingPurposeForLowCarbonAssessment]]]]
   [:buildingSection
    {:optional true}
    [:maybe
     [:vector
      {:description "Rakennuksen osat"}
      [:ref :ryhti/ChangeInformationBuildingSection]]]]
   [:address
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Address]]]]
   [:mainPurpose
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Pääasiallinen käyttötarkoitus. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712\">http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712</a>"}]
      (sort (vals mappings/rakennusluokitus-code->uri)))]]
   [:buildingProtectionMethod
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingProtectionMethod]]]]
   [:renovationDate {:optional true} [:maybe :string]]
   [:buildingCultureHistoricalSignificance
    {:optional true}
    [:maybe :string]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:chargingPoint
    {:optional true}
    [:maybe [:ref {:description "Latauspiste"} :ryhti/ChargingPoint]]]
   [:demolitionDeadline
    {:optional true}
    [:maybe [:string {:description "Demolition deadline"}]]]
   [:permanentBuildingIdentifier
    {:optional true}
    [:string {:description "Pysyvä rakennustunnus (PRT)"}]]
   [:administrativeLocationUnit
    {:optional true}
    [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]
   [:numberOfStoreys
    {:optional true}
    [:maybe [:int {:description "Kerrosluku"}]]]
   [:numberOfDeletedApartments {:optional true} [:maybe :int]]
   [:numberOfNewApartments {:optional true} [:maybe :int]]
   [:buildingKey {:optional true} :string]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:buildingProductList
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennustuoteluettelo"}
      :ryhti/BuildingProductList]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:addChangeEvent {:optional true} [:maybe :boolean]]
   [:buildingObjectType
    {:optional true}
    [:enum
     {:description "Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"}
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:publicUsage {:optional true} [:maybe :boolean]]
   [:temporary
    {:optional true}
    [:maybe [:boolean {:description "Onko väliaikainen"}]]]
   [:buildingObjectOwner
    {:optional true}
    [:maybe
     [:vector
      {:description "Rakennuskohteen omistaja"}
      [:ref :ryhti/ChangeInformationBuildingObjectOwner]]]]
   [:mainPurpose1994 {:optional true} [:maybe :int]]])

(def ChangeInformationApartment
  [:map
   {:closed true}
   [:apartmentIdentifier {:optional true} :string]
   [:isWaterCloset {:optional true} [:maybe :boolean]]
   [:apartmentStorey {:optional true} [:maybe :int]]
   [:apartmentIdentifierLetterSuffix
    {:optional true}
    [:maybe :string]]
   [:apartmentSubdivisionLetter {:optional true} [:maybe :string]]
   [:apartmentEquipment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ApartmentEquipment]]]]
   [:kitchenType
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Keittiötyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Keittiotyyppi\">http://uri.suomi.fi/codelist/rytj/Keittiotyyppi</a>"}]
      (sort (vals mappings/keittiotyyppi)))]]
   [:floorArea {:optional true} [:maybe :double]]
   [:lifeCycleState
    {:optional true}
    [:maybe
     [:enum
      {:description "Huoneiston elinkaaren vaihe. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe\">http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe</a>"}
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/1"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/2"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/3"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/4"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/5"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/6"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/7"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/8"
      "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/9"]]]
   [:commissioningDate {:optional true} [:maybe :string]]
   [:specificationOfLocationFloor
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Sijaintikerroksen tarkenne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne\">http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne</a>"}]
      (sort (vals mappings/sijaintikerroksen-tarkenne)))]]
   [:apartmentKey {:optional true} :string]
   [:housingType {:optional true} [:maybe :string]]
   [:addressNumber {:optional true} :int]
   [:apartmentType
    {:optional true}
    (into
     [:enum
      {:description "Huoneistotyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi\">http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi</a>"}]
     (sort (vals mappings/huoneistotyyppi)))]
   [:occupancyOfApartment {:optional true} [:maybe :string]]
   [:apartmentChangeType
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Huoneiston muutoksen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji\">http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji</a>"}]
      (sort (vals mappings/huoneiston-muutoksen-laji)))]]
   [:numberOfRooms {:optional true} [:maybe :int]]
   [:apartmentNumber {:optional true} :int]])

(def CivilDefenceShelterOtherBuildings
  [:map
   {:closed true, :description "Väestönsuojan muut rakennukset"}
   [:civilDefenceShelterOtherBuildingsKey :string]
   [:permanentBuildingIdentifier :string]
   [:civilDefenceShelterPointedCapacity :int]])

(def ChangeInformationStructure
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:structureSection
    {:optional true}
    [:maybe [:vector [:ref :ryhti/StructureSection]]]]
   [:address [:maybe [:vector [:ref :ryhti/Address]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:chargingPoint
    {:optional true}
    [:maybe [:ref {:description "Latauspiste"} :ryhti/ChargingPoint]]]
   [:structureMainPurpose
    (into
     [:enum
      {:description "Rakennelman käyttötarkoitus. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus\">http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus</a>"}]
     (sort (vals mappings/rakennelman-kayttotarkoitus)))]
   [:demolitionDeadline {:optional true} [:maybe :string]]
   [:permanentStructureIdentifier :string]
   [:administrativeLocationUnit
    {:optional true}
    [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]
   [:numberOfStoreys {:optional true} [:maybe :int]]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:maybe [:ref :ryhti/ChangeInformationAdministrativeLocationUnit]]]
   [:bikespaceCount {:optional true} [:maybe :int]]
   [:buildingObjectType
    [:enum
     {:description "Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"}
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:maybe
     [:ref
      {:description "Rakennuskohteen sijaintitiedot"}
      :ryhti/BuildingObjectLocationData]]]
   [:temporary [:maybe :boolean]]
   [:buildingObjectOwner
    {:optional true}
    [:maybe
     [:vector
      {:description "Rakennuskohteen omistaja"}
      [:ref :ryhti/ChangeInformationBuildingObjectOwner]]]]
   [:structureKey :string]])

(def CreatePermanentPermitIdentifierCommand
  [:map
   {:closed true,
    :description "Command for creating permanent permit identifier."}
   [:permitType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi/code/01"
     "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi/code/02"
     "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi/code/03"
     "http://uri.suomi.fi/codelist/rytj/RakennusvalvontaAsianTyyppi/code/04"]]
   [:municipalityPermitId {:optional true} :string]
   [:municipalityNumber {:optional true} :string]])

(def AcceptedDeviation
  [:map
   {:closed true,
    :description "Luvan yhteydessä myönnettävä poikkeaminen"}
   [:acceptedDeviationKey :string]
   [:contentOfDeviation
    [:ref
     {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
     :ryhti/LanguageString]]
   [:deviationType
    (into
     [:enum
      {:description "Poikkeamisen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji\">http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji</a>"}]
     (sort (vals mappings/poikkeamisen-laji)))]])

(def ExteriorData
  [:map
   {:closed true, :description "Ulkokuoren tiedot"}
   [:shape3d {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry3d]]]
   [:totalArea {:optional true} [:maybe :int]]
   [:relationToGroundLevel
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Suhde maan pintaan. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/Suhde_pintaan"}]
      (sort (vals mappings/suhde-pintaan)))]]
   [:flightObstacle {:optional true} [:maybe :boolean]]
   [:volume {:optional true} [:maybe :int]]
   [:numberOfStoreys {:optional true} [:maybe :int]]
   [:permittedRidgeHeight {:optional true} [:maybe :double]]
   [:permittedBuildingArea {:optional true} [:maybe :int]]
   [:shape {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:permittedVolume {:optional true} [:maybe :int]]
   [:grossFloorArea {:optional true} [:maybe :int]]
   [:height {:optional true} [:maybe :int]]])

(def PlannerBuildingReference
  [:map
   {:closed true}
   [:buildingSectionReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:permanentBuildingIdentifierReference :string]])

(def CustomValidationError
  [:map
   {:closed true}
   [:localizedMessage
    {:optional true}
    [:maybe [:ref :ryhti/LocalizedMessage]]]
   [:classKey {:optional true} [:maybe :string]]
   [:instance {:optional true} [:maybe :string]]
   [:ruleId {:optional true} [:maybe :string]]
   [:message {:optional true} [:maybe :string]]])

(def LocalizedMessage
  [:map
   {:closed true}
   [:fi {:optional true} [:maybe :string]]
   [:en {:optional true} [:maybe :string]]
   [:sv {:optional true} [:maybe :string]]])

(def Inspection
  [:map
   {:closed true, :description "Katselmus"}
   [:requiredByPermitRegulations :boolean]
   [:partiality :string]
   [:structureReference
    {:optional true}
    [:maybe [:vector [:ref :ryhti/InspectionStructureReference]]]]
   [:inspectionRemarks
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:inspectionCarriedOutByFirstName :string]
   [:buildingsAttachmentDocument
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]
   [:buildingObjectChange
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingObjectChange]]]]
   [:inspectionCarriedOutByLastName :string]
   [:specificAreaReference
    {:optional true}
    [:maybe [:vector :string]]]
   [:specificationOfObjectOfInspection
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:personsPresent :string]
   [:buildingReference
    {:optional true}
    [:maybe [:vector [:ref :ryhti/InspectionBuildingReference]]]]
   [:inspectionType
    [:enum
     {:description "Katselmuksen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Katselmuslaji\">http://uri.suomi.fi/codelist/rytj/Katselmuslaji</a>"}
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/01"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/02"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/03"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/04"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/05"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/06"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/07"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/08"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/09"
     "http://uri.suomi.fi/codelist/rytj/Katselmuslaji/code/10"]]
   [:inspectionDate :string]
   [:inspectionKey :string]
   [:inspectionStatus
    (into
     [:enum
      {:description "Katselmuksen tilanne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/KatselmuksenTilanne\">http://uri.suomi.fi/codelist/rytj/KatselmuksenTilanne</a>"}]
     (sort (vals mappings/katselmuksen-tilanne)))]])

(def DemolitionPermitDecision
  [:map
   {:closed true, :description "Rakentamislupapäätös"}
   [:publicNoticeDate [:maybe :string]]
   [:demolitionPermitDecisionKey :string]
   [:constructionToBeStartedBy {:optional true} [:maybe :string]]
   [:engagingParty
    {:optional true}
    [:maybe [:vector [:ref :ryhti/EngagingParty]]]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    [:maybe
     [:ref
      {:description "Liiteasiakirja"}
      :ryhti/BuildingAttachmentDocument]]]
   [:constructionToBeCompletedBy {:optional true} [:maybe :string]]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionToBeStartedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionDate [:maybe :string]]
   [:permitApplicationType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/01"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/02"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"
      "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/04"]]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:constructionToBeCompletedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def DemolitionMaterialAndConstructionWasteReport
  [:map
   {:closed true}
   [:demolitionMaterialAndConstructionWasteReportKey :string]
   [:demolitionMaterialRegisterIdentifier :string]
   [:demolitionProjectType [:maybe :string]]
   [:document
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def BuildingProtectionMethod
  [:map
   {:closed true, :description "Rakennuksen suojelutapa"}
   [:objectName {:optional true} [:maybe :string]]
   [:protectionMethod {:optional true} [:maybe :string]]
   [:prohibitionOfEndangermentEffectiveDate
    {:optional true}
    [:maybe :string]]
   [:buildingName {:optional true} [:maybe :string]]
   [:informationLink {:optional true} [:maybe :string]]
   [:objectDescription {:optional true} [:maybe :string]]
   [:prohibitionOfEndangerment {:optional true} [:maybe :boolean]]
   [:additionalInformation {:optional true} [:maybe :string]]
   [:dateOfLegalValidity {:optional true} [:maybe :string]]
   [:buildingProtectionMethodKey {:optional true} :string]])

(def CivilDefenceShelter
  [:map
   {:closed true, :description "Väestönsuoja"}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:civilDefenceShelterKey :string]
   [:civilDefenceShelterAdditionalInformation
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:civilDefenceShelterShared :boolean]
   [:civilDefenceShelterCapacity
    [:int {:description "Väestönsuojan suojapaikkojen määrä"}]]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:shelterArea {:optional true} :int]
   [:civilDefenceShelterClass
    [:maybe
     (into
      [:enum
       {:description "Väestönsuojan luokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka\">http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka</a>"}]
      (sort (vals mappings/vaestonsuoja-luokka)))]]
   [:civilDefenceShelterOtherBuildings
    {:optional true}
    [:maybe [:vector [:ref :ryhti/CivilDefenceShelterOtherBuildings]]]]])

(def MaterialData
  [:map
   {:closed true, :description "Materiaalitiedot"}
   [:buildingMaterialOfLoadBearingStructures
    [:vector
     {:description "Kantavien rakenteiden rakennusaine"}
     [:ref :ryhti/BuildingMaterialOfLoadBearingStructures]]]
   [:wideBodiedBuilding :boolean]
   [:facadeMaterial
    {:optional true}
    [:maybe [:vector [:ref :ryhti/FacadeMaterial]]]]
   [:reusableMaterialAmount {:optional true} [:maybe :double]]
   [:fireClass
    {:optional true}
    [:maybe
     (into
      [:enum
       {:description "Paloluokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Paloluokka\">http://uri.suomi.fi/codelist/rytj/Paloluokka</a>"}]
      (sort (vals mappings/paloluokka)))]]
   [:constructionMethod
    [:maybe
     (into
      [:enum
       {:description "Rakentamistapa. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_runkotapa\">http://uri.suomi.fi/codelist/vtj/Rake_runkotapa</a>"}]
      (sort (vals mappings/rakentamistapa)))]]])

(def BuildingPurposeForLowCarbonAssessment
  [:map
   {:closed true,
    :description "Käyttötarkoitus vähähiilisyyden arvioinnissa"}
   [:buildingPurposeForLowCarbonAssessmentKey
    {:optional true}
    :string]
   [:climateReport
    {:optional true}
    [:maybe
     [:ref {:description "Ilmastoselvitys"} :ryhti/ClimateReport]]]
   [:isPrimary {:optional true} :boolean]
   [:buildingPurposeForLowCarbonAssessmentType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/2"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/3"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/4"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/5"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/6"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/7"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/8"
     "http://uri.suomi.fi/codelist/rytj/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa/code/9"]]])

(def InteriorData
  [:map
   {:closed true, :description "Sisätilojen tiedot"}
   [:basementArea {:optional true} [:maybe :int]]
   [:atticArea {:optional true} [:maybe :int]]
   [:floorArea {:optional true} [:maybe :double]]])

(def ElectricalEnergySource
  [:map
   {:closed true, :description "Sähköenergian lähde"}
   [:electricalEnergySourceKey :string]
   [:electricalEnergySourceType
    (into
     [:enum
      {:description "Sähköenergianlähteen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde\">http://uri.suomi.fi/codelist/rytj/sahkoenergianlahde</a>"}]
     (sort (vals mappings/sahkoenergianlahde)))]
   [:isPrimary :boolean]])

(def BuildingSiteBuildingProduct
  [:map
   {:closed true,
    :description "Rakennuspaikan rakennustuotteet ja niiden hyödyntäminen"}
   [:buildingSiteBuildingProductType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/RakennuspaikanRakennustuotteenLaji/code/01"
     "http://uri.suomi.fi/codelist/rytj/RakennuspaikanRakennustuotteenLaji/code/02"
     "http://uri.suomi.fi/codelist/rytj/RakennuspaikanRakennustuotteenLaji/code/03"
     "http://uri.suomi.fi/codelist/rytj/RakennuspaikanRakennustuotteenLaji/code/04"]]
   [:productUtilizationAmountUnit {:optional true} [:maybe :string]]
   [:buildingSiteBuildingProductKey {:optional true} :string]
   [:productDescription {:optional true} [:maybe :string]]
   [:buildingSiteKey {:optional true} :string]
   [:productAmount {:optional true} :int]
   [:productUtilizationAmount {:optional true} [:maybe :int]]
   [:productAmountUnit {:optional true} :string]])

(def GeneralizedLocationPermitObject
  [:map
   {:closed true}
   [:description
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:address {:optional true} [:vector [:ref :ryhti/Address]]]
   [:specificActivitiesAreaType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/01"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/02"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/03"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/04"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/05"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/06"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/07"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/08"
      "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi/code/99"]]]
   [:totalArea {:optional true} [:maybe :int]]
   [:facadeMaterial
    {:optional true}
    [:maybe [:vector [:ref :ryhti/FacadeMaterial]]]]
   [:name
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:structureMainPurpose
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/001"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/002"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/003"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/004"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/005"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/006"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/007"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/008"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/009"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/010"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/011"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/012"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/013"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/014"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/015"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/016"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/017"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/018"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/019"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/020"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/021"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/022"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/023"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/024"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/025"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/026"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/027"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/028"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/029"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/030"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/031"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/032"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/033"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/034"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/035"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/036"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/037"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/038"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/039"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/040"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/041"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/042"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/043"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/044"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/045"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/046"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/047"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/048"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/049"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/050"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/051"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/052"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/053"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/054"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/055"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/056"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/057"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/058"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/059"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/060"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/061"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/062"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/063"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/064"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/065"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/066"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/067"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/068"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/069"
      "http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus/code/070"]]]
   [:geometry {:optional true} [:maybe [:ref :ryhti/RyhtiGeometry]]]
   [:administrativeLocationUnit
    {:optional true}
    [:ref
     {:description "Hallinnollinen sijaintiyksikkö"}
     :ryhti/AdministrativeLocationUnit]]
   [:locationPermitObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/01"
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/02"
     "http://uri.suomi.fi/codelist/rytj/SijoittamislupakohteenLaji/code/03"]]
   [:specificActivitiesAreaArea {:optional true} [:maybe :int]]
   [:volume {:optional true} [:maybe :int]]
   [:locationPermitObjectKey {:optional true} :string]
   [:decisionAdministrativeLocationUnit
    {:optional true}
    [:ref
     {:description "Hallinnollinen sijaintiyksikkö"}
     :ryhti/AdministrativeLocationUnit]]
   [:buildingObjectType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1"
     "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/2"]]
   [:location
    {:optional true}
    [:ref
     {:description "Rakennuskohteen sijaintitiedot"}
     :ryhti/BuildingObjectLocationData]]
   [:buildingMainPurpose
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/01"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/011"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0110"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0111"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0112"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/012"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0120"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0121"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/013"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0130"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/014"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0140"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/02"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/021"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0210"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0211"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/03"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/031"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0310"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0311"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0319"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/032"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0320"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0321"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0322"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0329"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/033"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0330"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/04"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/040"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0400"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/05"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/051"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0510"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0511"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0512"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0513"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0514"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/052"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0520"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0521"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/059"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0590"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/06"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/061"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0610"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0611"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0612"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0613"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0614"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0615"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0619"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/062"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0620"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0621"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/063"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0630"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/07"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/071"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0710"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0711"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0712"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0713"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0714"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/072"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0720"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/073"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0730"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0731"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0739"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/074"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0740"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0741"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0742"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0743"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0744"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0749"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/079"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0790"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/08"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/081"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0810"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/082"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0820"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/083"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0830"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/084"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0840"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0841"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/089"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0890"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0891"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/09"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/091"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0910"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0911"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0912"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0919"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/092"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0920"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/093"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0930"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0939"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/10"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/101"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1010"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1011"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/109"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1090"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1091"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/11"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/111"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1110"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/112"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1120"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/113"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1130"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/12"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/121"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1210"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1211"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1212"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1213"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1214"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1215"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/13"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/131"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1310"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1311"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1319"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/14"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/141"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1410"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1411"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1412"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1413"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1414"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1415"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1416"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1419"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/149"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1490"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1491"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1492"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1493"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1499"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/19"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/191"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1910"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1911"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1912"
      "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/1919"]]]
   [:grossFloorArea {:optional true} [:maybe :int]]
   [:buildingObjectOwner
    {:optional true}
    [:vector [:ref :ryhti/BuildingObjectOwner]]]
   [:attachment
    {:optional true}
    [:maybe [:vector [:ref :ryhti/BuildingAttachmentDocument]]]]])

(def GeoJsonMultiPointGeometry
  [:map
   {:closed true, :description "GeoJSON MultiPoint"}
   [:coordinates [:vector [:vector :double]]]
   [:type [:enum "MultiPoint"]]])

(def LandscapeWorkPermitDecision
  [:map
   {:closed true, :description "Maisemalupapäätös"}
   [:publicNoticeDate [:maybe :string]]
   [:constructionToBeStartedBy {:optional true} [:maybe :string]]
   [:engagingParty [:vector [:ref :ryhti/EngagingParty]]]
   [:decisionMakerType
    [:maybe
     (into
      [:enum
       {:description "Päätöksen tekijän tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/PaatoksenTekija\">http://uri.suomi.fi/codelist/rytj/PaatoksenTekija</a>"}]
      (sort (vals mappings/paatoksen-tekija)))]]
   [:decisionDocument
    [:maybe [:ref :ryhti/BuildingAttachmentDocument]]]
   [:landscapeWorkPermitDecisionKey :string]
   [:constructionToBeCompletedBy {:optional true} [:maybe :string]]
   [:relatedApplication [:maybe :string]]
   [:permitRegulation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/PermitRegulation]]]]
   [:decisionText
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:lifeCycleState
    [:maybe
     [:enum
      {:description "Päätöksen elinkaaren tila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila</a>"}
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/1"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/2"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/11"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/12"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/3"
      "http://uri.suomi.fi/codelist/rytj/paatoksen-elinkaaren-tila/code/4"]]]
   [:decisionMakerFirstName {:optional true} [:maybe :string]]
   [:decisionInForceUntil :string]
   [:guidingStatute
    {:optional true}
    [:maybe [:vector [:ref :ryhti/GuidingStatute]]]]
   [:decisionMakerName
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionToBeStartedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionDate [:maybe :string]]
   [:relatedPermit {:optional true} [:maybe [:vector :string]]]
   [:dateOfValidityOfDecision [:maybe :string]]
   [:constructionToBeCompletedByExtension
    {:optional true}
    [:maybe :string]]
   [:decisionArticle {:optional true} [:maybe :string]]
   [:acceptedDeviation
    {:optional true}
    [:maybe [:vector [:ref :ryhti/AcceptedDeviation]]]]
   [:dateOfDecision [:maybe :string]]])

(def DemolitionPermitIssueNoPersonData
  [:map
   {:closed true}
   [:demolitionPermitApplication
    [:maybe
     [:vector
      {:description "Purkamislupahakemus"}
      [:ref :ryhti/DemolitionPermitApplication]]]]
   [:extensionDecision
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ExtensionDecisionNoPersonData]]]]
   [:dateOfInitiation
    [:maybe [:string {:description "Vireilletulopäivä"}]]]
   [:updateType
    {:optional true}
    [:maybe
     [:enum
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/01"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/02"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/03"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/04"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/05"
      "http://uri.suomi.fi/codelist/rytj/paivityksenlaji/code/99"]]]
   [:lifeCycleState
    [:maybe
     (into
      [:enum
       {:description "Elinkaaritila. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila\">http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila</a>"}]
      (sort (vals mappings/rakvalv-asian-elinkaaren-tila)))]]
   [:permanentPermitIdentifier {:optional true} :string]
   [:updateDescription
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:constructionAction
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/GeneralizedConstructionActionNoPersonData]]]]
   [:demolitionDecision
    [:maybe
     [:ref
      {:description "Purkamislupapäätös"}
      :ryhti/DemolitionPermitDecisionNoPersonData]]]
   [:constructionProject
    {:optional true}
    [:maybe [:ref :ryhti/ConstructionProjectNoPersonData]]]
   [:buildingSite
    {:optional true}
    [:maybe
     [:ref {:description "Rakennuspaikka"} :ryhti/BuildingSite]]]
   [:attachment
    {:optional true}
    [:maybe
     [:vector [:ref :ryhti/BuildingAttachmentDocumentNoPersonData]]]]
   [:municipalityNumber :string]
   [:changePermit
    {:optional true}
    [:maybe [:vector [:ref :ryhti/ChangePermitNoPersonData]]]]])

(def HealthReportEntry
  [:map
   {:closed true}
   [:duration {:optional true} :string]
   [:status {:optional true} [:enum "Unhealthy" "Degraded" "Healthy"]]])

(def BuildingAttachmentDocument
  [:map
   {:closed true, :description "Liiteasiakirja"}
   [:documentDate :string]
   [:arrivedDate {:optional true} [:maybe :string]]
   [:retentionTime
    (into
     [:enum
      {:description "Asiakirjan säilytysaika vuosina ennen sen hävittämistä. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/sailytysaika\">http://uri.suomi.fi/codelist/rytj/sailytysaika</a>"}]
     (sort (vals mappings/sailytysaika)))]
   [:documentIdentifier
    [:string
     {:description "Asiakirjan pysyvä tunnus, esim. diaarinumero tai muu dokumentinhallinnan tunnus."}]]
   [:accessibility {:optional true} [:maybe :boolean]]
   [:descriptors
    {:optional true}
    [:maybe [:vector [:ref :ryhti/Descriptor]]]]
   [:name
    [:ref
     {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
     :ryhti/LanguageString]]
   [:confirmationDate {:optional true} [:maybe :string]]
   [:documentCreatorFirstName {:optional true} [:maybe :string]]
   [:attachmentType
    {:optional true}
    (into
     [:enum
      {:description "Liiteasiakirjan laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/ravaasiaki\">http://uri.suomi.fi/codelist/rytj/ravaasiaki</a>"}]
     (sort (vals mappings/asiakirjan-laji)))]
   [:fileKey
    [:string
     {:description "Erillisen rajapinnan kautta tallennetun tiedoston avain."}]]
   [:attachmentDocumentKey
    [:string
     {:description "Tiedon tuottajatahon tietojärjestelmän generoima kohteen versioriippumaton tunnus"}]]
   [:documentCreatorFamilyName {:optional true} [:maybe :string]]
   [:documentCreatorPersonalIdentityCode
    {:optional true}
    [:maybe :string]]
   [:personalDataContent
    (into
     [:enum
      {:description "Kuvaa asiakirjan henkilötietosisällön. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/henkilotietosisalto\">http://uri.suomi.fi/codelist/rytj/henkilotietosisalto</a>"}]
     (sort (vals mappings/henkilotietosisalto)))]
   [:languages
    [:vector
     {:description nil}
     (into
      [:enum
       {:description "Asiakirjan kieli tai sisältämät kielet. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/ryhtikielet\">http://uri.suomi.fi/codelist/rytj/ryhtikielet</a>"}]
      (sort (vals mappings/ryhtikielet)))]]
   [:categoryOfPublicity
    (into
     [:enum
      {:description "Kuvaa asiakirjan julkisuusluokan. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/julkisuus\">http://uri.suomi.fi/codelist/rytj/julkisuus</a>"}]
     (sort (vals mappings/asiakirjan-julkisuusluokka)))]
   [:documentCreatorOtherId {:optional true} [:maybe :string]]])

(def InternalNetwork
  [:map
   {:closed true}
   [:genericOpticalCabling :boolean]
   [:antennaNetwork :boolean]
   [:internalNetworkKey :string]
   [:genericPairCabling :boolean]
   [:existingFiberOpticNetwork {:optional true} [:maybe :boolean]]])

(def ReasonForExceedingThreshold
  [:map
   {:closed true,
    :description "Rakennuksen Hiilijalanjälkeen Raja-arvoista poikkeamisen peruste"}
   [:explanation {:optional true} [:maybe :string]]
   [:justificationType
    {:optional true}
    [:enum
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/01"
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/11"
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/12"
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/13"
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/14"
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/15"
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/02"
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/21"
     "http://uri.suomi.fi/codelist/rytj/is-raja-arvoista-poikkeamisen-laji/code/22"]]
   [:exceedingAmount {:optional true} :double]
   [:reasonForExceedingThresholdKey {:optional true} :string]])

(def GuidingStatute
  [:map
   {:closed true, :description "Säädösviite"}
   [:subSection {:optional true} [:maybe :int]]
   [:statuteName
    {:optional true}
    [:maybe
     [:ref
      {:description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}
      :ryhti/LanguageString]]]
   [:subParagraph {:optional true} [:maybe :string]]
   [:section {:optional true} [:maybe :int]]
   [:statuteCollectionNumber {:optional true} [:maybe :int]]
   [:chapter {:optional true} [:maybe :int]]
   [:statuteCollectionYear {:optional true} [:maybe :int]]
   [:guidingStatuteKey :string]
   [:paragraph {:optional true} [:maybe :int]]])

(def registry
  {:ryhti/GetBuildingOwnerResponse GetBuildingOwnerResponse,
   :ryhti/ForemanBuildingReference ForemanBuildingReference,
   :ryhti/PermanentBuildingIdentifierResponse PermanentBuildingIdentifierResponse,
   :ryhti/GeneralizedBuildingPermitIssue GeneralizedBuildingPermitIssue,
   :ryhti/AreaToBeBuiltForSpecificActivities AreaToBeBuiltForSpecificActivities,
   :ryhti/BuildingSiteCarbonFootprint BuildingSiteCarbonFootprint,
   :ryhti/DeviationPermitApplicationNoPersonData DeviationPermitApplicationNoPersonData,
   :ryhti/BuildingPermitApplication BuildingPermitApplication,
   :ryhti/PlannerBuildingReference PlannerBuildingReference,
   :ryhti/PermanentApartmentIdentifierResponse PermanentApartmentIdentifierResponse,
   :ryhti/Structure Structure,
   :ryhti/HealthReport HealthReport,
   :ryhti/DemolitionMaterialAndConstructionWasteReport DemolitionMaterialAndConstructionWasteReport,
   :ryhti/Planner Planner,
   :ryhti/BuildingInformationModel BuildingInformationModel,
   :ryhti/DemolitionMaterialAndConstructionWasteReportNoPersonData DemolitionMaterialAndConstructionWasteReportNoPersonData,
   :ryhti/GeneralizedBuildingSectionNoPersonData GeneralizedBuildingSectionNoPersonData,
   :ryhti/ForemanPlannerConstructionProject ForemanPlannerConstructionProject,
   :ryhti/LocalizedMessage LocalizedMessage,
   :ryhti/BuildingPermitDecisionNoPersonData BuildingPermitDecisionNoPersonData,
   :ryhti/Inspection Inspection,
   :ryhti/ChangeInformationBuilding ChangeInformationBuilding,
   :ryhti/BuildingObjectOwner BuildingObjectOwner,
   :ryhti/BuildingObjectLocationData BuildingObjectLocationData,
   :ryhti/DemolitionPermitApplication DemolitionPermitApplication,
   :ryhti/Operator Operator,
   :ryhti/BuildingProductList BuildingProductList,
   :ryhti/Entrance Entrance,
   :ryhti/PlannerStructureReference PlannerStructureReference,
   :ryhti/ChangeInformationMaterialData ChangeInformationMaterialData,
   :ryhti/Address Address,
   :ryhti/SpecialDesign SpecialDesign,
   :ryhti/ForemanStructureReference ForemanStructureReference,
   :ryhti/GeoJsonLineStringGeometry GeoJsonLineStringGeometry,
   :ryhti/PermitRegulation PermitRegulation,
   :ryhti/DemolitionPermitIssueNoPersonData DemolitionPermitIssueNoPersonData,
   :ryhti/InspectionStructureReference InspectionStructureReference,
   :ryhti/HealthReportEntry HealthReportEntry,
   :ryhti/LocationPermitObject LocationPermitObject,
   :ryhti/GeneralizedConstructionAction GeneralizedConstructionAction,
   :ryhti/DemolitionPermitDecision DemolitionPermitDecision,
   :ryhti/InspectionNoPersonData InspectionNoPersonData,
   :ryhti/ChangeInformationBuildingPermitIssue ChangeInformationBuildingPermitIssue,
   :ryhti/CivilDefenceShelter CivilDefenceShelter,
   :ryhti/GeneralizedUsageData GeneralizedUsageData,
   :ryhti/BuildingProductListNoPersonData BuildingProductListNoPersonData,
   :ryhti/GeoJsonPointGeometry GeoJsonPointGeometry,
   :ryhti/BuildingPurposeForLowCarbonAssessment BuildingPurposeForLowCarbonAssessment,
   :ryhti/CreatePermanentApartmentIdentifierCommand CreatePermanentApartmentIdentifierCommand,
   :ryhti/Equipment Equipment,
   :ryhti/GeneralizedBuildingNoPersonData GeneralizedBuildingNoPersonData,
   :ryhti/ApartmentEquipment ApartmentEquipment,
   :ryhti/CivilDefenceShelterOtherBuildings CivilDefenceShelterOtherBuildings,
   :ryhti/ChangeInformationApartment ChangeInformationApartment,
   :ryhti/BuildingObjectChange BuildingObjectChange,
   :ryhti/ExtensionDecision ExtensionDecision,
   :ryhti/ForemenPlannersConstructionProject ForemenPlannersConstructionProject,
   :ryhti/GeneralizedBuilding GeneralizedBuilding,
   :ryhti/AcceptedDeviation AcceptedDeviation,
   :ryhti/ElectricalEnergySource ElectricalEnergySource,
   :ryhti/LandscapeWorkPermitDecision LandscapeWorkPermitDecision,
   :ryhti/DeviationPermitIssue DeviationPermitIssue,
   :ryhti/BuildingSection BuildingSection,
   :ryhti/ConstructionDesignNoPersonData ConstructionDesignNoPersonData,
   :ryhti/DeviationPermitIssueNoPersonData DeviationPermitIssueNoPersonData,
   :ryhti/GetForemanPlannerPermitsResponse GetForemanPlannerPermitsResponse,
   :ryhti/BuildingMaterialOfLoadBearingStructures BuildingMaterialOfLoadBearingStructures,
   :ryhti/DeviationObject DeviationObject,
   :ryhti/ChangeInformationAreaToBeBuiltForSpecificActivitiesNoPersonData ChangeInformationAreaToBeBuiltForSpecificActivitiesNoPersonData,
   :ryhti/ConstructionAction ConstructionAction,
   :ryhti/GeneralizedConstructionActionNoPersonData GeneralizedConstructionActionNoPersonData,
   :ryhti/HeatingEnergySource HeatingEnergySource,
   :ryhti/DeviationPermitDecision DeviationPermitDecision,
   :ryhti/EngagingParty EngagingParty,
   :ryhti/EntranceInOtherBuildingSection EntranceInOtherBuildingSection,
   :ryhti/MaterialData MaterialData,
   :ryhti/FacadeMaterial FacadeMaterial,
   :ryhti/CalculatoryDeliveredEnergyUsage CalculatoryDeliveredEnergyUsage,
   :ryhti/LandscapeWorkPermitIssue LandscapeWorkPermitIssue,
   :ryhti/BuildingSiteBuildingProduct BuildingSiteBuildingProduct,
   :ryhti/ExteriorData ExteriorData,
   :ryhti/CustomValidationError CustomValidationError,
   :ryhti/GeoJsonMultiPolygonGeometry GeoJsonMultiPolygonGeometry,
   :ryhti/GeoJsonMultiLineStringGeometry GeoJsonMultiLineStringGeometry,
   :ryhti/BuildingCarbonFootprint BuildingCarbonFootprint,
   :ryhti/BuildingSiteCarbonHandprint BuildingSiteCarbonHandprint,
   :ryhti/Building Building,
   :ryhti/InspectionBuildingReference InspectionBuildingReference,
   :ryhti/HeatingMethod HeatingMethod,
   :ryhti/CoolingMethod CoolingMethod,
   :ryhti/BuildingProtectionMethod BuildingProtectionMethod,
   :ryhti/ClimateReport ClimateReport,
   :ryhti/ConstructionDesign ConstructionDesign,
   :ryhti/ChangePermit ChangePermit,
   :ryhti/ElevatorInOtherBuildingSection ElevatorInOtherBuildingSection,
   :ryhti/GetForemenPlannersResponse GetForemenPlannersResponse,
   :ryhti/ChangeInformationStructure ChangeInformationStructure,
   :ryhti/CreatePermanentPermitIdentifierCommand CreatePermanentPermitIdentifierCommand,
   :ryhti/ChangeInformationBuildingObjectOwner ChangeInformationBuildingObjectOwner,
   :ryhti/Foreman Foreman,
   :ryhti/InternalNetwork InternalNetwork,
   :ryhti/PermanentPermitIdentifierResponse PermanentPermitIdentifierResponse,
   :ryhti/GuidingStatute GuidingStatute,
   :ryhti/ProblemDetails ProblemDetails,
   :ryhti/RyhtiGeometry RyhtiGeometry,
   :ryhti/Elevator Elevator,
   :ryhti/BuildingPermitDecision BuildingPermitDecision,
   :ryhti/GeneralizedLocationPermitObjectNoPersonData GeneralizedLocationPermitObjectNoPersonData,
   :ryhti/ForemanPlannerConstructionAction ForemanPlannerConstructionAction,
   :ryhti/RyhtiGeometry3d RyhtiGeometry3d,
   :ryhti/GeneralizedBuildingPermitIssueNoPersonData GeneralizedBuildingPermitIssueNoPersonData,
   :ryhti/ChangeInformationAdministrativeLocationUnit ChangeInformationAdministrativeLocationUnit,
   :ryhti/GeoJsonMultiPointGeometry GeoJsonMultiPointGeometry,
   :ryhti/DemolitionPermitIssue DemolitionPermitIssue,
   :ryhti/InteriorData InteriorData,
   :ryhti/ClimateReportNoPersonData ClimateReportNoPersonData,
   :ryhti/CustomValidationProblemDetails CustomValidationProblemDetails,
   :ryhti/BuildingBuildingProduct BuildingBuildingProduct,
   :ryhti/ContactAddress ContactAddress,
   :ryhti/BuildingCarbonHandprint BuildingCarbonHandprint,
   :ryhti/BuildingAttachmentDocument BuildingAttachmentDocument,
   :ryhti/ReasonForExceedingThreshold ReasonForExceedingThreshold,
   :ryhti/ChangeInformationCivilDefenceShelter ChangeInformationCivilDefenceShelter,
   :ryhti/CallForDeviation CallForDeviation,
   :ryhti/AssemblyFacility AssemblyFacility,
   :ryhti/CreatePermanentStructureIdentifierCommand CreatePermanentStructureIdentifierCommand,
   :ryhti/GeoJsonPolygonGeometry GeoJsonPolygonGeometry,
   :ryhti/ForemanPlannerPermitIssue ForemanPlannerPermitIssue,
   :ryhti/GeneralizedBuildingSection GeneralizedBuildingSection,
   :ryhti/GeneralizedLocationPermitObject GeneralizedLocationPermitObject,
   :ryhti/ConstructionProjectNoPersonData ConstructionProjectNoPersonData,
   :ryhti/DeviationObjectNoPersonData DeviationObjectNoPersonData,
   :ryhti/ChargingPoint ChargingPoint,
   :ryhti/ExtensionDecisionNoPersonData ExtensionDecisionNoPersonData,
   :ryhti/SpecialDesignNoPersonData SpecialDesignNoPersonData,
   :ryhti/StructureNoPersonData StructureNoPersonData,
   :ryhti/DeviationPermitApplication DeviationPermitApplication,
   :ryhti/ChangeInformationDemolitionPermitIssue ChangeInformationDemolitionPermitIssue,
   :ryhti/Descriptor Descriptor,
   :ryhti/BuildingInformationModelNoPersonData BuildingInformationModelNoPersonData,
   :ryhti/BuildingObjectIssue BuildingObjectIssue,
   :ryhti/Apartment Apartment,
   :ryhti/BuildingPermitIssue BuildingPermitIssue,
   :ryhti/BuildingPurposeForLowCarbonAssessmentNoPersonData BuildingPurposeForLowCarbonAssessmentNoPersonData,
   :ryhti/ChangePermitNoPersonData ChangePermitNoPersonData,
   :ryhti/BuildingSite BuildingSite,
   :ryhti/LandscapeWorkPermitApplication LandscapeWorkPermitApplication,
   :ryhti/AdministrativeLocationUnit AdministrativeLocationUnit,
   :ryhti/StructureSectionNoPersonData StructureSectionNoPersonData,
   :ryhti/BuildingServicesEngineeringData BuildingServicesEngineeringData,
   :ryhti/DeviationPermitDecisionNoPersonData DeviationPermitDecisionNoPersonData,
   :ryhti/PermanentStructureIdentifierResponse PermanentStructureIdentifierResponse,
   :ryhti/GeneralizedDemolitionPermitIssue GeneralizedDemolitionPermitIssue,
   :ryhti/VentilationMethod VentilationMethod,
   :ryhti/LanguageString LanguageString,
   :ryhti/EnergyDataNoPersonData EnergyDataNoPersonData,
   :ryhti/ConstructionProject ConstructionProject,
   :ryhti/CreatePermanentBuildingIdentifierCommand CreatePermanentBuildingIdentifierCommand,
   :ryhti/ChangeInformationOperator ChangeInformationOperator,
   :ryhti/GeneralizedPurpose GeneralizedPurpose,
   :ryhti/ChangeInformationBuildingSection ChangeInformationBuildingSection,
   :ryhti/ChangeInformationProperty ChangeInformationProperty,
   :ryhti/UsageData UsageData,
   :ryhti/ChangeInformationAreaToBeBuiltForSpecificActivities ChangeInformationAreaToBeBuiltForSpecificActivities,
   :ryhti/BuildingAttachmentDocumentNoPersonData BuildingAttachmentDocumentNoPersonData,
   :ryhti/StructureSection StructureSection,
   :ryhti/CoolingEnergySource CoolingEnergySource,
   :ryhti/EnergyData EnergyData,
   :ryhti/NetworkConnection NetworkConnection,
   :ryhti/ChangeInformationConstructionAction ChangeInformationConstructionAction,
   :ryhti/Purpose Purpose,
   :ryhti/DemolitionPermitDecisionNoPersonData DemolitionPermitDecisionNoPersonData})
