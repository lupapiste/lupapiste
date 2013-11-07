(ns lupapalvelu.find-address-test
  (:require [lupapalvelu.find-address :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.find-address pwz to-property-id)

(facts "pwz"
  (pwz 4 "1")     => "0001"
  (pwz 4 "12")    => "0012"
  (pwz 4 "123")   => "0123"
  (pwz 4 "1234")  => "1234"
  (pwz 4 "12345") => "12345")

(facts "to-property-id"
  (to-property-id "1" "2" "3" "4") => "00100200030004")

(facts "search dispatch"
  (fact
    (prerequisite (search-property-id "12345678901234") => ...result...)
    (search "12345678901234") => ...result...
    (search " 12345678901234") => ...result...
    (search "12345678901234 ") => ...result...
    (search " 12345678901234 ") => ...result...)
  (fact
    (prerequisite (search-property-id "00102203334444") => ...result...)
    (search "1-22-333-4444") => ...result...
    (search "001-022-0333-4444") => ...result...
    (search "00102203334444") => ...result...)
  (fact
    (prerequisite (search-poi-or-street "foo") => ...result...)
    (search "foo") => ...result...)
  (fact
    (prerequisite (search-street-with-number "foo" "1") => ...result...)
    (search "foo 1") => ...result...
    (search "foo 1,") => ...result...)
  (fact
    (prerequisite (search-address "foo" "1" "bar") => ...result...)
    (search "foo 1 bar") => ...result...
    (search "foo 1, bar") => ...result...)
  (fact
    (search "") => []))
