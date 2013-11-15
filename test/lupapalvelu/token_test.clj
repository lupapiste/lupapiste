(ns lupapalvelu.token-test
  (:require [lupapalvelu.token :refer [make-token-id]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(facts
  (make-token-id) => #"[a-zA-Z0-9]{48}"
  (make-token-id) => #"[a-zA-Z0-9]{48}"
  (make-token-id) => #"[a-zA-Z0-9]{48}"
  (make-token-id) => #"[a-zA-Z0-9]{48}"
  (make-token-id) => #"[a-zA-Z0-9]{48}"
  (= (make-token-id) (make-token-id)) => falsey
  (= (make-token-id) (make-token-id)) => falsey
  (= (make-token-id) (make-token-id)) => falsey
  (= (make-token-id) (make-token-id)) => falsey
  (= (make-token-id) (make-token-id)) => falsey)
