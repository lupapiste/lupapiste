(ns lupapalvelu.pate.date
  "Date handling utilities. Limited to Finnish time zone and formats."
  (:require [clj-time.coerce :as timec]
            [clj-time.core :as time]
            [clj-time.format :as timef]))

(defn- divide
  "Returns [integer-ratio remainder].
  n: numerator
  d: denominator"
  [n d]
  [(int (/ n d)) (rem n d)])

(defn easter
  "Calculate the Easter Sunday date with Butcher's algorithm.
  https://fi.wikipedia.org/wiki/P%C3%A4%C3%A4si%C3%A4isen_laskeminen"
  [year]
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
    (time/local-date year n (inc p))))

(defn forward-to-week-day
  "Next date from the given date is the given week-day. Can be the
  date too."
  [date week-day]
  (let [dow   (time/day-of-week date)
        delta (- week-day dow)]
    (time/plus date (time/days (if (neg? delta)
                                 (+ 7 delta)
                                 delta)))))

(defn holiday?
  "True if the given date is a Finnish public holiday (excluding known
  weekend dates)."
  [date]
  (let [date (time/local-date (time/year date)
                              (time/month date)
                              (time/day date))
        year (time/year date)
        e    (easter year)]
    (contains? #{(time/local-date year 1 1)     ;; New Year
                 (time/local-date year 1 6)     ;; Epiphany
                 (time/minus e (time/days 2))   ;; Good Friday
                 (time/plus e (time/days 1))    ;; Easter Monday
                 (time/local-date year 5 1)     ;; May Day
                 (time/plus e (time/days 39))   ;; Ascension Day
                 (forward-to-week-day (time/local-date year 6 19) 5) ;; Mid-summer eve
                 (time/local-date year 12 6)    ;; Independence Day
                 (time/local-date year 12 24)   ;; Christmas eve
                 (time/local-date year 12 25)   ;; Christmas
                 (time/local-date year 12 26)   ;; Boxing Day
                 } date)))

(defn forward-to-work-day
  "Forwards date to the next work day, if needed."
  [date]
  (loop [date date]
    (let [dow  (time/day-of-week date)
          step (cond
                 (= dow 6)       2 ;; Saturday
                 (= dow 7)       1 ;; Sunday
                 (holiday? date) 1)]
      (if step
        (recur (time/plus date (time/days step)))
        date))))

(def finnish-formatter (timef/formatter "d.M.YYYY"))

(defn parse-finnish-date
  "Parses the given string according to the Finnish date format:
  23.8.2017. Returns nil, if the parsing fails."
  [s]
  (try (timef/parse-local-date finnish-formatter s)
       (catch Exception _ nil)))

(defn finnish-date
  "Returns the given date as a Finnish date format string. The date is
  in UTC. The argument can be either timestamp (ms from epoch),
  LocalDate, DateTime or Finnish date string (just in case)."
  [date]
  (some->> (cond
             (string? date) (some-> date parse-finnish-date timec/to-long)
             (instance? org.joda.time.LocalDate date) (timec/to-long date)
             (instance? org.joda.time.DateTime date) (timec/to-long date)
             (integer? date) date)
           timec/from-long
           (timef/unparse finnish-formatter)))

(defn parse-and-forward
  "'Parses' UTC timestamp and forwards it for n days or years depending on the
  unit (:days or :years). The result is always an UTC timestamp."
  [ts n unit]
  (some-> (timec/from-long ts)
          (time/plus ((if (= unit :days)
                        time/days
                        time/years) n))
          forward-to-work-day
          timec/to-long))
