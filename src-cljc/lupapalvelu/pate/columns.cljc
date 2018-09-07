(ns lupapalvelu.pate.columns
  "Schema and data driven layout mechanism for static data. Used for
  creating verdict hard copies. Refactoring into cljc was a bit
  premature, but might come handy in the future."
  (:require [sade.shared-strings :as ss]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [sade.shared-util :as util]
            [schema.core :refer [defschema] :as sc]))


(defn pathify [kw-path]
  (map keyword (ss/split (name kw-path) #"\.")))

#?(:clj (defn doc-value [application doc-name kw-path]
          (-> (->> (:documents application)
                   (filter #(= (-> % :schema-info :name) (name doc-name)))
                   first)
              (get-in (cons :data (pathify kw-path)))))
   :cljs (defn doc-value "Doc values not supported on frontend" [& _]))

(defn join-non-blanks
  "Trims and joins."
  [separator & coll]
  (->> coll
       flatten
       (map ss/trim)
       (remove ss/blank?)
       (ss/join separator)))

(defn loc-non-blank
  "Localized string or nil if the last part is blank."
  [lang & parts]
  (when-not (-> parts last ss/blank?)
    (layouts/localize lang parts)))

(defn loc-fill-non-blank
  "Localize and fill if every value is non-blank"
  [lang loc-key & values]
  (when (every? (comp not ss/blank? str) values)
    (apply (partial layouts/localize-and-fill lang loc-key) values)))

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
      (and (:phrase-text schema) (not (ss/blank? value)))
      (list [:div.markup (markup/markup->tags value)])

      (and (:date schema) (integer? value))
      (layouts/finnish-date (long value))

      :else
      value)))

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
       (remove nil?)
       (map name)))

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
        blank-as-nil #(when-not (ss/blank? %) %)
        value (or text (util/pcond-> (get-in source-value path source-value)
                                     string? blank-as-nil))]
    (when value
      [:div.cell (cond-> {}
                   (seq class) (assoc :class class))
       (cond->> value
         loc-prefix (layouts/localize lang loc-prefix)
         unit (layouts/add-unit lang unit))])))

(defn post-process [value post-fn]
  (cond-> value
    (and value post-fn) post-fn))

(defn resolve-loc-rule [loc-rule data]
  (let [rule-kw (into [] (util/split-kw-path (:rule loc-rule)))
        rule-value (get-in data rule-kw)
        rule-key (when (and (:key loc-rule) rule-value)
                   (keyword (str (name (:key loc-rule)) "." (name rule-value))))]
    (if (and rule-key (layouts/has-term? (:lang data) rule-key))
      rule-key
      (:key loc-rule))))

(defn safe-not-empty [a]
  (when (or (number? a) (not-empty a))
    a))

(defn entry-row
  [left-width
   {:keys [lang] :as data}
   [{:keys [loc loc-many source post-fn styles loc-rule id]}
    & cells]]
  (let [source-value (post-process (util/pcond-> (resolve-source data source)
                                                 string? ss/trim)
                                   post-fn)
        multiple?    (and (sequential? source-value)
                          (> (count source-value) 1))]
    (when (or (nil? source) (safe-not-empty source-value))
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
                                            (first cells)))
            loc             (if loc-rule (resolve-loc-rule loc-rule data) loc)]

        (when-not (empty? cell-values)
          [:div.section
           (util/filter-map-by-val some?
                                   {:class (util/intersection-as-kw section-styles row-styles)
                                    :id id})
           [:div.row
            {:class (util/difference-as-kw row-styles section-styles)}
            [:div.cell
             {:class (resolve-class [:bold] styles (when left-width
                                                     (str "cell--" left-width)))}
             (layouts/localize lang (if multiple? (or loc-many loc) loc))]
            (if multiple-cells?
              [:div.cell
               [:div.section
                cell-values]]
              cell-values)]])))))

(defn content
  [data {:keys [left-width entries]}]
  (->> entries
       (map (partial entry-row left-width data))
       (filter safe-not-empty)))

;; Shared verdict properties

(defn complexity [{lang :lang :as options}]
  (safe-not-empty (filter safe-not-empty
                     [(loc-non-blank lang
                                     :pate.complexity
                                     (dict-value options :complexity))
                      (dict-value options :complexity-text)])))

(defn references-included? [{:keys [verdict]} kw]
  (get-in verdict [:data (keyword (str (name kw) "-included"))]))

(defn references [{:keys [lang verdict] :as options} kw]
  (when (references-included? options kw)
    (let [ids (dict-value options kw)]
     (->> (get-in verdict [:references kw])
          (filter #(util/includes-as-kw? ids (:id %)))
          (map (keyword lang))
          sort))))

(defn review-info [options]
  (when (references-included? options :reviews)
    (dict-value options :review-info)))

(defn conditions [options]
  (let [tags (->> (dict-value options :conditions)
                  (map (fn [[k v]]
                         {:id   (name k)
                          :text (ss/trim (:condition v))}))
                  (remove (comp ss/blank? :text))
                  (sort-by :id)
                  (map (comp markup/markup->tags :text)))]
    (when (seq tags)
      ;; Extra "layer" needed for proper entry-row layout.
      [[:div.markup tags]])))

(defn statements [{lang :lang :as options}]
  (->> (dict-value options :statements)
       (filter :given)
       (map (fn [{:keys [given text status]}]
              (join-non-blanks ", "
                               text
                               (layouts/finnish-date given)
                               (layouts/localize lang :statement status))))
       safe-not-empty))

(defn collateral [{:keys [lang] :as options}]
  (when (dict-value options :collateral-flag)
    (join-non-blanks ", "
                     [(layouts/add-unit lang :eur (dict-value options
                                                              :collateral))
                      (loc-non-blank lang :pate.collateral-type
                                     (dict-value options
                                                      :collateral-type))
                      (dict-value options :collateral-date)])))

(defn muutoksenhaku [{lang :lang :as options}]
  (loc-fill-non-blank lang
                      :pdf.not-later-than
                      (dict-value options :muutoksenhaku)))

(defn voimassaolo [{lang :lang :as options}]
  (loc-fill-non-blank lang
                      :pdf.voimassa.text
                      (dict-value options :aloitettava)
                      (dict-value options :voimassa)))

(defn voimassaolo-ya [{lang :lang :as options}]
  (loc-fill-non-blank lang
                      :pdf.voimassaolo-ya
                      (dict-value options :start-date)
                      (dict-value options :end-date)))

(defn handler
  "Handler with title (if given)"
  [options]
  (if (util/=as-kw :ya (get-in options [:verdict :category]))
    (let [title (-> (assoc-in options
                              [:verdict :data :handler-titles]
                              [(get-in options [:verdict :data :handler-title])])
                    (references :handler-titles)
                    (first))
          handler (->> (dict-value options :handler)
                       (ss/trim))]
      (if (ss/blank? title)
        handler
        (str title " " handler)))
    (->> [:handler-title :handler]
         (map (partial dict-value options))
         (map ss/trim)
         (remove ss/blank?)
         (ss/join " "))))

(defn verdict-properties [options]
  (assoc options
         :complexity (complexity options)
         :reviews (references options :reviews)
         :review-info (review-info options)
         :plans   (references options :plans)
         :conditions (conditions options)
         :statements (statements options)
         :collateral (collateral options)
         :muutoksenhaku (muutoksenhaku options)
         :voimassaolo (voimassaolo options)
         :voimassaolo-ya (voimassaolo-ya options)
         :handler (handler options)))

(defn language [verdict]
  (-> verdict :data :language))
