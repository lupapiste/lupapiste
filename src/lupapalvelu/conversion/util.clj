(ns lupapalvelu.conversion.util
  (:require [clj-time.coerce :as c]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [lupapalvelu.application :as app]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.backing-system.krysp.review-reader :as review-reader]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$in $ne]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]))

(def general-permit-id-regex
  "So-called 'general' format, e.g. 63-0447-12-A"
  #"\d{2}-\d{4}-\d{2}-[A-Z]{1,3}")

(def database-permit-id-regex
  "So-called 'database' format, e.g. 12-0477-A 63"
  #"\d{2}-\d{4}-[A-Z]{1,3} \d{2}")

(defn destructure-permit-id
  "Split a permit id into a map of parts. Works regardless of which of the two
  id-format is used. Returns nil if input is invalid."
  [id]
  (let [id-type (cond
                  (= id (re-find general-permit-id-regex id)) :general
                  (= id (re-find database-permit-id-regex id)) :database
                  :else :unknown)]
    (when-not (= :unknown id-type)
      (zipmap (if (= id-type :general)
                '(:kauposa :no :vuosi :tyyppi)
                '(:vuosi :no :tyyppi :kauposa))
              (ss/split id #"[- ]")))))

(defn normalize-permit-id
  "Viitelupien tunnukset on Factassa tallennettu 'tietokantaformaatissa', josta ne on tunnuksella
  hakemista varten muunnettava yleiseen formaattiin.
  Esimerkki: 12-0477-A 63 -> 63-0447-12-A"
  [id]
  (ss/join "-" ((juxt :kauposa :no :vuosi :tyyppi) (destructure-permit-id id))))

(defn rakennelmatieto->kaupunkikuvatoimenpide [raktieto]
  (let [data (model/map2updates [] {:kayttotarkoitus nil
                                    :kokonaisala ""
                                    :kuvaus (get-in raktieto [:Rakennelma :kuvaus :kuvaus])
                                    :tunnus (get-in raktieto [:Rakennelma :tunnus :rakennusnro])
                                    :valtakunnallinenNumero ""})]
    (app/make-document "muu-rakentaminen"
                       (now)
                       {"kaupunkikuvatoimenpide" data}
                       (schemas/get-schema 1 "kaupunkikuvatoimenpide"))))

(defn make-converted-application-id
  "An application id is created for the year found in the kuntalupatunnus, e.g.
  `LP-092-2013-00123`, not for the current year as in normal paper application imports."
  [kuntalupatunnus]
  (let [year (-> kuntalupatunnus destructure-permit-id :vuosi)
        fullyear (str (if (> 20 (Integer. year)) "20" "19") year)
        municipality "092" ;; Hardcoded since the whole procedure is for Vantaa
        sequence-name (str "applications-" municipality "-" fullyear)
        nextvalue (mongo/get-next-sequence-value sequence-name)
        counter (format (if (> 10000 nextvalue) "9%04d" "%05d") nextvalue)]
    (ss/join "-" (list "LP" "092" fullyear counter))))

(defn get-duplicate-ids
  "This takes a kuntalupatunnus and returns the LP ids of every application in the database
  that contains the same kuntalupatunnus and does not contain :facta-imported true."
  [kuntalupatunnus]
  (let [ids (app/get-lp-ids-by-kuntalupatunnus kuntalupatunnus)]
    (->> (mongo/select :applications
                       {:_id {$in ids} :facta-imported {$ne true}}
                       {:_id 1})
         (map :id))))

(defn get-id-listing
  "Produces a CSV list of converted applications, where LP id is matched to kuntalupatunnus.
  See PATE-152 for the rationale."
  [filename]
  (let [data (->> (mongo/select :applications ;; Data is a sequence of vectors like ["LP-092-2018-90047" "18-0030-13-A"].
                                {:facta-imported true}
                                {:_id 1 :verdicts.kuntalupatunnus 1})
                  (map (fn [item]
                         [(:id item) (get-in item [:verdicts 0 :kuntalupatunnus])])))]
    (with-open [writer (io/writer filename)]
      (csv/write-csv writer data))))

(defn translate-state [state]
  (condp = state
    "ei tiedossa" nil
    "rakennusty\u00f6t aloitettu" :constructionStarted
    "lopullinen loppukatselmus tehty" :closed
    "lupa hyv\u00e4ksytty" :verdictGiven
    "lupa k\u00e4sitelty, siirretty p\u00e4\u00e4tt\u00e4j\u00e4lle" :underReview
    "luvalla ei loppukatselmusehtoa, lupa valmis" :closed
    "rakennusty\u00f6t aloitettu" :constructionStarted
    "uusi lupa, ei k\u00e4sittelyss\u00e4" :submitted
    "vireill\u00e4" :submitted
    nil))

(defn xml->verdict-timestamp [xml]
  (let [date (krysp-reader/->verdict-date xml)]
    (if (string? date)
      (c/to-long date)
      date)))

(defn generate-history-array [xml]
  (let [verdict-given {:state :verdictGiven
                       :ts (xml->verdict-timestamp xml)
                       :user usr/batchrun-user-data}
        history (for [{:keys [pvm tila]} (krysp-reader/get-sorted-tilamuutos-entries xml)]
                  {:state (translate-state tila)
                   :ts pvm
                   :user usr/batchrun-user-data})
        history-array (some->> verdict-given (conj history) (sort-by :ts))]
    (if-not (krysp-reader/is-foreman-application? xml)
      history-array
      (map (fn [e]
             (if (= :verdictGiven (:state e))
               (assoc e :state :foremanVerdictGiven)
               e))
           history-array))))

(defn read-all-test-files
  ([] (read-all-test-files "/Users/tuomo.virolainen/Desktop/test-data"))
  ([path]
   (let [files (->> (clojure.java.io/file path)
                    file-seq
                    (filter #(.isFile %))
                    (map #(.getAbsolutePath %)))]
     (map #(try
             (krysp-fetch/get-local-application-xml-by-filename % "R")
             (catch Exception e
               (println (.getMessage e)))) files))))

(defn list-all-states
  "List all unique states found in the test set."
  []
  (let [files (read-all-test-files)]
    (set (mapcat (fn [f]
                   (let [data (try
                                (krysp-reader/get-sorted-tilamuutos-entries f)
                                (catch Exception e
                                  (println (.getMessage e))))]
                     (map :tila data))) files))))

(defn get-building-type [xml]
  (let [reviews (review-reader/xml->reviews xml)
        katselmuksenRakennustieto (:katselmuksenRakennustieto (first reviews))]
    (as-> katselmuksenRakennustieto x
      (filter #(= "1" (get-in % [:KatselmuksenRakennus :jarjestysnumero])) x)
      (first x)
      (get-in x [:KatselmuksenRakennus :rakennuksenSelite]))))

(defn get-building-types []
  (frequencies (map get-building-type (read-all-test-files))))

(defn get-asian-kuvaukset []
  (->> (read-all-test-files)
       (map building-reader/->asian-tiedot)
       (filter string?)))

(defn get-xml-for-kuntalupatunnus [kuntalupatunnus]
  (->> (read-all-test-files)
       (filter #(= kuntalupatunnus (krysp-reader/xml->kuntalupatunnus %)))
       first))

(defn deduce-operation-type
  "Figure out the right primaryOperation for the application."
  [xml]
  (let [kuntalupatunnus (krysp-reader/xml->kuntalupatunnus xml)
        suffix (-> kuntalupatunnus destructure-permit-id :tyyppi)
        btype (get-building-type xml)
        asiantiedot (ss/lower-case (building-reader/->asian-tiedot xml))
        {:keys [description usage]} (-> xml building-reader/->buildings-summary first)]
    (cond
      (= "TJO" suffix) "tyonjohtajan-nimeaminen-v2"
      (contains? #{"P" "PI"} suffix) "purkaminen"
      (and (= "A" suffix)
           (contains? #{"Asuinkerrostalo" "Kerrostalo"} btype)) "kerrostalo-rivitalo"
      (and (= "A" suffix)
           (contains? #{"Omakotitalo"} btype)) "pientalo"
      (and (= "B" suffix)
           (or (contains? #{"Omakotitalo"} btype)
               (re-find #"yhden asunnon talo" usage))) "pientalo-laaj"
      (and (= "B" suffix)
           (re-find #"kerrosta|rivita" usage)) "kerrostalo-rt-laaj"
      (and (= "B" suffix)
           (ss/contains? usage "toimisto")) "muu-rakennus-laaj"
      (and (= "B" suffix)
           (ss/contains? usage "teollisuu")) "teollisuusrakennus-laaj"
      (and (= "C" suffix)
           (re-find #"mainos" asiantiedot)) "mainoslaite"
      (and (= "C" suffix)
           (re-find #"maalämpökaivo" asiantiedot)) "maalampokaivo"
      (and (= "C" suffix)
           (re-find #"pysäköintialue" asiantiedot)) "muu-rakentaminen"
      (and (= "D" suffix)
           (re-find #"yhdistäminen" asiantiedot)) "jakaminen-tai-yhdistaminen"
      (and (= "D" suffix)
           (re-find #"johtojen uusiminen|lvi|putki" asiantiedot)) "linjasaneeraus"
      (and (= "D" suffix)
           (re-find #"käyttötarkoituksen muutos|muuttaminen" asiantiedot)) "kayttotark-muutos"
      (and (= "D" suffix)
           (re-find #"muuttaminen .*huoneeksi|sis.* muutoks|kalustemuut" asiantiedot)) "sisatila-muutos"
      :else "aiemmalla-luvalla-hakeminen")))

(defn get-operation-types-for-testset
  "Returns a sequence for maps describing the deduced operation types
  for Krysp files in the test-set. Takes a kuntalupatunnus suffix as an
  optional argument. Calling the function with e.g. argument 'A' returns
  the operation types for A-type permits only."
  [& [suffix]]
  (let [rawdata (read-all-test-files)
        data (if suffix
               (filter #(= suffix (some->
                                   %
                                   krysp-reader/xml->kuntalupatunnus
                                   destructure-permit-id
                                   :tyyppi))
                       rawdata)
               rawdata)]
    (map #(assoc {}
                 :type (some-> % deduce-operation-type)
                 :tunnus (krysp-reader/xml->kuntalupatunnus %)) data)))

(defn get-asian-kuvaus [kuntalupatunnus]
  (-> kuntalupatunnus
      get-xml-for-kuntalupatunnus
      building-reader/->asian-tiedot))
