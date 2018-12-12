(ns lupapalvelu.time-util-test
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [midje.sweet :refer :all]
            [sade.util :refer [format-timestamp-local-tz]]
            [lupapalvelu.time-util :as time-util :refer [ts->str]]))


(facts "timestamp-at-the-end-of-previous-day"

       (with-redefs [t/default-time-zone (fn [] (t/time-zone-for-id "Europe/Helsinki"))]

         (defn ->timestamp [local-date-time-string]
           (-> (tf/formatter-local "d.M.YYYY HH:mm:ss:SSS")
               (tf/parse local-date-time-string)
               tc/to-long))

         (fact "sets time at the last millisecond of the previous day when timestamp has no time component "
               (ts->str (time-util/timestamp-at-the-end-of-previous-day (->timestamp "2.2.2018 00:00:00:000"))) => "1.2.2018 23:59:59:999")

         (fact "sets time at the last millisecond of the previous day when timestamp has time component"
               (ts->str (time-util/timestamp-at-the-end-of-previous-day (->timestamp "2.2.2018 08:00:00:000"))) => "1.2.2018 23:59:59:999"
               (ts->str (time-util/timestamp-at-the-end-of-previous-day (->timestamp "2.2.2018 00:00:00:001"))) => "1.2.2018 23:59:59:999")))
