(ns lupapalvelu.mml.geocoding.client
  "API of the MML \"Geokoodauspalvelu V2\".

  Note that the response schemas are only partially defined. Also,
  the returned properties depend on both the endpoint service and the
  selected data source."
  (:require [clojure.string :as str]
            [lupapalvelu.mml.geocoding.client-impl :as impl]
            [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def ^:dynamic ^:private *client*
  "Provide a layer of indirection for tests"
  impl/get!)

(def ^:private service->path
  {::forward "/geocoding/v2/pelias/search"
   ::reverse "/geocoding/v2/pelias/reverse"
   ::similar "/geocoding/v2/searchterm/similar"
   ::decode  "/geocoding/v2/searchterm/decode"})

(defn- pre-process-query
  [query]
  (-> query
      (dissoc :throw-on-failure?)
      (update :sources (partial str/join ","))))

(defn- run-query!
  [{:keys [service query]}]
  (-> {:path              (service->path service)
       :query             (pre-process-query query)
       :throw-on-failure? (:throw-on-failure? query)}
      *client*))

;; The API provides access to several different data sources (with different properties)

(def source-cadastral-units
  "Kiinteistörekisteri: Kiinteistötietojen kyselypalvelu (OGC API Features)"
  "cadastral-units")

(def source-building-addresses
  "Väestötietojärjestelmän rakennusten osoitteet: Rakennustietojen kyselypalvelu (WFS)"
  "addresses")

(def source-geographic-names
  "Nimistörekisteri: Nimistön kyselypalvelu (OGC API Features)"
  "geographic-names")

(def source-road-addresses
  "Maastotietokanta (laskennalliset tiestön osoitteet tuotettu maastotietokannan tiestöaineistosta)"
  "interpolated-road-addresses")

(def source-mapsheets-tm35
  "JHS 197kansallinen suositus"
  "mapsheets-tm35")

(sc/defschema Source
  "The Geocoding API provides access to several data sources"
  (sc/enum source-cadastral-units
           source-building-addresses
           source-geographic-names
           source-road-addresses
           source-mapsheets-tm35))

(def crs-etrs-tm32fin "EPSG:3067")

(def crs-wgs84
  "Only partially supported"
  "EPSG:4326")

(sc/defschema Crs (sc/enum crs-etrs-tm32fin crs-wgs84))

(sc/defschema Language (sc/enum "fi" "fin" "sv" "swe" "en" "eng"))

(sc/defschema StreetLanguage (sc/enum "fin" "swe"))

(sc/defschema CommonQuery
  (st/merge
    {:sources     [(sc/one Source "source") Source]
     :request-crs Crs
     :crs         Crs}
    (st/optional-keys
      {:size              ssc/PosInt
       :lang              Language
       :options           ssc/NonBlankStr
       :throw-on-failure? sc/Bool})))

(sc/defschema CommonProperty
  (st/merge
    {:kuntatunnus      ssc/DecimalString
     :kuntanimiFin     ssc/NonBlankStr
     :kuntanimiSwe     ssc/NonBlankStr}))

(sc/defschema Geometry
  {:coordinates [(sc/one ssc/LocationX "lon")
                 (sc/one ssc/LocationY "lat")]})

(sc/defschema CommonFeature
  {:geometry Geometry})

;; Reverse geocoding: coordinates -> address data

(sc/defschema ReverseSearchProperty
  (st/merge
    CommonProperty
    {:osoite.Osoite.katunimi ssc/NonBlankStr}
    (st/optional-keys
      ;; A single point usually returns a single address property,
      ;; but sometimes a building has two addresses (and maybe more?).
      ;; In addition, the number of properties is doubled if each address
      ;; also has a swedish alternative name => up to 4 choices.
      {:osoite.Osoite.kieli        StreetLanguage
       :osoite.Osoite.katunumero   ssc/NonBlankStr
       :osoite.Osoite#2.kieli      StreetLanguage
       :osoite.Osoite#2.katunimi   ssc/NonBlankStr
       :osoite.Osoite#2.katunumero ssc/NonBlankStr})))

(sc/defschema ReverseSearchResponse
  (st/open-schema
    {:features [(st/merge
                  CommonFeature
                  {:properties ReverseSearchProperty})]}))

(sc/defschema ReverseSearchQuery
  (st/merge
    CommonQuery
    {:point.lat ssc/LocationY
     :point.lon ssc/LocationX}))

(sc/defn reverse-search! :- (sc/maybe ReverseSearchResponse)
  "Execute a reverse lookup query (coordinates -> addresses)"
  [query :- ReverseSearchQuery]
  (run-query! {:service ::reverse
               :query   query}))

;; Forward geocoding: address or place name -> address data

(sc/defschema ForwardSearchProperty
  (st/merge
    CommonProperty
    {:katunimi   ssc/NonBlankStr
     :katunumero ssc/NonBlankStr}))

(sc/defschema ForwardSearchResponse
  (st/open-schema
    {:features [(st/merge
                  CommonFeature
                  {:properties ForwardSearchProperty})]}))

(sc/defschema ForwardSearchQuery
  (st/merge
    CommonQuery
    {:text ssc/NonBlankStr}))

(sc/defn forward-search! :- (sc/maybe ForwardSearchResponse)
  "Execute a text query (search term -> addresses)"
  [query :- ForwardSearchQuery]
  (run-query! {:service ::forward
               :query   query}))


(comment
  (let [query {:point.lon   309895.789
               :point.lat   6649487.765
               :sources     [source-building-addresses]
               :request-crs "EPSG:3067"
               :crs         "EPSG:3067"
               :size        1
               :lang        "fi"}]
    (reverse-search! query))
  (let [query {:text        "insinöörinkatu 58, Tampere"
               :sources     [source-building-addresses]
               :request-crs "EPSG:3067"
               :crs         "EPSG:3067"
               :size        3
               :lang        "sv"}]
    (forward-search! query))

  (let [query {:text        "tampere kunta"
               :sources     [source-geographic-names]
               :request-crs "EPSG:3067"
               :crs         "EPSG:3067"
               :size        5
               :lang        "fi"}]
    (forward-search! query)))
