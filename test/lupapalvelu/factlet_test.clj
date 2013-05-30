(ns lupapalvelu.factlet-test
  (:use lupapalvelu.factlet
        midje.sweet))

(def fact-call-count (atom 0))
(def value-call-count (atom 0))

(defn result [x] (swap! fact-call-count inc) x)
(defn plus [x y] (swap! value-call-count inc) (+ x y))

(fact "about fact*"
  (reset! fact-call-count 0)
  (reset! value-call-count 0)
  (fact*
    (let [a (plus 1 1) =not=> (result 3)
          b (plus a 1) => (result 3)]
      a => 2
      b => 3))
  @fact-call-count => 2
  @value-call-count => 2)

(fact "about facts*"
  (reset! fact-call-count 0)
  (reset! value-call-count 0)
  (facts*
    (let [a (plus 1 1) =not=> (result 3)
          b (plus a 1) => (result 3)]
      a => 2
      b => 3))
  @fact-call-count => 2
  @value-call-count => 2)
