(ns lupapalvelu.i18n
  (:require [clj-time.core :as time]
            [clj-time.format :as timef]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [lupapiste-commons.i18n.core :as commons]
            [lupapiste-commons.i18n.resources :as commons-resources]
            [lupapiste-commons.i18n.txt-resources :as commons-txt-resources]
            [mount.core :as mount :refer [defstate]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [errorf warn]])
  (:import [java.io File]))

;;
;; Supported languages
;;

(def all-languages [:fi :sv :en])

(def supported-langs (if (env/feature? :english)
                       [:fi :sv :en]
                       [:fi :sv]))
(def default-lang (first supported-langs))

(def all-languages-with-optional
  [:fi :sv (sc/optional-key :en)])

(defn supported-lang? [lang]
  (contains? (set supported-langs) (keyword lang)))

(def languages (-> supported-langs set))

(defn supported-langs-map
  "Return a map {:a (f :a) :b (f :b) ... } where :a, :b, ... are the supported languages"
  [f]
  (into {} (map (juxt identity f)
                supported-langs)))

(defn localization-schema
  "Return a map {:a value-type :b value-type ... } where :a, :b, ... are the supported languages"
  [value-type]
  (supported-langs-map (constantly value-type)))

(defn lenient-localization-schema [value-type]
  (zipmap all-languages-with-optional (repeat value-type)))

(sc/defschema EnumSupportedLanguages (apply sc/enum (map name languages)))
(def Lang
  "Language schema that supports both strings and keywords (e.g., \"fi\" vs. :fi)"
  (sc/pred supported-lang? "Supported language"))

;;
;; Loading translations from files
;;

(def common-translations-filename "shared_translations.txt")

(defn- read-translations-txt [name-or-file]
  (let [resource (if (instance? File name-or-file)
                   name-or-file
                   (io/resource name-or-file))]
    (commons/keys-by-language (commons/read-translations resource))))

(defn i18n-localizations
  "Reads all .txt files from i18n/ resource path.
   Returns them as collection of translation maps, where key is language
   and value is map of loc-key - loc-value pairs"
  []
  (let [;; HACK: `(eval '` is a workaround for cljsbuild in uberjar:
        this-path (util/this-jar (eval 'lupapalvelu.main))
        i18n-files (if (ss/ends-with this-path ".jar")      ; are we inside jar
                     (filter #(ss/ends-with % ".txt") (util/list-jar this-path "i18n/"))
                     (util/get-files-by-regex "resources/i18n/" #".+\.txt$")) ; dev
        i18n-files (if (every? string? i18n-files)          ; from jar, filenames are strings
                     (map (partial str "i18n/") i18n-files)
                     i18n-files)]
    (map read-translations-txt i18n-files)))

(defn- load-translations []
  (apply merge-with conj
         (read-translations-txt common-translations-filename)
         (i18n-localizations)))

(defstate localizations
  :start (load-translations))

(defn get-localizations []
  ;; HACK: There are a bajillion things, mostly tests, that expect to use i18n without explicitly loading translations
  ;;       first. So we have to do this Singleton-style lazy loading with `mount/start`:
  (mount/start #'localizations)
  localizations)

(defn reload! []
  (mount/stop #'localizations)
  (mount/start #'localizations))

(defn valid-language
  "Input validator for lang parameter. Accepts also empty lang."
  [{{:keys [lang]} :data}]
  (when-not (or (ss/blank? lang)
                (->> lang ss/lower-case keyword (contains? (set languages))))
    (fail :error.unsupported-language)))

(defn get-terms
  "Return localization terms for given language. If language is not supported returns terms for default language (\"fi\")"
  [lang]
  (let [terms (get-localizations)]
    (or (terms (keyword lang)) (terms default-lang))))

(defn- terms->term [terms] (s/join \. (map #(if (nil? %) "" (ss/->plain-string %)) (flatten terms))))

(defn unknown-term [& terms]
  (let [term (terms->term terms)]
    (errorf "unknown localization term '%s', parameters were %s" term terms)
    (if (env/dev-mode?)
      (str "???" term "???")
      "")))

(defn has-term? [lang & terms]
  (not (nil? (get (get-terms (keyword lang)) (terms->term terms)))))

(defn has-exact-term?
  "True only if the term is defined for the given language."
  [lang & terms]
  (not (nil? (get ((get-localizations) (keyword lang)) (terms->term terms)))))

(defn try-localize
  "Tries to find localization in language lang for terms. If fails, applies fail-fn to lang and terms."
  [fail-fn lang terms]
  (if-let [result (get (get-terms (keyword lang)) (terms->term terms))]
    result
    (fail-fn lang terms)))

(defn ^String localize [lang & terms]
  (try-localize (fn [_ terms] (apply unknown-term terms)) lang terms))

(defn localizer [lang]
  (partial localize (keyword lang)))

(defn localize-fallback
  "Returns first translation found for lang in terms.
   [fallback]: if not found tries with the fallback language. Default
  language is the ultimate fallback. When even that fails, the first
  term localisation with the default language is returned (to ensure
  the flagging of missing term). Note: terms can be vectors."
  [lang terms & [fallback]]
  {:pre [(or (not lang) (util/not=as-kw lang fallback))]}
  (let [lang (or lang default-lang)
        terms (if (string? terms) [terms] terms)]
    (if-let [term (util/find-first (partial has-exact-term? lang)
                                   terms)]
     (localize lang term)
     (if-let [fallback (or fallback (and (util/not=as-kw lang default-lang)
                                           default-lang))]
       (localize-fallback fallback terms)
       (localize default-lang (first terms))))))

(def ^:dynamic *lang* nil)
(def ^{:doc "Function that localizes provided term using the current language. Use within the \"with-lang\" block."
       :dynamic true}
  loc
  (fn [& args] (throw (Exception. (str "loc called outside with-lang context, args: " args)))))

(defmacro with-lang [lang & body]
  `(binding [*lang* (keyword ~lang)
             loc (localizer ~lang)]
     ~@body))

(defn with-default-localization [loc-map default]
  (merge (localization-schema default)
         loc-map))

(defn localize-and-fill
  "Fills {n} markers in the loc-string with values. For example, if
  English loc-string for a term 'hello' would be 'Hi there {0}!',
  then (localize-and-fill :en :hello 'Bob' ) => 'Hi there Bob!'.
    lang: keyword or string.
    term: string, keyword or sequence of string/keywords.
    values: Sequence of subsitute values (keyword/string/number)."
  [lang term & values]
  (let [s (localize lang (if (sequential? term)
                           term
                           (ss/split (name term) #"\.")))]
    (reduce (fn [acc i]
              (ss/replace acc
                          (format "{%s}" i)
                          (ss/->plain-string (nth values i))))
            s
            (range (count values)))))

(defn to-lang-map
  "Returns map, where keys are languages."
  [loc-fn-or-key]
  (letfn [(localize-function [lang]
            (if (ifn? loc-fn-or-key)
              (loc-fn-or-key lang)
              (localize lang loc-fn-or-key)))]
    (reduce
      #(assoc %1 (keyword %2) (localize-function %2))
      {}
      supported-langs)))

;;
;; Middleware
;;

(defn lang-middleware [handler]
  (fn [request]
    (let [lang (or (get-in request [:params :lang])
                   (get-in request [:user :language])
                   (name default-lang))]
      (with-lang lang
        (handler request)))))


;;
;; Create missing translation excels and merge them back into translation files
;;

(defn read-lines [lines]
  (reduce (fn [m line]
            (if-let [[_ k v] (re-matches #"^(.[^\s]*):\s*(.*)$" line)]
              (assoc m (s/trim k) (s/trim v))
              m))
    {}
    lines))

(defn ensure-no-duplicate-keys! [loc-maps]
  (let [keys (mapcat (comp keys :translations) loc-maps)
        sources-and-keys (map (comp (juxt (comp :source-name meta)
                                          identity))
                              keys)
        keys (map second sources-and-keys)]
    (when (empty? keys) (throw (ex-info "Could not find resource files, check your path is correct" {})))
    (when (not (apply distinct? keys))
      (let [duplicates (map first
                            (filter #(> (second %) 1)
                                    (frequencies keys)))]
        (throw (do (println (set duplicates)) (ex-info
                "The same key appears in multiple sources"
                {:duplicate-keys (->> sources-and-keys
                                      (filter (comp (set duplicates)
                                                    second))
                                      (sort-by second))})))))))

(defn- merge-localization-maps [loc-maps]
  (ensure-no-duplicate-keys! loc-maps)
  {:languages    (distinct (apply concat (map :languages loc-maps)))
   :translations (apply merge-with conj (map :translations loc-maps))})

(defn- txt-files->map [files]
  (->> files
       (map commons-txt-resources/txt->map)
       merge-localization-maps))

(defn- default-i18n-files [& [options]]
  (->> (util/get-files-by-regex (io/resource "i18n/") #".+\.txt$")
       (remove (fn [^File file]
                 (when (:exclude options)
                   (some #(when (re-matches % (.getName file))
                            (do (println (.getName file) "matched" % "and is excluded")
                                true))
                         (:exclude options)))))))

(defn missing-translations [localization-map lang]
  (update (commons-txt-resources/missing-translations localization-map
                                                      (keyword lang))
          :translations
          (util/fn->> (remove (comp ss/blank? :fi second))
                      (sort-by first))))

(defn missing-localizations-excel-file
  "Writes missing localizations of given language to excel file to
  a given file"
  [file lang & [options]]
  (-> (default-i18n-files options)
      (txt-files->map)
      (missing-translations lang)
      (commons-resources/write-excel file)))

(sc/defn ^:always-validate missing-localizations-excel
  "Writes missing localizations of given language to excel file to
  user home dir.

  Possible options
  - exclude: regexes that cause a translation file to be excluded on match"
  ([lang :- EnumSupportedLanguages]
   (missing-localizations-excel lang nil))
  ([lang    :- EnumSupportedLanguages
    options :- (sc/maybe {:exclude [sc/Regex]})]
   (if (= lang "fi")
     (println "Oops, this does not work with Finnish as the target language.")
     (let [date-str (timef/unparse (timef/formatter "yyyyMMdd") (time/now))
           filename (str (System/getProperty "user.home")
                         "/lupapiste_translations_"
                         date-str "_" (name lang)
                         ".xlsx")]
       (missing-localizations-excel-file (io/file filename) lang options)
       (println "Missing localizations written to excel file in:")
       (println filename)))))

(defn- all-localizations-excel
  ([] (all-localizations-excel (default-i18n-files)))
  ([source-files]
    (let [date-str (timef/unparse (timef/formatter "yyyyMMdd") (time/now))
          filename (str (System/getProperty "user.home") "/lupapiste_translations_all_" date-str ".xlsx")]
      (all-localizations-excel source-files (io/file filename))))
  ([source-files file]
   (-> source-files
       (txt-files->map)
       (commons-resources/write-excel file))))

(defn- contains-no-translations? [k-new v-new lang]
  (when (= "" (get v-new lang ""))
    (warn (str "No translations for key " k-new))
    true))

(defn- contains-unexpected-languages? [v-new lang]
  (not= (set (keys v-new)) #{:fi lang}))

; merge-with is not used because the translation maps from commons-resources are
; actually ordered maps, where normal merge with vanilla Clojure map does not
; play nice with key metadata.
(defn merge-new-translations [source new lang]
  {:languages    (distinct (apply concat (map :languages [source new])))
   :translations (into {}
                       (for [[k v] (:translations source)]
                         (let [[k-new v-new] (find (:translations new) k)]
                           (cond (nil? v-new) [k v]
                                 (contains-unexpected-languages? v-new lang)
                                 (throw (ex-info "new translation map contains unexpected language(s)"
                                                 {:expected-language lang
                                                  :translations      {k-new v-new}}))

                                 (contains-no-translations? k-new v-new lang) [k v]

                                 (nil? (:fi v))
                                 (throw (ex-info "Finnish text not found in the source"
                                                 {:source v
                                                  :new    v-new}))


                                 (not= (:fi v) (:fi v-new))
                                 (throw (ex-info (str "Finnish text \"" (:fi v-new)
                                                      "\" used for translation does not match the one found in current source \""
                                                      (:fi v) "\"")
                                                 {:source {k v}
                                                  :new    {k-new v-new}}))

                                 :else [k (merge v v-new)]))))})

(defn- sort-by-translation-entry [map-of-translations]
  (into {}
        (for [[k v] map-of-translations]
          [k (sort-by first v)])))

(defn group-translations-by-source [localization-map]
  (->> localization-map
       :translations
       (group-by (comp :source-name meta first))
       (sort-by-translation-entry)))

(defn- read-translation-excel [path]
  (commons/merge-translations (commons-resources/excel->map path)))

(defn- merge-translation-from-excel [acc {:keys [languages] :as translation-map}]
  (assert (= (count languages) 2)
          (str "Actual languages" languages))
  (let [lang (first (remove (partial = :fi) languages))]
    (merge-new-translations acc
                            translation-map
                            lang)))

(defn merge-translations-from-excels-into-source-files
  "Merges translation excel files into the current translation source files."
  [translation-files-dir-path paths]
  (let [translation-txt-files (util/get-files-by-regex translation-files-dir-path
                                                       #".+\.txt$")
        current-loc-map (-> translation-txt-files (txt-files->map))
        translation-maps (map read-translation-excel paths)
        new-loc-map (reduce merge-translation-from-excel
                            current-loc-map
                            translation-maps)]
    (doseq [[filepath translations] (group-translations-by-source new-loc-map)]
      (commons-txt-resources/write-txt {:translations (for [[k v] translations]
                                                    [k (sort v)])}
                                   (io/file translation-files-dir-path
                                            filepath)))))

(defn merge-translations-from-excels
  "Merges translation excel files from paths to one translation txt file. Uses commons/merge-translations."
  [& paths]
  (let [dir (.getParent (io/file (first paths)))]
    (commons-txt-resources/write-txt
      (apply
        commons/merge-translations
        (for [path paths]
          (read-translation-excel path)))
      (io/file dir (str "merged_translations_" (now) ".txt")))))
