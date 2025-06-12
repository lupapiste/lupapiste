(ns lupapalvelu.statistics.core
  "Core utilities for application processing time statistics.

  Provides functions for:
  - Determining if an application qualifies for processing time tracking
  - Calculating original deadlines based on submission date and permit complexity
  - Extracting verdict timestamps that mark the end of processing
  - Computing cutoff timestamps for processing period calculations

  Processing time is tracked with millisecond resolution internally, though end users
  typically see the data aggregated by full days.

  All deadline calculations respect Finnish work day conventions, excluding
  weekends and public holidays as defined in the pate-date namespace."
  (:require [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.pate.date :as pate-date]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]
            [swiss.arrows :refer :all])
  (:import [java.time ZonedDateTime]))

(set! *warn-on-reflection* true)

(sc/defn verdict-ts :- (sc/maybe ssc/Timestamp)
  "Returns the timestamp when the processing ended for the application.
  This is not the actual/official verdict date, but when the verdict was published.

  For Lupapiste verdicts, returns the published timestamp.
  For legacy verdicts, returns the earliest paatospvm (decision date) timestamp.
  Returns nil if no verdict exists."
  [application]
  (when-let [v (vif/earliest-published-verdict (util/mmap application))]
    (if (vc/lupapiste-verdict? v)
      (vc/verdict-published v)
      (some->> v
               :paatokset
               (mapcat :poytakirjat)
               (keep :paatospvm)
               seq
               (apply min)))))

(sc/defn cutoff-ts :- ssc/Timestamp
  "Returns the end timestamp of the processing calculation period.

  If the application has a verdict, returns the verdict timestamp.  Otherwise, returns the
  current time (plus one minute buffer to make sure that the current time is included) as
  a timestamp.  The timestamp represents the cutoff point for processing time
  calculations.

  As a sanity check, the cutoff cannot be before the application submitted timestamp."
  [{:keys [submitted] :as application}]
  {:pre [submitted]}
  (-> (or (verdict-ts application) (date/plus (date/now) :minute))
      date/timestamp
      (max submitted)))

(sc/defn original-deadline :- ZonedDateTime
  "Calculates the original deadline for an application by adding the specified
  number of months to the submitted date.

  The deadline is adjusted to the next work day if it falls on a weekend or
  public holiday, following Finnish work day conventions.

  Parameters:
  - application: Map with :submitted timestamp
  - months: Number of months to add to the submitted date

  Returns: ZonedDateTime representing the deadline"
  [{:keys [submitted]} months :- sc/Int]
  {:pre [submitted]}
  (pate-date/forward-to-work-day (date/plus submitted :months months)))

(defn- submitted-after-start-date?
  "Returns true if the application meets the start date criteria for processing statistics.

  An application is included if ANY of the following are true:
  - No start date is configured in :statistics-processing.start-date
  - The application has not been submitted yet (nil submitted)
  - The application was submitted after the configured start date

  This allows filtering applications to only include those submitted after a certain
  date when the statistics processing feature was enabled."
  [{:keys [submitted]}]
  (let [start-date (env/value :statistics-processing :start-date)]
    (or (nil? start-date)
        (nil? submitted)
        (date/after? submitted start-date))))

(defn- processing-operation?
  "Returns true if any of the application's operations has the :processing metadata flag.

  This identifies applications that should be included in processing time statistics.
  Operations with :processing true are typically building permits that require
  processing time tracking (e.g., pientalo, kerrostalo-rivitalo).

  Excluded operations include foreman applications, notices, and paper permits."
  [application]
  (boolean (some #(ops/get-operation-metadata (:name %) :processing)
                 (app-utils/get-operations application))))

(defn unsupported-change-permit?
  "True if `application` is a change permit that SHOULD NOT be included in the
  processing. Otherwise false (not a change permit or should be processed). A change
  permit is unsupported if its source application (or any of its link permits,
  if source application not defined) does not have processing information."
  [application]
  (let [status-key :_stats.processing.status
        query      {status-key {$exists true}}
        projection [status-key]]
    (boolean
      (when (util/=as-kw :muutoslupa (:permitSubtype application))
        (not (if (app-utils/source-application-info application :change)
               (app-utils/source-application application
                                             :kind :change
                                             :projection projection
                                             :query query)
               (app-utils/application-link-permits (:id application)
                                                   :projection projection
                                                   :query query)))))))

(defn processing-application?
  "Determines if an application should be included in processing time statistics.

  An application is included if ALL of the following criteria are met:
  - The :statistics-processing feature flag is enabled
  - The application meets the start date criteria (see `submitted-after-start-date?`)
  - The permit type is R (building permit)
  - The application is not a converted Facta import
  - The application is not an info request
  - At least one operation has the :processing flag
  - The application state is post-submission (submitted, sent, complementNeeded, etc.)
  - If the application is change permit (muutoslupa), its source application supports
    processing.

  Optional keyword arguments:
  - `:submitted` - If provided, requires that the application has a submitted timestamp

  This function filters applications to ensure only relevant building permits
  with proper processing operations are included in statistics calculations."
  [{:keys [permitType submitted state] :as application} & ks]
  (let [{must-be-submitted? :submitted} (zipmap ks (repeat true))]
    (and
      (env/feature? :statistics-processing)
      (submitted-after-start-date? application)
      (util/=as-kw permitType :R)
      (not (:facta-imported application))
      (not (:infoRequest application))
      (processing-operation? application)
      (util/includes-as-kw? states/post-submitted-states state)
      (not (and must-be-submitted? (nil? submitted)))
      (not (unsupported-change-permit? application)))))
