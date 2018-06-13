(ns lupapalvelu.exports
  "This namespace contains functionality used by the export API"
  (:require [clojure.set :refer [rename-keys]]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [schema.core :as sc]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.excel-reader :as xls]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util :refer [fn->]]
            [lupapalvelu.application :as application]
            [lupapalvelu.archive.archiving :as archiving]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.minimal :refer [dummy-onkalo-log-entry]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]))

;;
;; Export applications
;;

(def kayttotarkoitus-hinnasto (delay (xls/read-map "kayttotarkoitus-hinnasto.xlsx")))

(defn- uuden-rakentaminen [application operation]
  {:post [%]}
  (let [doc (domain/get-document-by-operation application operation)
        [_ kayttotarkoitus] (first (tools/deep-find doc [:kayttotarkoitus :value]))]
    (if (ss/blank? kayttotarkoitus)
      {:price-class "C" :kayttotarkoitus nil}
      {:price-class (get @kayttotarkoitus-hinnasto kayttotarkoitus) :kayttotarkoitus kayttotarkoitus})))

(def permit-type-price-codes
  {"R" 901
   "P" 901
   "YA" 902
   "KT" 904
   "MM" 904
   "MAL" 903
   "YI"  903
   "YL" 903
   "YM" 903
   "VVVL" 903
   "ARK" 802})

(def usage-price-codes
  {"A" 905
   "B" 906
   "C" 907
   "D" 908
   "E" 909
   "Z" 801})

(def price-classes-for-operation
  ; See lupapiste-chef/cookbooks/lupapiste-dw/files/default/etl/setupdata/price_class_csv.csv

  {:asuinrakennus               uuden-rakentaminen ; old operation tree
   :vapaa-ajan-asuinrakennus    uuden-rakentaminen
   :varasto-tms                 uuden-rakentaminen
   :julkinen-rakennus           uuden-rakentaminen ; old operation tree
   :muu-uusi-rakentaminen       uuden-rakentaminen
   :laajentaminen               "D"
   :perus-tai-kant-rak-muutos   "D"
   :kayttotark-muutos           "D"
   :julkisivu-muutos            "D"
   :jakaminen-tai-yhdistaminen  "D"
   :markatilan-laajentaminen    "D"
   :takka-tai-hormi             "D"
   :parveke-tai-terassi         "D"
   :muu-laajentaminen           "D"
   :auto-katos                  "D"
   :masto-tms                   "D"
   :mainoslaite                 "D"
   :aita                        "D"
   :maalampo                    "D"
   :jatevesi                    "D"
   :muu-rakentaminen            "D"
   :rakennustietojen-korjaus    "D"
   :purkaminen                  "D"
   :kaivuu                      "D"
   :puun-kaataminen             "D"
   :muu-maisema-toimenpide      "D"
   :tontin-ajoliittyman-muutos  "D"
   :paikoutysjarjestus-muutos   "D"
   :kortteli-yht-alue-muutos    "D"
   :muu-tontti-tai-kort-muutos  "D"
   :poikkeamis                  "D"
   :meluilmoitus                "D"
   :pima                        "D"
   :maa-aineslupa               "D"
   :aiemmalla-luvalla-hakeminen "D"
   :tyonjohtajan-nimeaminen     "E" ; old operation tree
   :tyonjohtajan-nimeaminen-v2  "E"
   :suunnittelijan-nimeaminen   "D"
   :jatkoaika                   "D"
   :aloitusoikeus               "D"
   :vvvl-vesijohdosta               "D"
   :vvvl-viemarista                 "D"
   :vvvl-vesijohdosta-ja-viemarista "D"
   :vvvl-hulevesiviemarista         "D"
   :ya-kayttolupa-tapahtumat                                          "D"
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen                    "D"
   :ya-kayttolupa-metsastys                                           "D"
   :ya-kayttolupa-vesistoluvat                                        "D"
   :ya-kayttolupa-terassit                                            "F"
   :ya-kayttolupa-kioskit                                             "D"
   :ya-kayttolupa-muu-kayttolupa                                      "D"
   :ya-kayttolupa-mainostus-ja-viitoitus                              "D"
   :ya-kayttolupa-nostotyot                                           "D"
   :ya-kayttolupa-vaihtolavat                                         "F"
   :ya-kayttolupa-kattolumien-pudotustyot                             "D"
   :ya-katulupa-muu-liikennealuetyo                                   "D"
   :ya-kayttolupa-talon-julkisivutyot                                 "D"
   :ya-kayttolupa-talon-rakennustyot                                  "D"
   :ya-kayttolupa-muu-tyomaakaytto                                    "D"
   :ya-katulupa-vesi-ja-viemarityot                                   "D"
   :ya-katulupa-maalampotyot                                          "D"
   :ya-katulupa-kaukolampotyot                                        "D"
   :ya-katulupa-kaapelityot                                           "D"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat           "D"
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen             "F"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                     "F"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen                   "F"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen      "F"
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen            "F"
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                        "F"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                        "F"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                       "F"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen        "F"
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen          "F"
   :ya-sijoituslupa-muu-sijoituslupa                                  "F"
   :ya-jatkoaika                                                      "D"
   :yl-uusi-toiminta                                                  "B"
   :yl-olemassa-oleva-toiminta                                        "D"
   :yl-toiminnan-muutos                                               "D"
   :yl-puiden-kaataminen                                              "D"
   :koeluontoinen-toiminta                                            "D"
   :tonttijako                                                        "D"
   :rasitetoimitus                                                    "D"
   :rajankaynti                                                       "D"
   :asemakaava                                                        "D"
   :ranta-asemakaava                                                  "D"
   :yleiskaava                                                        "D"
   :kiinteistonmuodostus                                              "D"
   :kerrostalo-rivitalo                                               uuden-rakentaminen
   :pientalo                                                          uuden-rakentaminen
   :teollisuusrakennus                                                uuden-rakentaminen
   :linjasaneeraus                                                    "D"
   :rak-valm-tyo                                                      "D"
   :raktyo-aloit-loppuunsaat                                          "D"
   :sisatila-muutos                                                   "D"
   :kerrostalo-rt-laaj                                                "D"
   :pientalo-laaj                                                     "D"
   :talousrakennus-laaj                                               "D"
   :teollisuusrakennus-laaj                                           "D"
   :vapaa-ajan-rakennus-laaj                                          "D"
   :muu-rakennus-laaj                                                 "D"
   :tontin-jarjestelymuutos                                           "D"
   :jatteen-keraystoiminta                                            "D"
   :muistomerkin-rauhoittaminen                                       "D"
   :lannan-varastointi                                                "D"
   :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan  "D"
   :ilmoitus-poikkeuksellisesta-tilanteesta                           "D"
   :maa-ainesten-kotitarveotto                                        "D"
   :maastoliikennelaki-kilpailut-ja-harjoitukset                      "D"
   :archiving-project                                                 "Z"
   })

(defn- resolve-price-class
  "priceClass = legacy price class, which is mapped in .csv in dw.
   priceCode = new price codes for 'puitesopimus'
   use = usage code for some price classes (kayttotarkoitus)
   useFi = usage code in Finnish
   useSv = usage code in Svedish
   usagePriceCode = mapping from legacy priceClass to new price code (called 'kayttotarkoitushinnasto')"
  [application op]
  (let [op-name  (keyword (:name op))
        price-class (get price-classes-for-operation op-name)]

    (cond
      ; special case 1: paperilupa
      (= :aiemmalla-luvalla-hakeminen op-name )
      (assoc op
        :priceClass price-class
        :priceCode nil
        :use nil
        :useFi nil
        :useSv nil
        :usagePriceCode (get usage-price-codes price-class))

      ; new buildings: fixed price code, but usage price code is determined from the building data
      (= uuden-rakentaminen price-class)
      (let [{:keys [price-class kayttotarkoitus]} (uuden-rakentaminen application op)]
        (assoc op
          :priceClass price-class
          :priceCode 900
          :use kayttotarkoitus
          :useFi (when-not (ss/blank? kayttotarkoitus) (i18n/localize :fi :kayttotarkoitus kayttotarkoitus))
          :useSv (when-not (ss/blank? kayttotarkoitus) (i18n/localize :sv :kayttotarkoitus kayttotarkoitus))
          :usagePriceCode (get usage-price-codes price-class)))

       ; In other cases the price code is determined from permit type
       :else
       (assoc op
         :priceClass price-class
         :priceCode (get permit-type-price-codes (:permitType application))
         :use nil
         :useFi nil
         :useSv nil
         :usagePriceCode (get usage-price-codes price-class)))))

(defn- operation-mapper [application op]
  (util/assoc-when-pred (resolve-price-class application op) util/not-empty-or-nil?
    :displayNameFi (i18n/localize "fi" "operations" (:name op))
    :displayNameSv (i18n/localize "sv" "operations" (:name op))
    :submitted     (when (= "aiemmalla-luvalla-hakeminen" (:name op)) (:created application))))

(defn- verdict-mapper [verdict]
  (-> verdict
    (select-keys [:id :kuntalupatunnus :timestamp :paatokset])
    (update :paatokset #(map (fn [paatos]
                               (merge
                                 (select-keys (:paivamaarat paatos) [:anto :lainvoimainen])
                                 (select-keys (-> paatos :poytakirjat first) [:paatos :paatoksentekija :paatospvm]))) %))))

(defn exported-application [application]
  (-> application
      (update :operations #(map (partial operation-mapper application) %))
      (update :verdicts #(map verdict-mapper %))
                                        ; documents not needed in DW
      (dissoc :documents)))

(defn application-to-salesforce [application]
  (letfn [(truncate-op-description [op] (update op :description #(ss/limit % 252 "...")))
          (map-operations [app] (->> (application/get-operations app)
                                     (map truncate-op-description)
                                     (map (partial operation-mapper app))))]
    (-> application
        (assoc  :operations          (map-operations application))
        (update :primaryOperation    truncate-op-description)
        (update :secondaryOperations (fn [ops] (map truncate-op-description ops)))
        (dissoc :documents))))

(def operation-schema
  {:id          ssc/ObjectIdStr
   :created     ssc/Timestamp
   :description (sc/maybe (ssc/max-length-string 255))
   :name        sc/Str})

(def export-operation-schema
  (merge operation-schema
         {(sc/optional-key :displayNameFi) sc/Str
          (sc/optional-key :displayNameSv) sc/Str
          :priceClass                      (sc/enum "A" "B" "C" "D" "E" "F" "Z")
          :priceCode                       (sc/maybe (apply sc/enum 900 (vals permit-type-price-codes)))
          :usagePriceCode                  (sc/maybe (apply sc/enum (vals usage-price-codes)))
          :use                             (sc/maybe sc/Str)
          :useSv                           (sc/maybe sc/Str)
          :useFi                           (sc/maybe sc/Str)
          (sc/optional-key :submitted)     ssc/Timestamp}))

(sc/defschema SalesforceExportApplication
  "Application schema for export to Salesforce"
  (merge {:id                                ssc/ApplicationId
          :address                           sc/Str
          :archived                          archiving/archived-ts-keys-schema
          :infoRequest                       sc/Bool
          :municipality                      sc/Str
          :state                             (apply sc/enum (map name states/all-states))
          (sc/optional-key :openInfoRequest) (sc/maybe sc/Bool)
          :organization                      sc/Str
          (sc/optional-key :permitSubtype)   (sc/maybe sc/Str)
          :permitType                        (apply sc/enum (keys (permit/permit-types)))
          :propertyId                        sc/Str
          :primaryOperation                  operation-schema
          :secondaryOperations               [operation-schema]}
         {:operations [export-operation-schema]}
         (zipmap [:created :modified] (repeat ssc/Timestamp))
         (zipmap [(sc/optional-key :started)
                  (sc/optional-key :closed)
                  (sc/optional-key :opened)
                  (sc/optional-key :sent) :submitted] (repeat (sc/maybe ssc/Timestamp)))))

(defn validate-application-export-data
  "Validate output data against schema."
  [_ {:keys [applications]}]
  (doseq [exported-application applications]
    (sc/validate SalesforceExportApplication exported-application)))


;;
;; Export Onkalo API usage
;;

(defn timestamp->end-of-month-date-string
  "Given Unix timestamp in milliseconds, returns a string
  representation of the last day of the month in which the timestamp
  resides."
  [^Long ts]
  (-> ts
      tc/from-long
      (t/to-time-zone (t/time-zone-for-id "Europe/Helsinki"))
      t/last-day-of-the-month
      tc/to-long
      util/to-xml-date))

(sc/defschema ArchiveApiUsageLogEntry
  "Schema for API usage log entries, produced by Onkalo"
  {:organization org/OrgId
   :timestamp sc/Int
   sc/Keyword sc/Any}) ; Rest of the data TBD

(sc/defschema SalesforceExportArchiveApiUsageEntry
  "Schema for archive API usage entry, exported to Salesforce"
  {:organization                   org/OrgId
   :lastDateOfTransactionMonth     (ssc/date-string "YYYY-MM-dd")
   :quantity                       ssc/Nat})

(sc/defschema SalesforceExportArchiveApiUsageResponse
  {:lastRunTimestampMillis ssc/Nat
   :transactions [SalesforceExportArchiveApiUsageEntry]})

(defn- most-recent-mongo-insert-timestamp [log-entries start-ts]
  (inc (if (seq log-entries)
         (->> log-entries (map :logged) (apply max))
         start-ts)))

(defn- archive-api-transactions-sorted-by-month-and-organization [log-entries]
  (->> log-entries
       (map (fn-> (select-keys [:organization :timestamp])
                  (update :timestamp timestamp->end-of-month-date-string)))
       (group-by (juxt :organization :timestamp))
       (util/map-values count)
       (map (fn [[[org-id last-date-of-month] quantity-of-transactions-in-given-month]]
              {:organization               org-id
               :lastDateOfTransactionMonth last-date-of-month
               :quantity                   quantity-of-transactions-in-given-month}))))

(sc/defn ^:always-validate
  onkalo-log-entries->salesforce-export-entries :- SalesforceExportArchiveApiUsageResponse
  "Maps onkalo log entries into data consumed by the salesforce integration.
   lastRuntimestampMillis is either the timestamp of most recent insert to mongo, or, if
   there are no log-entries, start-ts, so that the next batchrun will use the same starting
   timestamp as the current one."
  [log-entries :- [ArchiveApiUsageLogEntry]
   start-ts :- ssc/Nat]
  {:lastRunTimestampMillis (most-recent-mongo-insert-timestamp log-entries start-ts)
   :transactions (archive-api-transactions-sorted-by-month-and-organization log-entries)})

(defn mock-onkalo-api-logs
  "A mock implementation for actually fetching Onkalo logs from Mongo"
  [start-ts end-ts]
  (let [number-of-entries (rand-int 50)]
    (repeatedly number-of-entries #(dummy-onkalo-log-entry start-ts end-ts))))

(defn onkalo-api-logs-from-mongo
  [start-ts end-ts]
  (mongo/select :archive-api-usage
                {:logged {$gte start-ts
                          $lte end-ts}}
                [:organization :logged :timestamp]))

(def get-onkalo-api-logs! (if (env/feature? :mock-api-usage)
                            mock-onkalo-api-logs
                            onkalo-api-logs-from-mongo))

(defn archive-api-usage-to-salesforce
  [start-ts end-ts]
  {:pre [(number? start-ts) (number? end-ts) (<= start-ts end-ts)]}
  (-> (get-onkalo-api-logs! start-ts end-ts)
      (onkalo-log-entries->salesforce-export-entries start-ts)))
