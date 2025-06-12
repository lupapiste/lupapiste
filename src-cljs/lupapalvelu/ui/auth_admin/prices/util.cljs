(ns lupapalvelu.ui.auth-admin.prices.util
  (:require [cljs-time.core :as time]
            [cljs-time.format :as tformat]))

(def finnish-date-formatter (tformat/formatter "d.M.yyyy"))

(defn- set-date-end [datetime]
  (let [datetime (.clone datetime)]
    (doto datetime
      (.setHours 23)
      (.setMinutes 0)
      (.setSeconds 0)
      (.setMilliseconds 0))))

(defn days-between-dates [finnish-start-date finnish-end-date]
  (let [start-date-object (time/at-midnight (tformat/parse finnish-date-formatter finnish-start-date))
        end-date-object (set-date-end (tformat/parse finnish-date-formatter finnish-end-date))
        interval (time/interval start-date-object end-date-object)]
    (.round js/Math (/ (time/in-hours interval) 24))))

(defn get-next-no-billing-period-id
  "Find the greatest current id (= sequence number) x and return (inc x)"
  [no-billing-periods]
  (let [max-value (apply max (map (fn [k] (-> k name js/parseInt)) (keys no-billing-periods)))]
    (keyword (str (inc (or max-value 0))))))

(defn- check-dates-missing-values? [dates-map]
  (some (fn [{:keys [start end]}]
          (not (or (and (empty? start)
                        (empty? end))
                   (and
                     (not-empty start)
                     (not-empty end)))))
        (vals dates-map) ))

(defn all-periods-set?
  "Checks if all no billing periods have both start and end value or are empty"
  [no-billing-periods]
  (not (check-dates-missing-values? no-billing-periods)))

(defn should-add-new-no-billing-entry? [no-billing-periods]
  (every? (fn [{:keys [start end]}]
            (and
              (not-empty start)
              (not-empty end)))
          (vals no-billing-periods)))
