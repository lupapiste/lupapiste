(ns lupapalvelu.util
  (:refer-clojure :exclude [pos? neg? zero? max-key]))

;;
;; Nil-safe number utilities
;;

(defn pos?
  "Like clojure.core/pos?, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/pos? n) false))

(defn neg?
  "Like clojure.core/neg?, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/neg? n) false))

(defn zero?
  "Like clojure.core/zero?, but nil returns false instead of NPE"
  [n]
  (if n (clojure.core/zero? n) false))

;;
;; Collection utilities
;;

(defn max-key
  "Like clojure.core/max-key, but has single arity and is nil safe.
  Ignores elements without key."
  [key & ms]
  (some->> (filter key ms) not-empty (apply clojure.core/max-key key)))
