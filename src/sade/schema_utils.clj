(ns sade.schema-utils
  (:require [schema.core :as sc]
            [sade.util :as util])
  (:refer-clojure :exclude [keys select-keys get]))

(defn keys
  "Mimics clojure.core/keys.
  Unwraps schema.core OptionalKey and RequiredKey returning actual keyword."
  [schema]
  (map sc/explicit-schema-key (clojure.core/keys schema)))

(defn get-schema-key [schema k]
  (some
    #(when (util/=as-kw (sc/explicit-schema-key %) k)
       %)
    (clojure.core/keys schema)))

(defn select-keys
  "Selects keys from schema map. Schema key can be keyword, OptionalKey or RequiredKey."
  [schema keys]
  (reduce
    (fn [acc k]
      (if-let [key (get-schema-key schema k)]
        (assoc acc key (clojure.core/get schema key))
        acc))
    {}
    keys))

(defn get [schema k]
  (clojure.core/get schema (get-schema-key schema k)))
