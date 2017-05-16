(ns lupapalvelu.neighbors-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.neighbors-api :refer :all]))

(testable-privates lupapalvelu.neighbors-api valid-token?)

(def valid-token "123456")
(def valid-statuses [{:state "open"}
                     {:state "email-sent" :token "other token" :created 2}
                     {:state "email-sent" :token valid-token   :created 0}])

(def day (* 24 60 60 1000))

(facts "valid-token?"

  (fact "almost immediate token use"
    (valid-token? valid-token valid-statuses 1) => true)

  (fact "token use after a day"
    (valid-token? valid-token valid-statuses day) => true)

  (fact "token use after 2 weeks - 1 ms: valid"
    (valid-token? valid-token valid-statuses (* 2 7 day)) => true)

  (fact "token use at 2 weeks: valid"
    (valid-token? valid-token valid-statuses (dec (* 2 7 day))) => true)

  (fact "token use after 2 weeks + 1 ms: expired. See lupapalvelu.ttl namespace."
    (valid-token? valid-token valid-statuses (inc (* 2 7 day))) => false)

  (fact "token use before it was created: very dodgy, should fail"
    (valid-token? valid-token [{:state "email-sent" :token valid-token :created 2}] 1) => false)

  (fact "token use at the same time it was created: dodgy, should fail"
    (valid-token? valid-token [{:state "email-sent" :token valid-token :created 1}] 1) => false)

  (fact "no token given"
    (valid-token? nil valid-statuses 1) => false
    (valid-token? "" valid-statuses 1) => false)

  (fact "invalid token"
    (valid-token? (apply str (reverse valid-token)) valid-statuses 1) => false)

  (fact "can't use twice"
    (valid-token? valid-token (conj valid-statuses {:state "response-given-comments"}) 1) => false)

  (fact "email was not sent"
    (valid-token? valid-token [{:state "reminder-sent" :token valid-token :created 0}] 1) => false))
