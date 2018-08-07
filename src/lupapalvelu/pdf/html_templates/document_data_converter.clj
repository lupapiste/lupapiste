(ns lupapalvelu.pdf.html-templates.document-data-converter
  (:require [sade.util :refer [fn->>] :as util]
            [sade.strings :as ss]
            [clojure.walk :refer [prewalk]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.document.schemas :as schemas]))

(def supported-keys #{;; General
                      :info :type :repeating :select-one-of
                      ;; Conditional hiding
                      :blacklist :show-when :hide-when :exclude-from-pdf :hidden
                      ;; Localization
                      :i18nkey :i18name :i18nprefix :locPrefix
                      ;; Layout
                      :layout
                      ;; Handled inside leafs
                      :label
                      ;; Ignored
                      :read-only :read-only-after-sent :emit :listen
                      :required})

(defn- elem-dispatch [_ _ _ schema]
  ;;(assert (every? supported-keys (keys schema)) (format "Unsupported key(s) in '%s' schema: %s" name (remove supported-keys (keys schema))))
  (cond
    (some (comp #{schemas/select-one-of-key} :name) (:body schema)) ::select-one-of
    (:exclude-from-pdf schema) ::exclude-from-pdf
    (:blacklist schema)  ::blacklist
    (:i18nkey schema)    ::i18nkey
    (:i18nprefix schema) ::i18nprefix
    (:locPrefix schema)  ::locPrefix
    (:hide-when schema)  ::hide-when
    (:show-when schema)  ::show-when
    (:hidden schema)     ::hidden
    (:rows schema)       ::rows
    (:repeating schema)  ::repeating
    (:layout schema)     ::layout
    :else (:type schema)))

(defmulti convert-element (fn [& args] (apply elem-dispatch args)))

(defn value-dispatch [_ _ _ schema] (:type schema))

(defmulti element-value (fn [& args] (apply value-dispatch args)))

(defn doc->html
  "Convert document into hiccup html"
  ([{:keys [schema-info data]} lang]
   (doc->html (schemas/get-schema schema-info) data lang))
  ([{{name :name i18name :i18name} :info :as schema} data lang]
   (i18n/with-lang lang
     [:div.document (convert-element data [] [(or i18name name)] (assoc schema :type :group))])))

(defn- parse-i18nkey [{:keys [i18nkey]}]
  (some->> (ss/split i18nkey #"\.") (mapv keyword)))

(defn- get-in-schema-with-i18n-path [{{name :name i18name :i18name} :info :as doc-schema} path]
  (->> (remove (comp number? read-string ss/->plain-string) path)
       (reduce (fn [{i18n-path ::i18n-path :as schema} subschema-name]
                 (as-> (schemas/get-subschema schema subschema-name) $
                   (assoc $ ::i18n-path (or (parse-i18nkey $)
                                            (conj i18n-path (keyword subschema-name))))))
               (assoc doc-schema ::i18n-path [(keyword (or i18name name))]))))

(defn get-value-in
  "Get localized value for element inside document data."
  ([{:keys [schema-info data]} lang path]
   (get-value-in (schemas/get-schema schema-info) data lang path))
  ([schema data lang path]
   (let [elem-schema (get-in-schema-with-i18n-path schema path)]
     (i18n/with-lang lang
       (element-value data path (::i18n-path elem-schema) elem-schema)))))

(defn- path-string->absolute-path [elem-path path-string]
  (->> (ss/split path-string #"/")
       (#(if (ss/blank? (first %)) (rest %) (concat elem-path %)))
       (mapv keyword)))

(defn- get-title [i18n-path]
  (apply i18n/loc i18n-path))

(defn- get-value-by-path
  "Get element value by target-path string. target-path can be absolute (/path/to/element) or ralative (sibling/element).
  If target path is given as relative, path is used to resoleve absolute path."
  [doc path target-path]
  (get-in doc (conj (path-string->absolute-path (butlast path) target-path) :value)))

(defn- kw [& parts]
  (->> (map ss/->plain-string parts)
       (apply str)
       keyword))

(defn- leaf-element
  ([doc-data path i18n-path elem-schema attrs]
   (leaf-element (element-value doc-data path i18n-path elem-schema) i18n-path elem-schema attrs))
  ([value i18n-path {elem-name :name elem-type :type label :label} attrs]
   [(kw :span# elem-name :.leaf- elem-type)
    attrs
    (when-not (false? label) [:div.element-title [:b (get-title i18n-path)]])
    [:div.element-value value]]))

(defmethod convert-element ::hide-when [doc-data path i18n-path {:keys [hide-when] :as elem-schema}]
  (when-not ((:values hide-when) (get-value-by-path doc-data path (:path hide-when)))
    (convert-element doc-data path i18n-path (dissoc elem-schema :hide-when))))

(defmethod convert-element ::show-when [doc-data path i18n-path {:keys [show-when] :as elem-schema}]
  (when ((:values show-when) (get-value-by-path doc-data path (:path show-when)))
    (convert-element doc-data path i18n-path (dissoc elem-schema :show-when))))

(defmethod convert-element ::i18nkey [doc-data path _ elem-schema]
  (let [i18n-path (parse-i18nkey elem-schema)]
    (convert-element doc-data path i18n-path (dissoc elem-schema :i18nkey))))

(defmethod convert-element ::repeating [doc-data path i18n-path elem-schema]
  (->> (range (count (get-in doc-data path)))
       (map #(convert-element doc-data (conj path (kw %)) i18n-path (-> elem-schema (update :name str "-" %) (dissoc :repeating))))
       (apply vector :div.repeating)))

(defmethod convert-element ::layout [doc-data path i18n-path {:keys [layout] :as elem-schema}]
  (let [[tag & body] (convert-element doc-data path i18n-path (dissoc elem-schema :layout))]
    (apply vector (kw tag :.layout- layout) body)))

(defmethod convert-element ::select-one-of [doc-data path i18n-path {elem-body :body}]
  (let [selected (keyword (get-in doc-data (conj path (keyword schemas/select-one-of-key) :value)))
        selected-schema (util/find-first (comp #{selected} keyword :name) elem-body)]
    ;; TODO: Might need group header
    (convert-element doc-data (conj path selected) (conj i18n-path selected) selected-schema)))

(defmethod convert-element ::hidden [_ _ _ _]
  nil)

(defmethod element-value :default [doc-data path _ _]
  (get-in doc-data (conj path :value)))

(defmethod convert-element :string [doc-data path i18n-path schema]
  (leaf-element doc-data path i18n-path schema {}))

(defmethod convert-element :text [doc-data path i18n-path schema]
  (leaf-element doc-data path i18n-path schema {}))

(defmethod convert-element :select [doc-data path i18n-path schema]
  (leaf-element doc-data path i18n-path schema {}))

(defmethod element-value :checkbox [doc-data path _ _]
  (when (get-in doc-data (conj path :value)) (i18n/loc "yes")))

(defmethod convert-element :fundingSelector [doc-data path i18n-path schema]
  (let [value (if (true? (get-in doc-data (conj path :value))) (i18n/loc "yes") (i18n/loc "no"))]
    (leaf-element value i18n-path schema {})))

(defn- get-i18n-path-for-selection [doc-data path i18n-path schema]
  (let [selected (->> (conj path :value) (map #(if (keyword? %) % (keyword (str %)))) (get-in doc-data))]
    (when selected
      (or (parse-i18nkey (schemas/get-subschema schema selected))
          (conj i18n-path selected)))))

(defmethod element-value :select [doc-data path i18n-path schema]
  (some->> (get-i18n-path-for-selection doc-data path i18n-path schema)
           (apply i18n/loc)))

(defmethod element-value :radioGroup [doc-data path i18n-path schema]
  (some->> (get-i18n-path-for-selection doc-data path i18n-path schema)
           (apply i18n/loc)))

(defmethod convert-element :group [doc-data path i18n-path {elem-name :name elem-body :body}]
  (apply vector (kw :div# elem-name ".group")
         [:h3.group-title (get-title (conj i18n-path "_group_label"))]
         (->> (map (fn [{subschema-name :name :as subschema}]
                     (convert-element doc-data (conj path (keyword subschema-name)) (conj i18n-path (keyword subschema-name)) subschema))
                   elem-body)
              (remove nil?))))

(defn- table-header-row [_ _ _ {elem-body :body}]
  (apply vector :tr (map (fn->> :name (vector :th)) elem-body))) ; TODO: localize

(defn- table-data-row [doc-data path _ {elem-body :body}]
  (apply vector :tr (map (fn->> :name keyword (conj path) (get-in doc-data) :value (vector :td)) elem-body))) ; TODO: use convert-element

(defmethod convert-element :table [doc-data path i18n-path {elem-name :name :as elem-schema}]
  [(kw :div# elem-name :.table)
   [:h3.table-title]
   [:table
    [:thead (table-header-row doc-data path i18n-path elem-schema)]
    (->> (range (count (get-in doc-data path)))
         (map #(table-data-row doc-data (conj path %) i18n-path elem-schema))
         (apply vector :tbody))]])
