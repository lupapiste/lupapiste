(ns lupapalvelu.conversion.section-schemas
  (:require [lupapalvelu.pate.shared-schemas :as pate-schemas]
            [sade.shared-schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.coerce :as sco]
            [schema.core :as sc]))

(defn- name-string?
  "True if `s` a non-blank trimmed lowercase string."
  [s]
  (boolean (some-> (ss/blank-as-nil s)
                   ss/trim
                   ss/lower-case
                   (= s))))

(defn- safe-keyword?
  "Keyword that can be used as a mongo property name. While mongo supports utf8 names, we
  limit the set to a-zA-Z0-9_-."
  [k]
  (boolean (and (keyword? k)
                (re-matches #"[\w\-]+" (name k)))))

(def SafeKeyword (sc/pred safe-keyword? "Safe keyword"))

(sc/defschema L10n
  "Localization 'chain' for a section:
   1. Section `l10n` term
   2. Schema `l10n` term
   3. Regular i18n localization term."
  {(sc/optional-key :l10n) {SafeKeyword (st/optional-keys {:fi sc/Str
                                                           :sv sc/Str
                                                           :en sc/Str})}})

(declare RowBox)

(sc/defschema BoxItem
  "Layout item for the (flex)box container. Each item is either a (nested) box or dict
  reference. The latter can also have `box-css` property for classes to be added to the
  box item. The classes for the actual component (text, select, ...) are given via their
  specific schemas (toggle component is the sole exception as it receives both)."
  (sc/conditional :box {:box (sc/recursive #'RowBox)}
                  :else (st/assoc pate-schemas/PateItem
                                  (sc/optional-key :box-css) sc/Keyword)))

(sc/defschema RowBox
  "Container (box) is rendered as a flexbox. The layout has reasonable defaults that can
  be extended (but not overridden) with `:css` property."
  {:items                 [BoxItem]
   (sc/optional-key :css) sc/Keyword})

(sc/defschema SectionRow
  (sc/conditional
    :items {:items                   [BoxItem]
            (sc/optional-key :title) sc/Keyword}
    :else [BoxItem]))

(def NameStr (sc/pred name-string? "Trimmed non-blank lowercase string") )

(sc/defschema SchemaDocument
  "Section schemas are stored in `:conversion-section-schemas` mongo collection."
  (st/merge L10n
            {:id         ssc/ObjectIdStr
             :name       NameStr ; Unique
             :dictionary pate-schemas/SchemaTypes
             :rows       [SectionRow]
             ;; L10n key
             (sc/optional-key :title) sc/Keyword}))

(sc/defschema SchemaDocumentImport
  (-> SchemaDocument
      (st/dissoc :id)
      (st/assoc :op (sc/enum :add :merge :replace))
      (st/optional-keys [:dictionary :rows])))

(sc/defschema SectionData
  "Schema for the `ConversionDocument` `:sections` field."
  (st/merge L10n
            {:id                      ssc/ObjectIdStr
             ;; Unique within conversion document
             :name                    NameStr
             ;; Refers to `SchemaDocument` name
             :schema-name             NameStr
             ;; The data is validated against `SchemaDocument`.
             :data                    {sc/Keyword sc/Any}
             ;; Overrides the title defined in the `SchemaDocument`.
             (sc/optional-key :title) sc/Keyword
             :modified                ssc/Timestamp}))

(sc/defschema SectionImport
  "Add, merge/replace or delete conversion section."
  (sc/conditional
    (comp #{:delete} :op) {:op   (sc/eq :delete)
                           :name NameStr}
    :else (st/merge L10n
                    {:op                            (sc/enum :add :replace :merge)
                     :name                          NameStr
                     (sc/optional-key :schema-name) NameStr
                     (sc/optional-key :data)        {sc/Keyword sc/Any}
                     (sc/optional-key :title)       sc/Keyword})))

;; ------------------------------
;; Validators
;; ------------------------------

(def valid-schema-document! (sc/validator SchemaDocument) )

;; ------------------------------
;; Coercers
;; ------------------------------

(def ->schema-document
  (sco/coercer SchemaDocument
               (some-fn sco/json-coercion-matcher
                        {pate-schemas/css-classes util/kw-path})))

(def ->section-import (sco/coercer SectionImport sco/json-coercion-matcher))

;; ------------------------------
;; Helpers
;; ------------------------------

(defn pate-schema-type [schema]
  (util/find-first #(get schema %) pate-schemas/schema-type-keys))
