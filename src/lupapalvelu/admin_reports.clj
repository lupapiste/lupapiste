(ns lupapalvelu.admin-reports
  (:require [clj-time.local :as local]
   [dk.ative.docjure.spreadsheet :as xls]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]))

(defn string->keyword
  "String to trimmed lowercase keyword."
  [s]
  (-> s ss/trim ss/lower-case keyword))

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

(defn cell-def [row key]
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

(defn- workbook [sheets]
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

(defn user-report [company allow professional]
  (let [data    (user-report-data company allow professional)
        columns (concat [:lastName :firstName :email :phone :street
                         :zip :city :architect :allowDirectMarketing]
                        (when (not= company :no)
                          [:company.name :company.y]))
        headers (map #(:header (cell-def nil %)) columns)
        rows    (for [row data]
                  (map #(:value (cell-def row %)) columns))]
    (excel-response "User_report" [{:name "K\u00e4ytt\u00e4j\u00e4t"
                                    :header headers
                                    :rows rows}])))
