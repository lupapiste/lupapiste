(ns lupapalvelu.imports.util
  (:require [lupapalvelu.imports.schemas :as im-sc]
            [lupapalvelu.invoices.util :as inv-util]
            [lupapalvelu.logging :as log]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

(def ^:private milliseconds-in-day (.toMillis (Duration/ofDays 1)))

(defn excel-date->zoned-date-time
  "Adapted from `org.dhatim.fastexcel.reader.Cell/convertToDate`
  implementation. `excel-date` can be either number or string. If latter, it is parsed
  with `parse-double`. If the parsing fails the return value is nil. If `window1904?` is
  true (default false), the 1904 windowing is used. As a sanity check, the accepted day
  range is [0, 100000] inclusive. The range end corresponds to excel year 4637. The idea
  is to differentiate days from millisecond timestamps.

  An alternative approach would be to use Apache POI DateUtil, but it does not resolve the
  turn of the previous century correctly."
  ([excel-date window1904?]
   (try
     (let [double-date (util/pcond-> excel-date
                         string? parse-double)]
       (cond
         (nil? double-date)              (log/debugf "Parsed excel date %s is nil." excel-date)
         (not (<= 0 double-date 100000)) (log/debugf "%s out of excel date range." excel-date)
         :else
         (let [whole-days              (int (Math/floor double-date))
               ms-in-day               (long (+ (* (- double-date whole-days) milliseconds-in-day) 0.5))
               [whole-days ms-in-day]  (if (= ms-in-day milliseconds-in-day)
                                         [(inc whole-days) 0]
                                         [whole-days ms-in-day])
               [start-year day-adjust] (cond
                                         window1904?       [1904 0]
                                         (< whole-days 61) [1900 -1]
                                         :else             [1900 -2])]
           (-> (date/zoned-date-time {:year start-year :month 1 :day 1})
               (date/plus :days (+ whole-days day-adjust))
               (.plusNanos (* ms-in-day 1000000))))))
     (catch Exception e
       (log/debugf "Bad excel date %s: %s" excel-date (ex-message e)))))
  ([excel-date] (excel-date->zoned-date-time excel-date false)))

(sc/defn make-user-ts :- im-sc/user-ts
  ([user ts]
   {:ts      (or ts (date/timestamp (date/now)))
    :user-id (:id user user)})
  ([{:keys [user created] :as user-or-command}]
   (if user
     (make-user-ts user created)
     (make-user-ts user-or-command nil))))

(defn make-title
  "Returns map with `:fi` and `:sv` keys. The value for both is the trimmed `s`."
  [s]
  (zipmap [:fi :sv] (repeat (ss/trim s))))

(defmulti
  ^{:private true
    :doc     "Parses the given value according to the type. Throws, if parsing fails."}
  parse-cell-value! (fn [_cfg type _value _ks] (keyword type)))

(defmethod parse-cell-value! :default
  [_ t v _]
  (throw (ex-info (str "Unsupported type: " t) {:type t :value v})))

(defmethod parse-cell-value! :text
  [_ _ v _]
  (ss/blank-as-nil v))

(defmethod parse-cell-value! :date
  [{:keys [rowdata]} _ v ks]
  (some-> (or (when (some #{:excel} ks)
                (excel-date->zoned-date-time v (:window1904 rowdata)))
              (date/zoned-date-time v))
          date/start-of-day
          date/timestamp))

(defmethod parse-cell-value! :number
  [_ _ v _]
  ;; Scaling is nil and integer safe
  (inv-util/scale (inv-util/parse-number v)))

(defmethod parse-cell-value! :application-id
  [_ _ v _]
  (some->> v ss/blank-as-nil ss/upper-case ssc/valid-application-id!))

(defmethod parse-cell-value! :backend-id
  [_ _ v _]
  (ss/blank-as-nil v))

(defn parse-cell
  "Parses string `s` according to the configuration `cfg` and `column-type` (text, date,
  number, ...). Currently, the only supported extra keyword (`ks`) is `:excel`. If given,
  and `column-type` is date, the `s` is interpreted as an Excel date (days from
  epoch). Returns map with either `:value` or `:error` key."
  [cfg column-type s & ks]
  {:pre [(string? s)]}
  (try
    {:value (parse-cell-value! cfg column-type (ss/trim s) ks)}
    (catch Exception e
      (log/debug (ex-message e))
      {:error column-type})))

(defn with-modified
  "Adds `im-sc/user-ts` timestamp for `k` (default `:modified`) to given `mongo-updates`."
  ([{:keys [created user]} k mongo-updates]
   (assoc-in mongo-updates [$set k] (make-user-ts user created)))
  ([command mongo-updates]
   (with-modified command :modified mongo-updates)))
