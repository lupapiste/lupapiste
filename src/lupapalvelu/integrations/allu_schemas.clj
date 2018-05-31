(ns lupapalvelu.integrations.allu-schemas
  (:require [schema.core :as sc :refer [defschema enum optional-key cond-pre Int Bool]]
            [iso-country-codes.countries :as countries]
            [sade.schemas :refer [NonBlankStr NonEmptyVec
                                  Email Zipcode Tel Hetu FinnishY FinnishOVTid
                                  Kiinteistotunnus ApplicationId]]
            [sade.validators :refer [matches?]]
            [lupapalvelu.integrations.geojson-2008-schemas :refer [GeoJSON-2008]]))

(defschema ISO-3166-alpha-2
  (sc/pred (into #{} (map :alpha-2) countries/countries)
           "ISO-3166 alpha-2 country code"))

;; TODO: Improve this based on the standard.
(defschema JHS-106
  {(optional-key :apartmentNumber) NonBlankStr
   (optional-key :divisionLetter)  NonBlankStr
   (optional-key :entranceLetter)  NonBlankStr
   (optional-key :premiseNumber)   NonBlankStr
   (optional-key :streetName)      NonBlankStr})

(defschema RegistryKey
  (cond-pre Hetu FinnishY))

;; TODO: Pending specification
(defschema ClientApplicationKind
  NonBlankStr)

(defschema PostalAddress
  {(optional-key :city)          NonBlankStr
   (optional-key :postalCode)    Zipcode
   (optional-key :streetAddress) JHS-106})

(defschema Contact
  {(optional-key :email) Email
   (optional-key :id)    Int
   :name                 NonBlankStr
   (optional-key :phone) Tel
   (optional-key :postalAddress) PostalAddress})

(defschema CustomerType
  (enum "PERSON" "COMPANY" "ASSOCIATION" "OTHER"))

(defschema Customer
  {:country              ISO-3166-alpha-2
   (optional-key :email) Email
   (optional-key :id)    Int
   (optional-key :invoicingOperator) NonBlankStr ; TODO: Is there some format for this?
   :name                 NonBlankStr
   (optional-key :ovt)   FinnishOVTid
   (optional-key :phone) Tel
   (optional-key :postalAddress) PostalAddress
   (optional-key :registryKey)   RegistryKey
   :type CustomerType})

;; TODO: :startTime and :endTime should be RFC-3339 but on the other hand we don't use them anyway.
(defschema PlacementContract
  {:clientApplicationKind            ClientApplicationKind
   (optional-key :customerReference) NonBlankStr
   :customerWithContacts             {:contacts (NonEmptyVec Contact)
                                      :customer Customer}
   (optional-key :endTime) NonBlankStr
   :geometry               {:geometryOperations GeoJSON-2008}
   :identificationNumber             ApplicationId
   (optional-key :invoicingCustomer) Customer
   (optional-key :name)            NonBlankStr
   (optional-key :pendingOnClient) Bool
   (optional-key :postalAddress)   PostalAddress
   (optional-key :propertyIdentificationNumber) Kiinteistotunnus
   (optional-key :startTime)       NonBlankStr
   (optional-key :workDescription) NonBlankStr})
