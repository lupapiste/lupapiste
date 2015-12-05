(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as schema]
            [schema.experimental.generators :as generators]
            [clojure.string]
            [clojure.test.check.generators :as gen]))

;;
;; Util
;;

;; Generator

(def dynamically-created-schemas (atom {}))
(def custom-generators (atom {}))

(defn add-generator [schema generator]
  (swap! custom-generators assoc schema generator))

(defn dynamic-schema 
  ([schema-key predicate]
   {:pre [(keyword? schema-key)]}
   (locking dynamically-created-schemas
     (get @dynamically-created-schemas schema-key 
          (-> (swap! dynamically-created-schemas assoc schema-key predicate) schema-key))))
  ([schema-key predicate generator]
   (doto (dynamic-schema schema-key predicate)
     (add-generator generator))))

(defn generate
  ([schema]         (generate schema {}))
  ([schema wrappers] (generators/generate schema @custom-generators wrappers)))

(defn generator
  ([schema]         (generator schema {}))
  ([schema wrappers] (generators/generator schema @custom-generators wrappers)))

;; Predicate / constraint

(defn min-len-constraint [max-len]
  (fn [v] (>= (count v) max-len)))

(defn max-len-constraint [max-len]
  (fn [v] (<= (count v) max-len)))

(defn fixed-len-constraint [len]
  (fn [v] (= (count v) len)))

;;
;; Schemas
;; 

(schema/defschema BlankStr
  "A schema for empty or nil valued string"
  (schema/if string? (schema/pred empty?) (schema/pred nil?)))

(add-generator BlankStr (gen/elements ["" nil]))

(schema/defschema Email
  "A simple schema for email"
  (schema/constrained schema/Str (every-pred validators/valid-email? (max-len-constraint 255))))

(add-generator Email (gen/fmap
                      (fn [[name domain]]
                        (str name "@" domain ".com"))
                      (gen/tuple (gen/not-empty gen/string-alphanumeric)
                                 (gen/not-empty gen/string-alphanumeric))))

(schema/defschema Timestamp
  "A schema for timestamp"
  schema/Int)

(add-generator Timestamp (gen/fmap (partial + 1450000000000)
                                   gen/large-integer))

;; Dynamic schema constructors

(defn fixed-len-string-generator [len]
  (gen/fmap clojure.string/join 
            (gen/vector gen/char len)))

(defn fixed-length-string [len]
  (dynamic-schema (keyword (str "fixed-len-string-" len))
                  (schema/constrained schema/Str (fixed-len-constraint len))
                  (fixed-len-string-generator len)))

(defn min-len-string-generator [min-len]
  (gen/bind (gen/fmap #(+ min-len %) gen/pos-int)
            fixed-len-string-generator))

(defn min-length-string [min-len]
  (dynamic-schema (keyword (str "min-len-string-" min-len))
                  (schema/constrained schema/Str (min-len-constraint min-len))
                  (min-len-string-generator min-len)))

(defn max-len-string-generator [max-len]
  (gen/fmap clojure.string/join
            (gen/vector gen/char 0 max-len)))

(defn max-length-string [max-len]
  (dynamic-schema (keyword (str "max-len-string-" max-len))
                  (schema/constrained schema/Str (max-len-constraint max-len))
                  (max-len-string-generator max-len)))



