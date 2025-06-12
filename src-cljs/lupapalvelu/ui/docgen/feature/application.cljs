(ns lupapalvelu.ui.docgen.feature.application
  (:require [re-frame.core :as rf]
            [sade.property :as property]
            [sade.util :as su]))

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [app]}]]
  {:db (assoc db ::data {:buildings                  (:buildings app)
                         :drawings                   (:drawings app)
                         :facta-imported?            (boolean (:facta-imported app))
                         :id                         (:id app)
                         :in-post-verdict-state?     (:inPostVerdictState app)
                         :link-permit-data           (:linkPermitData app)
                         :location                   (:location app)
                         :location-wgs84             (:location-wgs84 app)
                         :organization               (:organization app)
                         :permit-subtype             (:permitSubtype app)
                         :permit-type                (:permitType app)
                         :primary-operation          (:primaryOperation app)
                         :property-id                (:propertyId app)
                         :property-id-source         (:propertyIdSource app)
                         :review-officers            (:reviewOfficers app)
                         :ryhti-data-2026            (:ryhti-data-2026 app)
                         :submit-data-source         (keyword (:submitDataSource app))
                         :submit-data-source-locked? (boolean (:submitDataSourceLocked app))
                         :submitted                  (:submitted app)})})

(defn app->documents
  "Return a seq of all document-like things in `app`."
  [app]
  (concat (:documents app)
          (:building-product-catalogs app)
          (:climate-reports app)
          (:tasks app)))

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::review-officer-data
  :<- [::data]
  :-> :review-officers)

(rf/reg-sub ::application-id
  :<- [::data]
  :-> :id)

(defn- db->data
  [{::keys [data]}]
  {:pre [(map? data)]}
  data)

(defn db->application-id
  [db]
  (-> db db->data :id))

(rf/reg-sub ::facta-imported?
  :<- [::data]
  :-> :facta-imported?)

(rf/reg-sub ::primary-operation
  :<- [::data]
  :-> :primary-operation)

(rf/reg-sub ::primary-operation-id
  :<- [::primary-operation]
  :-> :id)

(rf/reg-sub ::permit-type
  :<- [::data]
  :-> :permit-type)

(rf/reg-sub ::permit-subtype
  :<- [::data]
  :-> :permit-subtype)

(rf/reg-sub ::property-id
  :<- [::data]
  :-> :property-id)

(rf/reg-sub ::property-id-humanized
  :<- [::property-id]
  :-> property/to-human-readable-property-id)

(rf/reg-sub ::property-id-source
  :<- [::data]
  (fn [{:keys [property-id-source]}]
    (some-> property-id-source keyword)))

(rf/reg-sub ::archiving-project?
  :<- [::permit-type]
  (fn [permit-type]
    (= "ARK" permit-type)))

(rf/reg-sub ::buildings
  :<- [::data]
  :-> :buildings)

(rf/reg-sub ::organization
  :<- [::data]
  :-> :organization)

(rf/reg-sub ::drawings
  :<- [::data]
  :-> :drawings)

(rf/reg-sub ::user-drawings
  :<- [::drawings]
  (fn [drawings]
    (remove :source drawings)))

(rf/reg-sub ::allu-drawings
  :<- [::drawings]
  (fn [drawings [_ kind]]
    (if kind
      (su/filter-by-key :source kind drawings)
      (filter :allu-id drawings))))

(rf/reg-sub ::location
  :<- [::data]
  :-> :location)

(rf/reg-sub ::location-wgs84
  :<- [::data]
  :-> :location-wgs84)

(rf/reg-sub ::in-post-verdict-state?
  :<- [::data]
  :-> (comp boolean :in-post-verdict-state?))

(rf/reg-sub ::link-permit-data
  :<- [::data]
  :-> :link-permit-data)

(rf/reg-sub ::ryhti-data-2026
  :<- [::data]
  (fn [data]
    (boolean (:ryhti-data-2026 data))))

(rf/reg-sub ::submit-data-source
  :<- [::data]
  :-> :submit-data-source)

(rf/reg-sub ::submit-data-source-locked?
  :<- [::data]
  :-> :submit-data-source-locked?)

(rf/reg-sub ::submitted-ts
  :<- [::data]
  :-> :submitted)
