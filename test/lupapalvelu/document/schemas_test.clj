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

(fact "assoc-in-body"
  (let [body [{:name "foo"
               :body [{:name "bar"
                       :body [{:name "baz"}]}]}
              {:name "hoo"
               :body [{:name "haa"}
                      {:name "huu"}]}]]
    (assoc-in-body body ["foo" "bar" "baz"] :testi true) => [{:name "foo"
                                                              :body [{:name "bar"
                                                                      :body [{:name "baz" :testi true}]}]}
                                                             {:name "hoo"
                                                              :body [{:name "haa"}
                                                                     {:name "huu"}]}]
    (assoc-in-body body ["foo" "bar" "wrong"] :testi true) => body

    (last (assoc-in-body body ["hoo"] :testi false)) => {:name "hoo"
                                                         :body [{:name "haa"}
                                                                {:name "huu"}]
                                                         :testi false}))

