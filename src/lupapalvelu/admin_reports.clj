(ns lupapalvelu.admin-reports
  (:require [clj-time.local :as local]
            [clojure.set :refer [intersection]]
            [dk.ative.docjure.spreadsheet :as xls]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.i18n :refer [localize]]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]
            )
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]))

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
     :body (with-open [out (ByteArrayOutputStream.)]
             (xls/save-workbook! out (workbook sheets))
             (ByteArrayInputStream. (.toByteArray out)))}))

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
  (mongo/select :users
                (merge {}
                       (when-not (= allow :both)
                         {:allowDirectMarketing (= allow :yes)})
                       (when-not (= professional :both)
                         {:architect (= professional :yes)})
                       (when-not (= company :both)
                         {:company {$exists (= company :yes)}}))
                {:lastName 1 :firstName 1 :email 1 :phone 1
                 :companyName 1 :street 1 :zip 1 :city 1 :architect 1
                 :allowDirectMarketing 1 :company.id 1}))

(defn- company-map []
  (reduce (fn [acc {:keys [id y name]}]
            (assoc acc id {:y y :name name}))
          {}
          (mongo/select :companies {} {:y 1 :name 1})))

(defn- user-report-data [company allow professional]
  (let [users (user-list company allow professional)]
    (if (not= company :no)
      (let [companies (company-map)]
        (map #(if-let [company (get companies (-> % :company :id))]
                (assoc % :company company)
                %)
             users))
      users)))

(defn user-report-cell-def [row key]
  (let [defs {:lastName "Sukunimi"
              :firstName "Etunimi"
              :email "Email"
              :phone "Puhelin"
              :companyName "Yritys"
              :street "Katuosoite"
              :zip "Postinumero"
              :city "Kunta"
              :architect "Ammattilainen"
              :allowDirectMarketing "Suoramarkkinointilupa"
              :company.name {:header "Yritystili"
                             :path [:company :name]}
              :company.y {:header "Y-tunnus"
                          :path [:company :y]}}
        key-def (get defs key)]
    (if (map? key-def)
      {:header (:header key-def)
       :value (get-in row (:path key-def) "")}
      {:header key-def
       :value (get row key "")})))



(defn user-report [company allow professional]
  (let [data    (user-report-data company allow professional)
        columns (concat [:lastName :firstName :email :phone :companyName
                         :street :zip :city :architect :allowDirectMarketing]
                        (when (not= company :no)
                          [:company.name :company.y]))
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
(defn extended-columns [kind]
  [{:name :jate
    :loc (format "laajennettuRakennusjateselvitys.rakennusJaPurkujate.%s.jate" (name kind))}
   :maara
   (if (= kind :muuJate)
     {:fixed (localize :fi "unit.tonnia")}
     {:name  :yksikko
      :loc "unit"})
   :sijoituspaikka])

(def other-columns [{:name :aines-group
                    :fun (fn [{:keys [muu aines]}]
                           (if (ss/blank? muu)
                             (if (ss/not-blank? aines)
                               (localize :fi (format "waste.aines.%s" aines))
                               "")
                             muu))}
                   :hyodynnetaan
                   :poisajettavia
                   :sijoituspaikka])

(def extended-waste-columns {:vaarallisetJatteet (extended-columns :vaarallisetJatteet)
                             :muuJate (extended-columns :muuJate)
                             :muutKaivettavatMassat other-columns
                             :orgaaninenAines other-columns
                             :kaivettavaMaa other-columns})


(defn- waste-data []
  (->> (mongo/select :applications
                    {:documents.schema-info.name {$in waste-schemas}}
                    {:organization 1 :documents 1})
      tools/unwrapped
      (map (fn [{:keys [documents] :as app}]
             (-<>> documents
                  (map (fn [{:keys [schema-info data]}]
                         (when (contains? (set waste-schemas) (:name schema-info))
                           (assoc {} (:name schema-info) data))))
                  (filter identity)
                  (apply merge)
                  (merge app)
                  (dissoc <> :documents))))))

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
