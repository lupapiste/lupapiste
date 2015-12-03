(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as v]
            [schema.core :as sc]
            [schema.experimental.generators :as generators]
            [clojure.test.check.generators :as check-generators]))


;;
;; Schemas
;; 

(sc/defschema Email
  "A prismatic schema for email"
  (sc/both (sc/pred v/valid-email? "Not valid email")
           (util/max-length-string 255)))

(def schema-generators
  (generators/default-leaf-generators
    {Email (check-generators/fmap (fn [[name domain]]
                       (str name "@" domain ".com"))
                     (check-generators/tuple (check-generators/not-empty check-generators/string-alphanumeric)
                                (check-generators/not-empty check-generators/string-alphanumeric)))}))

(defn generate [schema]
  (generators/generate schema schema-generators))
