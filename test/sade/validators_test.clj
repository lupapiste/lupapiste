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
    (fact "003701091602900C" (finnish-ovt? "003701091602900C") => truthy)
    (fact "003718523029101CZ" (finnish-ovt? "003718523029101CZ") => truthy)))

(facts "rakennustunnus?"
  (fact (rakennustunnus? nil) => falsey)
  (fact (rakennustunnus? "") => falsey)
  (fact (rakennustunnus? "foo") => falsey)
  (fact (rakennustunnus? "1a") => falsey)
  (fact (rakennustunnus? "1A") => falsey)
  (fact (rakennustunnus? "182736459F") => truthy)
  (fact "SYKE sample with a fixed checksum" (rakennustunnus? "100012345N") => truthy)
  (fact "VRK sample with a fixed checksum" (rakennustunnus? "1234567892") => truthy))

(facts "application-id"
  (fact "can't be"
    (fact "nil" (application-id? nil) => false)
    (fact "empty" (application-id? "") => false)
    (fact "foo" (application-id? "foo") => false)
    (fact "ObjectId" (application-id? (org.bson.types.ObjectId.)) => false)
    (fact "partial LP id" (application-id? "LP-123-2016-0001") => false)
    (fact "LP id with slash" (application-id? "LP-123-2016/00001") => false)
    (fact "LP id with backslash" (application-id? "LP-123-2016\\00001") => false))

  (facts "can be"
    (fact "ObjectId as String" (application-id? (str (org.bson.types.ObjectId.))) => true)
    (fact "LP id" (application-id? "LP-123-2016-00001") => true)))
