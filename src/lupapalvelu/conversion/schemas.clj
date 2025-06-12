(ns lupapalvelu.conversion.schemas
  (:require [lupapalvelu.conversion.section-schemas :as sec-sc]
            [lupapalvelu.statement-schemas :as statement-sc]
            [lupapalvelu.states :as states]
            [lupapalvelu.system.util-components-schema :as components-schema]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(def ^:private unsupported #{:vak})

(def SupportedPermitType (sc/enum "R" "P"))

(def permit-id-formats
  "The order of the parts and the separators between them
  in the permit id format keyed to the regex that matches with it"
  {;; So-called 'general' format, e.g. 63-0447-12-A
   #"^\d+-\d{4}-\d{2}-[A-Z]{1,3}$"      {:parts      [:kauposa :no :vuosi :tyyppi]
                                         :separators ["-" "-" "-"]}
   ;; So-called 'database' format, e.g. 12-0477-A 63
   #"^\d{2}-\d{4}-[A-Z]{1,3}\s+\d+$"    {:parts      [:vuosi :no :tyyppi :kauposa]
                                         :separators ["-" "-" " "]}
   ;; Like 'database' format, but kaupunginosanumero at front e.g. 164 10-3001-R
   #"^\d+\s+\d{2}-\d{4}-[A-Z0-9]{1,3}$" {:parts      [:kauposa :vuosi :no :tyyppi]
                                         :separators [" " "-" "-"]}
   ;; So-called 'general' format without kaupunginosanumero e.g. 0001-00-C
   #"^\d{4,5}-\d{2}-[A-Z]{1,3}$"        {:parts      [:no :vuosi :tyyppi]
                                         :separators ["-" "-"]}
   ;; So-called 'database' format without kaupunginosanumero e.g. 00-1234-B
   #"^\d{2}-\d{4,6}-[A-Z]{1,3}$"        {:parts      [:vuosi :no :tyyppi]
                                         :separators ["-" "-"]}
   ;; Permit id with full year, no type information, e.g. 500-2026-0012
   #"^\d{3}-\d{4}-\d{4}$"               {:parts      [:kunta :vuosi :no]
                                         :separators ["-" "-"]}})

(defn- parse-permit-id
  "Extract parts from `id`. Typically, you should use `destructure-permit-id` instead."
  [id]
  (when (ss/not-blank? id)
    (when-let [parts (->> permit-id-formats
                          (some (fn [[regex {:keys [parts]}]]
                                  (when (re-matches regex id)
                                    parts))))]
      (zipmap parts (ss/split id #"[-\s]+")))))

(sc/defn ^:always-validate destructure-permit-id :- {:vuosi       (sc/pred #(> % 1900))
                                                     :permit-type SupportedPermitType
                                                     sc/Keyword   sc/Any}
  "Split a permit id into a map of parts. Works regardless of which of the
  id-formats is used."
  [id]
  (when-let [m (parse-permit-id id)]
    (-> m
        (update :vuosi (fn [yy]
                         (let [yy (util/->int yy)]
                           (if (<= 0 yy 99)
                             (let [current-year   (.getYear (date/now))
                                   two-digit-year (rem current-year 100)
                                   century        (- current-year two-digit-year)]
                               (if (> yy two-digit-year)
                                 (+ (- century 100) yy)
                                 (+ century yy)))
                             yy))))
        (assoc :permit-type (case (:tyyppi m)
                              ("POP" "STR" "POI" "SUU" "PSR") "P"
                              "R")))))

(defn ->lowerkeyword
  "Lowerkeyword is a trimmed non-blank lower-case keyword. `a` can be keyword, string or
  nil. Returns nil for blanks."
  [a]
  (some-> a name ss/trim ss/blank-as-nil ss/lower-case keyword))

(defn- lowerkeyword? [a]
  (boolean (some-> a ->lowerkeyword (= a))))

(def LowerKeyword (sc/pred lowerkeyword?))

(defn backend-id->suffix
  "`backend-id` suffix as a `LowerKeyword` or nil."
  [backend-id]
  (try
    (some-> backend-id
            destructure-permit-id
            :tyyppi
            ->lowerkeyword)
    (catch Exception _
      nil)))

(defn supported-backend-id?
  "True if the `backend-id` is in the known (parseable) format AND its type is importable or
  missing."
  [backend-id]
  (try
    (boolean (when-let [m (destructure-permit-id backend-id)]
               (not (some-> m :tyyppi ->lowerkeyword unsupported))))
    (catch Exception _
      false)))

(def SupportedBackendId (sc/pred supported-backend-id? "Backend-id supported by the conversion."))

(sc/defschema XY
  {:x ssc/LocationX
   :y ssc/LocationY})

(sc/defschema ConversionLocation
  (st/merge XY
            {(sc/optional-key :propertyId) ssc/NonBlankStr
             (sc/optional-key :address)    ssc/NonBlankStr}))

(def ErrorCode (sc/enum
                 ;; Organization application with the backend-id
                 ;; already exists
                 :duplicate-backend-id
                 ;; The backend-id type is not supported
                 :unsupported-backend-id
                 ;; Application id found in KuntaGML
                 :xml-application-id
                 ;; No KuntaGML message, no conversion
                 :no-kuntagml
                 ;; Bad or onsupported KuntaGML
                 :bad-kuntagml
                 ;; Cleaaning failed
                 :clean-failed
                 ;; Missing data errors
                 :no-app-info :no-location-info :no-property-id
                 ;; Bad foreman application
                 :bad-foreman
                 ;; Some other error
                 :unknown))

(sc/defschema HistoryEntry
  {:ts ssc/Timestamp
   :op (sc/enum :store :location :convert :send :link :link-repair
                :clean :fix-history :fix-verdicts :fix-foremen
                :fix-locations :fix-states :fix-reviews)})

(def LocationOverrides {SupportedBackendId ConversionLocation})

(def State
  "Every state is allowed but not used in practise (e.g. `:draft`)."
  (apply sc/enum states/all-states))

(def StateOrSet
  "Set variant is needed, because the state matching depends on the target application. For
  example, the first post-vardict state can be `:verdictGiven`, `:foremanVerdictGiven`,
  `:acknowledged`, ..."
  (sc/conditional
    keyword?  State
    not-empty #{State}))

(sc/defschema StateOverrides
  "If `ts` is not given, the current time is used."
  {SupportedBackendId (sc/conditional
                        :ts {:ts date/ZonedArg :state StateOrSet}
                        :else StateOrSet)})

(def StateMapping
  "Mapping from KuntaGML tilamuutos to Lupapiste state. Each mapping can either _fully_
  replace an existing mapping or create a new one."
  {ssc/NonBlankStr StateOrSet})

(sc/defschema ConversionEdn
  "Configuration EDN file syntax. If neither `:backend-ids` nor `:files` is given, the
  conversion targets are queried from the mongo conversion collection."
  {:organization-id                         ssc/NonBlankStr
   (sc/optional-key :permit-type)           SupportedPermitType
   (sc/optional-key :overwrite?)            sc/Bool
   (sc/optional-key :force-terminal-state?) sc/Bool
   (sc/optional-key :tags)                  [ssc/NonBlankStr]
   ;; Backend ids of the permits that are fetched from the backing system.
   (sc/optional-key :backend-ids)           [SupportedBackendId]
   ;; Local KuntaGML message filenames
   (sc/optional-key :files)                 [ssc/NonBlankStr]
   ;; Keys are backend ids
   (sc/optional-key :location-overrides)    LocationOverrides
   (sc/optional-key :location-fallback)     ConversionLocation
   (sc/optional-key :state-overrides)       StateOverrides
   (sc/optional-key :state-mapping)         StateMapping})

(sc/defschema ConversionDocument
  (st/merge
    ssc/SheriffNotes
    {:id                                 ssc/NonBlankStr ;; Old document ids are not object
     ;; id strings Schema allows for unsupported backend-ids. However, those are flagged
     ;; with error properties.
     :backend-id                         ssc/NonBlankStr
     ;; Parsed backend-id type suffix (e.g., `:tjo` `:r`, `:str`).
     (sc/optional-key :suffix)           LowerKeyword
     :year                               ssc/PosInt ;; Resolved from `:backend-id`
     ;; LP-id is generated when the conversion is deemed doable (e.g., no backend-id
     ;; conflicts, old application id remnants). Afterwards, the LP-id is not changed,
     ;; regardless what happens with the conversion.
     (sc/optional-key :LP-id)            ssc/ApplicationId
     :organization                       ssc/NonBlankStr
     :permit-type                        SupportedPermitType
     ;; Error status for the latest conversion run.
     (sc/optional-key :conversion-error) {:code                   ErrorCode
                                          ;; Typically an exception message
                                          (sc/optional-key :text) ssc/NonBlankStr}
     (sc/optional-key :linked)           sc/Bool
     (sc/optional-key :app-links)        [ssc/NonBlankStr] ;; Application or backend ids.
     (sc/optional-key :converted)        sc/Bool
     (sc/optional-key :kuntagml)         {(sc/optional-key :error) sc/Any
                                          (sc/optional-key :xml)   ssc/NonBlankStr}
     (sc/optional-key :history)          [HistoryEntry]
     (sc/optional-key :tags)             [ssc/NonBlankStr]
     ;; Running number within conversion batch. Only used for selecting conversion subsets.
     (sc/optional-key :n)                ssc/PosInt
     ;; Taken from `:location-overrides`.
     (sc/optional-key :location)         ConversionLocation
     ;; The application location coordinates after a successful conversion. Should be the
     ;; subset of `:location` if both are given.
     (sc/optional-key :reset-location)   XY
     (sc/optional-key :sections)         [sec-sc/SectionData]
     ;; The application state after successful conversion.
     (sc/optional-key :reset-state)      sc/Keyword
     sc/Keyword                          sc/Any}))

(sc/defschema Target
  (sc/conditional
    ;; We want to store bad ids as well. see `ConversionDocument` `:backend-id` above.
    :id   {:id ssc/NonBlankStr}
    :else {:filename ssc/NonBlankStr}))

(sc/defschema ResolvedTarget
  {;; From EDN or target file
   :id                                   ssc/NonBlankStr
   (sc/optional-key :filename)           ssc/NonBlankStr
   ;; From target file. If found the the target should not be converted.
   (sc/optional-key :xml-application-id) ssc/NonBlankStr
   (sc/optional-key :conversion-doc)     ConversionDocument})

(sc/defschema ConversionConfiguration
  (st/merge
    (st/select-keys ConversionEdn [:organization-id :permit-type :overwrite? :force-terminal-state?
                                   :location-overrides :location-fallback :tags
                                   :state-overrides :state-mapping])
    {;; Municipality for the first organization scope that matches `permit-type`.
     :municipality                     ssc/NonBlankStr
     ;; If not defined or empty, the targets are queried from the mongo conversion collection.
     (sc/optional-key :targets)        [Target]
     (sc/optional-key :years)          [ssc/PosInt]
     (sc/optional-key :range)          [ssc/PosInt]
     (sc/optional-key :limit)          ssc/PosInt
     (sc/optional-key :threads)        ssc/PosInt
     (sc/optional-key :retry)          sc/Bool
     (sc/optional-key :location-relax) sc/Bool
     (sc/optional-key :system)         components-schema/CommonComponents}))

(sc/defschema DeleteOptions
  {(sc/optional-key :delete-conversion-document?) sc/Bool
   (sc/optional-key :force?)                      sc/Bool
   (sc/optional-key :nuke?)                       sc/Bool})

;; ------------------------------
;; Checkers
;; ------------------------------

(def check-location (sc/checker ConversionLocation))
(def valid-location! (sc/validator ConversionLocation))

;; ------------------------------
;; Other schemas
;; ------------------------------

(def other-ts-fields [:created :sent :verdict-date :closed])

(defn valid-date-or-range? [value]
  (let [[a b :as xs] (if (sequential? value)
                       value
                       [value])]
    (try
      (case (count xs)
        1 (boolean (date/zoned-date-time a))
        2 (date/before? (date/start-of-day a)
                        (date/end-of-day b))
        false)
      (catch Exception _ false))))

(def DateOrRange (sc/pred valid-date-or-range? "Date or date range"))

(sc/defschema ForcePath
  "Parameter for forcing the dmcity endpoint path. The production channel corresponds to
  the `:application` path and conversion is `:conversion`..."
  {(sc/optional-key :force-path) (sc/enum :conversion :application)})

(sc/defschema OtherConfig
  "Criteria for target applications."
  (-> {:organization-id                      ssc/NonBlankStr
       (sc/optional-key :system)             components-schema/CommonComponents
       (sc/optional-key :permit-type)        SupportedPermitType
       (sc/optional-key :application-ids)    [ssc/ApplicationId]
       ;; State change options
       (sc/optional-key :all)                sc/Bool
       (sc/optional-key :last)               sc/Bool
       ;; KuntaGML options
       (sc/optional-key :application)        sc/Bool
       (sc/optional-key :verdict)            sc/Bool
       (sc/optional-key :review)             sc/Bool
       ;; Send appropriate messages based on the application contents.
       (sc/optional-key :auto)               sc/Bool
       (sc/optional-key :include-converted?) sc/Bool
       ;; Review fix options
       (sc/optional-key :fetch)              sc/Bool
       (sc/optional-key :states)             [(apply sc/enum states/post-verdict-states)]}
      (st/merge ForcePath)
      ;; Date options
      (into (map (fn [k]
                   [(sc/optional-key k) DateOrRange])
                 other-ts-fields))))

;; ------------------------------
;; Import schemas
;; ------------------------------

(sc/defschema TaskItem
  "Lupamaarays-task that should be approved."
  {:backend-id                  ssc/NonBlankStr
   :taskname                    ssc/NonBlankStr
   (sc/optional-key :received)  date/ZonedArg
   (sc/optional-key :processed) date/ZonedArg
   (sc/optional-key :text)      ssc/NonBlankStr})

(def ^:private check-task-item (sc/checker TaskItem))

(defn valid-task-item! [item]
  (if (or (check-task-item item)
          (-> item
              (select-keys [:received :processed :text])
              util/strip-blanks
              empty?))
    (throw (ex-info (str "Bad task item: " item) {:item item}))
    item))

(sc/defschema ImportTasksConfig
  {:organization-id ssc/NonBlankStr
   :op              (sc/eq :tasks)
   :items           [TaskItem]})

(sc/defschema ImportSchemasConfig
  {:op              (sc/eq :schemas)
   :items           [sec-sc/SchemaDocumentImport]})

(sc/defschema ImportSectionsConfig
  {:organization-id ssc/NonBlankStr
   :op              (sc/eq :sections)
   :items           {ssc/NonBlankStr [sec-sc/SectionImport]}})

(sc/defschema TextItem
  {:backend-id ssc/NonBlankStr
   :text       ssc/NonBlankStr})

(sc/defschema ImportNotesConfig
  {:organization-id ssc/NonBlankStr
   :op              (sc/eq :notes)
   :items           [TextItem]})

(sc/defschema ImportDescriptionsConfig
  {:organization-id ssc/NonBlankStr
   :op              (sc/eq :descriptions)
   :items           [TextItem]})

(sc/defschema StatementItem
  (sc/constrained
    (-> statement-sc/Statement
        (st/select-keys [:status :text :person])
        (st/update :person (util/fn-> (st/select-keys [:id :text :email :name])
                                      st/optional-keys))
        (st/assoc :given date/ZonedArg
                  :requested date/ZonedArg)
        st/optional-keys
        (st/assoc :backend-id ssc/NonBlankStr))
    (util/fn-> (dissoc :backend-id) util/strip-blanks not-empty boolean)
    "Must have data statement data"))

(sc/defschema ImportStatementsConfig
  {:organization-id ssc/NonBlankStr
   :op              (sc/eq :statements)
   :items           [StatementItem]})

(sc/defschema ImportConfig
  (let [op? #(comp #{%} :op)]
    (sc/conditional
      (op? :tasks)        ImportTasksConfig
      (op? :schemas)      ImportSchemasConfig
      (op? :sections)     ImportSectionsConfig
      (op? :notes)        ImportNotesConfig
      (op? :descriptions) ImportDescriptionsConfig
      (op? :statements)   ImportStatementsConfig)))
