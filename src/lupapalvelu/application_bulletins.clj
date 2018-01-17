(ns lupapalvelu.application-bulletins
  (:require [taoensso.timbre :refer [infof]]
            [monger.operators :refer :all]
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
            [lupapalvelu.document.model :as model]
            [schema.core :as sc]
            [sade.schemas :as ssc]
            [clj-time.coerce :as tc]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [sade.strings :as ss]))

(sc/defschema ApplicationBulletin
  {:id             sc/Str
   :versions       [sc/Any]
   :application-id ssc/ApplicationId
   :address        sc/Str
   :bulletinOpDescription sc/Str
   :bulletinState  sc/Any
   :applicant       sc/Str
   :_applicantIndex sc/Str
   :municipality    sc/Str
   :organization    sc/Str
   :modified        ssc/Timestamp
   :verdictCategory sc/Str
   (sc/optional-key :verdicts) [sc/Any]
   (sc/optional-key :pate-verdict) sc/Any})

(sc/defschema CommentFile
  {:fileId ssc/ObjectIdStr
   :filename sc/Str
   :size sc/Int
   :contentType sc/Str})

(def comment-file-checker (sc/checker CommentFile))

(def bulletin-state-seq (sm/state-seq states/bulletin-version-states))

(defn bulletin-state [app-state]
  (condp contains? (keyword app-state)
    states/pre-verdict-states              :proclaimed
    (difference states/post-verdict-states
                states/terminal-states)    :verdictGiven
    #{:final}                              :final))

;; Query/Projection fields

(defn versions-elemMatch
  ([now-ts]
   (versions-elemMatch now-ts (tc/to-long (t/minus (tc/from-long now-ts) (t/days 14)))))
  ([now-ts officialAt-lowerLimit]
    {:versions {$elemMatch
                {$or [{:bulletinState :proclaimed
                       :proclamationStartsAt {$lt now-ts} :proclamationEndsAt {$gt now-ts}}
                      {:bulletinState :verdictGiven
                       :appealPeriodStartsAt {$lt now-ts} :appealPeriodEndsAt {$gt now-ts}}
                      {:bulletinState :final
                       :officialAt {$lt now-ts
                                    $gt officialAt-lowerLimit}}]}}}))

(def bulletins-fields
  {:versions.bulletinState 1
   :versions.state 1 :versions.municipality 1
   :versions.address 1 :versions.location 1
   :versions.primaryOperation 1 :versions.propertyId 1
   :versions.applicant 1 :versions.modified 1
   :versions.proclamationEndsAt 1 :versions.proclamationStartsAt 1
   :versions.proclamationText 1
   :versions.verdictGivenAt 1 :versions.appealPeriodStartsAt 1
   :versions.appealPeriodEndsAt 1 :versions.verdictGivenText 1
   :versions.officialAt 1 :versions.category 1
   :versions.verdictData 1 :versions.application-id 1
   :modified 1
   :versions.bulletinOpDescription 1})

(def bulletin-fields
  (merge bulletins-fields
         {:versions._applicantIndex 1
          :versions.documents 1
          :versions.id 1
          :versions.attachments 1
          :versions.verdicts 1
          :versions.tasks 1
          :bulletinState 1}))

;; Snapshot

(def app-snapshot-fields
  [:_applicantIndex :address :applicant :created :documents :location
   :modified :municipality :organization :permitType :bulletinOpDescription
   :primaryOperation :propertyId :state :verdicts :pate-verdict :tasks])

(def attachment-snapshot-fields
  [:id :type :latestVersion :auth :metadata :contents :target])

(def remove-party-docs-fn
  (partial remove (fn-> :schema-info :type keyword (= :party))))

(defn create-bulletin-snapshot [{pate-verdict :pate-verdict [verdict & _] :verdicts permitType :permitType
                                applicationId :id :as application}]
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
        verdict-data (cond
                       pate-verdict   {:section (:verdict-section pate-verdict)
                                        :code    (:verdict-code pate-verdict)}
                       verdict         {:section  (-> verdict :paatokset first :poytakirjat first :pykala)
                                        :status   (-> verdict :paatokset first :poytakirjat first :status)
                                        :contact  (-> verdict :paatokset first :poytakirjat first :paatoksentekija)
                                        :text     (-> verdict :paatokset first :poytakirjat last :paatos)})
        attachments (->> (:attachments application)
                         (filter #(and (:latestVersion %) (metadata/public-attachment? %)))
                         (map #(select-keys % attachment-snapshot-fields))
                         (map #(update % :latestVersion (fn [v] (select-keys v [:filename :contentType :fileId :size])))))
        app-snapshot (assoc app-snapshot
                       :id (mongo/create-id)
                       :application-id applicationId
                       :attachments attachments
                       :verdictData verdict-data
                       :category (cond
                                   (permit/ymp-permit-type? (:permitType application)) "ymp"
                                   pate-verdict (:category pate-verdict)
                                   :default (ss/lower-case (name permitType)))
                       :bulletinState (bulletin-state (:state app-snapshot)))]
    app-snapshot))

(defn snapshot-updates [snapshot search-fields ts]
  {$push {:versions snapshot}
   $set  (merge {:modified ts} search-fields)})

(defn get-search-fields [fields app]
  (into {} (map #(hash-map % (% app)) fields)))

(sc/defn ^:always-validate create-bulletin [application created & [updates]] :- ApplicationBulletin
  (let [app-snapshot (create-bulletin-snapshot application)
       app-snapshot (if updates
                      (merge app-snapshot updates)
                      app-snapshot)
       search-fields [:municipality :address :verdicts :pate-verdict :_applicantIndex
                      :bulletinOpDescription :bulletinState :applicant :verdictData]
       search-updates (get-search-fields search-fields app-snapshot)]
   (snapshot-updates app-snapshot search-updates created)))


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
    (get-bulletin bulletinId bulletin-fields))
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
  "Verify that bulletin visibility date is valid at the given (or current) point of time"
  ([bulletin-version]
   (bulletin-version-date-valid? (now) bulletin-version))
  ([now {state :bulletinState :as bulletin-version}]
   (case (keyword state)
     :proclaimed   (and (< (:proclamationStartsAt bulletin-version) now)
                        (> (:proclamationEndsAt bulletin-version) now))
     :verdictGiven (and (< (:appealPeriodStartsAt bulletin-version) now)
                        (> (:appealPeriodEndsAt bulletin-version) now))
     :final        (and (< (:officialAt bulletin-version) now)
                        (> (tc/to-long (t/plus (tc/from-long now) (t/days 14))))))))

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

(defn process-delete-verdict [applicationId verdictId]
  (infof "Bulletins for removed verdict %s need to be cleaned up" verdictId)
  (mongo/remove :application-bulletins (str applicationId "_" verdictId)))

(defn process-check-for-verdicts-result
  [{{old-verdicts :verdicts applicationId :id :keys [organization permitType municipality] :as application} :application
    created :created :as command} new-verdicts]
  "For (non-YMP) organizations with bulletins enabled, update bulletins to match with verdicts retrieved from backing system"
  (when (and (not (permit/ymp-permit-type? (:permitType application)))
             (org/bulletins-enabled? (org/get-organization organization) permitType municipality))
    (let [old-verdict-ids          (map :id (remove :draft old-verdicts))
          new-verdict-ids          (map :id (remove :draft new-verdicts))
          removed-verdict-ids      (clojure.set/difference (set old-verdict-ids) (set new-verdict-ids))]
      (doseq [vid removed-verdict-ids] ; Delete bulletins related to removed verdicts
        (process-delete-verdict applicationId vid))
      (doseq [{vid :id :as verdict} new-verdicts]
        (infof "Upserting the bulletin for verdict %s" vid)
        (upsert-bulletin-by-id (str applicationId "_" vid)
          (create-bulletin (assoc application :state :verdictGiven
                                            :verdicts [verdict])
                         created
                         {:verdictGivenAt (-> verdict :paatokset first :paivamaarat :anto)
                          :appealPeriodStartsAt (or (-> verdict :paatokset first :paivamaarat :julkipano) (now))
                          :appealPeriodEndsAt   (or (-> verdict :paatokset first :paivamaarat :viimeinenValitus) (now))}))))))
