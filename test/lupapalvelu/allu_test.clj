(ns lupapalvelu.allu-test
  (:require [lupapalvelu.allu :refer :all]
            [lupapalvelu.backing-system.allu.schemas
             :refer [ValidPlacementApplication ValidShortTermRental ValidPromotion]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$pull $ne $exists]]
            [sade.schema-generators :as ssg]
            [schema.core :as sc]))

(testable-privates lupapalvelu.allu location-type)

(fact "location-type"
  (sc/with-fn-validation
    (fact "sijoitussopimus always has custom drawings"
      (let [application (ssg/generate ValidPlacementApplication)]
        (location-type application) => "custom"))

    (fact "lyhytaikainen maanvuokraus"
      (let [application (ssg/generate ValidShortTermRental)]
        (location-type application)
        => (get-in application [:documents 3 :data :lmv-location :location-type :value])))

    (fact "promootio"
      (let [application (ssg/generate ValidPromotion)]
        (location-type application)
        => (get-in application [:documents 3 :data :promootio-location :location-type :value])))))

(fact "make-names-unique"
  (make-names-unique :n [{:n "one" :z :hello}]) => [{:n "one" :z :hello}]
  (make-names-unique :n [{:n "one" :z :hello :id 11} {:n "one" :d :world :id 22}])
  => [{:n "one (11)" :z :hello :id 11} {:n "one (22)" :d :world :id 22}]
  (make-names-unique :n [{:n "one" :z :hello :id 1}
                         {:n "two" :d :world :id 2}
                         {:n "one" :y 3 :id 3}
                         {:n "two" :m 1 :id 4}
                         {:n "one" :id 5}
                         {:n "three" :d :world :id 6}])
  => (just [{:n "one (1)" :z :hello :id 1}
            {:n "two (2)" :d :world :id 2}
            {:n "one (3)" :y 3 :id 3}
            {:n "two (4)" :m 1 :id 4}
            {:n "one (5)" :id 5}
            {:n "three" :d :world :id 6}]
           :in-any-order)
  (make-names-unique :n [{} {}]) => [{:n " (null)"} {:n " (null)"}])

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

(facts "Filter Allu drawings"
  (fact "No drawings"
    (filter-allu-drawings {:drawings []} "kind") => nil
    (filter-allu-drawings {} "kind") => nil)
  (fact "One drawing, no match"
    (filter-allu-drawings {:drawings [{:allu-id "id"
                                       :source    "kind"}]} "kind")
    (filter-allu-drawings {:drawings [{:source    "hello"}]} "kind")=> nil))
(fact "One drawing, match"
  (filter-allu-drawings {:drawings [{:allu-id "id"
                                     :foo     "bar"
                                     :source  "hello"}]} "kind")
  => {$pull {:drawings {:allu-id {$exists true}
                        :source  {$ne "kind"}}}}
  (filter-allu-drawings {:drawings [{:allu-id "id"}]} "kind")
  => {$pull {:drawings {:allu-id {$exists true}
                        :source  {$ne "kind"}}}})

(fact "Multiple drawings, no matches"
  (filter-allu-drawings {:drawings [{:allu-id "id"
                                     :foo     "bar"
                                     :source    "kind"}
                                    {:foo "bar"}
                                    {:allu-id nil
                                     :source    "hello"}]} "kind")
  => nil)

(fact "Multiple drawings, match"
  (filter-allu-drawings {:drawings [{:allu-id "id"
                                     :foo     "bar"
                                     :source    "kind"}
                                    {:source    "hello"}
                                    {:allu-id "hii"
                                     :source    "hello"}]} "kind")
  => {$pull {:drawings {:allu-id {$exists true}
                        :source  {$ne "kind"}}}})
