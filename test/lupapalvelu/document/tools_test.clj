(ns lupapalvelu.document.tools-test
  (:require [lupapalvelu.document.tools :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [expose-testables]]))

(expose-testables lupapalvelu.document.tools)

(def schema
  {:info {:name "band"},
   :body
   [{:name "band",
     :type :group,
     :body
     [{:name "name", :type :string}
      {:name "genre", :type :string}
      {:name "members"
       :type :group
       :repeating true
       :body [{:name "name", :type :string}
              {:name "instrument", :type :string}]}]}]})

(def expected-simple-document
  {:band {:name nil
          :genre nil
          :members {:0 {:name nil
                        :instrument nil}}}})

(def expected-wrapped-simple-document
  {:band {:name {:value nil}
          :genre {:value nil}
          :members {:0 {:name {:value nil}
                        :instrument {:value nil}}}}})

(fact "simple schema"
  (create-unwrapped-data schema nil-values) => expected-simple-document)

(fact "simple schema with wrapped values"
  (-> schema
    (create-unwrapped-data nil-values)
    (wrapped :value)) => expected-wrapped-simple-document)

;;
;; Public api
;;

(facts "body"
  (fact "flattens stuff into lists"    (body 1 2 [3 4] 5) => [1 2 3 4 5])
  (fact "does not flatten recursively" (body 1 2 [3 4 [5]]) => [1 2 3 4 [5]]))

(fact "wrapped defaults to :value key"
  (wrapped nil) => {:value nil}
  (wrapped {:value nil}) => {:value {:value nil}})

(fact "unwrapped"
  (unwrapped {:k {:value nil}}) => {:k nil}
  (unwrapped expected-wrapped-simple-document :value) => expected-simple-document
  (unwrapped (wrapped expected-simple-document)) => expected-simple-document)

(fact "create-dummy-document-data"
  (create-document-data schema) => expected-wrapped-simple-document)

(def expected-wrapped-simple-document-timestamped
  {:band {:name {:value nil :modified nil}
          :genre {:value nil :modified nil}
          :members {:0 {:name {:value nil :modified nil}
                        :instrument {:value nil :modified nil}}}}})

(fact "timestampeds"
  (timestamped nil nil) => nil
  (timestamped {} nil) => {}
  (timestamped expected-wrapped-simple-document nil) => expected-wrapped-simple-document-timestamped)

(fact "schema-body-without-element-by-name"
  (schema-body-without-element-by-name (:body schema) "band") => []
  (schema-body-without-element-by-name (:body schema) "invalid") => (:body schema)
  (schema-body-without-element-by-name (:body schema) "members") => [{:name "band"
                                                                      :type :group
                                                                      :body [{:name "name"
                                                                              :type :string}
                                                                             {:name "genre"
                                                                              :type :string}]}])

(fact "strip-elements-by-name"
  (schema-without-element-by-name schema "band") => {:info {:name "band"} :body []}
  (schema-without-element-by-name schema "INVALID") => schema
  (schema-without-element-by-name schema "band") => {:info {:name "band"} :body []})

(def deep-find-test-data {:a {:1 {:b {:c 1}}
                              :2 {:b {:c 2}}
                              :3 {:c {:b {:c 3}}}}})

(def deep-find-result (deep-find deep-find-test-data [:b :c]))

(fact "Deep find"
      (some #(= % [[:a :1] 1]) deep-find-result) => truthy
      (some #(= % [[:a :2] 2]) deep-find-result) => truthy
      (some #(= % [[:a :3 :c] 3]) deep-find-result) => truthy
      (deep-find deep-find-test-data [:b :e]) => '()
      )

(def updates [["a" 1] ["b" 2] ["c" 3]])

(fact "get-update-item-value"
  (get-update-item-value updates "a") => 1
  (get-update-item-value updates "non-existing") => nil
  )


