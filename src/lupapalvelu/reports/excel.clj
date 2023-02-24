(ns lupapalvelu.reports.excel
  (:require [dk.ative.docjure.spreadsheet :as spreadsheet]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [taoensso.timbre :refer [error]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream OutputStream]
           [org.apache.poi.ss.usermodel Sheet Row Cell]
           [org.apache.poi.xssf.usermodel XSSFWorkbook]))

(set! *warn-on-reflection* true)

(defn ^XSSFWorkbook create-workbook
  ([data sheet-name header-row-content row-fn]
   (create-workbook [{:sheet-name sheet-name
                      :header header-row-content
                      :row-fn row-fn
                      :data data}]))
  ([sheets]
   {:pre [(every? (every-pred :sheet-name :header :row-fn) sheets)]}
   (let [wb (apply spreadsheet/create-workbook
                   (->> sheets
                        (map (fn [{:keys [sheet-name header row-fn data]}]
                               [sheet-name (cons header (map row-fn data))]))
                        (apply concat)))]
     ; Format each sheet
     (doseq [^Sheet sheet (spreadsheet/sheet-seq wb)
             :let [^Row header-row   (-> sheet spreadsheet/row-seq first)
                   column-count (.getLastCellNum header-row)
                   bold-style   (spreadsheet/create-cell-style! wb {:font {:bold true}})]]
       (spreadsheet/set-row-style! header-row bold-style)
       (doseq [i (range column-count)]
         (.autoSizeColumn sheet i)))
     wb)))

(defn xlsx-stream ^OutputStream [^XSSFWorkbook wb]
  (with-open [out (ByteArrayOutputStream.)]
    (spreadsheet/save-workbook-into-stream! out wb)
    (ByteArrayInputStream. (.toByteArray out))))

(def- excel-argument-separator \,)

(defn formula-str [formula-name & args]
  (str "="
       (ss/upper-case formula-name)
       "(" (ss/join excel-argument-separator args) ")"))

(defn hyperlink-str [link display-name]
  (formula-str "HYPERLINK" (str \" link \") (str \" display-name \")))

(defn hyperlinks-to-formulas! [wb]
  (doseq [^Sheet sheet (spreadsheet/sheet-seq wb)
          ^Cell cell  (spreadsheet/cell-seq sheet)
          :when (ss/starts-with (.toString cell) "=HYPERLINK")
          :let  [value (.toString cell)]]
    (.setCellFormula cell (subs value 1))))

(defn add-sum-row! [sheet-name wb values]
  (let [sheet (spreadsheet/select-sheet sheet-name wb)]
    (spreadsheet/add-row! sheet values)))

(def xlsx-mime-type "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

(defn excel-response [filename body error-message]
  (try
    {:status  200
     :headers {"Content-Type"        xlsx-mime-type
               "Content-Disposition" (str "attachment;filename=\"" filename "\"")}
     :body    body}
    (catch Exception e#
      (error error-message e#)
      {:status 500})))
