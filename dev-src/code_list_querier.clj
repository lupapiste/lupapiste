(ns code-list-querier
  "Generic code list querier for fetching and storing code data from external APIs.

   !!
   Instructions for this file can be found in /docs/code-list-querier.md
   !!

   Provides a centralized configuration map for different code list sources
   and a single entrypoint function to update one, multiple, or all code lists.

   Usage:
     (update-code-lists!)                    ; Updates all configured code lists
     (update-code-lists! :language-data)     ; Updates only language data
     (update-code-lists! [:language-data])   ; Updates multiple"
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [lupapalvelu.logging :as log]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as su]
            [schema.core :as sc :refer [defschema]]))

(set! *warn-on-reflection* true)

;;
;; Schemas
;;

;; not sure if we should actually validate against this schema, but it's here for now to help
(defschema CodeItem
  "Schema for code list item from koodistot.suomi.fi API"
  {:prefLabel      {:fi sc/Str
                    :sv sc/Str
                    :en sc/Str}
   :codeValue      sc/Str
   :created        sc/Str
   :membersUrl     sc/Str
   :modified       sc/Str
   :status         sc/Str
   :id             sc/Str
   :statusModified sc/Str
   :url            sc/Str
   :order          sc/Int
   :uri            sc/Str
   :hierarchyLevel sc/Int})

(defschema I18nConfig
  {;; Path where the i18n file will be written
   :i18n-file-path  sc/Str
   ;; Base path for i18n keys (e.g., \"language\")
   :i18n-path       sc/Str
   ;; Function to read and parse the EDN file for i18n generation.
   ;; Takes file-path as argument and returns a list of CodeItems.
   ;; Defaults to default-code-list-reader if not provided
   (sc/optional-key :data-reader-fn) (sc/pred fn?)
   ;; Function to extract code value from a code item.
   ;; Takes a code item as argument and returns a string.
   ;; Defaults to :codeValue key if not provided
   (sc/optional-key :code-value-fn) (sc/pred fn?)
   ;; If true (default), skip entries missing :fi or :sv labels and
   ;; log a warning; when false, write whatever labels are present
   (sc/optional-key :skip-missing) sc/Bool})

(defschema ConfigItem
  {;; URL to fetch the code list data from
   :url             sc/Str
   ;; Path to the EDN resource file (for reading in i18n generation)
   :resource-path   sc/Str
   ;; Defaults to ./resources/{resource-path}, but can be overwritten in special cases
   (sc/optional-key :edn-file-path) sc/Str
   ;; Function to transform the raw data before writing.
   (sc/optional-key :transform-fn) (sc/pred fn?)
   ;; Map with keys for i18n file generation. If not given no localization file is written
   (sc/optional-key :i18n-config) I18nConfig})

(defschema CodeListConfigs
  {sc/Any ConfigItem})

(def valid-code-list-configs! (sc/validator CodeListConfigs))

(defn fetch-code-list
  "Fetches code list data from the given URL.
   Returns the response body as parsed JSON."
  [url]
  (log/info "Fetching code list from:" url)
  (-> (http/hato-get url {:as :json})
      :body))

(defn- write-code-list-to-file
  "Writes code list data to an EDN file with an edit warning comment."
  [file-path data]
  (log/info "Writing code list data to:" file-path)
  (io/make-parents file-path)
  (with-open [writer (io/writer file-path)]
    (pprint/pprint data writer))
  (log/info "Successfully wrote code list to:" file-path))

(defn- quoted-string [s]
  (str "\"" (name s) "\""))

(defn- default-transform-fn
  "Default transformation function for code list data.
   Sorts languageCodes and codes by order to avoid unnecessary file diffs."
  [data]
  (cond-> (select-keys data [:languageCodes :codes :modified])
    (:languageCodes data) (update :languageCodes (partial sort-by :order))
    (:codes data) (update :codes (partial sort-by :order))
    (:organizations data) (dissoc :organizations)))

(defn mapping->code-value
  "Returns the code value (string) for a code item by matching its :uri in mapping."
  [mapping {:keys [uri] :as _code-item}]
  (some (fn [[k v]] (when (= v uri) (name k))) mapping))

(defn default-code-list-reader
  "Default function to read and filter code list data from an EDN resource.

   Parameters:
   - resource-path: Path to the EDN resource file"
  [resource-path]
  (->> (su/read-edn-resource resource-path)
       :codes
       (su/filter-by-key :status "VALID")))

(defn- prepend-code-to-labels
  "Prepends the codeValue to each translation in prefLabel.
   E.g., {:fi \"Foo\"} with codeValue \"01\" becomes {:fi \"01 - Foo\"}"
  [{:keys [codeValue] :as code-item}]
  (update code-item
          :prefLabel
          update-vals (fn [text] (str codeValue " - " text))))

(defn code-list-reader-with-code-prefix
  "Works like the default-code-list-reader but also prepends codeValue to each translation in prefLabel.

   Parameters:
   - resource-path: Path to the EDN resource file"
  [resource-path]
  (->> (default-code-list-reader resource-path)
       (mapv prepend-code-to-labels)))

(defn codelist-api-url
  "Shortcut to create URI for the codelist JSON api just with registry and scheme names."
  [registry-code scheme-code]
  (str "https://koodistot.suomi.fi/codelist-api/api/v1/coderegistries/"
       (name registry-code)
       "/codeschemes/"
       scheme-code
       "/?format=json&embedCodes=true&embedExtensions=true&embedMembers=true&expand=extension,member,codeScheme,code,memberValue,codeRegistry,organization,valueType,externalReference,propertyType"))

(defn suomi-uri->api-url
  "Parse Suomi.fi URI for codeset so it can be used to create codelist-api URL.
  This allows using the same URI everywhere on the codebase for the codeset."
  [suomi-uri]
  (let [[_ _ _ _ registry-code scheme-code] (ss/split suomi-uri #"/")]
    (codelist-api-url registry-code scheme-code)))

(def code-list-configs
  "Centralized configuration for all code list sources"
  (valid-code-list-configs!
    {:elinkaaren-vaihe
     {:url           (codelist-api-url :rytj "rakennuksen-toim-osan-elinkaaren-vaihe")
      :resource-path "codes/elinkaaren-vaihe.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/elinkaaren-vaihe.txt"
                      :i18n-path      "suomi.fi.elinkaaren-vaihe"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/elinkaaren-vaihe)}}

     :energialuokka
     {:url           (codelist-api-url :rytj "Energiatehokkuusluokka")
      :edn-file-path "./resources/codes/energiatehokkuusluokka.edn"
      :resource-path "codes/energiatehokkuusluokka.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/energiatehokkuusluokka.txt"
                      :i18n-path      "suomi.fi.energiatehokkuusluokka"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/energiatehokkuusluokka)}}

     :energialuokka-alaindeksi
     {:url           (codelist-api-url :rytj "energialuokkaAlaindeksi")
      :edn-file-path "./resources/codes/energialuokka-alaindeksi.edn"
      :resource-path "codes/energialuokka-alaindeksi.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/energialuokka-alaindeksi.txt"
                      :i18n-path      "suomi.fi.energialuokka-alaindeksi"
                      :skip-missing   false
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/energialuokka-alaindeksi)}}

     :etvra-tyyppi
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/rakrek/erityistatoimintaavartenrakennettavanalueentyyppi")
      :resource-path "codes/etvra-tyyppi.edn"}

     :hissin-laji
     {:url           (codelist-api-url :rytj "hissi")
      :resource-path "codes/hissin-laji.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/hissin-laji.txt"
                      :i18n-path      "suomi.fi.hissin-laji"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/hissin-laji)}}

     :hulevedenkasittely
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/rytj/hulevedenkasittely")
      :resource-path "codes/hulevedenkasittely.edn"}

     :huoneiston-muutoksen-laji
     {:url           (codelist-api-url :rytj "huoneistonmuutoksenlaji")
      :resource-path "codes/huoneiston-muutoksen-laji.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/huoneiston-muutoksen-laji.txt"
                      :i18n-path      "suomi.fi.huoneiston-muutoksen-laji"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/huoneiston-muutoksen-laji)}}

     :huoneiston-varuste
     {:url           (codelist-api-url :rytj "HuoneistoVaruste")
      :resource-path "codes/huoneiston-varuste.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/huoneiston-varuste.txt"
                      :i18n-path      "suomi.fi.huoneiston-varuste"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/huoneiston-varuste)}}

     :huoneistotyyppi
     {:url           (codelist-api-url :rytj "Huoneistotyyppi")
      :resource-path "codes/huoneistotyyppi.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/huoneistotyyppi.txt"
                      :i18n-path      "suomi.fi.huoneistotyyppi"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/huoneistotyyppi)}}

     :ilmanvaihtotavan-laji
     {:url           (codelist-api-url :rytj "ilmanvaihto")
      :resource-path "codes/ilmanvaihtotavan-laji.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/ilmanvaihtotavan-laji.txt"
                      :i18n-path      "suomi.fi.ilmanvaihtotavan-laji"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/ilmanvaihtotavan-laji)}}

     :ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji
     {:url           (codelist-api-url :rytj "is-raja-arvoista-poikkeamisen-laji")
      :resource-path "codes/ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji.txt"
                      :i18n-path      "suomi.fi.ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji"
                      :skip-missing   false
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/ilmastoselvityksen-raja-arvoista-poikkeamisen-perusteen-laji)}}

     :jaahdytysenergianlahde
     {:url           (codelist-api-url :rytj "jaahdytys")
      :edn-file-path "./resources/codes/jaahdytysenergianlahde.edn"
      :resource-path "codes/jaahdytysenergianlahde.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/jaahdytysenergianlahde.txt"
                      :i18n-path      "suomi.fi.jaahdytysenergianlahde"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/jaahdytysenergianlahde)}}

     :jaahdytystapa
     {:url           (codelist-api-url :rytj "jaahdytystapa")
      :edn-file-path "./resources/codes/jaahdytystapa.edn"
      :resource-path "codes/jaahdytystapa.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/jaahdytystapa.txt"
                      :i18n-path      "suomi.fi.jaahdytystapa"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/jaahdytystapa)}}

     :jatevesienkasittely
     {:url           (codelist-api-url :rytj "jatevesienkasittely")
      :resource-path "codes/jatevesienkasittely.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/jatevesienkasittely.txt"
                      :i18n-path      "suomi.fi.jatevesienkasittely"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/jatevesienkasittely)}}

     :julkisivu
     {:url           (codelist-api-url :rytj "julkisivunrakennusaine")
      :resource-path "codes/julkisivu.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/julkisivu.txt"
                      :i18n-path      "suomi.fi.julkisivu"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/julkisivu)}}

     :kaavatilanne
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/vtj/Rake_kaava")
      :resource-path "codes/kaavatilanne.edn"}

     :kantava-rakennusaine
     {:url           (codelist-api-url :rytj "kantavanrakenteenrakennusaine")
      :resource-path "codes/kantava-rakennusaine.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/kantava-rakennusaine.txt"
                      :i18n-path      "suomi.fi.kantava-rakennusaine"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/kantava-rakennusaine)}}

     :katselmuksen-tilanne
     {:url           (codelist-api-url :rytj "KatselmuksenTilanne")
      :resource-path "codes/katselmuksen-tilanne.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/katselmuksen-tilanne.txt"
                      :i18n-path      "suomi.fi.katselmuksen-tilanne"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/katselmuksen-tilanne)}}

     :keittiotyyppi
     {:url           (codelist-api-url :rytj "Keittiotyyppi")
      :resource-path "codes/keittiotyyppi.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/keittiotyyppi.txt"
                      :i18n-path      "suomi.fi.keittiotyyppi"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/keittiotyyppi)}}

     :lammitystapa
     {:url           (codelist-api-url :rytj "lammitystapa")
      :edn-file-path "./resources/codes/lammitystapa.edn"
      :resource-path "codes/lammitystapa.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/lammitystapa.txt"
                      :i18n-path      "suomi.fi.lammitystapa"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/lammitystapa)}}

     :lammonlahde
     {:url           (codelist-api-url :rytj "lammitysenergianlahde")
      :edn-file-path "./resources/codes/lammonlahde.edn"
      :resource-path "codes/lammonlahde.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/lammonlahde.txt"
                      :i18n-path      "suomi.fi.lammonlahde"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/lammonlahde)}}

     :language-data
     {:url           (codelist-api-url :interoperabilityplatform "languagecodes")
      :resource-path "codes/languages.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/languages.txt"
                      :data-reader-fn ryhti-mappings/language-code-list-reader
                      :skip-missing   true
                      :i18n-path      "language"}}

     :lupamaarayksen-laji
     {:url           (codelist-api-url :rytj "lupamaarayksenlaji")
      :resource-path "codes/lupamaarayksen-laji.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/lupamaarayksen-laji.txt"
                      :i18n-path      "suomi.fi.lupamaarayksen-laji"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/lupamaarayksen-laji)}}

     :omistajalaji
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/rytj/Omistajalaji")
      :resource-path "codes/omistajalaji.edn"}

     :ostoenergian-laji
     {:url           (codelist-api-url :rytj "ostoenergia")
      :resource-path "codes/ostoenergian-laji.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/ostoenergian-laji.txt"
                      :i18n-path      "suomi.fi.ostoenergian-laji"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/ostoenergian-laji)}}

     :paloluokka
     {:url           (codelist-api-url :rytj "Paloluokka")
      :resource-path "codes/paloluokka.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/paloluokka.txt"
                      :i18n-path      "suomi.fi.paloluokka"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/paloluokka)}}

     :postal-codes
     {:url           (codelist-api-url :statbr "postitoimipaik_1_20160607")
      :resource-path ryhti-mappings/postal-codes-data-resource-path
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/postal-codes.txt"
                      :i18n-path      "suomi.fi.postal-codes"
                      :data-reader-fn default-code-list-reader}}

     :rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa
     {:url           (codelist-api-url :rytj "rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa")
      :resource-path "codes/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa.txt"
                      :i18n-path      "suomi.fi.rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa"
                      :skip-missing   false
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa)}}

     :rakennusluokitus
     {:url           (codelist-api-url :jhs "rakennus_1_20180712")
      :resource-path ryhti-mappings/rakennusluokitus-data-resource-path
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/rakennusluokitus.txt"
                      :i18n-path      "suomi.fi.rakennusluokitus"
                      :code-value-fn  ryhti-mappings/rakennusluokitus-code-value
                      :data-reader-fn ryhti-mappings/rakennusluokitus-data-reader}}

     :rakennuspaikan-hallintaperuste
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/rytj/RakennuspaikanHallintaperuste")
      :resource-path "codes/rakennuspaikan-hallintaperuste.edn"}

     :rakennusvaruste
     {:url           (codelist-api-url :rytj "RakennusVaruste")
      :resource-path "codes/rakennusvaruste.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/rakennusvaruste.txt"
                      :i18n-path      "suomi.fi.rakennusvaruste"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/rakennusvaruste)}}

     :rakentamistapa
     {:url           (codelist-api-url :vtj "Rake_runkotapa")
      :resource-path "codes/rakentamistapa.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/rakentamistapa.txt"
                      :i18n-path      "suomi.fi.rakentamistapa"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/rakentamistapa)}}

     :rakentamistoimenpide
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/rytj/Rakentamistoimenpide")
      :resource-path "codes/rakentamistoimenpide.edn"}

     :sahkoenergianlahde
     {:url           (codelist-api-url :rytj "sahkoenergianlahde")
      :edn-file-path "./resources/codes/sahkoenergianlahde.edn"
      :resource-path "codes/sahkoenergianlahde.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/sahkoenergianlahde.txt"
                      :i18n-path      "suomi.fi.sahkoenergianlahde"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/sahkoenergianlahde)}}

     :sijaintikerroksen-tarkenne
     {:url           (codelist-api-url :rytj "sijaintikerroksenTarkenne")
      :resource-path "codes/sijaintikerroksen-tarkenne.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/sijaintikerroksen-tarkenne.txt"
                      :i18n-path      "suomi.fi.sijaintikerroksen-tarkenne"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/sijaintikerroksen-tarkenne)}}

     :sisaankaynnin-tyyppi
     {:url           (codelist-api-url :rytj "sisaankaynti")
      :resource-path "codes/sisaankaynnin-tyyppi.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/sisaankaynnin-tyyppi.txt"
                      :i18n-path      "suomi.fi.sisaankaynnin-tyyppi"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/sisaankaynnin-tyyppi)}}

     :suhde-pintaan
     {:url           (codelist-api-url :rytj "Suhde_pintaan")
      :resource-path "codes/suhdepintaan.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/suhde-pintaan.txt"
                      :i18n-path      "suomi.fi.suhde-maan-pintaan"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/suhde-pintaan)}}

     :suunnittelijanrooli
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/rytj/suunnittelijanrooli")
      :resource-path "codes/suunnittelijanrooli.edn"}

     :talousvesityyppi
     {:url           (codelist-api-url :rytj "talousvesi")
      :resource-path "codes/talousvesityyppi.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/talousvesityyppi.txt"
                      :i18n-path      "suomi.fi.talousvesityyppi"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/talousvesityyppi)}}

     :tyonjohtajan-rooli
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli")
      :resource-path "codes/tyonjohtajan-rooli.edn"}

     :vaativuusluokat
     ;; We have localizations for this already
     ;; Codeset included here so we get warning if new values are added
     {:url           (suomi-uri->api-url "http://uri.suomi.fi/codelist/rytj/vaativuusluokat")
      :resource-path "codes/vaativuusluokat.edn"}

     :vaestonsuoja-luokka
     {:url           (codelist-api-url :rytj "VaestonsuojaLuokka")
      :resource-path "codes/vaestonsuoja-luokka.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/vaestonsuoja-luokka.txt"
                      :i18n-path      "suomi.fi.vaestonsuoja-luokka"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/vaestonsuoja-luokka)
                      :data-reader-fn code-list-reader-with-code-prefix}}

     :verkostoliittymatyyppi
     {:url           (codelist-api-url :rytj "verkliit")
      :resource-path "codes/verkostoliittymatyyppi.edn"
      :i18n-config   {:i18n-file-path "./resources/i18n/generated/verkostoliittymatyyppi.txt"
                      :i18n-path      "suomi.fi.verkostoliittymatyyppi"
                      :code-value-fn  (partial mapping->code-value ryhti-mappings/verkostoliittymat)}}
     })) ; Separate row for easier merging

(defn- write-i18n-file
  [{:keys [i18n-file-path i18n-path data-reader-fn code-value-fn skip-missing]
    :or   {skip-missing true}} resource-path]
  (let [reader-fn (or data-reader-fn default-code-list-reader)
        code-value-fn (or code-value-fn :codeValue)]
    (with-open [writer (io/writer i18n-file-path)]
      (.write writer ^String
              (str (->> (reader-fn resource-path)
                        (mapcat
                          (fn [{:keys [uri] :as code-item}] ; code-item conforms to CodeItem schema
                            (let [code-value (code-value-fn code-item)
                                  labels (:prefLabel code-item)]
                              (cond
                                (not code-value)
                                (throw (ex-info (str "Missing code-value for code item: " uri)
                                                {:type ::missing-mapping
                                                 :uri uri}))

                                (and skip-missing (not (and (:fi labels) (:sv labels))))
                                (log/warn code-value "skipping: fi and sv not defined")

                                :else
                                (keep (fn [[lang text]]
                                        (when (some? text)
                                          (->> (vector (su/kw-path i18n-path code-value) lang text)
                                               (map quoted-string)
                                               (ss/join " "))))
                                      labels)))))
                        (ss/join "\n"))
                   "\n")))))

(defn- update-single-code-list!
  "Updates a single code list from its configuration."
  [config-key]
  (if-let [config (get code-list-configs config-key)]
    (try
      (let [{:keys [url edn-file-path transform-fn
                    i18n-config resource-path]} config
            transform-fn (or transform-fn default-transform-fn)
            raw-data (fetch-code-list url)
            transformed-data (transform-fn raw-data)
            edn-file-path (or edn-file-path (str "./resources/" resource-path))]
        (write-code-list-to-file edn-file-path transformed-data)

        ;; Handle i18n generation if configured
        (when i18n-config
          (log/info "Generating i18n file for" config-key)
          (write-i18n-file i18n-config resource-path))

        (log/info "Successfully updated" config-key)
        {:status :success
         :config-key config-key
         :file-path edn-file-path})
      (catch Exception e
        (log/error e "Failed to update code list:" config-key)
        {:status :error
         :config-key config-key
         :error (.getMessage e)}))
    (do
      (log/error "Unknown code list configuration:" config-key)
      {:status :error
       :config-key config-key
       :error (str "Unknown configuration: " config-key)})))

(defn update-code-lists!
  "Updates one, multiple, or all configured code lists.

     Parameters:
     - keys: Optional. Can be:
             - nil/omitted: Updates all configured code lists
             - keyword: Updates a single code list (e.g., :language-data)
             - collection: Updates multiple code lists (e.g., [:language-data :other-data])

     Returns a map with results for each updated code list.

     Examples:
       (update-code-lists!)                    ; Updates all
       (update-code-lists! :language-data)     ; Updates only language data
       (update-code-lists! [:language-data])   ; Updates multiple

     Remember to push changes in:
      - ./resources/country-and-language-data/languages.edn
      - ./resources/i18n/languages.txt"
  ([]
   (update-code-lists! (keys code-list-configs)))
  ([keys]
   (let [keys-to-update (cond
                          (keyword? keys) [keys]
                          (coll? keys) keys
                          :else [keys])]
     (log/info "Updating code lists:" keys-to-update)
     (into {} (map (fn [k] [k (update-single-code-list! k)]) keys-to-update)))))

(comment
  ;; Update all code lists
  (update-code-lists!)

  ;; Update only language data
  (update-code-lists! :language-data)

  ;; Update multiple specific code lists
  (update-code-lists! [:language-data]))
