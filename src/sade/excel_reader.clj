(ns sade.excel-reader
  (:require [clojure.java.io :as io]
            [ontodev.excel :as xls]))

(defn read-map
  "Reads the first sheet of the .xlsx file into a map:
  first column forms the keys and the second values.
  Both will always be strings. Note that dates will be represented as
  whole numbers, e.g., 1.1.2014 = '41640' (string)"
  [resource-name]
  (with-open [in (io/input-stream (io/resource resource-name))]
    (let [wb (seq (xls/load-workbook in))
          sheet1 (seq (first wb))
          rows (map xls/read-row sheet1)]
      (reduce #(assoc %1 (first %2) (second %2)) {} rows))))
