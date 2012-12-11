(ns lupapalvelu.xml.emit
  (:use [clojure.data.xml]))

(defn- create-element-hierarcy [data key content model]
  (element (:tag model) (:attr model)
           (if (:child model)
             (map #(element-to-xml data key %) (:child model))
             (str content))))

(defn element-to-xml [data k model]
  (let [current-key (:tag model)
        key (conj k current-key)
        current-data (get-in data key)]

    (if (sequential? current-data)
      (for [item current-data]
        (create-element-hierarcy item [] item model))
      (create-element-hierarcy data key current-data model)))
  )
