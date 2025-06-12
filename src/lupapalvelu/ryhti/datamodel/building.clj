(ns lupapalvelu.ryhti.datamodel.building
  "BuildingSite"
  (:require [lupapalvelu.ryhti.api.malli-schema.generated :as gen]
            [lupapalvelu.ryhti.api.mappings :as mappings]
            [lupapalvelu.ryhti.datamodel.common :refer [always-nil apply-mapping point-geometry string->number
                                                        unicode-trim]]))

(def address-mapping
  ;; 1.12. Osoite
  [{:ryhti     [:addressKey]
    :lupapiste [:osoite :ryhti :addressKey :value]}
   {:ryhti     [:addressName :fin]
    :lupapiste [:osoite :katu :value]
    :->ryhti   unicode-trim}
   {:ryhti     [:postalCode]
    :lupapiste [:osoite :postinumero :value]
    :nilable?  true}
   {:ryhti       [:addressNumber]
    :lupapiste   nil
    :description "Osoitejärjestysnumero, aina 1 koska Lupapisteeseen syötetään vain ensisijainen osoite."
    :->ryhti     (fn [_ _] 1)}

   ;; NOTE: Not sure if this is needed here? Testing to see if would affect errors.
   {:ryhti       [:location]
    :lupapiste   [:application :location]
    :->ryhti     (fn [location _] (point-geometry location))
    :->lupapiste (fn [location _] (:coordinates (:geometry location)))}

   ;; TODO: numberPartOfAddressNumber, subdivisionLetterOfAddressNumber?
   ])

(def ^{:schema gen/BuildingSite} building-site-mapping
  ;; 1.13. Rakennuspaikka
  [{:ryhti     [:buildingSiteKey]
    :lupapiste [:rakennuspaikka :data :ryhti :buildingSiteKey :value]}

   {:ryhti       [:name :fin]
    :lupapiste   [:application :address]
    :description "Nimi, 0..1"}

   {:ryhti       [:buildingSiteAddress]
    :lupapiste   [:address-field :osoite]
    :->ryhti     (fn [osoite context]
                   [(apply-mapping address-mapping (assoc context :osoite osoite))])
    :description "Osoite, pakollinen, 1..*. Lupapisteessä vain ensisijainen osoite."}

   {:ryhti       [:area]
    :lupapiste   [:measurement-field :pintaAla :value]
    :->ryhti     string->number
    :description "Pinta-ala, 0..1"}

   {:ryhti       [:stormWaterTreatmentType]
    :lupapiste   [:rakennuspaikka :data :huleveden-kasittely :value]
    :nilable?    true
    :description "HulevedenKäsittelytapa, pakollinen, 1"
    :->ryhti     (fn [v _]
                   (get mappings/hulevedenkasittely v))}

   {:ryhti       [:tenure]
    :lupapiste   [:rakennuspaikka :data :hallintaperuste :value]
    :nilable?    true
    :description "Hallintaperuste, pakollinen, 1"
    :->ryhti     (fn [v _]
                   (get mappings/rakennuspaikan-hallintaperuste v))}

   {:ryhti     [:geometry]
    ;; TODO: This or Tietomallit?
    :lupapiste [:application :location]
    :->ryhti   (fn [location _]
                 (point-geometry location))}

   {:ryhti     [:spatialPlan]
    :lupapiste []
    ;; TODO:
    :->ryhti   always-nil}

   {:ryhti     [:statusOfPlan]
    :lupapiste [:rakennuspaikka :data :kaavatilanne :value]
    :->ryhti   (fn [kaavatilanne _]
                 ;; TODO: What about kaavanaste field?
                 ;; Together with kaavatilanne it might match the kaavatilanne codeset better?
                 (get mappings/kaavatilanne kaavatilanne))}

   {:ryhti     [:description]
    :lupapiste []
    :->ryhti   always-nil}])
