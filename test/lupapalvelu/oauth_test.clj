(ns lupapalvelu.oauth-test
  (:require [lupapalvelu.oauth.api]
            [lupapalvelu.oauth.core :refer [check-user consented?]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.oauth.api
                   parse-scope)

(facts "parse-scope"
  (fact "No or empry scope"
    (parse-scope nil) => []
    (parse-scope "") => []
    (parse-scope "  ") => []
    (parse-scope ",,,,") => []
    (parse-scope " , , ,  ,  , ") => []
    (parse-scope []) => []
    (parse-scope ["  " ""  nil]) => [])
  (fact "Non-empty scope"
    (parse-scope "foo") => ["foo"]
    (parse-scope "foo,bar") => ["foo" "bar"]
    (parse-scope "  foo  ,  bar  ") => ["foo" "bar"]
    (parse-scope ", ,   foo  , , bar  ,") => ["foo" "bar"]
    (parse-scope "foo,foo,foo") => ["foo", "foo" "foo"]
    (parse-scope ["  foo  " nil "bar"]) => ["foo" "bar"]))

(facts "check-user"
  (check-user {}) => nil
  (check-user nil) => nil
  (check-user {:user {} :scope-vec ["foo"]}) => nil
  (check-user {:user {} :scope-vec ["pay"]})
  => :oauth.warning.company-pay-only
  (check-user {:user {:company {}} :scope-vec ["foo" "pay" "bar"]})
  => :oauth.warning.company-pay-only
  (check-user {:user      {:company {:id "com"}}
               :scope-vec ["foo" "pay" "bar"]}) => nil
  (check-user {:user      {:company {:id "com"}}
               :scope-vec ["foo"]}) => nil)

(facts "consented?"
  (consented? nil) => (throws AssertionError)
  (consented? {:client {:oauth {:client-id "cid"}}}) => true
  (consented? {:client    {:oauth {:client-id "cid"}}
               :user      {}
               :scope-vec ["foo"]}) => false
  (consented? {:client    {:oauth {:client-id "cid"}}
               :user      {:oauth-consent [{:client-id "bob" :scopes ["foo"]}]}
               :scope-vec ["foo"]}) => false
  (consented? {:client    {:oauth {:client-id "cid"}}
               :user      {:oauth-consent [{:client-id "cid" :scopes ["foo"]}]}
               :scope-vec ["foo"]}) => true
  (consented? {:client    {:oauth {:client-id "cid"}}
               :user      {:oauth-consent [{:client-id "cid" :scopes ["foo"]}
                                           {:client-id "bob" :scopes ["bar"]}]}
               :scope-vec ["foo" "bar"]}) => false
  (consented? {:client    {:oauth {:client-id "cid"}}
               :user      {:oauth-consent [{:client-id "cid" :scopes ["foo" "bar"]}
                                           {:client-id "bob" :scopes ["bar"]}]}
               :scope-vec ["foo" "bar"]}) => true
  (consented? {:client    {:oauth {:client-id "cid"}}
               :user      {:oauth-consent [{:client-id "cid" :scopes ["bar" "baz" "foo"]}
                                           {:client-id "bob" :scopes ["bar"]}]}
               :scope-vec ["foo" "bar"]}) => true)
