(ns lupapalvelu.asiointi.permanent-identifiers.schema
  "Utilities for extracting identifier data from the static document schemas.

  Used to read/write permanent IDs to buildings and apartments:
  - where is the target building or apartment
  - where is the corresponding permanent ID field"
  (:require [lupapalvelu.asiointi.shared-schemas :as shared-schemas]
            [lupapalvelu.document.all-schemas]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.shared-tools :as shared-tools]
            [malli.core :as m]
            [malli.experimental :as mx]
            [sade.util :as util]))

(def use-cases
  {;; We either want to assign building IDs (PRT)
   :assign-permanent-building-id  {;; schemas has necessary keys?
                                   :dependencies #{:buildingKey :pysyva-rakennustunnus}
                                   ;; buildingKey and PRT have to be under the same parent
                                   :siblings     #{:buildingKey :pysyva-rakennustunnus}}
   ;; or apartment IDs (PHT)
   :assign-permanent-apartment-id {;; apartments are under buildings, so we also need to know the buildingKey
                                   :dependencies #{:buildingKey :apartmentKey :pysyvaHuoneistotunnus}
                                   :siblings     #{:apartmentKey :pysyvaHuoneistotunnus}}})

(def interesting-keys (->> use-cases
                           vals
                           (mapcat (fn [{:keys [dependencies siblings]}]
                                     (concat dependencies siblings)))
                           (into #{})))

(def Path
  "Like a normal schema path, but `true` identifies repeating segments"
  (m/schema [:vector :keyword]))

(def PathSpec
  "Like a normal schema path (`Path`), but `true` identifies repeating segments"
  (m/schema [:vector [:or :keyword [:enum true]]]))

(def FieldPathSpec
  "field name -> path spec"
  (m/schema [:map-of :keyword
             PathSpec]))

(def DocumentPathSpec
  "document name -> field name -> path spec"
  (m/schema [:map-of shared-schemas/NonBlankStr
             FieldPathSpec]))

;; NOTE: Unfortunately we need to know how to traverse between the various Key fields
;; and the corresponding permanent ID fields. It would be better if the schema could
;; somehow describe the relationship between the fields but it is unclear how that
;; ought to be done.

(defn- field-path->parent-path
  [path field-name]
  (case field-name
    ;; Ryhti identifiers are under a `:ryhti` element so jump over that
    (:buildingKey
     :apartmentKey)          (-> path pop pop)
    (:pysyva-rakennustunnus
     :pysyvaHuoneistotunnus) (-> path pop)))

(defn path->sibling-path
  [path field-name]
  (let [parent-path (field-path->parent-path path field-name)]
    (case field-name
      :buildingKey  (conj parent-path :pysyva-rakennustunnus)
      :apartmentKey (conj parent-path :pysyvaHuoneistotunnus))))

(defn- find-path-specs
  "Walk through the given schemas and find `PathSpec`(s) for `field-names`."
  [schema-names field-names]
  (->> schema-names
       (map (fn [schema-name]
              [schema-name (schemas/get-schema 1 schema-name)]))
       (keep (fn [[schema-name schema]]
               (let [matches (shared-tools/walk-schema
                               schema
                               (fn [{:keys [element path context]}]
                                 ;; Keep track of parent `:repeating` values when we traverse the schema
                                 (let [context    (update context :rep (fnil conj []) (boolean (:repeating element)))
                                       field-name (keyword (:name element))]
                                   (cond
                                     (some (fn [target] (util/=as-kw field-name target)) field-names)
                                     ;; return a "path spec": like a normal path, but any repeating
                                     ;; segments are followed by a `true` value
                                     {:result  [field-name (->> (interleave path (:rep context))
                                                                (remove false?)
                                                                (into []))]
                                      :context context}

                                     :else
                                     {:context context}))))]
                 (when (seq matches)
                   [schema-name (reduce
                                  ;; Group found path spec(s) into a single map
                                  ;; With our current schemas we shouldn't find more than
                                  ;; one per field, but we'll verify that later.
                                  (fn [acc [field-name path-specs]]
                                    (update acc field-name (fnil conj #{}) path-specs))
                                  {}
                                  matches)]))))
       (into {})))

(defn- has-necessary-keys?
  [[_schema-name fields+path-specs]]
  ;; Check that the found PathSpecs satisfy at least one of the use-cases
  (some (fn [dependencies]
          (every? (fn [k] (contains? fields+path-specs k)) dependencies))
        (map :dependencies (vals use-cases))))

(defn- collapse-path-specs
  "Check that each field has a unique path-spec.
  In other words, try to detect if some schemas have e.g. multiple `:pysyva-rakennustunnus`
  in different parts of the schema."
  [[schema-name m]]
  [schema-name (reduce-kv (fn [acc field-name path-specs]
                            (when (< 1 (count path-specs))
                              (throw (ex-info "Schemas has multiple path specs to key!"
                                              {:schema-name schema-name
                                               :field-name  field-name
                                               :path-specs  path-specs})))
                            (assoc acc field-name (first path-specs)))
                          {}
                          m)])

(defn- same-parent-on-siblings?
  "Check that the siblings fields are under the same parent field."
  [[_schema-name m]]
  (->> (vals use-cases)
       (map (fn [{:keys [siblings]}]
              (->> siblings
                   (map (fn [field-name]
                          (some-> (get m field-name)
                                  (field-path->parent-path field-name))))
                   (apply =))))
       (every? true?)))

(mx/defn build-document-path-specs :- DocumentPathSpec
  {:malli/always true}
  [schema-names :- [:set shared-schemas/NonBlankStr]
   field-names :- [:set :keyword]]
  (->> (find-path-specs schema-names field-names)
       ;; We are only interested in schemas, which have enough keys,
       ;; e.g. not just `:pysyva-rakennustunnus`
       (filter has-necessary-keys?)
       ;; No schema should contain multiple separate paths to the same field. E.g. a schema
       ;; shouldn't have multiple different `:pysyva-rakennustunnus` paths or
       ;; multiple apartment tables etc.
       (map collapse-path-specs)
       ;; Check that the sibling fields (e.g. `:pysyva-rakennustunnus` <-> `:buildingKey`)
       ;; have the same parent field.
       (filter same-parent-on-siblings?)
       (into {})))

(def building-schema-names
  "Names of schemas with truthy `:building?` flag."
  (->> (schemas/get-schemas 1)
       ;; Exclude e.g. task schemas, which might refer to buildings
       (filter (fn [[_schema-name schema]]
                 (-> schema :info :building?)))
       (map key)
       (into #{})))

;; NOTE: We process all the applicable schemas based on their fields. This also
;; includes stuff like demolition schemas.
(def document-path-specs (build-document-path-specs building-schema-names interesting-keys))

(def target-doc-names (set (keys document-path-specs)))
