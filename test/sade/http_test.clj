(ns sade.http-test
  (:require [sade.core :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.http :as http]))

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

(fact "evil headers are filtered"
  (http/secure-headers {:headers {"cookie" 1 "Set-Cookie" 1 "server" 1 "Host" 1
                                  "connection" 1 "X-Powered-By" "LOL PHP"
                                  "User-Agent" "007"}}) => {:headers {"User-Agent" "007"}})

(facts "Bearer auth"

  (fact "Examle from RFC 6750"
    (http/parse-bearer {:headers {"authorization" "Bearer mF_9.B5f-4.1JqM"}}) => "mF_9.B5f-4.1JqM")

  (fact "x"
    (http/parse-bearer {:headers {"authorization" "Bearer x"}}) => "x")

  (fact "empty or blank returns nil"
    (http/parse-bearer nil) => nil
    (http/parse-bearer {:headers nil}) => nil
    (http/parse-bearer {:headers {"authorization" nil}}) => nil
    (http/parse-bearer {:headers {"authorization" ""}}) => nil
    (http/parse-bearer {:headers {"authorization" " "}}) => nil
    (http/parse-bearer {:headers {"authorization" "Bearer"}}) => nil
    (http/parse-bearer {:headers {"authorization" "Bearer "}}) => nil
    (http/parse-bearer {:headers {"authorization" "Bearer  "}}) => nil)

  (fact "non-ascii chars not supported"
    (http/parse-bearer {:headers {"authorization" "Bearer vesist\u00f6"}}) => nil)

  (fact "must not be in lower case"
    (http/parse-bearer {:headers {"authorization" "bearer x"}}) => nil)

  (fact "Basic auth return nil"
    (http/parse-bearer {:headers {"authorization" "Basic x"}}) => nil))
