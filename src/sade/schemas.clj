(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as validators]
            [sade.strings :as ss]
            [clj-time.format :as ctf]
            [schema.core :refer [defschema] :as sc]
            [schema.coerce :as coerce])
  (:import  [org.joda.time IllegalFieldValueException]))

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

(defprotocol SchemaKey
  (plain-key [key]))

(extend-protocol SchemaKey
  schema.core.OptionalKey
  (plain-key [key] (.-k key))
  clojure.lang.Keyword
  (plain-key [key] key)
  java.lang.String
  (plain-key [key] (keyword key))
  java.lang.Number
  (plain-key [key] (keyword (str key)))
  java.lang.Character
  (plain-key [key] (keyword (str key)))
  java.lang.Object
  (plain-key [key] key))

(defn plain-keys
  "Returns all keys for schema as keywords without optional-key wrapper."
  [schema]
  (->> (keys schema)
       (map plain-key)))

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

(defschema IntString
  "A schema for string containing single integer"
  (sc/constrained sc/Str (partial re-matches #"^-?\d+$") "Integer string"))

(defschema NatString
  "A schema for string containing natural number"
  (sc/constrained sc/Str (partial re-matches #"^\d+$") "Natural number string"))

(defschema DecimalString
  "A schema for string containing decimal number"
  (sc/constrained sc/Str (partial re-matches #"^-?\d+(.\d+)?$") "Decimal string"))

(defschema Digit
  "A schema for string containing single digit"
  (sc/constrained sc/Str (partial re-matches #"^\d$") "Single digit string"))

(defschema Letter
  "A schema for string containing single letter"
  (sc/constrained sc/Str (partial re-matches #"\p{L}") "Single letter"))

(defschema UpperCaseLetter
  "A schema for string containing single upper case letter"
  (sc/constrained sc/Str (partial re-matches #"\p{Lu}") "Single upper case letter"))

(defschema LowerCaseLetter
  "A schema for string containing single lower case letter"
  (sc/constrained sc/Str (partial re-matches #"\p{Ll}") "Single lower case letter"))

(defschema Tel
  (sc/constrained sc/Str (partial re-matches #"^\+?[\d\s-]+") "Telephone number"))

(defschema HttpUrl
  (sc/constrained sc/Str validators/http-url? "HTTP(S) URL"))

(defschema Rakennusnumero
  (sc/constrained sc/Str validators/rakennusnumero? "Finnish building number"))

(defschema Rakennustunnus
  (sc/constrained sc/Str validators/rakennustunnus? "Finnish building id"))

(defschema Kiinteistotunnus
  (sc/constrained sc/Str validators/kiinteistotunnus? "Finnish property id"))

(defschema Maaraalatunnus
  (sc/constrained sc/Str (partial re-matches validators/maara-alatunnus-pattern) "Finnish property partition id"))

(defschema BlankStr
  "A schema for empty or nil valued string"
  (sc/if string? (sc/pred empty? "Empty string") (sc/pred nil? "Nil")))

(defschema Email
  "A simple schema for email"
  (sc/constrained sc/Str (every-pred validators/valid-email? ss/in-lower-case? (max-length-constraint 255)) "Email"))

(defschema Username
  "A simple schema for username"
  (sc/constrained sc/Str (every-pred ss/in-lower-case? (max-length-constraint 255)) "Username"))

(defschema Timestamp
  "A schema for timestamp"
  (sc/pred (every-pred integer?) "Timestamp (long)"))

(defschema TimeString
  "A schema for timestring hh:mm:ss.d"
  (sc/constrained sc/Str (partial re-matches util/time-pattern) "Time string hh:mm:ss.d"))

(defschema Zipcode
  "A schema for Finnish zipcode"
  (sc/pred validators/finnish-zip? "Finnish zipcode"))

(defschema FinnishY
  (sc/pred validators/finnish-y? "Finnish company code, y-code"))

(defschema FinnishOVTid
  (sc/pred validators/finnish-ovt? "Finnish OVT id"))

(defschema Hetu
  (sc/pred validators/valid-hetu? "Not valid hetu"))

(defschema ObjectIdStr
  (sc/pred (partial validators/matches? #"^[0-9a-f]{24}$") "ObjectId hex string"))

(defschema IpAddress
  (sc/pred validators/ip-address? "IP address"))

(defschema ApplicationId
  (sc/pred validators/application-id? "Application ID"))

;; Schemas for blank or valid values

(sc/defschema OptionalHttpUrl
  (sc/if ss/blank? BlankStr HttpUrl))

(sc/defschema OptionalEmail
  (sc/if ss/blank? BlankStr Email))

;;
;; Dynamic schema constructors
;;

(defdynamicschema date-string [format]
  (let [formatter (ctf/formatter format)]
    (sc/constrained sc/Str #(try (ctf/parse formatter %)
                                 (catch IllegalFieldValueException e false))
                    (str "Date string " format))))

(defdynamicschema fixed-length-string [len]
  (sc/constrained sc/Str (fixed-length-constraint len)
                  (str "String, fixed length of " len)))

(defdynamicschema min-length-string [min-len]
  (sc/constrained sc/Str (min-length-constraint min-len)
                  (str "String, minimum length of " min-len)))

(defdynamicschema max-length-string [max-len]
  (sc/constrained sc/Str (max-length-constraint max-len)
                  (str "String, maximum length of " max-len)))

(defdynamicschema min-max-length-string [min-len max-len]
  (sc/constrained sc/Str (every-pred (min-length-constraint min-len) (max-length-constraint max-len))
                  (str "String, min-max bounded length of [" min-len "-" max-len "]")))

(defdynamicschema min-length-hex-string [min-len]
  (sc/constrained sc/Str (every-pred (min-length-constraint min-len) validators/hex-string?)
                  (str "Hex-string, minimum length of " min-len)))

(defdynamicschema min-max-valued-integer-string [min max]
  (sc/constrained IntString (every-pred #(if min (<= min (util/->int %)) true) #(if max (>= max (util/->int %)) true))
                  (format "Min max valued integer string with values [%d-%d]" min max)))

(defdynamicschema min-max-valued-decimal-string [min max]
  (sc/constrained DecimalString (every-pred #(if min (<= min (util/->double %)) true) #(if max (>= max (util/->double %)) true))
                  (format "Min max valued decimal string with values [%d-%d]" min max)))
