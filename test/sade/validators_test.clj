(ns sade.validators-test
  (:require [sade.validators :refer :all]
            [midje.sweet :refer :all]))

(facts finnish-y?
  (fact (finnish-y? nil)           => falsey)
  (fact (finnish-y? "")            => falsey)
  (fact (finnish-y? "foo")         => falsey)
  (fact (finnish-y? "2341529-4")   => falsey)
  (fact (finnish-y? "2341528-4")   => truthy))

(facts finnish-ovt?
  (fact (finnish-ovt? nil)             => falsey)
  (fact (finnish-ovt? "")              => falsey)
  (fact (finnish-ovt? "foo")           => falsey)
  (fact (finnish-ovt? "1234")          => falsey)
  (fact (finnish-ovt? "12345")         => falsey)
  (fact (finnish-ovt? "003712345")     => falsey)
  (fact (finnish-ovt? "003723415284")  => truthy)
  (fact (finnish-ovt? "0037234152841") => truthy)
  (fact (finnish-ovt? "00372341528412") => truthy)
  (fact (finnish-ovt? "003723415284123") => truthy)
  (fact (finnish-ovt? "0037234152841234") => truthy)
  (fact (finnish-ovt? "00372341528412345") => truthy)
  (fact (finnish-ovt? "003723415284123456") => falsey)
  (fact (finnish-ovt? "003701902735") => truthy)
  (fact (finnish-ovt? "003710601555") => truthy)
  (fact "invalid y"
    (finnish-ovt? "003723415294")  => falsey)
  (facts "Alphabetic suffix"
    (fact (finnish-ovt? "003718523029101CZ") => truthy)))