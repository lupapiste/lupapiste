(ns lupapalvelu.factlet-test
  (:use lupapalvelu.factlet
        midje.sweet))

(def call-count (atom 0))

(defn result [x]
  (swap! call-count inc)
  x)

(facts "about factlet"
  (reset! call-count 0)
  (factlet
    [a (+ 1 1) => (result 2)
     b (+ a 1) => (result 3)]
    a => 2
    b => 3)
  @call-count => 2)
