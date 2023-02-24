(ns lupapalvelu.migration.review-officer-duplicates
  "Removes duplicate tasks and related attachments caused by a bug in the review officers list.
  Takes the newer of the duplicates."
  (:require [clojure.math.combinatorics :as combinatorics]
            [lupapalvelu.migration.review-migration :as review-migration]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.review :as review]
            [lupapalvelu.tasks :as tasks]
            [monger.operators :refer :all]
            [sade.core]))


(defn- get-duplicate-task-id-from-pair
  "Selects the task that was updated least recently to be deleted.
  Presumes that the param is a pair of duplicate tasks."
  [tasks]
  (assert (= 2 (count tasks)))
  (->> tasks
       (sort-by #(-> % :data :katselmus :pitaja :modified))
       first
       :id))


(defn- get-pairs [tasks]
  (combinatorics/combinations tasks 2))


(defn get-review-duplicates
  "Takes a list of mongo tasks and returns a list of the tasks to be removed"
  [application]
  (->> (:tasks application)
       (filter tasks/task-is-review?)
       (get-pairs)
       (filter (partial apply review/tasks-match?))
       (map get-duplicate-task-id-from-pair)))


(defn migrate
  "So far this has only been a problem for Pori 609-R and is unlikely to spread now that it is fixed.
  But if it does, we can run this migration again for the other organization(s)"
  [organization-id support-ticket-id]
  (let [deleted-tasks (->> (mongo/select :applications {:organization                                   organization-id
                                                        :tasks.data.katselmus.pitaja.value._atomic-map? true})
                           (map (juxt identity get-review-duplicates))
                           (remove (comp empty? second)))
        sheriff-note  {:note    (str support-ticket-id " Migration: review officer list duplicates")
                       :created (sade.core/now)}]

    ;; Paraphrased from `lupapalvelu.migration.migrations/update-applications-array`
    (doseq [[application task-ids] deleted-tasks]
      (mongo/update-by-query :applications
                             {:_id (:id application)}
                             {$push {:_sheriff-notes sheriff-note}
                              $pull {:tasks {:id {$in task-ids}}}}))
    ;; Remove attachments
    (review-migration/cleanup-migration organization-id (mapcat second deleted-tasks))))
