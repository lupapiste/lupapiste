(ns lupapalvelu.reports.excel
  (:require [dk.ative.docjure.spreadsheet :as spreadsheet]
            [sade.core :refer :all]
            [sade.strings :as ss])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream OutputStream)
           (org.apache.poi.xssf.usermodel XSSFWorkbook)
           (org.apache.poi.ss.usermodel CellType)))

(defn ^XSSFWorkbook create-workbook
  ([data sheet-name header-row-content row-fn]
   (create-workbook [{:sheet-name sheet-name
                      :header header-row-content
                      :row-fn row-fn
                      :data data}]))
  ([sheets]
   {:pre [(every? (every-pred :sheet-name :header :row-fn) sheets)]}
   (let [wb (apply spreadsheet/create-workbook
                   (apply concat
                          (reduce (fn [wb-sheets conf]
                                    (conj wb-sheets
                                          [(:sheet-name conf)
                                           (concat [(:header conf)]
                                                   (map (:row-fn conf) (:data conf)))]))
                                  []
                                  sheets)))]
     ; Format each sheet
     (doseq [sheet (spreadsheet/sheet-seq wb)
             :let [header-row   (-> sheet spreadsheet/row-seq first)
                   column-count (.getLastCellNum header-row)
                   bold-style   (spreadsheet/create-cell-style! wb {:font {:bold true}})]]
       (spreadsheet/set-row-style! header-row bold-style)
       (doseq [i (range column-count)]
         (.autoSizeColumn sheet i)))
     wb)))

(defn ^OutputStream xlsx-stream [^XSSFWorkbook wb]
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
  (doseq [sheet (spreadsheet/sheet-seq wb)
          cell  (spreadsheet/cell-seq sheet)
          :when (ss/starts-with (.toString cell) "=HYPERLINK")
          :let  [value (.toString cell)]]
    (doto cell
      (.setCellType CellType/FORMULA)
      (.setCellFormula (subs value 1)))))
