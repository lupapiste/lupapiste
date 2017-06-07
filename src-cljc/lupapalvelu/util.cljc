(ns lupapalvelu.util
  (:refer-clojure :exclude [max-key]))

;; Collection utilities
;;

(defn max-key
  "Like clojure.core/max-key, but has single arity and is nil safe.
  Ignores elements without key."
  [key & ms]
  (some->> (filter key ms) not-empty (apply clojure.core/max-key key)))
