(ns lupapalvelu.find-address-test
  (:require [lupapalvelu.find-address :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(facts "search dispatch"
  (fact
    (prerequisite (search-property-id "fi" "12345678901234") => ...result...)
    (search "12345678901234" "fi") => ...result...
    (search " 12345678901234" "fi") => ...result...
    (search "12345678901234 " "fi") => ...result...
    (search " 12345678901234 " "fi") => ...result...)
  (fact
    (prerequisite (search-property-id "fi" "00102203334444") => ...result...)
    (search "1-22-333-4444" "fi") => ...result...
    (search "001-022-0333-4444" "fi") => ...result...
    (search "00102203334444" "fi") => ...result...)
  (fact
    (prerequisite (search-poi-or-street "fi" "foo") => ...result...)
    (search "foo" "fi") => ...result...)
  (fact
    (prerequisite (search-street-with-number "fi" "foo" "1") => ...result...)
    (search "foo 1" "fi") => ...result...
    (search "foo 1," "fi") => ...result...)
  (fact
    (prerequisite (search-address "fi" "foo" "1" "bar") => ...result...)
    (search "foo 1 bar" "fi") => ...result...
    (search "foo 1, bar" "fi") => ...result...)
  (fact
    (search "" "fi") => []))
