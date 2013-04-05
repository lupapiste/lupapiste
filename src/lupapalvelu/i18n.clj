(ns lupapalvelu.i18n
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [ontodev.excel :as xls]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [sade.env :as env]))

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

(defn- load-excel []
  (with-open [in (io/input-stream (io/resource "i18n.xlsx"))]
    (let [wb      (xls/load-workbook in)
          langs   (-> wb seq first first xls/read-row rest)
          headers (cons "key" langs)
          data (->> wb (map (partial read-sheet headers)) (apply concat))]
      (reduce (partial process-row langs) {} data))))

(def ^:private excel-data (load-excel))

(defn get-localizations [] excel-data)

(defn get-terms
  "Return localization temrs for given language. If language is not supported returns terms for default language (\"fi\")"
  [lang]
  (let [terms (get-localizations)]
    (or (terms lang) (terms "fi"))))

(defn unknown-term [term]
  (if (env/dev-mode?)
    (str "???" term "???")
    (do
      (log/errorf "unknown localization term '%s'" term)
      "")))

(defn localize
  "Localize \"term\" using given \"terms\". If \"term\" is unknown, return term surrounded with triple question marks."
  [terms term]
  (or (terms term) (str "???" term "???")))

(defn localizer [lang]
  (partial localize (get-terms lang)))

(def ^:dynamic *lang* nil)
(def ^{:doc "Function that localizes provided term using the current language. Use within the \"with-lang\" block."
       :dynamic true}
  loc)

(defmacro with-lang [lang & body]
  `(binding [loc (localizer ~lang)]
     ~@body))

(defn lang-middleware [handler]
  (fn [request]
    (let [lang (or (get-in request [:params :lang])
                   (get-in request [:user :lang])
                   "fi")]
      (binding [*lang* lang
                loc (localizer lang)]
        (handler request)))))

(env/in-dev

  ;;
  ;; Re-define get-localizations so that i18n.txt is always loaded and merged to excel data.
  ;;

  (defn- load-add-ons []
    (when-let [in (io/resource "i18n.txt")]
      (with-open [in (io/reader in)]
        (reduce (fn [m line]
                  (if-let [[_ k v] (re-matches #"^([^:]+):\s*(.*)$" line)]
                    (assoc m (s/trim k) (s/trim v))
                    m))
                {}
                (line-seq in)))))

  (defn get-localizations []
    (assoc excel-data "fi" (merge (get excel-data "fi") (load-add-ons)))))
