(ns lupapalvelu.bag-test
  (:require [lupapalvelu.bag :refer :all]
            [midje.sweet :refer :all]))

(facts "Bag"
  (let [a {:hello "world"}]
    (fact "No meta, no bag value"
      (meta a) => nil
      (out a :foo) => nil
      (bag a :foo "not found") => "not found")
    (fact "Add bag value"
      (let [a (put a :foo "bar")]
        (meta a) => {:lupapalvelu.bag/bag {:foo "bar"}}
        (out a :foo) => "bar"
        (bag a :foo
             (println "Is not printed.")
             (throw (ex-info "Is not thrown" {}))) => "bar"
        (fact "Clear bag value"
          (let [a (put a :two 2)]
            (meta a) => {:lupapalvelu.bag/bag {:foo "bar" :two 2}}
            (let [a (clear a :foo)]
              (meta a) => {:lupapalvelu.bag/bag {:two 2}}
              (bag a :foo "not-found") => "not-found"
              (fact "Nil is the same as missing"
                (let [a (put a :two nil)]
                  (meta a) => {:lupapalvelu.bag/bag {:two nil}}
                  (out a :two) => nil
                  (bag a :two "Hello!") => "Hello!")))))
        (fact "Clear the last bag value"
          (meta (clear a :foo)) => nil
          (meta (clear (vary-meta a assoc :three 3) :foo)) => {:three 3})))))
