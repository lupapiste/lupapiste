(ns lupapalvelu.conversion.util
  (:require [camel-snake-kebab.core :refer [->kebab-case]]
            [lupapalvelu.application :as app]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.backing-system.krysp.common-reader :refer [get-tunnus-xml-path]]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.db :as db]
            [lupapalvelu.document.model :as doc-model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.common-reader :refer [strip-xml-namespaces] :as cr]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.property :as prop]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]))

(defn ->double
  "Converts `arg` into double. Supports both dot and comma decimal separators. Does not
  throw, returns nil for bad data."
  [arg]
  (cond-> arg
    (string? arg) (-> ss/trim (ss/replace "," "."))
    true          (util/->double nil)))

(defn duplicate-candidate-ids
  "Applications that might be duplicates for the given `kuntalupatunnus` within the
  organization `organization-id`. Digitized (ARK) and previous permit applications are not
  considered duplicates."
  ([organization-id kuntalupatunnus original-id]
   (db/application-ids-by-kuntalupatunnus organization-id
                                          kuntalupatunnus
                                          (cond-> {:permitType            {$ne :ARK}
                                                   :primaryOperation.name {$ne :aiemmalla-luvalla-hakeminen}}
                                            original-id (assoc :_id {$ne original-id}))))
  ([organization-id kuntalupatunnus]
   (duplicate-candidate-ids organization-id kuntalupatunnus nil)))

(defn find-xml-ids
  "Returns `:backend-id`, `:application-id` map for the given `xml-file-or-filename`. Either
  one can be missing and for unexpected errors an `:error` map is returned. If
  `permit-type` is nil, it is determined based on xml contents."
  ([permit-type xml-file-or-filename]
   (try
     (let [xml         (-> (slurp xml-file-or-filename)
                           (xml/parse-string "utf8")
                           strip-xml-namespaces)
           found?      #(xml/select1 xml [%])
           permit-type (cond
                         permit-type                                           permit-type
                         (found? :RakennusvalvontaAsia)                        "R"
                         (some found? [:Poikkeamisasia :Suunnittelutarveasia]) "P"
                         :else
                         (throw (ex-info "No supported permit type found." {})))]
       (util/strip-nils
         {:backend-id     (some->> (get-tunnus-xml-path permit-type :kuntalupatunnus)
                                   (xml/get-text xml))
          :application-id (some->> (get-tunnus-xml-path permit-type :application-id)
                                   (xml/get-text xml))}))
     (catch Exception e
       {:error (ex-message e)})))
  ([xml-file-or-filename]
   (find-xml-ids nil xml-file-or-filename)))

(defn valid-permit-id-or-nil
  "Returns `permit-id` if it can be successfully parsed with `conv-sc/destructure-permit-id`,
  otherwise nil."
  [permit-id]
  (try
    (when (conv-sc/destructure-permit-id permit-id)
      permit-id)
    (catch Exception _ nil)))

(def kiinteistotunnus-regex
  #"^\d{3}-\d{3}-\d{4}-\d{4}$")

(def kiinteistotunnus-with-maaraala-regex
  #"^\d{3}-\d{3}-\d{4}-\d{4}-M-\d{4}$")

(def maaraalatunnus-regex
  #"^[A-Z]{1}\d{4}$")

(defn- parse-rakennuspaikkatieto [kuntalupatunnus rakennuspaikkatieto]
  (let [data                           (:Rakennuspaikka rakennuspaikkatieto)
        {:keys [kerrosala kaavatilanne rakennusoikeusYhteensa
                rantaKytkin]}          data
        {:keys [kunta postinumero osoitenimi osoitenumero
                postitoimipaikannimi]} (:osoite data)
        kiinteisto                     (get-in data [:rakennuspaikanKiinteistotieto :RakennuspaikanKiinteisto])
        {:keys [kiinteistotunnus kylanimi tilannimi
                maaraAlaTunnus]}       (get-in kiinteisto [:kiinteistotieto :Kiinteisto])
        kaupunginosanumero             (-> kuntalupatunnus conv-sc/destructure-permit-id :kauposa)]
    {:kaavatilanne    kaavatilanne
     :hallintaperuste (:hallintaperuste kiinteisto)
     :kiinteisto      {:kerrosala              kerrosala
                       :rakennusoikeusYhteensa rakennusoikeusYhteensa
                       :kylanimi               kylanimi
                       :tilanNimi              tilannimi
                       :kiinteistotunnus       (prop/to-human-readable-property-id kiinteistotunnus)
                       :maaraalaTunnus         maaraAlaTunnus
                       :rantaKytkin            (= rantaKytkin "true")}
     :osoite          {:kunta                kunta
                       :postinumero          postinumero
                       :kaupunginosanumero   kaupunginosanumero
                       :osoitenimi           (->> osoitenimi :teksti vector flatten (ss/join #" / "))
                       :osoitenumero         osoitenumero
                       :postitoimipaikannimi postitoimipaikannimi}}))

(defn rakennuspaikkatieto->rakennuspaikka-kuntagml-doc
  "Takes a :Rakennuspaikka element extracted from KuntaGML (via `building-reader/->rakennuspaikkatieto`),
  returns a document of type following the rakennuspaikka-kuntagml -schema."
  [kuntalupatunnus rakennuspaikkatieto]
  (let [data                (parse-rakennuspaikkatieto kuntalupatunnus rakennuspaikkatieto)
        schema              (schemas/get-schema 1 "rakennuspaikka-kuntagml")
        doc-datas           (->> data
                                 (doc-model/map2updates [])
                                 (app/sanitize-document-datas schema))
        manual-schema-datas {"rakennuspaikka-kuntagml" doc-datas}]
    (app/make-document {:created             (now)
                        :manual-schema-datas manual-schema-datas
                        :schema              schema})))

(defn get-elements-from-document-datas
  "Accepts an update sequence (a seq of vectors), returns a sequence of the values of the provided key."
  [dkey doc-datas]
  (for [[[k] v] doc-datas
        :when   (= dkey k)]
    v))

(defn kuntalupatunnus->description
  "Takes a kuntalupatunnus, returns the permit type in plain text ('12-124124-92-A' -> 'Uusi rakennus' etc.)"
  [kuntalupatunnus]
  (case (some-> kuntalupatunnus conv-sc/destructure-permit-id :tyyppi)
    "A"   "Uusi rakennus"
    "AJ"  "Jatko"
    "AL"  "Muutos"
    "AM"  "Uusi rakennus, rakentamisen aikainen muutos"
    "B"   "Lisärakennus"
    "BJ"  "Jatko"
    "BL"  "Muutos"
    "BM"  "Lisärakennus, rakentamisen aikainen muutos"
    "C"   "Toimenpide"
    "CJ"  "Jatko"
    "CL"  "Muutos"
    "CM"  "Toimenpide"
    "D"   "Muutostyö"
    "DJ"  "Muutostyön jatkolupa"
    "DL"  "Muutostyön muutoslupa"
    "DM"  "Muutostyö, rakentamisen aikainen muutos"
    "E"   "Ennakkolupa"
    "H"   "Työmaaparakki"
    "HAL" "Hallintopakko"
    "HJ"  "Jatko"
    "HUO" "Huomautus"
    "I"   "Ilmoitus"
    "ILM" "Ilmitulo"
    "K"   "Katumaan aitaaminen"
    "KJ"  "Jatko"
    "KMK" "Kehotus"
    "KMP" "Katselmuspöytäkirja"
    "KNK" "Kaupunkikuvaneuvottelukunnan lausunto"
    "KR"  "Kantarakennus"
    "KUN" "Kuntien toimenpiteettömät luvat"
    "LAS" "Laskelma"
    "LAU" "Pyydetty lausunto"
    "LKH" "Lausunto kunnallistekniikasta (hulevesisuunnitelmaa varten)"
    "LKL" "Lausunto kunnallistekniikasta (lohkomislupaa varten)"
    "LKP" "Lausunto kunnallistekniikasta (poikkeuslupaa varten)"
    "LKR" "Lausunto kunnallistekniikasta (rak.lupaa varten)"
    "LKT" "Lausunto kunnallistekniikasta (tp-lupaa varten)"
    "LOP" "Loppukatselmus"
    "M"   "Maankaivu"
    "MAA" "Maa-aineslupa"
    "MAI" "Maisematyölupa"
    "MAJ" "Maisematyöluvan jatkolupa"
    "MAK" "Maksun palautus"
    "MAM" "Maisematyöluvan muutoslupa"
    "N"   "Purkamisilmoitus"
    "OIK" "Oikaisuvaatimus"
    "P"   "Purkamislupa"
    "PI"  "Purkamisilmoitus"
    "PJ"  "Jatko"
    "PL"  "Lupaehdon muutos"
    "PM"  "Purkamislupa, rakentamisen aikainen muutos"
    "POP" "Poikkeamispäätös"
    "PSR" "Poikkeamispäätös ja suunnittelutarveratkaisu"
    "RAM" "Rakennusaikainen muutos"
    "RAS" "Rasite"
    "RVA" "Rakennuttajavalvonta"
    "S"   "Poikkeuslupa"
    "SEL" "Selityspyyntö"
    "SM"  "Poikkeuslupa"
    "STR" "Suunnittelutarveratkaisu"
    "TJO" "Vastuullinen työnjohtaja"
    "US"  "Uhkasakko"
    "VAK" "Vakuus"
    "Y"   "Kokoontumishuone"
    "YHT" "Yhteisjärjestely"
    "YKJ" "YKEn lausunto jätevesistä haja-asutusalueilla"
    "YKL" "Ympäristökeskuksen lausunto"
    "YKM" "YKE:n maisematyölupa"
    "YMP" "YKEn maalämpöporakaivolausunto"
    "YVI" "YKEn vapautus liittymisestä viemäriin"
    "YVJ" "YKE:n vapautus liittymisestä vesijohtoon"
    "YVV" "YKEn vapautus liittymisestä vesijohtoon ja viemäriin"
    "Z"   "Ei luvanvarainen hanke (Z-lausunto)"
    nil))

(defn remove-empty-rakennuspaikka
  "`app/make-application` creates an empty location document (using 'rakennuspaikka' schema)
  for the created application. Since we're using 'rakennuspaikka-kuntagml'-schema instead,
  this document is not used and can be weeded out here."
  [{:keys [documents] :as app}]
  (assoc app :documents
         (remove #(= "rakennuspaikka" (get-in % [:schema-info :name])) documents)))

(defn op-name->schema-name [op-name]
  (-> op-name operations/get-operation-metadata :schema))

(sc/defn ^:always-validate make-converted-application-id
  "An application id is created for the given `year` and not for the current year as in
  normal paper application imports."
  [municipality :- ssc/NonBlankStr year :- ssc/PosInt]
  (let [sequence-name (str "applications-" municipality "-" year)
        nextvalue     (mongo/get-next-sequence-value sequence-name)
        counter       (format (if (env/feature? :prefixed-id) "9%04d" "%05d") nextvalue)
        app-id        (ss/join "-" ["LP" municipality year counter])]
    (if (mongo/any? :applications {:_id app-id}) ;; Double-check that there will be no clashes
      (recur municipality year)
      app-id)))



(defn- found? [text regex]
  (some->> text ss/blank-as-nil (re-find regex)))

(defn deduce-operation-type
  "Takes a kuntalupatunnus and a 'toimenpide'-element from app-info, returns the operation type"
  ([conversion-doc description]
   (let [suffix          (some-> (:suffix conversion-doc) name)
         in-description? (partial found? (ss/lower-case description))]
     (cond
       (= suffix "tjo")     "tyonjohtajan-nimeaminen-v2"
       (#{"p" "pi"} suffix) "purkaminen"
       (or (ss/ends-with? suffix "j")
           (= suffix "jl")) "raktyo-aloit-loppuunsaat"
       (= suffix "mai")     (cond
                              (in-description? #"kaatami|kaatoa") "puun-kaataminen"
                              (in-description? #"valmistele")     "muu-tontti-tai-kort-muutos"
                              (in-description? #"kaivam|kaivu")   "kaivuu"
                              (in-description? #"pysäk|liittym")  "tontin-jarjestelymuutos"
                              :else                               "muu-maisema-toimenpide")
       (= suffix "puu")     "puun-kaataminen"
       :else                "konversio"))) ;; A minimal generic operation for this purpose.
  ([conversion-doc description toimenpide]
   (let [suffix                      (some-> (:suffix conversion-doc) name)
         uusi?                       (contains? toimenpide :uusi)
         {:keys [kayttotarkoitus
                 rakennustunnus]}    (some-> toimenpide :rakennustieto :Rakennus :rakennuksenTiedot)
         rakennus?                   (-> toimenpide :rakennustieto :Rakennus boolean)
         rakennelma?                 (-> toimenpide :rakennelmatieto :Rakennelma boolean)
         rakennuksen-selite          (some-> rakennustunnus :rakennuksenSelite ss/lower-case)
         muuttaminen?                (or (contains? toimenpide :muuMuutosTyo)
                                         (contains? toimenpide :uudelleenrakentaminen)
                                         (= "d" suffix)
                                         (ss/ends-with? suffix "l"))
         laajentaminen?              (or (contains? toimenpide :laajennus)
                                         (= rakennuksen-selite "laajennus")
                                         (= "b" suffix))
         purkaminen?                 (or (contains? toimenpide :purkaminen)
                                         (contains? #{"p" "pi"} suffix))
         rakennelman-kuvaus          (some-> toimenpide :rakennelmatieto :Rakennelma
                                             :kuvaus :kuvaus ss/lower-case)
         rakennelman-selite          (some-> toimenpide :rakennelmatieto :Rakennelma
                                             :tunnus :rakennuksenSelite ss/lower-case)
         rakennelman-kayttotarkoitus (some-> toimenpide :rakennelmatieto :Rakennelma
                                             :kayttotarkoitus ss/lower-case)
         in-description?             (partial found? (ss/lower-case description))
         in-somewhere?               (fn [flag regex]
                                       (and flag
                                            (some #(found? % regex) [rakennuksen-selite
                                                                     rakennelman-kuvaus
                                                                     rakennelman-selite])))
         in-house?                   (partial in-somewhere? rakennus?)
         in-structure?               (partial in-somewhere? rakennelma?)
         in-use?                     (fn [regex]
                                       (some #(found? % regex) [(ss/lower-case kayttotarkoitus)
                                                                rakennelman-kayttotarkoitus]))]
     (cond
       purkaminen?    "purkaminen"

       (and uusi? (or (in-house? #"omakotitalo|paritalo")
                      (in-use? #"011 yhden asunnon talot|kahden asunnon")))
       "pientalo"

       (and uusi? (or (in-house? #"talousrakennus")
                      (in-use? #"talousrakennuk|sauna")))
       "varasto-tms"

       (and uusi?
            (or (in-house? #"kerrostalo|asuinkerrostalo|rivitalo")
                (in-use? #"rivitalo|kerrostalo|luhtitalo")))
       "kerrostalo-rivitalo"

       (and uusi? (in-use? #"vapaa-ajan"))
       "vapaa-ajan-asuinrakennus"

       (and uusi? (or (in-structure? #"autokato|autosuoja|autotalli")
                      (and rakennelma? (in-use? #"autosuoja|autotalli"))))
       "auto-katos"

       (and laajentaminen? (in-use? #"toimisto|talousr"))
       "talousrakennus-laaj"

       (and laajentaminen? (in-use? #"teollisuuden tuotantorak"))
       "teollisuusrakennus-laaj"

       (and laajentaminen?
            (or (in-use? #"yhden asunnon talot")
                (in-house? #"omakotitalo")))
       "pientalo-laaj"

       (and laajentaminen?
            (or (in-use? #"rivital|kerrostal")
                (in-house? #"rivital|kerrostal")))
       "kerrostalo-rt-laaj"

       (and laajentaminen? (in-use? #"vapaa-ajan"))
       "vapaa-ajan-rakennus-laaj"

       (and muuttaminen? (in-description? #"ulko|julkisivu"))
       "julkisivu-muutos"

       (and muuttaminen? (in-description? #"huoneeksi|asuin"))
       "sisatila-muutos"

       (and rakennelma? (or (in-structure? #"jäteve")
                            (in-use? #"jätevesi")))
       "jatevesi"

       (and rakennelma? (or (in-structure? #"maalämpö")
                            (in-use? #"maalämpö")))
       "maalampo"

       (and rakennelma?
            (or (in-structure? #"^aita|^aidan")
                (in-use? #"^aita$")))
       "aita"

       (and rakennelma? (or (in-structure? #"mainos")
                            (in-use? #"mainos")))
       "mainoslaite"

       (and rakennelma?
            (or (in-structure? #"antenni|pilari|laituri|lipputan|markiisi|^masto|piippu|pylvä|torni|aurinko")
                (in-use? #"antenni|pilari|laituri|lipputanko|markiisi|masto|piippu|pylväs|torni|aurinko")))
       "masto-tms"

       rakennelma?    "muu-rakentaminen"
       uusi?          "muu-uusi-rakentaminen"
       laajentaminen? "muu-rakennus-laaj"
       rakennus?      "konversio-rakennus"
       :else          "konversio"))))

(def with-sheriff-note
  "Alias for `db/with-sheriff-note`."
  db/with-sheriff-note)

(def with-reporting-override
  "Alias for `db/with-reporting-override`."
  db/with-reporting-override)

(sc/defn valid-timestamp-or-nil :- (sc/maybe ssc/Timestamp)
  [zoned :- date/ZonedArg]
  (try
    (some-> zoned date/timestamp ssc/valid-timestamp!)
    (catch Exception _
      (log/warn "No valid timestamp for" zoned))))

(defn fetch-xml
  "Fetches KuntaGML message from the backing system. Note that this function is also called
  from `conversion-proxy` endpoint."
  [organization-or-id backend-id]
  (try
    {:xml (krysp/get-application-xml-by-backend-id {:organization organization-or-id
                                                    :permitType   (-> backend-id
                                                                      conv-sc/destructure-permit-id
                                                                      :permit-type)}
                                                   backend-id
                                                   true)}
    (catch Exception e
      {:error (ex-message e)})))

(def ^:const conversion-user-id "conversion-user")

(def conversion-user-data (assoc usr/batchrun-user-data
                                 :id conversion-user-id
                                 :username "konversio@lupapiste.fi"
                                 :lastName "Konversio"))

(defn conversion-user
  "Similar to `usr/batchrun-user` but with `conversion-user-data`."
  ([organization-id]
   (cond-> conversion-user-data
     (ss/not-blank? organization-id)
     (assoc-in [:orgAuthz (keyword organization-id)] #{:authority})))
  ([] (conversion-user nil)))

(defn conversion-user?
  "True if the `user` id matches `conversion-user-id`."
  [user]
  (= (:id user) conversion-user-id))

(defn conversion-or-batchrun-user?
  "Legacy conversions used `batchrun-user`."
  [user]
  (or (conversion-user? user)
      (usr/batchrun-user? user)))

(defn kuntagml-states
  "Vector of `:pvm`, `:tila` maps. Both fields are strings. Nil if no state history in
  KuntaGML."
  [xml]
  (some->> (xml/select xml [:kasittelynTilatieto :Tilamuutos])
           seq
           (mapv cr/all-of)))

(defn column-key [text]
  (-> (or (ss/blank-as-nil text)
          (gensym "nil-col"))
      (ss/replace #"[\(\)\/:]" "")
      ->kebab-case
      keyword))
