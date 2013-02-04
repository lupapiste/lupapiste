(ns lupapalvelu.i18n
  (:require [clojure.java.io :as io]
            [ontodev.excel :as xls]
            [cheshire.core :as json]))

(defn- load-i18n []
  (->
    (io/resource "i18n.xlsx")
    (xls/load-workbook)
    (xls/read-sheet "Sheet1")))

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
  (str ";loc.terms = " (json/generate-string loc) ";"))