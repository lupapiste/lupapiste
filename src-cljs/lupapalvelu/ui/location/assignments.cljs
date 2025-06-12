(ns lupapalvelu.ui.location.assignments
  "Automatic assignments for BIM model uploads"
  (:require [clojure.set :as set]
            [lupapalvelu.ui.location.model-utils :as model-utils]
            [re-frame.core :as rf]))

(def assignment-service js/lupapisteApp.services.assignmentService)

(rf/reg-event-db ::refresh-assignments
  (fn [db]
    (->> (js->clj (.automaticAssignments assignment-service) :keywordize-keys true)
         (filter (comp #{"structures"} :targetTab))
         (mapv (fn [assignment] (dissoc assignment :edit))) ; remove observable
         (assoc db ::assignments))))

(rf/reg-sub ::assignments :-> ::assignments)

(defn- ->child-array-id-set [coll array-key id-key]
  (reduce (fn [acc item]
            (->> (get item array-key)
                 (map id-key)
                 (into acc)))
          #{}
          coll))

(rf/reg-sub ::assignment-target-ids ;; Target id is BIM file version id
  :<- [::assignments]
  (fn [assignments]
    (->child-array-id-set assignments :targets :id)))

(rf/reg-sub ::phase-file-version-ids ;; Version id is assignment target id
  (fn [[_ operation-id model-phase]]
    (rf/subscribe [::model-utils/phase-files operation-id model-phase]))
  (fn [files _]
    (->child-array-id-set files :versions :file-version-id)))

(rf/reg-sub ::phase-assignment-count
  (fn [[_ operation-id model-phase]]
    [(rf/subscribe [::assignment-target-ids])
     (rf/subscribe [::phase-file-version-ids operation-id model-phase])])
  (fn [[assignment-target-ids phase-file-ids]]
    (count (set/intersection assignment-target-ids phase-file-ids))))

(rf/reg-sub ::file-assignment-count
  :<- [::assignment-target-ids]
  (fn [assignment-target-ids [_ file]]
    (count (set/intersection assignment-target-ids
                             (->child-array-id-set [file] :versions :file-version-id)))))
