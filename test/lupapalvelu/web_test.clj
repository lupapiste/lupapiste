(ns lupapalvelu.web-test
  (:require [lupapalvelu.core])
  (:use [lupapalvelu.web]
        [midje.sweet]
        [midje.util :only [testable-privates]]))

(testable-privates lupapalvelu.web parse session-timeout-handler)

(facts
  (parse "apikey" "apikey foo") => "foo"
  (parse "apikey" "apikey  foo ") => "foo"
  (parse "apikey" "apikey=foo") => "foo"
  (parse "apikey" "apikey= foo ") => "foo"
  (parse "apikey" "apikey") => nil
  (parse "apikey" "apikeyfoo") => nil
  (parse "apikey" "apikey ") => nil
  (parse "apikey" "baz boz") => nil
  (parse "apikey" "") => nil
  (parse "apikey" nil) => nil)

(facts
  (get-session-timeout {}) => 60000
  (get-session-timeout nil) => 60000
  (get-session-timeout {:session {:noir {:user {:session-timeout 1}}}}) => 1)

(against-background [(lupapalvelu.core/now) => 100
                     (get-session-timeout anything) => 100]
  (facts

    ; non-api and api requests without session:
    (session-timeout-handler (constantly {:foo :bar}) {:uri "/foo"})              => {:foo :bar}
    (session-timeout-handler (constantly {:foo :bar}) {:uri "/api/command/foo"})  => {:foo :bar :session {:expires 200}}
    
    ; non-api and api requests with expired session:
    (session-timeout-handler (constantly {:foo :bar}) {:uri "/foo" :session {:expires 90}})               => (contains {:status 401})
    (session-timeout-handler (constantly {:foo :bar}) {:uri "/api/command/foo" :session {:expires 90}})   => (contains {:status 401})
    
    ; non-api and api requests with valid session:
    (session-timeout-handler (constantly {:foo :bar}) {:uri "/foo"             :session {:expires 110}})  => {:foo :bar}
    (session-timeout-handler (constantly {:foo :bar}) {:uri "/api/command/foo" :session {:expires 110}})  => {:foo :bar :session {:expires 200}}
    (session-timeout-handler (constantly {:foo :bar}) {:uri "/api/query/foo"   :session {:expires 110}})  => {:foo :bar :session {:expires 200}}

    ; api requests append to request session:
    (session-timeout-handler (constantly {:foo :bar}) {:uri "/api/query/foo"   :session {:expires 110 :x :y}}) => {:foo :bar :session {:expires 200 :x :y}}

    ; api requests append to response session:
    (session-timeout-handler (constantly {:foo :bar :session {:x :z}}) {:uri "/api/query/foo" :session {:expires 110 :x :y}}) => {:foo :bar :session {:expires 200 :x :z}}))