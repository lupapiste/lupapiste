(ns sade.shared-schemas
  (:require [schema.core :refer [defschema] :as sc]))

(defn matches? [re s] (boolean (when (string? s) (re-matches re s))))

(def Nat
  "A schema for natural number integer"
  (sc/constrained sc/Int (comp not neg?) "Natural number"))

(def ObjectIdStr
  (sc/pred (partial matches? #"^[0-9a-f]{24}$") "ObjectId hex string"))
