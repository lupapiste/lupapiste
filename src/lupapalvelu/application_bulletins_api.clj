(ns lupapalvelu.application-bulletins-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [sade.util :as util]
            [slingshot.slingshot :refer [try+]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand defraw] :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]
            [monger.operators :refer :all]
            [lupapalvelu.application-search :refer [make-text-query dir]]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.permit :as permit]))

(def bulletin-page-size 10)

(defn- make-query [search-text municipality state]
  (let [text-query         (when-not (ss/blank? search-text)
                             (make-text-query (ss/trim search-text)))
        municipality-query (when-not (ss/blank? municipality)
                             {:versions.municipality municipality})
        state-query        (when-not (ss/blank? state)
                             {:versions.bulletinState state})
        queries            (filter seq [text-query municipality-query state-query])]
    (when-let [and-query (seq queries)]
      {$and and-query})))

(defn- get-application-bulletins-left [page searchText municipality state _]
  (let [query (make-query searchText municipality state)]
    (- (mongo/count :application-bulletins query)
       (* page bulletin-page-size))))

(def- sort-field-mapping {"bulletinState" :bulletinState
                          "municipality" :municipality
                          "address" :address
                          "applicant" :applicant
                          "modified" :modified})

(defn- make-sort [{:keys [field asc]}]
  (let [sort-field (sort-field-mapping field)]
    (cond
      (nil? sort-field) {}
      (sequential? sort-field) (apply array-map (interleave sort-field (repeat (dir asc))))
      :else (array-map sort-field (dir asc)))))

(defn- get-application-bulletins
  "Queries bulletins from mongo. Returns latest versions of bulletins.
   Bulletins which have starting dates in the future will be omitted."
  [page searchText municipality state sort]
  (let [query (or (make-query searchText municipality state) {})
        apps (mongo/with-collection "application-bulletins"
               (query/find query)
               (query/fields bulletins/bulletins-fields)
               (query/sort (make-sort sort))
               (query/paginate :page page :per-page bulletin-page-size))]
    (->> apps
         (map #(assoc (first (:versions %)) :id (:_id %)))
         (filter bulletins/bulletin-date-valid?))))

(defn- page-size-validator [{{page :page} :data}]
  (when (> (* page bulletin-page-size) (Integer/MAX_VALUE))
    (fail :error.page-is-too-big)))

(defquery application-bulletins
  {:description "Query for Julkipano"
   :parameters [page searchText municipality state sort]
   :input-validators [(partial action/number-parameters [:page])
                      page-size-validator]
   :user-roles #{:anonymous}}
  [_]
  (let [parameters [page searchText municipality state sort]]
    (ok :data (apply get-application-bulletins parameters)
        :left (apply get-application-bulletins-left parameters))))

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
  (let [latest-version-id (:id (last (:versions bulletin)))]
    (when-not (= bulletin-version-id latest-version-id)
      (fail :error.invalid-version-id))))

(defn- comment-can-be-added
  [{{bulletin-id :bulletinId bulletin-version-id :bulletinVersionId comment :comment} :data}]
  (if (ss/blank? comment)
    (fail :error.empty-comment)
    (let [projection {:bulletinState 1 :versions {$slice -1} "versions.id" 1}
          bulletin (bulletins/get-bulletin bulletin-id projection)]
      (if-not bulletin
        (fail :error.invalid-bulletin-id)
        (if-not (= (:bulletinState bulletin) "proclaimed")
          (fail :error.invalid-bulletin-state)
          (bulletin-version-is-latest bulletin bulletin-version-id))))))

(defn- referenced-file-can-be-attached
  [{{files :files} :data}]
  (let [files-found (map #(mongo/any? :fs.files {:_id (:fileId %) "metadata.sessionId" (vetuma/session-id)}) files)]
    (when-not (every? true? files-found)
      (fail :error.invalid-files-attached-to-comment))))

(defn- bulletin-can-be-commented
  ([{{bulletin-id :bulletinId} :data}]
   (let [projection {:bulletinState 1 "versions.proclamationStartsAt" 1 "versions.proclamationEndsAt" 1 :versions {$slice -1}}
         bulletin   (bulletins/get-bulletin bulletin-id projection)]
     (if-not (and (= (:bulletinState bulletin) "proclaimed")
                  (bulletins/bulletin-date-in-period? :proclamationStartsAt :proclamationEndsAt (-> bulletin :versions last)))
       (fail :error.bulletin-not-in-commentable-state))))
  ([command _]
    (bulletin-can-be-commented command)))

(def delivery-address-fields #{:firstName :lastName :street :zip :city})

(defn- user-is-vetuma-authenticated [_ _]
  (when (empty? (vetuma/vetuma-session))
    (fail :error.user-not-vetuma-authenticated)))

(defcommand add-bulletin-comment
  {:description      "Add comment to bulletin"
   :pre-checks       [bulletin-can-be-commented user-is-vetuma-authenticated]
   :input-validators [comment-can-be-added referenced-file-can-be-attached]
   :user-roles       #{:anonymous}}
  [{{files :files bulletin-id :bulletinId comment :comment bulletin-version-id :bulletinVersionId
     email :email emailPreferred :emailPreferred otherReceiver :otherReceiver :as data} :data created :created :as action}]
  (let [address-source (if otherReceiver
                         otherReceiver
                         (get-in (vetuma/vetuma-session) [:user]))
        delivery-address (select-keys address-source delivery-address-fields)
        contact-info (merge delivery-address {:email          email
                                              :emailPreferred emailPreferred})
        comment (bulletins/create-comment bulletin-id bulletin-version-id comment contact-info files created)]
    (mongo/insert :application-bulletin-comments comment)
    (bulletins/update-file-metadata bulletin-id (:id comment) files)
    (ok)))

(defn- get-search-fields [fields app]
  (into {} (map #(hash-map % (% app)) fields)))

(defn- create-bulletin [application created & [updates]]
  (let [app-snapshot (bulletins/create-bulletin-snapshot application)
        app-snapshot (if updates
                       (merge app-snapshot updates)
                       app-snapshot)
        search-fields [:municipality :address :verdicts :_applicantIndex :bulletinState :applicant]
        search-updates (get-search-fields search-fields app-snapshot)]
    (bulletins/snapshot-updates app-snapshot search-updates created)))

(defcommand move-to-proclaimed
  {:parameters [id proclamationEndsAt proclamationStartsAt proclamationText]
   :input-validators [(partial action/non-blank-parameters [:id :proclamationText])
                      (partial action/number-parameters [:proclamationStartsAt :proclamationEndsAt])
                      (partial bulletins/validate-input-dates :proclamationStartsAt :proclamationEndsAt)]
   :user-roles #{:authority}
   :states     #{:sent :complementNeeded}
   :pre-checks [(permit/validate-permit-type-is permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)]}
  [{:keys [application created] :as command}]
  (let [updates (create-bulletin application created {:proclamationEndsAt proclamationEndsAt
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
   :pre-checks [(permit/validate-permit-type-is permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)]}
  [{:keys [application created] :as command}]
  (let [updates (create-bulletin application created {:verdictGivenAt verdictGivenAt
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
   :pre-checks [(permit/validate-permit-type-is permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)]}
  [{:keys [application created] :as command}]
  ; Note there is currently no way to move application to final state so we sent bulletin state manuall
  (let [updates (create-bulletin application created {:officialAt officialAt
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
    (let [latest-version       (-> bulletin :versions first)
          bulletin-version     (assoc latest-version :versionId (:id latest-version)
                                                     :id (:id bulletin))
          append-schema-fn     (fn [{schema-info :schema-info :as doc}]
                                 (assoc doc :schema (schemas/get-schema schema-info)))
          bulletin             (-> bulletin-version
                                   (domain/filter-application-content-for {})
                                   ; unset keys (with empty values) set by filter-application-content-for
                                   (dissoc :comments :neighbors :statements)
                                   (update-in [:documents] (partial map append-schema-fn))
                                   (assoc :stateSeq bulletins/bulletin-state-seq))
          bulletin-commentable (= (bulletin-can-be-commented command) nil)]
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
                      (partial action/number-parameters [:proclamationStartsAt :proclamationEndsAt])]}
  [{:keys [application created] :as command}]
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
                      (partial action/number-parameters [:verdictGivenAt :appealPeriodStartsAt :appealPeriodEndsAt])]}
  [{:keys [application created] :as command}]
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
  [{:keys [application user] :as command}]
  (attachment/output-attachment attachmentId true
                                            (partial bulletins/get-bulletin-comment-attachment-file-as user)))

(defquery "publish-bulletin-enabled"
  {:parameters [id]
   :user-roles #{:authority :applicant}
   :pre-checks [(permit/validate-permit-type-is permit/YI permit/YL permit/YM permit/VVVL  permit/MAL)]})
