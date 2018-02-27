(ns lupapalvelu.building
  (:require [monger.operators :refer :all]
            [taoensso.timbre :refer [info warnf]]
            [schema.core :as sc]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [sade.coordinate :as coord]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- doc->building-data
  [{{{national-id :value} :valtakunnallinenNumero {short-id :value} :tunnus} :data {{operation-id :id} :op} :schema-info}
   include-bldgs-without-nid?]
  (when (or (and short-id include-bldgs-without-nid?) (ss/not-blank? national-id))
    {:operation-id operation-id
     :national-id national-id
     :short-id    short-id}))

(defn building-ids
  "Gathers building-id data from documents."
  [{:keys [documents]} & [include-bldgs-without-nid?]]
  (->> (map #(doc->building-data % include-bldgs-without-nid?) documents)
       (remove nil?)))

(defn building-id-mapping
  "Returns mapping from building short-id to national-id."
  [application]
  (->> (building-ids application)
       (map (juxt :national-id :short-id))
       (remove (partial some nil?))
       (into {})))

(defn buildingId-in-document?
  "Predicate to validate that given operation id matches document's operation id.
   Document schema is checked to contain valtakunnallinenNumero"
  [op-id {{op :op :as info} :schema-info}]
  (let [schema-body (:body (schemas/get-schema info))]
    (when (and op
               (:id op)
               (some #(= (:name %) schemas/national-building-id) schema-body))
      (= (:id op) op-id))))

(defn push-building-updates
  [{:keys [documents]} operation-id national-building-id location-map timestamp]
  (when (some (partial buildingId-in-document? operation-id) documents)
    {$push {:building-updates
            {:operationId operation-id
             :nationalBuildingId national-building-id
             :location location-map
             :timestamp timestamp}}}))

(defn document-buildingid-updates-for-operation
  "Generates valtakunnallinenNumero updates to documents regarding operation.
   Example update: {documents.1.data.valtakunnalinenNumero.value '123456001M'}"
  [{:keys [documents]} buildingId op-id]
  (mongo/generate-array-updates :documents
                                documents
                                (partial buildingId-in-document? op-id)
                                "data.valtakunnallinenNumero.value"
                                buildingId))

(defn buildings-array-buildingid-updates-for-operation
  "Generates nationalId (valtakunnallinenNumero) updates to buildings array regarding operation.
   Example update: {buildings.1.nationalId '123456001M'}"
  [{:keys [buildings]} buildingId op-id]
  (mongo/generate-array-updates :buildings
                                buildings
                                (comp #{op-id} :operationId)
                                "nationalId"
                                buildingId))

(defn buildings-array-location-updates-for-operation
  "Generates location.x location.y updates to buildings array regarding operation.
   Example update: {buildings.1.location [5232.12 1234.12]}"
  [{:keys [buildings]} {:keys [x y] :as location-map} op-id]
  (when location-map
    (if-not (sc/check ssc/Location location-map)
      (merge
        (mongo/generate-array-updates :buildings
                                      buildings
                                      (comp #{op-id} :operationId)
                                      "location"
                                      [x y])
        (mongo/generate-array-updates :buildings
                                      buildings
                                      (comp #{op-id} :operationId)
                                      "location-wgs84"
                                      (coord/convert "EPSG:3067" :WGS84 5 [x y])))
      (warnf "Invalid location update to buildings array, x: %s, y: %s" x y))))

(defn- operation-building-updates [operation-buildings application]
  (remove
    util/empty-or-nil?
    (map
      (fn [{op-id :operationId buildingId :nationalId}]
        (document-buildingid-updates-for-operation application buildingId op-id))
      operation-buildings)))

(defn building-updates
  "Returns both updates: buildings array of all buildings, and document specific buildingId updates.
   Return value is a map of updates. Example: {$set {:buildings [{:nationalId '123'}] :documents.1.data.valtakunnallinenNumero.value '123'}}"
  [application buildings]
  (when (seq buildings)
    (let [operation-buildings        (filter :operationId buildings)
          op-documents-array-updates (operation-building-updates operation-buildings application)]
      (when-not (empty? op-documents-array-updates)
        (info "operation building updates from verdict" (pr-str op-documents-array-updates)))
      {$set (apply merge (conj op-documents-array-updates {:buildings buildings}))})))
