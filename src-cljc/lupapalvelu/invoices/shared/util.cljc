(ns lupapalvelu.invoices.shared.util)

(defn row-has-operation? [operation row]
  ((set (:operations row)) operation))

(defn rows-with-index-by-operation [{:keys [rows] :as catalogue}]
  (let [all-operations (->> rows
                            (map :operations)
                            (remove nil?)
                            flatten
                            set)]
    (into {} (for [operation all-operations
                   :let [operation-rows (filter (partial row-has-operation? operation) rows)]]
               [operation operation-rows]))))
