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
       (sequential? x)
       (= (count x) 2)
       (string? (first x))
       (string? (second x))))

(defn get-operations-for-category
  "Get all operations as a flat string collection from an operation tree for a top level operation category such as Rakentaminen ja purkaminen"
  [operation-tree category]
  (let [by-category (into {} operation-tree)
        category-operation-tree (get by-category category)
        branch? (fn [x] (and (sequential? x)
                             (not (pair? x))))]
    (if (string? category-operation-tree)
      [category-operation-tree] ;; category has no children. Only one operation
      (->> (tree-seq branch? identity category-operation-tree)
           (filter pair?)
           (map second)))))

(defn get-operations-from-tree [operation-tree categories]
  (mapcat (partial get-operations-for-category operation-tree) categories))

(defn ->invoice-row [catalogue-row]
  {:type "from-price-catalogue"
   :text (:text catalogue-row)
   :unit (:unit catalogue-row)
   :units 1
   :price-per-unit (:price-per-unit catalogue-row)
   :discount-percent (or (:discount-percent catalogue-row) 1)})
