(ns lupapalvelu.operations-test
  (:use [lupapalvelu.operations]
        [midje.sweet]))

(def application1 {:operations [{:created 1374583208236,
                                 :id "51ee79a896467a9a0d0a1b0d",
                                 :name "yleiset-alueet-kaivuulupa",
                                 :operation-type "publicArea"}]})

(def application2 {:operations [{:created 1374583208236,
                                 :id "51ee79a896467a9a0d0a1b0d",
                                 :name "yleiset-alueet-kaivuulupa",
                                 :operation-type nil}]})

(facts
  (fact "when operation-type is given"
    (get-operation application1) => :publicArea
    (validate-public-area? irrelevant application1) => nil)
  (fact "when operation-type is not given"
    (get-operation application2) => nil
    (validate-public-area? irrelevant application2) => (contains {:ok false})))
