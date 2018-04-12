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

(defn difference-as-kw
  "Difference operation where collection items are first converted to
  keywords. Returns a keyword vector."
  [& colls]
  (->> colls
       (map (fn [coll]
              (map keyword coll)))
       (map set)
       (apply set/difference)
       vec))

(defn union-as-kw
  "Union of given collections as keywords. The result is a keyword list
  with no nils, no duplicates."
  [& colls]
  (->> colls
       (apply concat)
       (remove nil?)
       (map keyword)
       distinct))

(defn filter-map-by-val
  "Returns the mapping for which the value satisfies the predicate.
  (filter-map-by-val pos? {:a 1 :b -1}) => {:a 1}"
  [pred m]
  (into {} (filter (fn [[_ v]] (pred v)) m)))

(defn split-kw-path
  ":a.b.c -> [:a :b :c]"
  [kw]
  (map keyword (s/split (name (or kw "")) #"\.")))

(defn indexed
  "Returns a lazy sequence of [index, item] pairs, where items come
  from 's' and indexes count up from offset (default: 0).

  (indexed '(a b c d))    =>  ([0 a] [1 b] [2 c] [3 d])
  (indexed 1 '(a b c d))  =>  ([1 a] [2 b] [3 c] [4 d])"
  ([s]
   (indexed 0 s))
  ([offset s]
   (map vector (iterate inc offset) s)))

(defn drop-nth
  "Drops the nth item from vector, if it exists"
  [n v]
  (if (get v n)
    (into (subvec v 0 n) (subvec v (inc n)))
    v))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure.
  Supports also vector index values among the keys sequence just like clojure.core/update-in does."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (if (and (number? k) (sequential? m))
      (drop-nth k m)
      (dissoc m k))))

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
