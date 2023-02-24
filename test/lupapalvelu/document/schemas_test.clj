(ns lupapalvelu.document.schemas-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.schemas :refer [repeatable update-in-body
                                                  resolve-accordion-field-values]]))

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

(facts "Resolve accordion field values"
       (let [doc {:schema-info {:accordion-fields [{:type  :selected
                                                    :paths [["foo" "first"]
                                                            ["foo" "second"]
                                                            ["bar" "third"]]}
                                                   {:type  :text
                                                    :paths [["text" "tax"]]}
                                                   [["str1" "s1"]
                                                    ["str2" "s2"]
                                                    ["not" "available" "in" "data"]]]}
                  :data        {:foo  {:first  {:value "First"}
                                       :second {:value "Second"}}
                                :bar  {:third {:value "Third"}}
                                :text {:hii {:value "blaah"}
                                       :tax {:value "Tax"}}
                                :str1 {:s1 {:value "String1"}}
                                :str2 {:s2 {:value "String2"}}}}]
         (fact "None selected"
               (resolve-accordion-field-values doc)
               => ["Tax" "String1" "String2"])
         (fact "Foo selected"
               (resolve-accordion-field-values (update-in doc
                                                          [:data :_selected :value]
                                                          (constantly "foo")))
               => ["First" "Second" "Tax" "String1" "String2"])
         (fact "Bar selected"
               (resolve-accordion-field-values (update-in doc
                                                          [:data :_selected :value]
                                                          (constantly "bar")))
               => ["Third" "Tax" "String1" "String2"])))
