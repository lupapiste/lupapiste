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
  (fact (parse-address "foo bar 42,gotham ")  => {:street "foo bar"  :number "42"  :city "gotham"})
  (fact (parse-address "t\u00F6n\u00F6 42, h\u00E4me")  => {:street "t\u00F6n\u00F6"  :number "42"  :city "h\u00E4me"}))

