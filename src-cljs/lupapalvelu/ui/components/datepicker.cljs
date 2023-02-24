(ns lupapalvelu.ui.components.datepicker
  (:require ["moment" :as moment]
            ["moment/locale/fi"]
            ["moment/locale/sv"]
            ["react-day-picker/DayPicker" :default DayPicker]
            ["react-day-picker/moment" :default MomentLocaleUtils]
            [camel-snake-kebab.core :refer [->camelCaseString]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [lupapalvelu.ui.common :as common]
            [sade.shared-strings :as ss]))

(defn ->finnish-str [^js date]
  (.format ^js (moment date) "D.M.YYYY"))

(defn- opts-transform [opts]
  (clj->js (transform-keys ->camelCaseString opts)))

(defn make-date
  [source]
  (cond
    (inst? source)              {:date source}
    (ss/blank? source)          nil
    (js/moment.isMoment source) {:date (.toDate ^js source)}
    (string? source)            (if-let [m (js/util.toMoment source "fi")]
                                  {:date (.toDate m)}
                                  {:error :error.invalid-date})
    (int? source)               {:date (js/Date. source)}))

(defn datestring
  ([date]
   (when-not (ss/blank? date)
     (js/util.formatMoment (js/moment date) "fi"))))

(defn day-picker [{:keys [selected-days] :as options}]
  (js/React.createElement DayPicker
                          (opts-transform (merge {:locale            (common/get-current-language)
                                                  :locale-utils      MomentLocaleUtils
                                                  :first-day-of-week 1 ;; Monday
                                                  :show-week-numbers true
                                                  :initial-month (-> [selected-days] flatten first)}
                                                 options))))

(defn day-begin-ts
  "Time zero ms timestamp (midnight) in local browser time.
  `date` is `js/Date` instance. Nil returns nil."
  [date]
  (when date
    (.setHours date 0)
    (.setMinutes date 0)
    (.setSeconds date 0)
    (.setMilliseconds date 0)
    (.valueOf date)))

(defn day-end-ts
  "Time 23:59:59:999 ms timestamp in local browser time.
  `date` is `js/Date` instance. Nil returns nil."
  [date]
  (when date
    (.setHours date 23)
    (.setMinutes date 59)
    (.setSeconds date 59)
    (.setMilliseconds date 999)
    (.valueOf date)))
