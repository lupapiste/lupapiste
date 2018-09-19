(ns lupapalvelu.application-bulletins
  (:require [taoensso.timbre :refer [infof]]
            [monger.operators :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.set :as set]
            [sade.util :refer [fn->] :as util]
            [sade.core :refer :all]
            [lupapalvelu.attachment.metadata :as metadata]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.metadata :as pate-metadata]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.data-skeleton :as ds]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.application-bulletin-utils :refer :all]
            [schema.core :as sc]
            [sade.schemas :as ssc]
            [clj-time.coerce :as tc]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [sade.strings :as ss]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.application :as app]
            [lupapalvelu.storage.file-storage :as storage]
            [sade.shared-schemas :as sssc]
            [sade.env :as env]))

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
   (sc/optional-key :pate-verdicts) [sc/Any]})

(sc/defschema CommentFile
  {:fileId                          sssc/FileId
   :filename                        sc/Str
   :size                            sc/Int
   :contentType                     sc/Str
   (sc/optional-key :storageSystem) sssc/StorageSystem})

(def comment-file-checker (sc/checker CommentFile))

(def bulletin-state-seq (sm/state-seq states/bulletin-version-states))

(defn bulletin-state [app-state]
  (condp contains? (keyword app-state)
    states/pre-verdict-states                  :proclaimed
    (set/difference states/post-verdict-states
                    states/terminal-states)    :verdictGiven
    #{:final}                                  :final))

(defn bulletin-enabled-for-application-operation?
  [application]
  (and (not (foreman/foreman-app? application))
       (not (app/designer-app? application))))

;; Query/Projection fields
(defn- ts-as-date [ts]
  (-> ts tc/from-long t/with-time-at-start-of-day tc/to-long))

(defn versions-elemMatch
  ([now-ts]
   (versions-elemMatch now-ts (tc/to-long (t/minus (tc/from-long now-ts) (t/days 14)))))
  ([now-ts officialAt-lowerLimit]
   (let [date-ts (ts-as-date now-ts)]
    {:versions {$elemMatch
                {$or [{:bulletinState :proclaimed
                       :proclamationStartsAt {$lt now-ts} :proclamationEndsAt {$gte date-ts}}
                      {:bulletinState :verdictGiven
                       :appealPeriodStartsAt {$lt now-ts} :appealPeriodEndsAt {$gte date-ts}}
                      {:bulletinState :final
                       :officialAt {$lt now-ts
                                    $gt officialAt-lowerLimit}}]}}})))

(defn version-elemMatch
  [now-ts officialAt-lowerLimit]
  (let [date-ts (ts-as-date now-ts)]
    {$or [{:versions.bulletinState :proclaimed
           :versions.proclamationStartsAt {$lt now-ts} :versions.proclamationEndsAt {$gte date-ts}}
          {:versions.bulletinState :verdictGiven
           :versions.appealPeriodStartsAt {$lt now-ts} :versions.appealPeriodEndsAt {$gte date-ts}}
          {:versions.bulletinState :final
           :versions.officialAt {$lt now-ts
                                 $gt officialAt-lowerLimit}}]}))

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
   :primaryOperation :propertyId :state :verdicts :pate-verdicts :tasks])

(def attachment-snapshot-fields
  [:id :type :latestVersion :auth :metadata :contents :target])

(def remove-party-docs-fn
  (partial remove (fn-> :schema-info :type keyword (= :party))))

(defn verdict-data-for-bulletin-snapshot [verdict]
  (let [v (pate-metadata/unwrap-all verdict)]
    {:section (vc/verdict-section v)
     :status  (util/->int (vc/verdict-code v) nil)
     :contact (vc/verdict-giver v)
     :text    (vc/verdict-text v)}))

(defn with-path [path & [default]]
  (fn [verdict]
    (if-let [result (get-in verdict path)]
      result
      default)))

(def old-school-verdict-skeleton
  {:id (ds/access :id)
   :kuntalupatunnus (ds/access :kuntalupatunnus)
   :draft (ds/access :draft)
   :timestamp (ds/access :timestamp)
   :sopimus (ds/access :sopimus)
   :paatokset [{:id (ds/access :id)
                :paivamaarat {:anto (ds/access :anto)
                              :lainvoimainen (ds/access :lainvoimainen)}
                :poytakirjat [{:paatoksentekija (ds/access :paatoksentekija)
                               :urlHash nil
                               :status (ds/access :status)
                               :paatos (ds/access :paatos)
                               :paatospvm (ds/access :paatospvm)
                               :pykala (ds/access :pykala)
                               :paatoskoodi (ds/access :paatoskoodi)}]}]})

(def old-school-verdict-accessors
  {:id (with-path [:id])
   :kuntalupatunnus (with-path [:data :kuntalupatunnus])
   :draft (complement (with-path [:published :published]))
   :timestamp vc/verdict-modified
   :sopimus vc/contract?
   :anto (with-path [:data :anto])
   :lainvoimainen (with-path [:data :lainvoimainen])
   :paatoksentekija (with-path [:data :handler])
   :urlHash nil
   :status (comp util/->int vc/verdict-code)
   :paatos vc/verdict-text
   :paatospvm (with-path [:data :anto])
   :pykala vc/verdict-section
   :paatoskoodi (comp ss/lower-case
                      #(i18n/localize "fi" (str "verdict.status." %))
                      vc/verdict-code)})

(defn ->old-school-verdict [verdict]
  (if (vc/lupapiste-verdict? verdict)
    (ds/build-with-skeleton old-school-verdict-skeleton
                            (pate-metadata/unwrap-all verdict)
                            old-school-verdict-accessors)
    verdict))

(defn create-bulletin-snapshot [{[pate-verdict & _] :pate-verdicts [verdict & _] :verdicts permitType :permitType
                                 applicationId :id :as application}]
  (let [verdict (or pate-verdict verdict)
        app-snapshot (-> application
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
        verdict-data (verdict-data-for-bulletin-snapshot verdict)
        attachments (->> (:attachments application)
                         (filter #(and (:latestVersion %) (metadata/public-attachment? %)))
                         (map #(select-keys % attachment-snapshot-fields))
                         (map #(update % :latestVersion (fn [v] (select-keys v [:filename :contentType :fileId :size :storageSystem])))))
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

; FIXME: the `:- ApplicationBulletin` is syntactically in wrong place here, and does not actually validate anything
; it seems hell lot of tests break if we turn the validation on: not feasible to fix atm.
(sc/defn ^:always-validate create-bulletin [application created & [updates]] :- ApplicationBulletin
  (let [app-snapshot (create-bulletin-snapshot application)
       app-snapshot (if updates
                      (merge app-snapshot updates)
                      app-snapshot)
       search-fields [:municipality :address :verdicts :pate-verdicts :_applicantIndex
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
                     :attachments  (map #(assoc % :storageSystem (if (env/feature? :s3) :s3 :mongodb))
                                        files)}]
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

(defn get-bulletin-attachment [bulletin-id file-id]
  (when-let [bulletin (get-bulletin bulletin-id)]
    (when-let [attachment (->> (:versions bulletin)
                               last
                               :attachments
                               (filter #(= (get-in % [:latestVersion :fileId]) file-id))
                               first)]
      (storage/download-from-system bulletin-id file-id (get-in attachment [:latestVersion :storageSystem])))))

(defn get-bulletin-comment-attachment-file-as
  "Returns the bulletin attachment file if user has access to application, otherwise nil."
  [user file-id]
  (when-let [comment (mongo/select-one :application-bulletin-comments
                                       {:attachments.fileId file-id}
                                       [:attachments.$ :bulletinId])]
    (when (seq (lupapalvelu.domain/get-application-as (:bulletinId comment) user :include-canceled-apps? true))
      (storage/download-bulletin-comment-file (:bulletinId comment) file-id (-> comment :attachments first :storageSystem)))))

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

(defn- fallback-appeal-end-from-appeal-start
  "If appeal period end date is not available, use 14 days from appeal period start"
  [appeal-period-start]
  (-> appeal-period-start tc/from-long (t/plus (t/days 14)) tc/to-long))

(defn process-check-for-verdicts-result
  "For (non-YMP) organizations with bulletins enabled, update bulletins to match with verdicts retrieved from backing system"
  [{{old-verdicts :verdicts applicationId :id :keys [organization permitType municipality] :as application} :application
    created :created} new-verdicts app-descriptions]
  (let [{:keys [enabled descriptions-from-backend-system]} (org/bulletin-settings-for-scope (org/get-organization organization) permitType municipality)]
    (when (and enabled
               (not (permit/ymp-permit-type? (:permitType application)))
               (bulletin-enabled-for-application-operation? application))
      (let [old-verdict-ids          (map :id (remove :draft old-verdicts))
            new-verdict-ids          (map :id (remove :draft new-verdicts))
            removed-verdict-ids      (set/difference (set old-verdict-ids) (set new-verdict-ids))]
        (doseq [vid removed-verdict-ids] ; Delete bulletins related to removed verdicts
          (process-delete-verdict applicationId vid))
        (doseq [{vid :id kuntalupatunnus :kuntalupatunnus :as verdict} new-verdicts]
          (when-let [appeal-period-start (-> verdict :paatokset first :paivamaarat :julkipano)]
            (infof "Upserting the bulletin for verdict %s %s" vid kuntalupatunnus)
            (upsert-bulletin-by-id (str applicationId "_" vid)
              (create-bulletin (util/assoc-when-pred application util/not-empty-or-nil?
                                                     :state :verdictGiven
                                                     :bulletinOpDescription (when descriptions-from-backend-system
                                                                              (:kuvaus (util/find-by-key :kuntalupatunnus kuntalupatunnus app-descriptions)))
                                                     :verdicts [verdict])
                               created
                               {:verdictGivenAt (-> verdict :paatokset first :paivamaarat :anto)
                                :appealPeriodStartsAt appeal-period-start
                                :appealPeriodEndsAt   (or (-> verdict :paatokset first :paivamaarat :viimeinenValitus)
                                                          (fallback-appeal-end-from-appeal-start appeal-period-start))}))))))))
