(ns sade.shared-schemas
  (:require [sade.shared-strings :as ss]
            [sade.validators :as v]
            [schema.core :refer [defschema] :as sc]))

(defn matches? [re s] (boolean (when (string? s) (re-matches re s))))

(defschema Nat
  "A schema for natural number integer"
  (sc/constrained sc/Int (comp not neg?) "Natural number"))

(defschema PosInt
  "A schema for positive integer"
  (sc/constrained sc/Int pos? "Positive integer"))

(def object-id-pattern #"^[0-9a-f]{24}(?:-pdfa)?$")

(defschema ObjectIdStr
  (sc/pred (partial matches? object-id-pattern) "ObjectId hex string"))

(defschema ObjectIdKeyword
  (sc/pred #(and (keyword? %)
                 (matches? object-id-pattern (name %)))
           "ObjectId hex keyword"))

(def uuid-pattern #"^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}(?:-pdfa)?$")

(defschema UUIDStr
  (sc/pred (partial matches? uuid-pattern) "UUID hex string"))

(defschema FileId
  (sc/cond-pre UUIDStr ObjectIdStr))

(defn max-length-constraint [max-len]
  (fn [v] (<= (count v) max-len)))

(defschema Email
  "A simple schema for email"
  (sc/constrained sc/Str (every-pred v/valid-email? ss/in-lower-case? (max-length-constraint 255)) "Email"))

(defschema EmailSpaced
  "Like `Email` but allows surrounding whitespace"
  (sc/pred #(->> % ss/trim (sc/check Email) nil?)))

(defschema StorageSystem
  (sc/enum :mongodb :s3 :gcs "mongodb" "s3" "gcs"))

(defschema BlankStr
  "A schema for empty or nil valued string"
  (sc/if string? (sc/pred empty? "Empty string") (sc/pred nil? "Nil")))

(defschema NonBlankStr
  (sc/constrained sc/Str ss/not-blank? "Non-blank string"))

(defschema Timestamp
  "A schema for timestamp. Min date 1.1.1900 and max date is 31.12.9999."
  (sc/constrained sc/Int (every-pred (partial < -2208988800000) (partial > 253402214400000)) "Timestamp (long)"))
