(ns lupapalvelu.ryhti.datamodel.action
  "ConstructionAction, which is largely built from
  one of Building, Structure or Area Built for Specific Purpose."
  (:require [lupapalvelu.operations :as op]
            [lupapalvelu.ryhti.api.malli-schema.generated :as gen]
            [lupapalvelu.ryhti.api.mappings :as mappings]
            [lupapalvelu.ryhti.datamodel.building :refer [address-mapping]]
            [lupapalvelu.ryhti.datamodel.common :refer [->iso-date always-nil apply-mapping loc->language-string
                                                        point-geometry string->number]]
            [lupapalvelu.ryhti.datamodel.party :refer [building-object-owner]]
            [lupapalvelu.ryhti.datamodel.section :as section]
            [sade.date :as date]))

(declare location-mapping
         administrative-location-unit)

;; Reusable fields shared for Building, Structure and Special purpose areas

(def temporary-field
  {:ryhti       [:temporary]
   :lupapiste   [:rakennuskohde :data :kaytto :valiaikainenRakennusKytkin :value]
   :description "Tilapäinen, 1"})

(def number-of-storeys-field
  {:ryhti       [:numberOfStoreys]
   ;; TODO: Later we will need to calculate `(max floors-per-section)`
   :lupapiste   [:rakennuskohde :data :rakennuskohteen-osa :ulkokuorentiedot :kerrosluku :value]
   :->ryhti     string->number
   :nilable?    true
   :description "0..1"})

(def description-field
  {:ryhti       [:description]
   :lupapiste   [:rakennuskohde :data :kuvaus :value]
   :->ryhti     (fn [value _] {:fin value})
   :description "Kuvaus, 0"})

(def name-field
  {:ryhti       [:name]
   :lupapiste   [:rakennuskohde :data :kaytto :nimi :value]
   :->ryhti     (fn [value _] {:fin value})
   :description "Nimi, 0"})

(def demolition-deadline-field
  {:ryhti       [:demolitionDeadline]
   :lupapiste   nil
   ;; TODO:
   :description "Purkamisen määräaika, 0..1"})

(def location-field
  {:ryhti       [:location]
   ;; TODO: This or Tietomallit (/location-operations query?)
   :lupapiste   [:application :location]
   :->ryhti     (fn [location context]
                  (apply-mapping location-mapping (assoc context :location location)))
   :description "Rakennuskohteen sijaintitiedot"})

(def administrative-location-unit-field
  {:ryhti       [:administrativeLocationUnit]
   :lupapiste   [:rakennuskohde :data :sijaintitiedot :sijainti :hallinnollinen-sijainti-yksikko]
   :->ryhti     (fn [x context]
                  (apply-mapping administrative-location-unit (assoc context :hallinnollinen-sijainti-yksikko x)))
   :description "Hallinnollinen sijaintiyksikkö"})

;; Required on permitIssue
(def decision-administrative-location-unit-field
  {:ryhti       [:decisionAdministrativeLocationUnit]
   :lupapiste   [:rakennuskohde :data :sijaintitiedot :sijainti :hallinnollinen-sijainti-yksikko]
   :->ryhti     (fn [x context]
                  (apply-mapping administrative-location-unit (assoc context :hallinnollinen-sijainti-yksikko x)))
   :description "Hallinnollinen sijaintiyksikkö"})

(def building-object-owner-field
  {:ryhti       [:buildingObjectOwner]
   :lupapiste   [:rakennuskohde :data :rakennuksenOmistajat]
   :->ryhti     (fn [omistajat context]
                  (mapv (fn [omistaja]
                          (apply-mapping building-object-owner (assoc context :omistaja omistaja)))
                        (vals omistajat)))
   :description "Rakennuskohteen omistaja"})

(def address-field
  {:ryhti       [:address]
   :lupapiste   [:rakennuskohde :data :sijaintitiedot :osoite]
   :->ryhti     (fn [osoite context]
                  [(apply-mapping address-mapping (assoc context :osoite osoite))])
   :description "Kohteen osoite, 1..*"})

(def charging-point-mapping
  ;; Latauspiste
  [{:ryhti     [:chargingPointKey]
    :lupapiste [:latauspiste :ryhti :chargingPointKey :value]}

   {:ryhti       [:preCabling]
    :lupapiste   [:latauspiste :esikaapelointi :value]
    :description "Esikaapelointi, 1..1"
    :default     false}

   {:ryhti       [:chargingPointCount]
    :lupapiste   [:latauspiste :latauspisteidenMaara :value]
    :->ryhti     string->number
    :description "Latauspisteiden määrä, 1..1"
    :default     0}

   {:ryhti       [:otherCablingRoutes]
    :lupapiste   [:latauspiste :muutJohtoreitit :value]
    :description "Muut johtoreitit, 1..1"
    :default     false}

   {:ryhti       [:conduit]
    :lupapiste   [:latauspiste :putkitus :value]
    :description "Putkitus, 1..1"
    :default     false}])

(def bikespace-count
  "`:bikespaceCount` used to be within `:chargingPoint`."
  {:ryhti       [:bikespaceCount]
   :lupapiste   [:rakennuskohde :data :latauspiste :polkupyorapaikkojenMaara :value]
   :->ryhti     string->number
   :description "Polkupyöräpaikkojen määrä, 0..1"})

(def charging-point-field
  {:ryhti       [:chargingPoint]
   :lupapiste   [:rakennuskohde :data :latauspiste]
   :->ryhti     (fn [v context]
                  (apply-mapping charging-point-mapping (assoc context :latauspiste v)))
   :description "Latauspiste"})

(def building-object-type-field
  {:ryhti       [:buildingObjectType]
   :lupapiste   nil
   :->ryhti     (fn [_v _context]
                  ;; Suunnitelman mukainen tieto
                  "http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji/code/1")
   :description "1. Rakennuskohteen tiedon laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji\">http://uri.suomi.fi/codelist/rytj/rakkohteen-tiedon-laji</a>"})

(def building-purpose-for-low-carbon-assessment-mapping
  ;; 1.66. Käyttötarkoitus vähähiilisyyden arvioinnissa
  [{:ryhti       [:buildingPurposeForLowCarbonAssessmentKey]
    :lupapiste   [:vahahiilisyys :ryhti :buildingPurposeForLowCarbonAssessmentKey :value]
    :->ryhti     (fn [v _context] v)
    :description ""}

   {:ryhti       [:isPrimary]
    ;; TODO: Missing in lupapiste?
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :default     false
    :description "Ensisijainen, 1"}

   {:ryhti       [:buildingPurposeForLowCarbonAssessmentType]
    :lupapiste   [:vahahiilisyys :rakennusluokka-vahahiilisyys :value]
    :->ryhti     (fn [v _context]
                   (get mappings/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa (keyword v)))
    :description "Rakennuksen käyttötarkoitus vähähiilisyyden arvioinnissa, 1"}

   {:ryhti       [:climateReport]
    ;; TODO: Missing or elsewhere in lupapiste?
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Ilmastoselvitys, 1..1"}])

(def ^{:schema gen/Building} building-mapping
  ;; 1.22. Rakennuksen tiedot.
  [{:ryhti     [:buildingKey]
    :lupapiste [:rakennuskohde :data :ryhti :buildingKey :value]}

   {:ryhti       [:permanentBuildingIdentifier]
    :lupapiste   [:rakennuskohde :data :pysyva-rakennustunnus :value]
    :default     "1"
    :description "Pysyvä rakennustunnus (PRT). Kuntajärjestelmä luo tämän vasta käsittelyn loppuvaiheessa."}

   name-field
   description-field
   temporary-field
   ;; demolition-deadline-field
   number-of-storeys-field

   {:ryhti       [:buildingSection]
    :lupapiste   [:rakennuskohde :data :rakennuskohteen-osa]
    :->ryhti     (fn [osa context]
                   [(apply-mapping section/building-section-mapping (assoc context :osa osa))])
    :description "1..*"}

   {:ryhti       [:mainPurpose]
    :lupapiste   [:rakennuskohde :data :kaytto :rakennusluokka :value]
    :nilable?    true
    :->ryhti     (fn [v _context]
                   (mappings/lupapiste-rakennusluokka->uri v))
    :description "Pääasiallinen käyttötarkoitus. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712\">http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712</a>"}

   building-object-type-field
   location-field
   administrative-location-unit-field
   decision-administrative-location-unit-field
   building-object-owner-field
   address-field

   #_
   {:ryhti       [:addChangeEvent]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "lisätäänMuutostapahtuma, 0..1"}

   {:ryhti       [:renovationDate]
    :lupapiste   [:rakennuskohde :data :kaytto :perusparannusPaiva :value]
    :->ryhti     (fn [v _context]
                   ;; value is pp.kk.vvvv
                   (->iso-date (date/zoned-parse v :fi-date)))
    :description "Perusparannuspäivä, 0..1"}

   {:ryhti       [:buildingPurposeForLowCarbonAssessment]
    :lupapiste   [:rakennuskohde :data :rakennusluokka-vahahiilisyys]
    :->ryhti     (fn [v context]
                   [(apply-mapping building-purpose-for-low-carbon-assessment-mapping (assoc context :vahahiilisyys v))])
    :description "Käyttötarkoitus vähähiilisyyden arvioinnissa, 0..*"}

   #_
   {:ryhti       [:buildingProductList]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Rakennustuoteluettelo, 0..1"}

   bikespace-count
   charging-point-field])

(def administrative-location-unit
  ;; 1.23. Hallinnollinen sijaintiyksikkö
  [{:ryhti     [:administrativeLocationUnitKey]
    :lupapiste [:hallinnollinen-sijainti-yksikko :ryhti :administrativeLocationUnitKey :value]}

   {:ryhti       [:propertyIdentifier]
    :lupapiste   [:hallinnollinen-sijainti-yksikko :kiinteistotunnus :value]
    :description "Kiinteistotunnus, 1..1"}

   #_
   {:ryhti       [:unseparatedParcelIdentifier]
    :lupapiste   []
    :description "Määräala tunnus, 0..1"}

   #_
   {:ryhti       [:unitIdentifier]
    :lupapiste   []
    :description "Laitos tunnus, 0..1"}
   ])

(def ^{:schema gen/Structure} structure
  ;; 1.52. Rakennelma
  [{:ryhti     [:structureKey]
    :lupapiste [:rakennuskohde :data :ryhti :structureKey :value]}

   {:ryhti       [:permanentStructureIdentifier]
    :lupapiste   [:rakennuskohde :data :pysyva-rakennelmatunnus :value]
    :default     "1"
    :description "PysyväRakennelmaTunnus, 1. Kuntajärjestelmä hakee tämän."}

   name-field
   description-field

   ;; TODO: PDF: geometry, missing in OAS

   temporary-field
   ;; demolition-deadline-field
   number-of-storeys-field

   {:ryhti       [:structureSection]
    :lupapiste   [:rakennuskohde :data :rakennuskohteen-osa]
    :->ryhti     (fn [osa context]
                   [(apply-mapping section/structure-section-mapping (assoc context :osa osa))])
    :description "1..*"}

   {:ryhti       [:structureMainPurpose]
    :lupapiste   [:rakennuskohde :data :kaytto :rakennelman-kayttotarkoitus :value]
    :->ryhti     (fn [kayttotarkoitus _context]
                   (get mappings/rakennelman-kayttotarkoitus kayttotarkoitus))
    :description "1. Rakennelman käyttötarkoitus. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus\">http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus</a>"}

   building-object-type-field
   location-field
   administrative-location-unit-field
   decision-administrative-location-unit-field
   building-object-owner-field
   address-field
   bikespace-count
   charging-point-field])

(def location-mapping
  ;; 1.61. RakennuskohteenSijaintitiedot
  [{:ryhti     [:buildingObjectLocationDataKey]
    :lupapiste [:rakennuskohde :data :sijaintitiedot :sijainti :ryhti :buildingObjectLocationDataKey :value]}

   {:ryhti       [:votingDistrictNumber]
    :lupapiste   []
    :->ryhti     (fn [_ _]
                   ;; Required, but empty value OK
                   "")
    :description "Äänestysaluenumero, 0..1"}

   {:ryhti       [:pointLocation]
    :lupapiste   [:location]
    :->ryhti     (fn [location _] (point-geometry location))
    :description "Sijaintikeskipiste"}

   #_
   {:ryhti       [:geometry]
    :lupapiste   []
    :description "Muu geometria, 0..*"}])

(def ^{:schema gen/AreaToBeBuiltForSpecificActivities} area-to-be-built-for-specific-activities
  ;; 2.4. Erityistä toimintaa varten rakennettava alue
  [{:ryhti       [:areaToBeBuiltForSpecificActivitiesKey]
    :lupapiste   [:rakennuskohde :data :ryhti :areaToBeBuiltForSpecificActivitiesKey :value]
    :description ""}

   {:ryhti       [:specificActivitiesAreaType]
    :lupapiste   [:rakennuskohde :data :kaytto :erityista-toimintaa-tyyppi :value]
    :->ryhti     (fn [tyyppi _]
                   (get mappings/etvra-tyyppi tyyppi))
    :description "Laji, 1"}

   #_
   {:ryhti       [:networkConnection]
    :lupapiste   nil
    :description "Verkostoliittymä, 0..1"}

   ;; FIXME: pdf has wrong type, is the 1..* correct either?
   #_
   {:ryhti       [:completionDate]
    :lupapiste   nil
    ;; TODO:
    :description "Valmistumispäivämäärä, 1..*"}

   temporary-field
   name-field
   description-field
   ;; demolition-deadline-field
   building-object-type-field
   location-field
   administrative-location-unit-field
   decision-administrative-location-unit-field
   building-object-owner-field
   address-field
   section/building-information-model-field

   #_
   {:ryhti       [:constructionDesign]
    :lupapiste   nil
    :description "Rakennussuunnitelma"}

   #_
   {:ryhti       [:attachment]
    :lupapiste   nil
    :description "Liiteasiakirja"}

   {:ryhti       [:specificActivitiesAreaArea]
    :lupapiste   [:rakennuskohde :data :mitat :pintaAla :value]
    :->ryhti     string->number
    :description "Pinta-ala, 1"}

   ;; FIXME: ETVRA doesn't have charging points? Our schema has this.
   bikespace-count
   charging-point-field

   ;; TODO: Not on tietomallit.pdf?
   #_
   {:ryhti       [:demolitionMaterialAndConstructionWasteReport]
    :lupapiste   nil
    :description ""}

   #_
   {:ryhti       [:specialDesign]
    :lupapiste   nil
    :description "Erityissuunnitelma, 0..*"}
   ])

(def ^{:schema gen/ConstructionAction} construction-action-mapping
  ;; 1.14. Rakentamistoimenpide
  [{:ryhti     [:constructionActionKey]
    :lupapiste [:application :primaryOperation :ryhti :constructionActionKey]}

   {:ryhti       [:constructionActionType]
    :lupapiste   [:application :primaryOperation :name]
    :->ryhti     (fn [primary-operation-name _context]
                   (let [op-info (op/operations (keyword primary-operation-name))]
                     (get mappings/rakentamistoimenpide (:ryhti-rakentamistoimenpide op-info))))
    :description "Toimenpiteenlaji, 1"}

   {:ryhti       [:descriptionOfAction]
    :lupapiste   [:application :primaryOperation :name]
    :->ryhti     (loc->language-string #(str "operations." %))
    :description "Kuvaus, 1"}

   {:ryhti       [:renovation]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   ;; TODO:
                   false)
    :description "Perusparannus, 1"}

   {:ryhti       [:renovationRate]
    :lupapiste   nil
    ;; TODO:
    :->ryhti     always-nil
    :description "Korjausaste, 0..1"}

   {:ryhti       [:statusOfConstructionAction]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   ;; TODO: Koodisto
                   ;; TODO: Oikea arvo
                   ;; Suunniteltu
                   "http://uri.suomi.fi/codelist/rytj/rakkohteen-toimenpiteen-tila/code/1")
    :description "Toimenpiteen tila, 1"}

   {:ryhti       [:businessConstruction]
    :lupapiste   [:rakennuskohde :data :kaytto :rakentaja-tyyppi :value]
    :->ryhti     (fn [value _]
                   (if (boolean? value)
                     value
                     false))
    :description "Liiketaloudellinen rakentaminen, 1"}

   ;; One of building / structure / areaToBeBuilt... is required

                                        ; buildingObjectChange
   {:ryhti       [:building]
    :lupapiste   [:rakennuskohde]
    :->ryhti     (fn [rakennuskohde context]
                   (when (= "rakennus" (-> rakennuskohde :data :kaytto :kohteen-laji :value))
                     (apply-mapping building-mapping (assoc context :rakennuskohde rakennuskohde))))
    :description ""}
                                        ; finishedBuilding

   {:ryhti       [:structure]
    :lupapiste   [:rakennuskohde]
    :->ryhti     (fn [rakennuskohde context]
                   (when (= "rakennelma" (-> rakennuskohde :data :kaytto :kohteen-laji :value))
                     (apply-mapping structure (assoc context :rakennuskohde rakennuskohde))))
    :description ""}
                                        ; TODO: finishedStructure

   {:ryhti       [:areaToBeBuiltForSpecificActivities]
    :lupapiste   [:rakennuskohde]
    :->ryhti     (fn [rakennuskohde context]
                   (when (= "etvra" (-> rakennuskohde :data :kaytto :kohteen-laji :value))
                     (apply-mapping area-to-be-built-for-specific-activities (assoc context :rakennuskohde rakennuskohde))))
    :description ""}

   {:ryhti       [:finishedAreaToBeBuiltForSpecificActivities]
    :lupapiste   nil
    :->ryhti     always-nil
    :description ""}

   ;; fullyApprovedForUse
   ;; changeType
   ;; reasonForDemolition
   ;; dvvPermitIdentifier
   ;; areaUndergoingChanges

   ;; commissioningDate
   ;; expiryDate
   ;; completionDate
   ;; startDate
   ])
