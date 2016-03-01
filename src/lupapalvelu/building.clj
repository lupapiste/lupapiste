(ns lupapalvelu.building
  (:require [monger.operators :refer :all]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]))

(defn buildingId-in-document?
  "Predicate to validate that given operation id matches document's operation id.
   Document schema is checked to contain valtakunnallinenNumero"
  [op-id {{op :op :as info} :schema-info}]
  (let [schema-body (:body (schemas/get-schema info))]
    (when (and op
               (:id op)
               (some #(= (:name %) "valtakunnallinenNumero") schema-body))
      (= (:id op) op-id))))

(defn buildingid-updates-for-operation
  "Generates valtakunnallinenNumero updates to documents regarding operation.
   Example update: {documents.1.data.valtakunnalinenNumero.value '123456001M'}"
  [{:keys [documents]} buildingId op-id]
  (mongo/generate-array-updates :documents
                                documents
                                (partial buildingId-in-document? op-id)
                                "data.valtakunnallinenNumero.value"
                                buildingId))

(defn operation-building-updates [operation-buildings application]
  (remove
    util/empty-or-nil?
    (map
      (fn [{op-id :operationId buildingId :nationalId}]
        (buildingid-updates-for-operation application buildingId op-id))
      operation-buildings)))

(defn building-updates
  "Returns both updates: buildings array of all buildings, and document specific buildingId updates.
   Return value is a map of updates. Example: {:buildings [{:nationalId '123'}] :documents.1.data.valtakunnallinenNumero.value '123'}"
  [buildings application]
  (let [operation-buildings        (filter :operationId buildings)
        op-documents-array-updates (operation-building-updates operation-buildings application)]
    (apply merge (conj op-documents-array-updates {:buildings buildings}))))
