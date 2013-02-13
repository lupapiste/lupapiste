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

(facts "body"
  (fact "flattens stuff into lists"    (body 1 2 [3 4] 5) => [1 2 3 4 5])
  (fact "does not flatten recursively" (body 1 2 [3 4 [5]]) => [1 2 3 4 [5]]))
