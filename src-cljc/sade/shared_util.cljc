(ns sade.shared-util
  "Required and passed-through by sade.util"
  (:require [clojure.set :as set]))

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
  "Intersection of given collections as keywords."
  [& colls]
  (apply set/intersection (map #(set (map keyword %)) colls)))
