(ns sade.shared-schemas
  (:require [schema.core :refer [defschema] :as sc]))

(defn matches? [re s] (boolean (when (string? s) (re-matches re s))))

(def Nat
  "A schema for natural number integer"
  (sc/constrained sc/Int (comp not neg?) "Natural number"))

(def object-id-pattern #"^[0-9a-f]{24}$")

(def ObjectIdStr
  (sc/pred (partial matches? object-id-pattern) "ObjectId hex string"))
