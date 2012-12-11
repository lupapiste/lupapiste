(ns lupapalvelu.xml.emit
  (:use [clojure.data.xml]))

(defn element-to-xml [data k model]
  (let [current-key (:tag model)
        key (conj k current-key)
        current-data (get-in data key)]
    (println "=====================================")
    (clojure.pprint/pprint key )
    (println current-key)
    (println "")
    (clojure.pprint/pprint current-data)
    (println "")
    (clojure.pprint/pprint model)

    (println "=====================================")




      (element (:tag model) (:attr model)
             (if (:child model)
               (map #(element-to-xml data key %) (:child model))
               (if (vector? current-data)
                 (map #(element-to-xml % [current-key] model) current-data)
                 (str current-data))))))
