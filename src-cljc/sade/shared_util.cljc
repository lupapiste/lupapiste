(ns sade.shared-util
  "Required and passed-through by sade.util"
  (:require [clojure.set :as set]))

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

(defn edit-distance
  "Levenshtein distance between strings a and b using Wagner–Fischer
  algorithm. The implementation is iterative, but uses O(|a|*|b|) space."
  [a b]
  {:pre [(string? a) (string? b)]}
  (let [length-of-a (count a)
        length-of-b (count b)]
    (if (or (zero? length-of-a)
            (zero? length-of-b))
      ;; If either one of the strings is empty, the distance is simply
      ;; the length of the other
      (max length-of-a length-of-b)

      ;; Otherwise, calculate the distance with Wagner-Fischer
      (let [distances (->> (concat (map #(vector [% 0] %)
                                        (range (inc length-of-a)))
                                   (map #(vector [0 %] %)
                                        (range (inc length-of-b))))
                           (into {}))]
       (loop [d distances
              i 1
              j 1]
         (let [substitution-cost (if (= (get a (dec i)) (get b (dec j))) 0 1)
               d-i-j (min (inc (d [(dec i) j]))
                          (inc (d [i (dec j)]))
                          (+ (d [(dec i) (dec j)]) substitution-cost))]
           (if (and (= i length-of-a)
                    (= j length-of-b))
             d-i-j
             (recur (assoc d [i j] d-i-j)
                    (if (= j length-of-b)
                      (inc i) i)
                    (if (= j length-of-b)
                      1 (inc j))))))))))
