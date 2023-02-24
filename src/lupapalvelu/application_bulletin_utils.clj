(ns lupapalvelu.application-bulletin-utils
  (:require [sade.core :refer :all]
            [sade.date :as date]))

(defn day-range-ts
  "Local timestamp into two local timestamps representing the start and
  the end of the same day."
  [ts]
  [(date/timestamp (date/start-of-day ts))
   (date/timestamp (date/end-of-day ts))])

(defn bulletin-version-date-valid?
  "Verify that bulletin visibility date is valid at the given (or current) point in time"
  ([bulletin-version]
   (bulletin-version-date-valid? (now) bulletin-version))
  ([now {state :bulletinState :as bulletin-version}]
   (let [[day-start-ts day-end-ts] (day-range-ts now)
         [off-start-ts off-end-ts] (some-> bulletin-version
                                           :officialAt
                                           day-range-ts)]
     (case (keyword state)
       :proclaimed   (and (< (:proclamationStartsAt bulletin-version) day-end-ts)
                          (>= (:proclamationEndsAt bulletin-version) day-start-ts))
       :verdictGiven (and (< (:appealPeriodStartsAt bulletin-version) day-end-ts)
                          (>= (:appealPeriodEndsAt bulletin-version) day-start-ts))
       ;; Two week display window for finals.
       :final        (<= off-start-ts
                         day-end-ts
                         (date/timestamp (.plusDays (date/zoned-date-time off-end-ts) 14)))))))
