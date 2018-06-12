(ns lupapalvelu.integrations.allu-schemas
  (:require [schema.core :as sc :refer [defschema enum optional-key cond-pre Int Bool]]
            [clj-time.format :as tf]
            [iso-country-codes.countries :as countries]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [sade.schemas :refer [NonBlankStr Email Zipcode Tel Hetu FinnishY FinnishOVTid Kiinteistotunnus
                                  ApplicationId]]
            [sade.municipality :refer [municipality-codes]]
            [lupapalvelu.document.canonical-common :as doccc]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo :refer [SingleGeometry GeoJSON-2008]]))

;;;; # Generalia

(defschema DateTimeNoMs
  "ISO-8601/RFC-3339 aikaleima sekuntien tarkkuudella"
  (sc/pred (fn [s] (try (tf/parse (tf/formatters :date-time-no-ms) s)
                        (catch Exception _ false)))
           "ISO-8601/RFC-3339 date time without milliseconds"))

(defschema ISO-3166-alpha-2
  "Kaksikirjaiminen maakoodi (esim. 'FI')"
  (apply sc/enum (map :alpha-2 countries/countries)))

(defschema ISO-3166-alpha-3
  "Kolmikirjaiminen maakoodi (esim. 'FIN')"
  (apply sc/enum (map :alpha-3 countries/countries)))

(defschema MunicipalityCode
  "Kolminumeroinen kuntatunnus"
  (apply sc/enum municipality-codes))

(defschema FeaturelessGeoJSON-2008
  (sc/conditional
    geo/point? (geo/with-crs geo/Point)
    geo/multi-point? (geo/with-crs geo/MultiPoint)
    geo/line-string? (geo/with-crs geo/LineString)
    geo/multi-line-string? (geo/with-crs geo/MultiLineString)
    geo/polygon? (geo/with-crs geo/Polygon)
    geo/multi-polygon? (geo/with-crs geo/MultiPolygon)
    geo/geometry-collection? (geo/with-crs geo/GeometryCollection)
    'FeaturelessGeoJSONObject))

;;;; # Lupapiste applications

;;;; ## Merely Having the Right Types

(defschema TypedAddress
  {:katu                 {:value sc/Str}
   :postinumero          {:value sc/Str}
   :postitoimipaikannimi {:value sc/Str}
   :maa                  {:value sc/Str}})

(defschema TypedContactInfo
  {:puhelin {:value sc/Str}
   :email   {:value sc/Str}})

(defschema TypedPersonDoc
  {:henkilotiedot {:etunimi  {:value sc/Str}
                   :sukunimi {:value sc/Str}
                   :hetu     {:value sc/Str}}
   :osoite        TypedAddress
   :yhteystiedot  TypedContactInfo})

(defschema TypedCompanyDoc
  {:yritysnimi           {:value sc/Str}
   :liikeJaYhteisoTunnus {:value sc/Str}
   :osoite               TypedAddress
   :yhteyshenkilo        {:henkilotiedot {:etunimi  {:value sc/Str}
                                          :sukunimi {:value sc/Str}}
                          :yhteystiedot  TypedContactInfo}})

(defschema TypedCustomerDoc
  {:schema-info {:subtype (sc/enum :hakija :maksaja)}
   :data        {:_selected {:value (sc/enum "henkilo" "yritys")}
                 :henkilo   TypedPersonDoc
                 :yritys    TypedCompanyDoc}})

(defschema TypedApplicantDoc
  (assoc-in TypedCustomerDoc [:schema-info :subtype] (sc/eq :hakija)))

(defschema TypedPaymentInfo
  {:verkkolaskuTunnus {:value sc/Str}
   :ovtTunnus         {:value sc/Str}
   :valittajaTunnus   {:value sc/Str}})

(defschema TypedPayeeDoc
  (-> TypedCustomerDoc
      (assoc-in [:schema-info :subtype] (sc/eq :maksaja))
      (assoc-in [:data :laskuviite] {:value sc/Str})
      (assoc-in [:data :yritys :verkkolaskutustieto] TypedPaymentInfo)))

(defschema TypedDescriptionDoc
  {:schema-info {:subtype (sc/eq :hankkeen-kuvaus)}
   :data        {:kayttotarkoitus {:value sc/Str}}})

(defschema TypedPlacementApplication
  {:id               sc/Str
   :propertyId       sc/Str
   :municipality     sc/Str
   :address          sc/Str
   :primaryOperation {:name (apply sc/enum (keys doccc/ya-operation-type-to-schema-name-key))}
   :documents        [(sc/one TypedApplicantDoc "applicant")
                      (sc/one TypedDescriptionDoc "description")
                      (sc/one TypedPayeeDoc "payee")]
   :location-wgs84   [(sc/one sc/Num "longitude") (sc/one sc/Num "latitude")]
   :drawings         [{:geometry-wgs84 GeoJSON-2008}]})

;;;; ## Valid for Submitting

(defschema ValidAddress
  {:katu                 {:value NonBlankStr}
   :postinumero          {:value Zipcode}
   :postitoimipaikannimi {:value NonBlankStr}
   :maa                  {:value ISO-3166-alpha-3}})

(defschema ValidContactInfo
  {:puhelin {:value Tel}
   :email   {:value Email}})

(defschema ValidPersonDoc
  {:henkilotiedot {:etunimi  {:value NonBlankStr}
                   :sukunimi {:value NonBlankStr}
                   :hetu     {:value Hetu}}
   :osoite        ValidAddress
   :yhteystiedot  ValidContactInfo})

(defschema ValidCompanyDoc
  {:yritysnimi           {:value NonBlankStr}
   :liikeJaYhteisoTunnus FinnishY
   :osoite               ValidAddress
   :yhteyshenkilo        {:henkilotiedot {:etunimi  {:value NonBlankStr}
                                          :sukunimi {:value NonBlankStr}}
                          :yhteystiedot  ValidContactInfo}})

(defschema ValidCustomerDoc
  {:schema-info {:subtype (sc/enum :hakija :maksaja)}
   :data        {:_selected {:value (sc/enum "henkilo" "yritys")}
                 :henkilo   ValidPersonDoc
                 :yritys    ValidCompanyDoc}})

(defschema ValidApplicantDoc
  (assoc-in ValidCustomerDoc [:schema-info :subtype] (sc/eq :hakija)))

(defschema ValidPaymentInfo
  {:verkkolaskuTunnus {:value NonBlankStr}
   :ovtTunnus         {:value FinnishOVTid}
   :valittajaTunnus   {:value NonBlankStr}})

(defschema ValidPayeeDoc
  (-> ValidCustomerDoc
      (assoc-in [:schema-info :subtype] (sc/eq :maksaja))
      (assoc-in [:data :laskuviite] {:value sc/Str})
      (assoc-in [:data :yritys :verkkolaskutustieto] ValidPaymentInfo)))

(def ValidDescriptionDoc
  {:schema-info {:subtype (sc/eq :hankkeen-kuvaus)}
   :data        {:kayttotarkoitus {:value NonBlankStr}}})

(defschema ValidPlacementApplication
  {:id               ApplicationId
   :propertyId       Kiinteistotunnus
   :municipality     MunicipalityCode
   :address          NonBlankStr
   :primaryOperation {:name (apply sc/enum (keys doccc/ya-operation-type-to-schema-name-key))}
   :documents        [(sc/one ValidApplicantDoc "applicant")
                      (sc/one ValidDescriptionDoc "description")
                      (sc/one ValidPayeeDoc "payee")]
   :location-wgs84   [(sc/one sc/Num "longitude") (sc/one sc/Num "latitude")]
   :drawings         [{:geometry-wgs84 SingleGeometry}]})

;;;; # ALLU Placement Contracts

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
  {(optional-key :email)         Email
   (optional-key :id)            Int
   :name                         NonBlankStr
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
   (optional-key :email)             Email
   (optional-key :id)                Int
   (optional-key :invoicingOperator) NonBlankStr            ; TODO: Is there some format for this?
   :name                             NonBlankStr
   (optional-key :ovt)               FinnishOVTid
   (optional-key :phone)             Tel
   (optional-key :postalAddress)     PostalAddress
   (optional-key :registryKey)       RegistryKey
   :type                             CustomerType})

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
  {:clientApplicationKind                       NonBlankStr
   (optional-key :customerReference)            NonBlankStr
   :customerWithContacts                        {:contacts [(sc/one Contact "primary contact") Contact]
                                                 :customer Customer}
   :endTime                                     DateTimeNoMs
   :geometry                                    FeaturelessGeoJSON-2008
   :identificationNumber                        ApplicationId
   (optional-key :invoicingCustomer)            Customer
   :name                                        NonBlankStr
   (optional-key :pendingOnClient)              Bool
   (optional-key :postalAddress)                PostalAddress
   (optional-key :propertyIdentificationNumber) Kiinteistotunnus
   :startTime                                   DateTimeNoMs
   (optional-key :workDescription)              NonBlankStr})
