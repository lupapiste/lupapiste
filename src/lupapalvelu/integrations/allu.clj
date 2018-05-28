(ns lupapalvelu.integrations.allu
  (:require [schema.core :as sc]
            [iso-country-codes.core :refer [country-translate]]
            [sade.core :refer [def-]]
            [lupapalvelu.integrations.allu-schemas :refer [PlacementContract]]))

;;; FIXME: Avoid producing nil-valued fields.
;;; TODO: Abstract the :value selections away.

(def- convert-customer-type
  {:henkilo "PERSON", :yritys "COMPANY"})

(defn- fullname [{:keys [etunimi sukunimi]}]
  (str (:value etunimi) " " (:value sukunimi)))

(defn- address-country [address]
  (country-translate :alpha-3 :alpha-2 (-> address :maa :value)))

(defn- convert-address [{:keys [postitoimipaikannimi postinumero katu]}]
  {:city          (:value postitoimipaikannimi)
   :postalCode    (:value postinumero)
   :streetAddress {:streetName (:value katu)}})

(defn- doc->customer [{{{tag :value} :_selected :as data} :data}]
  (let [tag (keyword tag)]
    (case tag
      :henkilo (let [customer (get data tag)
                     address (:osoite customer)]
                 {:type (convert-customer-type tag)
                  :name (fullname (:henkilotiedot customer))
                  :country (address-country address)
                  :postalAddress (convert-address address)})

      :yritys (let [customer (get data tag)
                    address (:osoite customer)]
                {:type (convert-customer-type tag)
                 :name (-> customer :yritysnimi :value)
                 :country (address-country address)
                 :postalAddress (convert-address address)}))))

(defn- person->contact [{:keys [henkilotiedot], {:keys [puhelin email]} :yhteystiedot}]
  {:name (fullname henkilotiedot), :phone (:value puhelin), :email (:value email)})

(defn- doc->customer-with-contacts [{{{tag :value} :_selected :as data} :data :as doc}]
  (let [tag (keyword tag)]
    (case tag
      :henkilo {:customer (doc->customer doc)
                :contacts [(person->contact (get data tag))]}
      :yritys {:customer (doc->customer doc)
               :contacts [(person->contact (:yhteyshenkilo (get data tag)))]})))

(defn- application-postal-address [app]
  {:streetAddress {:streetName (:address app)}})

(sc/defn application->allu-placement-contract :- PlacementContract
  [{:keys [id primaryOperation propertyId], [customer-doc work-description payee-doc] :documents
    :as app}]
  {:clientApplicationKind "FIXME"
   :customerWithContacts  (doc->customer-with-contacts customer-doc)
   :geometry              {:geometryOperations {}}
   :identificationNumber  id
   :invoicingCustomer     (doc->customer payee-doc)
   :name            (:name primaryOperation)
   :pendingOnClient true
   :postalAddress   (application-postal-address app)
   :propertyIdentificationNumber propertyId
   :workDescription (-> work-description :data :kayttotarkoitus :value)})
