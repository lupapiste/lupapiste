(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as schema]
            [schema.experimental.generators :as generators]
            [clojure.test.check.generators :as gen]))

;;
;; Util
;;

;; Generator

(def custom-generators (atom {}))

(defn add-generator [schema generator]
  (swap! custom-generators assoc schema generator))

(defn generate
  ([schema]         (generate schema {}))
  ([schema wrapper] (generators/generate schema @custom-generators wrapper)))

(defn generator
  ([schema]         (generator schema {}))
  ([schema wrapper] (generators/generator schema @custom-generators wrapper)))

;; Predicate / constraint

(defn min-len-constraint [max-len]
  (fn [v] (when (>= (count v) max-len) v)))

(defn max-len-constraint [max-len]
  (fn [v] (when (<= (count v) max-len) v)))

(defn fixed-len-constraint [len]
  (fn [v] (when (= (count v) len) v)))

;;
;; Schemas
;; 

(schema/defschema BlankStr
  "A schema for empty or nil valued string"
  (schema/if string? (schema/pred empty?) (schema/pred nil?)))

(add-generator BlankStr (gen/elements ["" nil]))

(schema/defschema Email
  "A prismatic schema for email"
  (schema/constrained schema/Str (comp validators/valid-email? (max-len-constraint 255))))

(add-generator Email (gen/fmap
                      (fn [[name domain]]
                        (str name "@" domain ".com"))
                      (gen/tuple (gen/not-empty gen/string-alphanumeric)
                                 (gen/not-empty gen/string-alphanumeric))))


;; Dynamic schema constructors

(defn fixed-len-string-generator [len]
  (gen/bind (gen/not-empty gen/string)
            #(gen/return (apply str (take len (cycle %))))))

(defn fixed-length-string [len]
  (doto (schema/constrained schema/Str (fixed-len-constraint len))
    (add-generator (fixed-len-string-generator len))))

(defn min-len-string-generator [min-len]
  (gen/bind (gen/fmap #(+ min-len %) gen/pos-int)
            fixed-len-string-generator))

(defn min-length-string [min-len]
  (doto (schema/constrained schema/Str (min-len-constraint min-len))
    (add-generator (min-len-string-generator min-len))))

(defn max-len-string-generator [max-len]
  (gen/bind gen/string-alphanumeric 
            #(gen/return (apply str (take max-len %)))))

(defn max-length-string [max-len]
  (doto (schema/constrained schema/Str (max-len-constraint max-len))
    (add-generator (max-len-string-generator max-len))))



