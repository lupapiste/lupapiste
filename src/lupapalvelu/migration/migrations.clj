(ns lupapalvelu.migration.migrations
  (:require [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [lupapalvelu.document.tools :as tools]))

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


