(ns sade.http-test
  (:require [sade.core :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            sade.http))

(testable-privates sade.http merge-to-defaults)

(def- some-defaults {:jokuavain "oletusarvo"})

(background (sade.env/value :http-client) => some-defaults)

(facts "Only default options"
  (fact "no args" (merge-to-defaults) => some-defaults)
  (fact "nil" (merge-to-defaults nil) => some-defaults)
  (fact "empty map" (merge-to-defaults {}) => some-defaults)
  (fact "apply nil" (apply merge-to-defaults nil) => some-defaults)
  (fact "apply empty" (apply merge-to-defaults []) => some-defaults))

(facts "map parameter"
  (fact "new parameter" (merge-to-defaults {:b 2}) => {:jokuavain "oletusarvo" :b 2})
  (fact "new parameters" (merge-to-defaults {:b 2 :c 3}) => {:jokuavain "oletusarvo" :b 2 :c 3})
  (fact "override parameter" (merge-to-defaults {:jokuavain 2 :c 3}) => {:jokuavain 2 :c 3}))

(facts "parameters array"
  (fact "new parameter" (merge-to-defaults :b 2) => {:jokuavain "oletusarvo" :b 2})
  (fact "new parameters" (merge-to-defaults :b 2 :c 3) => {:jokuavain "oletusarvo" :b 2 :c 3})
  (fact "override parameter" (merge-to-defaults :jokuavain 2 :c 3) => {:jokuavain 2 :c 3}))

(fact "uneven number of options is not allowed"
  (merge-to-defaults :a :b 2) => (throws IllegalArgumentException "uneven number of options"))
