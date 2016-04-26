(ns lupapalvelu.application-schema
  (:require [schema.core :refer [defschema] :as sc]))

(defschema ApplicationId
  (sc/constrained sc/Str (partial re-matches #"LP-\d{3}-\d{4}-\d{5}") "Application id"))
