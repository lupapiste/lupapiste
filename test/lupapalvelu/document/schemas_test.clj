(ns lupapalvelu.document.schemas-test
  (:use [lupapalvelu.document.schemas]
        [midje.sweet]))

(facts "Facts about to-map-by-name"
  (fact (to-map-by-name []) => {})
  (fact (to-map-by-name [{:info {:name "a"}}]) => {"a" {:info {:name "a"}}})
  (fact (to-map-by-name
          [{:info {:name "a"}}
           {:info {:name "b"}}])
        =>
          {"a" {:info {:name "a"}}
           "b" {:info {:name "b"}}}))
