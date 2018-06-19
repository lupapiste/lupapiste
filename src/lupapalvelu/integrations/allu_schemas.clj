(ns lupapalvelu.integrations.allu-schemas
  (:require [schema.core :as sc :refer [defschema enum optional-key cond-pre Int Bool]]
            [clj-time.format :as tf]
            [iso-country-codes.countries :as countries]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [sade.schemas :refer [NonBlankStr Email Zipcode Tel Hetu FinnishY FinnishOVTid Kiinteistotunnus
                                  ApplicationId]]
            [sade.municipality :refer [municipality-codes]]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo :refer [SingleGeometry]]))

;;; TODO: Remove this namespace after migrating everything in here to more appropriate places.

;;;; # Generalia

(defschema DateTimeNoMs
  "ISO-8601/RFC-3339 date time without milliseconds"
  (sc/pred (fn [s] (try (tf/parse (tf/formatters :date-time-no-ms) s)
                        (catch Exception _ false)))
           "ISO-8601/RFC-3339 date time without milliseconds"))

(defschema ISO-3166-alpha-2
  "Two letter country code (e.g. 'FI')"
  (apply sc/enum (map :alpha-2 countries/countries)))

(defschema ISO-3166-alpha-3
  "Two letter country code (e.g. 'FIN')"
  (apply sc/enum (map :alpha-3 countries/countries)))

(defschema FinnishMunicipalityCode
  "Three digit Finnish municipality code"
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

(defschema ValidPlacementApplication
  {:id               ApplicationId
   :permitSubtype    NonBlankStr
   :organization     NonBlankStr
   :propertyId       Kiinteistotunnus
   :municipality     FinnishMunicipalityCode
   :address          NonBlankStr
   :primaryOperation {:name (apply sc/enum (map name (keys ya-operation-type-to-schema-name-key)))}
   :documents        [(sc/one (dds/doc-data-schema "hakija-ya" true) "applicant")
                      (sc/one (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true) "description")
                      (sc/one (dds/doc-data-schema "yleiset-alueet-maksaja" true) "payee")]
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

