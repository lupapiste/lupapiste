(ns lupapalvelu.document.tools-test
  (:use [lupapalvelu.document.tools]
        [midje.sweet]))

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
          :members {:0 {:members {:name nil :instrument nil}}}}})

(def expected-k-wrapped-simple-document
  {:band {:name {:k nil}
          :genre {:k nil}
          :members {:0 {:members {:name {:k nil} :instrument {:k nil}}}}}})

(fact "simple schema"
  (-> schema
    (create nil-values)
    flattened) => expected-simple-document)

(fact "simple schema with wrapped values"
  (-> schema
    (create nil-values)
    flattened
    (wrapped :k)) => expected-k-wrapped-simple-document)

(fact "wrapped defaults to :value key"
  (wrapped nil) => {:value nil}
  (wrapped {:k nil}) => {:k {:value nil}})

(fact "un-wrapped"
  (un-wrapped {:k {:value nil}}) => {:k nil}
  (un-wrapped expected-k-wrapped-simple-document :k) => expected-simple-document
  (un-wrapped (wrapped expected-simple-document)) => expected-simple-document)
