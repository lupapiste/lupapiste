(ns lupapalvelu.matti.schemas
  (:require [clojure.set :as set]
            [lupapalvelu.document.schemas :as doc-schemas]
            [lupapalvelu.document.tools :refer [body] :as tools]
            [lupapalvelu.matti.date :as date]
            [lupapalvelu.matti.shared :as shared]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]))

(def matti-string {:name "matti-string"
                   :type :string})

(def verdict-text {:name "matti-verdict-text"
                   :type :string})

(def verdict-section {:name "matti-verdict-section"
                      :type :string})

(def verdict-contact {:name  "matti-verdict-contact"
                      :label false
                      :type  :string})

(def verdict-giver {:name "matti-verdict-giver"
                    :type :select
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
                 :body (map #(hash-map :name %)
                            ["small" "medium" "large" "extra-large"])})

(def date {:name "matti-date"
           :type :date})

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
  {:verdict-code [(apply sc/enum (map name (keys shared/verdict-code-map)))]
   :foremen      [(apply sc/enum (map name shared/foreman-codes))]
   :reviews      [{:id   ssc/ObjectIdStr
                   :name MattiName
                   :type review-type}]
   :plans        [{:id   ssc/ObjectIdStr
                   :name MattiName}]})

(defschema MattiSavedTemplate
  (merge MattiCategory
         {:name     sc/Str
          :deleted  sc/Bool
          :draft    sc/Any ;; draft is published data on publish.
          :modified ssc/Timestamp
          (sc/optional-key :published) {:published ssc/Timestamp
                                        :data      sc/Any
                                        :settings  MattiPublishedSettings}}))

(defschema MattiSavedVerdictTemplates
  {:templates [MattiSavedTemplate]
   (sc/optional-key :settings)  {(sc/optional-key :r) MattiSavedSettings}
   (sc/optional-key :reviews)   [MattiSettingsReview]
   (sc/optional-key :plans)     [MattiGeneric]})

(def matti-schemas
  "Raw schemas are combined here for the benefit of
  pdf-export-test/ignored-schemas."
  [matti-string verdict-section verdict-text verdict-contact
   verdict-check in-verdict verdict-giver automatic-vs-manual
   complexity date])

(doc-schemas/defschemas 1
  (map (fn [m]
         {:info {:name (:name m)}
          :body (body m)})
       matti-schemas))

;; Phrases

(defschema Phrase
  {:id       ssc/ObjectIdStr
   :category (apply sc/enum (map name shared/phrase-categories))
   :tag      sc/Str
   :phrase   sc/Str})


;; Verdicts

#_(defschema Exclusions
  "Excluded dicts are removed from the verdict dictionary prior to
  validation. Rationale: the verdict should enforce the constraints
  selected in the verdict template."
  {(sc/optional-key sc/Keyword) (sc/conditional
                                 map? (sc/recursive #'Exclusions)
                                 :else true)})

(defschema MattiVerdict
  (merge MattiCategory
         {;; Verdict is draft until it is published
          (sc/optional-key :published)  ssc/Timestamp
          :modified                     ssc/Timestamp
          :data                         sc/Any
          (sc/optional-key :references) MattiPublishedSettings
          :template                     sc/Any
          ;;(sc/optional-key :exclusions) Exclusions
          }))

;; Schema utils

(defn- parse-int [x]
  (let [n (-> x str name)]
    (cond
      (integer? x)                 x
      (re-matches #"^[+-]?\d+$" n) (util/->int n))))

(defmulti validate-resolution :type)

(defmethod validate-resolution :default
  [options]
  :error.invalid-value-path)

(defn schema-error [{:keys [schema path value schema-overrides type]}]
  (if-let [schema (st/get-in (get schema-overrides type schema) path)]
    (when (sc/check schema value)
      :error.invalid-value)
    :error.invalid-value-path))

(defn path-error [path & [size]]
  (when (not= (count path) (or size 0))
    :error.invalid-value-path))

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

(defn check-date
  "The date is always in the Finnish format: 21.8.2017. Day and month
  zero-padding is accepted. Empty string is a valid date."
  [value]
  (let [trimmed (ss/trim (str value))]
    (when-not (or (ss/blank? trimmed)
                  (date/parse-finnish-date trimmed))
      :error.invalid-value)))

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
      (= data-type :select)        (when-not (ss/blank? (str value))
                                     (check-items [value] names))
      (= data-type :radioGroup)    (check-items [value] names)
      (= data-type :date)          (check-date value)
      :else                        :error.invalid-value)))

(defmethod validate-resolution :multi-select
  [{:keys [path schema data value] :as options}]
  ;; Items should not be part of the original path.
  (or (path-error path)
      (schema-error (assoc options
                           :path [:items]))
      (check-items value (->> data :items (map #(get % :value %))))))

(defn- get-path [{path :path}]
  (if (keyword? path)
    (util/split-kw-path path)
    path))

(defmethod validate-resolution :reference-list
  [{:keys [path schema data value references] :as options}]
  (let [canon-value (->> [value] flatten (remove nil?))]
    (or
     (path-error path)
     (when (and (= (:type data) :select)
                (> (count canon-value) 1))
       :error.invalid-value)
     (when (and (= (:type data) :multi-select)
                (not (or (nil? value)
                         (sequential? value))))
       :error.invalid-value)
     (when (seq canon-value) ;; Empty selection is allowed.
       (check-items canon-value
                    (map #(get % (:item-key data) %)
                         (get-in references (get-path data))))))))

(defmethod validate-resolution :phrase-text
  [{:keys [path schema value data] :as options}]
  (or (path-error path)
      (schema-error (assoc options :path [:text]))))

(defmethod validate-resolution :keymap
  [{:keys [path schema value data] :as options}]
  (or (path-error path 1)
      (when-not (util/includes-as-kw? (keys data) (first path))
        :error.invalid-value-path)))

(defn- resolve-dict-value
  [data]
  (let [docgen       (:docgen data)
        reflist      (:reference-list data)
        date-delta   (:date-delta data)
        multi-select (:multi-select data)
        phrase-text  (:phrase-text data)
        keymap       (:keymap data)
        wrap         (fn [type schema data]
                       {:type   type
                        :schema schema
                        :data   data})]
    (cond
      docgen       (wrap :docgen (doc-schemas/get-schema
                                  {:name (get docgen :name docgen)}) docgen)
      date-delta   (wrap :date-delta shared/MattiDateDelta date-delta)
      reflist      (wrap :reference-list shared/MattiReferenceList reflist)
      multi-select (wrap :multi-select shared/MattiMultiSelect multi-select)
      phrase-text  (wrap :phrase-text shared/MattiPhraseText phrase-text)
      keymap       (wrap :keymap shared/KeyMap keymap))))

(defn- validate-dictionary-value
  "Validates that path-value combination is valid for the given
  dictionary value. Returns error if not valid, nil otherwise."
  [dict-value value & [path references]]
  (if dict-value
    (validate-resolution (assoc (resolve-dict-value dict-value)
                                :value value
                                :path path
                                :references references))
    :error.invalid-value-path))

(defn- canonize-path [path]
  (cond
    (keyword? path) (util/split-kw-path path)
    (sequential? path) (map keyword path)
    :else path))

(defn validate-path-value
  "Convenience wrapper for validate-dictionary-value."
  ([{dic :dictionary} path value references]
   (let [{:keys [schema path]} (shared/dict-resolve (canonize-path path)
                                                    dic)]
     (if schema
       (validate-dictionary-value schema
                                  value
                                  path
                                  references)
       :error.invalid-value-path)))
  ([options path value]
   (validate-path-value options path value nil)))
