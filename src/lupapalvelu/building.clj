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
    (flatten
      (map
        (fn [{tags :tags bid :nationalId}]
          (map (partial buildingid-updates-for-operation application bid) tags))
        operation-buildings))))
