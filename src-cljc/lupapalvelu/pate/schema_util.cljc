(ns lupapalvelu.pate.schema-util
  "Utilities for constructing Pate schemas."
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [lupapalvelu.allu.allu-application :as allu-application]
            [lupapalvelu.pate.schema-helper :as helper]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(defn pate-assert [pred & msg]
  (let [div "\n-------------------------------------------------------\n"]
    (assert pred (str div
                      (ss/join " " msg)
                      div))))

(defn required [m]
  (assoc m :required? true))

(defn subschema-sections [{:keys [section sections]}]
  (if section
    [section]
    (or sections [])))

(defn check-dicts [dictionary form]
  (walk/prewalk (fn [x]
                  (let [dict      (:dict x)
                        repeating (:repeating x)]
                    (cond
                     repeating
                     (do
                       (check-dicts (-> dictionary
                                        repeating
                                        :repeating)
                                    (dissoc x :repeating))
                       nil)

                     dict
                     (do (pate-assert (dict dictionary)
                                      "Missing dict:" dict)
                         x)

                     :else x)))
                form))

(defn check-overlapping-dicts [subschemas]
  (->> subschemas
       (map (comp set keys :dictionary))
       (reduce (fn [acc key-set]
                 (let [overlapping (set/intersection acc key-set)]
                   (pate-assert (empty? overlapping)
                                "Overlapping dicts:" overlapping)
                   (set/union acc key-set)))
               #{})))

(defn check-unique-section-ids [sections]
  (doseq [[k v] (group-by :id sections)]
    (pate-assert (= (count v) 1) "Duplicate section id:" k)))

(defn combine-subschemas
  "Combines given subschemas into one schema. Throws if dictionary keys
  overlap or sections refer to a :dict that does not exist."
  [& subschemas]
  (check-overlapping-dicts subschemas)

  (let [schema (reduce (fn [acc {:keys [dictionary] :as subschema}]
                         (-> acc
                             (update :dictionary #(merge % dictionary))
                             (update :sections
                                     #(concat % (subschema-sections subschema)))))
                       {:dictionary {} :sections []}
                       subschemas)]
    (check-dicts (:dictionary schema)
                 (:sections schema))
    (check-unique-section-ids (:sections schema))
    schema))

;; Categories that are supported by the verdict template mechanism.
(def pate-categories   #{:r :tj :p :ya :contract :allu-contract :allu-verdict})
;; Categories that are only supported by the legacy mechanism.
(def legacy-categories #{:kt :ymp})
;; Categories that are only used with migrated legacy verdicts.
(def migration-categories #{:migration-contract :migration-verdict})
(def all-categories (set/union pate-categories legacy-categories migration-categories))

(defn pate-category?
  "True if the given category is supported by Pate verdict template
  mechanism."
  [category]
  (contains? pate-categories (keyword category)))

(defn legacy-category?
  "True if the given category is supported _only_ by legacy verdict
  mechanism."
  [category]
  (contains? legacy-categories (keyword category)))

(defn lowkeyword [s]
  (some-> s name ss/lower-case keyword))

(defn permit-type->categories
  "Categories (keywords) applicable to the given permit type. First
  category is the more typical (e.g., :ya vs. :contract)."
  [permit-type]
  (when-let [kw (lowkeyword permit-type)]
    (cond
      (= kw :r)                      [:r :tj]
      (= :p kw)                      [:p]
      (#{:ya} kw)                    [:ya :contract]
      (#{:kt :mm} kw)                [:kt]
      (#{:yi :yl :ym :vvvl :mal} kw) [:ymp])))

(defn category->permit-type
  "Default permit type for pate category. :tj, :contract and legacy category permit
  types are nil."
  [category]
  (get {:r "R" :p "P" :ya "YA"} (keyword category)))

(defn permit-subtype->category [permit-subtype]
  (when-let [kw (lowkeyword permit-subtype)]
    (cond
      (= kw :tyonjohtaja-hakemus)  :tj
      (= kw :tyonjohtaja-ilmoitus) :tj
      (= kw :sijoitussopimus)      :contract)))

(def operation-categories {:tyonjohtajan-nimeaminen-v2 :tj})

(defn category-by-operation [operation]
  (get operation-categories (lowkeyword operation)))

(defn application->category [{:keys [permitType permitSubtype organization
                                     operation-name primaryOperation]}]
  (if (allu-application/allu-application? organization permitType)
    (if (= permitSubtype "sijoitussopimus")
      :allu-contract
      :allu-verdict)
    (or (category-by-operation operation-name)
        (some-> primaryOperation :name category-by-operation)
        (permit-subtype->category permitSubtype)
        (first (permit-type->categories permitType)))))

(defn dict-resolve
  "Path format: [repeating index repeating index ... value-dict].
  Repeating denotes :repeating schema, index is arbitrary repeating
  index (skipped during resolution) and value-dict is the final dict
  for the item schema.

  Returns map with :schema and :path keys. The path is
  the remaining path (e.g., [:delta] for pate-delta). Note: the
  result is empty map if the path resolves to the repeating schema.

  Returns nil when the resolution fails."
  [path dictionary]
  (loop [[x & xs]   (->> path
                         (remove nil?)
                         (map keyword))
         dictionary dictionary]
    (when dictionary
      (if x
        (when-let [schema (get dictionary x)]
          (if (:repeating schema)
            (recur (rest xs) (:repeating schema))
            {:schema schema :path xs}))
        {}))))

(defn repeating-subpath
  "Subpath that resolves to a repeating named repeating. Nil if not
  found. Note that the actual existence of the path within data is not
  checked."
  [repeating path dictionary]
  (loop [path path]
    (cond
      (empty? path)             nil
      (= (last path) repeating) (when (= {} (dict-resolve path dictionary))
                                  path)
      :else                     (recur (butlast path)))))

(defn ya-verdict-type
  "YA verdicts come in different types. The initial value is extracted
  from the primary operation name."
  [{primary-op :primaryOperation}]
  (let [regex (->> helper/ya-verdict-types
                   (map name)
                   (ss/join "|")
                   re-pattern)]
    (re-find regex (:name primary-op))))

(defn- get-verdict-value
  "Using `rum/react` here instead of `value` ensures that the computed is updated when the base value changes"
  [data kw]
  #?(:cljs (if (map? data)
             (get data kw)
             (some-> data (rum/cursor-in [kw]) rum/react))
     :clj  (get data kw)))

(defn get-computed-value
  "Returns values that require some other operation on other field values than checking for value or existence."
  [state value-key]
  (let [verdict-code (get-verdict-value state :verdict-code)]
    (case value-key
      :negative-verdict? (helper/verdict-code-negative? verdict-code)
      :positive-verdict? (not (helper/verdict-code-negative? verdict-code)))))

(defn- get-value-as-boolean
  "Returns true if the given field's value is not empty"
  [val]
  (cond
    (coll? val)    (seq val)
    (boolean? val) val
    :else          (some-> val str ss/not-blank?)))

(defn- resolve-as-boolean
  "Returns true if the given key has a truthy value in the verdict/proposal data"
  [key data path]
  (boolean (cond
             (boolean? key) key
             (fn? key)      (key data path)
             (keyword? key) (let [[x & k] (util/split-kw-path key)]
                              (case x
                                :_computed (get-computed-value data (first k))
                                (get-value-as-boolean (get data key)))))))

(defn resolve-required?
  "True if `required?` resolution within `data` is true. See `PateRequired` for details."
  [required? data path]
  (resolve-as-boolean required? data path))

