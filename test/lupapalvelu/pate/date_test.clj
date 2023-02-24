(ns lupapalvelu.pate.date-test
  (:require [lupapalvelu.pate.date :refer :all]
            [midje.sweet :refer :all]
            [sade.date :as date]))

(defn work-day
  ([id from]
   (work-day id from from))
  ([id from to]
   (fact {:midje/description (format "%s next work day: %s -> %s" id from to)}
     (-> (date/zoned-date-time from)
         forward-to-work-day
         (date/finnish-date))
     => to)))

(defn holiday [id s result]
  (fact {:midje/description (format "%s %s -> %s" id s result)}
    (holiday? (date/zoned-date-time s)) => result))

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

(facts "Parse and forward"
  (fact "Jump forward to weekday"
    (parse-and-forward (date/timestamp "29.3.2018") 3 :days true)
    => (date/timestamp "3.4.2018"))
  (fact "Accept holiday as valid result"
    (parse-and-forward (date/timestamp "29.3.2018") 3 :days false)
    => (date/timestamp "1.4.2018")))

(facts "tomorrow-or-later?"
  (let [today     (date/now)
        yesterday (.minusDays today 1)
        tomorrow  (.plusDays today 1)]
    (tomorrow-or-later? (date/timestamp today)) => false
    (tomorrow-or-later? (date/timestamp (date/end-of-day today))) => false
    (tomorrow-or-later? (date/timestamp yesterday)) => false
    (tomorrow-or-later? (date/timestamp tomorrow)) => true
    (tomorrow-or-later? (date/timestamp (date/start-of-day tomorrow))) => true))
