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
  (sc/if string? (sc/pred empty? "Not empty") (sc/pred nil? "Not nil")))

(sc/defschema Email
  "A simple schema for email"
  (sc/constrained sc/Str (every-pred validators/valid-email? (max-length-constraint 255)) "Not valid email"))

(sc/defschema Timestamp
  "A schema for timestamp"
  (sc/pred (every-pred integer?) "Not valid timestamp"))

(sc/defschema Zipcode
  "A schema for finnish zipcode"
  (sc/pred validators/finnish-zip? "Not valid finnish zipcode"))

(sc/defschema FinnishY
  (sc/pred validators/finnish-y? "Not valid Y code"))

(sc/defschema FinnishOVTid
  (sc/pred validators/finnish-ovt? "Not valid finnish OVT id"))

(sc/defschema Hetu
  (sc/pred validators/valid-hetu? "Not valid hetu"))

;; Dynamic schema constructors

(defdynamicschema fixed-length-string [len]
  (sc/constrained sc/Str (fixed-length-constraint len)))

(defdynamicschema min-length-string [min-len]
  (sc/constrained sc/Str (min-length-constraint min-len)))

(defdynamicschema max-length-string [max-len]
  (sc/constrained sc/Str (max-length-constraint max-len)))

(defdynamicschema min-max-length-string [min-len max-len]
  (sc/constrained sc/Str (every-pred (min-length-constraint min-len) (max-length-constraint max-len))))

