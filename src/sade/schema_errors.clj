(ns sade.schema-errors
  (:require [schema.core :as sc]
            [schema.utils :as su])
  (:import [clojure.lang Keyword Symbol]
           [schema.core Constrained EnumSchema]
           [schema.utils NamedError ValidationError]))

(set! *warn-on-reflection* true)

(defn- flatten-map
  "Flattens a nested shallow map

  ```clojure
  (= (flatten-map {:a {:x {:i 3}
                       :y 4}
                   :b 5})
     {[:a :x :i] 3
      [:a :y]    4
      [:b]       6})
  ```"
  ([m]
   (flatten-map m []))
  ([m path]
   (reduce-kv
     (fn [m k v]
       (let [path (conj path k)]
         (if (map? v)
           (merge m (flatten-map v path))
           (assoc m path v))))
     {}
     m)))

(defprotocol ErrorFieldName
  (-field->name [field]
    "Transform schema validation error field to name"))

(extend-protocol ErrorFieldName
  Object
  (-field->name [field]
    (str field))
  Keyword
  (-field->name [field]
    (name field))
  Long
  (-field->name [field]
    field)
  NamedError
  (-field->name [field]
    (-field->name (.-error field)))
  ValidationError
  (-field->name [field]
    (.-value field)))

(defn make-field
  [fields]
  (mapv -field->name fields))

(defprotocol AnalyzeError
  (-error->data [error field]
    "Transform schema validation errors into data"))

(extend-protocol AnalyzeError
  String
  (-error->data [error field]
    {:field field
     :error error})
  Symbol
  (-error->data [error field]
    {:field field
     :error (str error)})
  NamedError
  (-error->data [error field]
    (let [[_ _ named] (su/named-error-explain error)]
      {:field    field
       :error    "not-valid"
       :expected named}))
  ValidationError
  (-error->data [error field]
    (let [schema (.-schema error)]
      (merge {:field field
              :error "not-valid"
              :value (.-value error)}
             (cond
               (instance? EnumSchema schema)  {:expected (->> (sc/explain schema)
                                                              rest
                                                              sort
                                                              vec)}
               (instance? Constrained schema) {:expected (let [[_ _root-schema message] (sc/explain schema)]
                                                           message)})))))

(defn error-or-errors->data
  "Create a sequence of error maps from a schema validation error.
   In other words, explains the results of `schema.core/check(er)`.
   The validation error shape depends on the shape of the original data.
   This function is not comprehensive but handles the common cases."
  [errors & paths]
  (cond
    ;; The error relates to some key in a map?
    (map? errors)
    (->> errors
         flatten-map
         (mapcat (fn [[fields error]]
                   (let [paths (concat paths fields)]
                     (if (satisfies? AnalyzeError error)
                       [(-error->data error (make-field paths))]
                       (apply error-or-errors->data error paths)))))
         vec)
    ;; The error relates to a item in a collection?
    (sequential? errors)
    (->> errors
         (mapcat (fn [index error]
                   (when error
                     (let [paths (concat paths [index])]
                       (if (satisfies? AnalyzeError error)
                         [(-error->data error (make-field paths))]
                         (apply error-or-errors->data error paths)))))
                 (range))
         vec)
    :else
    [(-error->data errors (make-field paths))]))

;;; Schema helpers which can produce better errors when analyzed with `sc/check(er)`

;; Predicate functions are opaque, so it's hard to diagnose why the data did not
;; satisfy the predicate. Especially because any anonymous predicate fn will get
;; a munged name like `sade.schema-errors-test$fn--147130$fn--147131/pred--147132`
;; To make the predicate more transparent, we can use the `pred-name` field to store
;; a data representation of the predicate.

(defn pattern-str
  "Schema for a `String` which matches the `pattern` regular expression."
  [pattern]
  (sc/constrained sc/Str (fn [s] (re-matches pattern s)) [:pattern (str pattern)]))

(defn fixed-str
  "Schema for a `String` of exactly `n` characters."
  [n]
  (sc/constrained sc/Str (fn [s] (= (count s) n)) [:fixed n]))

(defn min-max-str
  "Schema for a `String` with from `n` to `m` characters."
  [n m]
  (sc/constrained sc/Str (fn [s] (<= n (count s) m)) [:min-max-str n m]))

(defn max-str
  "Schema for a `String` of at most `n` characters."
  [n]
  (sc/constrained sc/Str (fn [s] (<= (count s) n)) [:max-str n]))

(defn not-empty-seq
  "Like `[(sc/one schema)]`, but gives better error messages."
  [schema]
  (sc/constrained [schema] seq [:not-empty]))

(defn has-one-of
  "Schema for a map, which has at least one of `required-keys`."
  [schema required-keys]
  (let [required (set required-keys)]
    (sc/constrained schema
                    (fn [m] (and (map? m)
                                 (some required (keys m))))
                    [:required-keys required])))
