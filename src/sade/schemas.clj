(ns sade.schemas
  (:require [clj-time.core :as ct]
            [clj-time.format :as ctf]
            [iso-country-codes.countries :as countries]
            [sade.coordinate :as coord]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as validators]
            [schema.coerce :as coerce]
            [schema.core :refer [defschema] :as sc])
  (:import [clojure.lang Keyword]
           [org.joda.time IllegalFieldValueException]
           [schema.core OptionalKey]))

;;
;; Util
;;

(defonce dynamically-created-schemas (atom {}))

(defmacro defdynamicschema [name params form]
  {:pre [(vector? params)]}
  `(defn ~name [& [~@params :as args#]]
     (let [schema-key# (apply vector ~name args#)]
       (locking dynamically-created-schemas
         (or (@dynamically-created-schemas schema-key#)
             ((swap! dynamically-created-schemas assoc schema-key# ~form) schema-key#))))))

(defprotocol SchemaKey
  (plain-key [key]))

(extend-protocol SchemaKey
  OptionalKey
  (plain-key [key] (.-k key))
  Keyword
  (plain-key [key] key)
  String
  (plain-key [key] (keyword key))
  Number
  (plain-key [key] (keyword (str key)))
  Character
  (plain-key [key] (keyword (str key)))
  Object
  (plain-key [key] key))

(defn plain-keys
  "Returns all keys for schema as keywords without optional-key wrapper."
  [schema]
  (->> (keys schema)
       (map plain-key)))

;; Predicate / constraint

(defn min-length-constraint [max-len]
  (fn [v] (>= (count v) max-len)))

(def max-length-constraint sssc/max-length-constraint)

(defn fixed-length-constraint [len]
  (fn [v] (= (count v) len)))

;;
;; Coercion
;;

(defn json-coercer [schema]
  (coerce/coercer schema coerce/json-coercion-matcher))

(defn json-coercer! [schema]
  (coerce/coercer! schema coerce/json-coercion-matcher))

;;
;; Schemas
;;

(def Nat sssc/Nat)

(defn nat-keyword? [kw]
  (and (nil? (namespace kw))
       (some? (util/->int kw nil))))

(defschema NatKeyword (sc/constrained sc/Keyword nat-keyword?))

(def PosInt sssc/PosInt)

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

(defschema LocationX (sc/constrained sc/Num coord/valid-x? "Invalid x coordinate"))
(defschema LocationY (sc/constrained sc/Num coord/valid-y? "Invalid y coordinate"))

(defschema Location "ETRS-TM35FIN location map"
  {:x LocationX
   :y LocationY})

(def BlankStr sssc/BlankStr)
(def NonBlankStr sssc/NonBlankStr)

(def Email sssc/Email)
(def EmailSpaced sssc/EmailSpaced)

(defschema EmailAnyCase
  "Like [[Email]], but no lowercase compulsion."
  (sc/constrained sc/Str (every-pred validators/valid-email? (max-length-constraint 255)) "Email"))

(defschema Username
  "A simple schema for username"
  (sc/constrained sc/Str (every-pred ss/in-lower-case? (max-length-constraint 255)) "Username"))

(def Timestamp sssc/Timestamp)

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

(def ObjectIdStr sssc/ObjectIdStr)
(def ObjectIdKeyword sssc/ObjectIdKeyword)
(def UUIDStr sssc/UUIDStr)

(defschema IpAddress
  (sc/pred validators/ip-address? "IP address"))

(defschema ApplicationId
  (sc/pred validators/application-id? "Application ID"))

(defschema ISO-3166-alpha-2
  "Two letter country code (e.g. 'FI')"
  (apply sc/enum (map :alpha-2 countries/countries)))

;; Schemas for blank or valid values

(sc/defschema OptionalHttpUrl
  (sc/if ss/blank? BlankStr HttpUrl))

(sc/defschema OptionalEmail
  (sc/if ss/blank? BlankStr Email))

;;
;; Dynamic schema constructors
;;

(defdynamicschema date-string [& formats]
  (let [formatter (if (< (count formats) 2)                 ; HACK: Workaround for the ctf/formatter arities SNAFU
                    (apply ctf/formatter formats)
                    (apply ctf/formatter ct/utc formats))]
    (sc/constrained sc/Str #(try (ctf/parse formatter %)
                                 (catch IllegalFieldValueException _ false))
                    (apply str "Date string " formats))))

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

;;
;; Other definitions
;;

(defschema AttachmentId
  (min-length-string 24))
