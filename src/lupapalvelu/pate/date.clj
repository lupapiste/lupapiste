(ns lupapalvelu.pate.date
  "Date handling utilities. Limited to Finnish time zone and formats."
  (:require [sade.date :as date]
            [schema.core :as sc])
  (:import [java.time ZonedDateTime DayOfWeek]
           [java.time.temporal ChronoUnit]))


(defn- make-date [year month day]
  (date/zoned-date-time {:year year :month month :day day}))

(defn- divide
  "Returns [integer-ratio remainder].
  n: numerator
  d: denominator"
  [n d]
  [(int (/ n d)) (rem n d)])

(sc/defn ^:always-validate easter :- ZonedDateTime
  "Calculate the Easter Sunday date with Butcher's algorithm.
  https://fi.wikipedia.org/wiki/P%C3%A4%C3%A4si%C3%A4isen_laskeminen"
  [year :- sc/Int]
  (let [[_ a] (divide year 19)
        [b c] (divide year 100)
        [d e] (divide b 4)
        [f _] (divide (+ b 8) 25)
        [g _] (divide (inc (- b f)) 3)
        [_ h] (divide (+ (* 19 a) b (- d) (- g) 15) 30)
        [i k] (divide c 4)
        [_ l] (divide (+ 32 (* 2 e) (* 2 i) (- h) (- k)) 7)
        [m _] (divide (+ a (* 11 h) (* 22 l)) 451)
        [n p] (divide (+ h l (- (* 7 m)) 114) 31)]
    (make-date year n (inc p))))

(sc/defn ^:always-validate forward-to-week-day :- ZonedDateTime
  "Next date from the given date is the given week-day. Can be the
  date too."
  [date :- ZonedDateTime week-day-nr :- sc/Int]
  (let [dow   (.getDayOfWeek date)
        delta (- week-day-nr (.getValue dow))]
    (.plusDays date (if (neg? delta)
                      (+ 7 delta)
                      delta))))

(sc/defn ^:always-validate holiday? :- sc/Bool
  "True if the given date is a Finnish public holiday (excluding known
  weekend dates)."
  [date :- ZonedDateTime]
  (let [year (.getYear date)
        e    (easter year)]
    (contains? #{(make-date year 1 1) ;; New Year
                 (make-date year 1 6) ;; Epiphany
                 (.minusDays e 2) ;; Good Friday
                 (.plusDays e 1) ;; Easter Monday
                 (make-date year 5 1) ;; May Day
                 (.plusDays e 39) ;; Ascension Day
                 (forward-to-week-day (make-date year 6 19) 5) ;; Mid-summer eve
                 (make-date year 12 6) ;; Independence Day
                 (make-date year 12 24) ;; Christmas eve
                 (make-date year 12 25) ;; Christmas
                 (make-date year 12 26) ;; Boxing Day
                 } date)))

(sc/defn ^:always-validate forward-to-work-day :- ZonedDateTime
  "Forwards date to the next work day, if needed."
  [date :- ZonedDateTime]
  (loop [date date]
    (let [dow  (.getDayOfWeek date)
          step (cond
                 (= dow DayOfWeek/SATURDAY) 2 ;; Saturday
                 (= dow DayOfWeek/SUNDAY) 1 ;; Sunday
                 (holiday? date) 1)]
      (if step
        (recur (.plusDays date step))
        date))))

(sc/defn ^:always-validate tomorrow-or-later? :- sc/Bool
  "True if `timestamp` is after the end of today in the default timezone."
  [timestamp :- sc/Int]
  (.isAfter (date/zoned-date-time timestamp)
            (date/end-of-day (date/now))))


(sc/defn ^:always-validate parse-and-forward :- sc/Int
  "'Parses' UTC timestamp and forwards it for n days or years depending on the
  unit (:days or :years).
  The result is always an UTC timestamp.
  If to-work-day? is true, jump forward to the next workday if the result is a holiday or weekend."
  [ts           :- sc/Int
   n            :- sc/Int
   unit         :- (sc/enum :days :years)
   to-work-day? :- sc/Bool]
  (some-> (date/zoned-date-time ts)
          (.plus n (if (= unit :days)
                     ChronoUnit/DAYS
                     ChronoUnit/YEARS))
          (cond-> to-work-day? forward-to-work-day)
          date/timestamp))
