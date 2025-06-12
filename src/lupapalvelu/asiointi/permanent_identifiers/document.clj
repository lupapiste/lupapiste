(ns lupapalvelu.asiointi.permanent-identifiers.document
  "Utilities for extracting identifier data from application documents."
  (:require [clojure.set :as set]
            [lupapalvelu.asiointi.shared-schemas :as shared-schemas]
            [lupapalvelu.asiointi.permanent-identifiers.schema :as id-schema]
            [lupapalvelu.domain :as domain]
            [malli.core :as m]
            [malli.experimental :as mx]
            [sade.strings :as ss]
            [sade.util :as util]))

(mx/defn path-matches? :- :boolean
  "Check if given `path` matches target path spec."
  [target-path-spec :- id-schema/PathSpec
   path :- id-schema/Path]
  (let [target-count (count target-path-spec)]
    (boolean
      (and
        ;; path spec and path length should be identical;
        ;; the former will contain `true` for repeating fields
        ;; while the latter will have a keyword (`:0`, `:1`, ...)
        (= target-count (count path))
        (loop [idx 0]
          (if (<= target-count idx)
            true
            (let [target-val (nth target-path-spec idx)
                  path-val   (nth path idx)]
              (cond
                ;; segments are identical keywords?
                (= target-val path-val)
                (recur (inc idx))

                ;; segment is a repeating group
                (and (true? target-val)
                     (re-matches #"\d+" (name path-val)))
                (recur (inc idx))

                :else
                false))))))))

(def DocumentPaths (m/schema [:map-of :keyword [:set id-schema/Path]]))

(mx/defn document-paths :- DocumentPaths
  "Given a document, find all existing paths under `:data` which match `fields+path-specs`."
  [{:keys [data]} fields+path-specs :- id-schema/FieldPathSpec]
  ;; This is not terribly efficient, but the `data` maps are usually not enormous
  (->> data
       ;; Flatten whole document to a single `{:foo.0.bar.baz "value" ...}` map
       util/kw-flatten-map
       keys
       ;; Split paths into `[:foo :0 :bar :baz :value]`, and drop the last path segment (`:value`)
       ;; Alternatively we could avoid `pop` with `tools/unwrapped`, but that's expensive
       (map (comp pop util/split-kw-path))
       ;; Compare each actual path with the path specs to find matches
       (reduce (fn [acc path]
                 (if-let [[field-name path] (some (fn [[field-name target-path-spec]]
                                                    (when (path-matches? target-path-spec path)
                                                      [field-name path]))
                                                  fields+path-specs)]
                   ;; We maybe dealing with repeating fields (e.g. huoneistot),
                   ;; therefore we may find multiple paths as each points to a different group/row
                   (update acc field-name (fnil conj #{}) path)
                   acc))
               {})))

(def DocumentEntity
  "Details about a building or apartment in a Document"
  (m/schema [:map
             ;; path of key in document
             [::key-path id-schema/Path]
             ;; path of (persistent id) value in document
             [::value-path id-schema/Path]
             ;; (persistent id) value, if present
             [::value {:optional true} shared-schemas/NonBlankStr]]))

(def DocumentEntities
  (m/schema [:map-of shared-schemas/NonBlankStr ;; key value, e.g. a UUID
             DocumentEntity]))

(mx/defn find-entities :- [:maybe DocumentEntities]
  "Check if given path(s) points to a building or apartment entities in `data`.

  The `field-name` is e.g. `:apartmentKey` and `paths` are all the corresponding
  paths into the document data (`#{[:rakennuskohteen-osa :huoneistot :1 :ryhti :apartmentKey] ...}`)"
  [data
   field-name :- :keyword
   paths :- [:set id-schema/Path]]
  (->> paths
       (keep (fn [key-path]
               ;; Grab the building / apartment key
               (when-let [key-value (-> (get-in data key-path) :value ss/blank-as-nil)]
                 ;; Based on the key find out the actual permanent ID field
                 (let [value-path (id-schema/path->sibling-path key-path field-name)
                       value      (-> (get-in data value-path) :value ss/blank-as-nil)]
                   [key-value (util/assoc-when {;; Path to key (e.g. `:buildingKey`)
                                                ::key-path   key-path
                                                ;; Path to value (e.g. `:pysyva-rakennustunnus`)
                                                ::value-path value-path}
                                               ;; Actual value, if present
                                               ::value      value)]))))
       (into {})
       not-empty))

(defn- document->paths
  "Find paths within a document which point to buildings or apartments."
  [{data :data :as doc}]
  (let [;; Based on the schema, where should look for the fields
        id-fields+path-specs (-> (get id-schema/document-path-specs (-> doc :schema-info :name))
                                 ;; We specifically look for the fields that uniquely identify buildings or apartments
                                 (select-keys #{:buildingKey :apartmentKey}))]
    ;; Search the document for any fields that identify buildings and apartments
    (->> (document-paths doc id-fields+path-specs)
         (keep (fn [[field-name paths]]
                 (when-let [results (find-entities data field-name paths)]
                   [field-name results])))
         (into {})
         not-empty)))

(def ParsedDocuments
  (m/schema [:map-of shared-schemas/NonBlankStr ;; doc-id
             [:map-of :keyword ;; key field, e.g. :buildingKey
              DocumentEntities]]))

(mx/defn parse-documents :- ParsedDocuments
  "Given an application, find all documents with buildings or apartments and
  return a map documents to keys to paths."
  [application]
  (->>
    ;; Grab all the documents, which might contain fields related to permanent identifiers
    (domain/get-building-documents application)
    (filter (fn [doc] (contains? id-schema/target-doc-names (-> doc :schema-info :name))))
    (keep (fn [{doc-id :id :as doc}]
            (when-let [paths (document->paths doc)]
              [doc-id paths])))
    (into {})))

(def Buildings
  (m/schema [:vector
             [:map {:closed true}
              [:buildingKey
               {:description "Identifies the target building"}
               shared-schemas/NonBlankStr]
              [:permanentBuildingIdentifier
               {:description (ss/join " "
                                      ["If present, assign this permanent building ID (PRT) to this building."
                                       "If the target building already has the same ID, this assignment is ignored."
                                       "If the target building already has a different ID, this assignment will return an error."])
                :optional    true}
               shared-schemas/NonBlankStr]
              [:apartments
               {:description "If present, assign these permanent apartment IDs (PHT) to the given apartments."
                :optional    true}
               [:vector
                [:map {:closed true}
                 [:apartmentKey
                  {:description "Identifies the target apartment"}
                  shared-schemas/NonBlankStr]
                 [:apartmentIdentifier
                  {:description (ss/join " "
                                         ["The new permanent apartment ID (PHT)."
                                          "If the target apartment already has the same ID, this assignment is ignored."
                                          "If the target apartment already has a different ID, this assignment will return an error."])}
                  shared-schemas/NonBlankStr]]]]]]))

(def Matches
  (m/schema [:vector
             [:map
              ;; Did something go wrong?
              [:error {:optional true} [:enum
                                        ;; Trying to overwrite an existing permanent ID with a different value
                                        :overwriting
                                        ;; Key & value does not match any building or apartment
                                        :unknown-field
                                        ;; Apartment given under wrong parent building
                                        :incorrect-building]]
              ;; The key and value we tried to find (e.g. `:buildingKey` + some UUID)
              [:key-name :keyword]
              [:key-value shared-schemas/NonBlankStr]
              ;; The new value we tried to write
              [:permanent-id-new {:optional true} shared-schemas/NonBlankStr]
              ;; The possible existing old permanent ID value found
              [:permanent-id-old {:optional true} shared-schemas/NonBlankStr]
              ;; Path to the permanent ID field in the document
              [:permanent-id-path {:optional true} id-schema/Path]
              ;; Doc-id of the matching document
              [:doc-id {:optional true} shared-schemas/NonBlankStr]]]))

(defn- find-matches
  [parsed-docs key-name key-value]
  (->> parsed-docs
       (keep (fn [[doc-id m]]
               (when-let [entry (get-in m [key-name key-value])]
                 [doc-id entry])))
       seq))

(mx/defn match-buildings :- Matches
  "Try to match the new building data with the data within the documents.

  Returns a vector of the results indicating whether the building data entries
  were successfully matched or not."
  [buildings :- Buildings
   parsed-docs :- ParsedDocuments]
  (let [matches (->> buildings
                     ;; 1. First we check the individual fields
                     ;; Flatten the nested building data for easier comparison
                     (mapcat (fn [{:keys [buildingKey apartments permanentBuildingIdentifier]}]
                               (->> apartments
                                    (map (fn [{:keys [apartmentKey apartmentIdentifier]}]
                                           (util/assoc-when {::key-name  :apartmentKey
                                                             ::key-value apartmentKey}
                                                            ::new-id    apartmentIdentifier)))
                                    (cons (util/assoc-when {::key-name  :buildingKey
                                                            ::key-value buildingKey}
                                                           ::new-id    permanentBuildingIdentifier)))))
                     (mapcat (fn [{::keys [key-name key-value new-id]}]
                               ;; Find the corresponding document entries
                               (if-let [matches (find-matches parsed-docs key-name key-value)]
                                 ;; Are we trying to overwrite existing permanent IDs with new values?
                                 (if-let [overwrites (->> matches
                                                          (filter (fn [[_doc-id {::keys [value]}]]
                                                                    (and value new-id (not= new-id value))))
                                                          (map (fn [[doc-id entry]]
                                                                 {:error            :overwriting
                                                                  :doc-id           doc-id
                                                                  :key-name         key-name
                                                                  :key-value        key-value
                                                                  :permanent-id-new new-id
                                                                  :permanent-id-old (::value entry)}))
                                                          seq)]
                                   overwrites
                                   ;; No problems
                                   (->> matches
                                        ;; Just in case the existing permanent ID matches the one in the update -> ignore
                                        (remove (fn [[_doc-id {::keys [value]}]]
                                                  (or (nil? new-id)
                                                      (and value (= new-id value)))))
                                        (map (fn [[doc-id entry]]
                                               {:doc-id            doc-id
                                                :key-name          key-name
                                                :key-value         key-value
                                                :permanent-id-new  new-id
                                                :permanent-id-path (::value-path entry)}))))
                                 [(util/assoc-when {:error     :unknown-field
                                                    :key-name  key-name
                                                    :key-value key-value}
                                                   :permanent-id-new new-id)])))
                     ;; 2. Then we also verify the building <-> apartment -relationships are ok
                     (concat (->> buildings
                                  (mapcat (fn [{:keys [buildingKey apartments]}]
                                            (let [building-docs  (->> (find-matches parsed-docs :buildingKey buildingKey)
                                                                      ;; NOTE: A bit unclear if we will ever have a situation where an application
                                                                      ;; has multiple documents containing the same `:buildingKey`. That might happen
                                                                      ;; with e.g. "laajennos" operation(s), but those should already have PRTs.
                                                                      (map (fn [[doc-id _entry]] doc-id))
                                                                      (into #{}))
                                                  apartment-docs (->> apartments
                                                                      (map (fn [{:keys [apartmentKey]}]
                                                                             [apartmentKey (->> (find-matches parsed-docs :apartmentKey apartmentKey)
                                                                                                (map (fn [[doc-id _entry]] doc-id))
                                                                                                (into #{}))]))
                                                                      (into {}))]
                                              (->> apartment-docs
                                                   ;; Check that the apartments belong to the same document(s) as the parent building
                                                   (remove (fn [[_apartmentKey apartment-docs]]
                                                             (set/subset? apartment-docs building-docs)))
                                                   (map (fn [[apartmentKey apartment-docs]]
                                                          {:error     :incorrect-building
                                                           :key-name  :apartmentKey
                                                           :key-value apartmentKey
                                                           ;; grab one of the offending documents
                                                           :doc-id    (first (set/difference apartment-docs building-docs))}))))))))
                     (into []))]
    ;; If there are any errors => just report the errors
    (or (not-empty (filterv :error matches))
        matches)))
