(ns sade.excel-reader
  (:require [clojure.java.io :as io]
            [ontodev.excel :as xls]))

(defn with-first-sheet-rows [resource-name f]
  (with-open [in (io/input-stream (io/resource resource-name))]
    (let [wb (seq (xls/load-workbook in))
          sheet (seq (first wb))
          rows (map xls/read-row sheet)]
      (f rows))))

(defn read-map
  "Reads the first sheet of the .xlsx file into a map:
   first column forms the keys and the second values.
   Both will always be strings. Note that dates will be represented as
   whole numbers, e.g., 1.1.2014 = '41640' (string)"
  [resource-name]
  (with-first-sheet-rows resource-name
    (fn [rows] (reduce #(assoc %1 (first %2) (second %2)) {} rows))))

(defn read-table
  "Reads he first sheet of the .xlsx file into a list of maps:
   the first row is interpreted as keys."
  [resource-name]
  (with-first-sheet-rows resource-name
    (fn [rows]
      (let [keys (->> rows first (map keyword) vec)
            data-rows (rest rows)]
        (map (fn [row] (zipmap keys row)) data-rows)))))
