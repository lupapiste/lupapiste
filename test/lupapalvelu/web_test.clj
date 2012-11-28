(ns lupapalvelu.web-test
  (:use lupapalvelu.web
        midje.sweet))

(def parse #'lupapalvelu.web/parse)

(facts
  (fact (parse "apikey" "apikey foo") => "foo")
  (fact (parse "apikey" "apikey  foo ") => "foo")
  (fact (parse "apikey" "apikey=foo") => "foo")
  (fact (parse "apikey" "apikey= foo ") => "foo")
  (fact (parse "apikey" "apikey") => nil)
  (fact (parse "apikey" "baz boz") => nil)
  (fact (parse "apikey" "") => nil)
  (fact (parse "apikey" nil) => nil))