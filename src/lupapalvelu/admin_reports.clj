(ns lupapalvelu.admin-reports
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.local :as local]
            [clojure.set :refer [intersection]]
            [clojure.set :as set]
            [dk.ative.docjure.spreadsheet :as xls]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [ring.util.io :as ring-io]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

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
            :let [sheet (xls/select-sheet name wb)]]
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
                         (local/format-local-time (local/local-now) :basic-date))]
    {:status 200
     :headers {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)
               "Cache-Control" "no-cache"}
     :body (ring-io/piped-input-stream
            (fn [out]
              (xls/save-workbook! out (workbook sheets))))}))

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
                  (merge {}
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
          (mongo/snapshot :companies {} [:y :name :locked :billingType])))

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
      (util/to-local-date ts)
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
                           :company.role :company.locked]))
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
                    "laajennettuRakennusjateselvitys"])
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
    :default                 ""))

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


(defn- waste-data []
  (map (fn [{:keys [documents] :as app}]
         (->> documents
              (filter #(contains? (set waste-schemas)
                                  (-> % :schema-info :name)))
              (reduce (fn [acc {:keys [schema-info data]}]
                        (assoc acc (:name schema-info) (tools/unwrapped data)))
                      (dissoc app :documents))))
       (mongo/select :applications
                     {:documents.schema-info.name {$in waste-schemas}
                      :permitType "R"}
                     {:organization 1 :documents 1})))

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
  (->> (intersection (set (keys data)) (set (keys waste-plan-columns)))
       (map #(waste-rows (listify data %) % waste-plan-columns))
       (apply concat)))

(defn actual-sheet [data]
  (->> (intersection (set (keys data)) (set (keys waste-actual-columns)))
       (map (fn [prefix]
              (-<>> [:suunniteltuJate :suunnittelematonJate]
                    (map (fn [k]
                           (k (listify (prefix data) k))))
                    (apply concat)
                    (assoc {} prefix)
                    (waste-rows <> prefix waste-actual-columns))))
       (apply concat)))

(defn extended-waste-sheet [{data :rakennusJaPurkujate}]
  (->> (intersection (set (keys data)) (set (keys extended-waste-columns)))
       (map #(waste-rows (listify data %)
                         %
                         extended-waste-columns
                         (localize :fi (format "laajennettuRakennusjateselvitys.rakennusJaPurkujate.%s._group_label" (name %)))))
       (apply concat)))

(defn extended-other-sheet [{:keys [muutKaivettavatMassat orgaaninenAines kaivettavaMaa]}]
  (let [data {:muutKaivettavatMassat (:ainekset muutKaivettavatMassat)
              :orgaaninenAines (:ainekset orgaaninenAines)
              :kaivettavaMaa (:ainekset kaivettavaMaa)}]
    (->> (intersection (set (keys data)) (set (keys extended-waste-columns)))
         (map #(waste-rows (listify data %) % extended-waste-columns
                           (localize :fi (format "laajennettuRakennusjateselvitys.%s.ainekset._group_label" (name %)))))
         (apply concat))))


(def waste-sheet-defs {:plan {:name "Rakennusj\u00e4tesuunnitelma"
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

(defn waste-report []
  (let [data (waste-data)]
    (->> [:plan :actual :extended-waste :extended-other]
         (map (partial waste-sheet data))
         (excel-response "waste-report"))))

(defn- month-start-as-timestamp [month year]
  (-> (t/date-time year month 1)
      local/to-local-date-time
      tc/to-long))

(defn- following-month-start-as-timestamp [month year]
  (-> (t/date-time year month 1)
      (t/plus (t/days (t/number-of-days-in-the-month year month)))
      local/to-local-date-time
      tc/to-long))

(defn applications-per-month-query [month year group-clause filter-query timestamp-key]
  (let [start-ts (month-start-as-timestamp month year)
        end-ts   (following-month-start-as-timestamp month year)
        match-query {$and [{timestamp-key {$gt start-ts}}
                           {timestamp-key {$lt end-ts}}]}
        aggregate  (remove nil?
                     [{"$match" match-query}
                      (when filter-query
                        {"$match" filter-query})
                      {"$project" {:primaryOperation 1
                                   :permitType 1
                                   :nbrOfOperations {"$sum" [1 {"$size" {"$ifNull" ["$secondaryOperations", []]}}]}}}
                      {"$group" group-clause}])]
    (mongo/aggregate "applications" aggregate)))

(defn archiving-projects-per-month-query [month year]
  (->> (applications-per-month-query month year {:_id "$permitType"
                                                 :countApp {$sum 1}
                                                 :countOp  {$sum "$nbrOfOperations"}} {:permitType "ARK"} :opened)
       (map #(set/rename-keys % {:_id :permitType}))
       (sort-by (comp - :countApp))))

(defn prev-permits-per-month-query [month year]
  (applications-per-month-query month year {:_id "R_prev-permit"
                                            :countApp {$sum 1}
                                            :countOp  {$sum "$nbrOfOperations"}}
    {:primaryOperation.name :aiemmalla-luvalla-hakeminen} :opened))

(defn applications-per-month-per-permit-type [month year]
  (->> (applications-per-month-query month year {:_id "$permitType"
                                                 :countApp {$sum 1}
                                                 :countOp  {$sum "$nbrOfOperations"}} nil :submitted)
       (map #(set/rename-keys % {:_id :permitType}))
       (sort-by (comp - :countApp))))

(defn designer-and-foreman-applications-per-month [month year]
  (applications-per-month-query month year
                                {:_id "$primaryOperation.name"
                                 :countApp {$sum 1}}
    {:primaryOperation.name {$in [:tyonjohtajan-nimeaminen-v2
                                  :suunnittelijan-nimeaminen]}} :submitted))
