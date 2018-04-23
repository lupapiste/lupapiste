(ns sade.shared-schemas
  (:require [schema.core :refer [defschema] :as sc]
            [sade.validators :as v]))

(defn matches? [re s] (boolean (when (string? s) (re-matches re s))))

(def Nat
  "A schema for natural number integer"
  (sc/constrained sc/Int (comp not neg?) "Natural number"))

(def object-id-pattern #"^[0-9a-f]{24}$")

(def ObjectIdStr
  (sc/pred (partial matches? object-id-pattern) "ObjectId hex string"))

(def uuid-pattern #"^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$")

(def UUIDStr
  (sc/pred (partial matches? uuid-pattern) "UUID hex string"))

(defn in-lower-case? [^String s]
  (if s
    (= s (.toLowerCase s))
    false))

(defn max-length-constraint [max-len]
  (fn [v] (<= (count v) max-len)))

(defschema Email
           "A simple schema for email"
  (sc/constrained sc/Str (every-pred v/valid-email? in-lower-case? (max-length-constraint 254)) "Email"))