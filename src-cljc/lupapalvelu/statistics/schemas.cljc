(ns lupapalvelu.statistics.schemas
  (:require [clojure.set :as set]
            [sade.shared-schemas :as ssc]
            [sade.strings :as ss]
            [schema-tools.core :as st]
            [schema-tools.coerce :as stc]
            [schema.core :as sc]
            [sade.util :as util]))

(def ^:private statuses
  "Processing status.

  `:no-complexity`. Months not initialized, waits for authority.

  `:delayed`. Active delays, waits for applicant.

  `:running`. Actively processed and needs to be regularly updated.

  `:finished`. Processing done, verdict given."
  [{:kw :no-complexity :db 0}
   {:kw :running       :db 1}
   {:kw :delayed       :db 2}
   {:kw :finished      :db 3}])

(def ^:private DbStatus (apply sc/enum (map :db statuses)))

(def Status (apply sc/enum (map :kw statuses)))

(def complexities
  [{:kw :special     :db 0 :months 1}
   {:kw :small       :db 1 :months 3}
   {:kw :medium      :db 2 :months 3}
   {:kw :large       :db 3 :months 3}
   {:kw :very-large  :db 4 :months 6}
   {:kw :extra-large :db 5 :months 6}])

(defn- find-item! [xs target]
  (if (number? target)
    (util/find-by-key :db target xs)
    (util/find-by-key :kw (keyword target) xs)))

(def ^:private find-status (partial find-item! statuses))
(def ^:private find-complexity (partial find-item! complexities))

(sc/defn ->db-statuses :- #{DbStatus}
  [& statuses]
  (->> statuses (map (comp :db find-status)) set))

(def DbComplexity
  (apply sc/enum (map :db complexities)))

(def Complexity
  (apply sc/enum (map :kw complexities)))

(def StrComplexity (apply sc/enum (map (comp name :kw) complexities)))

(sc/defn ->db-complexity :- DbComplexity
  [complexity :- Complexity]
  (:db (find-complexity complexity)))

(sc/defn complexity->months :- ssc/PosInt
  "Complexity determines the allocated processing time.

  +--------+-------------------------------+
  | Months | Complexity                    |
  +--------+-------------------------------+
  |   1    | `:special`                    |
  +--------+-------------------------------+
  |   3    | `:small`, `:medium`, `:large` |
  +--------+-------------------------------+
  |   6    | `:very-large`, `:extra-large` |
  +--------+-------------------------------+

  The overlapping month amounts are needed due to different localisations of each
  complexity."
  [complexity :- Complexity]
  (or (some-> (find-complexity complexity) :months)
      (throw (ex-info (str "Unsupported complexity: " complexity)
                      (util/mmap complexity)))))

(sc/defschema Period
  "Map presenting `java.time.Period` instance."
  {:y sc/Int :m sc/Int :d sc/Int})

(sc/defschema DbProcessing
  " Application processing snapshot in the database format (integers for sorting
    support). Durations are workdays.

   `:used` + `:remaining` + `:delays`
   = workdays between application `:submitted` and `:deadline`."
  (st/optional-keys
    {:complexity       DbComplexity
     :status           DbStatus
     :used             ssc/Nat ; Days
     :used-period      Period
     :remaining        sc/Int  ; Days
     :remaining-period Period
     :delays           ssc/Nat ; Days
     :requests         ssc/Nat ; Open requests
     :deadline         ssc/Timestamp}))

(sc/defschema Processing
  "Application processing snapshot. Durations are workdays.

   `:used` + `:remaining` + `:delays`
   = workdays between application `:submitted` and `:deadline`."
  (st/merge DbProcessing
            (st/optional-keys
              {:complexity Complexity
               :status     Status})))

(def ^:private processing-coercion-matcher
  (some-fn {DbStatus     (comp :db find-status)
            DbComplexity (comp :db find-complexity)
            Status       (comp :kw find-status)
            Complexity   (comp :kw find-complexity)}
           stc/json-coercion-matcher))

(def ->db-processing (stc/coercer DbProcessing processing-coercion-matcher))

(def ->processing (stc/coercer Processing processing-coercion-matcher))

(def ->front-processing (stc/coercer (st/open-schema Processing) processing-coercion-matcher))

;; ----------------------------------
;; Log
;; ----------------------------------

(sc/defschema ProcessingLogData
  {:submitted-ts                 ssc/Timestamp
   (sc/optional-key :verdict-ts) ssc/Timestamp
   (sc/optional-key :delays)     [{:start-ts                      ssc/Timestamp
                                   (sc/optional-key :end-ts)      ssc/Timestamp
                                   (sc/optional-key :explanation) ssc/NonBlankStr
                                   :text                          ssc/NonBlankStr}]
   :processing                   Processing})

(def ->logdata (stc/coercer ProcessingLogData stc/json-coercion-matcher))

;; ----------------------------------
;; Entries
;; ----------------------------------

(def EntryType (sc/enum :request :delay))

(sc/defschema EntryUpdate
  (st/optional-keys
    {:type          EntryType
     :delay-start   ssc/Timestamp
     :deleted?      sc/Bool
     :created-text  ssc/NonBlankStr
     :answered?     sc/Bool
     :answered-ts   ssc/Timestamp
     :answered-text sc/Str
     :explanation   sc/Str
     :closed?       sc/Bool
     :closed-ts     ssc/Timestamp
     :closed-text   sc/Str}))

(sc/defschema NewRequestParams
  (-> EntryUpdate
      (st/select-keys [:delay-start :created-text])
      (st/assoc :id ssc/ApplicationId)))

(sc/defschema NewDelayParams
  (-> EntryUpdate
      (st/select-keys [:delay-start :created-text :explanation :closed-ts])
      (st/assoc :id ssc/ApplicationId)))

(sc/defschema FrontendEntry
  (st/merge EntryUpdate
            {:id           ssc/ObjectIdStr
             :created-ts   ssc/Timestamp
             :created-name ssc/NonBlankStr
             :modified     ssc/Timestamp}
            (st/optional-keys
              {:answered-name ssc/NonBlankStr
               :closed-name   ssc/NonBlankStr
               :seen-by?      sc/Bool})))

(defn diff-map [m1 m2]
  (into {} (set/difference (set m1) (set m2))))

(defn- nil-key? [m k]
  (and (contains? m k) (nil? (k m))))

(defn ->entry-update [m]
  (-> (cond-> m
        (nil-key? m :closed-ts) (assoc :closed? false)
        (:closed-ts m)          (assoc :closed? true))
      util/strip-nils
      (st/select-schema EntryUpdate stc/json-coercion-matcher)
      ss/trimwalk
      not-empty))

(defn ->fronted-entry [m]
  (st/select-schema m FrontendEntry stc/json-coercion-matcher))
