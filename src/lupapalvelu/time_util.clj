(ns lupapalvelu.time-util
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]
            [sade.util :refer [to-finnish-date]]))

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

(defn at-the-end-of-previous-day [date]
  (when date
    (-> date
        (t/floor t/day)
        (t/plus (t/millis -1)))))

(defn ->local-date-time [timestamp]
  (-> timestamp
      tc/from-long
      tl/to-local-date-time))

(defn timestamp-at-the-end-of-previous-day [timestamp]
  (when timestamp
    (-> timestamp
        ->local-date-time
        at-the-end-of-previous-day
        tc/to-long)))

(defn ->date [date-str]
  (tf/parse time-format date-str))

(defn ->date-str [date]
  (tf/unparse time-format date))

(defn tomorrow-or-later? [date-str]
  (if date-str
    (not (t/before? (->date date-str) (tomorrow)))))
