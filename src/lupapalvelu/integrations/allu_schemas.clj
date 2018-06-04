(ns lupapalvelu.integrations.allu-schemas
  (:require [schema.core :as sc :refer [defschema enum optional-key cond-pre Int Bool]]
            [iso-country-codes.countries :as countries]
            [sade.schemas :refer [NonBlankStr NonEmptyVec
                                  Email Zipcode Tel Hetu FinnishY FinnishOVTid
                                  Kiinteistotunnus ApplicationId]]
            [lupapalvelu.integrations.geojson-2008-schemas :refer [GeoJSON-2008]]))

(defschema ISO-3166-alpha-2
  "Kaksikirjaiminen maakoodi (esim. 'FI')"
  (sc/pred (into #{} (map :alpha-2) countries/countries)
           "ISO-3166 alpha-2 country code"))

;; TODO: Improve this based on the standard.
(defschema JHS-106
  "Katuosoite.

  apartmentNumber: huoneiston numero
  divisionLetter: jakokirjainosa (soluasunnot tms.)
  entranceLetter: kirjainosa (yleens\u00e4 porras)
  premiseNumber: osoitenumero
  streetName: tien/kadun nimi

  Lupapisteess\u00e4 katuosoite on pelkk\u00e4 merkkijono. ALLUssa se voidaan vain laittaa suoraan
  streetName-kentt\u00e4\u00e4n ja muut kent\u00e4t j\u00e4tt\u00e4\u00e4 pois."
  {(optional-key :apartmentNumber) NonBlankStr
   (optional-key :divisionLetter)  NonBlankStr
   (optional-key :entranceLetter)  NonBlankStr
   (optional-key :premiseNumber)   NonBlankStr
   (optional-key :streetName)      NonBlankStr})

(defschema RegistryKey
  "Henkil\u00f6- tai Y-tunnus"
  (cond-pre Hetu FinnishY))

;; TODO: Pending specification
(defschema ClientApplicationKind
  "Hakemuksen tyyppi"
  NonBlankStr)

(defschema PostalAddress
  "Postiosoite.

  city: postitoimipaikan nimi
  postalCode: postinumero
  streetAddress: katuosoite"
  {(optional-key :city)          NonBlankStr
   (optional-key :postalCode)    Zipcode
   (optional-key :streetAddress) JHS-106})

(defschema Contact
  "Yhteystieto.

  email: s\u00e4hk\u00f6postiosoite
  id: Allun sis\u00e4inen id, voi j\u00e4tt\u00e4\u00e4 huomiotta, tulee poistumaan
  name: henkil\u00f6n tai yrityksen nimi
  phone: puhelinnumero
  postalAddress: postiosoite"
  {(optional-key :email) Email
   (optional-key :id)    Int
   :name                 NonBlankStr
   (optional-key :phone) Tel
   (optional-key :postalAddress) PostalAddress})

(defschema CustomerType
  "Hakijan/maksajan tyyppi (hankil\u00f6/yritys/yhsitys/muu)."
  (enum "PERSON" "COMPANY" "ASSOCIATION" "OTHER"))

(defschema Customer
  "Hakijan/maksajan yleiset tiedot.

  country: kotimaa
  email: s\u00e4hk\u00f6postiosoite
  id: Allun sis\u00e4inen id, voi j\u00e4tt\u00e4\u00e4 huomiotta, tulee poistumaan
  invoicingOperator: laskutusoperaattori (pankkitunnus (OKOYFIHH, NDEAFIHH ...) tms.)
  name: henkil\u00f6n tai yrityksen nimi
  ovt: (yrityksen) OVT-tunnus
  phone: puhelinnumero
  postalAddress: postiosoite
  registryKey: henkil\u00f6- tai Y-tunnus
  type: Onko kyseess\u00e4 henkil\u00f6, yritys, yhdistys vai jokin muu."
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
  "Sijoitushakemuksen/sopimuksen tiedot.

  clientApplicationKind: Hakemuksen laji
  customerReference: viitenumero laskuun
  customerWithContacts: hakijan yleiset ja yhteystiedot
  endTime: alueen k\u00e4yt\u00f6n loppuaika (ei k\u00e4yt\u00f6ss\u00e4 Lupapisteess\u00e4)
  geometry: k\u00e4ytett\u00e4v\u00e4n alueen geometriat
  identificationNumber: (Lupapisteen) hakemustunnus
  invoicingCustomer: maksajan tiedot
  name: hakemuksen nimi / lyhyt kuvaus
  pendingOnClient: tehd\u00e4\u00e4nk\u00f6 Lupapisteess\u00e4 viel\u00e4 muutoksia
  postalAddress: varattavan alueen postiosoite
  propertyIdentificationNumber: varattavan alueen kiinteist\u00f6tunnus
  startTime: alueen k\u00e4yt\u00f6n alkuaika (ei k\u00e4yt\u00f6ss\u00e4 Lupapisteess\u00e4)
  workDescription: hankkeen (pidempi) kuvaus"
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
