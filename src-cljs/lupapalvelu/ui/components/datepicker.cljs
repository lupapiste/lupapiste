(ns lupapalvelu.ui.components.datepicker
  (:require ["moment" :as moment]
            ["react-day-picker" :refer [DayPicker]]
            ["react-day-picker/locale/fi" :refer [fi]]
            ["react-day-picker/locale/sv" :refer [sv]]
            [lupapalvelu.ui.common :as common]
            [sade.strings :as ss]
            [goog.string :as goos]
            [reagent.core :as r]))

(defn ->finnish-str [^js date]
  (.format ^js (moment date) "D.M.YYYY"))

(defn make-date
  [source]
  (cond
    (inst? source)              {:date source}
    (ss/blank? source)          nil
    (js/moment.isMoment source) {:date (.toDate ^js source)}
    (string? source)            (let [m (js/util.toMoment source "fi")]
                                  (cond
                                    m {:date (.toDate m)}

                                    (re-matches #"[\-+]?\d+" source)
                                    {:date (js/Date. (goos/parseInt source))}

                                    :else {:error :error.invalid-date}))
    (int? source) {:date (js/Date. source)}))

(defn datestring
  ([date]
   (when-not (ss/blank? date)
     (js/util.formatMoment (js/moment date) "fi"))))

(defn day-picker [{:keys [selected] :as options}]
  (js/React.createElement DayPicker
                          (common/->js-options (merge {:mode              "single"
                                                       :className         (r/class-names "twp nobase"
                                                                                         (:className options))
                                                       :locale            (case (common/get-current-language)
                                                                            "fi" fi
                                                                            "sv" sv
                                                                            nil)
                                                       :first-day-of-week 1 ;; Monday
                                                       :show-week-number  true
                                                       :default-month     (-> [selected] flatten first)}
                                                      (dissoc options :className)))))

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
