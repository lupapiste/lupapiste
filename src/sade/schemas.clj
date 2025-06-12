(ns sade.schemas
  (:require [iso-country-codes.countries :as countries]
            [sade.coordinate :as coord]
            [sade.date :as date]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as validators]
            [schema.coerce :as coerce]
            [schema.core :refer [defschema] :as sc])
  (:import [clojure.lang Keyword]
           [org.bson.types ObjectId]
           [schema.core OptionalKey]))

(set! *warn-on-reflection* true)

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

(defschema Kuntanumero
  (sc/constrained sc/Str validators/kuntanumero? "Finnish municipal number"))

(defschema Maaraalatunnus
  (sc/constrained sc/Str (partial re-matches validators/maara-alatunnus-pattern) "Finnish property partition id"))

(defschema LocationX (sc/constrained sc/Num coord/valid-x? "Invalid x coordinate"))
(defschema LocationY (sc/constrained sc/Num coord/valid-y? "Invalid y coordinate"))

(defschema Location "ETRS-TM35FIN location map"
  {:x LocationX
   :y LocationY})

(def BlankStr sssc/BlankStr)
(def NonBlankStr sssc/NonBlankStr)

(defschema LocationXString  (sc/constrained NonBlankStr coord/valid-x?))
(defschema LocationYString  (sc/constrained NonBlankStr coord/valid-y?))

(def ValidX (sc/pred coord/valid-x? "Valid X coordinate"))
(def ValidY (sc/pred coord/valid-y? "Valid Y coordinate"))

(def Email sssc/Email)
(def EmailSpaced sssc/EmailSpaced)

(defschema EmailAnyCase
  "Like [[Email]], but no lowercase compulsion."
  (sc/constrained sc/Str (every-pred validators/valid-email? (max-length-constraint 255)) "Email"))

(defschema Username
  "A simple schema for username"
  (sc/constrained sc/Str (every-pred ss/in-lower-case? (max-length-constraint 255)) "Username"))

(def Timestamp sssc/Timestamp)
(def valid-timestamp! sssc/valid-timestamp!)

(defschema TimeString
  "A schema for timestring hh:mm:ss.d"
  (sc/constrained sc/Str (partial re-matches util/time-pattern) "Time string hh:mm:ss.d"))

(defschema Zipcode
  "A schema for Finnish zipcode"
  (sc/pred validators/finnish-zip? "Finnish zipcode"))

(defschema LegacyZipcode
  "A schema for Finnish zipcode but using the legacy validator"
  (sc/pred validators/five-digit-zip? "Finnish zipcode"))

(defschema FinnishY
  (sc/pred validators/finnish-y? "Finnish company code, y-code"))

(defschema FinnishOVTid
  (sc/pred validators/finnish-ovt? "Finnish OVT id"))

(defschema Hetu
  (sc/pred validators/valid-hetu? "Not valid hetu"))

(def ObjectIdField (sc/pred (fn [obj] (instance? ObjectId obj)) 'object-id?))
(def ObjectIdStr sssc/ObjectIdStr)
(def ObjectIdKeyword sssc/ObjectIdKeyword)
(def UUIDStr sssc/UUIDStr)

(def valid-object-id-str! sssc/valid-object-id-str!)
(def valid-object-id-str? sssc/valid-object-id-str?)

(defschema IpAddress
  (sc/pred validators/ip-address? "IP address"))

(def ApplicationId sssc/ApplicationId)
(def valid-application-id! sssc/valid-application-id!)

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

(defdynamicschema date-string
  ;; Each format is either a format string or `sade.date` format keyword.
  [& formats]
  (let [formatters (map #(util/pcond-> %
                           string? date/make-parser) formats)]
    (sc/constrained sc/Str (fn [s]
                             (some #(date/format? % s) formatters))
                    (str "Date string: " (ss/join ", " formats)))))

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

(def default-max-length-string (max-length-string 255))

;;
;; Other definitions
;;

(defschema AttachmentId
  (min-length-string 24))

(def Fn (sc/pred fn? "Function"))
(def IFn (sc/pred ifn? "Function"))

(sc/defschema SheriffNotes
  "For internal bookkeeping purposes in mongo."
  {(sc/optional-key :_sheriff-notes) [{:note      NonBlankStr
                                       :created   Timestamp
                                       sc/Keyword sc/Any}]})
