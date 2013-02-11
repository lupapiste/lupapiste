(ns lupapalvelu.i18n
  (:use [lupapalvelu.env :only [dev-mode?]])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [ontodev.excel :as xls]
            [cheshire.core :as json]))

(defn- add-term [row result lang]
  (let [k (get row "key")
        t (get row lang)]
    (if (and k t (not (s/blank? t)))
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

(def ^{:doc "Function that returns the localization terms map. Keys are languages (like \"fi\") and values are maps of localization terms."}
  ; In dev mode this is mapped to parse function, so we parse terms from excel source _always_. In prod we cache paring result.
  get-localizations (if (dev-mode?) parse (constantly (parse))))

(defn get-terms
  "Return localization temrs for given language. If language is not supported returns terms for default language (\"fi\")"
  [lang]
  (let [terms (get-localizations)]
    (or (terms lang) (terms "fi"))))

(defn localize
  "Localize \"term\" using given \"terms\". If \"term\" is unknown, return term surrounded with triple question marks."
  [terms term]
  (or (terms term) (str "???" term "???")))

(def ^{:doc "Function that localizes provided term using the current language. Use within the \"with-lang\" block."
       :dynamic true}
  loc)

(defmacro with-lang [lang & body]
  `(binding [loc (partial localize (get-terms ~lang))]
     ~@body))
