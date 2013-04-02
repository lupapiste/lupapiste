(ns lupapalvelu.token-test
  (:use [midje.sweet]
        [midje.util :only [testable-privates]]
        [lupapalvelu.token]))

(testable-privates lupapalvelu.token make-token-id)

(facts
  (fact (make-token-id) => #"[a-zA-Z0-9]{48}")
  (fact (make-token-id) => #"[a-zA-Z0-9]{48}")
  (fact (make-token-id) => #"[a-zA-Z0-9]{48}")
  (fact (make-token-id) => #"[a-zA-Z0-9]{48}")
  (fact (= (make-token-id) (make-token-id)) => falsey)
  (fact (= (make-token-id) (make-token-id)) => falsey)
  (fact (= (make-token-id) (make-token-id)) => falsey)
  (fact (= (make-token-id) (make-token-id)) => falsey))
