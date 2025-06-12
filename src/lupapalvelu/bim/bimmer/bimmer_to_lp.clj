(ns lupapalvelu.bim.bimmer.bimmer-to-lp
  "Execution layer for the Bimmer IFC import pipeline.
  Applies `ifc-field-mappings` to a Bimmer properties response to produce
  LP document data maps, and builds the psets query sent to the Bimmer service."
  (:require [lupapalvelu.bim.bimmer.ifc-field-mappings :as ifc-mappings]
            [lupapalvelu.document.shared-tools :as shared-tools]
            [lupapalvelu.logging :as log]
            [lupapalvelu.operations :as op]
            [sade.coordinate :as coord]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defschema PsetEntry
  "A single property set descriptor in a bimmer psets query.
  `:name` is optional for entity-level attribute queries (e.g. IfcMapConversion)
  that use `:attributes` directly instead of a named property set."
  {(sc/optional-key :name) sc/Str
   sc/Keyword              sc/Any})

(sc/defschema PsetsQuery
  "A bimmer psets query map, e.g. {:IfcBuilding [{:name \"FI_Kohde\"}]}.
   Keys are IFC entity class names as keywords. When sent to the bimmer service
   over pubsub they are serialized to strings via JSON."
  {sc/Keyword [PsetEntry]})

(defn merge-psets-queries
  "Merges multiple psets query maps into one, concatenating pset entries for the same entity.
  E.g. `[{\"IfcBuilding\" [{:name \"FI_Kohde\"}]} {\"IfcBuilding\" [{:name \"FI_KohdeOsoite\"}]}]
  => {\"IfcBuilding\" [{:name \"FI_Kohde\"} {:name \"FI_KohdeOsoite\"}]}`."
  [queries]
  (reduce (fn [q item]
            (merge-with (fn [existing new]
                          (into existing (remove (set existing) new)))
                        q
                        item))
          {}
          queries))

(defn- mapping->pset-entry [{:keys [entity pset pset-opts attribute]}]
  (if attribute
    {entity [{:attributes [(name attribute)]}]}
    (let [pset-entry (cond-> {:name (name pset)}
                       pset-opts (merge pset-opts))]
      {entity [pset-entry]})))

(defn psets-query
  "Returns the bimmer psets query map derived from all field, row, and aggregate mappings,
  e.g. `{:IfcBuilding [{:name \"FI_Kohde\"}]}`.
  IfcSpace FI_Laajuustieto entries in `aggregate-mappings` all carry the same `:pset-opts`
  (with `:replace` for all area type names), so `merge-psets-queries` deduplicates them
  into a single entry that tells Bimmer to return numeric area values keyed by type name.
  The IfcSpatialZone FI_Laajuustieto aggregate entry has no `:pset-opts` and is excluded
  because it is already covered by the `field-mappings` huoneistoala entry.
  IfcMapConversion is included unconditionally to extract coordinate mapping data for the
  operation location on the Tietomalli tab."
  []
  (let [aggregate-with-pset-opts (filter :pset-opts ifc-mappings/aggregate-mappings)]
    (->> (concat (vals ifc-mappings/field-mappings)
                 ifc-mappings/row-mappings
                 aggregate-with-pset-opts)
         (map mapping->pset-entry)
         (cons {:IfcMapConversion [{:attributes ["Eastings"
                                                 "Northings"
                                                 "OrthogonalHeight"
                                                 "Scale"
                                                 "XAxisAbscissa"
                                                 "XAxisOrdinate"]}]})
         merge-psets-queries)))

(defn- ifc-property-value [instance {:keys [pset property attribute]}]
  (if attribute
    (get instance (name attribute))
    (get-in instance [(name pset) (name property)])))

(defn- idx->key [idx] (util/make-kw idx))

(defn- repeating-field-path [lp-path table-prefix idx]
  (let [n (count table-prefix)]
    (vec (concat (subvec lp-path 0 n) [(idx->key idx)] (subvec lp-path n)))))

(defn- repeating-table-paths [schema]
  (-> (shared-tools/walk-schema
        schema
        (fn [{:keys [path element]}]
          {:result (when (:repeating element) path)}))
      set))

(defn- safe-transform
  [{:keys [ifc-mapping-key entity pset property attribute lp-path transform]} raw-val]
  (try
    (some-> raw-val transform)
    (catch Exception _
      (log/warnf "BIM field mapping transform failed: ifc-mapping-key=%s entity=%s pset=%s property=%s attribute=%s lp-path=%s raw-value=%s"
                 ifc-mapping-key entity pset property attribute lp-path
                 (pr-str raw-val) (type raw-val))
      nil)))

(defn- assoc-repeating-field [data {:keys [lp-path entity] :as mapping} properties table-prefix]
  (->> entity
       name
       (get properties)
       (map-indexed vector)
       (reduce (fn [coll [idx instance]]
                 (if-some [v (safe-transform mapping (ifc-property-value instance mapping))]
                   (assoc-in coll (repeating-field-path lp-path table-prefix idx) v)
                   coll))
               data)))

(defn- assoc-scalar-field [data {:keys [lp-path entity] :as mapping} properties]
  (let [instance (->> entity name (get properties) first)]
    (if-some [v (safe-transform mapping (ifc-property-value instance mapping))]
      (assoc-in data lp-path v)
      data)))

(defn ifc-mapping-paths
  "Walk `schema` and return `{ifc-mapping-key → lp-path}` for all elements
  that carry an `:ifc-mapping` key present in `valid-keys`."
  [schema valid-keys]
  (let [valid-keys (set valid-keys)]
    (->> (shared-tools/walk-schema
           schema
           (fn [{:keys [path element]}]
             (when-let [k (:ifc-mapping element)]
               (when (valid-keys k)
                 {:result [k path]}))))
         (into {}))))

(defn build-fields-data
  "Maps IFC property values from `properties` to LP document data paths using
  `ifc-mappings/field-mappings`. The `schema` is walked to resolve `:ifc-mapping`
  annotations to document field paths, and to identify which paths sit inside
  repeating tables (e.g. huoneistot), so those fields are indexed by their
  position in the entity instance list."
  [schema properties]
  (let [paths            (ifc-mapping-paths schema (keys ifc-mappings/field-mappings))
        mappings         (keep (fn [[k v]]
                                 (when-let [p (get paths k)]
                                   (assoc v :lp-path p :ifc-mapping-key k)))
                               ifc-mappings/field-mappings)
        repeating-tables (repeating-table-paths schema)
        matching-prefix  (fn [lp-path]
                           (some #(when (util/prefix-matches? lp-path %) %) repeating-tables))]
    (reduce (fn [data {:keys [lp-path] :as mapping}]
              (if-let [table-prefix (matching-prefix lp-path)]
                (assoc-repeating-field data mapping properties table-prefix)
                (assoc-scalar-field data mapping properties)))
            {}
            mappings)))

(defn- matched-rows [row-decls properties]
  (keep (fn [{:keys [entity row] :as decl}]
          (let [instance (-> properties (get (name entity)) first)]
            (when (ifc-property-value instance decl)
              row)))
        row-decls))

(defn- assoc-indexed-rows [data table-path rows]
  (reduce (fn [data [idx row]]
            (assoc-in data (conj table-path (idx->key idx)) row))
          data
          (map-indexed vector rows)))

(defn create-ifc-rows-data
  "Builds the repeating-table portions of the LP document data map
  using `ifc-mappings/row-mappings`. The `schema` is walked to resolve the
  `:ifc-mapping` key on each row-mapping entry to its table path."
  [schema properties]
  (let [table-paths (ifc-mapping-paths schema (map :ifc-mapping ifc-mappings/row-mappings))]
    (->> ifc-mappings/row-mappings
         (group-by :ifc-mapping)
         (reduce (fn [data [ifc-key decls]]
                   (if-let [table-path (get table-paths ifc-key)]
                     (assoc-indexed-rows data table-path (matched-rows decls properties))
                     data))
                 {}))))

(defn build-aggregate-data
  "Aggregates IFC numeric area values from `properties` to LP document data paths using
  `ifc-mappings/aggregate-mappings`. The `schema` is walked to resolve each mapping's
  `:ifc-mapping` key to its lp-path. For each mapping, all numeric values of the given
  property-key are summed across every entity instance and written as a rounded integer string."
  [schema properties]
  (let [paths (ifc-mapping-paths schema (map :ifc-mapping ifc-mappings/aggregate-mappings))]
    (reduce (fn [data {:keys [ifc-mapping entity pset property-key]}]
              (if-let [lp-path (get paths ifc-mapping)]
                (let [pset-name (name pset)
                      instances (get properties (name entity))
                      values    (keep #(get-in % [pset-name property-key]) instances)]
                  (if (seq values)
                    (assoc-in data lp-path (format "%.0f" (double (apply + values))))
                    data))
                data))
            {}
            ifc-mappings/aggregate-mappings)))

(defn operation->kohteen-laji
  "Returns the `kaytto.kohteen-laji` value (`\"rakennus\"`, `\"rakennelma\"`, or `\"etvra\"`)
  for the given LP operation name, mirroring the logic in `lupapalvelu.ryhti.transform/target-type`.
  Returns nil if the operation name is nil or unknown."
  [operation-name]
  (when operation-name
    (let [op-info (op/get-operation-metadata operation-name)]
      (cond
        (:building op-info)  "rakennus"
        (:structure op-info) "rakennelma"))))

(defn create-data-map
  "Builds the LP document data map from IFC `properties` using field and row mappings.
  When `operation-name` is provided, also sets `kaytto.kohteen-laji` from the operation type."
  ([schema properties]
   (create-data-map schema properties nil))
  ([schema properties operation-name]
   (let [base {:data (util/deep-merge (build-fields-data schema properties)
                                      (create-ifc-rows-data schema properties)
                                      (build-aggregate-data schema properties))}]
     (if-let [laji (operation->kohteen-laji operation-name)]
       (assoc-in base [:data :kaytto :kohteen-laji] laji)
       base))))

(defn create-rakennuspaikka-data-map
  "Maps IFC property values from `properties` to LP document data paths for the
  `rakennuspaikka` document schema. Delegates to `build-fields-data` — the schema
  walk naturally selects only the rakennuspaikka keys from `field-mappings`."
  [rakennuspaikka-schema properties]
  (build-fields-data rakennuspaikka-schema properties))

(defn extract-ifc-location
  "Extracts geolocation from IfcMapConversion in the Bimmer properties response.
  Returns `{:x eastings :y northings}` (EPSG:3067, metres) or `nil` when not present
  or when the resulting coordinates fall outside the valid ETRS-TM35FIN ranges.

  The `Scale` factor converts model units to target CRS units (metres).
  For example, a model in millimetres has Scale=0.001, so coordinates in mm are
  divided by Scale to produce metres: 395.2331 / 0.001 = 395 233.1 m (Eastings).

  Valid coordinate ranges as per `sade.coordinate/valid-x?` and `sade.coordinate/valid-y?`:
    x (Eastings):  strictly (10 000, 800 000) m
    y (Northings): closed  [6 610 000, 7 779 999] m"
  [properties]
  (when-let [instance (-> (get properties "IfcMapConversion") first)]
    (let [east  (get instance "Eastings")
          north (get instance "Northings")
          scale (get instance "Scale")]
      (when (and (number? east) (number? north))
        (let [{:keys [x y] :as location} (if (and (number? scale) (pos? scale))
                                           {:x (/ east scale) :y (/ north scale)}
                                           {:x east :y north})]
          (when (and (coord/valid-x? x) (coord/valid-y? y))
            location))))))

(defn assoc-ifc-data-query
  "Adds the IFC extraction query (derived from the field and row mappings)
  into `base-context` for later snapshotting."
  [base-context]
  (assoc base-context :query (psets-query)))
