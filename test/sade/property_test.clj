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
