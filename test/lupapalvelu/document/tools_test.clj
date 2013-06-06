(ns lupapalvelu.document.tools-test
  (:use [lupapalvelu.document.tools]
        [midje.sweet]
        [midje.util :only [expose-testables]]))

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
  (-> schema
    (create nil-values)
    flattened) => expected-simple-document)

(fact "simple schema with wrapped values"
  (-> schema
    (create nil-values)
    flattened
    (wrapped :value)) => expected-wrapped-simple-document)

;;
;; Public api
;;

(fact "wrapped defaults to :value key"
  (wrapped nil) => {:value nil}
  (wrapped {:value nil}) => {:value {:value nil}})

(fact "un-wrapped"
  (un-wrapped {:k {:value nil}}) => {:k nil}
  (un-wrapped expected-wrapped-simple-document :value) => expected-simple-document
  (un-wrapped (wrapped expected-simple-document)) => expected-simple-document)

(fact "create-dummy-document-data"
  (create-document-data schema) => expected-wrapped-simple-document)

(def expected-wrapped-simple-document-with-timestamp
  {:band {:name {:value nil :modified nil}
          :genre {:value nil :modified nil}
          :members {:0 {:name {:value nil :modified nil}
                        :instrument {:value nil :modified nil}}}}})

(fact "with-timestamps"
  (with-timestamp nil nil) => nil
  (with-timestamp {} nil) => {}
  (with-timestamp expected-wrapped-simple-document nil) => expected-wrapped-simple-document-with-timestamp)

