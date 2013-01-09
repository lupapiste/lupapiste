(ns lupapalvelu.xml.emit
  (:use [clojure.data.xml]))

(declare element-to-xml)

(defn- create-element-hierarcy [data model]
  (element (:tag model) (:attr model)
           (if (:child model)
             (map #(element-to-xml data %) (:child model))
             (str data))))

(defn element-to-xml [data model]
  (let [current-data ((:tag model) data)]
    (when (not (nil? current-data))
      (if (sequential? current-data)
        (for [item current-data]
          (create-element-hierarcy item model))
        (create-element-hierarcy current-data model)))))
