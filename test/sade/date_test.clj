(ns sade.date-test
  (:require [midje.sweet :refer :all]
            [sade.date :refer :all])
  (:import [java.time ZoneId Instant]
           [java.time.format DateTimeFormatter DateTimeFormatterBuilder]
           [java.time.temporal ChronoField]
           [java.util Date]))

(facts "zoned-date-time"
  (let [ts         1621005883813
        parse-bad? (throws #"Unknown format, cannot be parsed: bad")]
    (fact "Timestamp"
      (let [zoned (zoned-date-time ts)]
        (str zoned) => "2021-05-14T18:24:43.813+03:00[Europe/Helsinki]"
        (fact "ZonedDateTime passthrough"
          (zoned-date-time zoned) => zoned)
        (fact "String timestamp"
          (zoned-date-time (str ts)) => zoned)))
    (let [zoned (zoned-date-time 123)]
      (fact "Any number type is supported"
        (str zoned) => "1970-01-01T02:00:00.123+02:00[Europe/Helsinki]"
        (zoned-date-time (long 123)) => zoned
        (zoned-date-time (int 123)) => zoned
        (zoned-date-time (short 123)) => zoned
        (zoned-date-time (float 123.123)) => zoned
        (zoned-date-time (double 123.123)) => zoned))
    (fact "Nil-safe"
      (zoned-date-time nil) => nil)
    (fact "Zoned to timestamp"
      (let [zoned (zoned-date-time ts)]
        (timestamp zoned) => ts
        (timestamp nil) => nil
        (timestamp []) => (throws #"No implementation")))
    (fact "Map argument"
      (let [zoned (zoned-date-time {:day 27 :month 5 :year 2021})]
        (str zoned) => "2021-05-27T00:00+03:00[Europe/Helsinki]"
        (zoned-date-time (timestamp zoned)) => zoned
        (str (zoned-date-time {:day     27 :month  5  :year   2021
                               :hour    11 :minute 22 :second 33
                               :zone-id (ZoneId/of "America/Toronto")}))
        => "2021-05-27T11:22:33-04:00[America/Toronto]"
        (fact "Different map types"
          (zoned-date-time (hash-map :day 27 :month 5 :year 2021)) => zoned
          (zoned-date-time (sorted-map :day 27 :month 5 :year 2021)) => zoned)))
    (fact "Bad maps"
      (zoned-date-time {:day 27 :month 15 :year 2021}) => (throws))
    (fact "String argument"
      (zoned-date-time "") => nil
      (zoned-date-time "   ") => nil
      (str (zoned-date-time "28.8.2022"))
      => "2022-08-28T00:00+03:00[Europe/Helsinki]"
      (str (zoned-date-time "28.8.2022 19:11"))
      => "2022-08-28T19:11+03:00[Europe/Helsinki]"
      (str (zoned-date-time "2022-04-28"))
      => "2022-04-28T00:00+03:00[Europe/Helsinki]"
      (str (zoned-date-time "2022-04-28-14:30"))
      => "2022-04-28T00:00+03:00[Europe/Helsinki]"
      (str (zoned-date-time "2022-04-28T19:13:04Z"))
      => "2022-04-28T22:13:04+03:00[Europe/Helsinki]"
      (= (str (zoned-date-time "1655385252464"))
         (str (zoned-date-time 1655385252464))
         "2022-06-16T16:14:12.464+03:00[Europe/Helsinki]")
      => true
      (= (str (zoned-date-time "  -1655385252464  "))
         (str (zoned-date-time -1655385252464))
         "1917-07-18T12:25:36.536+01:39:49[Europe/Helsinki]")
      => true
      (= (str (zoned-date-time "0"))
         (str (zoned-date-time 0))
         "1970-01-01T02:00+02:00[Europe/Helsinki]")
      => true)
    (fact "Bad strings"
      (zoned-date-time "bad")
      => parse-bad?
      (zoned-date-time "--123")
      => (throws #"Unknown format, cannot be parsed: --123")
      (zoned-date-time "34.7.2022")
      => (throws java.time.format.DateTimeParseException)
      (zoned-date-time "1122249283479238479238479247928479234792347")
      => (throws NumberFormatException))

    (facts "with-time"
      (let [zoned (zoned-date-time ts)]
        (fact "All defaults (zero)"
          (str (with-time zoned)) => "2021-05-14T00:00+03:00[Europe/Helsinki]")
        (fact "Hour"
          (str (with-time zoned 17)) => "2021-05-14T17:00+03:00[Europe/Helsinki]")
        (fact "Hour and minute"
          (str (with-time zoned 17 45)) => "2021-05-14T17:45+03:00[Europe/Helsinki]")
        (fact "Hour, minute and seconds"
          (str (with-time zoned 17 45 36)) => "2021-05-14T17:45:36+03:00[Europe/Helsinki]")
        (fact "Nil-safe"
          (with-time nil) => nil
          (with-time "   " 12 13 14) => nil)
        (fact "Bad args"
          (let [bad (throws #"Invalid value")]
            (with-time zoned 24) => bad
            (with-time zoned -1) => bad
            (with-time zoned 17 60) => bad
            (with-time zoned 17 -1) => bad
            (with-time zoned 17 45 60) => bad
            (with-time zoned 17 45 -1) => bad
            (with-time "bad") => parse-bad?))))

    (facts "start-of-day"
      (str (start-of-day ts)) => "2021-05-14T00:00+03:00[Europe/Helsinki]"
      (str (start-of-day "29.4.2022 09:12")) => "2022-04-29T00:00+03:00[Europe/Helsinki]"
      (str (start-of-day "2022-04-28T23:11:22Z")) => "2022-04-29T00:00+03:00[Europe/Helsinki]"
      (str (start-of-day (with-zone (zoned-date-time "2022-04-28T23:11:22Z") utc-zone-id)))
      => "2022-04-28T00:00Z[UTC]"
      (start-of-day "bad") => parse-bad?)
    (facts "end-of-day"
      (str (end-of-day ts)) => "2021-05-14T23:59:59.999+03:00[Europe/Helsinki]"
      (str (end-of-day "29.4.2022 09:12")) => "2022-04-29T23:59:59.999+03:00[Europe/Helsinki]"
      (str (end-of-day "2022-04-28T23:11:22Z")) => "2022-04-29T23:59:59.999+03:00[Europe/Helsinki]"
      (str (end-of-day (with-zone (zoned-date-time "2022-04-28T23:11:22Z") utc-zone-id)))
      => "2022-04-28T23:59:59.999Z[UTC]"
      (end-of-day "bad") => parse-bad?
      (- (timestamp "30.5.2022") (timestamp (end-of-day (timestamp "29.5.2022")))) => 1)
    (facts "Current time"
      (start-of-day (now)) => (today))

    (facts "with-zone"
      (str (zoned-date-time ts)) => "2021-05-14T18:24:43.813+03:00[Europe/Helsinki]"
      (str (with-zone ts)) => "2021-05-14T18:24:43.813+03:00[Europe/Helsinki]"
      (str (with-zone ts utc-zone-id)) => "2021-05-14T15:24:43.813Z[UTC]"
      (str (with-zone (with-zone ts utc-zone-id)))
      => "2021-05-14T18:24:43.813+03:00[Europe/Helsinki]"
      (str (with-zone ts (ZoneId/of "Antarctica/South_Pole")))
      => "2021-05-15T03:24:43.813+12:00[Antarctica/South_Pole]"
      (with-zone "bad") => parse-bad?
      (with-zone ts (ZoneId/of "Bad/Zone")) => (throws #"Unknown time-zone ID: Bad/Zone")
      (with-zone nil) => nil
      (with-zone "  ") => nil)

    (let [ts  (timestamp "2021-05-04T18:24:43.1234")
          utc (with-zone "2022-04-28T23:30:45.8888Z" utc-zone-id)]
      (facts "zoned-format"
        (facts "keywords"
          (zoned-format ts :iso-date) => "2021-05-04+03:00"
          (zoned-format ts :iso-date :local) => "2021-05-04"
          (zoned-format ts :iso-datetime) => "2021-05-04T18:24:43+03:00"
          (zoned-format ts :iso-datetime :local) => "2021-05-04T18:24:43"
          (zoned-format utc :iso-datetime) => "2022-04-29T02:30:45+03:00"
          (zoned-format utc :iso-datetime :local) => "2022-04-29T02:30:45"
          (zoned-format utc :iso-date) => "2022-04-29+03:00"
          (zoned-format utc :iso-date :local) => "2022-04-29"
          (zoned-format ts :rfc) => "Tue, 4 May 2021 15:24:43 GMT"
          (zoned-format (with-zone ts utc-zone-id) :rfc) => "Tue, 4 May 2021 15:24:43 GMT"
          (zoned-format utc :rfc) => "Thu, 28 Apr 2022 23:30:45 GMT"
          (zoned-format ts :fi-date) => "4.5.2021"
          (zoned-format ts :fi-datetime) => "4.5.2021 18.24"
          (zoned-format utc :fi-date) => "29.4.2022"
          (zoned-format utc :fi-datetime) => "29.4.2022 2.30"
          (zoned-format ts :fi-date :zero-pad) => "04.05.2021"
          (zoned-format ts :fi-datetime :zero-pad) => "04.05.2021 18.24"
          (zoned-format utc :fi-date :zero-pad) => "29.04.2022"
          (zoned-format utc :fi-datetime :zero-pad) => "29.04.2022 02.30"
          (zoned-format ts :fi-datetime :seconds) => "4.5.2021 18.24.43"
          (zoned-format utc :fi-datetime :seconds) => "29.4.2022 2.30.45"
          (zoned-format ts :fi-datetime :zero-pad :seconds) => "04.05.2021 18.24.43"
          (zoned-format utc :fi-datetime :seconds :zero-pad) => "29.04.2022 02.30.45")
        (facts "formatters"
          (zoned-format ts (pattern-formatter "YYYY GGGG, 'it was ' EEEE."))
          => "2021 Anno Domini, it was  Tuesday."
          (zoned-format utc DateTimeFormatter/ISO_WEEK_DATE)
          => "2022-W17-4Z")
        (facts "Bad formats"
          (zoned-format ts :bad) => (throws #"Unsupported format: :bad")
          (zoned-format ts "foobar") => (throws #"Unsupported format: foobar")
          (zoned-format ts (DateTimeFormatter/ofPattern "bad"))
          => (throws Exception #"Unknown pattern letter: b"))
        (facts "Bad options are ignored"
          (zoned-format ts :iso-date :bad) => (zoned-format ts :iso-date)
          (zoned-format ts :iso-date :local :bad) => (zoned-format ts :iso-date :local))
        (facts "Convenience functions"
          (iso-datetime ts) => (zoned-format ts :iso-datetime)
          (iso-datetime ts :local) => (zoned-format ts :iso-datetime :local)
          (iso-date ts) => (zoned-format ts :iso-date)
          (iso-date ts :local) => (zoned-format ts :iso-date :local)
          (finnish-date ts) => (zoned-format ts :fi-date)
          (finnish-date ts :local) => (zoned-format ts :fi-date)
          (finnish-date ts :zero-pad) => (zoned-format ts :fi-date :zero-pad)
          (finnish-datetime ts) => (zoned-format ts :fi-datetime)
          (finnish-datetime ts :local) => (zoned-format ts :fi-datetime)
          (finnish-datetime ts :zero-pad) => (zoned-format ts :fi-datetime :zero-pad)
          (finnish-datetime ts :seconds) => (zoned-format ts :fi-datetime :seconds)
          (finnish-datetime ts :local :seconds) => (zoned-format ts :fi-datetime :seconds)
          (finnish-datetime ts :zero-pad :seconds)
          => (zoned-format ts :fi-datetime :seconds :zero-pad))
        (facts "XML"
          (xml-datetime ts) => (zoned-format ts :iso-datetime)
          (xml-date ts) => (zoned-format ts :iso-date)
          (iso-datetime "1900-01-01T13:14:15Z") => "1900-01-01T14:54:04+01:39:49"
          (xml-datetime "1900-01-01T13:14:15Z") => "1900-01-01T14:54:04+01:39"
          (iso-date "1910-10-11+18:00") => "1910-10-11+01:39:49"
          (xml-date "1910-10-11+18:00") => "1910-10-11+01:39"))))

  (let [apr5 "2022-04-05T00:00+03:00[Europe/Helsinki]"
        fmt  (-> (DateTimeFormatterBuilder.)
                 (.append (DateTimeFormatter/ISO_WEEK_DATE))
                 (.parseDefaulting ChronoField/NANO_OF_DAY 0)
                 (.toFormatter))]
    (facts "zoned-parse"
      (zoned-parse nil) => nil
      (zoned-parse "") => nil
      (zoned-parse "   ") => nil

      (facts "keywords"
        (str (zoned-parse "5.4.2022" :fi)) => apr5
        (str (zoned-parse "  05.4.2022  " :fi)) => apr5
        (str (zoned-parse "5.04.2022" :fi)) => apr5
        (str (zoned-parse "05.04.2022" :fi)) => apr5
        (str (zoned-parse "5.4.2022 19.51" :fi))
        => "2022-04-05T19:51+03:00[Europe/Helsinki]"
        (str (zoned-parse " 05.04.2022   19.51 " :fi))
        => "2022-04-05T19:51+03:00[Europe/Helsinki]"
        (str (zoned-parse "  10.05.2022    06:7:1 " :fi))
        => "2022-05-10T06:07:01+03:00[Europe/Helsinki]"
        (str (zoned-parse "5.4.2022" :fi-date)) => apr5
        (str (zoned-parse "  05.4.2022  " :fi-date)) => apr5
        (str (zoned-parse "5.04.2022" :fi-date)) => apr5
        (str (zoned-parse "05.04.2022" :fi-date)) => apr5
        (str (zoned-parse "2022-04-05" :iso-date)) => apr5
        (str (zoned-parse "  2022-04-05Z  " :iso-date)) => apr5
        (str (zoned-parse "2022-04-05z" :iso-date)) => apr5
        (str (zoned-parse "2022-04-05-12:30" :iso-date)) => apr5
        (str (zoned-parse "2022-04-05-18:00" :iso-date)) => apr5
        (str (zoned-parse "2022-04-05+12:30" :iso-date)) => apr5
        (str (zoned-parse "2022-04-05+18:00" :iso-date)) => apr5
        (str (zoned-parse " 2022-04-05T14:25 " :iso-datetime))
        => "2022-04-05T14:25+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05T14:25:15" :iso-datetime))
        => "2022-04-05T14:25:15+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05T14:25:15.1234" :iso-datetime))
        => "2022-04-05T14:25:15.123400+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05T14:25:15Z" :iso-datetime))
        => "2022-04-05T17:25:15+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05t14:25:15z" :iso-datetime))
        => "2022-04-05T17:25:15+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05T04:25:15+10:30" :iso-datetime))
        => "2022-04-04T20:55:15+03:00[Europe/Helsinki]")
      (facts "Guesses"
        (str (zoned-parse "5.4.2022")) => apr5
        (str (zoned-parse "05.04.2022")) => apr5
        (str (zoned-parse "5.4.2022 19.51"))
        => "2022-04-05T19:51+03:00[Europe/Helsinki]"
        (str (zoned-parse "05.04.2022 19.51"))
        => "2022-04-05T19:51+03:00[Europe/Helsinki]"
        (str (zoned-parse "  15.5.2022 16.32.12  "))
        => "2022-05-15T16:32:12+03:00[Europe/Helsinki]"
        (str (zoned-parse "  10.05.2022 06:7:1 " :fi))
        => "2022-05-10T06:07:01+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05")) => apr5
        (str (zoned-parse "2022-04-05Z")) => apr5
        (str (zoned-parse "  2022-04-05-12:30  ")) => apr5
        (str (zoned-parse "2022-04-05+03:00")) => apr5
        (str (zoned-parse "2022-04-05T14:25"))
        => "2022-04-05T14:25+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05T14:25:15"))
        => "2022-04-05T14:25:15+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05T14:25:15.1234"))
        => "2022-04-05T14:25:15.123400+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05T14:25:15Z"))
        => "2022-04-05T17:25:15+03:00[Europe/Helsinki]"
        (str (zoned-parse "2022-04-05T04:25:15+10:30"))
        => "2022-04-04T20:55:15+03:00[Europe/Helsinki]")
      (facts "Formatter"
        (let [parsed (zoned-parse " 2022-W30-7Z " fmt)]
          (str parsed) => "2022-07-31T00:00Z"
          (zoned-format parsed fmt) => "2022-W30-7Z"
          (zoned-format "hello" "world") => (throws)))
      (facts "Reformat"
        (reformat "3.4.2022" :iso-date) => "2022-04-03+03:00"
        (reformat "2021-W40-1+08:00" fmt :fi-datetime) => "3.10.2021 19.00"
        (reformat nil :fi-date) => nil
        (reformat "  " :fi-date) => nil)
      (facts "Parse fails"
        (zoned-parse "1:5:2022" :fi) => (throws)
        (zoned-parse "1:5:2022 12:13" :fi) => (throws)
        (zoned-parse "1:5:2022" :fi-date) => (throws)
        (zoned-parse "  5.4.2022 19.51 " :fi-date)
        => (throws #"Cannot parse '5.4.2022 19.51' as a Finnish date")
        (zoned-parse "bad" :iso-date) => (throws #"Cannot parse 'bad' as an ISO date.")
        (zoned-parse "bad" :iso-datetime) => (throws)
        (zoned-parse "bad" :fi) => (throws)
        (zoned-parse "bad" fmt) => (throws)
        (zoned-parse "bad") => (throws #"Unknown format, cannot be parsed: bad")
        (str (zoned-parse "5.4.2022")) => apr5
        (zoned-parse "5.4.2022 12") => (throws)
        (zoned-parse "5.4.2022 12. 30") => (throws)
        (zoned-parse "5.4.2022 12.30") => truthy
        (zoned-parse "5.4.2022" :iso-date) => (throws)
        (zoned-parse "5.4.2022" :iso-datetime) => (throws)
        (zoned-parse "5.4.2022 16:45") => truthy
        (zoned-parse "2021-02-01Z") => truthy
        (zoned-parse "2021-02-01Z" :iso-datetime) => (throws)
        (zoned-parse "2021-02-01A") => (throws))
      (facts "Predicates"
        (format? :fi "3.5.2022") => true
        (format? :fi-date " 3.5.2022 ") => true
        (format? :fi "3.5.2022 11.22.33 ") => true
        (format? :fi-date "3.5.2022 11.22.33 ") => false
        (format? :fi "45.14.2022") => false
        (format? :fi-date "45.14.2022") => false
        (xml-date? "2022-05-03Z") => true
        (xml-date? "2022-05-03") => true
        (xml-date? "2022-05-03T00:00:00Z") => false
        (xml-datetime? "2022-05-03Z") => false
        (xml-datetime? "2022-05-03T00:00:00Z") => true)))
  (let [s           "2022-10-05T12:31:44"
        unit-error? (throws #"No matching clause")]
    (facts "plus"
      (plus s :seconds 0) => (zoned-date-time s)
      (str (plus s :seconds 2)) => "2022-10-05T12:31:46+03:00[Europe/Helsinki]"
      (str (plus s :seconds -2)) => "2022-10-05T12:31:42+03:00[Europe/Helsinki]"
      (str (plus s :second)) => "2022-10-05T12:31:45+03:00[Europe/Helsinki]"
      (plus s :minutes 0) => (zoned-date-time s)
      (str (plus s :minutes 2)) => "2022-10-05T12:33:44+03:00[Europe/Helsinki]"
      (str (plus s :minutes -2)) => "2022-10-05T12:29:44+03:00[Europe/Helsinki]"
      (str (plus s :minute)) => "2022-10-05T12:32:44+03:00[Europe/Helsinki]"
      (plus s :hours 0) => (zoned-date-time s)
      (str (plus s :hours 2)) => "2022-10-05T14:31:44+03:00[Europe/Helsinki]"
      (str (plus s :hours -2)) => "2022-10-05T10:31:44+03:00[Europe/Helsinki]"
      (str (plus s :hour)) => "2022-10-05T13:31:44+03:00[Europe/Helsinki]"
      (plus s :days 0) => (zoned-date-time s)
      (str (plus s :days 2)) => "2022-10-07T12:31:44+03:00[Europe/Helsinki]"
      (str (plus s :days -2)) => "2022-10-03T12:31:44+03:00[Europe/Helsinki]"
      (str (plus s :day)) => "2022-10-06T12:31:44+03:00[Europe/Helsinki]"
      (plus s :weeks 0) => (zoned-date-time s)
      (str (plus s :weeks 2)) => "2022-10-19T12:31:44+03:00[Europe/Helsinki]"
      (str (plus s :weeks -2)) => "2022-09-21T12:31:44+03:00[Europe/Helsinki]"
      (str (plus s :week)) => "2022-10-12T12:31:44+03:00[Europe/Helsinki]"
      (plus s :months 0) => (zoned-date-time s)
      (str (plus s :months 2)) => "2022-12-05T12:31:44+02:00[Europe/Helsinki]"
      (str (plus s :months -2)) => "2022-08-05T12:31:44+03:00[Europe/Helsinki]"
      (str (plus s :month)) => "2022-11-05T12:31:44+02:00[Europe/Helsinki]"
      (plus s :years 0) => (zoned-date-time s)
      (str (plus s :years 2)) => "2024-10-05T12:31:44+03:00[Europe/Helsinki]"
      (str (plus s :years -2)) => "2020-10-05T12:31:44+03:00[Europe/Helsinki]"
      (str (plus s :year)) => "2023-10-05T12:31:44+03:00[Europe/Helsinki]"
      (plus s :week 10) => unit-error?
      (plus s :weeks) => unit-error?
      (plus s :weeks "10") => (throws)
      (plus "bad" :week) => (throws))
    (facts "minus"
      (minus s :minutes 0) => (zoned-date-time s)
      (str (minus s :minutes 2)) => "2022-10-05T12:29:44+03:00[Europe/Helsinki]"
      (str (minus s :minutes -2)) => "2022-10-05T12:33:44+03:00[Europe/Helsinki]"
      (str (minus s :minute)) => "2022-10-05T12:30:44+03:00[Europe/Helsinki]"
      (minus s :day 10) => unit-error?
      (minus s :days) => unit-error?
      (minus s :days "10") => (throws)
      (minus "bad" :day) => (throws)))
  (let [yesterday (minus (today) :day)
        tomorrow  (plus (today) :day)]
    (facts "before? and after?"
      (before? yesterday tomorrow) => true
      (before? tomorrow yesterday) => false
      (before? yesterday yesterday) => false
      (before? yesterday (today) tomorrow) => true
      (before? yesterday nil) => (throws NullPointerException)
      (before? "" yesterday) => (throws NullPointerException)
      (after? tomorrow tomorrow) => false
      (after? tomorrow yesterday) => true
      (after? yesterday tomorrow) => false
      (after? tomorrow (today) yesterday) => true
      (after? yesterday nil) => (throws NullPointerException)
      (after? "" yesterday) => (throws NullPointerException)
      (after? "1.6.2022" {:year 2022 :month 5 :day 15}) => true
      (before? "bad" tomorrow) => (throws #"Unknown format"))
    (let [utc (with-zone "20.5.2022 21.32.51" utc-zone-id)
          zoned (zoned-date-time "2022-05-20T18:32:51Z")]
      (facts "eq?"
       (eq? nil nil " " "") => true
       (eq? tomorrow (with-time (plus (now) :day))) => true
       (eq? yesterday tomorrow) => false
       (eq? "23.5.2022" "2022-05-23+12:00" {:year 2022 :month 5 :day 23})
       => true
       (eq? (now)) => true
       (eq? "20.5.2022 18.32.51" "2022-05-20T18:32:51") => true
       (eq? "20.5.2022 18.32.51" "2022-05-20T18:32:51Z") => false
       (= utc zoned) => false
       (eq? utc zoned) => true
       (eq? "bad" "also bad") => (throws)
       (eq? zoned (.withNano zoned 999999)) => true
       (eq? zoned (.withNano zoned (inc 999999))) => false))))


(let [ts 1667306723362]
  (fact "Instant"
    (let [i     (Instant/ofEpochMilli ts)
          zoned (zoned-date-time i)]
     (inst? i) => true
     zoned => (zoned-date-time ts)
     (str zoned) => "2022-11-01T14:45:23.362+02:00[Europe/Helsinki]"))
  (fact "Date"
    (let [date (Date. ts)
          zoned (zoned-date-time date)]
      (instance? Date date) => true
     zoned => (zoned-date-time ts)
     (str zoned) => "2022-11-01T14:45:23.362+02:00[Europe/Helsinki]")))
