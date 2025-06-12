(ns lupapalvelu.ui.docgen.loc
  "Utility functions for managing translation keys"
  (:require [lupapalvelu.ui.docgen.path :as path]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- loc-path
  "L10N key for a path pointing to a field in a document
  See e.g. the various `loc*` functions in `docutils.js`
  or `makeLabel` in `docmodel.js` for the original JS code."
  [doc-i18n-name path]
  (let [new-path (->> (cons doc-i18n-name path)
                      ;; Drop indexes if we're pointing to a field within a table
                      ;; or a repeating grouop.
                      path/path-without-indexes
                      (into []))]
    (->> new-path
         (map name)
         (ss/join "."))))

(defn loc-key
  "L10N key for a field
  * `doc-i18n-name`: Name of the document (e.g. `uusiRakennus`).
  * `path`: Path to the field. Should already include the field name.
  * `schema`: (optional) The schema of the field."
  ([doc-i18n-name path]
   (loc-key doc-i18n-name path nil))
  ([doc-i18n-name path schema]
   {:pre [doc-i18n-name (vector? path) (or (nil? schema)
                                           (map? schema))]}
   (or (:i18nkey schema) ; Field itself specifies the key
       (loc-path doc-i18n-name path))))

(defn apply-parent-i18nkey
  "If the parent schema contains a `i18nkey` override, it also applies to the child (rows)."
  [child-schema parent-schema]
  {:pre [(map? child-schema) (map? parent-schema)]}
  (let [new-key (str (:i18nkey parent-schema) "." (:name child-schema))]
    (cond-> child-schema
      (:i18nkey parent-schema) (update :i18nkey (fnil identity new-key)))))

(defn loc-key-with-suffix
  [suffix-kw read-schema? doc-i18n-name path schema]
  (or (and
        ;; Some use cases ignore the `i18nkey`
        read-schema?
        (:i18nkey schema))
      (str (loc-key doc-i18n-name path schema) "." (name suffix-kw))))

;; Helpers for producing semantic labels

(def loc-key-group-label (partial loc-key-with-suffix :_group_label true))
(def loc-key-copy-label (partial loc-key-with-suffix :_copy_label false))
(def loc-key-append-label (partial loc-key-with-suffix :_append_label false))
(def loc-key-help-label (partial loc-key-with-suffix :help false))

(defn loc-key-help-title
  [doc-i18n-name path schema]
  (if (util/includes-as-kw? #{:select :group} (:type schema))
    (loc-key-group-label doc-i18n-name path schema)
    (loc-key doc-i18n-name path schema)))
