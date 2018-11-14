(ns lupapalvelu.invoices.shared.util)

(defn filter-rows-having-operation [rows operation]
  (filter (fn [row] ((set (:operations row)) operation))
          rows))

(defn rows-by-operation [{:keys [rows] :as catalogue}]
  (let [all-operations (->> rows
                            (map :operations)
                            (remove nil?)
                            flatten
                            set)]
    (into {} (for [operation all-operations
                   :let [operation-rows (filter-rows-having-operation rows operation)]]
               [operation operation-rows]))))
