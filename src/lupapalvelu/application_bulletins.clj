(ns lupapalvelu.application-bulletins
  (:require [clojure.set :as set]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-bulletin-utils :refer :all]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.metadata :as metadata]
            [lupapalvelu.data-skeleton :as ds]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :refer [infof]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.date :as pate-date]
            [lupapalvelu.pate.metadata :as pate-metadata]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [schema.core :as sc]
            [swiss.arrows :refer :all]))

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
    states/pre-verdict-states :proclaimed
    (set/difference states/post-verdict-states
                    states/terminal-states) :verdictGiven
    ;; Ready was earlier non-terminal state.
    #{:ready} :verdictGiven
    #{:final} :final))

(defn check-bulletins-enabled [{:keys                                     [created organization]
                                {:keys [permitType municipality] :as app} :application}]
  (when (and organization
             (or (not (org/bulletins-enabled? @organization permitType municipality created))
                 (not (app/bulletin-enabled-for-application-operation? app))))
    (fail :error.bulletins-not-enabled-for-scope)))

;; Query/Projection fields

(defn bulletin-id->application-id
  "Bulletin id is either the LP id or it is the LP id plus a unique id as a suffix"
  [bulletin-id]
  (ss/replace bulletin-id #"_[a-z0-9]+" ""))

(defn default-official-at-lower-limit
  "Default may be a misnomer since this is de facto the only lower limit used"
  [now-ts]
  (date/timestamp (date/minus now-ts :weeks 2)))

(defn versions-elemMatch
  "Proclamation and appeal periods are counted in days.
  I.e. while the timestamps have a specific time associated with them (12:00)
  the proclamation/appeal is allowed for the duration of the whole day.
  See e.g. `lupapalvelu.application-bulletins-util/bulletin-version-date-valid?`

  Note also that R bulletins only have a single :verdictGiven version.
  YMP bulletins have both :verdictGiven and :proclaimed versions"
  ([now-ts]
   (versions-elemMatch now-ts (default-official-at-lower-limit now-ts) now-ts))
  ([now-ts officialAt-lowerLimit]
   (versions-elemMatch now-ts officialAt-lowerLimit now-ts))
  ([start-ts officialAt-lowerLimit end-ts]
   (let [[date-start-ts date-end-ts] (day-range-ts start-ts end-ts)]
     {:versions {$elemMatch
                 {$or [{:bulletinState        :proclaimed
                        :proclamationStartsAt {$lt date-end-ts} :proclamationEndsAt {$gte date-start-ts}}
                       {:bulletinState        :verdictGiven
                        :appealPeriodStartsAt {$lt date-end-ts} :appealPeriodEndsAt {$gte date-start-ts}}
                       {:bulletinState :final
                        :officialAt    {$lt date-end-ts
                                        $gt officialAt-lowerLimit}}]}}})))

(defn version-elemMatch
  "Only to be used in aggregation queries after $unwinding the versions.
  Otherwise this could cause a false positive since it does not check whether the starting and ending
  timestamps are from the same version"
  ([now-ts]
   (version-elemMatch now-ts (default-official-at-lower-limit now-ts) now-ts))
  ([now-ts officialAt-lowerLimit]
   (version-elemMatch now-ts officialAt-lowerLimit now-ts))
  ([start-ts officialAt-lowerLimit end-ts]
   (let [[date-start-ts date-end-ts] (day-range-ts start-ts end-ts)]
     {$or [{:versions.bulletinState        :proclaimed
            :versions.proclamationStartsAt {$lt date-end-ts} :versions.proclamationEndsAt {$gte date-start-ts}}
           {:versions.bulletinState        :verdictGiven
            :versions.appealPeriodStartsAt {$lt date-end-ts} :versions.appealPeriodEndsAt {$gte date-start-ts}}
           {:versions.bulletinState :final
            :versions.officialAt    {$lt date-end-ts
                                     $gt officialAt-lowerLimit}}]})))

(def bulletins-fields
  {:versions.bulletinState        1 :versions.state                  1
   :versions.municipality         1 :versions.address                1
   :versions.location             1 :versions.primaryOperation       1
   :versions.propertyId           1 :versions.applicant              1
   :versions.modified             1 :versions.proclamationEndsAt     1
   :versions.proclamationStartsAt 1 :versions.proclamationText       1
   :versions.verdictGivenAt       1 :versions.appealPeriodStartsAt   1
   :versions.appealPeriodEndsAt   1 :versions.verdictGivenText       1
   :versions.officialAt           1 :versions.category               1
   :versions.verdictData          1 :versions.application-id         1
   :modified                      1 :versions.bulletinOpDescription  1
   :versions.markup?              1 :versions.bulletinVersionCreated 1
   :versions.verdictDate          1 :versions.bulletinProcess        1})

(def bulletin-fields
  (merge bulletins-fields
         {:versions._applicantIndex 1
          :versions.documents       1
          :versions.id              1
          :versions.attachments     1
          :versions.attachment-id   1
          :versions.verdicts        1
          :versions.tasks           1
          :versions.permitType      1
          :bulletinState            1
          :versions.organization    1}))

;; Snapshot

(def app-snapshot-fields
  [:_applicantIndex :address :applicant :created :documents :location
   :modified :municipality :organization :permitType :bulletinOpDescription
   :primaryOperation :propertyId :state :verdicts :pate-verdicts :tasks
   :submitted])

(def attachment-snapshot-fields
  [:id :type :latestVersion :auth :metadata :contents :target :source])

(def remove-party-docs-fn
  (partial remove (fn-> :schema-info :type keyword (= :party))))

(defn- bulletin-verdict-giver
  "Verdict giver code if `bulletin-code-giver` for verdict template is true,
  `vc/verdict-giver-name` otherwise"
  [{:keys [template] :as verdict}]
  (if (:bulletin-code-giver template)
    (vc/verdict-giver verdict)
    (vc/verdict-giver-name verdict)))

(defn verdict-data-for-bulletin-snapshot [verdict]
  (let [v      (pate-metadata/unwrap-all verdict)
        code   (vc/verdict-code v)
        status (util/->int code nil)]
    (util/strip-nils
      {:section (vc/verdict-section v)
       :status  status
       :code    (when-not status code)
       :contact (bulletin-verdict-giver v)
       :text    (vc/verdict-text v)})))

(defn building-int-value
  "Returns accessor function which fetches building data numeric string values and
   convert those to integers, (key like :autopaikat-vahintaan)."
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
    ;; The verdict might have default required foremen roles, but we should
    ;; ignore them if the template says foremen roles should not be included
    (if (false? (get-in verdict [:data :foremen-included]))
      ""
      (->> (get-in verdict [:data :foremen])
           (map #(i18n/localize (or (get-in verdict [:data :language]) "fi") (str "pate-r.foremen." %)))
           (ss/join ", ")))))

(defn conditions
  "Accessor function to fetch conditions, returns conditions in map with key :sisalto."
  [verdict]
  (let [condition-key (if (vc/legacy? verdict) :name :condition)]
    (some->> (get-in verdict [:data :conditions])
             vals
             (map condition-key)
             (remove ss/blank?)
             (mapv #(assoc {} :sisalto %)))))

(defn reviews
  "Accessor function to fetch review names, returns names in map with key :tarkastuksenTaiKatselmuksenNimi."
  [verdict]
  (if (vc/legacy? verdict)
    (->> (get-in verdict [:data :reviews])
         vals
         (map :name)
         (mapv #(assoc {} :tarkastuksenTaiKatselmuksenNimi %)))
    ;; The verdict might have default required reviews, but we should
    ;; ignore them if the template says reviews should not be included
    (if (false? (get-in verdict [:data :reviews-included]))
      []
      (->> (get-in verdict [:data :reviews])
           (map (fn [id] (:fi (util/find-by-id id (get-in verdict [:references :reviews])))))
           (mapv #(assoc {} :tarkastuksenTaiKatselmuksenNimi %))))))

(def backing-system-verdict-skeleton
  {:id              (ds/access :id)
   :kuntalupatunnus (ds/access :kuntalupatunnus)
   :draft           (ds/access :draft)
   :timestamp       (ds/access :timestamp)
   :sopimus         (ds/access :sopimus)
   :paatokset       [{:id                         (ds/access :id)
                      :paivamaarat                {:anto             (ds/access :anto)
                                                   :lainvoimainen    (ds/access :lainvoimainen)
                                                   :julkipano        (ds/access :julkipano)
                                                   :viimeinenValitus (ds/access :muutoksenhaku)
                                                   :voimassaHetki    (ds/access :voimassa)}
                      :poytakirjat                [{:paatoksentekija (ds/access :paatoksentekija)
                                                    :urlHash         nil
                                                    :status          (ds/access :status)
                                                    :paatos          (ds/access :paatos)
                                                    :paatospvm       (ds/access :paatospvm)
                                                    :pykala          (ds/access :pykala)
                                                    :paatoskoodi     (ds/access :paatoskoodi)}]
                      :lupamaaraykset             {:autopaikkojaEnintaan        (ds/access :autopaikkojaEnintaan)
                                                   :autopaikkojaRakennettu      (ds/access :autopaikkojaRakennettu)
                                                   :autopaikkojaKiinteistolla   (ds/access :autopaikkojaKiinteistolla)
                                                   :vaaditutTyonjohtajat        (ds/access :vaaditutTyonjohtajat)
                                                   :vaaditutErityissuunnitelmat (ds/access :vaaditutErityissuunnitelmat)
                                                   :maaraykset                  (ds/access :maaraykset)
                                                   :vaaditutKatselmukset        (ds/access :vaaditutKatselmukset)}
                      :aloittamisoikeusVakuudella (ds/access :aloittamisoikeusVakuudella)}]})

(defn- backing-system-status [verdict]
  (when-not (vc/verdict-code-is-free-text? verdict)
    (if (vc/legacy? verdict)
      (util/->int (vc/verdict-code verdict)))))

(defn- backing-system-paatoskoodi [verdict]
  (if (vc/verdict-code-is-free-text? verdict)
    (vc/verdict-code verdict)
    (if (vc/legacy? verdict)
      (ss/lower-case (i18n/localize "fi" (str "verdict.status." (vc/verdict-code verdict))))
      (i18n/localize "fi" (str "pate.verdict-code." (vc/verdict-code verdict))))))

(def backing-system-verdict-accessors
  "Accessors for turning a Lupapiste verdict into a backing system verdict, NOT accessing a backing system verdict"
  {:id                          (ds/from-context [:id])
   :kuntalupatunnus             (ds/from-context [:data :kuntalupatunnus])
   :draft                       (complement (ds/from-context [:published :published]))
   :timestamp                   vc/verdict-modified
   :sopimus                     vc/contract?
   :anto                        (ds/from-context [:data :anto])
   :lainvoimainen               (ds/from-context [:data (some-fn :lainvoimainen :k-lainvoimainen)])
   :julkipano                   (ds/from-context [:data (some-fn :julkipano :k-kuulutus)])
   :muutoksenhaku               (ds/from-context [:data (some-fn :muutoksenhaku :k-muutoksenhaku)])
   :voimassa                    (ds/from-context [:data (some-fn :voimassa :k-voimassa)])
   :paatoksentekija             bulletin-verdict-giver
   :urlHash                     nil
   :status                      backing-system-status
   :paatos                      vc/verdict-text
   :paatospvm                   vc/verdict-date
   :pykala                      vc/verdict-section
   :paatoskoodi                 backing-system-paatoskoodi
   :autopaikkojaEnintaan        (building-int-value :autopaikat-enintaan)
   :autopaikkojaRakennettu      (building-int-value :rakennetut-autopaikat)
   :autopaikkojaKiinteistolla   (building-int-value :kiinteiston-autopaikat)
   :vaaditutTyonjohtajat        foremen
   :vaaditutErityissuunnitelmat #(mapv :fi (vc/verdict-required-plans %))
   :maaraykset                  conditions
   :vaaditutKatselmukset        reviews
   :aloittamisoikeusVakuudella  (ds/from-context [:data :collateral-flag])})

(defn ->backing-system-verdict
  "Normalize Pate verdicts into backing system verdicts.
  Makes it easier to handle the bulletin verdicts later, if all the verdicts
  look like backing system verdicts."
  [verdict]
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
                                              (:verdicts application))
                            :bulletinProcess (app/bulletin-process application))
        app-snapshot (dissoc app-snapshot :pate-verdicts)]
    app-snapshot))

(defn snapshot-updates [snapshot search-fields ts]
  {$push {:versions snapshot}
   $set  (merge {:modified ts} search-fields)})

(defn get-search-fields [fields app]
  (into {} (map #(hash-map % (% app)) fields)))

;; FIXME: the `:- ApplicationBulletin` is syntactically in wrong place here, and does not actually validate anything
;; it seems hell lot of tests break if we turn the validation on: not feasible to fix atm.
#_{:clj-kondo/ignore [:schema-misplaced-return]}
(sc/defn ^:always-validate create-bulletin [application created & [updates]] :- ApplicationBulletin
  (let [app-snapshot   (merge (create-bulletin-snapshot application)
                              {:bulletinVersionCreated created})
        app-snapshot   (if updates
                         (merge app-snapshot (util/strip-nils updates))
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
  "Returns the bulletin from the database; note that by default filters the fields to only include those
  allowed to be seen by the public"
  ([bulletinId]
   (get-bulletin bulletinId bulletin-fields))
  ([bulletinId projection]
   (mongo/with-id (mongo/by-id :application-bulletins bulletinId projection))))

(defn get-bulletin-by-query
  ([query]
   (get-bulletin-by-query query {}))
  ([query projection]
   (mongo/select-one :application-bulletins query projection)))

(defn download-bulletin-attachment
  "Warning: Already assumes that access has been checked"
  [bulletin-id attachment]
  (storage/download-from-system
    (bulletin-id->application-id bulletin-id)
    (get-in attachment [:latestVersion :fileId])
    (get-in attachment [:latestVersion :storageSystem])))

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
        authority? (assoc (first versions) :note :bulletin.not-active)))))

(defn download-bulletin-doc
  "Downloads the bulletin doc if a bulletin version can be found for the user in command;
  this means either the currently public version or the latest version if the command user is an appropriate authority"
  [{:keys [system] :as command} bulletin-id]
  {:pre [(map? system)]}
  (let [{:keys [attachment-id application-id]} (some->> (get-bulletin bulletin-id [:versions])
                                                        (bulletin-version-for-user command))]
    (some-<>> (when attachment-id application-id)
              (mongo/by-id :applications <> [:attachments])
              (att/get-attachment-latest-version-file system nil attachment-id false) ; user is nil => no access check
              (att/output-attachment <> true))))

(defn bulletin-is-accessible
  "Pre-checker that fails if the user cannot access the bulletin."
  [{:keys [data] :as command}]
  (when-let [bulletin-id (or (:bulletin-id data)
                             (:bulletinId data))]
    (when-not (bulletin-version-for-user command
                                         (get-bulletin bulletin-id [:versions]))
      (fail :error.bulletin.not-found))))

(defn bulletin-matches-application
  "Pre-checker that fails if the bulletin is not for the given application"
  [{{:keys [bulletinId id]} :data}]
  (when (and (ss/not-blank? bulletinId)
             (not (mongo/any? :application-bulletins
                              {:_id                     bulletinId
                               :versions.application-id id})))
    (fail :error.bulletin.id)))

(declare bulletin-date-in-period?)

(defn bulletin-appeal-enabled?
  "True if bulletin appeal mechanism is not disabled for `bulletin-version` and we are
  within the appeal period. Note that this only works for R and P permit types."
  [{:keys [municipality organization permitType] :as bulletin-version}]
  (boolean
    (when (bulletin-date-in-period? :appealPeriodStartsAt
                                    :appealPeriodEndsAt
                                    bulletin-version)
      (when-let [settings (when (and municipality organization
                                     (util/includes-as-kw? #{:R :P} permitType))
                            (some->> (org/get-organization organization [:scope])
                                     :scope
                                     (util/find-by-keys {:municipality municipality
                                                         :permitType   permitType})
                                     :bulletins))]
        ;; The default (nil) is interpreted as true
        (not (false? (:appeal settings)))))))

(defn bulletin-comment-deadline
  "Returns commenting deadline timestamp or nil, if commenting not allowed. Commenting in
  `verdictGiven` state is allowed only with featurflag `:bulletin-comment-verdict-given.`"
  [{state :bulletinState :as bulletin-version}]
  (letfn [(check [start-kw end-kw]
            (when (bulletin-date-in-period? start-kw end-kw bulletin-version)
              (-> bulletin-version end-kw date/end-of-day date/timestamp)))]
    (some->> (case (keyword state)
               :proclaimed
               [:proclamationStartsAt :proclamationEndsAt]

               :verdictGiven
               (when (env/feature? :bulletin-comment-verdict-given)
                 [:appealPeriodStartsAt :appealPeriodEndsAt])
               nil)
             (apply check))))

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
  (let [start (date/start-of-day (get bulletin-version startdate-kw))
        end   (date/end-of-day (get bulletin-version enddate-kw))]
    (boolean (and start end (date/before? start (date/now) end)))))

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

(defn process-delete-verdict [{applicationId :id :as application} verdictId]
  (infof "Bulletins for removed verdict %s need to be cleaned up" verdictId)
  (if (permit/ymp-permit-type? (:permitType application))
    (let [bulletin                     (mongo/by-id :application-bulletins applicationId [:versions])
          versions-in-proclaimed-state (some->> bulletin
                                                :versions
                                                (util/find-by-key :bulletinState "proclaimed"))]
      (cond
        versions-in-proclaimed-state
        (mongo/update-by-id :application-bulletins applicationId
          {$set   {:bulletinState "proclaimed"}
           $unset {:verdicts    1
                   :verdictData 1}
           $pull  {:versions {:bulletinState {$ne "proclaimed"}}}})

        bulletin
        (mongo/remove-by-id :application-bulletins applicationId)))
    (mongo/remove-by-id :application-bulletins (str applicationId "_" verdictId))))

(def ^:private date-on-work-days?
  "True for dates that should be pushed to the next work day. Uses the Pate naming style for the dates."
  {:julkipano       true
   :k-kuulutus      true
   :muutoksenhaku   true
   :k-muutoksenhaku true
   :lainvoimainen   false
   :k-lainvoimainen false
   :aloitettava     false
   :k-aloitettava   false
   :voimassa        false
   :k-voimassa      false})

(defn is-date-forwarded-to-work-day?
  "Returns true if the given verdict-date type should not be placed on a holiday or weekend.
   Defaults to true if not specified."
  [date-kw]
  (get date-on-work-days? date-kw true))

(defn modify-date
  [date-kw timestamp amount]
  (let [forward? (is-date-forwarded-to-work-day? date-kw)]
    (cond->   timestamp
      true     (date/plus :days amount)
      forward? pate-date/forward-to-work-day
      true     date/timestamp)))

(defn calculate-appeal-end
  "Use the given appeal end date or deduce it ourselves.

  Appeal period ends by default at the end of the day that is 14 days from the verdict given date (anto),
  which should be 1 day after the appeal period start date (julkipano).

  Note that we no longer consider the given date (anto). With kuulutus-bulletins, the given date refers
  to the \"tiedoksiantopäivä\", which has different semantics as the old given date."
  [{:keys [muutoksenhaku julkipano kuulutus]}]
  (cond
    muutoksenhaku muutoksenhaku
    julkipano     (modify-date :julkipano julkipano 15)
    ;; Tiedoksianto happens 7 days after kuulutus and
    ;; oikaisu must be requested within 30 days after tiedoksianto => 7 + 30 = 37
    kuulutus      (modify-date :k-kuulutus kuulutus 37)
    :else         (throw (IllegalArgumentException. "Couldn't determine fallback appeal end"))))

(defn- delete-old-verdict-bulletins!
  "Delete bulletins related to removed verdicts"
  [{{old-verdicts :verdicts :as application} :application}
   new-verdicts]
  (let [old-verdict-ids     (set (map :id (remove :draft old-verdicts)))
        new-verdict-ids     (set (map :id (remove :draft new-verdicts)))
        removed-verdict-ids (set/difference old-verdict-ids new-verdict-ids)]
    (run! (fn [vid] (process-delete-verdict application vid)) removed-verdict-ids)))

(defn- get-verdict-date
  [verdict kw]
  (-> verdict :paatokset first :paivamaarat kw))

(defn- paatospvm
  [verdict]
  (-> verdict :paatokset first :poytakirjat first :paatospvm))

(defn bulletin-op-description [{:keys [descriptions-from-backend-system]} kuntalupatunnus app-descriptions]
  (when descriptions-from-backend-system
    (:kuvaus (util/find-by-key :kuntalupatunnus kuntalupatunnus app-descriptions))))

(defn process-check-for-julkipano-verdicts-result
  "For (non-YMP) organizations with julkipano bulletins enabled, update bulletins to match with verdicts retrieved from backing system"
  [{{applicationId :id :keys [organization permitType municipality] :as application} :application created :created :as command}
   new-verdicts
   app-descriptions]
  (let [bulletin-settings (org/bulletin-settings-for-scope (org/get-organization organization) permitType municipality)
        op-description    #(bulletin-op-description bulletin-settings % app-descriptions)]
    (when (org/bulletins-enabled? bulletin-settings created)
      (delete-old-verdict-bulletins! command new-verdicts)
      (doseq [{vid :id kuntalupatunnus :kuntalupatunnus :as verdict} new-verdicts]
        (when-let [appeal-period-start (get-verdict-date verdict :julkipano)]
          (infof "Upserting the julkipano bulletin for verdict %s %s" vid kuntalupatunnus)
          (upsert-bulletin-by-id (str applicationId "_" vid)
                                 (create-bulletin (-> (dissoc application :pate-verdicts)
                                                      (util/assoc-when-pred util/not-empty-or-nil?
                                                                            :state :verdictGiven
                                                                            :bulletinOpDescription (op-description kuntalupatunnus)
                                                                            :verdicts [verdict]))
                                                  created
                                                  {:verdictDate          (paatospvm verdict)
                                                   :verdictGivenAt       (get-verdict-date verdict :anto)
                                                   :appealPeriodStartsAt appeal-period-start
                                                   :appealPeriodEndsAt   (calculate-appeal-end
                                                                           {:muutoksenhaku (get-verdict-date verdict :viimeinenValitus)
                                                                            :anto          (get-verdict-date verdict :anto)
                                                                            :julkipano     (get-verdict-date verdict :julkipano)})})))))))

(defn process-check-for-kuulutus-verdicts-result
  "For (non-YMP) organizations with kuulutus bulletins enabled, update bulletins to match with verdicts retrieved from backing system"
  [{{applicationId :id :keys [organization permitType municipality] :as application} :application created :created :as command}
   new-verdicts
   app-descriptions]
  (let [bulletin-settings (org/bulletin-settings-for-scope (org/get-organization organization) permitType municipality)
        op-description    #(bulletin-op-description bulletin-settings % app-descriptions)]
    (when (org/bulletins-enabled? bulletin-settings created)
      (delete-old-verdict-bulletins! command new-verdicts)
      (doseq [{vid :id kuntalupatunnus :kuntalupatunnus :as verdict} new-verdicts]
        ;; KuntaGML verdict has no specific field to indicate kuulutus date so let's assume `julkipano` is ok
        (when-let [appeal-period-start (get-verdict-date verdict :julkipano)]
          (infof "Upserting the kuulutus bulletin for verdict %s %s" vid kuntalupatunnus)
          (upsert-bulletin-by-id (str applicationId "_" vid)
                                 (create-bulletin (-> (dissoc application :pate-verdicts)
                                                      (util/assoc-when-pred util/not-empty-or-nil?
                                                                            :state :verdictGiven
                                                                            :bulletinOpDescription (op-description kuntalupatunnus)
                                                                            :verdicts [verdict]))
                                                  created
                                                  {:verdictDate          (paatospvm verdict)
                                                   :appealPeriodStartsAt appeal-period-start
                                                   :appealPeriodEndsAt   (calculate-appeal-end
                                                                           {:muutoksenhaku (get-verdict-date verdict :viimeinenValitus)
                                                                            :kuulutus      (get-verdict-date verdict :julkipano)})})))))))

(defn process-check-for-verdicts-result
  "For (non-YMP) organizations with bulletins enabled, update bulletins to match with verdicts retrieved from backing system"
  [{application :application :as command} new-verdicts app-descriptions]
  (when (and (not (permit/ymp-permit-type? (:permitType application)))
             (app/bulletin-enabled-for-application-operation? application))
    (case (app/bulletin-process application)
      :julkipano (process-check-for-julkipano-verdicts-result command new-verdicts app-descriptions)
      :kuulutus  (process-check-for-kuulutus-verdicts-result command new-verdicts app-descriptions))))

(defn- bulletin-rows
  "Row data for the organization bulletins excel report.
  Finds the bulletins first and then checks if the related application is in the organization
  but tries to filter out the bulletins by municipality first.

  NOTE: this means that bulletins created before organizations were combined can't all be found
  since their id reflects their organization at the time of publication.

  NOTE: the columns are the same as `lupapalvelu.ui.bulletins.local-bulletins/bulletins-table` in the frontend
  but those have projected fields from aggregation so this is slightly different"
  [loc org-id start-ts end-ts]
  (let [app-in-org? #(mongo/any? :applications {:_id % :organization org-id})
        id-pattern  (->> (ss/substring org-id 0 3) (format "LP-%s-") re-pattern)
        official-ts (default-official-at-lower-limit start-ts)
        query       (assoc (versions-elemMatch start-ts official-ts end-ts)
                           :_id id-pattern)
        bulletins   (some->> (mongo/select :application-bulletins query)
                             (filter #(-> % :id bulletin-id->application-id app-in-org?))
                             not-empty)
        get-date    (fn [bulletin ts-attr]
                      (some->> (:versions bulletin)
                               (keep ts-attr)
                               not-empty
                               (apply max) ; get the latest date
                               date/finnish-date))]
    (for [{:keys                                 [id address bulletinOpDescription category]
           {:keys [code status section contact]} :verdictData
           :as                                   bulletin} bulletins]
      {:section          (str section " "
                              (cond
                                (and category code) (loc (str "pate.verdict-code." code))
                                status (loc (str "verdict.status." status))))
       :app-id           (bulletin-id->application-id id)
       :building-site    address
       :type             bulletinOpDescription
       :verdict-giver    contact
       :verdict-given-at (get-date bulletin :verdictGivenAt)
       :rectif-end       (get-date bulletin :appealPeriodEndsAt)})))

(defn org-report
  "Collates an excel report on the organizations bulletins"
  [org-id lang start-ts end-ts]
  (let [loc (partial i18n/localize lang)
        wb  (excel/create-workbook
              [{:sheet-name (loc :bulletin-report.bulletins)
                :header     [(loc :bulletin.verdict-section)
                             (loc :bulletin.lupatunnus)
                             (loc :bulletin.building-site)
                             (loc :bulletin.type)
                             (loc :bulletin.verdict-giver)
                             (loc :bulletin.verdict.verdictGivenAt)
                             (loc :bulletin.rectification-period-ends)]
                :row-fn     (juxt :section :app-id :building-site :type :verdict-giver :verdict-given-at :rectif-end)
                :data       (bulletin-rows loc org-id start-ts end-ts)}])]
    (excel/xlsx-stream wb)))
