(ns lupapalvelu.statistics.db
  "Storing processing information into mongo."
  (:require [com.climate.claypoole :as cp]
            [lupapalvelu.elasticsearch.query :as es-query]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.statistics.core :as stats]
            [lupapalvelu.statistics.entry :as stats-e]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [monger.operators :refer :all]
            [sade.core :refer [fail]]
            [sade.date :as date]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [java.time Duration Period]))

(set! *warn-on-reflection* true)

(def ^:private stats-key :_stats)

(def processing-path [stats-key :processing])

(def processing-projection (util/kw-path processing-path))

(defn- query-factory [& statuses]
  {:state                                      {$in states/post-submitted-states}
   (util/kw-path stats-key :processing.status) {$in (apply stats-sc/->db-statuses statuses)}})

(def processing-query (query-factory :running :delayed :no-complexity))

(def ^:private processing-calculation-projection
  "Every application field that is needed for calculating the processing."
  [processing-projection :state :submitted :pate-verdicts :verdicts
   :permitType :facta-imported :primaryOperation.name :secondaryOperations.name
   :infoRequest])

(def processing-query-es
  (es-query/must [(es-query/term :state (mapv name states/post-submitted-states))
                  (es-query/term (util/kw-path stats-key :processing.status)
                                 (stats-sc/->db-statuses :running :delayed :no-complexity))]))

(defn read-processing [application]
  (some-> application stats-key :processing stats-sc/->processing))

(defn processing+extras
  [{:keys [application] :as command} & {:keys [processing extras]
                                        :or   {extras     #{:verdict-ts :unseen?}
                                               processing (read-processing application)}}]
  (let [{:keys [verdict-ts unseen?]} (zipmap extras (repeat true))]
    (some-> processing
            (cond->
              verdict-ts (util/assoc-when :verdict-ts (stats/verdict-ts application))
              unseen?    (util/assoc-when :unseen? (stats-e/unseen-entries? command))))))

(defn- proc-field [field-key application]
  (some-> application read-processing field-key))

(def ^:private proc-complexity (partial proc-field :complexity))

(sc/defn ^:private write-processing! :- stats-sc/Processing
  "Persists `processing` to `application` data in mongo."
  [application processing]
  (mongo/update-by-id :applications
                      (:id application)
                      {$set {(util/kw-path stats-key :processing) (stats-sc/->db-processing processing)}})
  (stats-sc/->processing processing))

(defn delete-statistics-updates
  "Adds mongo updates that remove statistics from the application. In other words, unsets
  the root key."
  ([m]
   (assoc-in m [$unset stats-key] true))
  ([]
   (delete-statistics-updates nil)))

(defn- clear-processing!
  "Removes processing from `application` and mongo. Returns cleared application."
  [application]
  (when (contains? application stats-key)
    (mongo/update-by-query :applications
                           {:_id      (:id application)
                            stats-key {$exists true}}
                           (delete-statistics-updates)))
  (dissoc application stats-key))

(defn- local-date [ts]
  (.toLocalDate (date/zoned-date-time ts)))

(sc/defn ^:private period :- stats-sc/Period
  [start end]
  (let [p (Period/between (local-date (date/zoned-date-time start))
                          (local-date (date/zoned-date-time end)))]
    {:y (.getYears p) :m (.getMonths p) :d (.getDays p)}))

(defn- days [start end]
  (date/until start end :days))

(sc/defn calculate-processing :- (sc/maybe stats-sc/Processing)
  "Calculates the current processing snapshot for an application.

  Returns a map containing:
  - :status - Current processing state (:no-complexity, :running, :delayed, :finished)
  - :used - Work days actively spent processing (excludes delays)
  - :remaining - Work days remaining until deadline
  - :delays - Work days spent in delay periods
  - :deadline - Target completion timestamp (adjusted for delays)
  - :requests - Count of open supplement/delay requests
  - :used-period, :remaining-period - Detailed period breakdowns
  - :complexity - Processing complexity level

  The time relationship: used + remaining + delays = total workdays from submission to deadline

  Two arities:
  - [application] - Uses complexity from existing processing data
  - [application complexity] - Uses provided complexity value

  Returns nil if the application doesn't support processing (e.g., not submitted,
  wrong permit type, excluded operation types)."
  ([{:keys [submitted] :as application} complexity :- (sc/maybe stats-sc/Complexity)]
   (some->
     (cond
       (not (stats/processing-application? application :submitted)) nil
       (not complexity)                                             {:status :no-complexity}
       :else
       (let [months           (stats-sc/complexity->months complexity)
             verdict-ts       (stats/verdict-ts application)
             cutoff-ts        (stats/cutoff-ts application)
             delay-ranges     (stats-e/delay-ranges application cutoff-ts)
             delays-ms        (stats-e/delay-ranges->ms delay-ranges)
             delays-days      (.toDays (Duration/ofMillis delays-ms))
             ;; Calculate used time by subtracting delays from total elapsed time
             ;; This works because delays are non-overlapping ranges
             used-period      (period submitted (- cutoff-ts delays-ms))
             used-days        (days submitted (- cutoff-ts delays-ms))
             ;; Authority has the full day to complete processing
             deadline-ts      (-> (stats/original-deadline application months)
                                  date/timestamp
                                  (+ delays-ms)
                                  date/end-of-day
                                  date/timestamp)
             remaining-period (period cutoff-ts deadline-ts)
             remaining-days   (days cutoff-ts deadline-ts)
             ;; Status determination: finished overrides delayed
             status           (cond
                                verdict-ts                                   :finished
                                (stats-e/delayed? delay-ranges (date/now))   :delayed
                                :else                                        :running)
             requests         (stats-e/application-open-entry-count application)]
         (util/mmap {:used   used-days   :remaining remaining-days
                     :delays delays-days :deadline  deadline-ts}
                    used-period remaining-period complexity status  requests)))
     stats-sc/->processing))
  ([application]
   (calculate-processing application (proc-complexity application))))

(defn- persist-or-clear-processing! [application processing]
  (if processing
    (write-processing! application processing)
    (do
      (clear-processing! application)
      nil)))

(sc/defn update-processing! :- (sc/maybe stats-sc/Processing)
  "Application processing is recalculated and updated in mongo. Returns either the updated
  processing or nil, if the processing is not supported for the application. In the latter
  case, the old processing is cleared if it exists. This can happen, when the an operation
  is changed, for example."
  ([application complexity :- stats-sc/Complexity]
   (persist-or-clear-processing! application
                                 (calculate-processing application complexity)))
  ([application]
   (persist-or-clear-processing! application
                                 (calculate-processing application))))

(defn update-existing-processing
  "Updates application processing only if it is properly configured (complexity is
  defined). Fails silently. Returns always nil."
  [application]
  (log/with-logging-context {:applicationId (:id application)}
    (try
      (some->> application proc-complexity (update-processing! application))
      nil
      (catch Exception e
        (log/error "Update processing failed:" (ex-message e))))))

(defn processing-batchrun
  "Updates every application that has an active processing. Returns the number of
  applications updated."
  []
  (->> (mongo/select :applications
                     (query-factory :running :delayed)
                     processing-calculation-projection)
       ;; Strange, because this was fixed in https://github.com/clj-kondo/configs/pull/6
       #_{:clj-kondo/ignore [:type-mismatch]}
       (cp/pmap 8 (fn [application]
                    (log/with-logging-context
                      {:applicationId (:id application)}
                      (try
                        (boolean (update-processing! application))
                        (catch Exception e
                          (log/error e "Update processing failed:"
                                     (ex-message e)))))))
       (filter true?)
       count))

(defn post-reset-processing
  "On-success post function that creates/resets processing. `command` is the initial
  command, so the included application is no longer current."
  [command _]
  (when-let [application (mongo/by-id :applications
                                      (-> command :application :id)
                                      processing-calculation-projection)]
    (update-processing! application)))

(defn processing-user
  "Pre-checker that fails if the user belongs only to non-R organizations. Succeeds if the
  user does not belong to any organization or to some R organization."
  [{:keys [user]}]
  (when-let [org-ids (some-> user :orgAuthz keys seq)]
    (when-not (mongo/any? :organizations
                          {:deactivated      {$ne true}
                           :scope.permitType :R
                           :_id              (if (second org-ids)
                                               {$in org-ids}
                                               (first org-ids))})
      (fail :error.not-processing))))
