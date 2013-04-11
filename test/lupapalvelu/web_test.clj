(ns lupapalvelu.web-test
  (:use [lupapalvelu.web]
        [midje.sweet]
        [midje.util :only [testable-privates]]))

(testable-privates lupapalvelu.web parse)

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

