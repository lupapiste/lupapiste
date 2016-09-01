(ns lupapalvelu.document.schemas-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.schemas :refer [repeatable update-in-body]]))

(facts "repeatable"
  (fact (repeatable "beers" {:name :beer
                             :type :string}) => [{:name "beers"
                                                  :type :group
                                                  :repeating true
                                                  :body [{:name :beer
                                                          :type :string}]}]))

(fact "update-in-body"
  (let [body [{:name "foo"
               :body [{:name "bar"
                       :body [{:name "baz"}]}]}
              {:name "hoo"
               :body [{:name "haa"}
                      {:name "huu"}]}]]
    (update-in-body body ["foo" "bar" "baz"] :testi (constantly true)) => [{:name "foo"
                                                                            :body [{:name "bar"
                                                                                    :body [{:name "baz" :testi true}]}]}
                                                                           {:name "hoo"
                                                                            :body [{:name "haa"}
                                                                                   {:name "huu"}]}]
    (update-in-body body ["foo" "bar" "wrong"] :testi (constantly true)) => body

    (last (update-in-body body ["hoo"] :testi (constantly false))) => {:name "hoo"
                                                                       :body [{:name "haa"}
                                                                              {:name "huu"}]
                                                                       :testi false}

    (first (update-in-body body ["foo"] :body (fn [old-val] (remove map? old-val)))) => {:name "foo" :body []}
    (first (update-in-body body ["foo"] :body #(conj % "faa"))) => {:name "foo"
                                                                    :body [{:name "bar"
                                                                            :body [{:name "baz"}]}
                                                                           "faa"]}))

