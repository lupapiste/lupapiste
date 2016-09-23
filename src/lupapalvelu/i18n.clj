(ns lupapalvelu.i18n
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [clj-time.format :as timef]
            [clj-time.core :as time]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [cheshire.core :as json]
            [ontodev.excel :as xls]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapiste-commons.i18n.core :as commons]
            [lupapiste-commons.i18n.resources :as commons-resources]))

(def supported-langs (if (env/feature? :english)
                       [:fi :sv :en]
                       [:fi :sv]))
(def default-lang (first supported-langs))

(defn- read-translations-txt [name-or-file]
  (let [resource (if (instance? java.io.File name-or-file)
                   name-or-file
                   (io/resource name-or-file))]
    (commons/keys-by-language (commons/read-translations resource))))

(defn i18n-localizations
  "Reads all .txt files from i18n/ resource path.
   Returns them as collection of translation maps, where key is language
   and value is map of loc-key - loc-value pairs"
  []
  (let [this-path (util/this-jar lupapalvelu.main)
        i18n-files (if (ss/ends-with this-path ".jar")      ; are we inside jar
                     (filter #(ss/ends-with % ".txt") (util/list-jar this-path "i18n/"))
                     (util/get-files-by-regex "resources/i18n/" #".+\.txt$")) ; dev
        i18n-files (if (every? string? i18n-files)          ; from jar, filenames are strings
                     (map (partial str "i18n/") i18n-files)
                     i18n-files)]
    (map read-translations-txt i18n-files)))

(defn- load-translations []
  (apply merge-with conj
         (read-translations-txt "shared_translations.txt")
         (i18n-localizations)))

(def- localizations (atom nil))
(def- excel-data (util/future* (load-translations)))

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

(def languages (-> supported-langs set))

(def supported-language-schema (apply sc/enum (map name languages)))

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

(defn- terms->term [terms] (s/join \. (map #(if (nil? %) "" (name %)) (flatten terms))))

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

(defn localize [lang & terms]
  (if-let [result (get (get-terms (keyword lang)) (terms->term terms))]
    result
    (apply unknown-term terms)))

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

(defn ensure-no-duplicate-keys! [loc-maps]
  (let [keys (mapcat (comp keys :translations) loc-maps)
        sources-and-keys (map (comp (juxt (comp :source-name meta)
                                          identity))
                              keys)
        keys (map second sources-and-keys)]

    (when (not (apply distinct? keys))
      (let [duplicates (map first
                            (filter #(> (second %) 1)
                                    (frequencies keys)))]
        (throw (ex-info
                "The same key appears in multiple sources"
                {:duplicate-keys (->> sources-and-keys
                                      (filter (comp (set duplicates)
                                                    second))
                                      (sort-by second))}))))))

(defn- merge-localization-maps [loc-maps]
  (ensure-no-duplicate-keys! loc-maps)
  {:languages    (distinct (apply concat (map :languages loc-maps)))
   :translations (apply merge-with conj (map :translations loc-maps))})

(defn- txt-files->map [files]
  (->> files
       (map commons-resources/txt->map)
       merge-localization-maps))

(defn- default-i18n-files []
  (util/get-files-by-regex (io/resource "i18n/") #".+\.txt$"))

(defn missing-translations [localization-map lang]
  (update (commons-resources/missing-translations localization-map
                                                  (keyword lang))
          :translations
          (util/fn->> (remove (comp ss/blank? :fi second))
                      (sort-by first))))

(defn missing-localizations-excel
  "Writes missing localizations of given language to excel file.
   If file is not provided, will create the file to user home dir."
  ([lang]
   (let [date-str (timef/unparse (timef/formatter "yyyyMMdd") (time/now))
         filename (str (System/getProperty "user.home")
                       "/lupapiste_translations_"
                       date-str
                       ".xlsx")]
        (missing-localizations-excel (io/file filename) lang)))
  ([file lang]
   (-> (default-i18n-files)
       (txt-files->map)
       (missing-translations lang)
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
                                 (contains-no-translations? k-new v-new lang) [k v]
                                 (contains-unexpected-languages? v-new lang)
                                 (throw (ex-info "new translation map contains unexpected language(s)"
                                                 {:expected-language lang
                                                  :translations      {k-new v-new}}))

                                 (nil? (:fi v))
                                 (throw (ex-info "Finnish text not found in the source"
                                                 {:source v
                                                  :new    v-new}))


                                 (not= (:fi v) (:fi v-new))
                                 (throw (ex-info "Finnish text used for translation does not match the one found in current source"
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
  (let [file (io/file path)]
    (with-open [in (io/input-stream file)]
      (let [wb (xls/load-workbook in)
            sheets (seq wb)]
        (apply commons/merge-translations (map commons-resources/sheet->map sheets))))))

(defn- merge-translation-from-excel [acc {:keys [languages] :as translation-map}]
  (assert (= (count languages) 2)
          (str "Actual languages" languages))
  (let [lang (first (remove (partial = :fi) languages))]
    (merge-new-translations acc
                            translation-map
                            lang)))

(defn merge-translations-from-excels-into-source-files [translation-files-dir-path paths]
  "Merges translation excel files into the current translation source files."
  (let [translation-txt-files (util/get-files-by-regex translation-files-dir-path
                                                       #".+\.txt$")
        current-loc-map (-> translation-txt-files (txt-files->map))
        translation-maps (map read-translation-excel paths)
        new-loc-map (reduce merge-translation-from-excel
                            current-loc-map
                            translation-maps)]
    (doseq [[filepath translations] (group-translations-by-source new-loc-map)]
      (commons-resources/write-txt {:translations (for [[k v] translations]
                                                    [k (sort v)])}
                                   (io/file translation-files-dir-path
                                            filepath)))))

(defn merge-translations-from-excels
  "Merges translation excel files from paths to one translation txt file. Uses commons/merge-translations."
  [& paths]
  (let [dir (.getParent (io/file (first paths)))]
    (commons-resources/write-txt
      (apply
        commons/merge-translations
        (for [path paths]
          (read-translation-excel path)))
      (io/file dir (str "merged_translations_" (now) ".txt")))))
