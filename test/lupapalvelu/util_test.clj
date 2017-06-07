(ns sade.util-test
  (:refer-clojure :exclude [pos? neg? zero? max-key])
  (:require [lupapalvelu.util :refer :all]
            [midje.sweet :refer :all]))

(facts max-key
  (fact "single arity"
    (max-key :foo) => nil?)

  (fact "key not found"
    (max-key :foo {:bar 1}) => nil?)

  (fact "one element"
    (max-key :foo {:foo 1}) => {:foo 1})

  (fact "multiple elements"
    (max-key :foo {:foo 1} {:foo 3} {:foo 2}) => {:foo 3}))
