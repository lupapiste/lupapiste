(ns lupapalvelu.conversion.section
  "Conversion section in the application UI contains data that is neither a) part of the
  regular application data model and b) not part of the initial KuntaGML conversion. Since
  data is imported separately and its shape is not predetermined, the section mechanism
  must allow some flexibility. The implementation consists of three parts:

  - Data schemas are stored in `:conversion-section-schemas` collection in mongo. The
  schemas are built on top of (some) Pate schemas and the same data validation/processing
  mechanism is used. The schemas define both the supported data types and the UI layout.

  - The actual data is stored into the `:sections` field of the conversion documents. Each
  section can refer to different schemas.

  - The frontend receives both the schemas and sections and renders the UI
  accordingly (very much like Pate)."
  (:require [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.import.shared :as shared]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.conversion.section-schemas :as sec-sc]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.metadata :as md]
            [lupapalvelu.pate.schemas :as pate-sc]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [swiss.arrows :refer :all]))

(sc/defn ^:always-validate read-schema :- (sc/maybe sec-sc/SchemaDocument)
  "Returns the schema named `schema-name`. Since names are unique, the result is
  unambiguous. If not found, either nil is returned or an exception thrown, depending on
  `throw-if-not-found?` (default false)."
  ([schema-name :- sec-sc/NameStr throw-if-not-found? :- sc/Bool]
   (or (some->> (mongo/select-one :conversion-section-schemas
                                  {:name schema-name})
                sec-sc/->schema-document)
       (when throw-if-not-found?
         (throw (ex-info (format "Schema %s not found." schema-name)
                         {:schema schema-name})))))
  ([schema-name] (read-schema schema-name false)))

(defn no-duplicate-names!
  "Throws if there are any duplicate `name` values in `imports`. Otherwise returns
  `imports`."
  [imports]
  (doseq [[s n] (frequencies (map :name imports))
          :when (> n 1)]
    (throw (ex-info (format "Duplicate name %s in imports." s)
                    {:name s})))
  imports)

(sc/defn ^:always-validate  import-schema-document :- sec-sc/SchemaDocument
  "Inserts new `SchemaDocument` and returns it. Depending on `op` can either add new
  schema (default) or merge/replace an existing one.

  `:merge` deep-merges `fields` with the document. However, `:rows` field is fully
  replaced if it is part of fields since the its merge semantics could became quite hairy.

  `:replace` replaces document fields with their `fields` counterparts. Other fields are
  left untouched.

 Merge and replace function like add if no old document is found. Throws on failure."
  [{schema-name :name op :op :as fields} :- sec-sc/SchemaDocumentImport]
  (let [fields                   (dissoc fields :op)
        {doc-id :id :as old-doc} (read-schema schema-name)]
    (case [(boolean old-doc) op]

      [true :add]
      (throw (ex-info (format "Schema %s already exists (%s)."
                              schema-name doc-id)
                      {:fields fields}))

      ;; `rows` are replaced as a whole if present in `fields`. Other option would be to
      ;; have a unique id for each row, but since the rows can have arbitrarily
      ;; nested "subrows" (boxes), it is not worthwhile.
      [true :merge]
      (let [new-rows (select-keys fields [:rows])
            fields   (dissoc fields :rows)
            new-doc  (-<>> fields
                           (util/deep-merge old-doc)
                           (merge <> new-rows)
                           sec-sc/valid-schema-document!)]
        (mongo/update-by-id :conversion-section-schemas
                            doc-id
                            {$set (merge (util/kw-flatten-map fields)
                                         new-rows)})
        new-doc)

      ;; `fields` replace their old counterparts, but the others are left intact.
      [true :replace]
      (let [new-doc (sec-sc/valid-schema-document! (merge old-doc fields))]
        (mongo/update-by-id :conversion-section-schemas doc-id {$set fields})
        new-doc)

      ;; No old-doc, every operation resolves to add.
      (let [new-doc (assoc fields :id (mongo/create-id))]
        (mongo/insert :conversion-section-schemas
                      (sec-sc/valid-schema-document! new-doc))
        new-doc))))

(sc/defn ^:always-validate import-schemas :- [sec-sc/SchemaDocument]
  [imports :- [sec-sc/SchemaDocumentImport]]
  (->> imports no-duplicate-names! (mapv import-schema-document)))

(def ^:private application-section
  (-> sec-sc/SectionData
      (st/dissoc :schema-name :name)
      (st/assoc :schema (st/select-keys sec-sc/SchemaDocument
                                        [:dictionary :rows]))))

(sc/defn ^:always-validate application-sections :- (sc/maybe [application-section])
  "Returns conversion sections for `application-id` if those exist (nil otherwise). The
  sections are enriched with the relevant schema information."
  [application-id :- ssc/NonBlankStr]
  (some->> (conv-doc/document {:LP-id application-id})
           :sections
           (keep (fn [{:keys [schema-name] :as section}]
                   ;; `schema` should always exist, but we just omit the whole section if
                   ;; it is not found.
                   (when-let [schema (read-schema schema-name)]
                     (util/assoc-when {:id       (:id section)
                                       :modified (:modified section)
                                       :schema   (select-keys schema [:dictionary :rows])
                                       :data     (some-> section :data md/unwrap-all)}
                                      :title (or (:title section) (:title schema))
                                      :l10n (not-empty (util/deep-merge (:l10n schema)
                                                                        (:l10n section)))))))
           seq))

(defn section-query-or-nil
  "Query map for finding a document from `conversion` collection that matches `app-id` and
  has a section `section-id`. Nil if either condition is blank."
  [{{app-id :id} :application {:keys [section-id]} :data}]
  (let [section-id (some-> section-id ss/trim ss/blank-as-nil)]
    (when (and section-id app-id)
      {:LP-id       app-id
       :sections.id section-id})))

(defn command->section
  "Unwrapped conversion section for `command` or nil."
  [command]
  (some-<>> (section-query-or-nil command)
            (mongo/select-one :conversion <> [:sections])
            :sections
            (util/find-by-id (-> command :data :section-id))
            (update <> :data md/unwrap-all)))

(defn edit-section
  "Validate, process and store an individual section field value.

  1. Value is validated (and potentially processed, e.g., dates are converted to
     timestamps).
  2. If the validation fails, error information is returned.
  3. Valid/processed value is stored into mongo in a metadata object."
  [{:keys                           [user created]
    {:keys [path value section-id]} :data
    :as                             command}]
  (let [{:keys [schema-name data]} (command->section command)
        {:keys [data value path op]
         :as   processed}          (pate-sc/validate-and-process-value
                                     (read-schema schema-name)
                                     path
                                     (ss/trimwalk value)
                                     data)]
    (if data
      (let [mongo-path (util/kw-path :sections.$.data path)
            updates    (assoc-in (if (= op :remove)
                                   {$unset {mongo-path true}}
                                   {$set {mongo-path (md/wrap (:id user) created value)}})
                                 [$set :sections.$.modified] created)]
        (mongo/update-by-query :conversion
                               {:sections {$elemMatch {:id section-id}}}
                               updates)
        {:modified created})
      processed)))

(sc/defn ^:always-validate validate-section-data :- sec-sc/SectionData
  "Validates section data against its schema. Returns `m` if everything is OK, throws
  otherwise."
  [{:keys [schema-name data] :as m} :- sec-sc/SectionData]
  (if-let [errors (pate-sc/validate-dictionary-data (read-schema schema-name true)
                                                    (md/unwrap-all data))]
    (throw (ex-info (format "Bad data in section %s: %s"
                            (:name m) errors)
                    {:errors errors}))
    m))

(sc/defn ^:private ^:always-validate process-sections :- (sc/maybe [sec-sc/SectionData])
  "Processes `imports` into `old-sections`. Imports and old sections are matched by their
  `:name` fields. Each import is processed according to its `:op` field:

  `:delete` The old section match is removed. Noop, if no match.

  `:add`    New section is added. Throws if there already is a match.

  `:merge` The import is _deep_ merged into its match. If no match, a new section is
           added.

  `:replace` The import is merged into its match. The import fields replace the old
             fields. If no match, a new section is added.

   Returns the processed sections or nil, if every section is deleted."
  [old-sections :- (sc/maybe [sec-sc/SectionData]) imports :- [sec-sc/SectionImport]]
  (let [imports   (no-duplicate-names! imports)
        old-names (set (map :name old-sections))
        nope!     #(throw (ex-info % {}))
        ts        (now)
        wrap-fn   (md/wrapper "batchrun-user" ts)
        prep      (util/fn-> (dissoc :op)
                             (assoc :modified ts)
                             (util/update-in-when [:data] (partial md/wrap-all wrap-fn)))
        updated   (keep (fn [{m-name :name :as m}]
                          (if-let [new-m (util/find-by-key :name m-name imports)]
                            (case (:op new-m)
                              :delete  nil
                              :add     (nope! (format "Section %s already exists."
                                                      m-name))
                              :merge   (util/deep-merge m (prep new-m))
                              :replace (merge m (prep new-m)))
                            m))
                        old-sections)
        added     (keep (fn [{m-name :name op :op :as m}]
                          (when-not (or (= op :delete) (old-names m-name))
                            (assoc (prep m) :id (mongo/create-id))))
                        imports)]
    (some->> (concat updated added) seq (mapv validate-section-data))))

(sc/defn ^:always-validate import-sections :- conv-sc/ConversionDocument
  "Imports sections into `document`."
  [document :- conv-sc/ConversionDocument  imports :- [sec-sc/SectionImport]]
  (let [merged (process-sections (:sections document) imports)]
    (conv-doc/update-document document (if merged
                                         {$set {:sections merged}}
                                         {$unset {:sections true}}))))


(defmethod shared/import--items :schemas
  [cfg]
  (import-schemas (:items cfg)))

(defmethod shared/import--items :sections
  [{:keys [organization-id items]}]
  (doseq [[backend-id
           imports] items
          :let      [doc (conv-doc/document organization-id backend-id)]]
    (if doc
      (import-sections doc imports)
      (log/warnf "Conversion document for %s in %s not found."
                 backend-id organization-id))))
