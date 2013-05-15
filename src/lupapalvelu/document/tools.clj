(ns lupapalvelu.document.tools
  (:require [clojure.walk :as walk]))

(defn nil-values [_] nil)

(defn type-verifier [{:keys [type] :as element}]
  (when-not (keyword? type) (throw (RuntimeException. (str "Invalid type: " element)))))

(defn missing [element]
  (throw (UnsupportedOperationException. (str element))))

(defn dummy-values [{:keys [type subtype case name body] :as element}]
  (condp = (keyword type)
    :text             "text"
    :checkbox         true
    :date             "2.5.1974"
    :select           (-> body first :name)
    :radioGroup       (-> body first :name)
    :personSelector   "123"
    :buildingSelector "001"
    :string           (condp = (keyword subtype)
                        :email            "example@example.com"
                        :tel              "012 123 4567"
                        :number           "42"
                        :digit            "1"
                        :kiinteistotunnus "09100200990013"
                        :zip              "33800"
                        nil               "string"
                        :letter           (condp = (keyword case)
                                            :lower "a"
                                            :upper "A"
                                            nil    "Z"
                                            (missing element))
                        (missing element))
    (missing element)))

;;
;; Internal
;;

(defn- ^{:testable true} flattened [col]
  (->> col
    (walk/postwalk
      (fn [x]
        (if (and (sequential? x) (-> x first map?))
          (into {} x)
          x)))))

(defn- ^{:testable true} group [x]
  (if (:repeating x)
    {:name :0
     :type :group
     :body (:body x)}
    (:body x)))

(defn- ^{:testable true} create [{body :body} f]
  (->> body
    (walk/prewalk
      (fn [x]
        (if (map? x)
          (let [k (-> x :name keyword)
                v (if (= :group (keyword (:type x))) (group x) (f x))]
            {k v})
          x)))))

;;
;; Public api
;;

(defn wrapped
  "Wraps leaf values in a map and under k key, key defaults to :value.
   Assumes that every key in the original map is a keyword."
  ([m] (wrapped m :value))
  ([m k]
    (walk/postwalk
      (fn [x] (if (or (keyword? x) (coll? x)) x {k x}))
      m)))

(defn un-wrapped
  "(un-wrapped (wrapped original)) => original"
  ([m] (un-wrapped m :value))
  ([m k]
    (assert (keyword? k))
    (walk/postwalk
      (fn [x] (if (contains? x k) (k x) x))
      m)))

(defn create-document-data
  "Creates document data from schema using function f as input-creator. f defaults to 'nil-valus'"
  ([schema]
    (create-document-data schema nil-values))
  ([schema f]
    (->
      schema
      (create f)
      flattened
      wrapped)))

(defn path-vals
  "Returns vector of tuples containing path vector to the value and the value."
  [m]
  (letfn
    [(pvals [l p m]
       (reduce
         (fn [l [k v]]
           (if (map? v)
             (pvals l (conj p k) v)
             (cons [(conj p k) v] l)))
         l m))]
    (pvals [] [] m)))

(defn assoc-in-path-vals
  "Re-created a map from it's path-vals extracted with (path-vals)."
  [c] (reduce (partial apply assoc-in) {} c))
