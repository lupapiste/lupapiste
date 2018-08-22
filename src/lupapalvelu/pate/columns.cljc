(ns lupapalvelu.pate.columns
  (:require #?(:clj  [lupapalvelu.i18n :as i18n]
               :cljs [lupapalvelu.ui.common :as common])
            #?(:clj  [lupapalvelu.pate.date :as date])
            #?(:clj  [lupapalvelu.domain :as domain])
            [clojure.string :as s]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [sade.shared-util :as util]
            [schema.core :refer [defschema] :as sc]))

#?(:clj (def finnish-date date/finnish-date)
   :cljs (def finnish-date common/format-timestamp))

#?(:clj (def localize i18n/localize)
   :cljs (defn localize [_ term] (common/loc term)))

(defn pathify [kw-path]
  (map keyword (s/split (name kw-path) #"\.")))

#?(:clj (defn doc-value [application doc-name kw-path]
          (get-in (domain/get-document-by-name application (name doc-name))
                  (cons :data (pathify kw-path))))
   :cljs (defn doc-value [& _]))

(defn- verdict-schema [{:keys [category schema-version legacy?]}]
  (if legacy?
    (legacy/legacy-verdict-schema category)
    (verdict-schemas/verdict-schema category schema-version)))

(defn dict-value
  "Dictionary value for the given kw-path. Options can either
  have :verdict key or be verdict itself. The returned values are
  transformed into friendly formats (e.g., timestamp -> date)."
  [options kw-path]
  (let [{data :data :as opts} (get options :verdict options)
        path                  (pathify kw-path)
        value                 (get-in data path)
        {schema :schema}      (schema-util/dict-resolve path
                                                        (:dictionary
                                                         (verdict-schema opts)))]
    (cond
      (and (:phrase-text schema) (not (s/blank? value)))
      (list [:div.markup (markup/markup->tags value)])

      (and (:date schema) (integer? value))
      (finnish-date value)

      :else
      value)))

(defn add-unit
  "Result is nil for blank value."
  [lang unit v]
  (when-not (s/blank? (str v))
    (case unit
      :ha      (str v " " (localize lang :unit.hehtaaria))
      :m2      [:span v " m" [:sup 2]]
      :m3      [:span v " m" [:sup 3]]
      :kpl     (str v " " (localize lang :unit.kpl))
      :section (str "\u00a7" v)
      :eur     (str v "\u20ac"))))

(defn resolve-source
  [{:keys [application] :as data} {doc-source  :doc
                                   dict-source :dict
                                   :as         source}]
  (cond
    doc-source  (apply doc-value (cons application doc-source))
    dict-source (dict-value data dict-source)
    :else       (get data source)))

(defn resolve-class [all selected & extra]
  (->> (util/intersection-as-kw all (flatten [selected]))
       (concat extra)
       (remove nil?)))

(defn resolve-cell [{:keys [lang]} source-value {:keys [text width unit loc-prefix styles] :as cell}]
  (let [path (some-> cell :path pathify)
        class (resolve-class layouts/cell-styles
                             styles
                             (when width (str "cell--" width))
                             (or (when (seq path)
                                   (get-in source-value
                                           (cons ::styles path)))
                                 (get-in source-value
                                         [::styles ::cell])))
        blank-as-nil #(when-not (s/blank? %) %)
        value (or text (util/pcond-> (get-in source-value path source-value)
                                     string? blank-as-nil))]
    (when value
      [:div.cell (cond-> {}
                   (seq class) (assoc :class class))
       (cond->> value
         loc-prefix (localize lang loc-prefix)
         unit (add-unit lang unit))])))

(defn post-process [value post-fn]
  (cond-> value
    (and value post-fn) post-fn))

(defn entry-row
  [left-width {:keys [lang] :as data} [{:keys [loc loc-many source post-fn styles]} & cells]]
  (let [source-value (post-process (util/pcond-> (resolve-source data source)
                                                 string? s/trim)
                                   post-fn)
        multiple?    (and (sequential? source-value)
                          (> (count source-value) 1))]
    (when (or (nil? source) (not-empty source-value))
      (let [section-styles  [:page-break :border-top :border-bottom]
            row-styles      (resolve-class layouts/row-styles styles
                                           (get-in source-value [::styles :row]))
            multiple-cells? (or (> (count cells) 1) multiple?)
            cell-values     (if multiple-cells?
                              (remove nil?
                                      (for [v    (if (sequential? source-value)
                                                   source-value
                                                   (vector source-value))
                                            :let [value (map (partial resolve-cell data v)
                                                             (if (empty? cells) [{}] cells))]]
                                        (when-not (every? nil? value)
                                          [:div.row
                                           {:class (get-in v [::styles :row])}
                                           value])))
                              (resolve-cell data
                                            (util/pcond-> source-value
                                                          sequential? first)
                                            (first cells)))]

        (when-not (empty? cell-values)
          [:div.section
           {:class (util/intersection-as-kw section-styles row-styles)}
           [:div.row
            {:class (util/difference-as-kw row-styles section-styles)}
            [:div.cell
             {:class (resolve-class [:bold] styles (when left-width
                                                     (str "cell--" left-width)))}
             (localize lang (if multiple? (or loc-many loc) loc))]
            (if multiple-cells?
              [:div.cell
               [:div.section
                cell-values]]
              cell-values)]])))))

(defn content
  [data {:keys [left-width entries]}]
  (->> entries
       (map (partial entry-row left-width data))
       (filter not-empty)))
