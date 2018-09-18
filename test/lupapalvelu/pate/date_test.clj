(ns lupapalvelu.pate.date-test
  (:require [clj-time.core :as time]
            [lupapalvelu.pate.date :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))


(defn work-day
  ([id from]
   (work-day id from from))
  ([id from to]
   (fact {:midje/description (format "%s next work day: %s -> %s" id from to)}
     (forward-to-work-day (parse-finnish-date from))
     => (parse-finnish-date to))))

(defn holiday [id s result]
  (fact {:midje/description (format "%s %s -> %s" id s result)}
    (holiday? (parse-finnish-date s)) => result))

(facts "Date parsing"
  (parse-finnish-date "1.2.2017")
  => (time/local-date 2017 2 1)
  (parse-finnish-date "01.2.2017")
  => (time/local-date 2017 2 1)
  (parse-finnish-date "1.02.2017")
  => (time/local-date 2017 2 1)
  (parse-finnish-date "01.02.2017")
  => (time/local-date 2017 2 1)
  (parse-finnish-date "88.77.2017")
  => nil)

(facts "Finnish date"
  (finnish-date (time/local-date 2017 2 1))
  => "1.2.2017"
  (finnish-date (time/date-time 2017 2 1))
  => "1.2.2017"
  (finnish-date nil) => nil
  (finnish-date "") => nil
  (finnish-date "foobar") => nil
  (finnish-date (util/to-millis-from-local-date-string "6.4.2018"))
  => "6.4.2018"
  (finnish-date (Long. (util/to-millis-from-local-date-string "6.4.2018")))
  => "6.4.2018"
  (finnish-date "04.05.2018") => "4.5.2018"
  (finnish-date (Integer. 0))
  => "1.1.1970"
  (finnish-date nil) => nil)

(facts "Holidays"
  (holiday "New Year" "1.1.2018" true)
  (holiday "Epiphany" "6.1.2018" true)
  (holiday "Good Friday" "14.4.2017" true)
  (holiday "Good Friday" "30.3.2018" true)
  (holiday "Easter Monday" "17.4.2017" true)
  (holiday "Easter Monday" "2.04.2018" true)
  (holiday "May Day" "01.05.2017" true)
  (holiday "Ascension Day" "25.5.2017" true)
  (holiday "Ascension Day" "10.5.2018" true)
  (holiday "Midsummer eve" "23.6.2017" true)
  (holiday "Midsummer eve" "22.6.2018" true)
  (holiday "Independence Day" "06.12.2018" true)
  (holiday "Christmas eve" "24.12.2017" true)
  (holiday "Christmas" "25.12.2017" true)
  (holiday "Boxing Day" "26.12.2017" true)
  (holiday "Regular Monday" "21.08.2017" false)
  (holiday "Saturday" "26.8.2017" false)
  (holiday "Sunday" "27.8.2017" false))

(facts "Next work days"
  (work-day "New Year" "1.1.2017" "2.1.2017")
  (work-day "Epiphany" "6.1.2017" "9.1.2017")
  (work-day "Good Friday" "14.4.2017" "18.4.2017")
  (work-day "Easter Monday" "17.4.2017" "18.4.2017")
  (work-day "May Day" "1.5.2017" "2.5.2017")
  (work-day "Ascension Day" "25.5.2017" "26.5.2017")
  (work-day "Midsummer eve" "23.6.2017" "26.6.2017")
  (work-day "Independence Day" "6.12.2017" "7.12.2017")
  (work-day "Christmas eve" "24.12.2017" "27.12.2017")
  (work-day "Christmas" "25.12.2017" "27.12.2017")
  (work-day "Boxing Day" "26.12.2017" "27.12.2017")
  (work-day "Regular Monday" "21.8.2017" "21.8.2017")
  (work-day "Saturday" "26.8.2017" "28.8.2017")
  (work-day "Sunday" "27.8.2017" "28.8.2017"))

(defn ts[datestring]
  (+ (* 1000 3600 12) (util/to-millis-from-local-date-string datestring)))

(facts "Parse and forward"
  (parse-and-forward (ts "29.3.2018") 3 :days)
  => (ts "3.4.2018"))
