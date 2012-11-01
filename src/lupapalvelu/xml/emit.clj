(ns lupapalvelu.xml.emit
  (:use [clojure.data.xml]))

(defn element-to-xml [data k model]
  (let [key (conj k (:tag model))]              
  (element (:tag model) (:attr model) (if (:child model)
                         (map #(element-to-xml data key %) (:child model))
                         (str (get-in data key))))))
