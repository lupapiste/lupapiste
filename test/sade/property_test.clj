(ns sade.property-test
  (:require [sade.property :refer :all]
            [midje.sweet :refer :all]))

(facts "to-property-id"
  (to-property-id "245-003-0105-0006") => "24500301050006"
  (to-property-id "245-3-105-6") => "24500301050006"
  (to-property-id "245-03-0105-06") => "24500301050006"
  (to-property-id "05-03-0105-006") => "00500301050006")

(facts "to-human-readable-property-id"
  (to-human-readable-property-id "24500301050006") => "245-3-105-6"
  (to-human-readable-property-id "00500301050006") => "5-3-105-6")

(facts "municipality-id-by-property-id"
  (municipality-id-by-property-id nil) => nil
  (municipality-id-by-property-id "") => nil
  (municipality-id-by-property-id "245-003-0105-0006") => "245"
  (municipality-id-by-property-id "05-03-0105-006") => "005"
  (municipality-id-by-property-id "24500301050006") => "245"
  (municipality-id-by-property-id "00500301050006") => "005"

  (fact "Maaninka -> Kuopio"
    (municipality-id-by-property-id "47641100030051") => "297"
    (municipality-id-by-property-id "476-411-30-85") => "297"
    (municipality-id-by-property-id "476-405-7-58") => "297")
  (fact "Juankoski -> Kuopio"
    (municipality-id-by-property-id "17441000180007") => "297")
  (fact "Luvia -> Eurajoki"
    (municipality-id-by-property-id "44289500002640") => "051"))
