(ns lupapalvelu.building-product-catalog
  (:require [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]))

(defn new-building-product-catalog-document
  "Returns a new rakennustuoteluettelo document with reference to the correct operation"
  [ref-id schema-name {:keys [created] :as command}]
  (let [schema         (schemas/get-schema 1 schema-name)
        schema-flags   (tools/resolve-schema-flags command)
        default-values (tools/create-document-data schema (partial tools/default-values schema-flags))
        document       {:id           (mongo/create-id)
                        :created      created
                        :schema-info  (select-keys (:info schema) model/db-schema-info-keys)
                        :data         default-values
                        ;; Atm refers to the operation, but once the building is separated to
                        ;; sections (rakennuksen osa) this should refer to the section
                        :building-ref ref-id}]
    document))

(defn copy-building-product-catalog-document
  "Returns a copied rakennustuoteluettelo document with reference to the correct operation.
  Is used to copy the data from the original (sisaltyvat-tuotteet) document to the new one (hyodynnetyt-tuotteet)."
  [ref-id schema-name {:keys [application created]}]
  (let [schema          (schemas/get-schema 1 schema-name)
        prev-catalog-data    (-> (util/find-by-key :building-ref ref-id (:building-product-catalogs application))
                                 :data)
        document        {:id           (mongo/create-id)
                         :created      created
                         :schema-info  (select-keys (:info schema) model/db-schema-info-keys)
                         :data         (-> (assoc prev-catalog-data :hyodynnetyt-tuotteet (:sisaltyvat-tuotteet prev-catalog-data))
                                           (dissoc :sisaltyvat-tuotteet))
                         :building-ref ref-id}]
    document))
