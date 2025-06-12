(ns lupapalvelu.invoices.integration.sap-sbo.sap-test-utils
  "Helper function(s) for traversing schemas"
  (:require [schema-tools.walk :as stw]
            [schema.core :as sc]))

(extend-protocol stw/WalkableSchema
  ;; Add handling for `OptionalKey` as `schema-tools.walk` doesn't
  ;; by default so it interprets them as simple `{:k :some-key}` maps
  ;; => walking the schema would produce unexpected `:k` schemas.
  schema.core.OptionalKey
  (-walk [this inner outer]
    (outer (with-meta (sc/optional-key (inner (:k this))) (meta this)))))

(defn schema->keys
  "Given a `schema`, recursively extract all map keys."
  [schema]
  (let [*keys (atom #{})]
    (stw/prewalk (fn [schema]
                   (when (keyword? schema)
                     (swap! *keys conj schema))
                   schema)
                 schema)
    (deref *keys)))
