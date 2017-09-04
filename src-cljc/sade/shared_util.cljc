(ns sade.shared-util
  "Required and passed-through by sade.util"
  (:require [clojure.set :as set]
            [clojure.string :as s]))

(defn find-first
  "Returns first element from coll for which (pred item)
   returns true. pred must be free of side-effects."
  [pred coll]
  (first (filter pred coll)))

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword)
  matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(defn find-by-id
  "Return item from sequence col of maps where :id matches id."
  [id col]
  (find-by-key :id id col))

(defn =as-kw
  "Converts arguments to keywords and compares if they are the same"
  ([x] true)
  ([x y] (= (keyword x) (keyword y)))
  ([x y & more] (apply = (keyword x) (keyword y) (map keyword more))))

(defn not=as-kw
  "Converts arguments to keywords and compares if they are the same"
  ([x] false)
  ([x y] (not= (keyword x) (keyword y)))
  ([x y & more] (apply not= (keyword x) (keyword y) (map keyword more))))

(defn includes-as-kw?
  "True, if any coll item matches x as keyword."
  [coll x]
  (boolean (some (partial =as-kw x) coll)))

(defn intersection-as-kw
  "Intersection of given collections as keywords. The result is a
  list. The items in the result set are taken from the first coll.
  (intersection-as-kw [\"yi\" :er \"san\"] '(:yi \"er\" :si) => (\"yi\" :er)"
  [& colls]
  (let [kw-set (apply set/intersection (map #(set (map keyword %)) colls))]
    (filter #(includes-as-kw? kw-set %) (first colls))))

(defn filter-map-by-val
  "Returns the mapping for which the value satisfies the predicate.
  (filter-map-by-val pos? {:a 1 :b -1}) => {:a 1}"
  [pred m]
  (into {} (filter (fn [[_ v]] (pred v)) m)))

(defn split-kw-path
  ":a.b.c -> [:a :b :c]"
  [kw]
  (map keyword (s/split (name (or kw "")) #"\.")))


;; ---------------------------------------------
;; The following are not aliased in sade.util.
;; ---------------------------------------------

(defn kw-path
  "Like sade.util/kw-path on the Clojure side. Note: this is not
  defaliased in sade.util."
  [& path]
  (->> path
       flatten
       (remove nil?)
       (map #(if (keyword? %)
               (name %)
               %))
       (s/join ".")
       keyword))

(defmacro fn->  [& body] `(fn [x#] (-> x# ~@body)))
(defmacro fn->> [& body] `(fn [x#] (->> x# ~@body)))

(defn safe-update-in [a-map path fn & params]
  "Like update-in, but does nothing in case the given path does not exist"
  (if (empty? path)
    (apply fn a-map params)
    (let [[fst & rst] path]
      (if (contains? a-map fst)
        (apply update a-map fst safe-update-in rst fn params)
        a-map))))
