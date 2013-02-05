(ns lupapalvelu.i18n
  (:require [clojure.java.io :as io]
            [ontodev.excel :as xls]
            [cheshire.core :as json]))

(defn- add-term [row result lang]
  (let [k (get row "key")
        t (get row lang)]
    (if (and k t)
      (assoc-in result [lang k] t)
      result)))

(defn- process-row [languages result row]
  (reduce (partial add-term row) result languages))

(defn- read-sheet [headers sheet]
  (->> sheet seq rest (map xls/read-row) (map (partial zipmap headers))))

(defn- load-i18n []
  (with-open [in (io/input-stream (io/resource "i18n.xlsx"))]
    (let [wb      (xls/load-workbook in)
          langs   (-> wb seq first first xls/read-row rest)
          headers (cons "key" langs)
          data    (->> wb (map (partial read-sheet headers)) (apply concat))]
      [langs data])))

(defn- parse []
  (let [[languages data] (load-i18n)]
    (reduce (partial process-row languages) {} data)))

(def loc (parse))

(defn loc->js []
  (str ";loc.setTerms(" (json/generate-string (parse)) ");"))
