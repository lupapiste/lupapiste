(ns lupapalvelu.conversion.util
  (:require [clj-time.coerce :as c]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [lupapalvelu.application :as app]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
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

(defn generate-history-array
  [xml]
  (let [verdict-given {:state :verdictGiven
                       :ts (krysp-reader/->verdict-date xml); (subs 0 10) c/to-long
                       :user usr/batchrun-user-data}
        history (for [{:keys [pvm tila]} (krysp-reader/get-sorted-tilamuutos-entries xml)]
                  {:state (translate-state tila)
                   :ts pvm
                   :user usr/batchrun-user-data})]
    (->> verdict-given
         (conj history)
         (sort-by :ts))))

(def path
  "/Users/tuomo.virolainen/Desktop/test-data/")

(defn list-all-states
  "List all unique states found in the test set."
  [path]
  (let [files (->> (clojure.java.io/file path)
                   file-seq
                   (filter #(.isFile %))
                   (map #(.getAbsolutePath %)))]
    (set (mapcat (fn [f]
                   (let [data (try
                                (krysp-reader/get-sorted-tilamuutos-entries (krysp-fetch/get-local-application-xml-by-filename f "R"))
                                (catch Exception e
                                  (println (.getMessage e))))]
                     (map :tila data))) files))))
