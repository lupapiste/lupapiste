(ns lupapalvelu.bim.bim-extracted-data
  "Schema definitions and persistence for IFC data snapshots (`:ifc-data` MongoDB collection)."
  (:require [lupapalvelu.bim.bimmer.bimmer-to-lp :as btl]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$in]]
            [sade.coordinate :as coord]
            [sade.core :refer [now]]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(def IfcDoc
  "Schema for the ifc-doc sub-document stored inside an :ifc-data collection entry.
   The :data map is open-ended — its keys and shape are determined by the docgen
   schema and the IFC property mapping.
   The :rakennuspaikka-data map contains fields mapped to the rakennuspaikka document schema.
   The optional :location map contains {:x eastings :y northings} extracted from
   IfcMapConversion (EPSG:3067 / ETRS-TM35FIN) for setting the operation location on the
   Tietomalli tab."
  {:id                         ssc/ObjectIdStr
   :created                    ssc/Timestamp
   :schema-info                {:name      sc/Str
                                :version   sc/Int
                                sc/Keyword sc/Any}
   :data                       {sc/Keyword sc/Any}
   :rakennuspaikka-data        {sc/Keyword sc/Any}
   (sc/optional-key :location) {:x (sc/pred coord/valid-x? :valid-etrs-tm35fin-x)
                                :y (sc/pred coord/valid-y? :valid-etrs-tm35fin-y)}})

(def IfcDataDocument
  "Schema for documents stored in the :ifc-data MongoDB collection."
  {:linked-metadata-file-version-id ssc/NonBlankStr
   :modified                        ssc/Timestamp
   :ifc-query                       btl/PsetsQuery
   :ifc-properties                  sc/Any
   :ifc-doc                         IfcDoc})

(def ^:private validate-ifc-data-document!
  "Pre-compiled validator for IfcDataDocument. Raises on schema violation."
  (sc/validator IfcDataDocument))

(defn ifc-data-document!
  [schema created {:keys [linked-metadata-file-version-id operation-name query properties]}]
  (validate-ifc-data-document!
    {:linked-metadata-file-version-id linked-metadata-file-version-id
     :modified                        created
     :ifc-query                       query
     :ifc-properties                  properties
     :ifc-doc                         (cond-> (merge {:id                  (mongo/create-id)
                                                      :created             created
                                                      :schema-info         (:info schema)
                                                      :rakennuspaikka-data (btl/create-rakennuspaikka-data-map
                                                                             (schemas/get-schema {:name "rakennuspaikka"})
                                                                             properties)}
                                                     (btl/create-data-map schema properties operation-name))
                                        (btl/extract-ifc-location properties) (assoc :location (btl/extract-ifc-location properties)))}))

(defn save-ifc-data-snapshot!
  "Persist an IFC data snapshot (query + raw properties + mapped data) into `:ifc-data`,
  upserting by `linked-metadata-file-version-id` so retries are idempotent.

  Note: the full document is replaced on each upsert, so `:ifc-doc/created` reflects
  the time of the *last* successful extraction, not necessarily the first. This is
  acceptable because `:modified` is the authoritative staleness indicator; true
  creation-time preservation would require enumerating all ifc-doc sub-fields with
  `$setOnInsert` dot-notation, coupling this function to the IfcDoc schema shape."
  [schema snapshot]
  (let [doc (ifc-data-document! schema (now) snapshot)]
    (mongo/update :ifc-data
                  {:linked-metadata-file-version-id (:linked-metadata-file-version-id doc)}
                  doc
                  :upsert true)))

(defn fetch-ifc-data-file-version-ids
  "Given a collection of file-version-ids, returns the subset that have an :ifc-data document."
  [file-version-ids]
  (->> (mongo/select :ifc-data
                     {:linked-metadata-file-version-id {$in (vec file-version-ids)}}
                     [:linked-metadata-file-version-id])
       (map :linked-metadata-file-version-id)
       set))

(defn delete-ifc-data-snapshots!
  "Delete all ifc-data snapshots for the given file-version-ids."
  [file-version-ids]
  (mongo/remove-many :ifc-data {:linked-metadata-file-version-id {$in (vec file-version-ids)}}))

(defn fetch-ifc-doc
  "Retrieve the `:ifc-doc` for a given `linked-metadata-file-version-id`.
  Returns the ifc-doc enriched with the full `:schema` (including `:info` and `:body`)
  so that the frontend docgen can render it, or nil if not found."
  [linked-metadata-file-version-id]
  (when-let [{:keys [schema-info] :as ifc-doc}
             (some-> (mongo/select-one :ifc-data
                                       {:linked-metadata-file-version-id linked-metadata-file-version-id}
                                       [:ifc-doc])
                     :ifc-doc)]
    (assoc ifc-doc :schema (schemas/get-schema schema-info))))
