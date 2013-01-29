(ns lupapalvelu.xml.krysp.reader-test
  (:use [lupapalvelu.xml.krysp.reader]
        [midje.sweet]))

(facts "strip-keys"
  (fact "takes (recursively) last sub-keyword from keywords"
        (strip-keys {:a:b:c {:x:y:z 2 :y 3}}) => {:c {:z 2 :y 3}})
  (fact "does not touch other key types"
        (strip-keys {[:a :b :c] 1
                     ":a:b:c" 2}) => {[:a :b :c] 1
                                      ":a:b:c" 2}))

(fact "strip-nils"
  (strip-nils {:a 1 :b nil :c {:d 2 :e nil}}) => {:a 1 :c {:d 2}})

(fact "strip-empty-maps"
  (strip-empty-maps {:a 1 :b {} :c {:d 2 :e {}}}) => {:a 1 :c {:d 2}})

(facts "to-boolean"
  (to-boolean 1) => 1
  (to-boolean true) => true
  (to-boolean "true") => true
  (to-boolean false) => false
  (to-boolean "false") => false
  (to-boolean "truthy") => "truthy")

(facts "strip-booleans"
  (convert-booleans {:a "true" :b {:c "false" :d "falsey"}}) => {:a true :b {:c false :d "falsey"}})

(facts "strip-xml-namespaces"
  (strip-xml-namespaces
    {:tag :a:b, :attrs nil, :content
     [{:tag :c:d, :attrs nil, :content nil}]}) => {:tag :b, :attrs nil, :content
                                                   [{:tag :d, :attrs nil, :content nil}]})

(facts "translations"
  (let [translations {:a :A :b :B}]
    (fact (translate translations :a) => :A)
    (fact (translate translations :c) => nil)
    (fact (translate translations :c :nils true) => :c)

    (fact (translate-keys translations {:a 1 :b 2}) => {:A 1 :B 2})
    (fact (translate-keys translations {:a 1 :c 2}) => {:A 1})))

(facts "map indexing"
  (fact (index-maps {:a {:b [{:c 1}
                             {:c 2}
                             {:c 3}]}}) => {:a {:b {:1 {:c 1}
                                                    :2 {:c 2}
                                                    :3 {:c 3}}}}))
