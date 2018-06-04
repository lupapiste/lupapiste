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
  entranceLetter: kirjainosa (yleensä porras)
  premiseNumber: osoitenumero
  streetName: tien/kadun nimi

  Lupapisteessä katuosoite on pelkkä merkkijono. ALLUssa se voidaan vain laittaa suoraan
  streetName-kenttään ja muut kentät jättää pois."
  {(optional-key :apartmentNumber) NonBlankStr
   (optional-key :divisionLetter)  NonBlankStr
   (optional-key :entranceLetter)  NonBlankStr
   (optional-key :premiseNumber)   NonBlankStr
   (optional-key :streetName)      NonBlankStr})

(defschema RegistryKey
  "Henkilö- tai Y-tunnus"
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

  email: sähköpostiosoite
  id: Allun sisäinen id, voi jättää huomiotta, tulee poistumaan
  name: henkilön tai yrityksen nimi
  phone: puhelinnumero
  postalAddress: postiosoite"
  {(optional-key :email) Email
   (optional-key :id)    Int
   :name                 NonBlankStr
   (optional-key :phone) Tel
   (optional-key :postalAddress) PostalAddress})

(defschema CustomerType
  "Hakijan/maksajan tyyppi (hankilö/yritys/yhsitys/muu)."
  (enum "PERSON" "COMPANY" "ASSOCIATION" "OTHER"))

(defschema Customer
  "Hakijan/maksajan yleiset tiedot.

  country: kotimaa
  email: sähköpostiosoite
  id: Allun sisäinen id, voi jättää huomiotta, tulee poistumaan
  invoicingOperator: laskutusoperaattori (pankkitunnus (OKOYFIHH, NDEAFIHH ...) tms.)
  name: henkilön tai yrityksen nimi
  ovt: (yrityksen) OVT-tunnus
  phone: puhelinnumero
  postalAddress: postiosoite
  registryKey: henkilö- tai Y-tunnus
  type: Onko kyseessä henkilö, yritys, yhdistys vai jokin muu."
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
  endTime: alueen käytön loppuaika (ei käytössä Lupapisteessä)
  geometry: käytettävän alueen geometriat
  identificationNumber: (Lupapisteen) hakemustunnus
  invoicingCustomer: maksajan tiedot
  name: hakemuksen nimi / lyhyt kuvaus
  pendingOnClient: tehdäänkö Lupapisteessä vielä muutoksia
  postalAddress: varattavan alueen postiosoite
  propertyIdentificationNumber: varattavan alueen kiinteistötunnus
  startTime: alueen käytön alkuaika (ei käytössä Lupapisteessä)
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
