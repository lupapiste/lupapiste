(ns lupapalvelu.application-bulletins
  (:require [monger.operators :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.set :refer [difference]]
            [sade.util :refer [fn->] :as util]
            [sade.core :refer :all]
            [lupapalvelu.attachment.metadata :as metadata]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.document.model :as model]))

(def bulletin-state-seq (sm/state-seq states/bulletin-version-states))

(defn bulletin-state [app-state]
  (condp contains? (keyword app-state)
    states/pre-verdict-states              :proclaimed
    (difference states/post-verdict-states
                states/terminal-states)    :verdictGiven
    #{:final}                              :final))

;; Query/Projection fields

(defn bulletins-fields []
  (let [now (now)]
    ;; It is important that this is array map, since versions-elemMatch projection
    ;; must be before versions-fields projection definition in mongo query.
    ;; Otherwise projection does not work properly.
    (array-map :versions {$elemMatch {$or [{:proclamationStartsAt {$gt now} :proclamationEndsAt {$lt now}}
                                           {:appealPeriodStartsAt {$gt now} :appealPeriodEndsAt {$lt now}}
                                           {:officialAt {$lt now}}]}}
               :versions.bulletinState 1
               :versions.state 1
               :versions.municipality 1
               :versions.address 1
               :versions.location 1
               :versions.primaryOperation 1
               :versions.propertyId 1
               :versions.applicant 1
               :versions.modified 1
               :versions.proclamationStartsAt 1 :versions.proclamationEndsAt 1
               :versions.proclamationText 1
               :versions.verdictGivenAt 1
               :versions.verdictGivenText 1
               :versions.appealPeriodStartsAt 1 :versions.appealPeriodEndsAt 1
               :versions.officialAt 1
               :versions.matti-verdicts.data 1
               :versions.matti-verdicts.category 1
               :modified 1
               :versions.bulletin-op-description 1)))

(defn bulletin-fields []
  (util/assoc-to-array-map
   (bulletins-fields)
   :versions._applicantIndex 1
   :versions.documents 1
   :versions.id 1
   :versions.attachments 1
   :versions.verdicts 1
   :versions.tasks 1
   :bulletinState 1))

;; Snapshot

(def app-snapshot-fields
  [:_applicantIndex :address :applicant :created :documents :location
   :modified :municipality :organization :permitType
   :primaryOperation :propertyId :state :verdicts :matti-verdicts :tasks])

(def attachment-snapshot-fields
  [:id :type :latestVersion :auth :metadata :contents :target])

(def remove-party-docs-fn
  (partial remove (fn-> :schema-info :type keyword (= :party))))

(defn create-bulletin-snapshot [application]
  (let [app-snapshot (-> application
                         (select-keys app-snapshot-fields)
                         (model/strip-blacklisted-data :bulletin)
                         (model/strip-turvakielto-data))
        app-snapshot (update-in
                       app-snapshot
                       [:documents]
                       remove-party-docs-fn)
        app-snapshot (update-in
                       app-snapshot
                       [:documents]
                       (partial map #(dissoc % :meta)))
        attachments (->> (:attachments application)
                         (filter #(and (:latestVersion %) (metadata/public-attachment? %)))
                         (map #(select-keys % attachment-snapshot-fields))
                         (map #(update % :latestVersion (fn [v] (select-keys v [:filename :contentType :fileId :size])))))
        app-snapshot (assoc app-snapshot
                       :id (mongo/create-id)
                       :attachments attachments
                       :bulletinState (bulletin-state (:state app-snapshot)))]
    app-snapshot))

(defn snapshot-updates [snapshot search-fields ts]
  {$push {:versions snapshot}
   $set  (merge {:modified ts} search-fields)})

(defn create-comment [bulletin-id version-id comment contact-info files created]
  (let [id          (mongo/create-id)
        new-comment {:id           id
                     :bulletinId   bulletin-id
                     :versionId    version-id
                     :comment      comment
                     :created      created
                     :contact-info contact-info
                     :attachments  files}]
    new-comment))

(defn get-bulletin
  ([bulletinId]
   (get-bulletin bulletinId (bulletin-fields)))
  ([bulletinId projection]
   (mongo/with-id (mongo/by-id :application-bulletins bulletinId projection))))

(defn get-bulletin-by-query
  ([query]
    (get-bulletin-by-query query {}))
  ([query projection]
    (mongo/select-one :application-bulletins query projection)))

(defn get-bulletin-attachment [attachment-id]
  (when-let [attachment-file (mongo/download attachment-id)]
    (when-let [bulletin (get-bulletin (:application attachment-file))]
      (when (seq bulletin) attachment-file))))

(defn get-bulletin-comment-attachment-file-as
  "Returns the attachment file if user has access to application, otherwise nil."
  [user file-id]
  (when-let [attachment-file (mongo/download file-id)]
    (when-let [application (lupapalvelu.domain/get-application-as (get-in attachment-file [:metadata :bulletinId]) user :include-canceled-apps? true)]
      (when (seq application) attachment-file))))

;;
;; Updates
;;

(defn update-bulletin
  "Updates bulletin by query with optional opts"
  [bulletin-id mongo-query changes & options]
  (apply mongo/update-by-query
         :application-bulletins
         (assoc mongo-query :_id bulletin-id)
         changes
         options))

(defn upsert-bulletin-by-id
  "Updates bulletin with upsert set to true."
  [bulletin-id changes]
  (update-bulletin bulletin-id {} changes :upsert true))

(defn update-file-metadata [bulletin-id comment-id files]
  (mongo/update-file-by-query {:_id {$in (map :fileId files)}} {$set {:metadata.linked     true
                                                                      :metadata.bulletinId bulletin-id
                                                                      :metadata.commentId  comment-id}}))

;;;
;;; Date checkers
;;;

(defn bulletin-date-in-period?
  [startdate-kw enddate-kw bulletin-version]
  {:pre [(contains? bulletin-version startdate-kw)
         (contains? bulletin-version enddate-kw)]}
  (let [[starts ends] (->> (util/select-values bulletin-version [startdate-kw enddate-kw])
                           (map c/from-long))
        ends     (t/plus ends (t/days 1))]
    (t/within? (t/interval starts ends) (c/from-long (now)))))

(defn validate-input-dates [startdate-kw enddate-kw command]
  (let [start (get-in command [:data startdate-kw])
        end   (get-in command [:data enddate-kw])]
    (when-not (< start end)
      (fail :error.startdate-before-enddate))))

(defn bulletin-version-date-valid?
  "Verify temporal visibility for bulletin version by state"
  [{state :bulletinState :as bulletin-version}]
  (let [now (now)]
    (case (keyword state)
      :proclaimed   (and (< (:proclamationStartsAt bulletin-version) now)
                         (> (:proclamationEndsAt bulletin-version) now))
      :verdictGiven (and (< (:appealPeriodStartsAt bulletin-version) now)
                         (> (:appealPeriodEndsAt bulletin-version) now))
      :final        (< (:officialAt bulletin-version) now))))

(defn bulletin-date-valid?
  "Verify that bulletin visibility date is less than current timestamp"
  [{bulletin-versions :versions}]
  (some bulletin-version-date-valid? bulletin-versions))

(defn verdict-given-bulletin-exists? [app-id]
  (mongo/any? :application-bulletins {:_id app-id :versions {"$elemMatch" {:bulletinState "verdictGiven"}}}))

(defn verdict-bulletin-should-not-exist [{{:keys [id]} :data}]
  (when id
    (when (verdict-given-bulletin-exists? id)
      (fail :error.bulletin.verdict-given-bulletin-exists))))

(defn validate-bulletin-verdict-state [{{:keys [id]} :data}]
  (when id
    (when-not (verdict-given-bulletin-exists? id)
      (fail :error.bulletin.missing-verdict-given-bulletin))))

(defn validate-official-at
  "Official at date can't be before appealing period has ended"
  [{{:keys [officialAt id]} :data}]
  (when (and (number? officialAt) (string? id))
    (when-let [bulletin (get-bulletin-by-query {:_id id :bulletinState :verdictGiven} [:versions])]
      (when (< officialAt
               (->> bulletin :versions (util/find-first #(= "verdictGiven" (:bulletinState %))) :appealPeriodEndsAt))
        (fail :error.bulletin.official-before-appeal-period)))))
