(ns lupapalvelu.web-test
  (:require [lupapalvelu.core :refer :all]
            [lupapalvelu.web :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]])
  (:import [org.apache.commons.io IOUtils]))

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

(fact {:a 1 :b 2}
  => (and (contains {:a 1})
          (contains {:b 2})))

(facts "parse-json-body"
  (parse-json-body {:body (IOUtils/toInputStream "{\"a\": true, \"b\": 42}")
                    :content-type "application/json;charset=utf-8"})
    => (contains {:params {:a true :b 42}
                  :json {:a true :b 42}}))