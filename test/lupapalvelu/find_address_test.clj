(ns lupapalvelu.find-address-test
  (:require [lupapalvelu.find-address :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

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
