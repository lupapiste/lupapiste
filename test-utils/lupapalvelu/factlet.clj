(ns lupapalvelu.factlet
  (:use [midje.sweet]))

(defn create-facts
  [c]
  (reduce
    (fn [form [k v]]
      (if (= k (symbol =>))
        (conj form '_ `(fact ~(str (-> form butlast last)) ~(last form) => ~v))
        (conj form k v)))
    [] (partition 2 c)))

(defmacro factlet [letform & body]
  `(let ~(create-facts letform)
     ~@body))
