(ns lupapalvelu.building
  (:require [monger.operators :refer :all]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]))

(defn buildingId-in-document? [operation {{op :op :as info} :schema-info data :data}]
  (let [schema-body (:body (schemas/get-schema info))]
    (when (and op
               (:id op)
               (some #(= (:name %) "valtakunnallinenNumero") schema-body))
      (= (:id op) (:tag operation)))))

(defn buildingid-updates-for-operation
  "Generates valtakunnallinenNumero updates to documents regarding operation.
   Example update: {documents.1.data.valtakunnalinenNumero.value '123456001M'}"
  [{:keys [documents]} buildingId operation]
  (mongo/generate-array-updates :documents
                                documents
                                (partial buildingId-in-document? operation)
                                "data.valtakunnallinenNumero.value"
                                buildingId))

(defn buildings-with-operation [buildings]
  (remove (comp empty? :tags) buildings))

(defn operation-building-updates [operation-buildings application]
  (remove
    util/empty-or-nil?
    (map
      (fn [{tags :tags bid :nationalId}]
        (buildingid-updates-for-operation application bid (first tags))) ; building belongs to only one operation, thus 'first'
      operation-buildings)))

(defn building-updates
  "Returns both updates: buildings array of all buildings, and document specific buildingId updates.
   Return value is a map of updates. Example: {:buildings [{:nationalId '123'}] :documents.1.data.valtakunnallinenNumero.value '123'}"
  [buildings application]
  (let [operation-buildings (buildings-with-operation buildings)
        op-documents-array-updates (operation-building-updates operation-buildings application)]
    (apply merge (conj op-documents-array-updates {:buildings buildings}))))
