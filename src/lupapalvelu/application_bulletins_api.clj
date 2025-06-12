(ns lupapalvelu.application-bulletins-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defcommand defquery defraw] :as action]
            [lupapalvelu.application-bulletin-utils :as bulletin-utils]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-search :refer [dir make-text-query]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.backing-system.dmcity.core :as dmcity]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :refer [warnf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notices.util :as notices-util]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.vetuma :as vetuma]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(def bulletin-page-size 10)

(defn- make-query [{:keys [searchText
                           municipality
                           organization
                           state
                           category
                           category-not
                           bulletinProcess]}]
  (let [queries (remove empty? [(when-not (ss/blank? searchText)
                                  (make-text-query (ss/trim searchText)))
                                (when-not (ss/blank? municipality)
                                  {:versions.municipality municipality})
                                (when-not (ss/blank? organization)
                                  {:versions.organization organization})
                                (when-not (ss/blank? bulletinProcess)
                                  (if (util/=as-kw :julkipano bulletinProcess)
                                    {$or [{:versions.bulletinProcess bulletinProcess}
                                          {:versions.bulletinProcess {$exists false}}]}
                                    {:versions.bulletinProcess bulletinProcess}))
                                (when-not (ss/blank? state)
                                  {:versions.bulletinState state})
                                (cond
                                  (seq category-not)
                                  {:versions.category {$nin category-not}}
                                  (not (ss/blank? category))
                                  {:versions.category category})])]
    (when-let [and-query (seq queries)]
      {$and and-query})))

(def- sort-field-mapping {"address"                      :versions.address
                          "appealPeriodStartsAt"         :versions.appealPeriodStartsAt
                          "applicant"                    :versions.applicant
                          "bulletinState"                :versions.bulletinState
                          "modified"                     :versions.modified
                          "municipality"                 :versions.municipality
                          ;; Sort by kuulutus date, but fall back to section if multiple bulletins
                          ;; are published on the same date
                          "appealPeriodStartsAt+section" [:versions.appealPeriodStartsAt :intSection]
                          "verdictGivenAt"               :versions.verdictGivenAt})

(def- default-sort (array-map :versions.modified -1))

(defn- make-sort [{:keys [field asc]}]
  (let [sort-field (sort-field-mapping field)]
    (assoc (cond
             (nil? sort-field)        default-sort
             (sequential? sort-field) (apply array-map (interleave sort-field (repeat (dir asc))))
             :else                    (array-map sort-field (dir asc)))
           ;; Tie-breaker
           :_id (dir asc))))

(defn- bulletin->version
  [{:keys [versions] :as bulletin}]
  (assoc versions :id (:_id bulletin)))

(defn with-default-process
  "Set default process for legacy julkipano bulletins"
  [version]
  (update version :bulletinProcess (fnil identity "julkipano")))

(defn drop-applicant
  [{:keys [category] :as version}]
  (if (#{"ymp"} category)
    version
    (dissoc version :applicant)))

(defn post-process-bulletin
  [bulletin]
  (-> bulletin
      bulletin->version
      with-default-process
      drop-applicant))

(defn split-results
  [page results]
  {:pre [(int? page) (vector? results)]}
  (let [result-count (count results)
        skip         (-> (* (dec page) bulletin-page-size)
                         (max 0) ; clamp between [0, result-count]
                         (min result-count))
        end          (min (+ skip bulletin-page-size)
                          result-count)
        results      (into [] (subvec results skip end))]
    {:left (- result-count skip bulletin-page-size)
     :data results}))

(defn get-application-bulletins
  "Queries bulletins from mongo. Returns latest versions of bulletins.
   Bulletins which have starting dates in the future will be omitted."
  [{:keys [page sort] :as parameters} now-ts]
  (let [query (or (make-query parameters) {})
        page  (util/->int page) ;; Fallbacks to zero
        ;; TODO: mongo might be able to calculate the total document count with
        ;; something like `$setWindowFields`+`$count` which would allow us to use `$skip` + `$limit`
        ;; instead of fetching all the matches and calculating it here
        apps  (->> (mongo/aggregate "application-bulletins"
                                    [{$match (bulletins/versions-elemMatch now-ts)}
                                     {$unwind {:path "$versions"}}
                                     {$match (bulletins/version-elemMatch now-ts)}
                                     {$group {:_id             "$_id"
                                              :versions        {$last "$$ROOT.versions"},
                                              :modified        {$last "$$ROOT.modified"},
                                              :address         {$last "$$ROOT.address"},
                                              :_applicantIndex {$last "$$ROOT._applicantIndex"}}}
                                     {$match query}
                                     {$project bulletins/bulletins-fields}
                                     ;; Try to convert sections (strings) into sortable integers
                                     {$addFields {:intSection {$convert {:input   "$versions.verdictData.section"
                                                                         :to      "int"
                                                                         ;; Most of the time sections are plain numbers as strings.
                                                                         ;; But handle conversion errors just in case some municipality
                                                                         ;; writes random text into the section.
                                                                         :onError -1
                                                                         :onNull  -1}}}}
                                     {$sort (make-sort sort)}]
                                    true)
                   (mapv post-process-bulletin))]
    (split-results page apps)))

(def Page (sc/constrained ssc/Nat
                          #(< (* % bulletin-page-size) Integer/MAX_VALUE)))
(def SearchText (sc/constrained sc/Str
                                #(<= (count %)
                                     (env/value :search-text-max-length))))
(def Sort {:field                 sc/Str
           (sc/optional-key :asc) sc/Bool})

(defquery application-bulletins
  {:description    "Query for Julkipano"
   :request-schema {:page                           Page
                    (sc/optional-key :searchText)   SearchText
                    (sc/optional-key :municipality) sc/Str
                    (sc/optional-key :state)        sc/Str
                    (sc/optional-key :sort)         Sort
                    (sc/optional-key :category)     sc/Str}
   :user-roles     #{:anonymous}}
  [{data :data now-ts :created}]
  (let [{:keys [data left]} (get-application-bulletins data now-ts)]
    (ok :data data
        :left left)))

(defquery local-application-bulletins
  {:request-schema {(sc/optional-key :page)            Page
                    (sc/optional-key :searchText)      SearchText
                    (sc/optional-key :organization)    sc/Str
                    (sc/optional-key :bulletinProcess) sc/Str
                    (sc/optional-key :sort)            Sort}
   :user-roles     #{:anonymous}}
  [{data :data now-ts :created}]
  ;; Filter out YMP bulletins as local bulletins view does not support them properly.
  (let [{:keys [data left]} (get-application-bulletins (assoc data :category-not ["ymp"]) now-ts)]
    (ok :data data
        :left left)))

(defquery application-bulletin-municipalities
  {:description "List of distinct municipalities of application bulletins"
   :parameters  []
   :user-roles  #{:anonymous}}
  [_]
  (let [municipalities (mongo/distinct :application-bulletins :versions.municipality)]
    (ok :municipalities municipalities)))

(defquery application-bulletin-states
  {:description "List of distinct states of application bulletins"
   :parameters  []
   :user-roles  #{:anonymous}}
  [_]
  (let [states (mongo/distinct :application-bulletins :versions.bulletinState)]
    (ok :states states)))

(defquery application-bulletin-categories
  {:description "List of distinct categories of application bulletins"
   :parameters  []
   :user-roles  #{:anonymous}}
  [_]
  (ok :categories (mongo/distinct :application-bulletins :versions.category)))

(defn- latest-valid-bulletin-version
  "Returns the latest bulletin version whose date period is currently valid."
  [bulletin-id]
  (some->> (bulletins/get-bulletin bulletin-id [:versions])
           :versions
           (filter bulletin-utils/bulletin-version-date-valid? )
           (sort-by :created)
           last))

(defn- comment-can-be-added
  [{{bulletin-id :bulletinId bulletin-version-id :bulletinVersionId} :data}]
  (let [bulletin-version (latest-valid-bulletin-version bulletin-id)]
    (cond
      (nil? bulletin-version)
      (fail :error.not-found)

      (not= bulletin-version-id (:id bulletin-version))
      (fail :error.invalid-version-id)

      (nil? (bulletins/bulletin-comment-deadline bulletin-version))
      (fail :error.unauthorized))))

(defn- latest-bulletin-version? [{{bulletin-id :bulletinId bulletin-version-id :bulletinVersionId} :data}]
  (let [latest-version-id (->> (bulletins/get-bulletin bulletin-id [:versions.id :versions.created])
                               :versions
                               (sort-by :created)
                               last
                               :id)]
    (cond
      (nil? latest-version-id)                     (fail :error.not-found)
      (not= latest-version-id bulletin-version-id) (fail :error.invalid-version-id))))

(defn- referenced-file-can-be-attached
  [id-type]
  (fn [{{files :files} :data user :user}]
    (let [target-id (case id-type
                      :user-id    (:id user)
                      :session-id (vetuma/session-id))]
      (when-not (every? #(storage/unlinked-file-exists? target-id (:fileId %))
                        files)
        (fail :error.invalid-files-attached-to-comment)))))

(def delivery-address-fields #{:firstName :lastName :street :zip :city})

(defn validate-uploaded-files [command]
  (when-let [files (seq (get-in command [:data :files]))]
    (when-let [err (some bulletins/comment-file-checker files)]
      (warnf "schema validation error in add-bulletin-comment: %s" (pr-str err))
      (fail :error.illegal-value:schema-validation :source ::validate-uploaded-files))))

(defcommand add-bulletin-comment
  {:description      "Add comment to bulletin"
   :pre-checks       [vetuma/session-pre-check
                      comment-can-be-added
                      (referenced-file-can-be-attached :session-id)
                      validate-uploaded-files]
   :input-validators [(partial action/non-blank-parameters [:bulletinId :comment])]
   :user-roles       #{:anonymous}}
  [{{files :files bulletin-id :bulletinId comment :comment bulletin-version-id :bulletinVersionId
     email :email emailPreferred :emailPreferred otherReceiver :otherReceiver} :data created :created session :session}]
  (let [address-source   (or otherReceiver (get-in (vetuma/vetuma-session session) [:user]))
        delivery-address (select-keys address-source delivery-address-fields)
        contact-info     (merge delivery-address {:email          email
                                                  :emailPreferred emailPreferred})
        comment          (bulletins/create-comment bulletin-id bulletin-version-id comment contact-info files created)]
    (mongo/insert :application-bulletin-comments comment)
    (when (seq files)
      (storage/link-files-to-bulletin (vetuma/session-id session) bulletin-id (map :fileId files)))
    (ok)))

(defn application-matches-bulletin
  "Pre-check that fails if the application (`id`) and bulletin (`bulletinId`) DO NOT match
  the same application."
  [{{:keys [id bulletinId]} :data}]
  (when (and id bulletinId)
    (when-not (mongo/any? :application-bulletins {:_id                     bulletinId
                                                  :versions.application-id id})
      (fail :error.not-found))))

(defcommand add-manual-bulletin-comment
  {:description    "Add comment to bulletin manually. Authority only."
   :request-schema {:id                      ssc/ApplicationId
                    :bulletinId              ssc/NonBlankStr
                    :bulletinVersionId       ssc/NonBlankStr
                    :contact                 {:firstName                        ssc/NonBlankStr
                                              :lastName                         ssc/NonBlankStr
                                              :street                           ssc/NonBlankStr
                                              :zip                              ssc/NonBlankStr
                                              :city                             ssc/NonBlankStr
                                              (sc/optional-key :emailPreferred) sc/Bool
                                              (sc/optional-key :email)          sc/Str}
                    :comment                 ssc/NonBlankStr
                    (sc/optional-key :files) [{:fileId      ssc/NonBlankStr
                                               :filename    ssc/NonBlankStr
                                               :size        ssc/PosInt
                                               :contentType ssc/NonBlankStr}]}
   :permissions    [{:required [:application/edit-bulletin]}]
   :pre-checks     [application-matches-bulletin
                    latest-bulletin-version?
                    (referenced-file-can-be-attached :user-id)
                    validate-uploaded-files]}
  [{{bulletin-id :bulletinId files               :files
     comment     :comment    bulletin-version-id :bulletinVersionId
     contact     :contact} :data created :created user :user}]
  (let [comment (bulletins/create-comment bulletin-id bulletin-version-id
                                          comment contact files created)]
    (mongo/insert :application-bulletin-comments comment)
    (when (seq files)
      (storage/link-files-to-bulletin (:id user) bulletin-id (map :fileId files)))
    (ok)))

(defcommand move-to-proclaimed
  {:request-schema   {:id                   ssc/ApplicationId ;; The same as bulletin id for YMP.
                      :proclamationStartsAt ssc/Timestamp
                      :proclamationEndsAt   ssc/Timestamp
                      :proclamationText     ssc/NonBlankStr}
   :permissions      [{:required [:application/edit-bulletin]
                       :context  {:application {:state      #{:sent :complementNeeded}
                                                :permitType permit/ymp-permit-type-kw-set}}}]
   :input-validators [(partial bulletins/validate-input-dates :proclamationStartsAt :proclamationEndsAt)]}
  [{:keys                      [application created data]}]
  (let [updates (bulletins/create-bulletin application created
                                           (select-keys data [:proclamationEndsAt :proclamationStartsAt
                                                              :proclamationText]))]
    (bulletins/upsert-bulletin-by-id (:id data) updates)
    (ok)))

(defcommand move-to-verdict-given
  {:request-schema   {:id                   ssc/ApplicationId ;; The same as bulletin id for YMP.
                      :verdictGivenAt       ssc/Timestamp
                      :appealPeriodStartsAt ssc/Timestamp
                      :appealPeriodEndsAt   ssc/Timestamp
                      :verdictGivenText     ssc/NonBlankStr}
   :input-validators [(partial bulletins/validate-input-dates :appealPeriodStartsAt :appealPeriodEndsAt)
                      (partial bulletins/validate-input-dates :verdictGivenAt :appealPeriodStartsAt)]
   :permissions      [{:required [:application/edit-bulletin]
                       :context  {:application {:state      #{:verdictGiven}
                                                :permitType permit/ymp-permit-type-kw-set}}}]
   :pre-checks [bulletins/verdict-bulletin-should-not-exist]}
  [{:keys                      [application created data]}]
  (let [updates (bulletins/create-bulletin application created
                                           (select-keys data [:verdictGivenAt :appealPeriodStartsAt
                                                              :appealPeriodEndsAt :verdictGivenText]))]
    (bulletins/upsert-bulletin-by-id (:id data) updates)
    (ok)))

(defcommand move-to-final
  {:request-schema {:id         ssc/ApplicationId ;; The same as bulletin id for YMP.
                    :officialAt ssc/Timestamp}
   :permissions    [{:required [:application/edit-bulletin]
                     :context  {:application {:state      #{:verdictGiven}
                                              :permitType permit/ymp-permit-type-kw-set}}}]
   :pre-checks     [bulletins/validate-bulletin-verdict-state
                    bulletins/validate-official-at]}
  [{:keys [application created] {:keys [id officialAt]} :data}]
  ; Note there is currently no way to move application to final state so we sent bulletin state manuall
  (let [updates (bulletins/create-bulletin application created {:officialAt    officialAt
                                                                :bulletinState :final})]
    (bulletins/upsert-bulletin-by-id id updates)
    (ok)))

(defn get-bulletin-version [bulletinId command]
  (if-let [bulletin (bulletins/get-bulletin bulletinId)]
    (let [latest-version   (bulletins/bulletin-version-for-user command bulletin)
          bulletin-version (assoc latest-version :versionId (:id latest-version)
                                  :id (:id bulletin))
          append-schema-fn (fn [{schema-info :schema-info :as doc}]
                             (assoc doc :schema (schemas/get-schema schema-info)))]
      (-> bulletin-version
          (domain/filter-application-content-for {})
          ;; unset keys (with empty values) set by
          ;; filter-application-content-for and other unneeded
          ;; fields
          (dissoc :comments :neighbors :statements :company-notes :tasks)
          (update-in [:documents] (partial mapv append-schema-fn))
          (update-in [:attachments] (partial mapv #(dissoc % :metadata :auth)))
          (assoc :stateSeq bulletins/bulletin-state-seq)
          (util/assoc-when :commentDeadline (bulletins/bulletin-comment-deadline bulletin-version))
          with-default-process))
    (fail! :error.bulletin.not-found)))

(defquery public-bulletin
  {:description    "Returns the current/latest application bulletin version for unsigned users."
   :request-schema {:bulletinId ssc/NonBlankStr}
   :pre-checks     [bulletins/bulletin-is-accessible]
   :user-roles     #{:anonymous}}
  [{:keys [created] {:keys [bulletinId]} :data :as command}]
  (let [bulletin-version (get-bulletin-version bulletinId command)
        bulletin-data    (cond-> bulletin-version
                           (not= "ymp" (:category bulletin-version))
                           (dissoc :applicant :_applicantIndex :attachments :documents :verdicts))]
    (bulletins/add-version-visit bulletinId (:versionId bulletin-data) created)
    (ok :bulletin (util/assoc-when bulletin-data
                                   :public? true
                                   :can-appeal? (bulletins/bulletin-appeal-enabled? bulletin-data)))))

(defn filter-paapiirustus [attachments]
  (filterv #(-> % :type :type-group (= "paapiirustus")) attachments))

(defquery vetuma-bulletin
  {:description      "Returns the current/latest application bulletin version for signed-in users."
   :parameters       [bulletinId]
   :input-validators [(partial action/non-blank-parameters [:bulletinId])]
   :pre-checks       [vetuma/session-pre-check
                      bulletins/bulletin-is-accessible]
   :user-roles       #{:anonymous}}
  [{:keys [created] :as command}]
  (let [bulletin-version (get-bulletin-version bulletinId command)
        bulletin         (if (#{"ymp"} (:category bulletin-version))
                           bulletin-version
                           (-> bulletin-version
                               (dissoc :applicant :documents)
                               (update :attachments filter-paapiirustus)))]
    (bulletins/add-version-visit bulletinId (:versionId bulletin-version) created)
    (ok :bulletin (util/assoc-when bulletin
                                   :can-appeal? (bulletins/bulletin-appeal-enabled? bulletin)))))

(defn- count-comments [version]
  (let [comment-count (mongo/count :application-bulletin-comments {:versionId (:id version)})]
    (assoc version :comments comment-count)))

(defquery bulletin-versions
  {:description "Returns all bulletin versions for application bulletin with comments"
   :request-schema {:id         ssc/ApplicationId
                    :bulletinId ssc/NonBlankStr}
   :permissions    [{:required [:application/read]}]
   :pre-checks [application-matches-bulletin]}
  [{{:keys [bulletinId]} :data}]
  (let [bulletin-fields (-> bulletins/bulletins-fields
                            (dissoc :versions)
                            (merge {:comments      1
                                    :versions.id   1
                                    :bulletinState 1}))
        bulletin        (bulletins/get-bulletin bulletinId bulletin-fields)
        versions        (mapv count-comments (:versions bulletin))
        bulletin        (assoc bulletin :versions versions)]
    (ok :bulletin bulletin)))

(defquery bulletin-comments
  {:description    "Returns paginated comments related to given version id"
   :request-schema {:id                      ssc/ApplicationId
                    :bulletinId              ssc/NonBlankStr
                    :versionId               ssc/NonBlankStr
                    (sc/optional-key :skip)  ssc/Nat
                    (sc/optional-key :limit) ssc/Nat
                    (sc/optional-key :asc)   sc/Bool}
   :permissions    [{:required [:application/read]}]
   :pre-checks     [application-matches-bulletin]}
  [{{:keys [bulletinId versionId asc skip]
     :or   {skip 0 asc true} :as data} :data}]
  (let [q              {:bulletinId bulletinId :versionId versionId}
        order-by       {:created (if asc 1 -1)}
        comments       (mongo/select-options :application-bulletin-comments
                                             q
                                             (assoc (select-keys data [:skip :limit])
                                                    :order-by order-by))
        total-comments (mongo/count :application-bulletin-comments q)
        comments-left  (max 0 (- total-comments (+ skip (count comments))))]
    (ok :comments comments :totalComments total-comments :commentsLeft comments-left)))

(defn- bulletin-can-be-saved
  ([data-key state {data :data}]
   (let [bulletin (bulletins/get-bulletin (get data data-key))]
     (if-not bulletin
       (fail :error.invalid-bulletin-id)
       (when-not (util/=as-kw (:bulletinState bulletin) state)
         (fail :error.invalid-bulletin-state)))))
  ([data-key state command _]
   (bulletin-can-be-saved data-key state command)))

(defcommand save-proclaimed-bulletin
  {:description      "Updates proclaimed version timestamps and text"
   :request-schema   {:id                   ssc/ApplicationId ;; The same as bulletin id for YMP.
                      :bulletinVersionId    ssc/NonBlankStr
                      :proclamationStartsAt ssc/Timestamp
                      :proclamationEndsAt   ssc/Timestamp
                      :proclamationText     ssc/NonBlankStr}
   :permissions      [{:required [:application/edit-bulletin]
                       :context  {:application {:state      #{:sent :complementNeeded}
                                                :permitType permit/ymp-permit-type-kw-set}}}]
   :input-validators [(partial bulletin-can-be-saved :id :proclaimed)
                      (partial bulletins/validate-input-dates :proclamationStartsAt :proclamationEndsAt)]}
  [{{:keys       [bulletinVersionId proclamationEndsAt proclamationStartsAt proclamationText]
     bulletin-id :id} :data}]
  (let [updates {$set {"versions.$.proclamationEndsAt"   proclamationEndsAt
                       "versions.$.proclamationStartsAt" proclamationStartsAt
                       "versions.$.proclamationText"     proclamationText}}]
    (bulletins/update-bulletin bulletin-id {"versions" {$elemMatch {:id bulletinVersionId}}} updates)
    (ok)))

(defcommand save-verdict-given-bulletin
  {:description      "Updates verdict given version timestamps and text"
   :request-schema   {:id                   ssc/ApplicationId
                      :bulletinId           ssc/NonBlankStr
                      :bulletinVersionId    ssc/NonBlankStr
                      :verdictGivenAt       ssc/Timestamp
                      :appealPeriodStartsAt ssc/Timestamp
                      :appealPeriodEndsAt   ssc/Timestamp
                      :verdictGivenText     ssc/NonBlankStr}
   :permissions      [{:required [:application/edit-bulletin]
                       :context  {:application {:state #{:verdictGiven}}}}]
   :input-validators [application-matches-bulletin
                      (partial bulletin-can-be-saved :bulletinId :verdictGiven)
                      (partial bulletins/validate-input-dates :appealPeriodStartsAt :appealPeriodEndsAt)
                      (partial bulletins/validate-input-dates :verdictGivenAt :appealPeriodStartsAt)]}
  [{{:keys [bulletinId bulletinVersionId verdictGivenAt appealPeriodEndsAt
            appealPeriodStartsAt verdictGivenText]} :data}]
  (let [updates {$set {"versions.$.verdictGivenAt"       verdictGivenAt
                       "versions.$.appealPeriodEndsAt"   appealPeriodEndsAt
                       "versions.$.appealPeriodStartsAt" appealPeriodStartsAt
                       "versions.$.verdictGivenText"     verdictGivenText}}]
    (bulletins/update-bulletin bulletinId {"versions" {$elemMatch {:id bulletinVersionId}}} updates)
    (ok)))

(defraw download-bulletin-comment-attachment
  {:parameters       [attachmentId]
   :user-roles       #{:authority :applicant}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]}
  [{:keys [user]}]
  (attachment/output-attachment attachmentId true
                                (partial bulletins/get-bulletin-comment-attachment-file-as user)))

(defraw download-bulletin-doc
  {:description      "Downloads a given bulletin's verdict document.
  The bulletin must be currently publicly available.
  Note that unlike bulletin attachments, this doesn't require the user to be authenticated for rakval bulletins."
   :parameters       [bulletinId]
   :user-roles       #{:anonymous}
   :input-validators [{:bulletinId ssc/NonBlankStr}]}
  [command]
  (or (bulletins/download-bulletin-doc command bulletinId)
      (fail :error.not-found)))

(defquery ymp-publish-bulletin-enabled
  {:description    "Bulletin implementation for YMP permit types enabled"
   :request-schema {:id ssc/ApplicationId}
   :permissions    [{:required [:application/read]
                     :context  {:application {:permitType permit/ymp-permit-type-kw-set}}}]
   :pre-checks     [(action/not-pre-check (op/validate-primary-operation-is :ymparistoluvan-selventaminen))]}
  [_]
  (ok))

(defn- check-bulletin-op-description-required [{organization                          :organization
                                                {permit-type       :permitType
                                                 municipality      :municipality
                                                 primary-operation :primaryOperation} :application}]
  (let [organization (force organization)]
    (when (or (and organization
                   (:descriptions-from-backend-system (org/bulletin-settings-for-scope organization permit-type municipality)))
              (util/=as-kw :kiinteistonmuodostus (:name primary-operation)))
      unauthorized)))

(def ^:private full-permit-type-kw-set
  (->> (permit/permit-types)
       keys
       (mapv keyword)
       set))

(def non-ymp-permit-types (set/difference full-permit-type-kw-set
                                          permit/ymp-permit-type-kw-set))

(defquery bulletin-for-application-verdict-enabled
  {:description    "Pseudo-query for the application bulletin tab visibility."
   :request-schema {:id ssc/ApplicationId}
   :permissions    [{:required [:application/edit-bulletin]
                     :context  {:application {:permitType non-ymp-permit-types
                                              :state      states/post-sent-states}}}]
   :pre-checks     [notices-util/is-not-notice
                    bulletins/check-bulletins-enabled]}
  [_]
  (ok))

(defquery bulletin-will-be-published-for-verdict
  {:description    "Pseudo query that is authed for non-YMP applications with bulletins enabled."
   :request-schema {:id ssc/ApplicationId}
   :permissions    [{:required [:application/edit-bulletin]
                     :context  {:application {:permitType non-ymp-permit-types
                                              :state      #{:submitted :complementNeeded}}}}]
   :pre-checks     [bulletins/check-bulletins-enabled]}
  [_]
  (ok))

(defquery app-bulletin-op-description-required
  {:description    "Pseudoquery that controls whether or not a descriptive title for verdict bulletins is mandatory before approve-application"
   :request-schema {:id ssc/ApplicationId}
   :permissions    [{:required [:application/edit-bulletin]
                     :context  {:application {:permitType (disj non-ymp-permit-types :YA)
                                              :state      #{:submitted :complementNeeded}}}}]
   :pre-checks     [notices-util/is-not-notice
                    dmcity/check-dmcity-disabled
                    bulletins/check-bulletins-enabled
                    check-bulletin-op-description-required]}
  [_]
  (ok))

(defcommand update-app-bulletin-op-description
  {:description    "Update the main operation description for the bulletin."
   :request-schema {:id          ssc/ApplicationId
                    :description ssc/NonBlankStr}
   :permissions    [{:required [:application/edit-bulletin]
                     :context  {:application {:permitType non-ymp-permit-types
                                              :state      #{:submitted :sent}}}}]
   :pre-checks     [notices-util/is-not-notice
                    bulletins/check-bulletins-enabled]}
  [{:keys [data] :as command}]
  (->> data :description ss/trim
       (assoc-in {} [$set :bulletinOpDescription])
       (action/update-application command))
  (ok))

(defquery local-bulletins-page-settings
  {:parameters       [organization]
   :user-roles       #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:organization])]}
  [_]
  (let [org-data (org/get-organization organization)
        enabled  (some #(-> % :bulletins :enabled) (:scope org-data))]
    (ok :enabled enabled
        :texts (or (some-> org-data :local-bulletins-page-settings :texts)
                   org/default-bulletin-texts))))

(defquery local-kuulutus-page-settings
  {:parameters       [organization]
   :user-roles       #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:organization])]}
  [_]
  (let [org-data (org/get-organization organization)
        enabled  (some #(-> % :bulletins :enabled) (:scope org-data))]
    (ok :enabled enabled
        :texts (or (some-> org-data :local-kuulutus-page-settings :texts)
                   org/default-bulletin-texts))))
