(ns lupapalvelu.application-bulletin-utils-test
  (:require [lupapalvelu.application-bulletin-utils :refer :all]
            [lupapalvelu.application-bulletins-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]))

(fact day-range-ts
  (let [ts 1549383144705
        range-ts (day-range-ts ts)]
    (date/finnish-datetime ts) => "5.2.2019 18.12"
    range-ts => [1549317600000 1549403999999]
    (map date/finnish-datetime range-ts)
    => ["5.2.2019 0.00" "5.2.2019 23.59"])
  (let [[s-ts e-ts] (day-range-ts (now))]
    (< s-ts (now) e-ts) => true))

(fact randstamp
  (date/finnish-date (randstamp "1.2.2012")) => "1.2.2012"
  (date/finnish-date (randstamp "21.12.2012")) => "21.12.2012"
  (date/finnish-date (randstamp "04.05.2002")) => "4.5.2002")

(facts bulletin-version-date-valid?
  (fact "proclaimed"
    (let [v (make-version :proclaimed
                          (randstamp "4.2.2019")
                          (randstamp "4.3.2019"))]
      (bulletin-version-date-valid? (randstamp "4.2.2019") v) => true
      (bulletin-version-date-valid? (randstamp "20.2.2019") v) => true
      (bulletin-version-date-valid? (randstamp "4.3.2019") v) => true
      (bulletin-version-date-valid? (randstamp "3.2.2019") v) => false
      (bulletin-version-date-valid? (randstamp "5.3.2019") v) => false
      (bulletin-version-date-valid?  (:proclamationStartsAt v) v) => true
      (bulletin-version-date-valid?  (:proclamationEndsAt v) v) => true))
  (fact "verdictGiven"
    (let [v (make-version :verdictGiven
                          (randstamp "4.2.2019")
                          (randstamp "4.3.2019"))]
      (bulletin-version-date-valid? (randstamp "4.2.2019") v) => true
      (bulletin-version-date-valid? (randstamp "20.2.2019") v) => true
      (bulletin-version-date-valid? (randstamp "4.3.2019") v) => true
      (bulletin-version-date-valid? (randstamp "3.2.2019") v) => false
      (bulletin-version-date-valid? (randstamp "5.3.2019") v) => false
      (bulletin-version-date-valid?  (:appealPeriodStartsAt v) v) => true
      (bulletin-version-date-valid?  (:appealPeriodEndsAt v) v) => true))
  (fact "final"
    (let [v (make-version :final (randstamp "4.2.2019"))]
      (bulletin-version-date-valid? (randstamp "4.2.2019") v) => true
      (bulletin-version-date-valid? (randstamp "14.2.2019") v) => true
      (bulletin-version-date-valid? (randstamp "18.2.2019") v) => true
      (bulletin-version-date-valid? (randstamp "19.2.2019") v) => false
      (bulletin-version-date-valid? (randstamp "5.3.2019") v) => false
      (bulletin-version-date-valid?  (:officialAt v) v) => true)))
