(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as sc]))

;;
;; Util
;;

(def dynamically-created-schemas (atom {}))

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

(sc/defschema BlankStr
  "A schema for empty or nil valued string"
  (sc/if string? (sc/pred empty?) (sc/pred nil?)))

(sc/defschema Email
  "A simple schema for email"
  (sc/constrained sc/Str (every-pred validators/valid-email? (max-length-constraint 255))))

(sc/defschema Timestamp
  "A schema for timestamp"
  sc/Int)

;; Dynamic schema constructors

(defn fixed-length-string [len]
  (dynamic-schema [:fixed-length-string len]
                  (sc/constrained sc/Str (fixed-length-constraint len))))

(defn min-length-string [min-len]
  (dynamic-schema [:min-length-string min-len]
                  (sc/constrained sc/Str (min-length-constraint min-len))))

(defn max-length-string [max-len]
  (dynamic-schema [:max-len-string max-len]
                  (sc/constrained sc/Str (max-length-constraint max-len))))

