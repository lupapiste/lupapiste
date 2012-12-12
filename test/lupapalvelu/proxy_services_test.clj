(ns lupapalvelu.proxy-services-test
  (:use [lupapalvelu.proxy-services]
        [midje.sweet]))

(facts
  (fact (parse-address "foo")                 => {:street "foo"      :number nil   :city nil})
  (fact (parse-address "foo bar")             => {:street "foo bar"  :number nil   :city nil})
  (fact (parse-address " foo bar ")           => {:street "foo bar"  :number nil   :city nil})
  (fact (parse-address "foo bar 42")          => {:street "foo bar"  :number "42"  :city nil})
  (fact (parse-address "foo bar 42, ")        => {:street "foo bar"  :number "42"  :city nil})
  (fact (parse-address "foo bar 42, gotham")  => {:street "foo bar"  :number "42"  :city "gotham"})
  (fact (parse-address "foo bar, gotham")     => {:street "foo bar"  :number nil   :city "gotham"})
  (fact (parse-address "foo bar 42,gotham ")  => {:street "foo bar"  :number "42"  :city "gotham"}))

