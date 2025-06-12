(ns sade.validators-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [midje.sweet :refer :all]
            [sade.property :refer :all]
            [sade.validators :refer :all]))

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
    (fact "ObjectId as String" (application-id? (str (org.bson.types.ObjectId.))) => false)
    (fact "partial LP id" (application-id? "LP-123-2016-0001") => false)
    (fact "LP id with slash" (application-id? "LP-123-2016/00001") => false)
    (fact "LP id with backslash" (application-id? "LP-123-2016\\00001") => false))

  (facts "can be"
    (fact "LP id" (application-id? "LP-123-2016-00001") => true)))

(fact "Property id tests"
  (facts "allowed"
    (fact (kiinteisto-ja-maara-alatunnus? "24500301050006") => true)
    (fact (kiinteisto-ja-maara-alatunnus? "24500301050006M604") => true))
  (facts "not allowed"
    (fact (kiinteisto-ja-maara-alatunnus? "24500301050006M604222") => false)
    (fact (kiinteisto-ja-maara-alatunnus? "2450030105000633M604") => false)
    (fact (kiinteisto-ja-maara-alatunnus? "245-3-105-633") => false)))

(facts "kuntanumero?"
  (fact (kuntanumero? nil) => falsey)
  (fact (kuntanumero? "") => falsey)
  (fact (kuntanumero? "foo") => falsey)
  (fact (kuntanumero? "1a") => falsey)
  (fact (kuntanumero? "1A") => falsey)
  (fact (kuntanumero? "0") => falsey)
  (fact (kuntanumero? "000") => truthy)
  (fact (kuntanumero? "999") => truthy)
  (fact (kuntanumero? "0000") => falsey))

(facts "hetu"
  (fact (valid-hetu? nil) => falsey)
  (fact (valid-hetu? "") => falsey)
  (fact (valid-hetu? "  ") => falsey)
  (fact (valid-hetu? "-") => falsey)
  (fact (valid-hetu? "1-1") => falsey)
  (fact (valid-hetu? "1") => falsey)
  (fact (valid-hetu? "13") => falsey)
  (fact (valid-hetu? "131") => falsey)
  (fact (valid-hetu? "1310") => falsey)
  (fact (valid-hetu? "13105") => falsey)
  (fact (valid-hetu? "131052") => falsey)
  (fact (valid-hetu? "131052-") => falsey)
  (fact (valid-hetu? "131052-3") => falsey)
  (fact (valid-hetu? "131052-30") => falsey)
  (fact (valid-hetu? "131052-308") => falsey)
  (fact (valid-hetu? "131052-308T") => truthy)
  (fact (valid-hetu? "131052-308S") => falsey)
  (fact "born in the 1800s => +"
    (fact "hetus were not assigned before 1850s"
      (valid-hetu? "010101+001C") => falsey
      (valid-hetu? "010149+001C") => falsey
      (valid-hetu? "010150+001C") => truthy)
    (valid-hetu? "131052+002X") => truthy)
  (fact "born in the 1900s => +"
    (valid-hetu? "131052-002X") => truthy)
  (fact "born in the 2000s => +"
    (valid-hetu? "131052A002X") => truthy)
  (facts "Supported separators"
    (doseq [sep "-+ABCDEFYXWVU"]
      (fact {:midje/description (str sep)}
        (valid-hetu? (str "131052" sep "002X")) => true)))
  (fact "Bad separator"
    (valid-hetu? "131052M002X") => false)
  (facts "Leap years vs. separators. 2000 is a leap year, 1900 and 1800 are not."
    (valid-hetu? "290200A1239") => true
    (valid-hetu? "290200F1239") => true
    (valid-hetu? "290200-1239") => false
    (valid-hetu? "290200Y1239") => false
    (valid-hetu? "290200+1239") => false))

(defspec hetu-validator-doesnt-throw-test
  (prop/for-all [s gen/string-ascii]
    (boolean? (valid-hetu? s))))
