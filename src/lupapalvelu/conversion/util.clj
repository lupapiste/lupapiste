(ns lupapalvelu.conversion.util
  (:require [clojure.set :refer [intersection]]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.document.model :as doc-model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.state-machine :refer [application-state-seq valid-state? state-graph]]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$exists $in $elemMatch $set]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [infof warnf]]))

(def permit-id-formats
  "The order of the parts and the separators between them
  in the permit id format keyed to the regex that matches with it"
  {;; So-called 'general' format, e.g. 63-0447-12-A
   #"^\d+-\d{4}-\d{2}-[A-Z]{1,3}$"      {:parts      [:kauposa :no :vuosi :tyyppi]
                                         :separators ["-" "-" "-"]}
   ;; So-called 'database' format, e.g. 12-0477-A 63
   #"^\d{2}-\d{4}-[A-Z]{1,3}\s+\d+$"    {:parts      [:vuosi :no :tyyppi :kauposa]
                                         :separators ["-" "-" " "]}
   ;; Like 'database' format, but kaupunginosanumero at front e.g. 164 10-3001-R
   #"^\d+\s+\d{2}-\d{4}-[A-Z0-9]{1,3}$" {:parts      [:kauposa :vuosi :no :tyyppi]
                                         :separators [" " "-" "-"]}
   ;; So-called 'general' format without kaupunginosanumero e.g. 0001-00-C
   #"^\d{4}-\d{2}-[A-Z]{1,3}$"          {:parts      [:no :vuosi :tyyppi]
                                         :separators ["-" "-"]}
   ;; So-called 'database' format without kaupunginosanumero e.g. 00-1234-B
   #"^\d{2}-\d{4}-[A-Z]{1,3}$"          {:parts      [:vuosi :no :tyyppi]
                                         :separators ["-" "-"]}})

(sc/defn ^:always-validate destructure-permit-id :- {:vuosi     (sc/pred #(> % 1900))
                                                     sc/Keyword sc/Any}
  "Split a permit id into a map of parts. Works regardless of which of the
  id-formats is used. Returns nil if input is invalid."
  [id]
  (when (ss/not-blank? id)
    (when-let [parts (->> permit-id-formats
                          (some (fn [[regex {:keys [parts]}]]
                                  (when (re-matches regex id)
                                    parts))))]
      (update (zipmap parts (ss/split id #"[-\s]+"))
              :vuosi
              (fn [yy]
                (let [current-year   (.getYear (date/now))
                      two-digit-year (rem current-year 100)
                      century        (- current-year two-digit-year)
                      yy             (util/->int yy)]
                  (if (> yy two-digit-year)
                    (+ (- century 100) yy)
                    (+ century yy))))))))

(defn determine-terminal-state [{:keys [operation-name] :as application}]
  (if (= "tyonjohtajan-nimeaminen-v2" operation-name)
    :foremanVerdictGiven
    (-> application
        application-state-seq
        last)))

(defn determine-app-state
  "This function searches the last state of the application. The possible
  application states are iterated from the end towards the beginning. The
  return value is either the first match found or the default terminal state,
  if the match is not found (e.g., history is empty). The rationale for this
  is that multiple history entries can have the same timestamp (a date in
  the KuntaGML message) so just sorting the entries by timestamp is not
  foolproof enough."
  [{:keys [history] :as application}]
  (let [last-states (->> history (sort-by :ts) (partition-by :ts) last)]
    (if (= 1 (count last-states))
      (:state (first last-states))
      (loop [states (application-state-seq application)]
        (if (empty? states)
          (determine-terminal-state application)
          (or ((set (map :state last-states)) (last states))
              (recur (drop-last states))))))))


(defn set-to-terminal-state [{:keys [history] :as application}]
  (let [terminal-state    (determine-terminal-state application)
        final-state-entry {:state (name terminal-state)
                           :ts    (or (some->> application :history
                                               (keep :ts) seq
                                               (apply max))
                                      (now))
                           :user  usr/batchrun-user-data}]
    (-> application
        (assoc :history (->> (conj history final-state-entry)
                             (sort-by :ts)
                             distinct))
        (assoc :state terminal-state))))

(defn set-to-right-state [application]
  (assoc application :state (determine-app-state application)))

(defn final-review-done? [tasks]
  (->> tasks
       (filter #(= "loppukatselmus" (:taskname %)))
       first
       :state
       (= "sent")))

(def kiinteistotunnus-regex
  #"^\d{3}-\d{3}-\d{4}-\d{4}$")

(def kiinteistotunnus-with-maaraala-regex
  #"^\d{3}-\d{3}-\d{4}-\d{4}-M-\d{4}$")

(def maaraalatunnus-regex
  #"^[A-Z]{1}\d{4}$")

(defn normalize-ktunnus
  "Hyphenate and parse the kiinteistötunnus to the standard format: 09201202020015 -> 092-012-0202-0015"
  ([kiinteistotunnus]
   (let [hd (partition 3 (take 6 kiinteistotunnus))
         tl (partition 4 (drop 6 kiinteistotunnus))
         normalized (->> (concat hd tl)
                         (map ss/join)
                         (ss/join "-"))]
     (or (re-find kiinteistotunnus-regex normalized)
         kiinteistotunnus)))
  ([kiinteistotunnus maaraalatunnus]
   (if-not (re-find maaraalatunnus-regex (str maaraalatunnus))
     (normalize-ktunnus kiinteistotunnus)
     (let [normalized (ss/join "-" (conj [] (normalize-ktunnus kiinteistotunnus)
                                         (first maaraalatunnus)
                                         (subs maaraalatunnus 1)))]
       (or (re-find kiinteistotunnus-with-maaraala-regex normalized)
           kiinteistotunnus)))))

(defn parse-rakennuspaikkatieto [kuntalupatunnus rakennuspaikkatieto]
  (let [data (:Rakennuspaikka rakennuspaikkatieto)
        {:keys [kerrosala kaavatilanne rakennusoikeusYhteensa]} data
        {:keys [kunta postinumero osoitenimi osoitenumero postitoimipaikannimi]} (:osoite data)
        kiinteisto (get-in data [:rakennuspaikanKiinteistotieto :RakennuspaikanKiinteisto])
        {:keys [kiinteistotunnus kylanimi maaraAlaTunnus]} (get-in kiinteisto [:kiinteistotieto :Kiinteisto])
        kaupunginosanumero (-> kuntalupatunnus destructure-permit-id :kauposa)]
    {:kaavatilanne kaavatilanne
     :hallintaperuste (:hallintaperuste kiinteisto)
     :kiinteisto {:kerrosala kerrosala
                  :rakennusoikeusYhteensa rakennusoikeusYhteensa
                  :kylanimi kylanimi
                  :kiinteistotunnus (normalize-ktunnus kiinteistotunnus maaraAlaTunnus)}
     :osoite {:kunta kunta
              :postinumero postinumero
              :kaupunginosanumero kaupunginosanumero
              :osoitenimi (->> osoitenimi :teksti vector flatten (ss/join #" / "))
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

(defn get-elements-from-document-datas
  "Accepts an update sequence (a seq of vectors), returns a sequence of the values of the provided key."
  [dkey doc-datas]
  (for [[[k] v] doc-datas
        :when (= dkey k)]
    v))

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

(defn get-permit-id-variations
  "Since some systems have the kuntalupatunnus in alternate forms, this function takes one kuntalupatunnus
  and returns the variations of it in different formats."
  [kuntalupatunnus]
  (let [destructured-id (-> (destructure-permit-id kuntalupatunnus)
                            (update :vuosi #(when % (-> % str (subs 2))))) ; 1987 -> "87"
        make-candidate  (fn [{:keys [parts separators]}]
                          (->> destructured-id
                               ((apply juxt parts))
                               (map vector (cons "" separators)) ; "" separator before the first part
                               ;; Put separators only between parts that exist and in the correct order
                               (reduce (fn [acc [separator part]]
                                         (cond
                                           (ss/blank? acc)  part
                                           (ss/blank? part) acc
                                           :else            (str acc separator part)))
                                       "")))]
    (->> (vals permit-id-formats)
         (map make-candidate)
         (cons kuntalupatunnus)
         distinct)))

(def get-lp-id-for-permit-id
  "Checks first the conversion database and then the application database proper with the different formats
  of the kuntalupatunnus"
  (memoize
    (fn [organization-id kuntalupatunnus]
      (try
        ;; Make variations into regex patterns so even if the source is missing the kaupunginosa
        ;; or there's whitespace etc. the match will still be made
        (let [variations (->> (get-permit-id-variations kuntalupatunnus)
                              (map re-pattern))]
          (or (some #(-> (mongo/select-one :conversion
                                           {:organization organization-id
                                            :LP-id        {$exists true}
                                            :backend-id   %}
                                           [:LP-id])
                         :LP-id)
                    variations)
              (some #(->> % (app/get-lp-ids-by-kuntalupatunnus organization-id) first)
                    variations)))
        (catch Exception e
          (warnf "Exception when fetching LP-id for conversion kuntalupatunnus %s %s"
                 kuntalupatunnus
                 (.getMessage e)))))))

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

(defn is-empty-party-document? [{:keys [schema-info] :as doc}]
  (if (= "party" (some-> schema-info :type name))
    (->> doc
         (tree-seq map? vals)
         (keep :value)
         (filter string?)
         (remove (partial contains? #{"" "taysiaikainen" "FIN" "henkilo"}))
         empty?)
    false))

(defn remove-empty-party-documents [{:keys [documents] :as app}]
  (assoc app :documents (remove is-empty-party-document? documents)))

(defn decapitalize
  "Convert the first character of the string to lowercase."
  [string]
  (apply str (ss/lower-case (first string)) (rest string)))

(defn add-description-and-deviation-info [{:keys [documents] :as app} xml document-datas]
  (let [kuntalupatunnus (krysp-reader/xml->kuntalupatunnus xml)
        kuvaus (building-reader/->asian-tiedot xml)
        poikkeamat (->> document-datas
                        (map (partial get-elements-from-document-datas "poikkeamat"))
                        flatten
                        set
                        (clojure.string/join #"\n"))
        kuvausteksti (str (when kuvaus (str kuvaus "\n"))
                          (format "Luvan tyyppi: %s"
                                  (decapitalize (kuntalupatunnus->description kuntalupatunnus))))]
    (assoc app :documents
           (map (fn [doc]
                  (cond
                    (and (= "hankkeen-kuvaus" (get-in doc [:schema-info :name]))
                         (empty? (get-in doc [:data :kuvaus :value])))
                    (-> doc
                      (assoc-in [:data :kuvaus :value] kuvausteksti)
                      (assoc-in [:data :poikkeamat :value] poikkeamat))
                    (and (#{"hankkeen-kuvaus-minimum" "jatkoaika-hankkeen-kuvaus"} (get-in doc [:schema-info :name]))
                         (empty? (get-in doc [:data :kuvaus :value])))
                    (-> doc
                      (assoc-in [:data :kuvaus :value] kuvausteksti))
                    (and (re-find #"maisematyo" (get-in doc [:schema-info :name]))
                         (empty? (get-in doc [:data :kuvaus :value])))
                    (assoc-in doc [:data :kuvaus :value] kuvausteksti)
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

(defn make-converted-application-id
  "An application id is created for the year found in the kuntalupatunnus, e.g.
  `LP-092-2013-00123`, not for the current year as in normal paper application imports."
  [municipality kuntalupatunnus]
  (let [year          (-> kuntalupatunnus destructure-permit-id :vuosi)
        sequence-name (str "applications-" municipality "-" year)
        nextvalue     (mongo/get-next-sequence-value sequence-name)
        counter       (format (if (env/feature? :prefixed-id) "9%04d" "%05d") nextvalue)
        app-id        (ss/join "-" ["LP" municipality year counter])]
    (if (mongo/any? :applications {:_id app-id}) ;; Double-check that there will be no clashes
      (recur municipality kuntalupatunnus)
      app-id)))

(defn translate-state [state application]
  (let [valid-states (set (keys (state-graph application)))
        select-valid #(->> % (intersection valid-states) first)]
    (condp = state
      "ei tiedossa" nil
      "rakennustyöt aloitettu" :constructionStarted
      "lopullinen loppukatselmus tehty" (select-valid #{:closed :ready})
      "lupa rauennut" :canceled
      "lupa hyväksytty" (select-valid #{:verdictGiven :foremanVerdictGiven :ready})
      "lupa käsitelty, siirretty päättäjälle" (select-valid #{:underReview :sent})
      "luvalla ei loppukatselmusehtoa, lupa valmis" (select-valid #{:closed :ready})
      "rakennustyöt aloitettu" :constructionStarted
      "uusi lupa, ei käsittelyssä" :submitted
      "vireillä" :submitted
      nil)))

(defn remove-illegal-states
  "Removes illegal states from history-array."
  [application]
  (update application :history (partial filter #(valid-state? application (:state %)))))

(defn generate-history-array [xml application]
  (let [history (for [{:keys [pvm tila]} (krysp-reader/get-sorted-tilamuutos-entries xml)
                      :let               [state (translate-state tila application)]
                      :when              state]
                  {:state state
                   :ts    (or pvm 0)
                   :user  usr/batchrun-user-data})]
    (some->> history
             (map (fn [{:keys [ts] :as entry}] ;; Set invalid timestamps to the beginning of the Unix epoch.
                    (if-not (pos? ts)
                      (assoc entry :ts 0)
                      entry)))
             (sort-by :ts))))

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
  [kuntalupatunnus organization-id]
  (let [ids (app/get-lp-ids-by-kuntalupatunnus organization-id kuntalupatunnus)]
    (->> (mongo/select :applications
                       {:_id {$in ids} :organization organization-id}
                       {:_id 1})
         (map :id))))

(defn deduce-operation-type
  "Takes a kuntalupatunnus and a 'toimenpide'-element from app-info, returns the operation type"
  ([kuntalupatunnus description]
   (let [suffix (-> kuntalupatunnus destructure-permit-id :tyyppi)]
     (cond
       (= suffix "TJO") "tyonjohtajan-nimeaminen-v2"
       (#{"P" "PI"} suffix) "purkaminen"
       (->> suffix last (= \J)) "raktyo-aloit-loppuunsaat"
       (= suffix "MAI") (cond
                          (re-find #"kaatami|kaatoa" description) "puun-kaataminen"
                          (re-find #"valmistele" description) "muu-tontti-tai-kort-muutos"
                          (re-find #"kaivam|kaivu" description) "kaivuu"
                          (re-find #"pysäk|liittym" description) "tontin-jarjestelymuutos"
                          :else "muu-maisema-toimenpide")
       :else "konversio"))) ;; A minimal generic operation for this purpose.
  ([kuntalupatunnus description toimenpide]
   (let [suffix             (-> kuntalupatunnus destructure-permit-id :tyyppi)
         uusi?              (contains? toimenpide :uusi)
         rakennustieto      (get-in toimenpide [:rakennustieto :Rakennus :rakennuksenTiedot])
         {:keys
          [kayttotarkoitus
           rakennustunnus]} rakennustieto
         kayttotarkoitus    (str kayttotarkoitus)
         rakennuksen-selite (str (:rakennuksenSelite rakennustunnus)) ;; Guard against nil
         muuttaminen?       (or (= "D" suffix)
                                (= \L (last suffix)))
         laajentaminen?     (or (contains? toimenpide :laajentaminen)
                                (= rakennuksen-selite "Laajennus")
                                (= "B" suffix))
         rakennelman-kuvaus (str (get-in toimenpide [:rakennelmatieto :Rakennelma :kuvaus :kuvaus]))
         rakennelman-selite (str (get-in toimenpide [:rakennelmatieto :Rakennelma :tunnus :rakennuksenSelite]))]
     (cond
       (contains? #{"P" "PI"} suffix) "purkaminen"
       (and uusi?
            (or (re-find #"(?i)omakotitalo|paritalo" rakennuksen-selite)
                (re-find #"011 yhden asunnon talot|kahden asunnon" kayttotarkoitus))) "pientalo"
       (and uusi?
            (re-find #"(?i)talousrakennuk|sauna" kayttotarkoitus)) "varasto-tms"
       (and uusi?
            (or (contains? #{"Kerrostalo" "Asuinkerrostalo" "Rivitalo"} rakennuksen-selite)
                (re-find #"(?i)rivitalo|kerrostalo|luhtitalo" kayttotarkoitus))) "kerrostalo-rivitalo"
       (and uusi?
            (= "Talousrakennus" rakennuksen-selite)) "pientalo"
       (and uusi?
            (re-find #"(?i)vapaa-ajan" kayttotarkoitus)) "vapaa-ajan-asuinrakennus"
       (and uusi?
            (or (= "Katos" rakennelman-kuvaus)
                (= "Autokatos" rakennelman-selite))) "auto-katos"
       (and laajentaminen?
            (re-find #"(?i)toimisto|talousr" kayttotarkoitus)) "talousrakennus-laaj"
       (and laajentaminen?
            (re-find #"(?i)teollisuuden tuotantorak" kayttotarkoitus)) "teollisuusrakennus-laaj"
       (and laajentaminen?
            (or (re-find #"(?i)yhden asunnon talot" kayttotarkoitus)
                (= "Omakotitalo" rakennuksen-selite))) "pientalo-laaj"
       (and laajentaminen?
            (or (re-find #"(?i)rivital|kerrostal" kayttotarkoitus)
                (#{"Kerrostalo" "Rivitalo"} rakennuksen-selite))) "kerrostalo-rt-laaj"
       (and laajentaminen?
            (re-find #"(?i)vapaa-ajan" kayttotarkoitus)) "vapaa-ajan-rakennus-laaj"
       (and muuttaminen? (re-find #"(?i)ulko|julkisivu" description)) "julkisivu-muutos"
       (and muuttaminen? (re-find #"(?i)huoneeksi|asuin" description)) "sisatila-muutos"
       uusi? "muu-uusi-rakentaminen"
       laajentaminen? "muu-rakennus-laaj"
       :else "konversio"))))

(defn get-kuntalupatunnus-for-converted [lp-id]
  (:backend-id (mongo/select-one :conversion {:LP-id lp-id} [:backend-id])))

(defn vakuustieto? [kuntalupatunnus]
  (-> kuntalupatunnus destructure-permit-id :tyyppi (= "VAK")))

(defn add-vakuustieto!
  "This takes an XML of a VAK-type kuntaGML application, i.e. deposit.
  It then retrieves the kantalupa that the VAK-applications refers to
  and adds the info about the deposit to its verdict (poytakirja) element.
  This needs to be run for all the VAK-type applications the Vantaa-conversion,
  after all the applications have been converted."
  [xml]
  (let [kantalupa-kuntalupatunnus (-> xml krysp-reader/->viitelupatunnukset first) ;; The kuntalupatunnus the VAK-application points to.
        kantalupa-lupapiste-id (get-lp-id-for-permit-id)
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
