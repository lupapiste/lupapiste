(ns lupapalvelu.xml.emit
  (:use [clojure.data.xml]))

;(defn- create-element)

(defn element-to-xml [data k model]
  (let [current-key (:tag model)
        key (conj k current-key)
        current-data (get-in data key)]

    (if (vector? current-data)
      (for [item current-data]
                 (element (:tag model) (:attr model)
                          (if (:child model)
                            (map #(element-to-xml item [] %) (:child model))
                            (str item)
                            ))
                           )
      (element (:tag model) (:attr model)
               (if (:child model)
                 (map #(element-to-xml data key %) (:child model))
                 (str current-data))))

      )
  )
