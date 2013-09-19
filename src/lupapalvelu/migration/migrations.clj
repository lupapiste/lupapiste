(ns lupapalvelu.migration.migrations
  (:require [monger.operators :refer :all]
            [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]))

(defn drop-schema-data [document]
  (let [schema-info (-> document :schema :info (assoc :version 1))]
    (-> document
      (assoc :schema-info schema-info)
      (dissoc :schema))))

(defmigration schemas-be-gonez
  {:apply-when (pos? (mongo/count :applications {:schema-version {$exists false}}))}
  (doseq [application (mongo/select :applications {:schema-version {$exists false}} {:documents true})]
    (mongo/update-by-id :applications (:id application) {$set {:schema-version 1
                                                               :documents (map drop-schema-data (:documents application))}})))

(defn verdict-to-verdics [{verdict :verdict}]
  {$set {:verdicts (map domain/->paatos verdict)}
   $unset {:verdict 1}})

(defmigration verdicts-migraation
  {:apply-when (pos? (mongo/count  :applications {:verdict {$exists true}}))}
  (let [applications (mongo/select :applications {:verdict {$exists true}})]
    (doall (map #(mongo/update-by-id :applications (:id %) (verdict-to-verdics %)) applications))))
