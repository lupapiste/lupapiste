(ns lupapalvelu.action-utils
  (:require [lupapalvelu.logging :as log]))

(defprotocol FindLazyPaths
  (find-lazy [item path]
    "Walk through the response map and find unrealized values (lazy seqs, delays..)."))

(extend-protocol FindLazyPaths

  clojure.lang.IPending
  (find-lazy [item path]
    (when-not (realized? item)
      [path]))

  java.util.Map
  (find-lazy [item path]
    (->> item
         (mapcat (fn [[k v]]
                   (find-lazy v (conj path k))))
         (into [])))

  java.util.List
  (find-lazy [item path]
    (if (and (instance? clojure.lang.IPending item)
             (not (realized? item)))
      [path]
      (->> item
           (map-indexed (fn [k v]
                          (find-lazy v (conj path k))))
           (mapcat identity)
           (into []))))

  java.util.Set
  (find-lazy [item path]
    (->> item
         (map-indexed (fn [k v]
                        (find-lazy v (conj path k))))
         (mapcat identity)
         (into [])))

  java.lang.Object
  (find-lazy [_ _] nil)

  nil
  (find-lazy [_ _] nil))

(defn warn-on-lazy
  "Log a warning if response map contains any unrealized values.
   Returns nil."
  [response command-name]
  (->> (find-lazy response [])
       (run! (fn [path]
               (log/warnf "Lazy value in response, command: \"%s\", path: %s"
                          command-name path)))))
