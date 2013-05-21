(ns lupapalvelu.factlet-test
  (:use lupapalvelu.factlet
        midje.sweet))

(def fact-call-count (atom 0))
(def value-call-count (atom 0))

(defn result [x] (swap! fact-call-count inc) x)
(defn plus [x y] (swap! value-call-count inc) (+ x y))

(facts "about factlet"
  (reset! call-count 0)
  (factlet
    [a (plus 1 1) => (result 2)
     b (plus a 1) => (result 3)]
    a => 2
    b => 3)
  @fact-call-count => 2
  @value-call-count => 2)
