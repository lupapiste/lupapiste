(ns lupapalvelu.invoices.shared.util)

(defn row-has-operation? [operation row]
  ((set (:operations row)) operation))

(defn maps-with-index-key [coll-of-maps]
  (map-indexed (fn [idx m] (assoc m :index idx)) coll-of-maps))

(defn rows-with-index-by-operation [{:keys [rows] :as catalogue}]
  (let [indexed-rows (maps-with-index-key rows)
        all-operations (->> rows
                            (map :operations)
                            (remove nil?)
                            flatten
                            set)]
    (into {} (for [operation all-operations
                   :let [operation-rows (filter (partial row-has-operation? operation) indexed-rows)]]
               [operation operation-rows]))))
