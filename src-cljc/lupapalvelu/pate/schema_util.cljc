(ns lupapalvelu.pate.schema-util
  "Utilities for constructing Pate schemas."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.allu.allu-application :as allu-application]))

(defn pate-assert [pred & msg]
  (let [div "\n-------------------------------------------------------\n"]
    (assert pred (str div
                      (s/join " " msg)
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
(def pate-categories   #{:r :tj :p :ya :contract :allu-contract})
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

(defn- lowkeyword [s]
  (some-> s name s/lower-case keyword))

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
      (= kw :tyonjohtaja-hakemus) :tj
      (= kw :sijoitussopimus)     :contract)))

(def operation-categories {:tyonjohtajan-nimeaminen-v2 :tj})

(defn category-by-operation [operation]
  (get operation-categories (lowkeyword operation)))

(defn application->category [{:keys [permitType permitSubtype organization]}]
  (if (allu-application/allu-application? organization permitType)
    :allu-contract
    (or (permit-subtype->category permitSubtype)
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
      (empty? path)           nil
      (= (last path)
         repeating) (when (= (dict-resolve path dictionary)
                             {})
                      path)
      :else                   (recur (butlast path)))))

(defn ya-verdict-type
  "YA verdicts come in different types. The initial value is extracted
  from the primary operation name."
  [{primary-op :primaryOperation}]
  (let [regex (->> helper/ya-verdict-types
                   (map name)
                   (s/join "|")
                   re-pattern)]
    (re-find regex (:name primary-op))))
