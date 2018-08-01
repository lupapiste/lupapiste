(ns lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common)

(defn has-tag [m]
  (if (:tag m)
    (if-let [children (seq (:child m))]
      (if (reduce #(and %1 %2) (map has-tag children))
          true
          (do
            (println "Tag missing in:") (clojure.pprint/pprint children) (println)
            false))
      true)
    false))
