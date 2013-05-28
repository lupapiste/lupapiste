(ns lupapalvelu.factlet
  (:use midje.sweet
        clojure.walk)
  (:require [midje.parsing.util.recognizing :as recognize]))

(defn- create-facts
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

(defn- let? [form]
  (and (list? form) (= 'let (first form))))

(defn- let-facts [form]
  (prewalk
    (fn [x]
      (if (let? x)
        `(let ~(create-facts (second x)) ~@(nnext x))
        x))
    form))

(defmacro fact* [& form]
  `(do ~@(let-facts form)))
