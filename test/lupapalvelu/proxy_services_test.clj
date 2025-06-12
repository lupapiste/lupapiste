(ns lupapalvelu.proxy-services-test
  (:require [lupapalvelu.proxy-services :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.proxy-services
                   parse-address sanitize-request
                   safe-content-type? secure)

(facts
  (fact (parse-address "foo")                 => ["foo"      nil   nil])
  (fact (parse-address "foo bar")             => ["foo bar"  nil   nil])
  (fact (parse-address " foo bar ")           => ["foo bar"  nil   nil])
  (fact (parse-address "foo bar 42")          => ["foo bar"  "42"  nil])
  (fact (parse-address "foo bar 42, ")        => ["foo bar"  "42"  nil])
  (fact (parse-address "foo bar 42, gotham")  => ["foo bar"  "42"  "gotham"])
  (fact (parse-address "foo bar, gotham")     => ["foo bar"  nil   "gotham"])
  (fact (parse-address "foo bar 42,gotham ")  => ["foo bar"  "42"  "gotham"])
  (fact (parse-address "tönö 42, häme")  => ["tönö" "42" "häme"]))

(fact
  (sanitize-request {:headers {"set-cookie" "cookie" "foo" "bar"}}) => {:headers {"foo" "bar"}}
  (sanitize-request {:headers {"cookie" "cookie-x=value;cookie-y=1" "foo" "bar"}}) => {:headers {"foo" "bar"}}
  (sanitize-request {:params {:a "null-char(\0);"}
                     :query-params {:WKT "POLYGON(1,\n\n2);" :term "itäinen"}
                     :form-params {:WKT "POLYGON(1,\t2);"}}) => {:params {:a "null-char();"}
                                                                 :query-params {:WKT "POLYGON(1,2);" :term "itäinen"}
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

(defn safety-check [content-type check]
  (doseq [h ["Content-Type" "CONTENT-TYPE" "content-type" "cOnTeNt-tYpE"]]
    (fact {:midje/description (str "Content-Type: " content-type)}
      (safe-content-type? (assoc-in nil [:headers h] content-type))
      => check)))

(facts "Allowed map content type"
  (safety-check nil falsey)
  (safety-check "" falsey)
  (safety-check "   " falsey)
  (safety-check 1 falsey)
  (safety-check "text/xml; foobar" truthy)
  (safety-check "text/html; foobar" falsey)
  (safety-check " TEXT/HTML; FOOBAR " falsey)
  (safety-check " TEXT/XML; FOOBAR " truthy)
  (safety-check "application/json" truthy)
  (safety-check "application/pdf" truthy)
  (safety-check "application/xml" truthy)
  (safety-check "image/png" truthy)
  (safety-check "image/whatever" truthy)
  (safety-check "image/svg" falsey)
  (safety-check "image/svg+xml" falsey)
  (safety-check "text/svg" falsey))


(defn check-secure [response]
  ((secure (constantly response)) {}))

(facts "secure"
  (check-secure {:headers {"x-foobar"     "hello"
                           "content-type" "image/png;good"}
                 :status  200
                 :body    "art"})
  => {:headers {"content-type" "image/png;good"}
      :status  200
      :body    "art"}

  (check-secure nil) => {:status 404
                         :body   "Not found"}

  (check-secure {:status  200
                 :headers {"Content-Type" "text/svg;surprise"}
                 :body    "<svg>sneaky</svg>"})
  => {:status 415
      :body   "Unsupported media type"})
