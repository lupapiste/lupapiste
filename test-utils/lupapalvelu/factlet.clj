(ns lupapalvelu.factlet
  (:use midje.sweet
        clojure.walk)
  (:require [midje.parsing.util.recognizing :as recognize]))

(defn create-facts
  [c]
  (reduce
    (fn [form [k v]]
      (if (recognize/all-arrows (str k)) ;; will change for midje 1.6, bugga.
        (conj form '_ `(fact ~(str (-> form butlast last)) ~(-> form butlast last) ~k ~v))
        (conj form k v)))
    [] (partition 2 c)))

(defmacro factlet [letform & body]
  `(let ~(create-facts letform)
     ~@body))

(defmacro fact* [& form]
  (let [form (prewalk
               (fn [x]
                 (if (and (list? x) (= 'let (first x)))
                   `(let ~(create-facts (second x)) ~@(-> x next next))
                   x)) form)]
    `(do ~@form)))
