(ns lupapalvelu.ui.date
  (:require [goog.date :as good]
            [sade.strings :as ss]))

(defonce ^:private fi-date-format (goog.i18n.DateTimeFormat. "d.M.yyyy"))
(defonce ^:private fi-time-format (goog.i18n.DateTimeFormat. "HH:mm:ss"))

(defn- formatify [fmt ts]
  (some->> ts
           good/DateTime.fromTimestamp
           (.format fmt)))

(def finnish-date
  "Returns Finnish datestring for the given `timestamp`. Nil-safe."
  (partial formatify fi-date-format))

(def finnish-time
  "Returns timestring HH:MM:SS for the given `timestamp`. Nil-safe."
  (partial formatify fi-time-format))

(defn finnish-datetime
  "Combines Finnish datestring with timestring for the given `timestamp`. The default
  `separator` is ', '."
  ([timestamp separator]
   (ss/join-non-blanks separator [(finnish-date timestamp)
                                  (finnish-time timestamp)]))
  ([timestamp] (finnish-datetime timestamp ", ")))

(defn now
  "Current timestamp (ms from epoch)."
  []
  (.valueOf (good/DateTime.)))

(defn start-of-day
  "Truncates given `timestamp` to the start of the day. Nil safe."
  [timestamp]
  (when-let [dt (some-> timestamp good/DateTime.fromTimestamp)]
    (doto dt
      (.setHours 0)
      (.setMinutes 0)
      (.setSeconds 0)
      (.setMilliseconds 0))
    (.valueOf dt)))
