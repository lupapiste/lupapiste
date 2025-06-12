(ns lupapalvelu.application-bulletins-test-util
  (:require [midje.sweet :refer :all]
            [sade.date :as date]))

(defn randstamp
  "Random timestamp within the given day. The range is from 00:00 to
  23:59. Given datestring is in the Finnish format."
  [datestring]
  (-> (date/zoned-date-time datestring)
      (date/with-time (rand-int 24) (rand-int 60) (rand-int 60))
      (date/timestamp)))

(defn make-version [state start-ts & [end-ts]]
  (cond-> {:bulletinState (name state)}
    (= state :proclaimed) (assoc :proclamationStartsAt start-ts
                                 :proclamationEndsAt end-ts)
    (= state :verdictGiven) (assoc :appealPeriodStartsAt start-ts
                                   :appealPeriodEndsAt end-ts)
    (= state :final) (assoc :officialAt start-ts)))
