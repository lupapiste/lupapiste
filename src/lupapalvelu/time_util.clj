(ns lupapalvelu.time-util
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]
            [sade.util :refer [format-timestamp-local-tz]])
  (:import [org.joda.time DateTime LocalDateTime]))

(def time-format (tf/formatter "dd.MM.YYYY"))

(defn timestamp-after? [timestamp1 timestamp2]
  (t/after? (tc/from-long timestamp1) (tc/from-long timestamp2)))

(defn tomorrow []
  (-> (t/today)
      (t/plus (t/days 1))
      (tc/to-date-time)))

(defn day-before [date]
  (t/plus date (t/days -1)))

(defn timestamp-day-before [timestamp]
  (when timestamp
    (tc/to-long (day-before (tc/from-long timestamp)))))

(defn ->timestamp [local-date-time-string]
  (-> (tf/formatter-local "d.M.YYYY HH:mm:ss:SSS")
      (tf/parse local-date-time-string)
      tc/to-long))

(defn ts->str [timestamp]
  (format-timestamp-local-tz timestamp "d.M.YYYY HH:mm:ss:SSS"))

(defn at-the-end-of-previous-day [date]
  (when date
    (-> date
        (t/floor t/day)
        (t/plus (t/millis -1)))))

(defn ->date-time [timestamp & [timezone]]
  (DateTime. timestamp (or timezone (t/default-time-zone))))

(defn timestamp-at-the-end-of-previous-day [timestamp & [timezone]]
  (when timestamp
    (-> timestamp
        (->date-time timezone)
        at-the-end-of-previous-day
        tc/to-long)))

(defn ts->str [timestamp & [{:keys [pattern timezone]}]]
  (let [tz (or timezone (t/default-time-zone))
        date-in-tz (DateTime. timestamp tz)
        frmt (-> (or pattern "d.M.YYYY HH:mm:ss:SSS")
                 tf/formatter-local
                 (tf/with-zone tz))]
             (tf/unparse frmt date-in-tz)))

(defn ->date [^String date-str ]
  (tf/parse time-format date-str))

(defn ->date-str [^DateTime date-time]
  (tf/unparse time-format date-time))

(defn tomorrow-or-later? [date-str]
  (if date-str
    (not (t/before? (->date date-str) (tomorrow)))))
