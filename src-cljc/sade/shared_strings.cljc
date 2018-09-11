(ns sade.shared-strings
  "Required and passed-through by sade.strings."
  (:require [clojure.string :as s])
  (:refer-clojure :exclude [replace]))

(defn trim [x] (when x (s/trim x)))

(defn split
  ([s re] (when s (s/split s re)))
  ([s re limit] (when s (s/split s re limit))))

(defn replace [s match replacement] (when s (s/replace s match replacement)))

(def ^{:doc "Alias to clojure.string/blank?"} blank? s/blank?)

(defn not-blank? [s] (not (blank? s)))

(def ^{:doc "Arguments: [coll] or [separator coll]. Alias to clojure.string/join"} join s/join)
