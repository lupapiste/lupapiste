(ns sade.shared-strings
  "Required and passed-through by sade.strings."
  (:require [clojure.string :as s]
            [clojure.walk :as walk])
  (:refer-clojure :exclude [replace]))

(defn lower-case [x] (when x (s/lower-case x)))

(defn in-lower-case? [x] (boolean (and x (= x (lower-case x)))))

(defn upper-case [x] (when x (s/upper-case x)))

(defn capitalize [x] (when x (s/capitalize x)))

(defn trim [x] (when x (s/trim x)))

(defn trim-newline [x] (when x (s/trim-newline x)))

(defn split
  ([s re] (when s (s/split s re)))
  ([s re limit] (when s (s/split s re limit))))

(defn split-lines [s] (some-> s s/split-lines))

(defn replace [s match replacement] (when s (s/replace s match replacement)))

(def ^{:doc "Alias to clojure.string/blank?"} blank? s/blank?)

(defn not-blank? [s] (not (blank? s)))

(def ^{:doc "Arguments: [coll] or [separator coll]. Alias to clojure.string/join"} join s/join)

(defn join-non-blanks
  ([separator coll]
   (join separator (remove blank? coll)))
  ([coll]
   (join (remove blank? coll))))

(defn trimwalk
  "Walks the given `form` and trims every string."
  [form]
  (walk/postwalk (fn [v]
                   (cond-> v
                     (string? v) trim))
                 form))

(defn blank-as-nil
  "If given string is blank, returns nil. Else returns string."
  [string]
  (when-not (blank? string) string))
