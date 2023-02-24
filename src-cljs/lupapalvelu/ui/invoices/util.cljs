(ns lupapalvelu.ui.invoices.util
  (:require [clojure.string :refer [blank? join]]
            [cljs-time.coerce :as tcoerce]
            [cljs-time.core :as time]
            [cljs-time.format :as tformat]))

(def invoice-date-formatter (tformat/formatter "dd.MM.yyyy"))
(def finnish-date-formatter (tformat/formatter "d.M.yyyy"))

(defn- set-date-end [datetime]
  (let [datetime (.clone datetime)]
    (doto datetime
      (.setHours 23)
      (.setMinutes 0)
      (.setSeconds 0)
      (.setMilliseconds 0))))

(defn ->timestamp [invoice-date-formatter-formatted-date]
  (tcoerce/to-long (tformat/parse invoice-date-formatter invoice-date-formatter-formatted-date)))

(defn finnish-date->timestamp [finnish-date-formatter-formatted-date]
  (tcoerce/to-long (tformat/parse finnish-date-formatter finnish-date-formatter-formatted-date)))

(defn ->date [timestamp]
  (tcoerce/from-long timestamp))

(defn ->finnish-date-str
  "We use `js/util.finnishDate` in order to make sure that the local time is consistently used."
  [timestamp]
  (js/util.finnishDate timestamp))

(defn days-between-dates [finnish-start-date finnish-end-date]
  (let [start-date-object (time/at-midnight (tformat/parse finnish-date-formatter finnish-start-date))
        end-date-object (set-date-end (tformat/parse finnish-date-formatter finnish-end-date))
        interval (time/interval start-date-object end-date-object)]
    (.round js/Math (/ (time/in-hours interval) 24)))) ;;Because sometimes you just want same dates to yield count of 1 day.

(defmulti ->date-str (fn [x] (cond
                               (nil? x)       :default
                               (number? x)    :timestamp
                               (time/date? x) :date)))

(defmethod ->date-str :timestamp [timestamp]
  (tformat/unparse invoice-date-formatter (->date timestamp)))

(defmethod ->date-str :date [date]
  (tformat/unparse invoice-date-formatter date))

(defmethod ->date-str :default [_]
  nil)

(defn- split-on-space [s]
  (clojure.string/split s #"\s"))

(defn strip-whitespace [s]
  (->> (split-on-space s)
       (remove blank?)
       (join "")))

(defn num? [val]
  (let [val (strip-whitespace val)]
    (when-not (blank? val)
      (not (js/isNaN val)))))

(defn ->int [val]
  (when (num? val)
    (js/parseInt val)))

(defn comma->dot [val]
  (when (and val (string? val))
    (clojure.string/replace val #"," ".")))

(defn ->float [val]
  (let [val-with-dot (comma->dot val)]
    (when (num? val-with-dot)
      (js/parseFloat val-with-dot))))

(defn sort-by-fn-nils-last [f items]
  (sort-by (fn [i] (or (f i) (.-MAX_SAFE_INTEGER js/Number))) items))

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
                    (not (empty? start))
                    (not (empty? end))))))
        (vals dates-map) ))

(defn all-periods-set?
  "Checks if all no billing periods have both start and end value or are empty"
  [no-billing-periods]
  (not (check-dates-missing-values? no-billing-periods)))

(defn should-add-new-no-billing-entry? [no-billing-periods]
  (every? (fn [{:keys [start end]}]
            (and
             (not (empty? start))
             (not (empty? end))))
          (vals no-billing-periods)))
