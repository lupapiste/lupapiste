(ns lupapalvelu.migration.review-migration
  "Utilities for review-related migrations."
  (:require [clojure.set :as set]
            [taoensso.timbre :refer [warnf]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.pate.verdict :as pate]
            [lupapalvelu.pate.verdict-interface :as vfi]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.tasks :as tasks]))

(defn reviews-attachment-ids
  [task-ids attachments]
  (when (seq task-ids)
    (->> attachments
         (filter (fn [{:keys [source target] :as att}]
                   (and (some-> source :id ss/not-blank?)
                        (util/includes-as-kw? task-ids (:id source))
                        (= (:id source) (:id target))
                        (util/=as-kw (:type source) :tasks)
                        (util/=as-kw (:type target) :task))))
         (map :id)
         not-empty)))

(comment

  ; NOTE these functions inside this comment were never actually used, nor properly tested.
  ; They might be of help when trying to fix some tasks with bad data.
  ; The actual TT-18282 case was in the end resolved by totally $pulling all the falsey tasks.
  (defn task-attachments [application task-id]
    (-> application
        :attachments
        (filter #(contains? #{task-id} (get-in % [:target :id])))))

  (defn pull-task!
    "$pulls given task-id from mongodb. Also deletes attachments related to that task.
    Returns deleted task-id"
    [application task-id]
    (logging/with-logging-context
      {:applicationId (:id application) :userId "migration-user"}
      (let [attachment-ids (map :id (task-attachments application task-id))]
        (if (pos? (mongo/update-by-query :applications
                                         {:_id (:id application) :tasks {$elemMatch {:id task-id}}}
                                         {$pull {:tasks {:id task-id}}}))
          (when (seq attachment-ids)
            (att/delete-attachments! application attachment-ids))
          (warnf "Task %s was not pulled from mongo" task-id))
        task-id)))

  (defn- task-required? [required-reviews {:keys [taskname data]}]
    (let [laji (get-in data [:katselmuksenLaji :value])]
      (some? (->> required-reviews
                  (some #(and (= laji (:type %))
                              (= taskname (:fi %))))))))

  (defn cleanup-task!
    "Cleans data from task and deletes related attachments.
    Leaves the 'empty' task model in place."
    [ts {:keys [buildings] :as application} {:keys [taskname id created] :as task}]
    (let [unwrapped-data (tools/unwrapped (:data task))
          verdict        (vfi/latest-published-verdict {:application application})
          reviews        (vc/verdict-required-reviews verdict)
          empty-task     (merge
                           (tasks/new-task "task-katselmus"
                                           taskname
                                           (merge
                                             (select-keys unwrapped-data [:katselmuksenLaji])
                                             {:vaadittuLupaehtona (task-required? reviews task)
                                              :rakennus           (tasks/rakennus-data-from-buildings {} buildings)})
                                           {:created ts}
                                           {:type "verdict"
                                            :id   (:id verdict)})
                           {:id      id
                            :created created})]
      empty-task)))

(defn duplicate? [candidate target]
  (= (dissoc candidate :id)
     (-> (dissoc target :id)
         (assoc-in [:data :muuTunnus] ""))))

(defn duplicate-backend-reviews
  "Return :task-ids, :attachment-ids map. When two tasks are identical
  the duplicate task-id refers to a task created later. Attachment-ids
  refer to attachments belonging to the duplicate tasks. Tmestamps are
  ignored in comparisons and muuTunnus for duplicate must be empty
  string."
  [{:keys [tasks attachments]}]
  (loop [[review & others] (->> tasks
                                (filter #(some-> % :schema-info :subtype
                                                 (util/=as-kw :review-backend)))
                                (map tools/unwrapped)
                                (sort-by (comp - :created))
                                (map #(dissoc % :created)))
         duplicate-ids []]
    (if (empty? others)
      {:task-ids duplicate-ids
       :attachment-ids (reviews-attachment-ids duplicate-ids attachments)}
      (recur others (cond-> duplicate-ids
                      (some (partial duplicate? review)
                            others)
                      (conj (:id review)))))))


(defn duplicate-review-target-applications []
  (mongo/select :applications
                {:permitType "R"
                 :tasks      {$elemMatch {:schema-info.subtype  "review-backend"
                                          :data.muuTunnus.value #"LP-"}}}
                [:tasks :attachments]))

(defn dry-run
  "Returns a map where keys are application ids values maps
  with :task-ids, attachment-ids, :all-task-ids, all-attachment-ids
  maps. The result is used with `check-aftermath`."
  []
  (reduce (fn [acc {:keys [id tasks attachments] :as application}]
            (assoc acc
                   id
                   (merge (duplicate-backend-reviews application)
                          {:all-task-ids       (map :id tasks)
                           :all-attachment-ids (map :id attachments)})))
          {}
          (duplicate-review-target-applications)))

(defn subset-ok? [super s diff]
  (and (set/subset? (set s) (set super))
       (= (set/difference (set super) (set s)) (set diff))))

(defn check-aftermath [dry-run-result]
  (let [apps (duplicate-review-target-applications)]
    (assert (= (count dry-run-result) (count apps)))
    (doseq [{:keys [id tasks attachments]} apps
            :let [latest-task-ids (map :id tasks)
                  latest-att-ids (map :id attachments)
                  {:keys [task-ids attachment-ids all-task-ids
                          all-attachment-ids]} (get dry-run-result id)]]
      (if (seq task-ids)
        (assert (subset-ok? all-task-ids latest-task-ids task-ids)
                (str id ": removed tasks"))
        (assert (= (set latest-task-ids) (set all-task-ids))
                (str id ": tasks unchanged")))
      (if attachment-ids
        (assert (subset-ok? all-attachment-ids latest-att-ids attachment-ids)
                (str id ": removed attachments"))
        (assert (= (set all-attachment-ids) (set latest-att-ids))
                (str id ": attachments unchanged"))))))


(defn cleanup-condition?
  "An apply-when condition for the ``attachment-cleanup-migration` migration below"
  [organization-id deleted-task-ids]
  (pos? (mongo/count :applications {:organization organization-id
                                    :attachments.target.id {$in deleted-task-ids}})))

(defn cleanup-migration
  "A function for defining migrations that clean up task attachments.
   This is required after the tasks themselves are removed"
  [organization-id deleted-task-ids]
  (doseq [app (mongo/select :applications
                            {:organization organization-id
                             :attachments.target.id {$in deleted-task-ids}}
                            [:attachments :organization])
          :let [task-attachments (->> (:attachments app)
                                      (filter #(-> % :target :type (= "task")))
                                      (filter #(contains? (set deleted-task-ids) (get-in % [:target :id]))))]]
    (logging/with-logging-context {:applicationId (:id app) :userId "migration-user"}
      (att/delete-attachments! app (map :id task-attachments)))))
