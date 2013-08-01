(ns lupapalvelu.web-test
  (:require [lupapalvelu.core])
  (:use [lupapalvelu.web]
        [midje.sweet]
        [midje.util :only [testable-privates]]))

(testable-privates lupapalvelu.web parse ->hashbang)

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

(facts "facts about hashbang parsing"
  (->hashbang nil)               => nil
  (->hashbang "http://foo")      => nil
  (->hashbang "foo")             => "foo"
  (->hashbang "/foo")            => "foo"
  (->hashbang "!/foo")           => "foo"
  (->hashbang "#!/foo")          => "foo")
