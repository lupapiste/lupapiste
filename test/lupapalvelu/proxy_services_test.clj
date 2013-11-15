(ns lupapalvelu.proxy-services-test
  (:require [lupapalvelu.proxy-services :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.proxy-services parse-address)

(facts
  (fact (parse-address "foo")                 => ["foo"      nil   nil])
  (fact (parse-address "foo bar")             => ["foo bar"  nil   nil])
  (fact (parse-address " foo bar ")           => ["foo bar"  nil   nil])
  (fact (parse-address "foo bar 42")          => ["foo bar"  "42"  nil])
  (fact (parse-address "foo bar 42, ")        => ["foo bar"  "42"  nil])
  (fact (parse-address "foo bar 42, gotham")  => ["foo bar"  "42"  "gotham"])
  (fact (parse-address "foo bar, gotham")     => ["foo bar"  nil   "gotham"])
  (fact (parse-address "foo bar 42,gotham ")  => ["foo bar"  "42"  "gotham"])
  (fact (parse-address "t\u00F6n\u00F6 42, h\u00E4me")  => ["t\u00F6n\u00F6" "42" "h\u00E4me"]))

(testable-privates lupapalvelu.proxy-services secure)

(fact
  ((secure (fn [r] {:headers {"set-cookie" "cookie" "foo" "bar"}})) {}) => {:headers {"foo" "bar"}})
