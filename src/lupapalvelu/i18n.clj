(ns lupapalvelu.i18n
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [ontodev.excel :as xls]
            [cheshire.core :as json]
            [sade.env :as env]
            [sade.core :refer :all]
            [lupapiste-commons.i18n.core :as commons]))

(def default-lang :fi)

(defn- add-term [k t result lang]
  (assoc-in result [(keyword lang) k] (s/trim t)))

(defn- process-translation [row k default-t result lang]
  (let [t (get row lang)]
    (if (seq t)
      (add-term k t result lang)
      (add-term k default-t result lang))))

(defn- process-row [langs-but-default result row]
  (let [k (get row "key")
        default-t (get row (name default-lang))]
    (if (and (not (s/blank? k)) default-t)
      (reduce
        (partial process-translation row k default-t)
        (add-term k default-t result default-lang)
        langs-but-default)
      result)))

(defn- read-sheet [headers sheet]
  (->> sheet seq rest (map xls/read-row) (map (partial zipmap headers))))

(defn- load-translations []
  (merge-with conj
              (-> (commons/read-translations (io/resource "shared_translations.txt"))
                  commons/keys-by-language)
              (with-open [in (io/input-stream (io/resource "i18n.xlsx"))]
                (let [wb      (xls/load-workbook in)
                      langs   (-> wb seq first first xls/read-row rest)
                      headers (cons "key" langs)
                      data    (->> wb (map (partial read-sheet headers)) (apply concat))
                      langs-but-default (disj (set langs) (name default-lang))]
                  (reduce (partial process-row langs-but-default) {} data)))))

(def- localizations (atom nil))
(def- excel-data (future (load-translations)))

(defn reload! []
  (if (seq @localizations)
    (reset! localizations (load-translations))
    (reset! localizations @excel-data)))

(defn- get-or-load-localizations []
  (if-not @localizations
    (reload!)
    @localizations))

(defn get-localizations []
  (get-or-load-localizations))

(def languages (-> (get-localizations) keys set))

(defn get-terms
  "Return localization terms for given language. If language is not supported returns terms for default language (\"fi\")"
  [lang]
  (let [terms (get-localizations)]
    (or (terms (keyword lang)) (terms default-lang))))

(defn unknown-term [term]
  (if (env/dev-mode?)
    (str "???" term "???")
    (do
      (errorf "unknown localization term '%s'" term)
      "")))

(defn has-term? [lang & terms]
  (not (nil? (get (get-terms (keyword lang)) (s/join \. terms)))))

(defn localize [lang & terms]
  (let [term (s/join \. (map name terms))]
    (if-let [result (get (get-terms (keyword lang)) term)]
      result
      (unknown-term term))))

(defn localizer [lang]
  (partial localize (keyword lang)))

(def ^:dynamic *lang* nil)
(def ^{:doc "Function that localizes provided term using the current language. Use within the \"with-lang\" block."
       :dynamic true}
  loc
  (fn [& args] (throw (Exception. (str "loc called outside with-lang context, args: " args)))))

(defmacro with-lang [lang & body]
  `(binding [*lang* (keyword ~lang)
             loc (localizer ~lang)]
     ~@body))

(defn lang-middleware [handler]
  (fn [request]
    (let [lang (or (get-in request [:params :lang])
                   (get-in request [:user :lang])
                   (name default-lang))]
      (with-lang lang
        (handler request)))))

(defn read-lines [lines]
  (reduce (fn [m line]
            (if-let [[_ k v] (re-matches #"^(.[^\s]*):\s*(.*)$" line)]
              (assoc m (s/trim k) (s/trim v))
              m))
    {}
    lines))

(defn- load-add-ons []
  (when-let [in (io/resource "i18n.txt")]
    (with-open [in (io/reader in)]
      (read-lines (line-seq in)))))

(env/in-dev
  ;;
  ;; Re-define get-localizations so that i18n.txt is always loaded and merged to excel data.
  ;;
  (defn get-localizations []
    (let [lz (get-or-load-localizations)]
      (update-in lz [default-lang] merge (load-add-ons)))))

