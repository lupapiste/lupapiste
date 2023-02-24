(ns sade.date
  "Date and datetime handling with `ZonedDateTime`."
  (:require [sade.env :as env]
            [sade.strings :as ss]
            [schema.core :as sc])
  (:import [clojure.lang MapEquivalence]
           [java.time ZoneId ZonedDateTime Instant]
           [java.time.format DateTimeFormatter DateTimeFormatterBuilder]
           [java.time.temporal TemporalField ChronoField ChronoUnit]
           [java.util Date]))

(set! *warn-on-reflection* true)

(def default-zone-id (ZoneId/of (or (env/value :timezone-id) "Europe/Helsinki")))
(def utc-zone-id     (ZoneId/of "UTC"))

(sc/defn pattern-formatter :- DateTimeFormatter
  "Takes a string `pattern` and creates a `DateTimeFormatter` with an override for the
  default zone."
  [pattern :- sc/Str]
  (.withZone (DateTimeFormatter/ofPattern pattern) default-zone-id))

(def ^:private fi-date                  (pattern-formatter "d.M.yyyy"))
(def ^:private fi-date-zero             (pattern-formatter "dd.MM.yyyy"))
(def ^:private fi-datetime              (pattern-formatter "d.M.yyyy H.mm"))
(def ^:private fi-datetime-seconds      (pattern-formatter "d.M.yyyy H.mm.ss"))
(def ^:private fi-datetime-zero         (pattern-formatter "dd.MM.yyyy HH.mm"))
(def ^:private fi-datetime-zero-seconds (pattern-formatter "dd.MM.yyyy HH.mm.ss"))

(defn- int-range
  "Integer range a, b (inclusive)."
  [a b t]
  (sc/constrained sc/Int #(<= a % b) (format "%s range is %s-%s (inclusive)" t a b)))

(def Hour   (int-range 0 23 "Hour"))
(def Minute (int-range 0 59 "Minute"))
(def Second (int-range 0 59 "Second"))

(sc/defschema Fields
  {:year                      sc/Int
   :month                     (int-range 1 12 "Month")
   :day                       (int-range 1 31 "Day")    ;; Defaults for the optionals:
   (sc/optional-key :hour)    Hour                      ;; 0
   (sc/optional-key :minute)  Minute                    ;; 0
   (sc/optional-key :second)  Second                    ;; 0
   (sc/optional-key :zone-id) ZoneId})                  ;; default-zone-id

(def ZonedOrNil    (sc/maybe ZonedDateTime))
(def FormatArg     (sc/cond-pre sc/Keyword DateTimeFormatter))
(def FormatOption
  "`:zero-pad` single digits are padded with zero.
   `:local` no offset.
   `:seconds` include seconds."
  (sc/enum :zero-pad :local :seconds))

(defprotocol ^:private Zoned
  (zoned-date-time ^java.time.ZonedDateTime ;; FQN type hint needed for the client code
    [x]
    "Returns `ZonedDateTime` instance or nil. Typically the result is in `default-zone-id`
     and throws on error. Possible types of `x`:

   - `ZonedDateTime` or nil are returned unchanged
   - Timestamp (milliseconds from epoch).
   - `Fields` map
   - String parameter is parsed with `zoned-parse` and the proper format is guessed."))

;; Predicate annot be partial due to extend-protocol below.
(def ZonedArg (sc/pred #(satisfies? Zoned %)))

(sc/defn with-time :- ZonedDateTime
  "Copy of `zoned` with the time fields (hours, minutes, seconds) set. The default value
  for each is 0. Nanoseconds are always zeroed. Nil-safe: nil if `zoned` resolves to nil."
  ([zoned :- ZonedArg
    h :- Hour
    m :- Minute
    s :- Second]
   (some-> (zoned-date-time zoned) (.withHour h) (.withMinute m) (.withSecond s)
           (.withNano 0)))
  ([zoned :- ZonedArg
    h :- Hour
    m :- Minute]
   (with-time zoned h m 0))
  ([zoned :- ZonedArg
    h :- Hour]
   (with-time zoned h 0 0))
  ([zoned :- ZonedArg]
   (with-time zoned 0 0 0)))

(sc/defn start-of-day :- ZonedDateTime
  "Returns `zoned` with time at the start of the day."
  [zoned :- ZonedArg]
  (with-time zoned))

(sc/defn end-of-day :- ZonedDateTime
  "Returns `zoned` with time as one millisecond to midnight."
  [zoned :- ZonedArg]
  (some-> zoned
          (with-time 23 59 59)
          (.with ChronoField/MILLI_OF_SECOND 999)))

(sc/defn with-zone :- ZonedDateTime
  "'Moves' `zoned` to the given timezone while maintaining the same instant/timestamp. If
  the `zone-id` is not given, the `default-zone-id` is used. `utc-zone-id` is a convenience
  definition for the UTC zone id."
  ([zoned :- ZonedArg zone-id :- ZoneId]
   (some-> (zoned-date-time zoned) (.withZoneSameInstant zone-id)))
  ([zoned]
   (with-zone zoned default-zone-id)))

(def formatter? (partial instance? DateTimeFormatter))

(def StrOrNil (sc/maybe sc/Str))

;; ----------------------
;; Formatting
;; ----------------------

(defmulti ^:private formatify (fn [_ fmt & _] fmt))

(defmethod formatify :default
  [^ZonedDateTime zoned fmt & _]
  (if (formatter? fmt)
    (.format zoned fmt)
    (throw (ex-info (str "Unsupported format: " fmt)
                    {:zoned zoned :fmt fmt}))))

(defmethod formatify :iso-datetime
  [^ZonedDateTime zoned _ {:keys [local]}]
  (-> (.withNano zoned 0) ; Otherwise included
      with-zone
      (formatify (if local
                   DateTimeFormatter/ISO_LOCAL_DATE_TIME
                   DateTimeFormatter/ISO_OFFSET_DATE_TIME))))

(defmethod formatify :iso-date
  [^ZonedDateTime zoned _ {:keys [local]}]
  (formatify (with-zone zoned)
             (if local
               DateTimeFormatter/ISO_LOCAL_DATE
               DateTimeFormatter/ISO_OFFSET_DATE)))

(defmethod formatify :rfc
  [^ZonedDateTime zoned _ _]
  (formatify (with-zone zoned utc-zone-id)
             DateTimeFormatter/RFC_1123_DATE_TIME))

(defmethod formatify :fi-datetime
  [^ZonedDateTime zoned _ {:keys [zero-pad seconds]}]
  (formatify zoned
             (cond
               (and zero-pad seconds) fi-datetime-zero-seconds
               zero-pad               fi-datetime-zero
               seconds                fi-datetime-seconds
               :else fi-datetime)))

(defmethod formatify :fi-date
  [^ZonedDateTime zoned _ {:keys [zero-pad]}]
  (formatify zoned
             (if zero-pad
               fi-date-zero
               fi-date)))

(sc/defn zoned-format :- StrOrNil
  "Format the given `zoned` with `fmt`. The latter is either a `DateTimeFormatter`
  instance or one of the pre-defined formatter keywords. Format `opts` are passed to the
  formatter implementation. Returns nil if `zoned` is nil or blank string.

  +-----------------+--------------+-------------------------------+
  | Keyword         | Option       | Example                       |
  +-----------------+--------------+-------------------------------+
  | `:iso-datetime` | -            | 2022-05-16T06:10:38+03:00     |
  |                 | `:local`     | 2022-05-16T06:10:38           |
  +-----------------+--------------+-------------------------------+
  | `:iso-date`     | -            | 2022-05-16+03:00              |
  |                 | `:local`     | 2022-05-16                    |
  +-----------------+--------------+-------------------------------+
  | `:rfc`          | -            | Mon, 16 May 2022 03:10:38 GMT |
  +-----------------+--------------+-------------------------------+
  | `:fi-datetime`  | -            | 16.5.2022 6.10                |
  |                 | `:zero-pad`  | 16.05.2022 06.10              |
  |                 | `:seconds`   | 16.5.2022 6.10.38             |
  |                 | both options | 16.05.2022 06.10.38           |
  +-----------------+--------------+-------------------------------+
  | `:fi-date`      | -            | 16.5.2022                     |
  |                 | `:zero-pad`  | 16.05.2022                    |
  +-----------------+--------------+-------------------------------+"
  [zoned :- ZonedArg fmt :- FormatArg & opts :- [FormatOption]]
  (some-> (zoned-date-time zoned)
          (formatify fmt (zipmap opts (cycle [true])))))

(defn iso-datetime
  "Convenience shorthand for ISO 8601 datetime format."
  [zoned & opts]
  (apply zoned-format zoned :iso-datetime opts))

(defn iso-date
  "Convenience shorthand for ISO 8601 date format."
  [zoned & opts]
  (apply zoned-format zoned :iso-date opts))

(defn- prune-offset
  "XML schema does not allow timezone offsets with seconds, so we simply remove that part if
  present. `s` is `iso-date` or `iso-datetime`."
  [s]
  (some->> s (re-matches #"(.*[Z\-+]\d\d:\d\d)(:\d+)?$") second))

(defn xml-datetime
  "Like `iso-datetime` but conforms to XML Schema.
  See https://www.w3.org/TR/xmlschema11-2/#dateTime
  "
  [zoned]
  (prune-offset (iso-datetime zoned)))

(defn xml-date
  "Like `iso-date` but conforms to XML Schema.
  See https://www.w3.org/TR/xmlschema11-2/#date"
  [zoned]
  (prune-offset (iso-date zoned)))

(defn finnish-date
  "Convenience shorthand for Finnish date format. Options:

  `:zero-pad` Day and month are always two digits and zero padded, if needed."
  [zoned & opts]
  (apply zoned-format zoned :fi-date opts))

(defn finnish-datetime
  "Convenience shorthand for Finnish datetime format.

  `:zero-pad` Day and month are always two digits and zero padded, if needed.
  `:seconds` Seconds are also displayed."
  [zoned & opts]
  (apply zoned-format zoned :fi-datetime opts))

;; ----------------------
;; Parsing
;; ----------------------

(sc/defn ^:private build-formatter :- DateTimeFormatter
  "Builds a formatter that supports parsing data and converts the results into the default
  timezone. `resets` are optional fields that have zero as the default value."
  [fmt :- DateTimeFormatter & resets :- [TemporalField]]
  (let [^DateTimeFormatterBuilder builder (reduce (fn [^DateTimeFormatterBuilder acc reset]
                                                    (.parseDefaulting acc reset 0))
                                                  (.append (DateTimeFormatterBuilder.) fmt)
                                                  resets)]
    (-> builder (.toFormatter) (.withZone default-zone-id))))

(def ^:private iso-date-parser (build-formatter DateTimeFormatter/ISO_LOCAL_DATE
                                                ChronoField/NANO_OF_DAY))
(def ^:private iso-datetime-parser (build-formatter DateTimeFormatter/ISO_DATE_TIME))

(def ^:private fi-parser
  "Flexible Finnish datetime parser definition where the time part is optional (and seconds
  optional within time). For example, the following strings could be successfully parsed:
  1.5.2022, 01.05.2022 13:40, 1.5.2022 3.7.1, 01.05.2022 03.07.01."
  (build-formatter (pattern-formatter "d.M.yyyy[ H.m[.s]]")
                   ChronoField/HOUR_OF_DAY
                   ChronoField/MINUTE_OF_HOUR
                   ChronoField/SECOND_OF_MINUTE
                   ChronoField/NANO_OF_SECOND))

(def ^:private iso-date-regex
  "We assume that the date is fixed regardless of timezone/offset. So we simply ignore it
and only use the date part."
  #"(^\d+-\d+-\d+)([Zz\-+].*)?$")

(defmulti ^:private parse (fn [_ fmt & _] fmt))

(defmethod parse :default
  [s fmt]
  (if (formatter? fmt)
    (ZonedDateTime/parse s fmt)
    (throw (ex-info (format "Cannot parse '%s'. Unsupported formatter: " fmt)
                    {:s s :fmt fmt}))))

(defmethod parse :fi
  [s _]
  ;; We support both . and : time separators and tolerate extra whitespace between date
  ;; and time parts.
  (let [[date time] (ss/split s #"\s+" 2)]
    (cond-> date
      time (str " " (ss/replace time ":" "."))
      true (parse fi-parser))))

(defmethod parse :fi-date
  [s _]
  (if (re-matches #"^\d+\.\d+\.\d+$" s)
    (parse s fi-parser)
    (throw (ex-info (format "Cannot parse '%s' as a Finnish date." s) {:s s}))))

(defmethod parse :iso-date
  [s _]
  ;; We only use the date part and set the time to the start of the day.
  (or (some-> (re-matches iso-date-regex s)
              second
              (parse iso-date-parser)
              start-of-day)
      (throw (ex-info (format "Cannot parse '%s' as an ISO date." s) {:s s}))))

(defmethod parse :iso-datetime
  [s _]
  (parse s iso-datetime-parser))

(defmethod parse :timestamp
  [s _]
  (zoned-date-time (Long/parseLong s)))

(def ^:private regexes {:fi           #"^\d+\.\d+\.\d+( .+)?$"
                        :iso-date     iso-date-regex
                        :iso-datetime #"^\d+-\d+-\d+T\d+.+$"
                        :timestamp    #"^-?\d+$"})

(defn- guess-fmt [s]
  (when-let [s (some-> s ss/trim ss/blank-as-nil)]
    (or (some (fn [[k v]]
                (when (re-matches v s)
                  k))
              regexes)
        (throw (ex-info (str "Unknown format, cannot be parsed: " s)
                        {:s s})))))

(sc/defn zoned-parse :- ZonedOrNil
  "Parse the given `s` with `fmt`. The latter is either a `DateTimeFormatter` instance or
  one of the pre-defined formatter keywords. If `fmt` is not given the correct parser is
  guessed based on the input string contents. Returns nil for blank or nil `s`.

  When a prefefined (keyword) format/parser succeeds the resulting `ZonedDateTime` is in
  the default timezone (typically Finnish). The supported keywords are `:fi`, `:fi-date`,
  `:iso-datetime` and `:iso-date`.

  +-----------------+-----------------------------------------------------------------+
  | `:fi`           | Finnish datetime, with optional time part (with or without      |
  |                 | seconds).                                                       |
  +-----------------+-----------------------------------------------------------------+
  | `:fi-date`      | Finnish date. Does not allow time part (always zero/midnight).  |
  +-----------------+-----------------------------------------------------------------+
  | `:iso-datetime` | ISO 8601 date, where parts after minutes are optional. If       |
  |                 | timezone/offset is not given, default timezone is used.         |
  +-----------------+-----------------------------------------------------------------+
  | `:iso-date`     | ISO 8601 date parte (year-month-day). However, the              |
  |                 | offset/timezone part is always ignored and the default timezone |
  |                 | is used. Time is zero/midnight.                                 |
  +-----------------+-----------------------------------------------------------------+
  |  `:timestamp`   | String representation of a timestamp (milliseconds from epoch). |
  +-----------------+-----------------------------------------------------------------+"
  ([s :- StrOrNil fmt :- FormatArg]
   (some-> (ss/trim s) ss/blank-as-nil (parse fmt)))
  ([s :- StrOrNil]
   (some-> (ss/blank-as-nil s) (zoned-parse (guess-fmt s)))))

(sc/defn reformat :- StrOrNil
  "Converts (parses and formats) one textual representation to another. If only one format
  is given, the source format is guessed based on the input string.  Returns nil for blank
  or nil `s`."
  ([s :- StrOrNil fmt-from :- FormatArg fmt-to :- FormatArg]
   (some-> (zoned-parse s fmt-from) (zoned-format fmt-to)))
  ([s :- StrOrNil fmt-to :- FormatArg]
   (some-> (ss/blank-as-nil s) (reformat (guess-fmt s) fmt-to))))

(defn- try-parse [& args]
  (try
    (boolean (apply zoned-parse args))
    (catch Exception _
      false)))

(sc/defn format? :- sc/Bool
  "True if the given `s` can be parsed with `fmt`. The arguments are resolved in the same way as with
  `zoned-parse`. Thus, if no format is given, the result is true, if `s` can be parse with
  a guessed format."
  ([ fmt :- FormatArg s :- StrOrNil]
   (try-parse s fmt))
  ([s :- StrOrNil]
   (try-parse s)))

(def xml-date? "Convenience predicate for `:iso-date`" (partial format? :iso-date))
(def xml-datetime? "Convenience predicate for `:iso-datetime`" (partial format? :iso-datetime))

;; ----------------------
;; Creation
;; ----------------------

(extend-protocol Zoned
  ZonedDateTime
  (zoned-date-time [zdt] zdt)

  nil
  (zoned-date-time [_] nil)

  Number
  (zoned-date-time [timestamp]
    (some-> timestamp
            (Instant/ofEpochMilli) ;; Works with all number types
            zoned-date-time))

  MapEquivalence
  (zoned-date-time [arg]
    (let [{:keys [hour minute second zone-id]} arg]
      (ZonedDateTime/of (:year arg) (:month arg) (:day arg)
                        (or hour 0) (or minute 0) (or second 0)
                        0 ;; We ignore nanoseconds.
                        (or zone-id default-zone-id))))

  String
  (zoned-date-time [s] (zoned-parse s))

  Instant
  (zoned-date-time [instant]
    (ZonedDateTime/ofInstant instant default-zone-id))

  Date
  (zoned-date-time [date]
    (zoned-date-time (.toInstant date))))

(sc/defn timestamp :- (sc/maybe sc/Int)
  "Returns timestamp (milliseconds from epoch) that corresponds to given
  `zoned`. Nil-safe: returns nil on nil or blank string.."
  [zoned :- ZonedArg]
  (some-> (zoned-date-time zoned) (.toInstant) (.toEpochMilli)))

(sc/defn now :- ZonedDateTime
  "Convenience function for getting a `ZonedDateTime` for the current time."
  ([zone-id :- ZoneId]
   (ZonedDateTime/now zone-id))
  ([]
   (now default-zone-id)))

(defn today
  "`start-of-day` for `now`."
  ([zone-id] (start-of-day (now zone-id)))
  ([] (start-of-day (now))))

(defn- chrono-units
  "Must fail for single unit."
  [units]
  (case units
    :seconds ChronoUnit/SECONDS
    :minutes ChronoUnit/MINUTES
    :hours   ChronoUnit/HOURS
    :days    ChronoUnit/DAYS
    :weeks   ChronoUnit/WEEKS
    :months  ChronoUnit/MONTHS
    :years   ChronoUnit/YEARS))

(defn- chrono-unit
  "Must fail for plural units."
  [unit]
  (case unit
    :second ChronoUnit/SECONDS
    :minute ChronoUnit/MINUTES
    :hour   ChronoUnit/HOURS
    :day    ChronoUnit/DAYS
    :week   ChronoUnit/WEEKS
    :month  ChronoUnit/MONTHS
    :year   ChronoUnit/YEARS))

(def Units (sc/enum :seconds :minutes :hours :days :weeks :months :years))
(def Unit  (sc/enum :second :minute :hour :day :week :month :year))

(defn- add [zoned ^ChronoUnit chrunit ^Long n]
  (.plus ^ZonedDateTime (zoned-date-time zoned)  n chrunit))

(sc/defn plus :- ZonedDateTime
  "Returns new zoned with the a time period added. Two argument version is a convenience
  shortcut for adding one unit."
  ([zoned :- ZonedArg units :- Units n :- sc/Int]
   (add zoned (chrono-units units) n))
  ([zoned :- ZonedArg unit :- Unit]
   (add zoned (chrono-unit unit) 1)))

(sc/defn minus :- ZonedDateTime
  "Returns new zoned with the a time period subtracted. Two argument version is a convenience
  shortcut for subtracting one unit."
  ([zoned :- ZonedArg units :- Units n :- sc/Int]
   (add zoned (chrono-units units) (* -1 n)))
  ([zoned :- ZonedArg unit :- Unit]
   (add zoned (chrono-unit unit) -1)))

(defn- resolve-zds?
  "`op-fn` is the comparison function that takes two `ZonedDateTime` instances as parameters
  and returns boolean. Individual `zd1`, `zd2` and `zds` list are `ZonedArg`s."
  [op-fn zd1 zd2 zds]
  (->> (cons zd2 zds)
       (reduce (fn [zoned zd]
                     (let [zd (zoned-date-time zd)]
                       (if (op-fn zoned zd)
                         zd
                         (reduced false))))
               (zoned-date-time zd1))
       boolean))

(sc/defn after? :- sc/Bool
  "True if `zd1` is after `zd2`, which is after the first `zds`, which in turn is after
  the next one and so on."
  [zd1 :- ZonedArg zd2 :- ZonedArg & zds :- [ZonedArg]]
  (resolve-zds? #(.isAfter ^ZonedDateTime %1 ^ZonedDateTime %2) zd1 zd2 zds))

(sc/defn before? :- sc/Bool
  "True if `zd1` is before `zd2`, which is before the first `zds`, which in is before the
  next one and so on."
  [zd1 :- ZonedArg zd2 :- ZonedArg & zds :- [ZonedArg]]
  (resolve-zds? #(.isBefore ^ZonedDateTime %1 ^ZonedDateTime %2) zd1 zd2 zds))

(sc/defn eq? :- sc/Bool
  "True if each `zoned` denotes the same timestamp. Thus, the granularity is milliseconds
  and timezones/offsets do not matter."
  [zoned :- ZonedArg & zds :- [ZonedArg]]
  (->> (cons zoned zds)
       (map timestamp)
       (apply =)))
