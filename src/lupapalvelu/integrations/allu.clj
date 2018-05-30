(ns lupapalvelu.integrations.allu
  (:require [clojure.walk :refer [postwalk]]
            [schema.core :as sc]
            [iso-country-codes.core :refer [country-translate]]
            [sade.core :refer [def-]]
            [lupapalvelu.integrations.allu-schemas :refer [PlacementContract]]))

;;; FIXME: Avoid producing nil-valued fields.

;;;; Cleaning up :value indirections

(defn- flatten-values [app]
  (letfn [(flatten-node [node]
            (if (and (map? node) (contains? node :value))
              (:value node)
              node))]
    (postwalk flatten-node app)))

;;;; Conversion details

(def- convert-customer-type
  {:henkilo "PERSON", :yritys "COMPANY"})

(defn- fullname [{:keys [etunimi sukunimi]}]
  (str etunimi " " sukunimi))

(defn- address-country [address]
  (country-translate :alpha-3 :alpha-2 (:maa address)))

(defn- convert-address [{:keys [postitoimipaikannimi postinumero katu]}]
  {:city          postitoimipaikannimi
   :postalCode    postinumero
   :streetAddress {:streetName katu}})

(defn- doc->customer [{{tag :_selected :as data} :data}]
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
                 :name (:yritysnimi customer)
                 :country (address-country address)
                 :postalAddress (convert-address address)}))))

(defn- person->contact [{:keys [henkilotiedot], {:keys [puhelin email]} :yhteystiedot}]
  {:name (fullname henkilotiedot), :phone puhelin, :email email})

(defn- doc->customer-with-contacts [{{tag :_selected :as data} :data :as doc}]
  (let [tag (keyword tag)]
    (case tag
      :henkilo {:customer (doc->customer doc)
                :contacts [(person->contact (get data tag))]}
      :yritys {:customer (doc->customer doc)
               :contacts [(person->contact (:yhteyshenkilo (get data tag)))]})))

(defn- application-postal-address [app]
  {:streetAddress {:streetName (:address app)}})

(defn- convert-value-flattened-app
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
   :workDescription (-> work-description :data :kayttotarkoitus)})

;;;; Putting it all together

(sc/defn application->allu-placement-contract :- PlacementContract [app]
  (-> app flatten-values convert-value-flattened-app))
