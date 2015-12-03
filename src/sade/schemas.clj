(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as schema]
            [schema.experimental.generators :as generators]
            [clojure.test.check.generators :as check-generators]))


;;
;; Schemas
;; 

(schema/defschema Email
  "A prismatic schema for email"
  (schema/both (schema/pred validators/valid-email? "Not valid email")
           (util/max-length-string 255)))

(def schema-generators
  (generators/default-leaf-generators
    {Email (check-generators/fmap (fn [[name domain]]
                       (str name "@" domain ".com"))
                     (check-generators/tuple (check-generators/not-empty check-generators/string-alphanumeric)
                                (check-generators/not-empty check-generators/string-alphanumeric)))}))

(defn generate [schema]
  (generators/generate schema schema-generators))
