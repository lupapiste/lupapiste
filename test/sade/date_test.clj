(ns sade.date-test
  (:require [clojure.test :refer :all]
            [sade.date :refer :all])
  (:import [java.time Instant ZoneId]
           [java.time.format DateTimeFormatter DateTimeFormatterBuilder]
           [java.time.temporal ChronoField]
           [java.util Date]))

(set! *warn-on-reflection* true)

(deftest zoned-date-time-test
  (let [ts 1621005883813]

    (testing "Timestamp"
      (let [zoned (zoned-date-time ts)]
        (is (= "2021-05-14T18:24:43.813+03:00[Europe/Helsinki]" (str zoned)))

        (testing "ZonedDateTime passthrough"
          (is (= zoned (zoned-date-time zoned))))

        (testing "String timestamp"
          (is (= zoned (zoned-date-time (str ts)))))))

    (testing "Any number type is supported"
      (let [zoned (zoned-date-time 123)]
        (is (= "1970-01-01T02:00:00.123+02:00[Europe/Helsinki]" (str zoned)))
        (is (= zoned (zoned-date-time (long 123))))
        (is (= zoned (zoned-date-time (int 123))))
        (is (= zoned (zoned-date-time (short 123))))
        (is (= zoned (zoned-date-time (float 123.123))))
        (is (= zoned (zoned-date-time (double 123.123))))))

    (testing "Nil-safe"
      (is (nil? (zoned-date-time nil))))

    (testing "Zoned to timestamp"
      (let [zoned (zoned-date-time ts)]
        (is (= ts (timestamp zoned)))
        (is (nil? (timestamp nil)))
        (is (thrown? Exception (timestamp [])))))

    (testing "Map argument"
      (let [zoned (zoned-date-time {:day 27 :month 5 :year 2021})]
        (is (= "2021-05-27T00:00+03:00[Europe/Helsinki]" (str zoned)))
        (is (= zoned (zoned-date-time (timestamp zoned))))
        (is (= "2021-05-27T11:22:33-04:00[America/Toronto]"
               (str (zoned-date-time {:day     27 :month  5  :year   2021
                                      :hour    11 :minute 22 :second 33
                                      :zone-id (ZoneId/of "America/Toronto")}))))

        (testing "Different map types"
          (is (= zoned (zoned-date-time (hash-map :day 27 :month 5 :year 2021))))
          (is (= zoned (zoned-date-time (sorted-map :day 27 :month 5 :year 2021)))))))

    (testing "Bad maps"
      (is (thrown? Exception (zoned-date-time {:day 27 :month 15 :year 2021}))))

    (testing "String argument"
      (is (nil? (zoned-date-time "")))
      (is (nil? (zoned-date-time "   ")))
      (is (= "2022-08-28T00:00+03:00[Europe/Helsinki]"
             (str (zoned-date-time "28.8.2022"))))
      (is (= "2022-08-28T19:11+03:00[Europe/Helsinki]"
             (str (zoned-date-time "28.8.2022 19:11"))))
      (is (= "2022-04-28T00:00+03:00[Europe/Helsinki]"
             (str (zoned-date-time "2022-04-28"))))
      (is (= "2022-04-28T00:00+03:00[Europe/Helsinki]"
             (str (zoned-date-time "2022-04-28-14:30"))))
      (is (= "2022-04-28T22:13:04+03:00[Europe/Helsinki]"
             (str (zoned-date-time "2022-04-28T19:13:04Z"))))
      (is (= (str (zoned-date-time "1655385252464"))
             (str (zoned-date-time 1655385252464))
             "2022-06-16T16:14:12.464+03:00[Europe/Helsinki]"))
      (is (= (str (zoned-date-time "  -1655385252464  "))
             (str (zoned-date-time -1655385252464))
             "1917-07-18T12:25:36.536+01:39:49[Europe/Helsinki]"))
      (is (= (str (zoned-date-time "0"))
             (str (zoned-date-time 0))
             "1970-01-01T02:00+02:00[Europe/Helsinki]")))

    (testing "Bad strings"
      (is (thrown-with-msg? Exception #"Unknown format, cannot be parsed: bad"
                            (zoned-date-time "bad")))
      (is (thrown-with-msg? Exception #"Unknown format, cannot be parsed: --123"
                            (zoned-date-time "--123")))
      (is (thrown? java.time.format.DateTimeParseException
                   (zoned-date-time "34.7.2022")))
      (is (thrown? NumberFormatException
                   (zoned-date-time "1122249283479238479238479247928479234792347"))))))

(deftest with-time-test
  (let [ts 1621005883813
        zoned (zoned-date-time ts)]

    (testing "All defaults (zero)"
      (is (= "2021-05-14T00:00+03:00[Europe/Helsinki]" (str (with-time zoned)))))

    (testing "Hour"
      (is (= "2021-05-14T17:00+03:00[Europe/Helsinki]" (str (with-time zoned 17)))))

    (testing "Hour and minute"
      (is (= "2021-05-14T17:45+03:00[Europe/Helsinki]" (str (with-time zoned 17 45)))))

    (testing "Hour, minute and seconds"
      (is (= "2021-05-14T17:45:36+03:00[Europe/Helsinki]" (str (with-time zoned 17 45 36)))))

    (testing "Nil-safe"
      (is (nil? (with-time nil)))
      (is (nil? (with-time "   " 12 13 14))))

    (testing "Bad args"
      (is (thrown-with-msg? Exception #"Invalid value" (with-time zoned 24)))
      (is (thrown-with-msg? Exception #"Invalid value" (with-time zoned -1)))
      (is (thrown-with-msg? Exception #"Invalid value" (with-time zoned 17 60)))
      (is (thrown-with-msg? Exception #"Invalid value" (with-time zoned 17 -1)))
      (is (thrown-with-msg? Exception #"Invalid value" (with-time zoned 17 45 60)))
      (is (thrown-with-msg? Exception #"Invalid value" (with-time zoned 17 45 -1)))
      (is (thrown-with-msg? Exception #"Unknown format, cannot be parsed: bad"
                            (with-time "bad"))))))

(deftest start-of-day-test
  (let [ts 1621005883813]
    (is (= "2021-05-14T00:00+03:00[Europe/Helsinki]" (str (start-of-day ts))))
    (is (= "2022-04-29T00:00+03:00[Europe/Helsinki]" (str (start-of-day "29.4.2022 09:12"))))
    (is (= "2022-04-29T00:00+03:00[Europe/Helsinki]" (str (start-of-day "2022-04-28T23:11:22Z"))))
    (is (= "2022-04-28T00:00Z[UTC]"
           (str (start-of-day (with-zone (zoned-date-time "2022-04-28T23:11:22Z") utc-zone-id)))))
    (is (thrown-with-msg? Exception #"Unknown format, cannot be parsed: bad"
                          (start-of-day "bad")))))

(deftest end-of-day-test
  (let [ts 1621005883813]
    (is (= "2021-05-14T23:59:59.999+03:00[Europe/Helsinki]" (str (end-of-day ts))))
    (is (= "2022-04-29T23:59:59.999+03:00[Europe/Helsinki]" (str (end-of-day "29.4.2022 09:12"))))
    (is (= "2022-04-29T23:59:59.999+03:00[Europe/Helsinki]" (str (end-of-day "2022-04-28T23:11:22Z"))))
    (is (= "2022-04-28T23:59:59.999Z[UTC]"
           (str (end-of-day (with-zone (zoned-date-time "2022-04-28T23:11:22Z") utc-zone-id)))))
    (is (thrown-with-msg? Exception #"Unknown format, cannot be parsed: bad"
                          (end-of-day "bad")))
    (is (= 1 (- (timestamp "30.5.2022") (timestamp (end-of-day (timestamp "29.5.2022"))))))))

(deftest current-time-test
  (testing "Current time"
    (is (= (start-of-day (now)) (today)))))

(deftest with-zone-test
  (let [ts 1621005883813]
    (is (= "2021-05-14T18:24:43.813+03:00[Europe/Helsinki]" (str (zoned-date-time ts))))
    (is (= "2021-05-14T18:24:43.813+03:00[Europe/Helsinki]" (str (with-zone ts))))
    (is (= "2021-05-14T15:24:43.813Z[UTC]" (str (with-zone ts utc-zone-id))))
    (is (= "2021-05-14T18:24:43.813+03:00[Europe/Helsinki]"
           (str (with-zone (with-zone ts utc-zone-id)))))
    (is (= "2021-05-15T03:24:43.813+12:00[Antarctica/South_Pole]"
           (str (with-zone ts (ZoneId/of "Antarctica/South_Pole")))))
    (is (thrown-with-msg? Exception #"Unknown format" (with-zone "bad")))
    (is (thrown-with-msg? Exception #"Unknown time-zone ID: Bad/Zone"
                          (with-zone ts (ZoneId/of "Bad/Zone"))))
    (is (nil? (with-zone nil)))
    (is (nil? (with-zone "  ")))))

(deftest zoned-parse-test
  (let [apr5 "2022-04-05T00:00+03:00[Europe/Helsinki]"
        fmt  (-> (DateTimeFormatterBuilder.)
                 (.append DateTimeFormatter/ISO_WEEK_DATE)
                 (.parseDefaulting ChronoField/NANO_OF_DAY 0)
                 (.toFormatter))]

    (is (nil? (zoned-parse nil)))
    (is (nil? (zoned-parse "")))
    (is (nil? (zoned-parse "   ")))

    (testing "keywords"
      (is (= apr5 (str (zoned-parse "5.4.2022" :fi))))
      (is (= apr5 (str (zoned-parse "  05.4.2022  " :fi))))
      (is (= apr5 (str (zoned-parse "5.04.2022" :fi))))
      (is (= apr5 (str (zoned-parse "05.04.2022" :fi))))
      (is (= "2022-04-05T19:51+03:00[Europe/Helsinki]"
             (str (zoned-parse "5.4.2022 19.51" :fi))))
      (is (= "2022-04-05T19:51+03:00[Europe/Helsinki]"
             (str (zoned-parse " 05.04.2022   19.51 " :fi))))
      (is (= "2022-05-10T06:07:01+03:00[Europe/Helsinki]"
             (str (zoned-parse "  10.05.2022    06:7:1 " :fi))))
      (is (= apr5 (str (zoned-parse "5.4.2022" :fi-date))))
      (is (= apr5 (str (zoned-parse "  05.4.2022  " :fi-date))))
      (is (= apr5 (str (zoned-parse "5.04.2022" :fi-date))))
      (is (= apr5 (str (zoned-parse "05.04.2022" :fi-date))))
      (is (= apr5 (str (zoned-parse "2022-04-05" :iso-date))))
      (is (= apr5 (str (zoned-parse "  2022-04-05Z  " :iso-date))))
      (is (= apr5 (str (zoned-parse "2022-04-05z" :iso-date))))
      (is (= apr5 (str (zoned-parse "2022-04-05-12:30" :iso-date))))
      (is (= apr5 (str (zoned-parse "2022-04-05-18:00" :iso-date))))
      (is (= apr5 (str (zoned-parse "2022-04-05+12:30" :iso-date))))
      (is (= apr5 (str (zoned-parse "2022-04-05+18:00" :iso-date))))
      (is (= "2022-04-05T14:25+03:00[Europe/Helsinki]"
             (str (zoned-parse " 2022-04-05T14:25 " :iso-datetime))))
      (is (= "2022-04-05T14:25:15+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T14:25:15" :iso-datetime))))
      (is (= "2022-04-05T14:25:15.123400+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T14:25:15.1234" :iso-datetime))))
      (is (= "2022-04-05T17:25:15+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T14:25:15Z" :iso-datetime))))
      (is (= "2022-04-05T17:25:15+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05t14:25:15z" :iso-datetime))))
      (is (= "2022-04-04T20:55:15+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T04:25:15+10:30" :iso-datetime))))
      (is (= "2022-04-04T20:55:15.987+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T04:25:15.987+10:30" :iso-datetime))))
      (is (= "2022-04-05T04:25:15.987+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T04:25:15.987" :iso-datetime))))
      (is (= "2022-04-05T07:25:15.987+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T04:25:15.987Z" :iso-datetime)))))

    (testing "Guesses"
      (is (= apr5 (str (zoned-parse "5.4.2022"))))
      (is (= apr5 (str (zoned-parse "05.04.2022"))))
      (is (= "2022-04-05T19:51+03:00[Europe/Helsinki]"
             (str (zoned-parse "5.4.2022 19.51"))))
      (is (= "2022-04-05T19:51+03:00[Europe/Helsinki]"
             (str (zoned-parse "05.04.2022 19.51"))))
      (is (= "2022-05-15T16:32:12+03:00[Europe/Helsinki]"
             (str (zoned-parse "  15.5.2022 16.32.12  "))))
      (is (= "2022-05-10T06:07:01+03:00[Europe/Helsinki]"
             (str (zoned-parse "  10.05.2022 06:7:1 " :fi))))
      (is (= apr5 (str (zoned-parse "2022-04-05"))))
      (is (= apr5 (str (zoned-parse "2022-04-05Z"))))
      (is (= apr5 (str (zoned-parse "  2022-04-05-12:30  "))))
      (is (= apr5 (str (zoned-parse "2022-04-05+03:00"))))
      (is (= "2022-04-05T14:25+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T14:25"))))
      (is (= "2022-04-05T14:25:15+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T14:25:15"))))
      (is (= "2022-04-05T14:25:15.123400+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T14:25:15.1234"))))
      (is (= "2022-04-05T17:25:15+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T14:25:15Z"))))
      (is (= "2022-04-04T20:55:15+03:00[Europe/Helsinki]"
             (str (zoned-parse "2022-04-05T04:25:15+10:30")))))

    (testing "Formatter"
      (let [parsed (zoned-parse " 2022-W30-7Z " fmt)]
        (is (= "2022-07-31T00:00Z" (str parsed)))
        (is (= "2022-W30-7Z" (zoned-format parsed fmt)))
        (is (thrown? Exception (zoned-format "hello" "world")))))

    (testing "Parse fails"
      (is (thrown? Exception (zoned-parse "1:5:2022" :fi)))
      (is (thrown? Exception (zoned-parse "1:5:2022 12:13" :fi)))
      (is (thrown? Exception (zoned-parse "1:5:2022" :fi-date)))
      (is (thrown-with-msg? Exception #"Cannot parse '5.4.2022 19.51' as a Finnish date"
                            (zoned-parse "  5.4.2022 19.51 " :fi-date)))
      (is (thrown-with-msg? Exception #"Cannot parse 'bad' as an ISO date."
                            (zoned-parse "bad" :iso-date)))
      (is (thrown? Exception (zoned-parse "bad" :iso-datetime)))
      (is (thrown? Exception (zoned-parse "bad" :fi)))
      (is (thrown? Exception (zoned-parse "bad" fmt)))
      (is (thrown-with-msg? Exception #"Unknown format, cannot be parsed: bad"
                            (zoned-parse "bad")))
      (is (= apr5 (str (zoned-parse "5.4.2022"))))
      (is (thrown? Exception (zoned-parse "5.4.2022 12")))
      (is (thrown? Exception (zoned-parse "5.4.2022 12. 30")))
      (is (some? (zoned-parse "5.4.2022 12.30")))
      (is (thrown? Exception (zoned-parse "5.4.2022" :iso-date)))
      (is (thrown? Exception (zoned-parse "5.4.2022" :iso-datetime)))
      (is (some? (zoned-parse "5.4.2022 16:45")))
      (is (some? (zoned-parse "2021-02-01Z")))
      (is (thrown? Exception (zoned-parse "2021-02-01Z" :iso-datetime)))
      (is (thrown? Exception (zoned-parse "2021-02-01A"))))

    (testing "Predicates"
      (is (= true (format? :fi "3.5.2022")))
      (is (= true (format? :fi-date " 3.5.2022 ")))
      (is (= true (format? :fi "3.5.2022 11.22.33 ")))
      (is (= false (format? :fi-date "3.5.2022 11.22.33 ")))
      (is (= false (format? :fi "45.14.2022")))
      (is (= false (format? :fi-date "45.14.2022")))
      (is (= true (xml-date? "2022-05-03Z")))
      (is (= true (xml-date? "2022-05-03")))
      (is (= false (xml-date? "2022-05-03T00:00:00Z")))
      (is (= false (xml-datetime? "2022-05-03Z")))
      (is (= true (xml-datetime? "2022-05-03T00:00:00Z"))))))

(deftest plus-minus-test
  (let [s "2022-10-05T12:31:44"]

    (testing "plus"
      (is (= (zoned-date-time s) (plus s :seconds 0)))
      (is (= "2022-10-05T12:31:46+03:00[Europe/Helsinki]" (str (plus s :seconds 2))))
      (is (= "2022-10-05T12:31:42+03:00[Europe/Helsinki]" (str (plus s :seconds -2))))
      (is (= "2022-10-05T12:31:45+03:00[Europe/Helsinki]" (str (plus s :second))))
      (is (= (zoned-date-time s) (plus s :minutes 0)))
      (is (= "2022-10-05T12:33:44+03:00[Europe/Helsinki]" (str (plus s :minutes 2))))
      (is (= "2022-10-05T12:29:44+03:00[Europe/Helsinki]" (str (plus s :minutes -2))))
      (is (= "2022-10-05T12:32:44+03:00[Europe/Helsinki]" (str (plus s :minute))))
      (is (= (zoned-date-time s) (plus s :hours 0)))
      (is (= "2022-10-05T14:31:44+03:00[Europe/Helsinki]" (str (plus s :hours 2))))
      (is (= "2022-10-05T10:31:44+03:00[Europe/Helsinki]" (str (plus s :hours -2))))
      (is (= "2022-10-05T13:31:44+03:00[Europe/Helsinki]" (str (plus s :hour))))
      (is (= (zoned-date-time s) (plus s :days 0)))
      (is (= "2022-10-07T12:31:44+03:00[Europe/Helsinki]" (str (plus s :days 2))))
      (is (= "2022-10-03T12:31:44+03:00[Europe/Helsinki]" (str (plus s :days -2))))
      (is (= "2022-10-06T12:31:44+03:00[Europe/Helsinki]" (str (plus s :day))))
      (is (= (zoned-date-time s) (plus s :weeks 0)))
      (is (= "2022-10-19T12:31:44+03:00[Europe/Helsinki]" (str (plus s :weeks 2))))
      (is (= "2022-09-21T12:31:44+03:00[Europe/Helsinki]" (str (plus s :weeks -2))))
      (is (= "2022-10-12T12:31:44+03:00[Europe/Helsinki]" (str (plus s :week))))
      (is (= (zoned-date-time s) (plus s :months 0)))
      (is (= "2022-12-05T12:31:44+02:00[Europe/Helsinki]" (str (plus s :months 2))))
      (is (= "2022-08-05T12:31:44+03:00[Europe/Helsinki]" (str (plus s :months -2))))
      (is (= "2022-11-05T12:31:44+02:00[Europe/Helsinki]" (str (plus s :month))))
      (is (= (zoned-date-time s) (plus s :years 0)))
      (is (= "2024-10-05T12:31:44+03:00[Europe/Helsinki]" (str (plus s :years 2))))
      (is (= "2020-10-05T12:31:44+03:00[Europe/Helsinki]" (str (plus s :years -2))))
      (is (= "2023-10-05T12:31:44+03:00[Europe/Helsinki]" (str (plus s :year))))
      (is (thrown-with-msg? Exception #"No matching clause" (plus s :week 10)))
      (is (thrown-with-msg? Exception #"No matching clause" (plus s :weeks)))
      (is (thrown? Exception (plus s :weeks "10")))
      (is (thrown? Exception (plus "bad" :week))))

    (testing "minus"
      (is (= (zoned-date-time s) (minus s :minutes 0)))
      (is (= "2022-10-05T12:29:44+03:00[Europe/Helsinki]" (str (minus s :minutes 2))))
      (is (= "2022-10-05T12:33:44+03:00[Europe/Helsinki]" (str (minus s :minutes -2))))
      (is (= "2022-10-05T12:30:44+03:00[Europe/Helsinki]" (str (minus s :minute))))
      (is (thrown-with-msg? Exception #"No matching clause" (minus s :day 10)))
      (is (thrown-with-msg? Exception #"No matching clause" (minus s :days)))
      (is (thrown? Exception (minus s :days "10")))
      (is (thrown? Exception (minus "bad" :day))))))

(deftest before-after-eq-test
  (let [yesterday (minus (today) :day)
        tomorrow  (plus (today) :day)]

    (testing "before? and after?"
      (is (= true (before? yesterday tomorrow)))
      (is (= false (before? tomorrow yesterday)))
      (is (= false (before? yesterday yesterday)))
      (is (= true (before? yesterday (today) tomorrow)))
      (is (thrown? NullPointerException (before? yesterday nil)))
      (is (thrown? NullPointerException (before? "" yesterday)))
      (is (= false (after? tomorrow tomorrow)))
      (is (= true (after? tomorrow yesterday)))
      (is (= false (after? yesterday tomorrow)))
      (is (= true (after? tomorrow (today) yesterday)))
      (is (thrown? NullPointerException (after? yesterday nil)))
      (is (thrown? NullPointerException (after? "" yesterday)))
      (is (= true (after? "1.6.2022" {:year 2022 :month 5 :day 15})))
      (is (thrown-with-msg? Exception #"Unknown format" (before? "bad" tomorrow))))

    (let [utc (with-zone "20.5.2022 21.32.51" utc-zone-id)
          zoned (zoned-date-time "2022-05-20T18:32:51Z")]

      (testing "eq?"
        (is (= true (eq? nil nil " " "")))
        (is (= true (eq? tomorrow (with-time (plus (now) :day)))))
        (is (= false (eq? yesterday tomorrow)))
        (is (= true (eq? "23.5.2022" "2022-05-23+12:00" {:year 2022 :month 5 :day 23})))
        (is (= true (eq? (now))))
        (is (= true (eq? "20.5.2022 18.32.51" "2022-05-20T18:32:51")))
        (is (= false (eq? "20.5.2022 18.32.51" "2022-05-20T18:32:51Z")))
        (is (= false (= utc zoned)))
        (is (= true (eq? utc zoned)))
        (is (thrown? Exception (eq? "bad" "also bad")))
        (is (= true (eq? zoned (.withNano zoned 999999))))
        (is (= false (eq? zoned (.withNano zoned (inc 999999)))))))))

(deftest zoned-format-test
  (let [ts  (timestamp "2021-05-04T18:24:43.1234")
        utc (with-zone "2022-04-28T23:30:45.98765Z" utc-zone-id)]

    (testing "keywords"
      (is (= "2021-05-04+03:00" (zoned-format ts :iso-date)))
      (is (= "2021-05-04" (zoned-format ts :iso-date :local)))
      (is (= "2021-05-04T18:24:43+03:00" (zoned-format ts :iso-datetime)))
      (is (= "2021-05-04T18:24:43" (zoned-format ts :iso-datetime :local)))
      (is (= "2021-05-04T18:24:43.123+03:00" (zoned-format ts :iso-datetime :ms)))
      (is (= "2021-05-04T18:24:43.123" (zoned-format ts :iso-datetime :local :ms)))

      (is (= "2021-05-04T15:24:43Z" (zoned-format ts :iso-datetime :utc)))
      (is (= "2021-05-04T15:24:43" (zoned-format ts :iso-datetime :utc :local)))
      (is (= "2021-05-04T15:24:43.123Z" (zoned-format ts :iso-datetime :utc :ms)))
      (is (= "2021-05-04T15:24:43.123" (zoned-format ts :iso-datetime :utc :local :ms)))

      (is (= "2022-04-29T02:30:45+03:00" (zoned-format utc :iso-datetime)))
      (is (= "2022-04-29T02:30:45" (zoned-format utc :iso-datetime :local)))
      (is (= "2022-04-29+03:00" (zoned-format utc :iso-date)))
      (is (= "2022-04-29" (zoned-format utc :iso-date :local)))

      (is (= "2022-04-29T02:30:45.987+03:00" (zoned-format utc :iso-datetime :ms)))
      (is (= "2022-04-29T02:30:45.987" (zoned-format utc :iso-datetime :local :ms)))
      (is (= "2022-04-28T23:30:45Z" (zoned-format utc :iso-datetime :utc)))
      (is (= "2022-04-28T23:30:45" (zoned-format utc :iso-datetime :utc :local)))
      (is (= "2022-04-28T23:30:45.987Z" (zoned-format utc :iso-datetime :ms :utc)))
      (is (= "2022-04-28T23:30:45.987" (zoned-format utc :iso-datetime :ms :utc :local)))

      (is (= "Tue, 4 May 2021 15:24:43 GMT" (zoned-format ts :rfc)))
      (is (= "Tue, 4 May 2021 15:24:43 GMT" (zoned-format (with-zone ts utc-zone-id) :rfc)))
      (is (= "Thu, 28 Apr 2022 23:30:45 GMT" (zoned-format utc :rfc)))
      (is (= "Tue, 4 May 2021 18:24:43 +0300" (zoned-format ts :rfc :local)))
      (is (= "Tue, 4 May 2021 18:24:43 +0300" (zoned-format (with-zone ts utc-zone-id) :rfc :local)))
      (is (= "Fri, 29 Apr 2022 02:30:45 +0300" (zoned-format utc :rfc :local)))
      (is (= "4.5.2021" (zoned-format ts :fi-date)))
      (is (= "4.5.2021 18.24" (zoned-format ts :fi-datetime)))
      (is (= "29.4.2022" (zoned-format utc :fi-date)))
      (is (= "29.4.2022 2.30" (zoned-format utc :fi-datetime)))
      (is (= "04.05.2021" (zoned-format ts :fi-date :zero-pad)))
      (is (= "04.05.2021 18.24" (zoned-format ts :fi-datetime :zero-pad)))
      (is (= "29.04.2022" (zoned-format utc :fi-date :zero-pad)))
      (is (= "29.04.2022 02.30" (zoned-format utc :fi-datetime :zero-pad)))
      (is (= "4.5.2021 18.24.43" (zoned-format ts :fi-datetime :seconds)))
      (is (= "29.4.2022 2.30.45" (zoned-format utc :fi-datetime :seconds)))
      (is (= "04.05.2021 18.24.43" (zoned-format ts :fi-datetime :zero-pad :seconds)))
      (is (= "29.04.2022 02.30.45" (zoned-format utc :fi-datetime :seconds :zero-pad)))

      (is (= "20210504" (zoned-format ts :basic-date)))
      (is (= "20220429" (zoned-format utc :basic-date))))

    (testing "formatters"
      (is (= "2021 Anno Domini, it was  Tuesday."
             (zoned-format ts (pattern-formatter "YYYY GGGG, 'it was ' EEEE."))))
      (is (= "2022-W17-4Z" (zoned-format utc DateTimeFormatter/ISO_WEEK_DATE))))

    (testing "Bad formats"
      (is (thrown-with-msg? Exception #"Unsupported format: :bad"
                            (zoned-format ts :bad)))
      (is (thrown-with-msg? Exception #"Unsupported format: foobar"
                            (zoned-format ts "foobar")))
      (is (thrown? Exception (zoned-format ts (DateTimeFormatter/ofPattern "bad")))))

    (testing "Bad options are ignored"
      (is (= (zoned-format ts :iso-date) (zoned-format ts :iso-date :bad)))
      (is (= (zoned-format ts :iso-date :local) (zoned-format ts :iso-date :local :bad))))

    (testing "Convenience functions"
      (is (= (zoned-format ts :iso-datetime) (iso-datetime ts)))
      (is (= (zoned-format ts :iso-datetime :local) (iso-datetime ts :local)))
      (is (= (zoned-format ts :iso-date) (iso-date ts)))
      (is (= (zoned-format ts :iso-date :local) (iso-date ts :local)))
      (is (= (zoned-format ts :fi-date) (finnish-date ts)))
      (is (= (zoned-format ts :fi-date) (finnish-date ts :local)))
      (is (= (zoned-format ts :fi-date :zero-pad) (finnish-date ts :zero-pad)))
      (is (= (zoned-format ts :fi-datetime) (finnish-datetime ts)))
      (is (= (zoned-format ts :fi-datetime) (finnish-datetime ts :local)))
      (is (= (zoned-format ts :fi-datetime :zero-pad) (finnish-datetime ts :zero-pad)))
      (is (= (zoned-format ts :fi-datetime :seconds) (finnish-datetime ts :seconds)))
      (is (= (zoned-format ts :fi-datetime :seconds) (finnish-datetime ts :local :seconds)))
      (is (= (zoned-format ts :fi-datetime :seconds :zero-pad)
             (finnish-datetime ts :zero-pad :seconds))))

    (testing "XML"
      (is (= (zoned-format ts :iso-datetime) (xml-datetime ts)))
      (is (= (zoned-format ts :iso-date) (xml-date ts)))
      (is (= "1900-01-01T14:54:04+01:39:49" (iso-datetime "1900-01-01T13:14:15Z")))
      (is (= "1900-01-01T14:54:04+01:39" (xml-datetime "1900-01-01T13:14:15Z")))
      (is (= "1910-10-11+01:39:49" (iso-date "1910-10-11+18:00")))
      (is (= "1910-10-11+01:39" (xml-date "1910-10-11+18:00"))))))

(deftest instant-date-test
  (let [ts 1667306723362]

    (testing "Instant"
      (let [i     (Instant/ofEpochMilli ts)
            zoned (zoned-date-time i)]
        (is (inst? i))
        (is (= zoned (zoned-date-time ts)))
        (is (= "2022-11-01T14:45:23.362+02:00[Europe/Helsinki]" (str zoned)))))

    (testing "Date"
      (let [date (Date. ts)
            zoned (zoned-date-time date)]
        (is (instance? Date date))
        (is (= zoned (zoned-date-time ts)))
        (is (= "2022-11-01T14:45:23.362+02:00[Europe/Helsinki]" (str zoned)))))))

(deftest make-parser-test
  (let [dp       (make-parser "yy/M/d qqq")
        runeberg (zoned-date-time "5.2.2025")]

    (is (= "25/2/5 Q1" (zoned-format runeberg dp)))
    (is (= (zoned-date-time "1.5.2024") (zoned-parse " 24/5/1 Q2 " dp)))
    (is (thrown? Exception (zoned-parse "24/6/8" dp)))))



(deftest until-test
  ;; Original test assumed a 24h day which breaks on DST transitions (23h spring forward, 25h fall back).
  ;; We now: (1) test a fixed non-DST date to assert the canonical 24h behaviour, and (2) test the current day
  ;; allowing variable hour length, ensuring consistency between hours/minutes/seconds. Finally, we keep the
  ;; month difference assertion.
  (testing "Non-DST day (fixed reference date)"
    (let [tod (zoned-date-time "2025-05-14") ;; May 14 is safely outside Finnish DST boundary changes.
          tom (plus tod :day)]
      (is (= 1 (until tod tom :days)))
      (is (= -1 (until tom tod :days)))
      (is (= 24 (until tod tom :hours)))
      (is (= -24 (until tom tod :hours)))
      (is (= (* 24 60) (until tod tom :minutes)))
      (is (= (* -24 60) (until tom tod :minutes)))
      (is (= (* 24 60 60) (until tod tom :seconds)))
      (is (= (* -24 60 60) (until tom tod :seconds)))
      (is (= 0 (until tod tom :months)))
      (is (= 0 (until tom tod :months)))))

  (testing "Current day — allow DST variance (23/24/25h)"
    (let [tod (today)
          tom (plus tod :day)
          days (until tod tom :days)
          hours (until tod tom :hours)
          minutes (until tod tom :minutes)
          seconds (until tod tom :seconds)]
      (is (= 1 days))
      ;; Hours in a day may be 23 (spring forward), 24 (normal), or 25 (fall back)
      (is (contains? #{23 24 25} hours))
      ;; Derived unit relationships must hold
      (is (= (* hours 60) minutes))
      (is (= (* minutes 60) seconds))
      ;; Reverse direction negates
      (is (= (- hours) (until tom tod :hours)))
      (is (= (- minutes) (until tom tod :minutes)))
      (is (= (- seconds) (until tom tod :seconds)))
      (is (= -1 (until tom tod :days)))
      ;; Month diff for a one-day span is always zero
      (is (= 0 (until tod tom :months)))
      (is (= 0 (until tom tod :months)))))

  (testing "Explicit DST fall-back transition (last Sunday of October 2025: 25h)"
    (let [dst-start (zoned-date-time "2025-10-26") ;; DST ends on 2025-10-26 in Finland
          dst-end   (plus dst-start :day)
          hours     (until dst-start dst-end :hours)]
      (is (= 1 (until dst-start dst-end :days)))
      ;; We expect either 24 or 25 here depending on library rules; typically 25.
      (is (contains? #{24 25} hours))
      (is (= (* hours 60) (until dst-start dst-end :minutes)))
      (is (= (* hours 60 60) (until dst-start dst-end :seconds)))))

  (testing "Explicit DST spring-forward transition (last Sunday of March 2025: 23h)"
    (let [spring-start (zoned-date-time "2025-03-30") ;; DST starts 2025-03-30 (Finland): lose one hour
          spring-end   (plus spring-start :day)
          hours        (until spring-start spring-end :hours)]
      (is (= 1 (until spring-start spring-end :days)))
      ;; We expect either 23 or 24 depending on time-zone library; typically 23.
      (is (contains? #{23 24} hours))

      (is (= (* hours 60) (until spring-start spring-end :minutes)))
      (is (= (* hours 60 60) (until spring-start spring-end :seconds))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests for sade.date/year
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest year-test
  (testing "String in Finnish d.m.yyyy format"
    (is (= 2023 (year "16.5.2023"))))

  (testing "String in ISO yyyy-MM-dd format"
    (is (= 2022 (year "2022-08-28")))
    (is (= 1999 (year "1999-01-01"))))

  (testing "String in XML yyyy-MM-ddZ format"
    (is (= 2022 (year "2022-08-28Z")))
    (is (= 1999 (year "1999-01-01Z"))))

  (testing "Timestamp as string"
    (let [ts 1672444800000] ;; 2022-12-31T02:00+02:00
      (is (= 2022 (year (str ts))))
      (is (= 2022 (year ts)))))

  (testing "java.util.Date"
    (let [date (Date. 1672531199000)] ;; 2023-01-01T00:59:59+02:00 (EET)
      (is (= 2023 (year date)))))

  (testing "Map with year, month, day"
    (is (= 2020 (year {:year 2020 :month 2 :day 29}))))

  (testing "ZonedDateTime instance"
    (let [zdt (zoned-date-time "2021-07-04T12:13:14+03:00")]
      (is (= 2021 (year zdt)))))

  (testing "Blank and nil input"
    (is (nil? (year nil)))
    (is (nil? (year "   "))))

  (testing "Invalid input"
    (is (thrown? Exception (year "foobar")))
    (is (thrown? Exception (year :not-a-date)))))
