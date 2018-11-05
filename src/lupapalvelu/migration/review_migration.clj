(ns lupapalvelu.migration.review-migration
  "Utilities for review-related migrations."
  (:require [clojure.set :as set]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

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
