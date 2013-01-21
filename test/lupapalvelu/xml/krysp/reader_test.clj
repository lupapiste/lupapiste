(ns lupapalvelu.xml.krysp.reader-test
  (:use [lupapalvelu.xml.krysp.reader]
        [midje.sweet]))

(facts
  (fact "takes (recursively) last sub-keyword from keywords"
        (strip-keys {:a:b:c {:x:y:z 2 :q:w:y 3}}) => {:c {:z 2 :y 3}})
  (fact "does not touch other key types"
        (strip-keys {[:a :b :c] 1
                     ":a:b:c" 2}) => {[:a :b :c] 1
                                      ":a:b:c" 2}))
