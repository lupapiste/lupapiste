(ns lupapalvelu.backing-system.allu.schemas
  "Schemas for ALLU integration. Mostly adapted from ALLU Swagger docs."
  (:require [schema.core :as sc :refer [defschema optional-key enum]]
            [schema-tools.core :as stc]
            [sade.municipality :refer [municipality-codes]]
            [sade.schemas :refer [NonBlankStr EmailAnyCase Zipcode Tel Hetu FinnishY Kiinteistotunnus
                                  ApplicationId ISO-3166-alpha-2 date-string]]
            [lupapalvelu.document.allu-schemas :refer [application-types application-kinds]]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo]
            [lupapalvelu.states :as states]
            [sade.shared-schemas :as sssc]
            [sade.schemas :as ssc]))

(defschema allu-date-string (date-string :date-time :date-time-no-ms))

(defschema LoginCredentials
  "User credentials for ALLU login API."
  {:password sc/Str
   :username sc/Str})

(defschema AlluUser
  "ALLU user name and title."
  {:name  sc/Str
   :title sc/Str})

(defschema ApplicationStatus
  "Application states in ALLU."
  (enum "PENDING_CLIENT", "PRE_RESERVED", "PENDING", "WAITING_INFORMATION", "INFORMATION_RECEIVED", "HANDLING"
        "NOTE", "RETURNED_TO_PREPARATION", "WAITING_CONTRACT_APPROVAL", "DECISIONMAKING", "DECISION", "REJECTED"
        "OPERATIONAL_CONDITION", "FINISHED", "CANCELLED", "REPLACED", "ARCHIVED"))

(defschema ApplicationHistoryParams
  "ALLU /applicationhistory body parameters"
  {:applicationIds [sssc/Nat]
   :eventsAfter    allu-date-string})

(defschema StatusChange
  "ALLU state change event."
  {:applicationIdentifier       sc/Str
   :eventTime                   allu-date-string
   :newStatus                   ApplicationStatus
   (optional-key :targetStatus) ApplicationStatus})

(defschema SupervisionStatus
  "ALLU supervision event state."
  (enum "APPROVED" "REJECTED" "OPEN" "CANCELLED"))

(defschema SupervisionTaskType
  "ALLU supervision task type."
  (enum "PRELIMINARY_SUPERVISION", "OPERATIONAL_CONDITION", "SUPERVISION", "WORK_TIME_SUPERVISION"
        "FINAL_SUPERVISION", "WARRANTY"))

(defschema SupervisionEvent
  "ALLU supervision event. Only used to make a reasonable [[ApplicationHistory]] schema."
  {:comment   sc/Str
   :eventTime allu-date-string
   :status    SupervisionStatus
   :type      SupervisionTaskType})

(defschema ApplicationHistory
  "State change and supervision history for applications in ALLU."
  {:applicationId     sssc/Nat
   :events            [StatusChange]
   :supervisionEvents [SupervisionEvent]})

(defschema MetadataBase
  "ALLU decision/contract file metadata basics; decisionMaker and handler."
  {:decisionMaker (sc/maybe AlluUser)
   :handler       AlluUser})

(defschema DecisionMetadata
  "ALLU metadata for decision files."
  MetadataBase)

(defschema ContractStatus
  "ALLU contract status enum."
  (enum "PROPOSAL" "ARRIVED" "REJECTED" "FINAL"))

(defschema ContractMetadata
  "ALLU metadta for contract files."
  (assoc MetadataBase
    :creationTime allu-date-string
    :status ContractStatus))

(defschema AlluMetadata
  "Metadata of any ALLU file (decision/contract)."
  (sc/conditional
    :status ContractMetadata
    :else DecisionMetadata))

(defschema ApplicationType
  "ALLU application type as :lower-kebab-case-keyword."
  (apply sc/enum (keys application-types)))

(defschema ApplicationKind
  "ALLU fixed location kind as :lower-kebab-case-keyword."
  (apply sc/enum (keys application-kinds)))

(defschema APPLICATION_KIND
  "ALLU fixed location kind from ALLU API; 'CAPS_SNAKE_STRING'."
  (apply sc/enum (vals application-kinds)))

(defschema LocationType
  "Location/drawings type ('fixed' from ALLU semiconstants or 'custom' from Lupapiste user)."
  (enum "fixed" "custom"))

(defschema SijoituslupaOperation
  "An application primaryOperation.name that is classified as a :Sijoituslupa."
  (->> canonical-common/ya-operation-type-to-schema-name-key
       (filter (comp #(= % :Sijoituslupa) val))
       (map (comp name key))
       (apply sc/enum)))

;; TODO: Improve this based on the standard.
(defschema JHS-106
  "JHS-106 -standardin mukainen katuosoite.

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
  orderer: onko tämä hakemuksen tilaaja itse?
  phone: puhelinnumero
  postalAddress: postiosoite"
  {(optional-key :email)         EmailAnyCase
   (optional-key :id)            sc/Int
   :name                         NonBlankStr
   (optional-key :orderer)       sc/Bool                    ; TODO: Emit this in ./conversion.clj (?)
   (optional-key :phone)         Tel
   (optional-key :postalAddress) PostalAddress})

(defschema CustomerType
  "Hakijan/maksajan tyyppi (henkil\u00f6/yritys/yhdistys/muu)."
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
  {:country                          ISO-3166-alpha-2
   (optional-key :email)             EmailAnyCase
   (optional-key :id)                sc/Int
   (optional-key :invoicingOperator) NonBlankStr            ; TODO: Is there some format for this?
   :name                             NonBlankStr
   (optional-key :ovt)               sc/Str
   (optional-key :phone)             Tel
   (optional-key :postalAddress)     PostalAddress
   (optional-key :registryKey)       sc/Str
   :type                             CustomerType})

(defschema CustomerWithContacts
  "Customer and their contact infromation(s)."
  {:contacts [(sc/one Contact "primary contact") Contact]
   :customer Customer})

(defschema FeaturelessGeoJSON-2008
  "A GeoJSON object that is not a Feature or a FeatureCollection."
  (sc/conditional
    geo/point? (geo/with-crs geo/Point)
    geo/multi-point? (geo/with-crs geo/MultiPoint)
    geo/line-string? (geo/with-crs geo/LineString)
    geo/multi-line-string? (geo/with-crs geo/MultiLineString)
    geo/polygon? (geo/with-crs geo/Polygon)
    geo/multi-polygon? (geo/with-crs geo/MultiPolygon)
    geo/geometry-collection? (geo/with-crs geo/GeometryCollection)
    'FeaturelessGeoJSONObject))

(defschema FixedLocationId
  "ALLU id of a [[FixedLocation]]."
  (sc/named sc/Int 'FixedLocationId))

(defschema FixedLocation
  "ALLU API fixed location, e.g. Rautatientori."
  {:applicationKind        APPLICATION_KIND
   :id                     FixedLocationId
   :area                   sc/Str
   (optional-key :section) sc/Str
   :geometry               (let [single (get-in geo/GeometryCollection [:geometries 0])]
                             (assoc geo/GeometryCollection
                               :geometries [(sc/one single "compulsory first SingleGeometry") single]))})

(defschema ContractBase
  "Base contract which contains the fields that are in common with all contract types

  customerReference: viitenumero laskuun
  endTime: alueen käytön loppuaika (ei käytössä Lupapisteessä)
  geometry: käytettävän alueen geometriat
  identificationNumber: (Lupapisteen) hakemustunnus
  invoicingCustomer: maksajan tiedot
  name: hakemuksen nimi / lyhyt kuvaus
  pendingOnClient: tehdäänkö Lupapisteessä vielä muutoksia
  postalAddress: varattavan alueen postiosoite
  representativeWithContacts: TODO
  startTime: alueen käytön alkuaika (ei käytössä Lupapisteessä)"
  {(optional-key :area)                         sc/Num
   (optional-key :customerReference)            NonBlankStr
   :customerWithContacts                        CustomerWithContacts
   :endTime                                     allu-date-string
   :geometry                                    FeaturelessGeoJSON-2008
   :identificationNumber                        ApplicationId
   (optional-key :invoicingCustomer)            Customer
   :name                                        NonBlankStr
   (optional-key :pendingOnClient)              sc/Bool
   (optional-key :postalAddress)                PostalAddress
   (optional-key :propertyIdentificationNumber) Kiinteistotunnus
   (optional-key :representativeWithContacts)   CustomerWithContacts
   :startTime                                   allu-date-string
   (optional-key :workDescription)              NonBlankStr})

(defschema PlacementContract
  "Sijoitushakemuksen/sopimuksen tiedot.

  clientApplicationKind: Hakemuksen laji
  propertyIdentificationNumber: varattavan alueen kiinteist\u00f6tunnus
  workDescription: hankkeen (pidempi) kuvaus"
  (stc/merge ContractBase
             {:clientApplicationKind                       NonBlankStr
              (optional-key :propertyIdentificationNumber) sc/Any
              (optional-key :workDescription)              NonBlankStr}))

(defschema ShortTermRental
  "Lyhytaikaisen maavuokran tiedot

  applicationKind: Hakemuksen laji
  fixedLocationIds: vuokrattavien sijainten id-numerot
  description: hankkeen (pidempi) kuvaus"
  (stc/merge ContractBase
             {:applicationKind                 NonBlankStr
              (optional-key :fixedLocationIds) [sc/Int]
              (optional-key :description)      NonBlankStr}))

(defschema Promotion
  "Promootion tiedot

  eventStartTime: promootion alkuaika
  eventEndTime: promootion loppuaika
  structureDescription: rakenteiden kuvaus
  structureArea: rakenteiden vaatima pinta-ala"
  (stc/merge ContractBase
             {(optional-key :fixedLocationIds)     [sc/Int]
              (optional-key :description)          NonBlankStr
              :eventStartTime                      allu-date-string
              :eventEndTime                        allu-date-string
              (optional-key :structureDescription) NonBlankStr
              (optional-key :structureArea)        sc/Num}))

(defschema AttachmentMetadata
  "Metadata for attachments.

  name: attachment file name
  description: attachment description
  mimeType: MIME type of the attachment file"
  {:name        sc/Str
   :description sc/Str
   :mimeType    (sc/maybe sc/Str)})

(defschema AttachmentFile
  "Data for ALLU attachments

  fileId: id in storage system
  storageSystem: storage database where the file is stored
  attach-self: if true, convert the application to pdf and send to ALLU as an attachment"
  (sc/conditional :fileId {:fileId        sssc/FileId
                           :storageSystem sssc/StorageSystem}
                  :attach-self {:attach-self sc/Bool}))

(defschema ValidContractApplicationBase
  "A partial schema for a Lupapiste applications that can be used in ALLU integration. For testing."
  {:id             ApplicationId
   :created        ssc/Timestamp
   :state          (apply sc/enum (map name states/all-states))
   :organization   (sc/eq "091-YA")
   :propertyId     Kiinteistotunnus
   :municipality   (apply sc/enum municipality-codes)
   :address        NonBlankStr
   :location-wgs84 [(sc/one geo/FiniteNum "longitude") (sc/one geo/FiniteNum "latitude")]
   :drawings       [{:geometry-wgs84 geo/SingleGeometry}]})

(defschema ValidPlacementApplication
  "A partial schema for a Lupapiste application that can be converted into a `PlacementContract`. For testing."
  (assoc ValidContractApplicationBase
    :permitType (sc/eq "YA")
    :permitSubtype (sc/eq "sijoitussopimus")
    :primaryOperation {:name SijoituslupaOperation}
    :documents [(sc/one (dds/doc-data-schema "hakija-ya" true) "applicant")
                (sc/one (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true) "description")
                (sc/one (dds/doc-data-schema "yleiset-alueet-maksaja" true) "payee")
                (sc/optional (dds/doc-data-schema "hakijan-asiamies" true) "representative")]))

(defschema ValidShortTermRental
  "A partial schema for a Lupapiste application that can be converted into a `ShortTermRental`. For testing."
  (assoc ValidContractApplicationBase
    :permitType (sc/eq "A")
    :operation-name (sc/eq "lyhytaikainen-maanvuokraus")
    :documents [(sc/one (dds/doc-data-schema "hakija-ya" true) "applicant")
                (sc/one (dds/doc-data-schema "lyhytaikainen-maanvuokraus" true) "description")
                (sc/one (dds/doc-data-schema "yleiset-alueet-maksaja" true) "payee")
                (sc/one (dds/doc-data-schema "lmv-location" true) "location")
                (sc/one (dds/doc-data-schema "lmv-time" true) "time")
                (sc/optional (dds/doc-data-schema "hakijan-asiamies" true) "representative")]))

(defschema ValidPromotion
  "A partial schema for a Lupapiste application that can be converted into a `Promotion`. For testing."
  (assoc ValidContractApplicationBase
    :permitType (sc/eq "A")
    :operation-name (sc/eq "promootio")
    :documents [(sc/one (dds/doc-data-schema "hakija-ya" true) "applicant")
                (sc/one (dds/doc-data-schema "promootio" true) "promotion")
                (sc/one (dds/doc-data-schema "promootio-time" true) "time")
                (sc/one (dds/doc-data-schema "promootio-location" true) "location")
                (sc/one (dds/doc-data-schema "promootio-structures" true) "structures")
                (sc/one (dds/doc-data-schema "promootio-info" true) "info")
                (sc/one (dds/doc-data-schema "yleiset-alueet-maksaja" true) "payee")
                (sc/optional (dds/doc-data-schema "hakijan-asiamies" true) "representative")]))
