(ns lupapalvelu.proxy-services-test
  (:require [lupapalvelu.proxy-services :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.proxy-services
                   parse-address sanitize-request
                   safe-content-type?)

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
  (sanitize-request {:headers {"set-cookie" "cookie" "foo" "bar"}}) => {:headers {"foo" "bar"}}
  (sanitize-request {:headers {"cookie" "cookie-x=value;cookie-y=1" "foo" "bar"}}) => {:headers {"foo" "bar"}}
  (sanitize-request {:params {:a "null-char(\0);"}
                     :query-params {:WKT "POLYGON(1,\n\n2);" :term "it\u00E4inen"}
                     :form-params {:WKT "POLYGON(1,\t2);"}}) => {:params {:a "null-char();"}
                                                                 :query-params {:WKT "POLYGON(1,2);" :term "it\u00E4inen"}
                                                                 :form-params {:WKT "POLYGON(1,2);"}})
(facts "plan-infos-by-point-body: Trimble"
  (fact "Nil of Trimble not configured for the municipality"
    (plan-infos-by-point-body :trimble 11 22 "123") => nil)
  (fact "If Trimble returns placeholder map, the response is ignored."
    (plan-infos-by-point-body :trimble 11 22 "123") => nil
    (provided (sade.env/get-config) => {:trimble-kaavamaaraykset {:123 {:url "hello"}}}
              (lupapalvelu.wfs/trimble-kaavamaaraykset-by-point 11 22 "123")
              => [{:foo ""
                   :bar "  "
                   :baz nil}
                  [1 2 3]]))
  (fact "If Trimble returns good enough result"
    (plan-infos-by-point-body :trimble 11 22 "123") => [{:foo "foo"
                                                         :bar "  "
                                                         :baz nil}
                                                        [1 2 3]]
    (provided (sade.env/get-config) => {:trimble-kaavamaaraykset {:123 {:url "hello"}}}
              (lupapalvelu.wfs/trimble-kaavamaaraykset-by-point 11 22 "123")
              => [{:foo "foo"
                   :bar "  "
                   :baz nil}
                  [1 2 3]])))

(facts "Allowed map content type"
  (safe-content-type? nil) => falsey
  (safe-content-type? "") => falsey
  (safe-content-type? "   ") => falsey
  (safe-content-type? 1) => falsey
  (safe-content-type? "text/xml; foobar") => truthy
  (safe-content-type? "text/html; foobar") => falsey
  (safe-content-type? " TEXT/HTML; FOOBAR ") => falsey
  (safe-content-type? " TEXT/XML; FOOBAR ") => truthy
  (safe-content-type? "application/json") => truthy
  (safe-content-type? "application/pdf") => truthy
  (safe-content-type? "application/xml") => truthy
  (safe-content-type? "image/png") => truthy
  (safe-content-type? "image/whatever") => truthy
  (safe-content-type? "image/svg") => falsey
  (safe-content-type? "image/svg+xml") => falsey
  (safe-content-type? "text/svg") => falsey)
