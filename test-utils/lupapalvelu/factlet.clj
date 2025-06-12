(ns lupapalvelu.factlet
  (:require [midje.sweet :refer :all]
            [midje.parsing.util.recognizing :as recognize]
            [clojure.walk :refer [prewalk]]))

;;; Processings let bindings in facts

(defn let? [form]
  (and
    (list? form)
    (or
      (= 'let (first form))
      (= 'clojure.core/let (first form)))))

(defn checkables-to-facts-in-let-bindings
  "Rewrites let-bindings by adding facts for all checkables.
   Form (let [a 1 => 1]) gets rewritten to:
        (let a 1
             _ (fact ... a => 1)])."
  [bindings]
  (reduce
    (fn [form [k v]]
      (if (recognize/all-arrows (str k))
        (conj form '_ `(midje.sweet/fact
                         ~(str (-> form butlast last) " " k
                               " in let-bindings at line #" (-> bindings meta :line))
                         ~(-> form butlast last) ~k ~v))
        (conj form k v)))
    [] (partition 2 bindings)))

(defn checkables-to-facts-in-lets
  "Rewrites the let-bindings from the form recursively to support
   using checkables in bindings. See bind-facts-to-checkables for details."
  [form]
  (prewalk
    (fn [x]
      (if (let? x)
        `(let ~(checkables-to-facts-in-let-bindings (with-meta (second x) (meta x))) ~@(nnext x))
        x))
    form))

(defmacro fact* [& forms]
  (with-meta `(fact (do ~@(checkables-to-facts-in-lets forms))) (meta &form)))

(defmacro facts* [& forms]
  (with-meta `(fact* ~@forms) (meta &form)))

;(facts* (let [a :b => :c
;             b a => :d
;             _ b => :d]
;         b => :c)
; (= 5 5)
; :e => :f
; (let [_ :g => :e])
; (fact* :h => :i))