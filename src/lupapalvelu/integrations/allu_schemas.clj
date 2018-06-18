(ns lupapalvelu.integrations.allu-schemas
  (:require [schema.core :as sc :refer [defschema enum optional-key cond-pre Int Bool]]
            [clj-time.format :as tf]
            [iso-country-codes.countries :as countries]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [sade.schemas :refer [NonBlankStr Email Zipcode Tel Hetu FinnishY FinnishOVTid Kiinteistotunnus
                                  ApplicationId]]
            [sade.municipality :refer [municipality-codes]]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo :refer [SingleGeometry]]))

;;; TODO: Remove this namespace after migrating everything in here to more appropriate places.

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
   :liikeJaYhteisoTunnus {:value FinnishY}
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
  {:verkkolaskuTunnus {:value sc/Str}
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
   :permitSubtype    NonBlankStr
   :organization     NonBlankStr
   :propertyId       Kiinteistotunnus
   :municipality     MunicipalityCode
   :address          NonBlankStr
   :primaryOperation {:name (apply sc/enum (map name (keys ya-operation-type-to-schema-name-key)))}
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

