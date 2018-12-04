(ns lupapalvelu.allu-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.allu :refer :all]))

(fact "make-names-unique"
  (make-names-unique :n [{:n "one" :z :hello}]) => [{:n "one" :z :hello}]
  (make-names-unique :n [{:n "one" :z :hello} {:n "one" :d :world}])
  => [{:n "one 1" :z :hello} {:n "one 2" :d :world}]
  (make-names-unique :n [{:n "one" :z :hello} {:n "two" :d :world}
                         {:n "one" :y 3} {:n "two" :m 1}
                         {:n "one"} {:n "three" :d :world}])
  => (just [{:n "one 1" :z :hello} {:n "two 1" :d :world}
            {:n "one 2" :y 3} {:n "two 2" :m 1}
            {:n "one 3"} {:n "three" :d :world}]
           :in-any-order)
  (make-names-unique :n [{} {}]) => [{:n " 1"} {:n " 2"}])

(facts "merge-drawings"
  (fact "No old drawings"
    (merge-drawings {} [])
    => {:allu-drawings []
        :new-drawings  []}
    (merge-drawings {} [{:id 1 :name "foo"}])
    => {:allu-drawings []
        :new-drawings  [{:id 1 :name "foo"}]})
  (fact "Old non-Allu drawings"
    (merge-drawings {:drawings [{:id 1 :name "Hello" :desc "hi"}
                                {:id 2 :name "olleH"}]}
                    [{:id 1 :name "World"}])
    => {:allu-drawings []
        :new-drawings  [{:id 1 :name "World"}]})
  (fact "Allu drawings"
    (merge-drawings {:drawings [{:id "allu1" :allu-id 1 :name "Allu One"}]}
                    [{:id "allu1" :name "A1" :desc "Changed"}])
    => {:allu-drawings [{:id "allu1" :allu-id 1 :name "A1" :desc "Changed"}]
        :new-drawings  []}
    (merge-drawings {:drawings [{:id "allu1" :allu-id 1 :name "Allu One"}
                                {:id "allu2" :allu-id 2 :name "Allu Two"}]}
                    [{:id "allu1" :name "A1" :desc "Changed"}])
    => {:allu-drawings [{:id "allu1" :allu-id 1 :name "A1" :desc "Changed"}]
        :new-drawings  []})
  (fact "Both"
    (merge-drawings {:drawings [{:id "allu1" :allu-id 1 :name "Allu One"}
                                {:id "allu2" :allu-id 2 :name "Allu Two"}
                                {:id "allu3" :allu-id 3 :name "Allu Three"}
                                {:id 1 :name "One" :desc "yi"}]}
                    [{:id "allu1" :name "A1" :desc "Changed"}
                     {:id "allu3" :name "A3" :desc "san"}
                     {:id 1 :name "Yi"}
                     {:id 2 :name "Er"}])
    => (just {:allu-drawings (just [{:id "allu1" :allu-id 1 :name "A1" :desc "Changed"}
                                    {:id "allu3" :allu-id 3 :name "A3" :desc "san"}]
                                   :in-any-order)
              :new-drawings  (just [{:id 1 :name "Yi"}
                                    {:id 2 :name "Er"}]
                                   :in-any-order)})))
