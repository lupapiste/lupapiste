(ns lupapalvelu.ryhti.datamodel.section
  "BuildingSection and StructureSection"
  (:require [lupapalvelu.ryhti.api.malli-schema.generated :as gen]
            [lupapalvelu.ryhti.api.mappings :as mappings]
            [lupapalvelu.ryhti.datamodel.attachment :as attachment]
            [lupapalvelu.ryhti.datamodel.apartment :refer [apartment-mapping]]
            [lupapalvelu.ryhti.datamodel.common :refer [apply-mapping point-geometry required-filled string->double
                                                        string->number]]
            [sade.strings :as ss]))

(set! *warn-on-reflection* true)

(def life-cycle-state-field
  {:ryhti       [:lifeCycleState]
   :lupapiste   nil
   :->ryhti     (fn [_v _context]
                  ;; TODO:
                  ;; Suunnitteilla
                  "http://uri.suomi.fi/codelist/rakrek/rakelinvaih/code/01")
   :description "Rakennelman elinkaaren vaihe. 1. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rakrek/rakelinvaih"})

(def completion-date-field
  {:ryhti       [:completionDate]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description "Valmistumispäivämäärä, 0..1"})

(def protection-method-field
  {:ryhti       [:protectionMethod]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description "Suojelutapa, 0..1 Missing codeset: Rakennelman osan suojatapa. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/suojtapa"} )

(def culture-historical-significance-field
  {:ryhti       [:cultureHistoricalSignificance]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description "KulttuurihistoriallinenMerkittävyys, 0..1 Missing codeset: Rakennelman osan kulttuurihistoriallinen merkittävyys. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rakrek/kulthistmer"})

;; TODO: Missing in Lupapiste / Attachments?
(def construction-design-field
  {:ryhti       [:constructionDesign]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description "Rakennussuunnitelma, 0.,*"})

(def building-information-model-mapping
  [
   {:ryhti     [:buildingInformationModelKey]
    :lupapiste [:bim-model :metadata :ryhti :buildingInformationModelKey]
    :->ryhti   (fn [k _] k)
    :required? true}

   {:ryhti     [:buildingInformationModelType]
    :lupapiste [:bim-model :metadata :model-phase]
    :->ryhti   (fn [k _]
                 (case k
                   "submission-time" (:suunnitelmamalli mappings/rakennuksentietomallinlaji)
                   ;; construction-time + completion-time
                   (:toteumamalli mappings/rakennuksentietomallinlaji)))}

   {:ryhti     [:document]
    :lupapiste [:bim-model]
    :->ryhti   (fn [bim-model context]
                 ;; In Ryhti, a single BIM model contains (multiple) attachment(s). In Lupapiste, attachments and
                 ;; BIM models are separate. But we can try to convert a BIM model to a pseudo-attachment and use the existing mapping.
                 (let [bim-as-attachment {:contents      (-> bim-model :metadata :name)
                                          ;; NOTE: We don't have a "BIM-model visibility" selector, at least not yet
                                          :metadata      {:nakyvyys :julkinen}
                                          :ryhti         (-> bim-model :metadata :ryhti)
                                          ;; `asiakirjan-laji` doesn't have anything for BIM models so let's consider them as generic plans
                                          :type          {:type-group nil
                                                          :type-id    :muu_suunnitelma}
                                          :latestVersion {:ryhti (-> bim-model :versions last :metadata :ryhti)}}
                       result            (apply-mapping attachment/attachment-mapping (assoc context :attachment bim-as-attachment))
                       check             (required-filled attachment/attachment-mapping)]
                   (when (check result)
                     [result])))
    :nilable?  true}

   ;; NOTE: Our BIM models (or operations) don't have a location
   ;; so all we can do is return the application location
   {:ryhti     [:referencePoint]
    :lupapiste [:application :location]
    :->ryhti   (fn [location _] (point-geometry location))}])

(def ^{:schema gen/BuildingInformationModel} building-information-model-field
  {:ryhti       [:buildingInformationModel]
   :lupapiste   nil
   :->ryhti     (fn [_v {:keys [operation-id bim-models-by-operation] :as context}]
                  ;; NOTE: Lupapiste binds BIM-models to individual operations, but Ryhti
                  ;; supports BIM-models under e.g. a) the permit itself, b) the ETVRA area, or
                  ;; c) an individual (building/structure) section
                  (->> (get bim-models-by-operation operation-id)
                       (sort-by (comp :buildingInformationModelKey :ryhti :metadata))
                       (map (fn [bim-model]
                              (apply-mapping building-information-model-mapping (assoc context :bim-model bim-model))))
                       (filter (required-filled building-information-model-mapping))
                       (into [])
                       not-empty))
   :description "Rakennustietomalli, 0..*"})

;; TODO: Attachments?
(def special-design-field
  {:ryhti       [:specialDesign]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description ""})

;; TODO: Attachments?
(def attachment-field
  {:ryhti       [:attachment]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description ""})

;; TODO: Missing in Lupapiste?
(def partition-reason-field
  {:ryhti       [:partitionReason]
   :lupapiste   nil
   :->ryhti     (fn [_v _context]
                  "http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji/code/1")
   :description "Rakennelman osan osittelun laji, 1. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/rak-osittelun-laji"})

(def partition-description-field
  {:ryhti       [:partitionDescription]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description "OsitteluKuvaus, 0..1"})

(def demolition-date-field
  {:ryhti       [:demolitionDate]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description "purkamisPäivämäärä, 0..1"})

(def reason-for-demolition-field
  {:ryhti       [:reasonForDemolition]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description "Purkamisen syy, 0..1. Käytetään koodistoa http://uri.suomi.fi/codelist/rytj/PurkamisenSyy"})

;; TODO: laajennettuRakennusjateselvitys-v2?
;; Not on tietomallit.pdf
(def waste-report-field
  {:ryhti       [:demolitionMaterialAndConstructionWasteReport]
   :lupapiste   nil
   :->ryhti     (fn [_v _context] nil)
   :description ""})

(defn data-field [{:keys [ryhti-key ryhti-property lupapiste-property enum-mapping required?]}]
  [{:ryhti     [ryhti-key]
    :lupapiste [:data :ryhti ryhti-key :value]}

   (cond-> {:ryhti     [ryhti-property]
            :lupapiste [:data lupapiste-property :value]
            :->ryhti   (fn [v _context]
                         (enum-mapping (keyword v)))}
     required? (assoc :required? true))

   {:ryhti     [:isPrimary]
    :lupapiste [:data :is-primary :value]}])

(def building-material-of-load-bearing-structures-mapping
  ;; 1.26. Kantavien rakenteiden rakennusaine
  (data-field
    {:ryhti-key          :buildingMaterialOfLoadBearingStructuresKey
     :ryhti-property     :buildingMaterialOfLoadBearingStructuresType
     :lupapiste-property :rakennusaine
     :enum-mapping       mappings/kantava-rakennusaine
     :required?          true}))

(def facade-material-mapping
  ;; 1.27. Julkisivumateriaali
  (data-field
    {:ryhti-key          :facadeMaterialKey
     :ryhti-property     :facadeMaterialType
     :lupapiste-property :julkisivumateriaali
     :enum-mapping       mappings/julkisivu
     :required?          true}))

(def heating-method-mapping
  ;; 1.29. Lämmitystapa
  (data-field
    {:ryhti-key          :heatingMethodKey
     :ryhti-property     :heatingMethodType
     :lupapiste-property :lammitystapatyyppi
     :enum-mapping       mappings/lammitystapa}))

(def heating-energy-source-mapping
  ;; 1.30. LämmitysenergianLähde
  (data-field
    {:ryhti-key          :heatingEnergySourceKey
     :ryhti-property     :heatingEnergySourceType
     :lupapiste-property :lammonlahdetyyppi
     :enum-mapping       mappings/lammonlahde}))

(def cooling-method-mapping
  ;; 1.31. Jäähdytystapa
  (data-field
    {:ryhti-key          :coolingMethodKey
     :ryhti-property     :coolingMethodType
     :lupapiste-property :jaahdytystapatyyppi
     :enum-mapping       mappings/jaahdytystapa}))

(def cooling-energy-source-mapping
  ;; 1.32. JäähdytysenergianLähde
  (data-field
    {:ryhti-key          :coolingEnergySourceKey
     :ryhti-property     :coolingEnergySourceType
     :lupapiste-property :jaahdytysenergianlahdetyyppi
     :enum-mapping       mappings/jaahdytysenergianlahde}))

(def electrical-energy-source-mapping
  ;; 1.33. SähköenergianLähde
  (data-field
    {:ryhti-key          :electricalEnergySourceKey
     :ryhti-property     :electricalEnergySourceType
     :lupapiste-property :sahkoenergianlahdetyyppi
     :enum-mapping       mappings/sahkoenergianlahde}))

(def material-data-mapping
  ;; 1.25. Materiaalitiedot
  [{:ryhti       [:constructionMethod]
    :lupapiste   [:materiaalitiedot :rakentamistapa :value]
    :nilable?    true
    :->ryhti     (fn [v _context]
                   (get mappings/rakentamistapa (keyword v)))
    :description "Rakentamistapa 1. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_runkotapa\">http://uri.suomi.fi/codelist/vtj/Rake_runkotapa</a>"}

   {:ryhti       [:buildingMaterialOfLoadBearingStructures]
    :lupapiste   [:materiaalitiedot :kantavaRakennusaine]
    :->ryhti     (fn [aineet context]
                   (into []
                         (comp (map (fn [aine]
                                      (apply-mapping building-material-of-load-bearing-structures-mapping (assoc context :data aine))))
                               (filter (required-filled building-material-of-load-bearing-structures-mapping)))
                         (vals aineet)))
    :description "Kantavien rakenteiden rakennusaine, 1..*"}

   {:ryhti       [:facadeMaterial]
    :lupapiste   [:materiaalitiedot :julkisivu]
    :->ryhti     (fn [materiaalit context]
                   (into []
                         (comp (map (fn [materiaali]
                                      (apply-mapping facade-material-mapping (assoc context :data materiaali))))
                               (filter (required-filled facade-material-mapping)))
                         (vals materiaalit)))
    :description "Julkisivumateriaali, 1..*"}

   {:ryhti       [:fireClass]
    :lupapiste   [:materiaalitiedot :paloluokka]
    :->ryhti     (fn [v _context]
                   (get mappings/paloluokka v))
    :description "Paloluokka, 0..1. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Paloluokka\">http://uri.suomi.fi/codelist/rytj/Paloluokka</a>"}

   {:ryhti       [:wideBodiedBuilding]
    :lupapiste   [:materiaalitiedot :laajarunkoinen-rakennus :value]
    :description "LaajarunkoinenRakennus, 1"}

   #_
   {:ryhti       [:reusableMaterialAmount]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "UudelleenhyödynnettävänMateriaalinMäärä, 0..1"}])

(def material-data-field
  {:ryhti       [:materialData]
   :lupapiste   [:osa :materiaalitiedot]
   :->ryhti     (fn [v context]
                  (apply-mapping material-data-mapping (assoc context :materiaalitiedot v)))
   :description "Materiaalitiedot, 0..1"})

(def energy-data-mapping
  ;; 1.28. Energiatiedot
  [{:ryhti     [:energyDataKey]
    :lupapiste [:energiatiedot :ryhti :energyDataKey :value]}

   {:ryhti       [:energyEfficiencyRating]
    :lupapiste   [:energiatiedot :energialuokka :value]
    :->ryhti     (fn [v _context]
                   (get mappings/energiatehokkuusluokka (keyword v)))
    :description "Energiatehokkuusluokka, 0..1 http://uri.suomi.fi/codelist/rytj/Energiatehokkuusluokka"}

   #_
   {:ryhti       [:computationalEnergyEfficiencyComparand]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "LaskennallinenEnergiatehokkuudenVertailuluku, 0..1"}

   {:ryhti       [:heatingMethod]
    :lupapiste   [:energiatiedot :lammitystapa]
    :->ryhti     (fn [v context]
                   (mapv (fn [x]
                           (apply-mapping heating-method-mapping (assoc context :data x)))
                         (vals v)))
    :description "Lammitystapa, 1..*"}

   {:ryhti       [:heatingEnergySource]
    :lupapiste   [:energiatiedot :lammitysenergianlahde]
    :->ryhti     (fn [v context]
                   (mapv (fn [x]
                           (apply-mapping heating-energy-source-mapping (assoc context :data x)))
                         (vals v)))
    :description "LämmitysenergianLähde, 0..*"}

   {:ryhti       [:coolingMethod]
    :lupapiste   [:energiatiedot :jaahdytystapa]
    :->ryhti     (fn [v context]
                   (mapv (fn [x]
                           (apply-mapping cooling-method-mapping (assoc context :data x)))
                         (vals v)))
    :description "Jäähdytystapa, 0..*"}

   {:ryhti       [:coolingEnergySource]
    :lupapiste   [:energiatiedot :jaahdytysenergianlahde]
    :->ryhti     (fn [v context]
                   (mapv (fn [x]
                           (apply-mapping cooling-energy-source-mapping (assoc context :data x)))
                         (vals v)))
    :description "Jäähdytysenergianlahde, 0..*"}

   {:ryhti       [:electricalEnergySource]
    :lupapiste   [:energiatiedot :sahkoenergianlahde]
    :->ryhti     (fn [v context]
                   (mapv (fn [x]
                           (apply-mapping electrical-energy-source-mapping (assoc context :data x)))
                         (vals v)))
    :description "Sähköenergianlähde, 0..*"}

   #_
   {:ryhti       [:netHeatedArea]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "LämmitettyNettopintaAla, 0..1"}

   #_
   {:ryhti       [:heatedVolume]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "LämmitettyTilavuus, 0..1"}

   #_
   {:ryhti       [:attachment]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "0..*"}

   #_
   {:ryhti       [:calculatoryDeliveredEnergyUsage]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "LaskennallinenOstoenergianKulutus, 0..1"}

   {:ryhti       [:energyRatingSubindex]
    :lupapiste   [:energiatiedot :energialuokan-alaindeksi :value]
    :->ryhti     (fn [v _context]
                   (get mappings/energialuokka-alaindeksi (keyword v)))
    :description "Energiatehokkuusluokan alaindeksi. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/energialuokkaAlaindeksi"}])

(def energy-data-field
  {:ryhti       [:energyData]
   :lupapiste   [:osa :energiatiedot]
   :->ryhti     (fn [v context]
                  (apply-mapping energy-data-mapping (assoc context :energiatiedot v)))
   :description "Energiatiedot, 0..1"})

(def interior-data-mapping
  ;; 1.35. Sisätilojen tiedot
  [{:ryhti       [:floorArea]
    :lupapiste   [:sisatilat :huoneistoala :value]
    :->ryhti     string->double
    :description "Huoneistoala, 0..1"}

   {:ryhti       [:basementArea]
    :lupapiste   [:sisatilat :kellariala :value]
    :->ryhti     string->number
    :description "Kellariala, 0..1"}

   {:ryhti       [:atticArea]
    :lupapiste   [:sisatilat :ullakkoala :value]
    :->ryhti     string->number
    :description "Ullakkoala, 0..1"}])

(def exterior-data-mapping
  ;; 1.36. Ulkokuoren tiedot
  [{:ryhti       [:numberOfStoreys]
    :lupapiste   [:ulkokuorentiedot :kerrosluku :value]
    :->ryhti     string->number
    :description "Kerrosluku, 0..1"}

   {:ryhti       [:height]
    :lupapiste   [:ulkokuorentiedot :korkeus :value]
    :->ryhti     string->number
    :description "Korkeus, 0..1"}

   #_
   {:ryhti       [:flightObstacle]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Lentoeste, 0..1"}

   #_
   {:ryhti       [:shape]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Muoto, 0..1"}

   #_
   {:ryhti       [:shape3d]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description ""}

   {:ryhti       [:relationToGroundLevel]
    :lupapiste   [:ulkokuorentiedot :suhde-maan-pintaan :value]
    :->ryhti     (fn [v _context]
                   (get mappings/suhde-pintaan (keyword v)))
    :description "Suhde maan pintaan, 0..1. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/Suhde_pintaan"}

   {:ryhti       [:volume]
    :lupapiste   [:ulkokuorentiedot :tilavuus :value]
    :->ryhti     string->number
    :description "Tilavuus, 0..1"}

   {:ryhti       [:grossFloorArea]
    :lupapiste   [:ulkokuorentiedot :kerrosala :value]
    :->ryhti     string->number
    :description "Kerrosala, 0..1"}

   {:ryhti       [:totalArea]
    :lupapiste   [:ulkokuorentiedot :kokonaisala :value]
    :->ryhti     string->number
    :description "Kokonaisala, 0..1"}

   ;; Check :rakennusoikeusValinta first, and only add correct fields based on that

   {:ryhti       [:permittedBuildingArea]
    :lupapiste   [:ulkokuorentiedot :rakennusoikeudellinenKerrosala :value]
    :->ryhti     (fn [v context]
                   (when (= "kerrosala" (:value (:rakennusoikeusValinta (:ulkokuorentiedot context))))
                     (string->number v)))
    :description "Rakennusoikeudellinen Kerrosala, 0..1"}

   {:ryhti       [:permittedRidgeHeight]
    :lupapiste   [:ulkokuorentiedot :harjanKorkeusrakennusalalla :value]
    :->ryhti     (fn [v context]
                   (when (#{"harjakorkeus"} (:value (:rakennusoikeusValinta (:ulkokuorentiedot context))))
                     (string->double v)))
    :description "Harjankorkeus rakennusalalla, 0..1"}

   {:ryhti       [:permittedVolume]
    :lupapiste   [:ulkokuorentiedot :rakennusoikeusKuutiometreina :value]
    :->ryhti     (fn [v context]
                   (when (#{"tilavuus" "harjakorkeus"} (:value (:rakennusoikeusValinta (:ulkokuorentiedot context))))
                     (string->number v)))
    :description "Käytetty rakennusoikeus? 0..1"}])

(def exterior-data-field
  {:ryhti       [:exteriorData]
   :lupapiste   [:osa :ulkokuorentiedot]
   :->ryhti     (fn [v context]
                  (apply-mapping exterior-data-mapping (assoc context :ulkokuorentiedot v)))
   :description "UlkokuorenTiedot, 0..1"})

(def ventilation-method-mapping
  ;; 1.38. Ilmanvaihtotapa
  (data-field
    {:ryhti-key          :ventilationMethodKey
     :ryhti-property     :ventilationMethodType
     :lupapiste-property :ilmanvaihtotyyppi
     :enum-mapping       mappings/ilmanvaihtotavan-laji
     :required?          true}))

(def network-connection-mapping
  ;; 1.39. Verkostoliittymä
  [{:ryhti     [:networkConnectionKey]
    :lupapiste [:data :ryhti :networkConnectionKey :value]}

   {:ryhti     [:networkConnectionType]
    :lupapiste [:data :verkostoliittymatyyppi :value]
    :nilable?  true
    :->ryhti   (fn [v _context]
                 (get mappings/verkostoliittymat (keyword v)))}

   {:ryhti     [:connected]
    :lupapiste [:data :liitettu :value]}])

(def building-services-engineering-data-mapping
  ;; 1.37. Talotekniikkatiedot
  [;; NOTE: Lupapiste data has :buildingServicesEngineeringDataKey which doesn't seem be included in OAS

   {:ryhti       [:ventilationMethod]
    :lupapiste   [:tekniset-tiedot :ilmanvaihtotavan-laji]
    :->ryhti     (fn [v context]
                   (into []
                         (comp (map (fn [x]
                                      (apply-mapping ventilation-method-mapping (assoc context :data x))))
                               (filter (required-filled ventilation-method-mapping)))
                         (vals v)))
    :description "Ilmanvaihtotapa, 1..*"}

   {:ryhti       [:sewerageMethodType]
    :lupapiste   [:tekniset-tiedot :jatevesienkasittely :value]
    :->ryhti     (fn [v _context]
                   (get mappings/jatevesienkasittely (keyword v)))
    :description "Jätevesien käsittelyn laji, 0..1. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/jatevesienkasittely"}

   {:ryhti       [:householdWaterType]
    :lupapiste   [:tekniset-tiedot :talousvesityyppi :value]
    :->ryhti     (fn [v _context]
                   (get mappings/talousvesityyppi (keyword v)))
    :description "Talousveden laji, 0..1. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/talousvesi"}

   {:ryhti       [:networkConnection]
    :lupapiste   [:tekniset-tiedot :verkostoliittymat]
    :->ryhti     (fn [v context]
                   (mapv (fn [x]
                           (apply-mapping network-connection-mapping (assoc context :data x)))
                         (vals v)))
    :description "Verkostoliittymä, 0..*"}

   #_
   {:ryhti       [:stormWaterTreatmentType]
    ;; TODO: Seems to be missing in Lupapiste Or same as building-site? Rakennuspaikka?
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Huleveden käsittelyn laji, 0..*. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/hulevedenkasittely"}])

(def building-services-engineering-data-field
  {:ryhti       [:buildingServicesEngineeringData]
   :lupapiste   [:osa :tekniset-tiedot]
   :->ryhti     (fn [v context]
                  (apply-mapping building-services-engineering-data-mapping (assoc context :tekniset-tiedot v)))
   :description "Talotekniikkatiedot, 0..1"})

(def purpose-mapping
  (data-field
    {:ryhti-key          :purposeKey
     :ryhti-property     :type
     :lupapiste-property :tyyppi
     ;; FIXME: schema uses completely different codeset than Ryhti spec?
     :enum-mapping       (constantly "http://uri.suomi.fi/codelist/jhs/rakennus_1_20180712/code/0110")}))

(def usage-data-mapping
  ;; 1.40. Rakennuksen käyttötiedot
  [
   #_
   {:ryhti       [:commissioningDate]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Käyttöönottopäivämäärä, 0..1"}

   #_
   {:ryhti       [:usageStatus]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Rakennuksen käytössäolotilanne, 0..1. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne\">http://uri.suomi.fi/codelist/vtj/Rake_kaytossaolotilanne</a>"}

   {:ryhti       [:purpose]
    :lupapiste   [:kayttotiedot :kayttotarkoitus]
    :->ryhti     (fn [v context]
                   (mapv (fn [x]
                           (apply-mapping purpose-mapping (assoc context :data x)))
                         (vals v)))
    :description "Käyttötarkoitus, 1..*"}

   #_
   {:ryhti       [:usefulFloorArea]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Käyttöala, 0..1"}

   {:ryhti       [:intendedWorkingLife]
    :lupapiste   [:kayttotiedot :kayttoika :value]
    :->ryhti     string->number
    :description "TavoitteellinenKäyttöikä, 0..1"}

   {:ryhti       [:plannedUserCount]
    :lupapiste   [:kayttotiedot :kayttajamaara :value]
    :->ryhti     string->number
    :description "SuunniteltuKäyttäjämäärä, 0..1"}])

(def equipment-mapping
  ;; 1.42. Varuste
  [{:ryhti     [:equipmentKey]
    :lupapiste [:varuste :ryhti :equipmentKey :value]}

   {:ryhti       [:equipmentType]
    :lupapiste   [:varuste :varustetyyppi :value]
    :->ryhti     (fn [v _context]
                   (get mappings/rakennusvaruste (keyword v)))
    :description "Rakennuksen varuste. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/rakennusvaruste"}

   {:ryhti       [:equipmentCount]
    :lupapiste   [:varuste :maara :value]
    :->ryhti     string->number
    :description "VarusteenLukumäärä, 1"}])

(def equipment-field
  {:ryhti       [:equipment]
   :lupapiste   [:osa :tekniset-tiedot :varuste]
   :->ryhti     (fn [v context]
                  (mapv (fn [varuste]
                          (apply-mapping equipment-mapping (assoc context :varuste varuste)))
                        (vals v)))
   :description "Varuste, 0..*"})

(def entrance-mapping
  ;; 1.43. Sisäänkäynti
  [{:ryhti     [:entranceKey]
    :lupapiste [:sisaankaynti :ryhti :entranceKey :value]}

   {:ryhti     [:isPrimary]
    :lupapiste [:sisaankaynti :is-primary :value]}

   {:ryhti       [:entranceType]
    :lupapiste   [:sisaankaynti :tila :laji :value]
    :->ryhti     (fn [v _context]
                   (get mappings/sisaankaynnin-tyyppi (keyword v)))
    :required?   true
    :description "Sisäänkäynnin tyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/sisaankaynti\">http://uri.suomi.fi/codelist/rytj/sisaankaynti</a>"}

   {:ryhti       [:lifeCycleState]
    :lupapiste   [:sisaankaynti :tila :elinkaaren-vaihe :value]
    :->ryhti     (fn [v _context]
                   (get mappings/elinkaaren-vaihe (keyword v)))
    :required?   true
    :description "Rakennuksen toiminnallisen osan elinkaaren vaihe. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe\">http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe</a>"}

   #_
   {:ryhti       [:addressOfEntrance]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Osoitetunnus, 0..1"}

   {:ryhti       [:isAccessible]
    :lupapiste   [:sisaankaynti :esteeton :value]
    :description "Esteetön, 1"}

   {:ryhti       [:name :fin]
    :lupapiste   [:sisaankaynti :nimi :value]
    :description "0"}

   #_
   {:ryhti       [:description]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "0"}

   {:ryhti       [:geometry]
    :lupapiste   [:sisaankaynti :sijaintipiste]
    :->ryhti     (fn [v _context]
                   (let [e (some-> v :itakoordinaatti :value ss/blank-as-nil parse-double)
                         n (some-> v :pohjoiskoordinaatti :value ss/blank-as-nil parse-double)]
                     (when (and e n)
                       (point-geometry [e n]))))
    :description "Geometria, 0..1"}

   {:ryhti     [:isRescueDepartmentsAttackPassage]
    :lupapiste [:sisaankaynti :hyokkaysreitti :value]}])

(def elevator-mapping
  ;; 1.45. Hissi
  [{:ryhti     [:elevatorKey]
    :lupapiste [:hissi :ryhti :elevatorKey :value]}

   {:ryhti       [:name :fin]
    :lupapiste   [:hissi :hissin-tiedot :nimi :value]
    :description "0"}

   #_
   {:ryhti       [:description]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "0"}

   #_
   {:ryhti       [:geometry]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description ""}

   {:ryhti       [:elevatorType]
    :lupapiste   [:hissi :hissin-tiedot :hissin-laji :value]
    :->ryhti     (fn [v _context]
                   (get mappings/hissin-laji (keyword v)))
    :required?   true
    :description "Hissin laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/hissi\">http://uri.suomi.fi/codelist/rytj/hissi</a>"}

   {:ryhti       [:lifeCycleState]
    :lupapiste   [:hissi :hissin-tiedot :elinkaaren-vaihe :value]
    :nilable?    true
    :->ryhti     (fn [v _context]
                   (get mappings/elinkaaren-vaihe (keyword v)))
    :description "Rakennuksen toiminnallisen osan elinkaaren vaihe. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe\">http://uri.suomi.fi/codelist/rytj/rakennuksen-toim-osan-elinkaaren-vaihe</a>"}

   {:ryhti       [:insideLength]
    :lupapiste   [:hissi :mitat :sisapituus :value]
    :->ryhti     string->double
    :description "Sisäpituus, 1"}

   {:ryhti       [:insideWidth]
    :lupapiste   [:hissi :mitat :sisaleveys :value]
    :->ryhti     string->double
    :description "Sisäleveys, 1"}

   {:ryhti       [:doorOpeningWidth]
    :lupapiste   [:hissi :mitat :oviaukon-leveys :value]
    :->ryhti     string->double
    :description "OviaukonLeveys, 1"}

   {:ryhti       [:doorOpeningHeight]
    :lupapiste   [:hissi :mitat :oviaukon-korkeus :value]
    :->ryhti     string->double
    :description "OviaukonKorkeus, 1"}

   {:ryhti       [:passengerCapacity]
    :lupapiste   [:hissi :kapasiteetti :henkilomaara :value]
    :->ryhti     string->number
    :description "Henkilömäärä, 1"}

   {:ryhti       [:loadCapacity]
    :lupapiste   [:hissi :kapasiteetti :kantavuus :value]
    :->ryhti     string->number
    :description "Kantavuus, 1"}

   {:ryhti       [:fireFighterElevator]
    :lupapiste   [:hissi :hissin-tiedot :palomieshissi :value]
    :->ryhti     (fn [v _]
                   (= "yes" v))
    :description "Palomieshissi, 1"}])

(def civil-defence-shelter-mapping
  ;; 1.47. Väestönsuoja
  [{:ryhti     [:civilDefenceShelterKey]
    :lupapiste [:vaestonsuoja :ryhti :civilDefenceShelterKey :value]}

   {:ryhti       [:name :fin]
    :lupapiste   [:vaestonsuoja :nimi :value]
    :description "Nimi, 0"}

   #_
   {:ryhti       [:description]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}

   #_
   {:ryhti       [:geometry]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description ""}

   {:ryhti       [:civilDefenceShelterClass]
    :lupapiste   [:vaestonsuoja :luokka :value]
    :nilable?    true
    :->ryhti     (fn [v _context]
                   (get mappings/vaestonsuoja-luokka (keyword v)))
    :description "Väestönsuojan luokka, 0..1. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka\">http://uri.suomi.fi/codelist/rytj/VaestonsuojaLuokka</a>"}

   {:ryhti       [:civilDefenceShelterCapacity]
    :lupapiste   [:vaestonsuoja :kapasiteetti :value]
    :->ryhti     string->number
    :description "Väestönsuojan suojapaikkojen määrä, 1"}

   {:ryhti       [:civilDefenceShelterShared]
    :lupapiste   [:vaestonsuoja :yhteinen :value]
    :description "Yhteinen väestönsuoja, 1"}

   #_
   {:ryhti       [:civilDefenceShelterAdditionalInformation]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Lokalisoitu merkkijono-luokka eri kielille. Lisää vähintään yksi kieli."}

   #_
   {:ryhti       [:civilDefenceShelterOtherBuildings]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description ""}

   {:ryhti       [:shelterArea]
    :lupapiste   [:vaestonsuoja :suoja-ala :value]
    :->ryhti     string->number
    :description "Suoja-ala, 1..1"}])

(def internal-network-mapping
  ;; 1.50. Sisäverkon tiedot
  [{:ryhti     [:internalNetworkKey]
    :lupapiste [:sisaverkko :ryhti :internalNetworkKey :value]}

   {:ryhti     [:antennaNetwork]
    :lupapiste [:sisaverkko :antenniverkko :value]}

   {:ryhti     [:existingFiberOpticNetwork]
    :lupapiste [:sisaverkko :olemassaOlevaValokuituverkko :value]}

   {:ryhti     [:genericOpticalCabling]
    :lupapiste [:sisaverkko :yleiskaapeloinninOptinenKaapelointi :value]}

   {:ryhti     [:genericPairCabling]
    :lupapiste [:sisaverkko :yleiskaapeloinninParikaapelointi :value]}])

(def ^{:schema gen/BuildingSection} building-section-mapping
  ;; 1.24. Rakennuksen osa
  [{:ryhti     [:buildingSectionKey]
    :lupapiste [:osa :ryhti :buildingSectionKey :value]}

   life-cycle-state-field
   completion-date-field
   protection-method-field
   culture-historical-significance-field
   material-data-field
   energy-data-field

   {:ryhti       [:interiorData]
    :lupapiste   [:osa :sisatilat]
    :->ryhti     (fn [v context]
                   (apply-mapping interior-data-mapping (assoc context :sisatilat v)))
    :description "Sisätilat, 0..1"}

   exterior-data-field
   building-services-engineering-data-field

   {:ryhti       [:usageData]
    :lupapiste   [:osa :kayttotiedot]
    :->ryhti     (fn [v context]
                   (apply-mapping usage-data-mapping (assoc context :kayttotiedot v)))
    :description "Käyttötiedot, 1"}

   equipment-field

   {:ryhti       [:entrance]
    :lupapiste   [:osa :sisaankaynti]
    :->ryhti     (fn [v context]
                   (into []
                         (comp (map (fn [x]
                                      (apply-mapping entrance-mapping (assoc context :sisaankaynti x))))
                               (filter (required-filled entrance-mapping)))
                         (vals v)))
    :description "Sisäänkäynti, 0..*"}

   {:ryhti       [:elevator]
    :lupapiste   [:osa :hissit]
    :->ryhti     (fn [v context]
                   (into []
                         (comp (map (fn [hissi]
                                      (apply-mapping elevator-mapping (assoc context :hissi hissi))))
                               (filter (required-filled elevator-mapping)))
                         (vals v)))
    :description "Hissit, 0..*"}

   {:ryhti       [:apartment]
    :lupapiste   [:osa :huoneistot]
    :->ryhti     (fn [v context]
                   (mapv (fn [huoneisto]
                           (apply-mapping apartment-mapping (assoc context :huoneisto huoneisto)))
                         (vals v)))
    :description "Huoneistot, 0..*"}

                                        ; construction-design-field
                                        ; building-information-model-field
                                        ; special-design-field
                                        ; attachment-field
   partition-reason-field
                                        ; partition-description-field

   {:ryhti       [:civilDefenceShelter]
    :lupapiste   [:osa :vaestonsuoja]
    :->ryhti     (fn [v context]
                   (mapv (fn [vaestonsuoja]
                           (apply-mapping civil-defence-shelter-mapping (assoc context :vaestonsuoja vaestonsuoja)))
                         (vals v)))
    :description "Väestönsuoja, 0..*"}

   #_
   {:ryhti       [:assemblyFacility]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Kokoontumistila, 0..*"}

   #_
   {:ryhti       [:relatedFinishedBuildingSection]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "LiittyväValmiinRakennuksenOsa, 0..1"}

   ;; demolition-date-field
   ;; reason-for-demolition-field
   ;; waste-report-field

   {:ryhti       [:internalNetwork]
    :lupapiste   [:osa :sisaverkko]
    :->ryhti     (fn [v context]
                   (apply-mapping internal-network-mapping (assoc context :sisaverkko v)))
    :description "Sisäverkon tiedot, 0..1"}

   #_
   {:ryhti       [:elevatorInOtherBuildingSection]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Muissa osissa sijaitsevat hissit, 0..*"}

   #_
   {:ryhti       [:entranceInOtherBuildingSection]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Muissa osissa sijaitsevat sisäänkäynnit, 0..*"}

   building-information-model-field])

(def ^{:schema gen/StructureSection} structure-section-mapping
  ;; 1.53. Rakennelman osa
  [{:ryhti     [:structureSectionKey]
    :lupapiste [:osa :ryhti :structureSectionKey :value]}

   life-cycle-state-field
   ;; completion-date-field
   ;; protection-method-field
   ;; culture-historical-significance-field

   material-data-field
   energy-data-field
   exterior-data-field
   building-services-engineering-data-field

   {:ryhti       [:structurePurpose]
    ;; NOTE: Same field as on Structure? (mainPurpose)
    :lupapiste   [:rakennuskohde :data :kaytto :rakennelman-kayttotarkoitus :value]
    :->ryhti     (fn [kayttotarkoitus _context]
                   (get mappings/rakennelman-kayttotarkoitus kayttotarkoitus))
    :description "Rakennelman käyttötarkoitus, 1. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/RakennelmanKayttotarkoitus"}

   equipment-field

   ;; construction-design-field
   building-information-model-field
   ;; special-design-field
   ;; attachment-field
   partition-reason-field
   ;; partition-description-field
   ;; demolition-date-field
   ;; reason-for-demolition-field
   ;; waste-report-field

   #_
   {:ryhti       [:relatedFinishedStructureSection]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "liittyväValmiinRakennelmanOsa, 0..1"}])
