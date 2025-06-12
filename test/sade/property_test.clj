(ns sade.property-test
  (:require [clojure.test :refer [are deftest]]
            [sade.property :as p]))

(deftest to-property-id-test
  (are [from to] (= to (p/to-property-id from))
    "245-003-0105-0006" "24500301050006"
    "245-3-105-6"       "24500301050006"
    "245-03-0105-06"    "24500301050006"
    "05-03-0105-006"    "00500301050006"))

(deftest to-human-readable-property-id-test
  (are [from to] (= to (p/to-human-readable-property-id from) )
    "24500301050006"   "245-3-105-6"
    " 00500301050006 " "5-3-105-6"
    "12345"            "12345"
    "  hello "         "hello"
    nil                nil
    "  "               ""
    "245-3-105-6"      "245-3-105-6"))

(deftest municipality-id-by-property-id-test
  (are [from to] (= to (p/municipality-id-by-property-id from))
    nil                 nil
    ""                  nil
    "245-003-0105-0006" "245"
    "05-03-0105-006"    "005"
    "24500301050006"    "245"
    "00500301050006"    "005"
    ;; Maaninka -> Kuopio
    " 47641100030051 "  "297"
    " 476-411-30-85 "   "297"
    "476-405-7-58"      "297"
    ;; Juankoski -> Kuopio
    "17441000180007"    "297"
    ;; Luvia -> Eurajoki
    "44289500002640"    "051"))

(deftest property-coercion-tests
  (are [input output] (= output (p/->db-property-id input))
    nil                  nil
    ""                   nil
    "  "                 nil
    "kissa"              nil
    "123"                nil
    "1234"               nil
    "1-2-3-4"            "00100200030004"
    "  1-2-3-4  "        "00100200030004"
    "001-002-0003-0004"  "00100200030004"
    "00100200030004"     "00100200030004"
    " 00100200030004 "   "00100200030004"
    "001002000300045678" nil))
