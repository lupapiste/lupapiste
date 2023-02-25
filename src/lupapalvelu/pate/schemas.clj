(ns lupapalvelu.pate.schemas
  (:require [clojure.set :as set]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]))

(defschema PateCategoryTag (apply sc/enum (map name schema-util/all-categories)))

(defschema PateCategory
  {:id       ssc/ObjectIdStr
   :category PateCategoryTag})

(defschema PateTerm
  (->> helper/supported-languages
      (map #(vector % sc/Str))
      (into {})))

(defschema PateDependency
  "Published settings depency of a template"
  (merge PateTerm {:selected sc/Bool}))

(def review-type (->> (concat helper/review-types
                              helper/ya-review-types)
                      distinct
                      (map name)
                      (apply sc/enum)))

(defschema PateSavedSettings
  {:modified ssc/Timestamp
   :draft    sc/Any})

(defschema PatePublishedSettings
  {(sc/optional-key :organization-name) sc/Str
   (sc/optional-key :verdict-code)      [(apply sc/enum (map name (keys helper/verdict-code-map)))]
   (sc/optional-key :date-deltas)       (->> (helper/verdict-dates)
                                             (map (fn [k]
                                                    [(sc/optional-key k)
                                                     {:delta sc/Int
                                                      :unit  (sc/enum "days" "years")}]))
                                             (into {}))
   (sc/optional-key :foremen)           [(apply sc/enum (map name helper/foreman-codes))]

   ;; Boardname included only when the verdict giver is Lautakunta.
   (sc/optional-key :boardname) sc/Str})

(defschema PatePublishedTemplateSettings
  (merge PatePublishedSettings
         {(sc/optional-key :reviews)         [(merge PateDependency
                                                     {:type review-type})]
          (sc/optional-key :plans)           [PateDependency]
          (sc/optional-key :handler-titles)  [PateDependency]}))

(defn- wrapped
  "Unwrapped value is supported as a fallback for existing templates."
  ([schema]
   (wrapped schema false))
  ([schema fallback?]
   (let [md {:_value    schema
             :_user     sc/Str
             :_modified ssc/Timestamp}]
     (if fallback?
       (sc/conditional
         map? md
         :else schema)
       md))))

(defschema PateSavedTemplate
  (merge PateCategory
         {:name                        (wrapped sc/Str true)
          :deleted                     (wrapped sc/Bool true)
          (sc/optional-key :draft)     sc/Any ;; draft is published data on publish.
          :modified                    ssc/Timestamp
          (sc/optional-key :published) {:published  (wrapped ssc/Timestamp true)
                                        :data       sc/Any
                                        :inclusions [sc/Str]
                                        :settings   PatePublishedTemplateSettings}}))

(defschema PateSavedVerdictTemplates
  {(sc/optional-key :templates) [PateSavedTemplate]
   (sc/optional-key :settings)  (->> schema-util/pate-categories
                                     (map #(vector (sc/optional-key %) PateSavedSettings))
                                     (into {}))})


;; Verdicts

(defschema PateVerdictReq
  (merge PateTerm
         {:id ssc/ObjectIdStr}))

(defschema PateVerdictReferences
  (merge PatePublishedSettings
         {(sc/optional-key :reviews)         [(merge PateVerdictReq
                                                     {:type review-type})]
          (sc/optional-key :plans)           [PateVerdictReq]
          (sc/optional-key :handler-titles)  [PateVerdictReq]}))

;; Phrases

(defschema PhraseCategory
  (sc/cond-pre (apply sc/enum (map name shared-schemas/phrase-categories))
               ssc/ObjectIdStr))

(defschema Phrase
  {:id       ssc/ObjectIdStr
   :category PhraseCategory
   :tag      sc/Str
   :phrase   sc/Str})

(defschema CustomPhraseCategoryMap
  {ssc/ObjectIdKeyword PateTerm})

(defschema UserRef
  "We have to define our own summary, since requiring the
  lupapalvelu.user namespace would create a cyclical dependency."
  {:id        ssc/ObjectIdStr
   :username  ssc/Username})

(defschema ReplacementPateVerdict
  (sc/conditional
   :replaces    {(sc/optional-key :replaces)      ssc/ObjectIdStr}
   :replaced-by {;; The publisher of the replacement verdict.
                 (sc/optional-key :user)          UserRef
                 (sc/optional-key :replaced-by)   ssc/ObjectIdStr}))

(defschema PateSignature
  {:date                         ssc/Timestamp
   :user-id                      (ssc/min-length-string 1)  ; HACK: Copy-pasted usr/Id to avoid dependency cycle.
   ;; Firstname Lastname
   :name                         sc/Str
   ;; If the user is authed to the application via company. The
   ;; company name is also added to the name: User Name, Company Ltd.
   (sc/optional-key :company-id) ssc/ObjectIdStr})

(defschema PateBaseVerdict
  (merge PateCategory
         {(sc/optional-key :published)          {:tags                            sc/Str
                                                 ;; The same as :state._modified
                                                 :published                       sc/Int
                                                 ;; Id for the attachment that is a PDF version of tags.
                                                 (sc/optional-key :attachment-id) ssc/AttachmentId}
          (sc/optional-key :proposal)           {:tags                            sc/Str
                                                 :proposed                        sc/Int
                                                 (sc/optional-key :attachment-id) ssc/AttachmentId}
          :state                                (wrapped (sc/enum "draft"
                                                                  "scheduled"
                                                                  "publishing-verdict"
                                                                  "publishing-proposal"
                                                                  "published"
                                                                  "proposal"
                                                                  "signing-contract"))
          :modified                             ssc/Timestamp
          :data                                 sc/Any
          ;; Whether the verdict timestamps are available depends on
          ;; the verdict type (legacy or not) and template settings.
          (sc/optional-key :archive)            {(sc/optional-key :verdict-date)  sc/Int
                                                 (sc/optional-key :anto)          sc/Int
                                                 (sc/optional-key :lainvoimainen) sc/Int
                                                 :verdict-giver                   sc/Str}

          (sc/optional-key :signatures)         [PateSignature]
          (sc/optional-key :signature-requests) [PateSignature]}))

(defschema PateModernVerdict
  (merge PateBaseVerdict
         {:schema-version                sc/Int
          (sc/optional-key :references)  PateVerdictReferences
          :template                      {:inclusions                 [shared-schemas/keyword-or-string]
                                          (sc/optional-key :giver)    (sc/enum "viranhaltija"
                                                                            "lautakunta")
                                          (sc/optional-key :title)    sc/Str
                                          (sc/optional-key :subtitle) sc/Str}
          (sc/optional-key :replacement) ReplacementPateVerdict}))

(defschema PateLegacyVerdict
  (merge PateBaseVerdict
         {:legacy?  (sc/enum true)
          :template {:inclusions [shared-schemas/keyword-or-string]}}))

(defschema PateVerdict
  (sc/conditional
   :legacy?        PateLegacyVerdict
   :schema-version PateModernVerdict
   'lol-does-not-look-like-pate-verdict))

;; Schema utils

(defn parse-int
  "Empty strings are considered zeros."
  [x]
  (let [n (-> x str ss/trim)]
    (cond
      (integer? x)                 x
      (nil? x)                     0
      (not (string? x))            nil
      (ss/blank? x)                0
      (re-matches #"^[+-]?\d+$" n) (util/->int n))))

(defmulti validate-resolution :type)

(defmethod validate-resolution :default [_] :error.invalid-value-path)

(defn schema-error [{:keys [schema path value schema-overrides type]}]
  (if-let [schema (st/get-in (get schema-overrides type schema) path)]
    (when (sc/check schema value)
      :error.invalid-value)
    :error.invalid-value-path))

(defn path-error [path & [size]]
  (when (not= (count path) (or size 0))
    :error.invalid-value-path))

(defmethod validate-resolution :date-delta [{:keys [path value] :as options}]
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

(defmethod validate-resolution :date [{:keys [path value] :as options}]
  (try
    (or (path-error path)
        (schema-error (assoc options
                             :value (date/timestamp value)
                             :path [:value])))
    (catch Exception _
      :error.invalid-value)))

(defmethod validate-resolution :select [{:keys [path value data]}]
  (or (path-error path)
      (when-not (ss/blank? (str value))
        (check-items [value] (:items data)))))

(defmethod validate-resolution :multi-select [{:keys [path data value] :as options}]
  ;; Items should not be part of the original path.
  (or (path-error path)
      (schema-error (assoc options
                           :path [:items]))
      (check-items value (->> data :items (map #(get % :value %))))))

(defn- get-path [{path :path}]
  (if (keyword? path)
    (util/split-kw-path path)
    path))

(defmethod validate-resolution :reference-list [{:keys [path data value references]}]
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
                         (util/pcond->> (get-in references (get-path data))
                                        map? (map (fn [[k v]]
                                                    (assoc v :MAP-KEY k))))))))))

(defmethod validate-resolution :phrase-text [{:keys [path] :as options}]
  (or (path-error path)
      (schema-error (assoc options :path [:text]))))

(defmethod validate-resolution :keymap [{:keys [path data]}]
  (or (path-error path 1)
      (when-not (util/includes-as-kw? (keys data) (first path))
        :error.invalid-value-path)))

(defmethod validate-resolution :button [{:keys [path]}]
  (path-error path))

(defmethod validate-resolution :application-attachments [{:keys [path  value]}]
  (or (path-error path)
      (when (sc/check [sc/Str] value)
        :error.invalid-value)))

(defn- simple-value-resolution [{:keys [path] :as options}]
  (or (path-error path)
      (schema-error (assoc options :path [:value]))))

(defmethod validate-resolution :toggle [options]
  (simple-value-resolution options))

(defmethod validate-resolution :text [options]
  (simple-value-resolution options))

(defmethod validate-resolution :row-order [{:keys [path value data] :as options}]
  (or (path-error path)
      (schema-error options)
      (when-not (and
                  (= (count value) (count data))
                  (= (set value) (set data)))
        :error.invalid-value)))

(defn- resolve-dict-value [data]
  (let [{:keys [reference-list date-delta multi-select phrase-text
                keymap button application-attachments toggle text date select
                row-order]} data
        wrap                (fn [type schema
                                 data] {:type   type
                                        :schema schema
                                        :data   data})]
    (cond
      date-delta              (wrap :date-delta shared-schemas/PateDateDelta date-delta)
      reference-list          (wrap :reference-list shared-schemas/PateReferenceList reference-list)
      multi-select            (wrap :multi-select shared-schemas/PateMultiSelect multi-select)
      phrase-text             (wrap :phrase-text shared-schemas/PatePhraseText phrase-text)
      keymap                  (wrap :keymap shared-schemas/KeyMap keymap)
      button                  (wrap :button shared-schemas/PateButton button)
      application-attachments (wrap :application-attachments
                                    shared-schemas/PateComponent
                                    application-attachments)
      toggle                  (wrap :toggle shared-schemas/PateToggle toggle)
      text                    (wrap :text shared-schemas/PateText text)
      date                    (wrap :date shared-schemas/PateDate date)
      select                  (wrap :select shared-schemas/PateSelect select)
      row-order               (wrap :row-order shared-schemas/RowOrder row-order))))

(defn- validate-dictionary-value
  "Validates that path-value combination is valid for the given
  dictionary value. Returns error if not valid, nil otherwise."
  [dict-value value & [path references]]
  (if dict-value
    (let [resolution (resolve-dict-value dict-value)]
      (if (some-> resolution :data :read-only?)
        :error.read-only
        (validate-resolution (assoc resolution
                                    :value value
                                    :path path
                                    :references references))))
    :error.invalid-value-path))

(defn- canonize-path [path]
  (cond
    (keyword? path) (util/split-kw-path path)
    (sequential? path) (map keyword path)
    :else path))

(defn validate-path-value
  "Convenience wrapper for validate-dictionary-value."
  ([{dic :dictionary} path value references]
   (let [{:keys [schema path]} (schema-util/dict-resolve (canonize-path path)
                                                         dic)]
     (if schema
       (validate-dictionary-value schema
                                  value
                                  path
                                  references)
       :error.invalid-value-path)))
  ([schema path value]
   (validate-path-value schema path value nil)))

(defn move-item
  "Move a specific item within list. List is treated as a circle buffer.
  `direction`  `:up` (left) or `:down` (right)
  `pred` The first match is the moved item. If none matches, nil is returned.
  `items` All items

  (move-item :up zero? [-2 -1 0 1 2])  ->  [-2 0 -1 1 2]
  (move-item :down pos? [-2 -1 0 1 2]) ->  [-2 -1 0 2 1]
  (move-item :up neg? [-2 -1 0 1 2])   ->  [-1 0 1 2 -2]"
  [direction pred items]
  (let [up?   (= direction :up)
        down? (not up?)]
    (loop [[x & xs] items
           passed   []]
      (let [found? (pred x)]
        (cond
          (nil? x)           nil ; The row no longer exists, no updates needed.

          (and found? up?)   ; Up like a rocket machine
          (if (seq passed)
            (concat (butlast passed) [x (last passed)] xs)
            (concat xs [x]))

          (and found? down?) ; Down like a roller coaster
          (if (seq xs)
            (concat  passed [(first xs) x] (rest xs))
            (cons x passed))

          :else              (recur xs (conj passed x)))))))

(defn- move-item-in-repeating
  "When an item is moved up or down, two things happen:
   1. The new location (neighbors) for the item is resolved
   2. The whole repeating structure is reordered

  Returns a map, where each key is repeating index (row id) and value is a manual dict - index value
  map. If the target is not found, returns nil."
  [target-id {:keys [move] :as button-schema} repeating-data]
  (let [{:keys [manual direction]} move
        items (->> repeating-data
                   (map (fn [[k v]] [k (manual v)]))
                   (sort-by second))
        matches? #(= (first %) target-id)]
    (some->> (move-item direction matches? items)
             (map-indexed (fn [i [k _]]
                            [k {manual i}]))
             (into {}))))

(declare validate-and-process-value)

(defn- process-button
  "In the context of the backend schema validation and processing, button always presents either
  adding, removing or moving (reordering the whole repeating) an item in a repeating structure. This
  function is called from `validate-and-process-value` (see below)."
  [{:keys [path old-data dict-schema dictionary err]}]
  (let [{:keys [add remove move]
         :as   button-schema} (:button dict-schema)
        {:keys [row-id row-order]} move]
    (or
     (cond
       ;; Add button dict must be a sibling for the target repeating.
       add
       (when-let [subpath (schema-util/repeating-subpath add
                                                         (concat (butlast path) [add])
                                                         dictionary)]
         (let [add-path (->> (mongo/create-id)
                             keyword
                             list
                             (concat subpath))
               ;; If the repeatings is sorted manually, then the new (empty) item must
               manual   (get-in dictionary (concat subpath [:sort-by :manual]))
               value    (cond-> {}
                          manual (assoc manual (count (get-in old-data subpath))))]
           {:op    :add
            :path  add-path
            :value value
            :data  (assoc-in old-data add-path value)}))

       ;; Remove button must belong to the target repeating. However,
       ;; the target can be an ancestor repeating as well.
       remove
       (when-let [subpath (schema-util/repeating-subpath remove
                                                         path
                                                         dictionary)]
         (let [removepath (subvec (vec path) 0 (inc (count subpath)))]
           {:op    :remove
            :path  removepath
            :value true
            :data  (util/dissoc-in old-data removepath)}))

       ;;; Move button must belong to the target repeating.
       (:manual move)
       (when-let [subpath (schema-util/repeating-subpath (some->> path (drop-last 2) last)
                                                         path
                                                         dictionary)]
         (let [new-order (move-item-in-repeating (-> path butlast last)
                                                 button-schema
                                                 (get-in old-data subpath))]
           {:op    (when new-order :order)
            :path  subpath
            :value new-order
            :data  (reduce-kv (fn [acc k v]
                                (update-in acc (concat subpath [k]) merge v))
                              old-data
                              new-order)}))

       ;; Move grid row
       row-order
       (when-let [items (some->> (get old-data row-order (get-in dictionary [row-order :row-order]) )
                                 (move-item (:direction move) #(= row-id %)))]
         (util/pcond-> (validate-and-process-value {:dictionary dictionary}
                                                   [row-order]
                                                   items
                                                   old-data)
                       :op (assoc :op :add))))

     (err :error.invalid-value-path))))

(defn validate-and-process-value
  "Validates and processes new value.  Old-data is the whole old-data.

  For error situations the returned map has either

  :errors  Value is a vector of paths and errors
  :failure Value is error. Unexpected failure (e.g., invalid value path)

  On success the map has the following keys:

  :op Eiher :set, :add, :remove or :order. The latter two are :repeating
      operations only. :add is used both for repeating and grid sorting with :row-order.

  :path  canonized path
  :value processed value
  :data  processed data

  Typically there is no transformation and the old-data is simply
  updated with value."
  ([{dic :dictionary} path value old-data references]
   (let [path                (canonize-path path)
         {dict-schema :schema
          dict-path   :path} (schema-util/dict-resolve path dic)
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

       (:date dict-schema)
       (let [value (date/timestamp value)]
         {:op    :set
          :path  path
          :value value
          :data  (assoc-in old-data path value)})

       :else
       {:op    :set
        :path  path
        :value value
        :data  (assoc-in old-data path value)})))
  ([schema path value old-data]
   (validate-and-process-value schema path value old-data nil)))

(defn- resolve-required-filled?
  [{:keys [schema data excludes schema-path data-path] :as options}]
  (->> schema
       :dictionary
       (filter (fn [[k v]]
                 (or (some-> v :required? (schema-util/resolve-required? data (conj data-path k)))
                     (:repeating v))))
       (remove (fn [[k _]] ((set excludes) (util/kw-path schema-path k))))
       (every? (fn [[k v]]
                 (let [value (get-in data (conj data-path k))]
                   (cond
                     (:multi-select v)    (not-empty value)
                     (:reference v)       true ; Required only for highlighting purposes
                     (:date v)            (integer? value)
                     (and (:required? v)
                          (:repeating v)
                          (empty? value)) false

                     (:repeating v)
                     (every? (fn [rep-id]
                               (resolve-required-filled? (-> options
                                                             (assoc :schema {:dictionary (:repeating v)})
                                                             (update :schema-path conj k)
                                                             (update :data-path conj k rep-id))))
                             (keys value))

                     :else                (ss/not-blank? value)))))))

(defn required-filled?
  "True if every required dict item has a proper value.

  `schema`  Full schema (with `:dictionary`)
  `data`    Full data
  `exludes` List of kw-paths for dicts to be excluded from the check. Typically just dicts, but for
  repeatings, must include the repeating dict as well (e.g., :rep.foo)"
  ([schema data excludes]
   (resolve-required-filled? {:schema      schema
                              :data        data
                              :excludes    excludes
                              :schema-path []
                              :data-path   []}))
  ([schema data]
   (required-filled? schema data [])))

(defn section-dicts
  "Set of :dict and :repeating keys in the given
  section. The :repeating short-circuits the traversal."
  [section]
  (letfn [(search-fn [x]
            (let [dict     (:dict x)
                  repeating (:repeating x)]
              (cond
                dict            dict
                repeating       repeating
                (map? x)        (map search-fn (vals x))
                (sequential? x) (map search-fn x))))]
    (->> section search-fn flatten (remove nil?) set)))

(defn dict-sections
  "Map of :dict (or :repeating) values to section ids."
  [sections]
  (reduce (fn [acc {id :id :as section}]
            (reduce (fn [m dict]
                      (update m dict #(conj (set %) (keyword id))))
                    acc
                    (section-dicts section)))
          {}
          sections))

(defn resolve-verdict-attachment-type
  "Returns attachment type map (`:type-group`, `:type-id`) for given `type-id`. If the type is not
  found, the type for `:muu` is returned as fallback (or nil, if not even `:muu` is supported)"
  ([{:keys [permitType]} type-id]
   (let [types (att-type/attachment-types-by-permit-type (keyword permitType))]
     (some-> (some #(util/find-by-key :type-id % types) [(keyword type-id) :muu])
             (select-keys [:type-group :type-id])
             (->> (util/map-values name)))))
  ([application]
   (resolve-verdict-attachment-type application :paatos)))

(defn map->paths
  "Flattens map into paths.
  {:one 1 :two 2 :three {:four [1 2 3 4]}}
  -> ([:two 2] [:one 1] (:three :four [1 2 3 4]))"
  [m]
  (reduce-kv (fn [acc k v]
               (if (map? v)
                 (concat acc (map (partial cons k) (map->paths v)))
                 (cons [k v] acc)))
             []
             m))

(defn validate-dictionary-data
  "Validates given data against the dictionary in the schema. Returns
  validation errors as a list of lists where each item is in the
  format [error-code path] where the last path item is the
  value: [:error.invalid-value [:path :to :toggle 999]].

  Note: the data cannot contain metadata."
  [schema data & [references]]
  (->> (map->paths data)
       (map (fn [path]
              (when-let [err (validate-path-value schema
                                                  (butlast path)
                                                  (last path)
                                                  references)]
                [err path])))
       (remove nil?)
       seq))
