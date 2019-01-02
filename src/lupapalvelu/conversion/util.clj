(ns lupapalvelu.conversion.util
  (:require [taoensso.timbre :refer [infof]]
            [clj-time.coerce :as c]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.backing-system.krysp.review-reader :as review-reader]
            [lupapalvelu.document.model :as doc-model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapiste.mongocheck.core :as mongocheck]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$in $ne $elemMatch $set]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]))

(def config
  {:resource-path (format "/Users/%s/Desktop/test-data" (System/getenv "USER"))})

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

(defn parse-rakennuspaikkatieto [kuntalupatunnus rakennuspaikkatieto]
  (let [data (:Rakennuspaikka rakennuspaikkatieto)
        {:keys [kerrosala kaavatilanne rakennusoikeusYhteensa]} data
        {:keys [kunta postinumero osoitenimi osoitenumero postitoimipaikannimi]} (:osoite data)
        kiinteisto (get-in data [:rakennuspaikanKiinteistotieto :RakennuspaikanKiinteisto])
        kaupunginosanumero (-> kuntalupatunnus destructure-permit-id :kauposa)]
    {:kaavatilanne kaavatilanne
     :hallintaperuste (:hallintaperuste kiinteisto)
     :kiinteisto {:kerrosala kerrosala
                  :rakennusoikeusYhteensa rakennusoikeusYhteensa
                  :kylanimi (get-in kiinteisto [:kiinteistotieto :Kiinteisto :kylanimi])
                  :kiinteistotunnus (get-in kiinteisto [:kiinteistotieto :Kiinteisto :kiinteistotunnus])}
     :osoite {:kunta kunta
              :postinumero postinumero
              :kaupunginosanumero kaupunginosanumero
              :osoitenimi (->> osoitenimi :teksti (ss/join #" / "))
              :osoitenumero osoitenumero
              :postitoimipaikannimi postitoimipaikannimi}}))

(defn rakennuspaikkatieto->rakennuspaikka-kuntagml-doc
  "Takes a :Rakennuspaikka element extracted from KuntaGML (via `building-reader/->rakennuspaikkatieto`),
  returns a document of type following the rakennuspaikka-kuntagml -schema."
  [kuntalupatunnus rakennuspaikkatieto]
  (let [data (parse-rakennuspaikkatieto kuntalupatunnus rakennuspaikkatieto)
        schema (schemas/get-schema 1 "rakennuspaikka-kuntagml")
        doc-datas (->> data
                       (doc-model/map2updates [])
                       (app/sanitize-document-datas schema))
        manual-schema-datas {"rakennuspaikka-kuntagml" doc-datas}]
    (app/make-document nil (now) manual-schema-datas schema)))

(defn kuntalupatunnus->description
  "Takes a kuntalupatunnus, returns the permit type in plain text ('12-124124-92-A' -> 'Uusi rakennus' etc.)"
  [kuntalupatunnus]
  (let [tyyppi (-> kuntalupatunnus destructure-permit-id :tyyppi)]
    (condp = tyyppi
      "A" "Uusi rakennus"
      "AJ" "Jatko"
      "AL" "Muutos"
      "AM" "Uusi rakennus, rakentamisen aikainen muutos"
      "B" "Lisärakennus"
      "BJ" "Jatko"
      "BL" "Muutos"
      "BM" "Lisärakennus, rakentamisen aikainen muutos"
      "C" "Toimenpide"
      "CJ" "Jatko"
      "CL" "Muutos"
      "CM" "Toimenpide"
      "D" "Muutostyö"
      "DJ" "Muutostyön jatkolupa"
      "DL" "Muutostyön muutoslupa"
      "DM" "Muutostyö, rakentamisen aikainen muutos"
      "E" "Ennakkolupa"
      "H" "Työmaaparakki"
      "HAL" "Hallintopakko"
      "HJ" "Jatko"
      "HUO" "Huomautus"
      "I" "Ilmoitus"
      "ILM" "Ilmitulo"
      "K" "Katumaan aitaaminen"
      "KJ" "Jatko"
      "KMK" "Kehotus"
      "KMP" "Katselmuspöytäkirja"
      "KNK" "Kaupunkikuvaneuvottelukunnan lausunto"
      "KR" "Kantarakennus"
      "KUN" "Kuntien toimenpiteettömät luvat"
      "LAS" "Laskelma"
      "LAU" "Pyydetty lausunto"
      "LKH" "Lausunto kunnallistekniikasta (hulevesisuunnitelmaa varten)"
      "LKL" "Lausunto kunnallistekniikasta (lohkomislupaa varten)"
      "LKP" "Lausunto kunnallistekniikasta (poikkeuslupaa varten)"
      "LKR" "Lausunto kunnallistekniikasta (rak.lupaa varten)"
      "LKT" "Lausunto kunnallistekniikasta (tp-lupaa varten)"
      "LOP" "Loppukatselmus"
      "M" "Maankaivu"
      "MAA" "Maa-aineslupa"
      "MAI" "Maisematyölupa"
      "MAJ" "Maisematyöluvan jatkolupa"
      "MAK" "Maksun palautus"
      "MAM" "Maisematyöluvan muutoslupa"
      "N" "Purkamisilmoitus"
      "OIK" "Oikaisuvaatimus"
      "P" "Purkamislupa"
      "PI" "Purkamisilmoitus"
      "PJ" "Jatko"
      "PL" "Lupaehdon muutos"
      "PM" "Purkamislupa, rakentamisen aikainen muutos"
      "POP" "Poikkeamispäätös"
      "PSR" "Poikkeamispäätös ja suunnittelutarveratkaisu"
      "RAM" "Rakennusaikainen muutos"
      "RAS" "Rasite"
      "RVA" "Rakennuttajavalvonta"
      "S" "Poikkeuslupa"
      "SEL" "Selityspyyntö"
      "SM" "Poikkeuslupa"
      "STR" "Suunnittelutarveratkaisu"
      "TJO" "Vastuullinen työnjohtaja"
      "US" "Uhkasakko"
      "VAK" "Vakuus"
      "Y" "Kokoontumishuone"
      "YHT" "Yhteisjärjestely"
      "YKJ" "YKEn lausunto jätevesistä haja-asutusalueilla"
      "YKL" "Ympäristökeskuksen lausunto"
      "YKM" "YKE:n maisematyölupa"
      "YMP" "YKEn maalämpöporakaivolausunto"
      "YVI" "YKEn vapautus liittymisestä viemäriin"
      "YVJ" "YKE:n vapautus liittymisestä vesijohtoon"
      "YVV" "YKEn vapautus liittymisestä vesijohtoon ja viemäriin"
      "Z" "Ei luvanvarainen hanke (Z-lausunto)"
      "tunnistamaton lupatyyppi")))

(defn normalize-permit-id
  "Viitelupien tunnukset on Factassa tallennettu 'tietokantaformaatissa', josta ne on tunnuksella
  hakemista varten muunnettava yleiseen formaattiin.
  Esimerkki: 12-0477-A 63 -> 63-0447-12-A"
  [id]
  (ss/join "-" ((juxt :kauposa :no :vuosi :tyyppi) (destructure-permit-id id))))

(defn rakennelmatieto->kaupunkikuvatoimenpide [raktieto]
  (let [data (doc-model/map2updates [] {:kayttotarkoitus nil
                                    :kokonaisala ""
                                    :kuvaus (get-in raktieto [:Rakennelma :kuvaus :kuvaus])
                                    :tunnus (get-in raktieto [:Rakennelma :tunnus :rakennusnro])
                                    :valtakunnallinenNumero ""})
        schema (schemas/get-schema 1 "kaupunkikuvatoimenpide")]
    (app/make-document "muu-rakentaminen"
                       (now)
                       {"kaupunkikuvatoimenpide" (app/sanitize-document-datas schema data)}
                       (schemas/get-schema 1 "kaupunkikuvatoimenpide"))))

(defn decapitalize
  "Convert the first character of the string to lowercase."
  [string]
  (apply str (ss/lower-case (first string)) (rest string)))

(defn add-description [{:keys [documents] :as app} xml]
  (let [kuntalupatunnus (krysp-reader/xml->kuntalupatunnus xml)
        kuvaus (building-reader/->asian-tiedot xml)
        kuvausteksti (str kuvaus
                          (format "\nLuvan tyyppi: %s"
                                  (decapitalize (kuntalupatunnus->description kuntalupatunnus))))]
    (assoc app :documents
           (map (fn [doc]
                  (cond
                    (and (re-find #"hankkeen-kuvaus" (get-in doc [:schema-info :name]))
                         (empty? (get-in doc [:data :kuvaus :value])))
                    (assoc-in doc [:data :kuvaus :value] kuvausteksti)
                    (and (re-find #"maisematyo" (get-in doc [:schema-info :name]))
                         (empty? (get-in doc [:data :kuvaus :value])))
                    (assoc-in doc [:data :kuvaus :value] kuvaus)
                    :else doc))
                documents))))

(defn remove-empty-rakennuspaikka
  "`app/make-application` creates an empty location document (using 'rakennuspaikka' schema)
  for the created application. Since we're using 'rakennuspaikka-kuntagml'-schema instead,
  this document is not used and can be weeded out here."
  [{:keys [documents] :as app}]
  (assoc app :documents
         (remove #(= "rakennuspaikka" (get-in % [:schema-info :name])) documents)))

(defn op-name->schema-name [op-name]
  (-> op-name operations/get-operation-metadata :schema))

(defn toimenpide->toimenpide-document [op-name toimenpide]
  (let [schema-name (op-name->schema-name op-name)
        schema (schemas/get-schema 1 schema-name)
        data (->> toimenpide
                  (doc-model/map2updates [])
                  (app/sanitize-document-datas schema))]
    (app/make-document op-name
                       (now)
                       {schema-name data}
                       (schemas/get-schema 1 schema-name))))

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
        history (for [{:keys [pvm tila]} (krysp-reader/get-sorted-tilamuutos-entries xml)
                      :when (translate-state tila)]
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

(defn missing-timestamp->dummy
  "Sometimes the history-array does not contains a timestamp for closing.
  In these cases, it's set to (now). If an optional second argument is passed,
  the value under that key is updated instead."
  ([app]
   (missing-timestamp->dummy app :closed))
  ([app k]
   (update app k (fn [ts] (if (nil? ts) (now) ts)))))

(defn add-timestamps [app history-array]
  (if (empty? history-array)
    (missing-timestamp->dummy app)
    (let [{:keys [ts state]} (first history-array)
          app-key (app-state/timestamp-key state)]
      (recur (if-not (nil? app-key)
               (assoc app app-key ts)
               app)
             (rest history-array)))))

;; Dev time helpers

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

(defn read-all-test-files
  ([] (read-all-test-files (:resource-path config)))
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
  "Takes a kuntalupatunnus and a 'toimenpide'-element from app-info, returns the operation type"
  ([kuntalupatunnus description]
   (let [suffix (-> kuntalupatunnus destructure-permit-id :tyyppi)]
     (condp = suffix
       "TJO" "tyonjohtajan-nimeaminen-v2"
       "P" "purkaminen"
       "PI" "purkaminen"
       "MAI" (cond
               (re-find #"kaatami|kaatoa" description) "puun-kaataminen"
               (re-find #"valmistele" description) "muu-tontti-tai-kort-muutos"
               (re-find #"kaivam|kaivu" description) "kaivuu"
               (re-find #"pysäk|liittym" description) "tontin-jarjestelymuutos"
               :else "muu-maisema-toimenpide")
       "konversio"))) ;; A minimal generic operation for this purpose.
                      ;; If a an application does not contain 'toimenpide'-element and is not P(I) or TJO, 'konversio it is'.
  ([kuntalupatunnus description toimenpide]
   (let [suffix (-> kuntalupatunnus destructure-permit-id :tyyppi)
        uusi? (contains? toimenpide :uusi)
        rakennustieto (get-in toimenpide [:rakennustieto :Rakennus :rakennuksenTiedot])
        {:keys [kayttotarkoitus rakennustunnus]} rakennustieto
        rakennuksen-selite (:rakennuksenSelite rakennustunnus)
        laajentaminen? (or (contains? toimenpide :laajentaminen)
                           (= rakennuksen-selite "Laajennus")
                           (= "B" suffix))
        rakennelman-kuvaus (get-in toimenpide [:rakennelmatieto :Rakennelma :kuvaus :kuvaus])
        rakennelman-selite (get-in toimenpide [:rakennelmatieto :Rakennelma :tunnus :rakennuksenSelite])]
    (cond
      (contains? #{"P" "PI"} suffix) "purkaminen"
      (and uusi?
           (= "Omakotitalo" rakennuksen-selite)) "pientalo"
      (and uusi?
           (contains? #{"Kerrostalo" "Asuinkerrostalo" "Rivitalo"} rakennuksen-selite)) "kerrostalo-rivitalo"
      (and uusi?
           (= "Talousrakennus" rakennuksen-selite)) "pientalo"
      (and uusi?
           (or (= "Katos" rakennelman-kuvaus)
               (= "Autokatos" rakennelman-selite))) "auto-katos"
      (and laajentaminen?
           (re-find #"toimisto" kayttotarkoitus)) "talousrakennus-laaj"
      (and laajentaminen?
           (re-find #"teollisuuden tuotantorak" kayttotarkoitus)) "teollisuusrakennus-laaj"
      (and laajentaminen?
           (or (re-find #"yhden asunnon talot" kayttotarkoitus)
               (= "omakotitalo" rakennuksen-selite))) "pientalo-laaj"
      (and laajentaminen?
           (or (re-find #"rivital|kerrostal" kayttotarkoitus)
               (= "omakotitalo" rakennuksen-selite))) "kerrostalo-rt-laaj"
      :else "aiemmalla-luvalla-hakeminen"))))

(defn add-vakuustieto!
  "This takes an XML of a VAK-type kuntaGML application, i.e. deposit.
  It then retrieves the kantalupa that the VAK-applications refers to
  and adds the info about the deposit to its verdict (poytakirja) element.
  This needs to be run for all the VAK-type applications the Vantaa-conversion,
  after all the applications have been converted."
  [xml]
  (let [kantalupa-kuntalupatunnus (-> xml krysp-reader/->viitelupatunnukset first normalize-permit-id) ;; The kuntalupatunnus the VAK-application points to.
        kantalupa-lupapiste-id (-> kantalupa-kuntalupatunnus app/get-lp-ids-by-kuntalupatunnus first) ;; The lupapiste-id of the aforementioned kantalupa.
        kuntalupatunnus-vak (krysp-reader/->kuntalupatunnus xml)
        {:keys [vakuudenLaji vakuudenmaara voimassaolopvm]} (krysp-reader/->vakuustieto xml)
        vakuus-string (format "Sisältää vakuuden (kuntalupatunnus: %s).\nVakuuden määrä: %s, vakuuden laji: %s, voimassaolopäivä: %s."
                              kuntalupatunnus-vak vakuudenmaara vakuudenLaji voimassaolopvm)
        {:keys [id paatokset]} (-> (mongo/by-id :applications kantalupa-lupapiste-id {:verdicts 1}) :verdicts first)
        old-paatos-text (get-in paatokset [0 :poytakirjat 0 :paatos])
        new-paatos-text (str (when-not (empty? old-paatos-text) "\n") vakuus-string)]
    (when paatokset
      (infof "Adding vakuustieto from %s -> %s (%s)." kuntalupatunnus-vak kantalupa-lupapiste-id kantalupa-kuntalupatunnus)
      (mongo/update-by-query :applications
                             {:_id kantalupa-lupapiste-id :verdicts {$elemMatch {:id id}}}
                             {$set {:verdicts.0.paatokset.0.poytakirjat.0.paatos new-paatos-text}}))))

(defn read-xml [kuntalupatunnus]
  (let [filename (str (:resource-path config) "/" kuntalupatunnus ".xml")]
    (krysp-fetch/get-local-application-xml-by-filename filename "R")))

(defn get-operations-debug [kuntalupatunnus]
  (let [xml (read-xml kuntalupatunnus)
        app-info (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
        {:keys [toimenpiteet]} app-info]
    (if-not (empty? toimenpiteet)
      (map (partial deduce-operation-type kuntalupatunnus) toimenpiteet)
      (deduce-operation-type kuntalupatunnus))))

(defn get-operation-types-for-testset
  "Returns a sequence for maps describing the deduced operation types
  for Krysp files in the test-set. Takes a kuntalupatunnus suffix as an
  optional argument. Calling the function with e.g. argument 'A' returns
  the operation types for A-type permits only."
  [& [suffix]]
  (let [rawdata (read-all-test-files)
        _ (println (str "Read all " (count rawdata) " files."))
        data (if suffix
               (filter #(= suffix (some->
                                   %
                                   krysp-reader/xml->kuntalupatunnus
                                   destructure-permit-id
                                   :tyyppi))
                       rawdata)
               rawdata)]
    (map (fn [xml]
            (let [kuntalupatunnus (krysp-reader/xml->kuntalupatunnus xml)
                  {:keys [toimenpiteet]} (krysp-reader/get-app-info-from-message xml kuntalupatunnus)]
               (assoc {}
                      :types (if toimenpiteet
                               (map (partial deduce-operation-type kuntalupatunnus) toimenpiteet)
                               (deduce-operation-type kuntalupatunnus))
                      :tunnus (krysp-reader/xml->kuntalupatunnus xml)))) data)))

(defn get-asian-kuvaus [kuntalupatunnus]
  (-> kuntalupatunnus get-xml-for-kuntalupatunnus building-reader/->asian-tiedot))

(defn run-checks []
  (mongocheck/execute-checks (mongo/get-db)))
