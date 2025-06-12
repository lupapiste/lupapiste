(ns sade.http-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [def-]]
            [sade.env :as env]
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

  (fact "Example from RFC 6750"
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

  (fact "Basic auth returns nil"
    (http/parse-bearer {:headers {"authorization" "Basic x"}}) => nil))

(fact "client-ip"
  (fact "x-forwarded-for"
    (env/with-feature-value :parse-x-forwarded-for true
      (fact "single ip -> should never happen with GCP"
        (http/client-ip {:headers {"x-forwarded-for" "10.0.0.1"}}) => nil?
        (http/client-ip {:headers {"x-forwarded-for" " 10.0.0.1  "}}) => nil?)
      (fact "two ips"
        ;; GCP:
        ;; The load balancer appends two IP addresses separated by a single comma to the X-Forwarded-For header
        ;; in the following order:
        ;; - The IP address of the client that connects to the load balancer
        ;; - The IP address of the load balancer's forwarding rule
        (http/client-ip {:headers {"x-forwarded-for" "10.0.0.1,192.168.0.1"}}) => "10.0.0.1"
        (http/client-ip {:headers {"x-forwarded-for" " 10.0.0.1,192.168.0.1  "}}) => "10.0.0.1")
      (fact "three ips"
        ;; GCP:
        ;; If the request includes an X-Forwarded-For header, the load balancer preserves the supplied value
        ;; before the <client-ip>,<load-balancer-ip>
        ;; The load balancer does not verify any IP addresses that precede <client-ip>,<load-balancer-ip> in
        ;; this header. The preceding IP addresses might contain other characters, including spaces.
        ;; Note that we ignore the
        (http/client-ip {:headers {"x-forwarded-for" "10.0.0.1,192.168.0.1,127.0.0.1"}}) => "192.168.0.1"
        (http/client-ip {:headers {"x-forwarded-for" " 10.0.0.1    ,192.168.0.1,127.0.0.1  "}}) => "192.168.0.1"
        (http/client-ip {:headers {"x-forwarded-for" " foo bar potentially malicious data !!   ,192.168.0.1,127.0.0.1  "}}) => "192.168.0.1")))
  (fact "without x-forwarded-for feature"
    (env/with-feature-value :parse-x-forwarded-for false
      (fact "single ip -> should never happen with GCP"
        (http/client-ip {:headers {"x-forwarded-for" "10.0.0.1"}}) => nil?
        (http/client-ip {:headers {"x-forwarded-for" " 10.0.0.1  "}}) => nil?)
      (fact "two ips"
        (http/client-ip {:headers {"x-forwarded-for" "10.0.0.1,192.168.0.1"}}) => nil?
        (http/client-ip {:headers {"x-forwarded-for" " 10.0.0.1,192.168.0.1  "}}) => nil?)
      (fact "three ips"
        (http/client-ip {:headers {"x-forwarded-for" "10.0.0.1,192.168.0.1,127.0.0.1"}}) => nil?
        (http/client-ip {:headers {"x-forwarded-for" " 10.0.0.1    ,192.168.0.1,127.0.0.1  "}}) => nil?
        (http/client-ip {:headers {"x-forwarded-for" " foo bar potentially malicious data !!   ,192.168.0.1,127.0.0.1  "}}) => nil?)))
  (fact "x-real-ip"
    (fact "single ip"
      (http/client-ip {:headers {"x-real-ip" "10.0.0.1"}}) => "10.0.0.1"
      (http/client-ip {:headers {"x-forwarded-for" "   "
                                 "x-real-ip"       "10.0.0.1"}}) => "10.0.0.1"))
  (fact "remote-addr"
    (http/client-ip {:remote-addr "10.0.0.1"}) => "10.0.0.1")
  (fact "priorities"
    (fact "without x-forwarded-for"
      (env/with-feature-value :parse-x-forwarded-for false
        (http/client-ip {:remote-addr "10.0.0.1"}) => "10.0.0.1"
        (http/client-ip {:remote-addr "10.0.0.1"
                         :headers     {"x-real-ip" "10.0.0.2"}}) => "10.0.0.2"
        (http/client-ip {:remote-addr "10.0.0.1"
                         :headers     {"x-real-ip"       "10.0.0.2"
                                       "x-forwarded-for" "10.0.0.3, 10.0.0.4"}}) => "10.0.0.2"))
    (fact "with x-forwarded-for"
      (env/with-feature-value :parse-x-forwarded-for true
        (http/client-ip {:remote-addr "10.0.0.1"}) => "10.0.0.1"
        (http/client-ip {:remote-addr "10.0.0.1"
                         :headers     {"x-real-ip" "10.0.0.2"}}) => "10.0.0.2"
        (http/client-ip {:remote-addr "10.0.0.1"
                         :headers     {"x-real-ip"       "10.0.0.2"
                                       "x-forwarded-for" "10.0.0.3, 10.0.0.4"}}) => "10.0.0.3"))))
