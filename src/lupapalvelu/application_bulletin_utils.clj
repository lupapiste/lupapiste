(ns lupapalvelu.application-bulletin-utils
  (:require [lupapalvelu.bulletin-report.core :as report]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]))

(set! *warn-on-reflection* true)

(defn day-range-ts
  "Local timestamp into two local timestamps representing the start and
  the end of the time window, starting from midnight before the first date and
  ending at midnight of the last date."
  ([ts]
   (day-range-ts ts ts))
  ([start-ts end-ts]
   [(date/timestamp (date/start-of-day start-ts))
    (date/timestamp (date/end-of-day end-ts))]))

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

(defn bulletin-list-item
  "Transforms the bulletin data into a form useful for listing bulletins in the frontend.
  The text is e.g. \"§123 11.02.2024 - 12.03.2025\" with the section and start/end times all optional"
  [bulletin lang]
  (let [{:keys [id section start-date end-date]} (report/bulletin-info bulletin lang)
        section-prefix                           (some->> section (str "§"))
        time-window-suffix                       (when (or start-date end-date)
                                                   (->> [start-date end-date]
                                                        (map #(some-> % (date/finnish-date :zero-pad)))
                                                        (apply format "%s - %s")))]
    {:id   id
     :text (ss/join-non-blanks " " [section-prefix time-window-suffix])}))
