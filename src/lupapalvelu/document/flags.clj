(ns lupapalvelu.document.flags
  "Utilities for detecting `:schema-include` or `:schema-exclude` flags in Docgen schemas")

(defprotocol FindFlags
  (find-flags [item path]
    "Walk through the schema and locate elements guarded by `:schema-include` or `:schema-exclude`"))

(extend-protocol FindFlags

  java.util.Map
  (find-flags [item path]
    (let [include (:schema-include item)
          exclude (:schema-exclude item)]
      (cond-> []
        include (conj {:path path
                       :flag include
                       :op   :include})
        exclude (conj {:path path
                       :flag exclude
                       :op   :exclude})
        ;; Flags may appear within maps so must recursively search
        true    (into (->> item
                           (mapcat (fn [[k v]]
                                     (find-flags v (conj path k)))))))))

  java.util.List
  (find-flags [item path]
    (->> item
         (map-indexed (fn [k v]
                        (find-flags v (conj path k))))
         (mapcat identity)
         (into [])))

  java.util.Set
  (find-flags [item path]
    ;; We cannot represent paths within sets as they have no order.
    ;; We can still check for the flags within and raise an error if any are found
    (let [inner-paths (->> item
                           (map-indexed (fn [k v]
                                          (find-flags v (conj path k))))
                           (mapcat identity)
                           (into []))]
      (when (seq inner-paths)
        (throw (ex-info "Cannot report paths within sets" {:paths inner-paths})))))

  java.lang.Object
  (find-flags [_ _] nil)

  nil
  (find-flags [_ _] nil))

(defn schema->flag-paths
  "Given a schema, return paths guarded by `:schema-include` or `:schema-exclude`.
  Returns a map of include/exclude -> flags -> paths."
  [schema]
  {:pre [(map? schema)]}
  (when-let [paths (not-empty (find-flags schema []))]
    (reduce
      (fn [paths {:keys [op flag path]}]
        (update-in paths [op flag] (fnil conj []) path))
      {}
      paths)))

;; Add / get / remove flag information to schemas

(defn with-flag-paths
  "Add path information about the `:schema-include`/`:schema-exclude` flags into the schema."
  [schema]
  (assoc-in schema [:info :flags] (or (schema->flag-paths schema) {})))

(defn get-flag-paths
  "Extract the flag path information."
  [schema]
  (get-in schema [:info :flags]))

(defn clear-flag-paths
  "Remove the flag path information from a schema"
  [schema]
  (cond-> schema
    (:info schema) (update :info dissoc :flags)))
