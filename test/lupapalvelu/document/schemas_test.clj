(ns lupapalvelu.document.schemas-test
  (:use [lupapalvelu.document.schemas]
        [midje.sweet]))

(facts "body"
  (fact "flattens stuff into lists"    (body 1 2 [3 4] 5) => [1 2 3 4 5])
  (fact "does not flatten recursively" (body 1 2 [3 4 [5]]) => [1 2 3 4 [5]]))

(facts "repeatable"
  (fact (repeatable "beers" {:name :beer
                             :type :string}) => [{:name "beers"
                                                  :type :group
                                                  :repeating true
                                                  :body [{:name :beer
                                                          :type :string}]}]))

