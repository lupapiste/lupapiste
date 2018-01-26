(ns lupapalvelu.application-bulletin-utils
  (:require [sade.core :refer :all]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))

(defn bulletin-version-date-valid?
  "Verify that bulletin visibility date is valid at the given (or current) point of time"
  ([bulletin-version]
   (bulletin-version-date-valid? (now) bulletin-version))
  ([now {state :bulletinState :as bulletin-version}]
   (case (keyword state)
     :proclaimed (and (< (:proclamationStartsAt bulletin-version) now)
                        (> (:proclamationEndsAt bulletin-version) now))
     :verdictGiven (and (< (:appealPeriodStartsAt bulletin-version) now)
                        (> (:appealPeriodEndsAt bulletin-version) now))
     :final (and (< (:officialAt bulletin-version) now)
                 (> (tc/to-long (t/plus (tc/from-long now) (t/days 14))))))))