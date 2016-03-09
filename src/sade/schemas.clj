(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :refer [defschema] :as sc]
            [schema.coerce :as coerce]))

;;
;; Util
;;

(defonce dynamically-created-schemas (atom {}))

(defmacro defdynamicschema [name params form]
  {:pre [(vector? params)]}
  (let [schema-key (apply vector name params)]
    `(defn ~name ~params
       (locking dynamically-created-schemas
         (or (@dynamically-created-schemas ~schema-key)
             ((swap! dynamically-created-schemas assoc ~schema-key ~form) ~schema-key))))))

;; Predicate / constraint

(defn min-length-constraint [max-len]
  (fn [v] (>= (count v) max-len)))

(defn max-length-constraint [max-len]
  (fn [v] (<= (count v) max-len)))

(defn fixed-length-constraint [len]
  (fn [v] (= (count v) len)))

;;
;; Coercion
;;

(defn json-coercer [schema]
  (coerce/coercer schema coerce/json-coercion-matcher))

;;
;; Schemas
;;

(defschema Nat 
  "A schema for natural number integer"
  (sc/constrained sc/Int (comp not neg?) "Natural number"))

(defschema Section
  "A schema for section number string"
  (sc/constrained sc/Str (partial re-matches #"§ \d+") "Section string"))

(defschema BlankStr
  "A schema for empty or nil valued string"
  (sc/if string? (sc/pred empty? "Not empty") (sc/pred nil? "Not nil")))

(defschema Email
  "A simple schema for email"
  (sc/constrained sc/Str (every-pred validators/valid-email? (max-length-constraint 255)) "Not valid email"))

(defschema Timestamp
  "A schema for timestamp"
  (sc/pred (every-pred integer?) "Not valid timestamp"))

(defschema Zipcode
  "A schema for finnish zipcode"
  (sc/pred validators/finnish-zip? "Not valid finnish zipcode"))

(defschema FinnishY
  (sc/pred validators/finnish-y? "Not valid Y code"))

(defschema FinnishOVTid
  (sc/pred validators/finnish-ovt? "Not valid finnish OVT id"))

(defschema Hetu
  (sc/pred validators/valid-hetu? "Not valid hetu"))

(defschema ObjectIdStr
  (sc/pred (partial validators/matches? #"^[0-9a-f]{24}$") "ObjectId hex string"))

(defschema IpAddress
  (sc/pred validators/ip-address? "Not valid IP address"))

;; Dynamic schema constructors

(defdynamicschema fixed-length-string [len]
  (sc/constrained sc/Str (fixed-length-constraint len)
                  (str "Not valid string with fixed length of " len)))

(defdynamicschema min-length-string [min-len]
  (sc/constrained sc/Str (min-length-constraint min-len)
                  (str "Not valid string with minimum length of " min-len)))

(defdynamicschema max-length-string [max-len]
  (sc/constrained sc/Str (max-length-constraint max-len)
                  (str "Not valid string with maximum length of " max-len)))

(defdynamicschema min-max-length-string [min-len max-len]
  (sc/constrained sc/Str (every-pred (min-length-constraint min-len) (max-length-constraint max-len))
                  (str "Not valid string with length of [" min-len "-" max-len "]")))

(defdynamicschema min-length-hex-string [min-len]
  (sc/constrained sc/Str (every-pred (min-length-constraint min-len) validators/hex-string?)
                  (str "Not valid hex-string with minimum length of " min-len)))
