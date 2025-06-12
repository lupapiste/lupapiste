(ns lupapalvelu.ryhti.datamodel.apartment
  (:require [lupapalvelu.ryhti.api.malli-schema.generated :as gen]
            [lupapalvelu.ryhti.api.mappings :as mappings]
            [lupapalvelu.ryhti.datamodel.common :refer [apply-mapping string->double string->number]]))

(def ^{:schema gen/ApartmentEquipment} apartment-equipment-mapping
  ;; 1.54. Huoneiston varuste
  [{:ryhti     [:apartmentEquipmentKey]
    :lupapiste [:varuste :id :ryhti :apartmentEquipmentKey :value]
    :->ryhti   (fn [v _context] v)}

   {:ryhti       [:apartmentEquipmentType]
    :lupapiste   [:varuste :tyyppi]
    :->ryhti     (fn [k _context]
                   (get mappings/huoneiston-varuste k))
    :description "Huoneiston varusteen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste\">http://uri.suomi.fi/codelist/rytj/HuoneistoVaruste</a>"}

   {:ryhti     [:numberOfEquipment]
    :lupapiste [:varuste :value]
    :default   0
    :->ryhti   string->number}])

(def ^{:schema gen/Apartment} apartment-mapping
  ;; 1.54. Huoneisto
  [{:ryhti     [:apartmentKey]
    :lupapiste [:huoneisto :ryhti :apartmentKey :value]}

   {:ryhti       [:lifeCycleState]
    :lupapiste   nil
    :->ryhti     (fn [_v _context]
                   ;; TODO:
                   "http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe/code/1")
    :description "Huoneiston elinkaaren vaihe, 1. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe\">http://uri.suomi.fi/codelist/rytj/huon-elinkaaren-vaihe</a>"}

   {:ryhti       [:apartmentEquipment]
    :lupapiste   [:huoneisto]
    :->ryhti     (fn [huoneisto context]
                   (mapv (fn [k]
                           (apply-mapping apartment-equipment-mapping
                                          (assoc context :varuste {:tyyppi k
                                                                   :id     (get huoneisto (keyword (str "varuste-" (name k))))
                                                                   :value  (:value (get huoneisto k))})))
                         ;; Lupapiste model has the count for each equipment in the huoneisto map directly.
                         ;; The identifiers are stored separately in a corresponding `:varuste-[type]` group
                         [:wc
                          :suihku
                          :sauna
                          :parveke
                          :lammin-vesi]))
    :description "Varuste, 0..*"}

   {:ryhti       [:apartmentIdentifier]
    :lupapiste   [:huoneisto :pysyvaHuoneistotunnus :value]
    :default     "1"
    :description "PysyväHuoneistoTunnus, 1. Kuntajärjestelmä hakee tämän."}

   ;; 1.56. OsoiterakenteenMukainenHuoneistoTunnus
   ;; These are a separate schema on tietomallit.pdf but inline on the OAS
   {:ryhti       [:apartmentIdentifierLetterSuffix]
    :lupapiste   [:huoneisto :porras :value]
    :description "Kirjainosa, porras? 0..1"}

   {:ryhti       [:apartmentSubdivisionLetter]
    :lupapiste   [:huoneisto :jakokirjain :value]
    :description "Jakokirjain, 0..1"}
   ;; ---


   {:ryhti       [:apartmentType]
    :lupapiste   [:huoneisto :huoneistoTyyppi :value]
    :->ryhti     (fn [v _context]
                   (get mappings/huoneistotyyppi (keyword v)))
    :description "Huoneistotyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi\">http://uri.suomi.fi/codelist/rytj/Huoneistotyyppi</a>"}

   {:ryhti       [:floorArea]
    :lupapiste   [:huoneisto :huoneistoala :value]
    :->ryhti     string->double
    :description "Huoneistoala, 0..1"}

   {:ryhti       [:numberOfRooms]
    :lupapiste   [:huoneisto :huoneluku :value]
    :nilable?    true
    :->ryhti     string->number
    :description "Huoneiden Lukumäärä, 1"}

   {:ryhti       [:kitchenType]
    :lupapiste   [:huoneisto :keittionTyyppi :value]
    :->ryhti     (fn [v _context]
                   (get mappings/keittiotyyppi (keyword v)))
    :description "Keittiötyyppi. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/Keittiotyyppi\">http://uri.suomi.fi/codelist/rytj/Keittiotyyppi</a>"}

   ;; Käymälätyyppi / ToiletType

   {:ryhti       [:apartmentChangeType]
    :lupapiste   [:huoneisto :muutostapa :value]
    :->ryhti     (fn [v _context]
                   (get mappings/huoneiston-muutoksen-laji (keyword v)))
    :description "Huoneiston muutoksen laji. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji\">http://uri.suomi.fi/codelist/rytj/huoneistonmuutoksenlaji</a>"}

   #_
   {:ryhti       [:commissioningDate]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "Käyttöönottopäivämäärä, 0..1"}

   {:ryhti       [:apartmentNumber]
    :lupapiste   [:huoneisto :huoneistonumero :value]
    :->ryhti     string->number
    :description "Huoneisto numero?"}

   {:ryhti       [:addressNumber]
    :lupapiste   [:huoneisto :huoneistonumero :value]
    ;; TODO:
    :->ryhti     (fn [_ _] 1)
    :description "Osoitejärjestysnumero, 1"}

   {:ryhti       [:apartmentStorey]
    :lupapiste   [:huoneisto :sijaintikerros :value]
    :->ryhti     string->number
    :description ""}

   #_
   {:ryhti       [:isWaterCloset]
    :lupapiste   nil
    :->ryhti     (fn [_v _context] nil)
    :description "onVesikäymälä, 0..1"}

   {:ryhti       [:specificationOfLocationFloor]
    :lupapiste   [:huoneisto :sijaintikerroksen-tarkenne :value]
    :->ryhti     (fn [v _]
                   (get mappings/sijaintikerroksen-tarkenne (keyword v)))
    :description "Sijaintikerroksen tarkenne. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne\">http://uri.suomi.fi/codelist/rytj/sijaintikerroksenTarkenne</a>"}])
