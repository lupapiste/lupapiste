(ns lupapalvelu.document.tools
  (:require [clojure.walk :as walk]
            [lupapalvelu.document.model :as model]))

(defn nil-values [_] nil)

(defn dummy-values [{:keys [type name body]}]
  (condp = type
    :select           (-> body first :name)
    :checkbox         true
    :number           "42"
    :email            "example@example.com"
    :tel              "012 123 4567"
    :letter           "\u00c5"
    :date             "2.5.1974"
    :kiinteistotunnus "09100200990013"
    name))

(defn flattened [col]
  (->> col
    (walk/postwalk
      (fn [x]
        (if (and (vector? x) (-> x first map?))
          (into {} x)
          x)))))

(defn- group [x]
  (if (:repeating x)
    {:name :0
     :type :group
     :body (dissoc x :repeating)}
    (:body x)))

(defn create [{body :body} f]
  (->> body
    (walk/prewalk
      (fn [x]
        (if (map? x)
          (let [k (-> x :name keyword)
                v (if (= :group (:type x)) (group x)(f x))]
            {k v})
          x)))))

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
