(ns lupapalvelu.application-bulletins-api
  (:require [taoensso.timbre :refer [trace debug debugf info warn warnf error errorf fatal]]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.action :refer [defquery defcommand defraw] :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]
            [monger.operators :refer :all]
            [lupapalvelu.application-search :refer [make-text-query dir]]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.organization :as org]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [lupapalvelu.states :as states]
            [lupapalvelu.application-bulletin-utils :as bulletin-utils]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.application-bulletin-utils :as bulletin-utils]))

(def bulletin-page-size 10)

(defn- make-query [{:keys [searchText municipality organization state]}]
  (let [queries (remove empty? [(when-not (ss/blank? searchText)
                               (make-text-query (ss/trim searchText)))
                             (when-not (ss/blank? municipality)
                               {:versions.municipality municipality})
                             (when-not (ss/blank? organization)
                               {:versions.organization organization})
                             (when-not (ss/blank? state)
                               {:versions.bulletinState state})])]
    (when-let [and-query (seq queries)]
      {$and and-query})))

(def- sort-field-mapping {"bulletinState" :versions.bulletinState
                          "municipality" :versions.municipality
                          "address" :versions.address
                          "applicant" :versions.applicant
                          "modified" :versions.modified})

(def- default-sort {:versions.modified -1})

(defn- make-sort [{:keys [field asc]}]
  (let [sort-field (sort-field-mapping field)]
    (cond
      (nil? sort-field) default-sort
      (sequential? sort-field) (apply array-map (interleave sort-field (repeat (dir asc))))
      :else (array-map sort-field (dir asc)))))

(defn get-application-bulletins
  "Queries bulletins from mongo. Returns latest versions of bulletins.
   Bulletins which have starting dates in the future will be omitted."
  [{:keys [page sort] :as parameters} now-ts]
  (let [officialAt-lowerLimit (tc/to-long (t/minus (tc/from-long now-ts) (t/days 14)))
        query (or (make-query parameters) {})
        page (cond
               (string? page) (read-string page)
               :default page)
        apps (mongo/aggregate "application-bulletins"
               [{"$match" (bulletins/versions-elemMatch now-ts officialAt-lowerLimit)}
                {"$unwind" {:path "$versions"}}
                {"$match" (bulletins/version-elemMatch now-ts officialAt-lowerLimit)}
                {"$group" {:_id "$_id"
                           :versions {"$last" "$$ROOT.versions"},
                           :modified {"$last" "$$ROOT.modified"},
                           :address {"$last" "$$ROOT.address"},
                           :_applicantIndex {"$last" "$$ROOT._applicantIndex"}}}
                {"$match" query}
                {"$project" bulletins/bulletins-fields}
                {"$sort" (make-sort sort)}])
        apps (map #(assoc (:versions %) :id (:_id %)) apps)
        apps (map (fn [{:keys [category] :as app}] (if (#{"ymp"} category)
                                                     app
                                                     (dissoc app :applicant))) apps)
        skip (* (dec page) bulletin-page-size)]
    {:left (- (count apps) skip bulletin-page-size)
     :data (->> apps (drop skip) (take bulletin-page-size))}))

(defn- page-size-validator [{{page :page} :data}]
  (when (> (* page bulletin-page-size) (Integer/MAX_VALUE))
    (fail :error.page-is-too-big)))

(defn- search-text-validator [{{:keys [searchText]} :data}]
  (when (> (count searchText) (env/value :search-text-max-length))
    (fail :error.search-text-is-too-long)))

(defquery application-bulletins
  {:description "Query for Julkipano"
   :parameters [page searchText municipality state sort]
   :input-validators [(partial action/number-parameters [:page])
                      page-size-validator
                      search-text-validator]
   :user-roles #{:anonymous}}
  [{data :data now-ts :created}]
  (let [{:keys [data left]} (get-application-bulletins data now-ts)]
    (ok :data data
        :left left)))

(defquery local-application-bulletins
  {:parameters [searchText organization]
   :input-validators [search-text-validator]
   :user-roles #{:anonymous}}
  [{data :data now-ts :created}]
  (let [{:keys [data left]} (get-application-bulletins data now-ts)]
    (ok :data data
        :left left)))

(defquery application-bulletin-municipalities
  {:description "List of distinct municipalities of application bulletins"
   :parameters []
   :user-roles #{:anonymous}}
  [_]
  (let [municipalities (mongo/distinct :application-bulletins :versions.municipality)]
    (ok :municipalities municipalities)))

(defquery application-bulletin-states
  {:description "List of distinct states of application bulletins"
   :parameters []
   :user-roles #{:anonymous}}
  [_]
  (let [states (mongo/distinct :application-bulletins :versions.bulletinState)]
    (ok :states states)))

(defn- bulletin-version-is-latest [bulletin bulletin-version-id]
  (let [latest-version-id (:id (last (filter bulletin-utils/bulletin-version-date-valid? (:versions bulletin))))]
    (when-not (= bulletin-version-id latest-version-id)
      (fail :error.invalid-version-id))))

(defn- bulletin-can-be-commented
  ([{{bulletin-id :bulletinId} :data}]
   (let [projection {:bulletinState 1 "versions.proclamationStartsAt" 1 "versions.proclamationEndsAt" 1 :versions {$slice -1}}
         bulletin   (bulletins/get-bulletin bulletin-id projection)]
     (if-not (and (= (:bulletinState bulletin) "proclaimed")
                  (bulletins/bulletin-date-in-period? :proclamationStartsAt :proclamationEndsAt (-> bulletin :versions last)))
       (fail :error.bulletin-not-in-commentable-state)))))

(defn- comment-can-be-added
  [{{bulletin-id :bulletinId bulletin-version-id :bulletinVersionId comment :comment} :data}]
  (if (ss/blank? comment)
    (fail :error.empty-comment)
    (let [projection {:bulletinState 1 :versions 1}
          bulletin (bulletins/get-bulletin bulletin-id projection)]
      (if-not bulletin
        (fail :error.invalid-bulletin-id)
        (if-not (= (:bulletinState bulletin) "proclaimed")
          (fail :error.invalid-bulletin-state)
          (bulletin-version-is-latest bulletin bulletin-version-id))))))

(defn- referenced-file-can-be-attached
  [{{files :files} :data}]
  (when-not (storage/unlinked-files-exist? (vetuma/session-id) (map :fileId files))
    (fail :error.invalid-files-attached-to-comment)))

(def delivery-address-fields #{:firstName :lastName :street :zip :city})

(defn validate-uploaded-files [command]
  (when-let [files (seq (get-in command [:data :files]))]
    (when-let [err (some bulletins/comment-file-checker files)]
      (warnf "schema validation error in add-bulletin-comment: %s" (pr-str err))
      (fail :error.illegal-value:schema-validation :source ::validate-uploaded-files))))

(defcommand add-bulletin-comment
  {:description      "Add comment to bulletin"
   :pre-checks       [comment-can-be-added
                      vetuma/session-pre-check]
   :input-validators [referenced-file-can-be-attached
                      validate-uploaded-files]
   :user-roles       #{:anonymous}}
  [{{files :files bulletin-id :bulletinId comment :comment bulletin-version-id :bulletinVersionId
     email :email emailPreferred :emailPreferred otherReceiver :otherReceiver} :data created :created}]
  (let [address-source (or otherReceiver (get-in (vetuma/vetuma-session) [:user]))
        delivery-address (select-keys address-source delivery-address-fields)
        contact-info (merge delivery-address {:email          email
                                              :emailPreferred emailPreferred})
        comment (bulletins/create-comment bulletin-id bulletin-version-id comment contact-info files created)]
    (mongo/insert :application-bulletin-comments comment)
    (when (seq files)
      (storage/link-files-to-bulletin (vetuma/session-id) bulletin-id (map :fileId files)))
    (ok)))

(defcommand move-to-proclaimed
  {:parameters [id proclamationEndsAt proclamationStartsAt proclamationText]
   :input-validators [(partial action/non-blank-parameters [:id :proclamationText])
                      (partial action/number-parameters [:proclamationStartsAt :proclamationEndsAt])
                      (partial bulletins/validate-input-dates :proclamationStartsAt :proclamationEndsAt)]
   :user-roles #{:authority}
   :states     #{:sent :complementNeeded}
   :pre-checks [(permit/validate-permit-type-is permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)]}
  [{:keys [application created]}]
  (let [updates (bulletins/create-bulletin application created {:proclamationEndsAt proclamationEndsAt
                                                      :proclamationStartsAt proclamationStartsAt
                                                      :proclamationText proclamationText})]
    (bulletins/upsert-bulletin-by-id id updates)
    (ok)))

(defcommand move-to-verdict-given
  {:parameters [id verdictGivenAt appealPeriodStartsAt appealPeriodEndsAt verdictGivenText]
   :input-validators [(partial action/non-blank-parameters [:id :verdictGivenText])
                      (partial action/number-parameters [:verdictGivenAt :appealPeriodStartsAt :appealPeriodEndsAt])
                      (partial bulletins/validate-input-dates :appealPeriodStartsAt :appealPeriodEndsAt)]
   :user-roles #{:authority}
   :states     #{:verdictGiven}
   :pre-checks [(permit/validate-permit-type-is permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)
                bulletins/verdict-bulletin-should-not-exist]}
  [{:keys [application created]}]
  (let [updates (bulletins/create-bulletin application created {:verdictGivenAt verdictGivenAt
                                                      :appealPeriodStartsAt appealPeriodStartsAt
                                                      :appealPeriodEndsAt appealPeriodEndsAt
                                                      :verdictGivenText verdictGivenText})]
    (bulletins/upsert-bulletin-by-id id updates)
    (ok)))

(defcommand move-to-final
  {:parameters [id officialAt]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/number-parameters [:officialAt])]
   :user-roles #{:authority}
   :states     #{:verdictGiven}
   :pre-checks [(permit/validate-permit-type-is permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)
                bulletins/validate-bulletin-verdict-state
                bulletins/validate-official-at]}
  [{:keys [application created]}]
  ; Note there is currently no way to move application to final state so we sent bulletin state manuall
  (let [updates (bulletins/create-bulletin application created {:officialAt officialAt
                                                      :bulletinState :final})]
    (bulletins/upsert-bulletin-by-id id updates)
    (ok)))

(defn bulletin-exists [{{bulletin-id :bulletinId} :data}]
  (when-not (mongo/any? :application-bulletins {:_id bulletin-id})
    (fail :error.bulletin.not-found)))

(defquery bulletin
  "return only latest version for application bulletin"
  {:parameters [bulletinId]
   :input-validators [(partial action/non-blank-parameters [:bulletinId]) bulletin-exists]
   :user-roles #{:anonymous}}
  [command]
  (if-let [bulletin (bulletins/get-bulletin bulletinId)]
    (let [latest-version       (->> bulletin :versions
                                    (filter (partial bulletin-utils/bulletin-version-date-valid? (:created command))) last)
          bulletin-version     (assoc latest-version :versionId (:id latest-version)
                                                     :id (:id bulletin))
          append-schema-fn     (fn [{schema-info :schema-info :as doc}]
                                 (assoc doc :schema (schemas/get-schema schema-info)))
          bulletin             (-> bulletin-version
                                   (domain/filter-application-content-for {})
                                   ; unset keys (with empty values) set by filter-application-content-for
                                   (dissoc :comments :neighbors :statements :company-notes)
                                   (update-in [:documents] (partial map append-schema-fn))
                                   (update-in [:attachments] (partial map #(dissoc % :metadata :auth)))
                                   (assoc :stateSeq bulletins/bulletin-state-seq))
          bulletin             (if (#{"ymp"} (:category bulletin))
                                 bulletin
                                 (dissoc bulletin :applicant :_applicantIndex))
          bulletin-commentable (nil? (bulletin-can-be-commented command))]
      (ok :bulletin (merge bulletin {:canComment bulletin-commentable})))
    (fail :error.bulletin.not-found)))

(defn- count-comments [version]
  (let [comment-count (mongo/count :application-bulletin-comments {:versionId (:id version)})]
    (assoc version :comments comment-count)))

(defquery bulletin-versions
  "returns all bulletin versions for application bulletin with comments"
  {:parameters [bulletinId]
   :input-validators [(partial action/non-blank-parameters [:bulletinId])]
   :user-roles #{:authority :applicant}}
  (let [bulletin-fields (-> bulletins/bulletins-fields
                            (dissoc :versions)
                            (merge {:comments 1
                                    :versions.id 1
                                    :bulletinState 1}))
        bulletin (bulletins/get-bulletin bulletinId bulletin-fields)
        versions (map count-comments (:versions bulletin))
        bulletin (assoc bulletin :versions versions)]
    (ok :bulletin bulletin)))

(defquery bulletin-comments
  "returns paginated comments related to given version id"
  {:parameters [bulletinId versionId]
   :input-validators [(partial action/non-blank-parameters [:bulletinId :versionId])]
   :user-roles #{:authority :applicant}}
  [{{skip :skip limit :limit asc :asc} :data}]
  (let [skip           (util/->int skip)
        limit          (util/->int limit)
        sort           (if (= "false" asc) {:created -1} {:created 1})
        comments       (mongo/with-collection "application-bulletin-comments"
                         (query/find  {:versionId versionId})
                         (query/sort  sort)
                         (query/skip  skip)
                         (query/limit limit))
        total-comments (mongo/count :application-bulletin-comments {:versionId versionId})
        comments-left  (max 0 (- total-comments (+ skip (count comments))))]
    (ok :comments comments :totalComments total-comments :commentsLeft comments-left)))

(defn- bulletin-can-be-saved
  ([state {{bulletin-id :bulletinId bulletin-version-id :bulletinVersionId} :data}]
   (let [bulletin (bulletins/get-bulletin bulletin-id)]
     (if-not bulletin
       (fail :error.invalid-bulletin-id)
       (if-not (= (:bulletinState bulletin) state)
         (fail :error.invalid-bulletin-state)
         (bulletin-version-is-latest bulletin bulletin-version-id)))))
  ([state command _]
   (bulletin-can-be-saved state command)))

(defcommand save-proclaimed-bulletin
  "updates proclaimed version timestamps and text"
  {:parameters [bulletinId bulletinVersionId proclamationEndsAt proclamationStartsAt proclamationText]
   :user-roles #{:authority}
   :states     #{:sent :complementNeeded}
   :input-validators [(partial action/non-blank-parameters [:bulletinId :bulletinVersionId])
                      (partial bulletin-can-be-saved "proclaimed")
                      (partial action/number-parameters [:proclamationStartsAt :proclamationEndsAt])
                      (partial bulletins/validate-input-dates :proclamationStartsAt :proclamationEndsAt)]}
  [_]
  (let [updates {$set {"versions.$.proclamationEndsAt"   proclamationEndsAt
                       "versions.$.proclamationStartsAt" proclamationStartsAt
                       "versions.$.proclamationText"     proclamationText}}]
    (bulletins/update-bulletin bulletinId {"versions" {$elemMatch {:id bulletinVersionId}}} updates)
    (ok)))

(defcommand save-verdict-given-bulletin
  "updates verdict given version timestamps and text"
  {:parameters [bulletinId bulletinVersionId verdictGivenAt appealPeriodEndsAt appealPeriodStartsAt verdictGivenText]
   :user-roles #{:authority}
   :states     #{:verdictGiven}
   :input-validators [(partial action/non-blank-parameters [:bulletinId :bulletinVersionId :verdictGivenText])
                      (partial bulletin-can-be-saved "verdictGiven")
                      (partial action/number-parameters [:verdictGivenAt :appealPeriodStartsAt :appealPeriodEndsAt])
                      (partial bulletins/validate-input-dates :appealPeriodStartsAt :appealPeriodEndsAt)]}
  [_]
  (let [updates {$set {"versions.$.verdictGivenAt"       verdictGivenAt
                       "versions.$.appealPeriodEndsAt"   appealPeriodEndsAt
                       "versions.$.appealPeriodStartsAt" appealPeriodStartsAt
                       "versions.$.verdictGivenText"     verdictGivenText}}]
    (bulletins/update-bulletin bulletinId {"versions" {$elemMatch {:id bulletinVersionId}}} updates)
    (ok)))

(defraw "download-bulletin-comment-attachment"
  {:parameters [attachmentId]
   :user-roles #{:authority :applicant}
   :input-validators [(partial action/non-blank-parameters [:attachmentId])]}
  [{:keys [user] :as command}]
  (attachment/output-attachment attachmentId true
                                            (partial bulletins/get-bulletin-comment-attachment-file-as user)))

(defquery ymp-publish-bulletin-enabled
  {:description "Bulletin implementation for YMP permit types enabled"
   :parameters [id]
   :user-roles #{:authority :applicant}
   :pre-checks [(permit/validate-permit-type-is permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)]})

(defn- check-bulletins-enabled [{organization :organization {permit-type :permitType municipality :municipality :as app} :application}]
  (when (and organization (or (not (org/bulletins-enabled? @organization permit-type municipality))
                              (not (bulletins/bulletin-enabled-for-application-operation? app))))
    (fail :error.bulletins-not-enebled-for-scope)))

(defn- check-bulletin-op-description-required [{organization :organization {permit-type :permitType municipality :municipality} :application}]
  (when (and organization (:descriptions-from-backend-system (org/bulletin-settings-for-scope @organization permit-type municipality)))
    unauthorized))

(defquery bulletin-for-application-verdict-enabled
  {:description ""
   :parameters  [id]
   :user-roles  #{:authority}
   :states      states/post-sent-states
   :pre-checks  [(permit/validate-permit-type-is-not permit/YI permit/YL permit/YM permit/VVVL permit/MAL)
                 check-bulletins-enabled]})

(defquery bulletin-will-be-published-for-verdict
  {:description ""
   :parameters  [id]
   :user-roles  #{:authority}
   :states      #{:submitted :complementNeeded}
   :pre-checks  [(permit/validate-permit-type-is-not permit/YI permit/YL permit/YM permit/VVVL permit/MAL)
                 check-bulletins-enabled]})

(defquery app-bulletin-op-description-required
  {:description "Pseudoquery that controls whether or not a descriptive title for verdict bulletins is mandatory before approve-application"
   :parameters  [id]
   :user-roles  #{:authority}
   :states      #{:submitted :complementNeeded}
   :pre-checks  [(permit/validate-permit-type-is-not permit/YI permit/YL permit/YM permit/VVVL permit/MAL)
                 check-bulletins-enabled
                 check-bulletin-op-description-required]})

(defcommand update-app-bulletin-op-description
  {:parameters [id description]
   :user-roles #{:authority}
   :states     #{:submitted :sent :complementNeeded}
   :pre-checks [(permit/validate-permit-type-is-not permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)
                check-bulletins-enabled]}
  [command]
  (action/update-application command {"$set" {:bulletinOpDescription description}})
  (ok))

(defquery local-bulletins-page-settings
  {:parameters [organization]
   :user-roles #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:organization])]}
  [_]
  (let [org-data (org/get-organization organization)
        enabled  (some #(-> % :bulletins :enabled) (:scope org-data))]
    (ok :enabled enabled
        :texts (-> org-data :local-bulletins-page-settings :texts))))
