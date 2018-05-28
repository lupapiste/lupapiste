(ns lupapalvelu.pate.schema-util
  "Utilities for constructing Pate schemas."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [clojure.walk :as walk]))

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
