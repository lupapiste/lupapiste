(ns lupapalvelu.migration.review-migration
  "Utilities for review-related migrations."
  (:require [lupapalvelu.document.tools :as tools]
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
  "Returns list of :app-id, :taks-ids and :attachment-ids maps."
  []
  (->> (duplicate-review-target-applications)
       (map (fn [{id :id :as application}]
              (assoc (duplicate-backend-reviews application)
                     :app-id id)))
       (filter (comp seq :task-ids))
       doall))
