(ns sade.schema-utils
  (:require [schema.core :as sc])
  (:refer-clojure :exclude [keys]))

(defn keys
  "Mimics clojure.core/keys.
  Unwraps schema.core OptionalKey and RequiredKey returning actual keyword."
  [schema]
  (map sc/explicit-schema-key (clojure.core/keys schema)))
