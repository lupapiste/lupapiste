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

(defn remove-maps-with-value [coll-of-maps key values]
  (remove #((set values) (get % key)) coll-of-maps))

(defn empty-rows-by-operation [operations]
  (into {} (for [operation operations]
             [operation []])))

(defn pair? [x]
  (and (not (string? x))
       (or (seq? x) (vector? x))
       (= (count x) 2)))

(defn get-operations-for-category [operation-tree category]
  (let [by-category (into {} operation-tree)
        by-subcategory (into {} (get by-category category))
        operation-name-value-pairs (->> (vals by-subcategory)
                                        (apply concat)
                                        (remove #(not (pair? %))))]
    (map second operation-name-value-pairs)))

(defn get-operations-from-tree [operation-tree categories]
  (mapcat (partial get-operations-for-category operation-tree) categories))
