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

(defn between? [min max value]
  (cond
    (not value) false
    (and min max) (<= min value max)
    min (<= min value)
    max (<= value max)
    :else true))

(defn unit-price-for [{:keys [price-per-unit min-total-price max-total-price] :as catalogue-row}]
  (if (between? min-total-price max-total-price price-per-unit)
    price-per-unit
    (or min-total-price 0)))

(defn ->invoice-row [{:keys [min-total-price max-total-price product-constants] :as catalogue-row}]
  (cond-> {:type "from-price-catalogue"
           :code (:code catalogue-row)
           :text (:text catalogue-row)
           :unit (:unit catalogue-row)
           :order-number (:order-number catalogue-row)
           :units 0
           :price-per-unit (unit-price-for catalogue-row)
           :discount-percent (or (:discount-percent catalogue-row) 0)}
    product-constants (assoc :product-constants product-constants)
    min-total-price (assoc :min-unit-price min-total-price)
    max-total-price (assoc :max-unit-price max-total-price)))


(defn indexed-rows [catalogue]
  (->> (:rows catalogue)
       maps-with-index-key
       vec))

(defn find-map [docs key val]
  (some (fn [doc]
          (when (= (get doc key) val)
            doc))
        docs))

(defn row-title [{:keys [code text] :as invoice-row}]
  (if code
    (str code " " text)
    (str "---- " text)))

(defn non-zero-val? [x]
  (and (some? x)
       (not (zero? x))))

(defn unit-price-editable? [{:keys [price-per-unit min-unit-price max-unit-price] :as invoice-row}]
  (or (zero? price-per-unit)
      (non-zero-val? min-unit-price)
      (non-zero-val? max-unit-price)))

(def person-payer-update-keys [:person-id])
(def company-payer-update-keys [:company-id :ovt :operator :company-contact-person])
(def keys-used-to-update-invoice (concat person-payer-update-keys
                                         company-payer-update-keys
                                         [:application-payer?
                                          :billing-reference
                                          :case-reference
                                          :description
                                          :entity-address
                                          :entity-name
                                          :internal-info
                                          :letter
                                          :operations
                                          :organization-internal-invoice?
                                          :payer-type
                                          :partner-code
                                          :permit-number
                                          :price-catalogue-id
                                          :sap-number
                                          :price-catalogue-id
                                          :state]))
