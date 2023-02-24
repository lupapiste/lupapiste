(ns lupapalvelu.application-bulletins
  (:require [clojure.set :as set]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-bulletin-utils :refer :all]
            [lupapalvelu.attachment.metadata :as metadata]
            [lupapalvelu.data-skeleton :as ds]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.metadata :as pate-metadata]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [infof]]))

(sc/defschema ApplicationBulletin
  {:id                              sc/Str
   :versions                        [sc/Any]
   :application-id                  ssc/ApplicationId
   :address                         sc/Str
   :bulletinOpDescription           sc/Str
   ;; Whether the the description is in markup format
   (sc/optional-key :markup?)       sc/Bool
   :bulletinState                   sc/Any
   :applicant                       sc/Str
   :_applicantIndex                 sc/Str
   :municipality                    sc/Str
   :organization                    sc/Str
   :modified                        ssc/Timestamp
   :verdictCategory                 sc/Str
   (sc/optional-key :verdicts)      [sc/Any]
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
    states/pre-verdict-states               :proclaimed
    (set/difference states/post-verdict-states
                    states/terminal-states) :verdictGiven
    ;; Ready was earlier non-terminal state.
    #{:ready}                               :verdictGiven
    #{:final}                               :final))

(defn bulletin-enabled-for-application-operation?
  [application]
  (and (not (foreman/foreman-app? application))
       (not (app/designer-app? application))))

;; Query/Projection fields


(defn versions-elemMatch
  "Proclamation and appeal periods are counted in days.
  I.e. while the timestamps have a specific time associated with them (12:00)
  the proclamation/appeal is allowed for the duration of the whole day.
  See e.g. `lupapalvelu.application-bulletins-util/bulletin-version-date-valid?`

  Note also that R bulletins only have a single :verdictGiven version.
  YMP bulletins have both :verdictGiven and :proclaimed versions"
  ([now-ts]
   (versions-elemMatch now-ts  (date/timestamp (date/minus now-ts :weeks 2))))
  ([now-ts officialAt-lowerLimit]
   (let [[date-start-ts date-end-ts] (day-range-ts now-ts)]
     {:versions {$elemMatch
                 {$or [{:bulletinState        :proclaimed
                        :proclamationStartsAt {$lt date-end-ts} :proclamationEndsAt {$gte date-start-ts}}
                       {:bulletinState        :verdictGiven
                        :appealPeriodStartsAt {$lt date-end-ts} :appealPeriodEndsAt {$gte date-start-ts}}
                       {:bulletinState :final
                        :officialAt    {$lt date-end-ts
                                        $gt officialAt-lowerLimit}}]}}})))

(defn version-elemMatch
  [now-ts officialAt-lowerLimit]
  (let [[date-start-ts date-end-ts] (day-range-ts now-ts)]
    {$or [{:versions.bulletinState :proclaimed
           :versions.proclamationStartsAt {$lt date-end-ts} :versions.proclamationEndsAt {$gte date-start-ts}}
          {:versions.bulletinState :verdictGiven
           :versions.appealPeriodStartsAt {$lt date-end-ts} :versions.appealPeriodEndsAt {$gte date-start-ts}}
          {:versions.bulletinState :final
           :versions.officialAt {$lt date-end-ts
                                 $gt officialAt-lowerLimit}}]}))

(def bulletins-fields
  {:versions.bulletinState        1 :versions.state                 1
   :versions.municipality         1 :versions.address               1
   :versions.location             1 :versions.primaryOperation      1
   :versions.propertyId           1 :versions.applicant             1
   :versions.modified             1 :versions.proclamationEndsAt    1
   :versions.proclamationStartsAt 1 :versions.proclamationText      1
   :versions.verdictGivenAt       1 :versions.appealPeriodStartsAt  1
   :versions.appealPeriodEndsAt   1 :versions.verdictGivenText      1
   :versions.officialAt           1 :versions.category              1
   :versions.verdictData          1 :versions.application-id        1
   :modified                      1 :versions.bulletinOpDescription 1
   :versions.markup?              1})

(def bulletin-fields
  (merge bulletins-fields
         {:versions._applicantIndex 1
          :versions.documents       1
          :versions.id              1
          :versions.attachments     1
          :versions.verdicts        1
          :versions.tasks           1
          :bulletinState            1
          :versions.organization    1}))

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
  (let [v      (pate-metadata/unwrap-all verdict)
        code   (vc/verdict-code v)
        status (util/->int code nil)]
    (util/strip-nils
      {:section (vc/verdict-section v)
       :status  status
       :code    (when-not status code)
       :contact (vc/verdict-giver v)
       :text    (vc/verdict-text v)})))

(defn building-int-value
  "Returns accessor function which fetches building data numeric string values and
   convert those to integers, (key like :autopaikat-yhteensa)."
  [key]
  (fn [verdict]
    (->> (map key (vals (get-in verdict [:data :buildings])))
         (map util/->int)
         (apply +))))

(defn foremen
  "Accessor function to fetch localized foreman names, returns string."
  [verdict]
  (if (vc/legacy? verdict)
    (->> (get-in verdict [:data :foremen])
         vals
         (map :role)
         (ss/join ", "))
    (->> (get-in verdict [:data :foremen])
         (map #(i18n/localize (or (get-in verdict [:data :language]) "fi") (str "pate-r.foremen." %)))
         (ss/join ", "))))

(defn conditions
  "Accessor function to fetch conditions, returns conditions in map with key :sisalto."
  [verdict]
  (let [condition-key (if (vc/legacy? verdict) :name :condition)]
    (some->> (get-in verdict [:data :conditions])
             vals
             (map condition-key)
             (remove ss/blank?)
             (map #(assoc {} :sisalto %)))))

(defn reference-value
  "Accessor function to fetch actual values from references based on keys in data."
  [key]
  (fn [verdict]
    (->> (get-in verdict [:data (keyword key)])
         (map (fn [id] (:fi (util/find-by-id id (get-in verdict [:references (keyword key)]))))))))

(defn reviews
  "Accessor function to fetch review names, returns names in map with key :tarkastuksenTaiKatselmuksenNimi."
  [verdict]
  (if (vc/legacy? verdict)
    (->> (get-in verdict [:data :reviews])
         vals
         (map :name)
         (map #(assoc {} :tarkastuksenTaiKatselmuksenNimi %)))
    (->> (get-in verdict [:data :reviews])
         (map (fn [id] (:fi (util/find-by-id id (get-in verdict [:references :reviews])))))
         (map #(assoc {} :tarkastuksenTaiKatselmuksenNimi %)))))

(def backing-system-verdict-skeleton
  {:id              (ds/access :id)
   :kuntalupatunnus (ds/access :kuntalupatunnus)
   :draft           (ds/access :draft)
   :timestamp       (ds/access :timestamp)
   :sopimus         (ds/access :sopimus)
   :paatokset       [{:id             (ds/access :id)
                      :paivamaarat    {:anto             (ds/access :anto)
                                       :lainvoimainen    (ds/access :lainvoimainen)
                                       :julkipano        (ds/access :julkipano)
                                       :viimeinenValitus (ds/access :muutoksenhaku)
                                       :voimassaHetki    (ds/access :voimassa)}
                      :poytakirjat    [{:paatoksentekija (ds/access :paatoksentekija)
                                        :urlHash         nil
                                        :status          (ds/access :status)
                                        :paatos          (ds/access :paatos)
                                        :paatospvm       (ds/access :paatospvm)
                                        :pykala          (ds/access :pykala)
                                        :paatoskoodi     (ds/access :paatoskoodi)}]
                      :lupamaaraykset {:autopaikkojaEnintaan        (ds/access :autopaikkojaEnintaan)
                                       :autopaikkojaRakennettu      (ds/access :autopaikkojaRakennettu)
                                       :autopaikkojaKiinteistolla   (ds/access :autopaikkojaKiinteistolla)
                                       :vaaditutTyonjohtajat        (ds/access :vaaditutTyonjohtajat)
                                       :vaaditutErityissuunnitelmat (ds/access :vaaditutErityissuunnitelmat)
                                       :maaraykset                  (ds/access :maaraykset)
                                       :vaaditutKatselmukset        (ds/access :vaaditutKatselmukset)}}]})

(defn- backing-system-status [verdict]
  (when-not (vc/verdict-code-is-free-text? verdict)
    (if (vc/legacy? verdict)
      (util/->int (vc/verdict-code verdict)))))

(defn- backing-system-paatoskoodi [verdict]
  (if (vc/verdict-code-is-free-text? verdict)
    (vc/verdict-code verdict)
    (if (vc/legacy? verdict)
      (ss/lower-case (i18n/localize "fi" (str "verdict.status." (vc/verdict-code verdict))))
      (i18n/localize "fi" (str "pate-r.verdict-code." (vc/verdict-code verdict))))))

(def backing-system-verdict-accessors
  "Accessors for turning a Lupapiste verdict into a backing system verdict, NOT accessing a backing system verdict"
  {:id (ds/from-context [:id])
   :kuntalupatunnus (ds/from-context [:data :kuntalupatunnus])
   :draft (complement (ds/from-context [:published :published]))
   :timestamp vc/verdict-modified
   :sopimus vc/contract?
   :anto (ds/from-context [:data :anto])
   :lainvoimainen (ds/from-context [:data :lainvoimainen])
   :julkipano (ds/from-context [:data :julkipano])
   :muutoksenhaku (ds/from-context [:data :muutoksenhaku])
   :voimassa (ds/from-context [:data :voimassa])
   :paatoksentekija (ds/from-context [:data :handler])
   :urlHash nil
   :status backing-system-status
   :paatos vc/verdict-text
   :paatospvm vc/verdict-date
   :pykala vc/verdict-section
   :paatoskoodi backing-system-paatoskoodi
   :autopaikkojaEnintaan (building-int-value :autopaikat-yhteensa)
   :autopaikkojaRakennettu (building-int-value :rakennetut-autopaikat)
   :autopaikkojaKiinteistolla (building-int-value :kiinteiston-autopaikat)
   :vaaditutTyonjohtajat foremen
   :vaaditutErityissuunnitelmat #(mapv :fi (vc/verdict-required-plans %))
   :maaraykset conditions
   :vaaditutKatselmukset reviews})

(defn ->backing-system-verdict [verdict]
  (if (vc/lupapiste-verdict? verdict)
    (-> backing-system-verdict-skeleton
        (ds/build-with-skeleton
          (pate-metadata/unwrap-all verdict)
          backing-system-verdict-accessors)
        (util/strip-nils))
    verdict))

(defn create-bulletin-snapshot [{[pate-verdict & _] :pate-verdicts
                                 [verdict & _]      :verdicts
                                 permitType         :permitType
                                 applicationId      :id :as application}]
  (let [verdict      (or pate-verdict verdict)
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
        attachments  (->> (:attachments application)
                          (filter #(and (:latestVersion %) (metadata/public-attachment? %)))
                          (map #(select-keys % attachment-snapshot-fields))
                          (map #(update % :latestVersion (fn [v]
                                                           (select-keys v [:filename :contentType
                                                                           :fileId :size :storageSystem])))))
        app-snapshot (assoc app-snapshot
                            :id (mongo/create-id)
                            :application-id applicationId
                            :attachments attachments
                            :verdictData verdict-data
                            :category (cond
                                        (permit/ymp-permit-type? permitType)
                                        "ymp"

                                        pate-verdict
                                        (:category pate-verdict)

                                        :else
                                        (ss/lower-case (name permitType)))
                            :bulletinState (bulletin-state (:state app-snapshot))
                            :verdicts (concat (mapv ->backing-system-verdict (:pate-verdicts application))
                                              (:verdicts application)))
        app-snapshot (dissoc app-snapshot :pate-verdicts)]
    app-snapshot))

(defn snapshot-updates [snapshot search-fields ts]
  {$push {:versions snapshot}
   $set  (merge {:modified ts} search-fields)})

(defn get-search-fields [fields app]
  (into {} (map #(hash-map % (% app)) fields)))

; FIXME: the `:- ApplicationBulletin` is syntactically in wrong place here, and does not actually validate anything
; it seems hell lot of tests break if we turn the validation on: not feasible to fix atm.
(sc/defn ^:always-validate create-bulletin [application created & [updates]] :- ApplicationBulletin
  (let [app-snapshot   (create-bulletin-snapshot application)
        app-snapshot   (if updates
                         (merge app-snapshot updates)
                         app-snapshot)
        search-fields  [:municipality :address :verdicts :pate-verdicts :_applicantIndex
                        :bulletinOpDescription :bulletinState :applicant :verdictData]
        search-updates (get-search-fields search-fields app-snapshot)]
   (snapshot-updates app-snapshot search-updates created)))

(defn add-version-visit [bulletin-id version-id created]
  (mongo/update-by-query :application-bulletins
                         {:_id      bulletin-id
                          :versions {$elemMatch {:id version-id}}}
                         {$push {:versions.$.visits created}}))

(defn create-comment [bulletin-id version-id comment contact-info files created]
  (let [id          (mongo/create-id)
        new-comment {:id           id
                     :bulletinId   bulletin-id
                     :versionId    version-id
                     :comment      comment
                     :created      created
                     :contact-info contact-info
                     :attachments  (map #(assoc % :storageSystem (storage/default-storage-system-id))
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
    (when (seq (domain/get-application-as (:bulletinId comment) user :include-canceled-apps? true))
      (storage/download-bulletin-comment-file (:bulletinId comment) file-id (-> comment :attachments first :storageSystem)))))


(defn bulletin-version-for-user
  "The primary returned version is the latest active/visible version. If none exists and the
  user is an authority for the bulletin organization, the latest version is returned (with
  note)."
  [{:keys [user created]} bulletin]
  (when-let [versions (some-> (:versions bulletin)
                              reverse ;; The latest first
                              seq)]
    (let [authority?      (and (usr/authority? user)
                               (usr/user-has-role-in-organization? user
                                                                   (-> versions first :organization)
                                                                   roles/reader-org-authz-roles))
          current-version (util/find-first #(bulletin-version-date-valid? created %)
                                           versions)]
      (cond
        current-version current-version
        authority?      (assoc (first versions) :note :bulletin.not-active)))))

(defn bulletin-is-accessible
  "Pre-checker that fails if the user cannot access the bulletin."
  [{:keys [data] :as command}]
  (when-let [bulletin-id (:bulletinId data)]
    (when-not (bulletin-version-for-user command
                                         (get-bulletin bulletin-id [:versions]))
      (fail :error.bulletin.not-found))))

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
  "True if the current time is within `bulletin-version` period (start of the start date and
  end of the end date). `startdate-kw` and `enddate-kw` are keys for `bulletin-version`
  timestamps."
  [startdate-kw enddate-kw bulletin-version]
  {:pre [(contains? bulletin-version startdate-kw)
         (contains? bulletin-version enddate-kw)]}
  (date/before? (date/start-of-day (get bulletin-version startdate-kw))
                (date/now)
                (date/end-of-day (get bulletin-version enddate-kw))))

(defn validate-input-dates
  "Input validator that fails if the if the start of the start date is after the end of end
  date. Dates are given as `startdate-kw` and `enddate-kw` timestamps respectively."
  [startdate-kw enddate-kw command]
  (let [start (get-in command [:data startdate-kw])
        end   (get-in command [:data enddate-kw])]
    (when-not (date/before? (date/start-of-day start) (date/end-of-day end))
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

(defn calculate-appeal-end
  "Use the given appeal end date or deduce it ourselves.

  Appeal period ends by default at the end of the day that is 14 days from the verdict given date (anto),
  which should be 1 day after the appeal period start date (julkipano)."
  [muutoksenhaku anto julkipano]
  (let [modify (fn [timestamp amount]
                 (-> timestamp (date/plus :days amount) date/timestamp))]
    (cond
      muutoksenhaku muutoksenhaku
      anto          (modify anto 14)
      julkipano     (modify julkipano 15)
      :else         (throw (IllegalArgumentException. "Couldn't determine fallback appeal end")))))

(defn process-check-for-verdicts-result
  "For (non-YMP) organizations with bulletins enabled, update bulletins to match with verdicts retrieved from backing system"
  [{{old-verdicts :verdicts applicationId :id :keys [organization permitType municipality] :as application} :application
    created :created} new-verdicts app-descriptions]
  (let [{:keys [enabled descriptions-from-backend-system]} (org/bulletin-settings-for-scope (org/get-organization organization) permitType municipality)]
    (when (and enabled
               (not (permit/ymp-permit-type? (:permitType application)))
               (bulletin-enabled-for-application-operation? application))
      (let [old-verdict-ids     (map :id (remove :draft old-verdicts))
            new-verdict-ids     (map :id (remove :draft new-verdicts))
            removed-verdict-ids (set/difference (set old-verdict-ids) (set new-verdict-ids))
            get-date            #(-> %1 :paatokset first :paivamaarat %2)]
        (doseq [vid removed-verdict-ids] ; Delete bulletins related to removed verdicts
          (process-delete-verdict applicationId vid))
        (doseq [{vid :id kuntalupatunnus :kuntalupatunnus :as verdict} new-verdicts]
          (when-let [appeal-period-start (get-date verdict :julkipano)]
            (infof "Upserting the bulletin for verdict %s %s" vid kuntalupatunnus)
            (upsert-bulletin-by-id (str applicationId "_" vid)
                                   (create-bulletin (util/assoc-when-pred application util/not-empty-or-nil?
                                                     :state :verdictGiven
                                                     :bulletinOpDescription (when descriptions-from-backend-system
                                                                              (:kuvaus (util/find-by-key :kuntalupatunnus kuntalupatunnus app-descriptions)))
                                                     :verdicts [verdict])
                                                    created
                                                    {:verdictGivenAt       (get-date verdict :anto)
                                :appealPeriodStartsAt appeal-period-start
                                :appealPeriodEndsAt   (->> [:viimeinenValitus :anto :julkipano]
                                                           (map #(get-date verdict %))
                                                           (apply calculate-appeal-end))}))))))))
