(ns lupapalvelu.security-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.security :refer :all]))

(fact random-password
  (random-password) => #"[a-zA-Z0-9]{40}")

(fact check-password
  (check-password nil nil) => falsey
  (check-password nil "") => falsey
  (check-password "" nil) => falsey
  (check-password "" "") => falsey
  (check-password "" (get-hash "")) => truthy

  (check-password "foobar" (get-hash "foobar" (dispense-salt))) => truthy
  (check-password "foobar" (get-hash "foobaz" (dispense-salt))) => falsey

  (check-password "foobar" (get-hash "foobar")) => truthy
  (check-password "foobar" (get-hash "foobaz")) => falsey)
