(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as sc]))

;;
;; Util
;;

(def dynamically-created-schemas (atom {}))

(defmacro defdynamicschema [name params form]
  {:pre [(vector? params)]}
  (let [schema-key (apply vector name params)]
    `(defn ~name ~params 
       (locking dynamically-created-schemas 
         (get @dynamically-created-schemas ~schema-key
              ((swap! dynamically-created-schemas assoc ~schema-key ~form) ~schema-key))))))

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

(defdynamicschema fixed-length-string [len]
  (sc/constrained sc/Str (fixed-length-constraint len)))

(defdynamicschema min-length-string [min-len]
  (sc/constrained sc/Str (min-length-constraint min-len)))

(defdynamicschema max-length-string [max-len]
  (sc/constrained sc/Str (max-length-constraint max-len)))

