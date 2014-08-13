(ns lupapalvelu.i18n
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [ontodev.excel :as xls]
            [cheshire.core :as json]
            [sade.env :as env]))

(defn- add-term [row result lang]
  (let [k (get row "key")
        t (get row lang)]
    (if (and k t (pos? (.length t)))
      (assoc-in result [(keyword lang) k] (s/trim t))
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
          data    (->> wb (map (partial read-sheet headers)) (apply concat))]
      (reduce (partial process-row langs) {} data))))

(def ^:private excel-data (future (load-excel)))

(defn get-localizations [] @excel-data)

(def languages (-> (get-localizations) keys set))

(defn get-terms
  "Return localization terms for given language. If language is not supported returns terms for default language (\"fi\")"
  [lang]
  (let [terms (get-localizations)]
    (or (terms (keyword lang)) (terms :fi))))

(defn unknown-term [term]
  (if (env/dev-mode?)
    (str "???" term "???")
    (do
      (errorf "unknown localization term '%s'" term)
      "")))

(defn has-term? [lang & terms]
  (not (nil? (get (get-terms (keyword lang)) (s/join \. terms)))))

(defn localize [lang & terms]
  (let [term (s/join \. terms)]
    (if-let [result (get (get-terms (keyword lang)) term)]
      result
      (unknown-term term))))

(defn localizer [lang]
  (partial localize (keyword lang)))

(def ^:dynamic *lang* nil)
(def ^{:doc "Function that localizes provided term using the current language. Use within the \"with-lang\" block."
       :dynamic true}
  loc)

(defmacro with-lang [lang & body]
  `(binding [*lang* (keyword ~lang)
             loc (localizer ~lang)]
     ~@body))

(defn lang-middleware [handler]
  (fn [request]
    (let [lang (or (get-in request [:params :lang])
                   (get-in request [:user :lang])
                   "fi")]
      (with-lang lang
        (handler request)))))

(defn read-lines [lines]
  (reduce (fn [m line]
            (if-let [[_ k v] (re-matches #"^(.[^\s]*):\s*(.*)$" line)]
              (assoc m (s/trim k) (s/trim v))
              m))
    {}
    lines))

(env/in-dev

  ;;
  ;; Re-define get-localizations so that i18n.txt is always loaded and merged to excel data.
  ;;

  (defn- load-add-ons []
    (when-let [in (io/resource "i18n.txt")]
      (with-open [in (io/reader in)]
        (read-lines (line-seq in)))))

  (defn get-localizations []
    (update-in @excel-data [:fi] merge (load-add-ons))))
