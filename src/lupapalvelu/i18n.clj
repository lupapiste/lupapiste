(ns lupapalvelu.i18n
  (:require [clojure.java.io :as io]
            [ontodev.excel :as xls]
            [cheshire.core :as json]))

(defn- read-sheet [sheet] 
  (let [rows    (seq sheet)
        headers (map xls/to-keyword (xls/read-row (first rows))) 
        data    (map xls/read-row (rest rows))]
    (map (partial zipmap headers) data)))

(defn- load-i18n []
  (->>
    (io/resource "i18n.xlsx")
    (xls/load-workbook)
    (map read-sheet)
    (apply concat)
    (filter (fn [row] (= (count row) 3)))))

(defn- add-term [row result lang]
  (assoc-in result [lang (get row :key)] (get row lang)))

(defn- process-row [languages result row]
  (reduce (partial add-term row) result languages))

(defn- parse []
  (let [data (load-i18n)
        languages (-> data first (dissoc :key) keys)]
    (reduce (partial process-row languages) {} data)))

(def loc (parse))

(defn loc->js []
  (str ";loc.setTerms(" (json/generate-string (parse)) ");"))

(loc->js)