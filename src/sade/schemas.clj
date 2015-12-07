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

(def dynamically-created-schemas (atom {}))
(def custom-generators (atom {}))

(defn dynamic-schema 
  ([schema-key predicate]
   (locking dynamically-created-schemas
     (get @dynamically-created-schemas schema-key 
          ((swap! dynamically-created-schemas assoc schema-key predicate) schema-key)))))

;; Predicate / constraint

(defn min-length-constraint [max-len]
  (fn [v] (>= (count v) max-len)))

(defn max-length-constraint [max-len]
  (fn [v] (<= (count v) max-len)))

(defn fixed-length-constraint [len]
  (fn [v] (= (count v) len)))

;;
;; Schemas
;; 

(schema/defschema BlankStr
  "A schema for empty or nil valued string"
  (schema/if string? (schema/pred empty?) (schema/pred nil?)))

(schema/defschema Email
  "A simple schema for email"
  (schema/constrained schema/Str (every-pred validators/valid-email? (max-len-constraint 255))))

(schema/defschema Timestamp
  "A schema for timestamp"
  schema/Int)

;; Dynamic schema constructors

(defn fixed-length-string [len]
  (dynamic-schema [:fixed-length-string len]
                  (schema/constrained schema/Str (fixed-length-constraint len))))

(defn min-length-string [min-len]
  (dynamic-schema [:min-length-string min-len]
                  (schema/constrained schema/Str (min-length-constraint min-len))))

(defn max-length-string [max-len]
  (dynamic-schema [:max-len-string max-len]
                  (schema/constrained schema/Str (max-length-constraint max-len))))

