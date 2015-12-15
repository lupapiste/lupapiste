(ns lupapalvelu.proxy-services-test
  (:require [lupapalvelu.proxy-services :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.proxy-services parse-address secure)

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

(fact
  ((secure (fn [r _] r) "WMS") {:headers {"set-cookie" "cookie" "x-foo" "bar"}}) => {:headers {"x-foo" "bar"}}
  ((secure identity) {:headers {"cookie" "cookie-x=value;cookie-y=1" "x-foo" "bar"}}) => {:headers {"x-foo" "bar"}}
  ((secure identity) {:params {:a "null-char(\0);"}
                      :query-params {:WKT "POLYGON(1,\n\n2);"}
                      :form-params {:WKT "POLYGON(1,\t2);"}}) => {:params {:a "null-char();"}
                                                                  :query-params {:WKT "POLYGON(1,2);"}
                                                                  :form-params {:WKT "POLYGON(1,2);"}})
