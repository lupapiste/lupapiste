(ns sade.schemas
  (:require [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as schema]
            [schema.experimental.generators :as generators]
            [clojure.test.check.generators :as check-generators]))
;;
;; Util
;;


(def min-length (memoize
                  (fn [min-len]
                    (schema/pred
                      (fn [v]
                        (>= (count v) min-len))
                      (str "Shorter than " min-len)))))

(def max-length (memoize
                  (fn [max-len]
                    (schema/pred
                      (fn [v]
                        (<= (count v) max-len))
                      (str "Longer than " max-len)))))

(defn min-length-string [min-len]
  (schema/both schema/Str (min-length min-len)))

(defn max-length-string [max-len]
  (schema/both schema/Str (max-length max-len)))

(defn fixed-length-string [len]
  (schema/both (min-length-string len) (max-length-string len)))

;;
;; Schemas
;; 

(schema/defschema Email
  "A prismatic schema for email"
  (schema/both (schema/pred validators/valid-email? "Not valid email")
               (max-length-string 255)))

;;
;; Generators
;;

(def schema-generators
  (generators/default-leaf-generators
    {Email (check-generators/fmap (fn [[name domain]]
                       (str name "@" domain ".com"))
                     (check-generators/tuple (check-generators/not-empty check-generators/string-alphanumeric)
                                (check-generators/not-empty check-generators/string-alphanumeric)))}))

(defn generate [schema]
  (generators/generate schema schema-generators))
