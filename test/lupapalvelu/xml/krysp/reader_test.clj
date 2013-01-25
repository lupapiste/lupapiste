(ns lupapalvelu.xml.krysp.reader-test
  (:use [lupapalvelu.xml.krysp.reader]
        [midje.sweet]))

(facts
  (fact "takes (recursively) last sub-keyword from keywords"
        (strip-keys {:a:b:c {:x:y:z 2 :y 3}}) => {:c {:z 2 :y 3}})
  (fact "does not touch other key types"
        (strip-keys {[:a :b :c] 1
                     ":a:b:c" 2}) => {[:a :b :c] 1
                                      ":a:b:c" 2}))

(fact (strip-nils {:a 1 :b nil :c {:d 2 :e nil}}) => {:a 1 :c {:d 2}})

(fact (strip-empty-maps {:a 1 :b {} :c {:d 2 :e {}}}) => {:a 1 :c {:d 2}})

(fact
  (let [translations {:a :A :b :B}]
    (fact (translate translations :a) => :A)
    (fact (translate translations :c) => nil)
    (fact (translate translations :c :nils true) => :c)))
