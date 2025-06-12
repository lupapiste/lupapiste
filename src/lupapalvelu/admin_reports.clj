(ns lupapalvelu.admin-reports
  (:require [clojure.set :as set]
            [dk.ative.docjure.spreadsheet :as xls]
            [lupapalvelu.application-search :as app-search]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [ring.util.io :as ring-io]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all])
  (:import [java.util Date]
           [org.apache.poi.ss.usermodel Sheet]))

(set! *warn-on-reflection* true)

(defn string->keyword
  "String to trimmed lowercase keyword."
  [s]
  (-> s ss/trim ss/lower-case keyword))

(defn- workbook
  "Creates Docjure excel workbook form the given sheets. Each sheet is
  map with keys :name :header :rows."
  [sheets]
  (let [wb (apply xls/create-workbook
                  (->> sheets
                       (map (fn [{:keys [name header rows]}]
                              [name (cons header rows)]))
                       (apply concat)))
        header-style (xls/create-cell-style! wb {:font {:bold true}
                                                 :border-bottom :thin})]
    (doseq [{:keys [name header]} sheets
            :let [^Sheet sheet (xls/select-sheet name wb)]]
      (-> sheet
          xls/row-seq
          first
          (xls/set-row-style! header-style))
      (doseq [i (range (count header))]
        (.autoSizeColumn sheet i)))
    wb))

(defn excel-response
  "HTTP response for excel download. Filename without extension (will
  be appended with date). Each sheet is map with
  keys :name :header :rows."
  [filename sheets]
  (let [filename (format "%s_%s.xlsx" filename
                         (date/zoned-format (date/now) :basic-date))]
    {:status 200
     :headers {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)
               "Cache-Control" "no-cache"}
     :body (ring-io/piped-input-stream
            (fn [out]
              (xls/save-workbook! out (workbook sheets))))}))

(defn- month-start-as-timestamp [month year]
  (date/timestamp {:year year :month month :day 1}))

(defn- following-month-start-as-timestamp [month year]
  (-> (date/zoned-date-time {:year year :month month :day 1})
      (.plusMonths 1)
      date/timestamp))

;; -------------------------------
;; Company unsubscribed
;; -------------------------------

(defn company-unsubscribed-emails []
  (:emails (mongo/select-one :admin-config {:_id "unsubscribed"})))

(defn upsert-company-unsubscribed
  "Emails is a string where addresses are separated by whitespace."
  [emails]
  (mongo/update-by-id :admin-config
                      "unsubscribed"
                      {:emails (-<> emails
                                   ss/lower-case
                                   ss/trim
                                   (ss/split #"[\s,]+")
                                   (remove ss/blank? <>)
                                   sort)}
                      :upsert true))

(defn users-spam-flags
  "Assocs :spam property to every user. Spam is false if the user's
  email is included in the company-unsubscribed-emails."
  [users]
  (let [emails (set (company-unsubscribed-emails))]
    (map (fn [{email :email :as user}]
           (assoc user :spam (not (contains? emails (ss/lower-case email)))))
         users)))

;; -------------------------------
;; User report
;; -------------------------------

(defn yes-no-both
  "Returns pre-checker that given parameters' values are yes, no or both."
  [parameters]
  (fn [{data :data}]
    (when-not (every? (util/fn-> data string->keyword #{:yes :no :both}) parameters)
      :error.yes-no-both)))

(defn- user-list [company allow professional]
  (mongo/snapshot :users
                  (merge {:enabled true}
                         (when-not (= allow :both)
                           {:allowDirectMarketing (= allow :yes)})
                         (when-not (= professional :both)
                           {:architect (= professional :yes)})
                         (when-not (= company :both)
                           {:company {$exists (= company :yes)}}))
                  {:lastName 1 :firstName 1 :email 1 :phone 1
                   :companyName 1 :street 1 :zip 1 :city 1 :architect 1
                   :allowDirectMarketing 1 :company.id 1 :company.role 1}))

(defn- company-map []
  (reduce (fn [acc {:keys [id] :as company}]
            (assoc acc id (dissoc company :id)))
          {}
          (mongo/snapshot :companies {} [:y :name :created :locked :billingType])))

(defn- user-report-data [company allow professional]
  (let [users (users-spam-flags (user-list company allow professional))]
    (if (not= company :no)
      (let [companies (company-map)]
        (map #(let [{:keys [id role]} (:company %)]
                (if-let [company (get companies id)]
                  (assoc % :company (assoc company :role role))
                  %))
               users))
      users)))

(defn- safe-local-date [timestamp]
  (let [ts (util/->long timestamp)]
    (if (and ts (pos? ts))
      (date/finnish-date ts :zero-pad)
      "")))

(defn- company-role-in-finnish [role]
  (get {:admin "yll\u00e4pit\u00e4j\u00e4"
        :user  "k\u00e4ytt\u00e4j\u00e4"}
       (keyword role)
       ""))

(defn- company-billing-type [billing-type]
  (if (ss/blank? billing-type)
    ""
    (localize :fi (str "register.company.billing." billing-type ".title"))))

(defn user-report-cell-def [row key]
  (let [defs    {:lastName             "Sukunimi"
                 :firstName            "Etunimi"
                 :email                "Email"
                 :phone                "Puhelin"
                 :companyName          "Yritys"
                 :street               "Katuosoite"
                 :zip                  "Postinumero"
                 :city                 "Kunta"
                 :architect            "Ammattilainen"
                 :allowDirectMarketing "Suoramarkkinointilupa"
                 :spam                 "Spam"
                 :company.name         {:header "Yritystili"
                                        :path   [:company :name]}
                 :company.billingType  {:header "Laskutusjakso"
                                        :path   [:company :billingType]
                                        :fun    company-billing-type}
                 :company.y            {:header "Y-tunnus"
                                        :path   [:company :y]}
                 :company.role         {:header "Yritystilirooli"
                                        :path   [:company :role]
                                        :fun    company-role-in-finnish}
                 :company.created      {:header "Yritystili avattu"
                                        :path   [:company :created]
                                        :fun    safe-local-date}
                 :company.locked       {:header "Yritystili suljettu"
                                        :path   [:company :locked]
                                        :fun    safe-local-date}}
        key-def (get defs key)]
    (if (map? key-def)
      {:header (:header key-def)
       :value  ((get key-def :fun identity) (get-in row (:path key-def) ""))}
      {:header key-def
       :value  (get row key "")})))



(defn user-report [company allow professional]
  (let [data    (user-report-data company allow professional)
        columns (concat [:lastName :firstName :email :phone :companyName
                         :street :zip :city :architect :allowDirectMarketing :spam]
                        (when-not (= company :no)
                          [:company.name :company.billingType :company.y
                           :company.role :company.created :company.locked]))
        headers (map #(:header (user-report-cell-def nil %)) columns)
        rows    (for [row data]
                  (map #(:value (user-report-cell-def row %)) columns))]
    (excel-response "User_report" [{:name "K\u00e4ytt\u00e4j\u00e4t"
                                    :header headers
                                    :rows rows}])))

;; -------------------------------
;; Waste report
;; -------------------------------

(def waste-schemas ["rakennusjatesuunnitelma"
                    "rakennusjateselvitys"
                    "laajennettuRakennusjateselvitys"
                    "laajennettuRakennusjateselvitys-v2"])
(def waste-plain-columns [:suunniteltuMaara
                          {:name  :yksikko
                           :loc "jateyksikko"}
                          :painoT])
(def waste-plan-columns {:rakennusJaPurkujate (cons {:name :jatetyyppi
                                                     :loc "jatetyyppi"}
                                                    waste-plain-columns)
                         :vaarallisetAineet (cons {:name :vaarallinenainetyyppi
                                                   :loc "vaarallinenainetyyppi"}
                                                  waste-plain-columns)})
(def waste-actual-columns {:rakennusJaPurkujate (concat [{:name :jatetyyppi
                                                          :loc "jatetyyppi"}]
                                                        waste-plain-columns
                                                        [:toteutunutMaara
                                                         :jatteenToimituspaikka])
                           :vaarallisetAineet (concat [{:name :vaarallinenainetyyppi
                                                        :loc "vaarallinenainetyyppi"}]
                                                      waste-plain-columns
                                                      [:toteutunutMaara
                                                       :jatteenToimituspaikka])})

(defn other-column [prefix selected other]
  (cond
    (= selected "muu")       other
    (ss/not-blank? selected) (localize :fi (format "%s.%s" prefix selected))
    :else                    ""))

(defn extended-columns [kind]
  [{:name :jate-group
    :fun (fn [{:keys [muu jate]}]
           (other-column (format "laajennettuRakennusjateselvitys.rakennusJaPurkujate.%s.jate-group.jate"
                                 (name kind))
                         jate
                         muu))}
   :maara
   (if (= kind :muuJate)
     {:fixed (localize :fi "unit.tonnia")}
     {:name  :yksikko
      :loc "unit"})
   :sijoituspaikka])

(def relocatable-columns [{:name :aines-group
                           :fun (fn [{:keys [muu aines]}]
                                  (other-column "waste.aines" aines muu))}
                          :hyodynnetaan
                          :poisajettavia
                          :sijoituspaikka])

(def extended-waste-columns {:vaarallisetJatteet (extended-columns :vaarallisetJatteet)
                             :muuJate (extended-columns :muuJate)
                             :muutKaivettavatMassat relocatable-columns
                             :orgaaninenAines relocatable-columns
                             :kaivettavaMaa relocatable-columns})

(defn- waste-data [organization-id yearVerdictGiven]
  (let [start-ts (month-start-as-timestamp 1 yearVerdictGiven)
        end-ts   (following-month-start-as-timestamp 12 yearVerdictGiven)]
    (map (fn [{:keys [documents] :as app}]
           (->> documents
                (filter #(contains? (set waste-schemas)
                                    (-> % :schema-info :name)))
                (reduce (fn [acc {:keys [schema-info data]}]
                          (assoc acc (:name schema-info) (tools/unwrapped data)))
                        (dissoc app :documents))))
         (mongo/select :applications
                       {:organization               organization-id
                        :documents.schema-info.name {$in waste-schemas}
                        :permitType                 "R"
                        :verdictDate                {$gte start-ts $lt end-ts}}
                       [:organization :documents]))))

(defn- listify
  "Map list to vector. Maps without proper values omitted."
  [m k]
  (->> (get m k)
       vals
       (remove (util/fn->> vals (every? #(or (map? %) (ss/blank? %)))))
       (assoc {} k)))

(defn waste-map->row [columns m]
  (map (fn [{:keys [fixed fun name loc] :as col}]
         (let [v (get m (or name col))]
           (cond
            fixed fixed
            fun (fun v)
            loc (if (ss/not-blank? v)
                  (localize :fi (format "%s.%s" loc v))
                  "")
            :else v)))
       columns))

(defn waste-rows
  "(prefix m) must be list."
  ([m prefix columns]
   (waste-rows m prefix columns (localize :fi prefix)))
  ([m prefix columns prefix-loc]
   (->> m
        prefix
        (map (partial waste-map->row (prefix columns)))
        (map (partial cons prefix-loc)))))

(defn plan-sheet [data]
  (->> (set/intersection (set (keys data)) (set (keys waste-plan-columns)))
       (map #(waste-rows (listify data %) % waste-plan-columns))
       (apply concat)))

(defn actual-sheet [data]
  (->> (set/intersection (set (keys data)) (set (keys waste-actual-columns)))
       (map (fn [prefix]
              (-<>> [:suunniteltuJate :suunnittelematonJate]
                    (map (fn [k]
                           (k (listify (prefix data) k))))
                    (apply concat)
                    (assoc {} prefix)
                    (waste-rows <> prefix waste-actual-columns))))
       (apply concat)))

(defn extended-waste-sheet [{data :rakennusJaPurkujate}]
  (->> (set/intersection (set (keys data)) (set (keys extended-waste-columns)))
       (map #(waste-rows (listify data %)
                         %
                         extended-waste-columns
                         (localize :fi (format "laajennettuRakennusjateselvitys.rakennusJaPurkujate.%s._group_label" (name %)))))
       (apply concat)))

(defn extended-other-sheet [{:keys [muutKaivettavatMassat orgaaninenAines kaivettavaMaa]}]
  (let [data {:muutKaivettavatMassat (:ainekset muutKaivettavatMassat)
              :orgaaninenAines (:ainekset orgaaninenAines)
              :kaivettavaMaa (:ainekset kaivettavaMaa)}]
    (->> (set/intersection (set (keys data)) (set (keys extended-waste-columns)))
         (map #(waste-rows (listify data %) % extended-waste-columns
                           (localize :fi (format "laajennettuRakennusjateselvitys.%s.ainekset._group_label" (name %)))))
         (apply concat))))


(def waste-sheet-defs
  {:plan {:name "Rakennusj\u00e4tesuunnitelma"
          :schema-name "rakennusjatesuunnitelma"
          :fun plan-sheet
          :header ["ram.type"
                   "jatetyyppi"
                   "rakennusJaPurkujate.suunniteltuMaara"
                   "jateyksikko"
                   "rakennusJaPurkujate.painoT"]}
   :actual {:name "Rakennusj\u00e4teselvitys"
            :schema-name "rakennusjateselvitys"
            :fun actual-sheet
            :header ["ram.type"
                     "jatetyyppi"
                     "rakennusJaPurkujate.suunniteltuMaara"
                     "jateyksikko"
                     "rakennusJaPurkujate.painoT"
                     "rakennusJaPurkujate.toteutunutMaara"
                     "rakennusJaPurkujate.jatteenToimituspaikka"]}
   :extended-waste {:name "Laajennettu 1"
                    :schema-name "laajennettuRakennusjateselvitys"
                    :fun extended-waste-sheet
                    :header ["ram.type"
                             "jatetyyppi"
                             "laajennettuRakennusjateselvitys.rakennusJaPurkujate.vaarallisetJatteet.maara"
                             "jateyksikko"
                             "laajennettuRakennusjateselvitys.rakennusJaPurkujate.vaarallisetJatteet.sijoituspaikka"]}
   :extended-other {:name "Laajennettu 2"
                    :schema-name "laajennettuRakennusjateselvitys"
                    :fun extended-other-sheet
                    :header ["ram.type"
                             "laajennettuRakennusjateselvitys.orgaaninenAines.ainekset.aines-group"
                             "laajennettuRakennusjateselvitys.orgaaninenAines.ainekset.hyodynnetaan"
                             "laajennettuRakennusjateselvitys.orgaaninenAines.ainekset.poisajettavia"
                             "laajennettuRakennusjateselvitys.orgaaninenAines.ainekset.sijoituspaikka"]}})


(defn- waste-sheet [data def-key]
  (let [{:keys [schema-name fun header name]} (def-key waste-sheet-defs)]
    {:rows (->> data
                (filter #(get % schema-name))
                (map (fn [{:keys [id organization] :as app}]
                       (map (partial concat [id organization])
                            (fun (get app schema-name)))) )
                (apply concat))
     :header (concat ["LP-tunnus" "Organisaatio"]
                     (map (partial localize :fi) header))
     :name name}))

(defn waste-report [organization-id yearVerdictGiven]
  (let [data (waste-data organization-id yearVerdictGiven)]
    (->> [:plan :actual :extended-waste :extended-other]
         (map (partial waste-sheet data))
         (excel-response "waste-report"))))

(defn- make-date
  "Separate function so the type hinting removes a reflection warning."
  [^Long ts]
  (Date. ts))

(defn verdicts-contracts-report [start-ts end-ts]
  (let [loc          (partial localize :fi)
        period       {$gte start-ts
                      $lte end-ts}
        applications (mongo/select :applications
                                   {:permitType    {$ne :ARK}
                                    :permitSubtype {$nin [:tyonjohtaja-hakemus :tyonjohtaja-ilmoitus]}
                                    $or            [{:verdictDate period}
                                                    {:agreementSigned period}]}
                                   [:primaryOperation :state :permitType :organization
                                    :verdictDate :agreementSigned])
        ;; We fetch every organization name. This way we can retain the lazyness of
        ;; `applications`. Since the number of organizations is quite low (three digits)
        ;; this is a prudent memory saving measure.
        org-names    (->> (mongo/select :organizations {} [:name.fi])
                          (map (juxt :id (util/fn-> :name :fi)))
                          (into {}))
        sheets       [{:name   (loc :requests)
                       :header [(loc :applications.id.longtitle)
                                (loc :operations.primary)
                                (loc :tila)
                                (loc :application.type)
                                (loc :billing.organization-id)
                                (loc :organization.name)
                                (loc :admin-report.verdicts.verdict-date)
                                (loc :admin-report.verdicts.contract-date)]
                       :rows   (for [{:keys [id primaryOperation state permitType organization
                                             verdictDate agreementSigned]} applications]
                                 [id
                                  (->> primaryOperation :name (loc :operations))
                                  (loc state)
                                  permitType
                                  organization
                                  (get org-names organization)
                                  (some-> verdictDate make-date)
                                  (some-> agreementSigned make-date)])}]]
    (excel-response (loc :admin-report.verdicts.filename) sheets)))


(defn- number-value [v]
  (let [v (util/->double (ss/replace v "," "."))]
    (when-not (= (str v) "0.0")
      v)))

(defn- some-value
  ([data value-fn kwps]
   (some (fn [kwp]
           (some->> (util/split-kw-path kwp)
                    (get-in data)
                    ss/blank-as-nil
                    value-fn))
         (flatten [kwps])))
  ([data kwps]
   (some-value data identity kwps)))

(defn- document-columns [{:keys [data schema-info]}]
  (when-let [op-name (some->> schema-info :op :name ss/blank-as-nil (localize :fi :operations))]
    (let [data    (tools/unwrapped data)
          area-vs (map (partial some-value data number-value)
                       [;; R, P
                        [:mitat.kerrosala :toimenpiteet.kerrosala]
                        :mitat.rakennusoikeudellinenKerrosala
                        ;; Structure (R), R, P
                        [:kokonaisala :mitat.kokonaisala :toimenpiteet.kokonaisala]
                        :mitat.kellarinpinta-ala
                        :mitat.muutosala :kaytettykerrosala.pintaAla])]
      (when (some number? area-vs)
        (concat [op-name]
                area-vs
                [(some-value data [;;Structure (R), R, P
                                   :kayttotarkoitus
                                   :kaytto.kayttotarkoitus
                                   :kaytettykerrosala.kayttotarkoitusKoodi])
                 (some-value data :toimenpiteet.kayttotarkoitus) ;; P
                 (some-value data :kaytto.rakennusluokka)])))))

(defn operation-areas-report [start-ts end-ts]
  (let [loc          (partial localize :fi)
        period       {$gte start-ts
                      $lte end-ts}
        applications (mongo/select :applications
                                   {:permitType    {$in [:R :P]}
                                    :permitSubtype {$nin [:tyonjohtaja-hakemus, :tyonjohtaja-ilmoitus]}
                                    $or            [{:verdictDate period}
                                                    {:agreementSigned period}]}
                                   [:documents :state :permitType :organization])
        ;; We fetch every organization name. This way we can retain the lazyness of
        ;; `applications`. Since the number of organizations is quite low (three digits)
        ;; this is a prudent memory saving measure.
        org-names    (->> (mongo/select :organizations {} [:name.fi])
                          (map (juxt :id (util/fn-> :name :fi)))
                          (into {}))
        usage        #(str (loc :kayttotarkoitus) " " %)
        sheets       [{:name   (loc :operations)
                       :header [(loc :applications.id.longtitle)
                                (loc :tila)
                                (loc :application.type)
                                (loc :billing.organization-id)
                                (loc :organization.name)
                                (loc :applications.operation)
                                (loc :purku.mitat.kerrosala)
                                (loc :purku.mitat.rakennusoikeudellinenKerrosala)
                                (loc :purku.mitat.kokonaisala)
                                (loc :purku.mitat.kellarinpinta-ala)
                                (loc :rakennuksen-muuttaminen.mitat.muutosala)
                                (loc :rakennushanke.kaytettykerrosala.pintaAla) ;; P
                                (usage 1)
                                (usage 2) ;; P
                                (loc :rakennusluokka)]
                       :rows (mapcat (fn [{:keys [id state permitType organization
                                                  documents]}]
                                       (when-let [colxs (some->> documents
                                                                 (keep document-columns)
                                                                 seq)]
                                         (let [preamble [id
                                                         (loc state)
                                                         permitType
                                                         organization
                                                         (get org-names organization)]]
                                           (map #(concat preamble %) colxs))))
                                     applications)}]]
    (excel-response (loc :admin-report.operation-areas) sheets)))

(defn- ts->key
  "1694017035297 -> [2023 9]"
  [ts]
  {:pre [ts]}
  (let [zdt (date/zoned-date-time ts)]
    [(.getYear zdt) (.getMonthValue zdt)]))

(defn- safe-inc [n]
  (if n (inc n) 1))

(defn- safe-add [a b]
  (+ (or a 0) (or b 0)))

(defn- add-counts [data {:keys [secondaryOperations]} & path]
  (-> data
      (update-in (util/conjv path :applications) safe-inc)
      (update-in (util/conjv path :operations) safe-add (inc (count secondaryOperations)))))

(defn applications-period-report [start-ts end-ts]
  (let [loc          (partial localize :fi)
        period       {$gte start-ts
                      $lte end-ts}
        by-submitted (mongo/select :applications
                                   {:permitType            {$ne :ARK}
                                    :primaryOperation.name {$ne :aiemmalla-luvalla-hakeminen}
                                    :submitted             period}
                                   [:permitType :primaryOperation.name :submitted :secondaryOperations])
        by-opened    (mongo/select :applications
                                   ;; :opened is repeated in order to make sure we hit
                                   ;; permit-type - opened index.
                                   {$or [{:permitType :ARK :opened period}
                                         {:permitType            :R
                                          :primaryOperation.name :aiemmalla-luvalla-hakeminen
                                          :opened                period}]}
                                   [:permitType :primaryOperation.name :opened :secondaryOperations])
        result       (reduce (fn [acc {:keys [permitType submitted primaryOperation] :as doc}]
                               (let [op (keyword (:name primaryOperation))]
                                 (add-counts acc doc (ts->key submitted)
                                             (case op
                                               (:tyonjohtajan-nimeaminen-v2 :suunnittelijan-nimeaminen)
                                               op
                                               permitType))))
                             {}
                             by-submitted)
        result       (reduce (fn [acc {:keys [permitType opened primaryOperation] :as doc}]
                               (let [op (keyword (:name primaryOperation))]
                                 (add-counts acc doc (ts->key opened)
                                             (case op
                                               :aiemmalla-luvalla-hakeminen
                                               op
                                               permitType))))
                             result
                             by-opened)
        sheets       [{:name   (loc :authorityAdmin.applicationsReportTimeperiod.title)
                       :header [(loc :year)
                                (loc :month)
                                (loc :application.type)
                                (loc :common.description)
                                (loc :requests)
                                (loc :operations)]
                       :rows   (sort (mapcat (fn [[[year month] m]]
                                               (map (fn [[k {:keys [applications operations]}]]
                                                      (vec (concat [year month]
                                                                   (if (keyword? k)
                                                                     ["R" (loc :operations k)]
                                                                     [k (loc k)])
                                                                   [applications operations])))
                                                    m))
                                             result))}]]
    (excel-response (loc :authorityAdmin.applicationsReportTimeperiod.title) sheets)))

(defn- get-review-tasks [application]
  (keep (fn [task] (when (and (-> task :schema-info :subtype (= "review"))
                              (-> task :state (= "sent")))
                     (assoc task :app-id (:id application))))
        (:tasks application)))

(defn review-report [org-id start-ts end-ts]
  (let [loc          (partial localize :fi)
        period       {$gte start-ts
                      $lte end-ts}
        applications (mongo/select :applications
                                   {:permitType                {$in [:R :P]}
                                    :organization              org-id
                                    :primaryOperation.name     {$ne [:tyonjohtajan-nimeaminen-v2]}
                                    :tasks.schema-info.subtype {$in [:review :review-backend]}
                                    :tasks.state               :sent
                                    :verdictDate               period}
                                   [:tasks])
        tasks        (mapcat get-review-tasks applications)
        sheets       [{:name   (loc :authorityAdmin.applicationsReviewsTimeperiod.title)
                       :header [(loc :task-katselmus.nimi)
                                (loc :task-katselmus.katselmuksenLaji)
                                (loc :task-katselmus.katselmus.tila)
                                (loc :task-katselmus.katselmus.pitoPvm)
                                (loc :task-katselmus.katselmus.pitaja)
                                (loc :applications.id.longtitle)]
                       :rows (map (fn [{:keys [app-id taskname data]}]
                                    (let [katselmus (:katselmus data)
                                          tyyppi    (-> data :katselmuksenLaji :value)
                                          tila      (-> katselmus :tila :value)
                                          pvm       (-> katselmus :pitoPvm :value)
                                          pitaja    (-> katselmus :pitaja :value)]
                                      [taskname tyyppi tila pvm (if (map? pitaja) (:name pitaja) pitaja) app-id]))
                                  tasks)}]]
    (excel-response (loc :authorityAdmin.applicationsReviewsTimeperiod.title) sheets)))

(defn- archiving-orgs
  []
  (->> (mongo/select :organizations {:deactivated               {$ne true}
                                     :permanent-archive-enabled true}
                     {:name.fi 1})
       (map (fn [{:keys [id] :as org}]
              [id (-> org :name :fi)]))
       (into {})))

(defn- archivable-per-org
  "Produces a vector of maps, with `:org-id`, `:org-name`, and `:app-count` keys."
  [orgs {:keys [modified-after modified-before]}]
  (->> (mongo/aggregate :applications
                        [;; Find all archivable applications
                         {$match {$and [;; There's an index for org + modified
                                        {:organization {$in (set (keys orgs))}
                                         :modified     {$gte modified-after
                                                        $lte modified-before}}
                                        app-search/archival-base-query]}}
                         ;; Group them by organization
                         {$project {:organization 1}}
                         {$group {:_id       :$organization
                                  :app-count {$count {}}}}])
       (map (fn [{:keys [_id app-count]}]
              {:org-id    _id
               :org-name  (get orgs _id)
               :app-count app-count}))
       (sort-by :org-id)
       (into [])))

(defn archivable-applications-report
  "Produce an Excel report of the length of the _archiving queue_ for each organization.

  Options:
  * `modified-after` -- Applications modified after timestamp
  * `modified-before` -- Applications modified before timestamp"
  {:arglists '([{:keys [modified-after modified-before]}])}
  [opts]
  (let [orgs-map          (archiving-orgs)
        app-count-per-org (archivable-per-org orgs-map opts)
        loc               (partial localize :fi)
        title             :admin-report.archivable-applications.title
        sheets            [{:name   (loc title)
                            :header [(loc :organization.id)
                                     (loc :organization.name)
                                     (loc :admin-report.archivable-applications.count)]
                            :rows   (->> app-count-per-org
                                         (mapv (fn [{:keys [org-id org-name app-count]}]
                                                 [org-id org-name app-count])))}]]
    (excel-response (loc title) sheets)))
