(ns lupapalvelu.time-util-test
  (:require [clj-time.coerce :as tc]
            [clj-time.format :as timeformat]
            [midje.sweet :refer :all]
            [sade.util :refer [format-timestamp-local-tz]]
            [lupapalvelu.time-util :as time-util]))

(defn ->str [timestamp]
  (format-timestamp-local-tz timestamp "d.M.YYYY HH:mm:ss:SSS"))

(defn ->timestamp [date-time]
  (-> (timeformat/formatter-local "d.M.YYYY HH:mm:ss:SSS")
      (timeformat/parse date-time)
      tc/to-long))

(facts "timestamp-at-the-end-of-previous-day"
       (fact "sets time at the last millisecond of the previous day when timestamp has no time component "
             (->str (time-util/timestamp-at-the-end-of-previous-day (->timestamp "2.2.2018 00:00:00:000"))) => "1.2.2018 23:59:59:999")

       (fact "sets time at the last millisecond of the previous day when timestamp has time component"
             (->str (time-util/timestamp-at-the-end-of-previous-day (->timestamp "2.2.2018 08:00:00:000"))) => "1.2.2018 23:59:59:999"
             (->str (time-util/timestamp-at-the-end-of-previous-day (->timestamp "2.2.2018 00:00:00:001"))) => "1.2.2018 23:59:59:999"))
