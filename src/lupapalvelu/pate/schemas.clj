(ns lupapalvelu.pate.schemas
  (:require [clojure.set :as set]
            [lupapalvelu.document.schemas :as doc-schemas]
            [lupapalvelu.document.tools :refer [body] :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.shared :as shared]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]))

(def verdict-giver {:name "pate-verdict-giver"
                    :type :select
                    :body [{:name "viranhaltija"}
                           {:name "lautakunta"}]})

(def automatic-vs-manual {:name "automatic-vs-manual"
                          :type :radioGroup
                          :label false
                          :body  [{:name "automatic"}
                                  {:name "manual"}]})

(def complexity {:name "pate-complexity"
                 :type :select
                 :body (map #(hash-map :name %)
                            ["small" "medium" "large" "extra-large"])})

(def collateral-type {:name "collateral-type"
                      :type :select
                      :body [{:name "shekki"}
                             {:name "panttaussitoumus"}]})

(def languages {:name "pate-languages"
                :type :select
                :body (map #(hash-map :name (name %)) i18n/languages)})

(defschema PateCategory
  {:id       ssc/ObjectIdStr
   :category (sc/enum "r" "p" "ya" "kt" "ymp")})

(defschema PateName
  {:fi sc/Str
   :sv sc/Str
   :en sc/Str})

(defschema PateGeneric
  "Generic is either review or plan."
  (merge PateCategory
         {:name    PateName
          :deleted sc/Bool}))

(def review-type (apply sc/enum (map name (keys shared/review-type-map))))

(defschema PateSettingsReview
  (merge PateGeneric
         {:type review-type}))

(defschema PateSavedSettings
  {:modified ssc/Timestamp
   :draft    sc/Any})

(defschema PatePublishedSettings
  {:verdict-code                [(apply sc/enum (map name (keys shared/verdict-code-map)))]
   :date-deltas                 (->> shared/verdict-dates
                                     (map (fn [k]
                                            [(sc/optional-key k) sc/Int]))
                                     (into {}))
   (sc/optional-key :foremen)   [(apply sc/enum (map name shared/foreman-codes))]
   (sc/optional-key :reviews)   [{:id   ssc/ObjectIdStr
                                  :name PateName
                                  :type review-type}]
   (sc/optional-key :plans)     [{:id   ssc/ObjectIdStr
                                  :name PateName}]
   ;; Boardname included only when the verdict giver is Lautakunta.
   (sc/optional-key :boardname) sc/Str})

(defschema PateSavedTemplate
  (merge PateCategory
         {:name     sc/Str
          :deleted  sc/Bool
          :draft    sc/Any ;; draft is published data on publish.
          :modified ssc/Timestamp
          (sc/optional-key :published) {:published ssc/Timestamp
                                        :data      sc/Any
                                        :settings  PatePublishedSettings}}))

(defschema PateSavedVerdictTemplates
  {:templates [PateSavedTemplate]
   (sc/optional-key :settings)  {(sc/optional-key :r) PateSavedSettings
                                 (sc/optional-key :p) PateSavedSettings}
   (sc/optional-key :reviews)   [PateSettingsReview]
   (sc/optional-key :plans)     [PateGeneric]})

(def pate-schemas
  "Raw schemas are combined here for the benefit of
  pdf-export-test/ignored-schemas."
  [verdict-giver automatic-vs-manual complexity collateral-type
   languages])

(doc-schemas/defschemas 1
  (map (fn [m]
         {:info {:name (:name m)}
          :body (body m)})
       pate-schemas))

;; Phrases

(defschema Phrase
  {:id       ssc/ObjectIdStr
   :category (apply sc/enum (map name shared/phrase-categories))
   :tag      sc/Str
   :phrase   sc/Str})


;; Verdicts

(defschema PateVerdict
  (merge PateCategory
         {;; Verdict is draft until it is published
          (sc/optional-key :published)  ssc/Timestamp
          :modified                     ssc/Timestamp
          :data                         sc/Any
          (sc/optional-key :references) PatePublishedSettings
          :template                     sc/Any}))

;; Schema utils

(defn parse-int
  "Empty strings are considered as zeros."
  [x]
  (let [n (-> x str name)]
    (cond
      (integer? x)                 x
      (ss/blank? x)                0
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
  (or (path-error path)
      (schema-error (assoc options
                           :value (parse-int value)
                           :path [:delta]))))

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
  ;; For now we support only the types used by Pate
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

(defmethod validate-resolution :date
  [{:keys [path value] :as options}]
  (or (path-error path)
      (check-date value)))

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
  (let [canon-value (->> [value] flatten (remove nil?))
        data-type   (:type data)]
    (or
     (path-error path)
     (when (and (= data-type :select)
                (> (count canon-value) 1))
       :error.invalid-value)
     (when (and (= data-type :multi-select)
                (not (or (nil? value)
                         (sequential? value))))
       :error.invalid-value)
     ;; Empty selection for all types and "" for select type is
     ;; allowed.
     (when (and (seq canon-value)
                (not (and (= data-type :select)
                          (= (first canon-value) ""))))
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

(defmethod validate-resolution :button
  [{:keys [path]}]
  (path-error path))

(defmethod validate-resolution :application-attachments
  [{:keys [path  value]}]
  (or (path-error path)
      (when (sc/check [sc/Str] value)
        :error.invalid-value)))

(defn- simple-value-resolution
  [{:keys [path value] :as options}]
  (or (path-error path)
      (schema-error (assoc options :path [:value]))))

(defmethod validate-resolution :toggle
  [options]
  (simple-value-resolution options))

(defmethod validate-resolution :text
  [options]
  (simple-value-resolution options))

(defn- resolve-dict-value
  [data]
  (let [{:keys [docgen reference-list
                date-delta multi-select
                phrase-text keymap button
                application-attachments
                toggle text date]} data
        wrap                  (fn [type schema data]
                                {:type   type
                                 :schema schema
                                 :data   data})]
    (cond
      docgen                  (wrap :docgen (doc-schemas/get-schema
                                             {:name (get docgen :name docgen)}) docgen)
      date-delta              (wrap :date-delta shared/PateDateDelta date-delta)
      reference-list          (wrap :reference-list shared/PateReferenceList reference-list)
      multi-select            (wrap :multi-select shared/PateMultiSelect multi-select)
      phrase-text             (wrap :phrase-text shared/PatePhraseText phrase-text)
      keymap                  (wrap :keymap shared/KeyMap keymap)
      button                  (wrap :button shared/PateButton button)
      application-attachments (wrap :application-attachments
                                    shared/PateComponent
                                    application-attachments)
      toggle                  (wrap :toggle shared/PateToggle toggle)
      text                    (wrap :text shared/PateText text)
      date                    (wrap :date shared/PateDate date))))

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
  ([schema path value]
   (validate-path-value schema path value nil)))

(defn- process-button
  "In the context of the backend schema validation and processing,
  button always presents either adding or removing item from a
  repeating structure. This function is called from
  validate-and-process-value (see below)."
  [{:keys [path old-data dict-schema dict-path dictionary err]}]
  (let [add    (-> dict-schema :button :add)
        remove (-> dict-schema :button :remove)]
    (or
     (cond
       ;; Add button dict must be a sibling for the target repeating.
       add (when-let [subpath (shared/repeating-subpath add
                                                        (concat (butlast path) [add])
                                                        dictionary)]
             (let [add-path (->> (mongo/create-id)
                                 keyword
                                 list
                                 (concat subpath))]
               {:op    :add
                :path  add-path
                :value {}
                :data  (assoc-in old-data add-path {})}))
       ;; Remove button must belong to the target repeating. However,
       ;; the target can be an ancestor repeating as well.
       remove (when-let [subpath (shared/repeating-subpath remove
                                                           path
                                                           dictionary)]
                (let [removepath (subvec (vec path) 0 (inc (count subpath)))]
                  {:op    :remove
                   :path  removepath
                   :value true
                   :data  (util/dissoc-in old-data removepath)})))
     (err :error.invalid-value-path))))


(defn validate-and-process-value
  "Validates and rocesses new value.  Old-data is the whole old-data.

  For error situations the returned map has either

  :errors  Value is a vector of paths and errors
  :failure Value is error. Unexpected failure (e.g., invalid value path)

  On success the map has the following keys:

  :op Eiher :set, :add or :remove. The latter two are :repeating
      operations.
  :path  canonized path
  :value processed value
  :data  processed data

  Typically there is no transformation and the old-data is simply
  updted with value."
  ([{dic :dictionary} path value old-data references]
   (let [path                (canonize-path path)
         {dict-schema :schema
          dict-path   :path} (shared/dict-resolve path dic)
         error               (if dict-schema
                               (validate-dictionary-value dict-schema
                                                          value
                                                          dict-path
                                                          references)
                               :error.invalid-value-path)
         err                 (fn [e]
                               (if (= e :error.invalid-value-path)
                                 {:failure e}
                                 {:errors [[path e]]}))
         repeating-path      (drop-last (-> dict-path count inc) path)]
     (cond
       error
       (err error)

       ;; Repeating indeces must exist
       (and (not-empty repeating-path)
            (nil? (get-in old-data repeating-path)))
       (err :error.invalid-value-path)

       (:button dict-schema)
       (process-button {:path        path        :old-data  old-data
                        :dict-schema dict-schema :dict-path dict-path
                        :dictionary  dic         :err       err})

       :else
       {:op    :set
        :path  path
        :value value
        :data  (assoc-in old-data path value)})))
  ([schema path value old-data]
   (validate-and-process-value schema path value old-data nil)))

(defn required-filled?
  "True if every required dict item has a proper value."
  [schema data]
  (->> schema
       :dictionary
       (filter (fn [[k v]]
                 (:required? v)))
       (every? (fn [[k v]]
                 (case (-> v (dissoc :required?) keys first)
                   :multi-select (not-empty (k data))
                   :reference    true ;; Required only for highlighting purposes
                   (ss/not-blank? (k data)))))))
