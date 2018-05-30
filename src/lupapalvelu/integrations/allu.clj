(ns lupapalvelu.integrations.allu
  (:require [clojure.walk :refer [postwalk]]
            [schema.core :as sc]
            [iso-country-codes.core :refer [country-translate]]
            [sade.core :refer [def-]]
            [lupapalvelu.integrations.allu-schemas :refer [PlacementContract]]))

;;; FIXME: Avoid producing nil-valued fields.

;;;; Cleaning up :value indirections

(defn- flatten-values [app]
  (letfn [(node-value [node]
            (if (and (map? node) (contains? node :value))
              (:value node)
              node))]
    (postwalk node-value app)))

;;;; Conversion details

(def- convert-customer-type
  {:henkilo "PERSON", :yritys "COMPANY"})

(defn- fullname [{:keys [etunimi sukunimi]}]
  (str etunimi " " sukunimi))

(defn- customer-name [customer-type customer]
  (case customer-type
    :henkilo (fullname (:henkilotiedot customer))
    :yritys  (:yritysnimi customer)))

(defn- address-country [address]
  (country-translate :alpha-3 :alpha-2 (:maa address)))

(defn- convert-address [{:keys [postitoimipaikannimi postinumero katu]}]
  {:city          postitoimipaikannimi
   :postalCode    postinumero
   :streetAddress {:streetName katu}})

(defn- doc->customer [{{tag :_selected :as data} :data}]
  (let [tag (keyword tag)
        customer (get data tag)
        address (:osoite customer)]
    {:type          (convert-customer-type tag)
     :name          (customer-name tag customer)
     :country       (address-country address)
     :postalAddress (convert-address address)}))

(defn- person->contact [{:keys [henkilotiedot], {:keys [puhelin email]} :yhteystiedot}]
  {:name (fullname henkilotiedot), :phone puhelin, :email email})

(defn- customer-contact [{{tag :_selected :as data} :data :as doc}]
  (let [tag (keyword tag)]
    (case tag
      :henkilo (get data tag)
      :yritys  (:yhteyshenkilo (get data tag)))))

(defn- drawing->GeoJSON-2008 [{:keys [geometry geometry-wgs84] :as drawing}]
  {:type "Feature"
   :geometry geometry-wgs84
   :properties (dissoc drawing :geometry :geometry-wgs84)}) ; TODO: dissoc even more (?)

(def- WGS84-URN "urn:ogc:def:crs:OGC:1.3:CRS84")

(defn- drawings->GeoJSON-2008 [drawings]
  {:type "FeatureCollection"
   :features (mapv drawing->GeoJSON-2008 drawings)
   :crs {:type "name"
         :properties {:name WGS84-URN}}})

(defn- application-postal-address [app]
  {:streetAddress {:streetName (:address app)}})

(defn- convert-value-flattened-app
  [{:keys [id primaryOperation propertyId drawings]
    [customer-doc work-description payee-doc] :documents
    :as app}]
  {:clientApplicationKind "FIXME"
   :customerWithContacts  {:customer (doc->customer customer-doc)
                           :contacts [(person->contact (customer-contact customer-doc))]}
   :geometry              {:geometryOperations (drawings->GeoJSON-2008 drawings)}
   :identificationNumber  id
   :invoicingCustomer     (doc->customer payee-doc)
   :name            (:name primaryOperation)
   :pendingOnClient true
   :postalAddress   (application-postal-address app)
   :propertyIdentificationNumber propertyId
   :workDescription (-> work-description :data :kayttotarkoitus)})

;;;; Putting it all together

(sc/defn application->allu-placement-contract :- PlacementContract [app]
  (-> app flatten-values convert-value-flattened-app))
