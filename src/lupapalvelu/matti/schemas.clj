(ns lupapalvelu.matti.schemas
  (:require [clojure.set :as set]
            [lupapalvelu.document.schemas :as doc-schemas]
            [lupapalvelu.document.tools :refer [body] :as tools]
            [lupapalvelu.matti.shared :as shared]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]))


(def matti-string {:name "matti-string"
                   :type :string})

(def verdict-text {:name "matti-verdict-text"
                   :type :string})

(def verdict-contact {:name  "matti-verdict-contact"
                      :label false
                      :type  :string})

(def verdict-id {:name     "matti-verdict-id"
                 :readonly true
                 :type     :string})

(def verdict-giver {:name "matti-verdict-giver"
                    :type :select
                    :locPrefix "matti-verdict"
                    :body [{:name "viranhaltija"}
                           {:name "lautakunta"}]})

(def automatic-vs-manual {:name "automatic-vs-manual"
                          :type :radioGroup
                          :label false
                          :body  [{:name "automatic"}
                                  {:name "manual"}]})

(def verdict-check {:name "matti-verdict-check"
                    :label false
                    :type :checkbox})

(def in-verdict {:name "required-in-verdict"
                 :label false
                 :i18nkey "matti.template-removed"
                 :type :checkbox})

(def complexity {:name "matti-complexity"
                 :type :select
                 :locPrefix "matti"
                 :body (map #(hash-map :name %)
                            ["small" "medium" "large" "extra-large"])})

(defschema MattiCategory
  {:id       ssc/ObjectIdStr
   :category (sc/enum "r" "p" "ya" "kt" "ymp")})

(defschema MattiName
  {:fi sc/Str
   :sv sc/Str
   :en sc/Str})

(defschema MattiGeneric
  "Generic is either review or plan."
  (merge MattiCategory
         {:name    MattiName
          :deleted sc/Bool}))

(def review-type (apply sc/enum (map name (keys shared/review-type-map))))

(defschema MattiSettingsReview
  (merge MattiGeneric
         {:type review-type}))

(defschema MattiSavedSettings
  {:modified ssc/Timestamp
   :draft    sc/Any})

(defschema MattiPublishedSettings
  {:verdict [(apply sc/enum (map name (keys shared/verdict-code-map)))]
   :foremen [(apply sc/enum (map name shared/foreman-codes))]
   :reviews [{:id   ssc/ObjectIdStr
              :name MattiName
              :type review-type}]
   :plans   [{:id   ssc/ObjectIdStr
              :name MattiName}]})

(defschema MattiSavedTemplate
  (merge MattiCategory
         {:name     sc/Str
          :deleted  sc/Bool
          :draft    sc/Any ;; draft is copied to version data on publish.
          :modified ssc/Timestamp
          :versions [{:id        ssc/ObjectIdStr
                      :published ssc/Timestamp
                      :data      sc/Any
                      :settings  MattiPublishedSettings}]}))

(defschema MattiSavedVerdictTemplates
  {:templates [MattiSavedTemplate]
   (sc/optional-key :settings)  {(sc/optional-key :r) MattiSavedSettings}
   (sc/optional-key :reviews)   [MattiSettingsReview]
   (sc/optional-key :plans)     [MattiGeneric]})

(doc-schemas/defschemas 1
  (map (fn [m]
         {:info {:name (:name m)}
          :body (body m)})
       [matti-string verdict-text verdict-contact verdict-check
        in-verdict verdict-giver verdict-id automatic-vs-manual
        complexity]))

;; Phrases

(defschema Phrase
  {:id       ssc/ObjectIdStr
   :category (apply sc/enum (map name shared/phrase-categories))
   :tag      sc/Str
   :phrase   sc/Str})


;; Verdicts

(defschema MattiVerdict
  {:id                          ssc/ObjectIdStr
   :template-id                 ssc/ObjectIdStr
   ;; Verdict is draft until it is published
   (sc/optional-key :published) ssc/Timestamp
   :modified                    ssc/Timestamp
   :data                        sc/Any})

;; Schema utils

(defn- resolve-path-schema
  "Resolution result is map with two fixed keys (:path, :data) and one
  depending on the schema type (:schema, :docgen, :reference-list)."
  [data xs]
  (let [path         (mapv keyword xs)
        docgen       (:docgen data)
        reflist      (:reference-list data)
        date-delta   (:date-delta data)
        multi-select (:multi-select data)
        phrase-text  (:phrase-text data)
        wrap         (fn [id schema data]
                       {id {:schema schema
                            :path   path
                            :data   data}})]
    (cond
      (:grid data) (wrap :section shared/MattiSection data)
      docgen       (wrap :docgen (doc-schemas/get-schema {:name docgen}) docgen)
      date-delta   (wrap :date-delta shared/MattiDateDelta date-delta)
      reflist      (wrap :reference-list shared/MattiReferenceList reflist)
      multi-select (wrap :multi-select shared/MattiMultiSelect multi-select)
      phrase-text  (wrap :phrase-text shared/MattiPhraseText phrase-text))))

(defn- parse-int [x]
  (let [n (-> x str name)]
   (cond
     (integer? x)                 x
     (re-matches #"^[+-]?\d+$" n) (util/->int n))))

(defn- resolve-index [xs x]
  (if-let [index (parse-int x)]
    index
    (first (keep-indexed (fn [i data]
                           (when (= x (:id data))
                             i))
                         xs))))

(defn- pick-item [xs x]
  (when-let [i (resolve-index xs x)]
    (when (contains? (vec xs) i)
      (nth xs i))))

(declare schema-data-helper)

(defn item-data [items [x & xs]]
  (when-let [item (pick-item items x)]
    (schema-data-helper item xs)))

(defn cell-data [grid [x & xs]]
  (when-let [row (pick-item (:rows grid) x)]
    (when-let [col (pick-item (get row :row row) (first xs))]
      (schema-data-helper col (drop 1 xs)))))

(defn schema-data-helper
  [data [x & xs :as path]]
  (cond
    (some-> data :schema :list) (item-data (get-in data [:schema :list :items]) path)
    (:schema data)              (resolve-path-schema (:schema data) path) ;; Leaf
    (:sections data)            (item-data (:sections data) path)
    ;; Must be before grid for section properties (removed)
    (nil? xs)                   (resolve-path-schema data path)
    (:grid data)                (cell-data (:grid data) path)))

(defn schema-data
  "Data is the reference schema instance (e.g.,
  shared/default-verdict-template). The second argument is path into
  data. Returns map with remaining path and resolved schema (see
  resolve-path-schema)."
  [data path]
  (when (and (seq path) (empty? (filter coll? path)))
    (schema-data-helper data (map #(name (if (number? %) (str %) %)) path))))

(defmulti validate-resolution :type)

(defmethod validate-resolution :default
  [options]
  :error.invalid-value-path)

(defn schema-error [{:keys [schema path value schema-overrides type]}]
  (if-let [schema (st/get-in (get schema-overrides type schema) path)]
    (when (sc/check schema value)
      :error.invalid-value)
    :error.invalid-value-path))

(defmethod validate-resolution :section
  [{:keys [path schema value] :as options}]
  (cond
    (coll? value) :error.invalid-value
    :else         (schema-error options)))

(defmethod validate-resolution :date-delta
  [{:keys [path schema value] :as options}]
  (let [property (first path)]
    (if (contains? #{:enabled :delta} property)
     (schema-error (merge options
                          (when (= property :delta)
                            {:value (parse-int value)})))
     :error.invalid-value-path)))

(defn keyword-set [xs]
  (set (map keyword xs)))

(defn check-items [x data-items]
  (let [items (flatten [x])
        v-set (keyword-set items)
        d-set (keyword-set data-items)]
    (cond
      (not (set/subset? v-set d-set))    :error.invalid-value
      (not= (count items) (count v-set)) :error.duplicate-items)))

(defmethod validate-resolution :docgen
  [{:keys [path schema value data] :as options}]
  ;; TODO: Use the old-school docgen validation if possible
  ;; For now we support only the types used by Matti
  (let [body      (-> schema :body first)
        data-type (:type body)
        names     (map :name (:body body))
        check     (fn [pred] (when (sc/check pred value)
                               :error.invalid-value))]
    (cond
      (seq path)                   :error.invalid-value-path
      (coll? value)                :error.invalid-value
      (data-type #{:text :string}) (check sc/Str)
      (= data-type :checkbox)      (check sc/Bool)
      ;; TODO: Nil handling should follow valueAllowUnset.
      (= data-type :select)        (when (seq value)
                                     (check-items [value] names))
      (= data-type :radioGroup)    (check-items [value] names)
      :else                        :error.invalid-value))
  )

(defmethod validate-resolution :multi-select
  [{:keys [path schema data value] :as options}]
  ;; Items should not be part of the original path.
  (or (schema-error (assoc options
                           :path (conj path :items)))
      (check-items value (:items data))))

(defn- get-path [{path :path}]
  (if (keyword? path)
    (util/split-kw-path path)
    path))

(defmethod validate-resolution :reference-list
  [{:keys [path schema data value references] :as options}]
  (cond
    (not-empty path) :error.invalid-value-path
    :else            (when (seq value) ;; Empty selection is allowed.
                       (check-items value (get-in references (get-path data))))))

(defmethod validate-resolution :phrase-text
  [{:keys [path schema value data] :as options}]
  (schema-error (assoc options :path (conj path :text))))

(defn validate-path-value
  "Error message if not valid, nil otherwise."
  [schema-instance data-path value & [{:keys [references schema-overrides]}]]
  (let [resolution (schema-data schema-instance data-path)]
    (validate-resolution (assoc  (some-> resolution vals first)
                                 :type (some-> resolution keys first)
                                 :value value
                                 :references references
                                 :schema-overrides schema-overrides))))
