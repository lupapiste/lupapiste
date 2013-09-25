(ns lupapalvelu.migration.migrations
  (:require [monger.operators :refer :all]
            [lupapalvelu.migration.core :refer [defmigration]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [clojure.walk :as walk]))

(defn drop-schema-data [document]
  (let [schema-info (-> document :schema :info (assoc :version 1))]
    (-> document
      (assoc :schema-info schema-info)
      (dissoc :schema))))

(defmigration schemas-be-gone
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


(defn fix-invalid-schema-infos [{documents :documents operations :operations :as application}]
  (let [updated-documents (doall (for [o operations]
                                   (let [operation-name (keyword (:name o))
                                         target-document-name (:schema (operation-name op/operations))
                                         created (:created o)
                                         document-to-update (some (fn [d] (if
                                                                            (and
                                                                              (= created (:created d))
                                                                              (= target-document-name (get-in d [:schema-info :name])))
                                                                            d)) documents)
                                         updated (when document-to-update (assoc document-to-update :schema-info  (merge (:schema-info document-to-update) {:op o
                                                                                                                                                            :removable (= "R" (:permitType application))})))
                                         ]

                                     updated)))
        unmatched-operations (filter
                               (fn [{id :id :as op}]
                                 (nil? (some
                                         (fn [d]
                                           (when
                                             (= id (get-in d [:schema-info :op :id]))
                                             d))
                                         updated-documents)))
                               operations)
        updated-documents (into updated-documents (for [o unmatched-operations]
                                                    (let [operation-name (keyword (:name o))
                                                          target-document-name (:schema (operation-name op/operations))
                                                          created (:created o)
                                                          document-to-update (some (fn [d]
                                                                                     (if
                                                                                       (and
                                                                                         (< created (:created d))
                                                                                         (= target-document-name (get-in d [:schema-info :name])))
                                                                                       d)) documents)
                                                          updated (when document-to-update (assoc document-to-update :schema-info  (merge (:schema-info document-to-update) {:op o
                                                                                                                                                                             :removable (= "R" (:permitType application))})))]
                                                      updated)))
        result (map
                 (fn [{id :id :as d}]
                   (if-let [r (some (fn [nd] (when (= id (:id nd)) nd)) updated-documents)]
                     r
                     d)) documents)
        new-operations (filter
                         (fn [{id :id :as op}]
                           (some
                             (fn [d]
                               (when
                                 (= id (get-in d [:schema-info :op :id]))
                                 d))
                             result))
                         operations)]

    (assoc (assoc application :documents (into [] result)) :operations new-operations)))

(defmigration invalid-schema-infos-validation
  (let [applications (mongo/select :applications {:infoRequest false})]
    (doall (map #(mongo/update-by-id :applications (:id %) (fix-invalid-schema-infos %)) applications))))
