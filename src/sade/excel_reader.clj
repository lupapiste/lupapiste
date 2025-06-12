(ns sade.excel-reader
  (:require [dk.ative.docjure.spreadsheet :as xls]
            [sade.date :as date]
            [sade.util :as util]
            [sade.strings :as ss]))

(defn- process-row [row]
  (let [[k v] (->> (xls/cell-seq row)
                   (map xls/read-cell)
                   ss/trimwalk)]
    [(str k) (util/pcond-> v
               inst? date/zoned-date-time)]))

(defn read-map
  "Reads the first sheet of the .xlsx file into a map: first column forms the keys and the
  second values.  Keys are always strings. Date cells are converted to
  `date/zoned-date-time`s."
  [resource-name]
  (->> (xls/load-workbook-from-resource resource-name)
       (xls/select-sheet (constantly true))
       (xls/row-seq)
       (map process-row)
       (into {})))
