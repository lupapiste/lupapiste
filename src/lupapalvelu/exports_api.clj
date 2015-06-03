(ns lupapalvelu.exports-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [monger.operators :refer :all]
            [sade.excel-reader :as xls]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [ok]]
            [lupapalvelu.action :refer [defexport] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]))

(def kayttotarkoitus-hinnasto (delay (xls/read-map "kayttotarkoitus-hinnasto.xlsx")))

(defn- uuden-rakentaminen [application operation]
  {:post [%]}
  (let [doc (domain/get-document-by-operation application operation)
        [_ kayttotarkoitus] (first (tools/deep-find doc [:kayttotarkoitus :value]))]
    (if (ss/blank? kayttotarkoitus)
      "C"
      (get @kayttotarkoitus-hinnasto kayttotarkoitus))))

(def price-classes-for-operation
  {:asuinrakennus               uuden-rakentaminen
   :vapaa-ajan-asuinrakennus    uuden-rakentaminen
   :varasto-tms                 uuden-rakentaminen
   :julkinen-rakennus           uuden-rakentaminen
   :muu-uusi-rakentaminen       uuden-rakentaminen
   :laajentaminen               "C"
   :perus-tai-kant-rak-muutos   "C"
   :kayttotark-muutos           "D"
   :julkisivu-muutos            "C"
   :jakaminen-tai-yhdistaminen  "C"
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
   :purkaminen                  "D"
   :kaivuu                      "D"
   :puun-kaataminen             "D"
   :muu-maisema-toimenpide      "D"
   :tontin-ajoliittyman-muutos  "D"
   :paikoutysjarjestus-muutos   "D"
   :kortteli-yht-alue-muutos    "D"
   :muu-tontti-tai-kort-muutos  "D"
   :poikkeamis                  "C"
   :meluilmoitus                "D"
   :pima                        "D"
   :maa-aineslupa               "D"
   :aiemmalla-luvalla-hakeminen "D"
   :tyonjohtajan-nimeaminen     "E"
   :tyonjohtajan-nimeaminen-v2  "E"
   :suunnittelijan-nimeaminen   "E"
   :jatkoaika                   "E"
   :aloitusoikeus               "E"
   :vvvl-vesijohdosta               "E"
   :vvvl-viemarista                 "E"
   :vvvl-vesijohdosta-ja-viemarista "E"
   :vvvl-hulevesiviemarista         "E"
   :ya-kayttolupa-tapahtumat                                          "D"
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen                    "D"
   :ya-kayttolupa-metsastys                                           "D"
   :ya-kayttolupa-vesistoluvat                                        "D"
   :ya-kayttolupa-terassit                                            "D"
   :ya-kayttolupa-kioskit                                             "D"
   :ya-kayttolupa-muu-kayttolupa                                      "D"
   :ya-kayttolupa-mainostus-ja-viitoitus                              "D"
   :ya-kayttolupa-nostotyot                                           "D"
   :ya-kayttolupa-vaihtolavat                                         "D"
   :ya-kayttolupa-kattolumien-pudotustyot                             "D"
   :ya-kayttolupa-muu-liikennealuetyo                                 "D"
   :ya-kayttolupa-talon-julkisivutyot                                 "D"
   :ya-kayttolupa-talon-rakennustyot                                  "D"
   :ya-kayttolupa-muu-tyomaakaytto                                    "D"
   :ya-katulupa-vesi-ja-viemarityot                                   "C"
   :ya-katulupa-maalampotyot                                          "C"
   :ya-katulupa-kaukolampotyot                                        "C"
   :ya-katulupa-kaapelityot                                           "C"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat           "C"
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen             "C"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                     "C"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen                   "C"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen      "C"
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen            "C"
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                        "C"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                        "C"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                       "C"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen        "C"
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen          "C"
   :ya-sijoituslupa-muu-sijoituslupa                                  "C"
   :ya-jatkoaika                                                      "D"
   :yl-uusi-toiminta                                                  "B"
   :yl-olemassa-oleva-toiminta                                        "D"
   :yl-toiminnan-muutos                                               "D"
   :tonttijaon-hakeminen                                              "D"
   :tonttijaon-muutoksen-hakeminen                                    "D"
   :tontin-lohkominen                                                 "D"
   :tilan-rekisteroiminen-tontiksi                                    "D"
   :yhdistaminen                                                      "D"
   :halkominen                                                        "D"
   :rasitetoimitus                                                    "D"
   :rajankaynnin-hakeminen                                            "D"
   :rajannayton-hakeminen                                             "D"
   :ya-lohkominen                                                     "D"
   :tilusvaihto                                                       "D"
   :rakennuksen-sijainti                                              "D"
   :asemakaava-laadinta                                               "D"
   :asemakaava-muutos                                                 "D"
   :ranta-asemakaava-laadinta                                         "D"
   :ranta-asemakaava-muutos                                           "D"
   :yleiskaava-laadinta                                               "D"
   :yleiskaava-muutos                                                 "D"
   :kerrostalo-rivitalo                                               uuden-rakentaminen
   :pientalo                                                          uuden-rakentaminen
   :teollisuusrakennus                                                uuden-rakentaminen
   :linjasaneeraus                                                    "C"
   :rak-valm-tyo                                                      "C"
   :raktyo-aloit-loppuunsaat                                          "C"
   :sisatila-muutos                                                   "C"
   :kerrostalo-rt-laaj                                                "C"
   :pientalo-laaj                                                     "C"
   :talousrakennus-laaj                                               "C"
   :teollisuusrakennus-laaj                                           "C"
   :vapaa-ajan-rakennus-laaj                                          "C"
   :muu-rakennus-laaj                                                 "C"
   :tontin-jarjestelymuutos                                           "D"
   })

(defn- export [collection query fields]
  (ok collection (mongo/select collection query fields)))

(defn- resolve-price-class [application op]
  (let [price-class (get price-classes-for-operation (keyword (:name op)))]
    (assoc op :priceClass (if (fn? price-class) (price-class application op) price-class))))

(defn- operation-mapper [application op]
  (util/assoc-when (resolve-price-class application op)
    :displayNameFi (i18n/localize "fi" "operations" (:name op))
    :displayNameSv (i18n/localize "sv" "operations" (:name op))
    :submitted     (when (= "aiemmalla-luvalla-hakeminen" (:name op)) (:created application))))

(defexport export-applications
  {:user-roles #{:trusted-etl}}
  [{{ts :modifiedAfterTimestampMillis} :data user :user}]
  (let [query (merge
                (domain/application-query-for user)
                {"primaryOperation" {$exists true}}
                (when (ss/numeric? ts)
                  {:modified {$gte (Long/parseLong ts 10)}}))
        fields [:address :applicant :authority :closed :created :convertedToApplication :infoRequest :modified
                :municipality :opened :openInfoRequest :primaryOperation :secondaryOperations :organization
                :propertyId :permitSubtype :permitType :sent :started :state :submitted]
        raw-applications (mongo/select :applications query fields)
        applications-with-operations (map
                                       (fn [a] (assoc a :operations (conj (seq (:secondaryOperations a)) (:primaryOperation a))))
                                       raw-applications)
        applications (map
                       (fn [a] (update-in a [:operations] #(map (partial operation-mapper a) %)))
                       applications-with-operations)]
    (ok :applications applications)))

(defexport export-organizations
  {:user-roles #{:trusted-etl}}
  [_]
  (export :organizations {} [:name :scope]))
