(ns lupapalvelu.application
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [clojure.string :refer [blank? join trim split]]
            [clojure.walk :refer [keywordize-keys]]
            [swiss.arrows :refer [-<>>]]
            [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [clj-time.format :as tf]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.xml :as xml]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand update-application without-system-keys notify] :as action]
            [lupapalvelu.mongo :refer [$each] :as mongo]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.document.commands :as commands]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.user :refer [with-user-by-email] :as user]
            [lupapalvelu.user-api :as user-api]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rakennuslupa-mapping]
            [lupapalvelu.ktj :as ktj]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.application-meta-fields :as meta-fields]))

;; Validators

(defn- property-id? [^String s]
  (and s (re-matches #"^[0-9]{14}$" s)))

(defn property-id-parameters [params command]
  (when-let [invalid (seq (filter #(not (property-id? (get-in command [:data %]))) params))]
    (info "invalid property id parameters:" (join ", " invalid))
    (fail :error.invalid-property-id :parameters (vec invalid))))

(defn- validate-owner-or-writer
  "Validator: current user must be owner or writer.
   To be used in commands' :pre-checks vector."
  [command application]
  (when-not (domain/owner-or-writer? application (-> command :user :id))
    unauthorized))

(defn- validate-x [{{:keys [x]} :data}]
  (when (and x (not (< 10000 (util/->double x) 800000)))
    (fail :error.illegal-coordinates)))

(defn- validate-y [{{:keys [y]} :data}]
  (when (and y (not (<= 6610000 (util/->double y) 7779999)))
    (fail :error.illegal-coordinates)))

;; Helpers

(defn get-application-xml [{:keys [id permitType] :as application} & [raw?]]
  (if-let [{url :url} (organization/get-krysp-wfs application)]
    (if-let [fetch (permit/get-application-xml-getter permitType)]
      (fetch url id raw?)
      (do
        (error "No fetch function for" permitType (:organization application))
        (fail! :error.unknown)))
    (fail! :error.no-legacy-available)))

(defn- set-user-to-document [application document user-id path current-user timestamp]
  {:pre [document]}
  (when-not (ss/blank? user-id)
    (let [path-arr     (if-not (blank? path) (split path #"\.") [])
          schema       (schemas/get-schema (:schema-info document))
          subject      (user/get-user-by-id user-id)
          with-hetu    (and
                         (model/has-hetu? (:body schema) path-arr)
                         (user/same-user? current-user subject))
          person       (tools/unwrapped (model/->henkilo subject :with-hetu with-hetu :with-empty-defaults true))
          model        (if (seq path-arr)
                         (assoc-in {} (map keyword path-arr) person)
                         person)
          updates      (tools/path-vals model)
          ; Path should exist in schema!
          updates      (filter (fn [[update-path _]] (model/find-by-name (:body schema) update-path)) updates)]
      (when-not schema (fail! :error.schema-not-found))
      (when-not subject (fail! :error.user-not-found))
      (when-not (and (domain/has-auth? application user-id) (domain/no-pending-invites application user-id))
        (fail! :error.application-does-not-have-given-auth))
      (debugf "merging user %s with best effort into %s %s" model (get-in document [:schema-info :name]) (:id document))
      (commands/persist-model-updates application "documents" document updates timestamp)))) ; TODO support for collection parameter

(defn- insert-application [application]
  (mongo/insert :applications (merge application (meta-fields/applicant-index application))))

(def collections-to-be-seen #{"comments" "statements" "verdicts"})

(defn- mark-collection-seen-update [{id :id} timestamp collection]
  {:pre [(collections-to-be-seen collection) id timestamp]}
  {(str "_" collection "-seen-by." id) timestamp})

(defn- mark-indicators-seen-updates [application user timestamp]
  (merge
    (apply merge (map (partial mark-collection-seen-update user timestamp) collections-to-be-seen))
    (when (user/authority? user) (model/mark-approval-indicators-seen-update application timestamp))
    (when (user/authority? user) {:_attachment_indicator_reset timestamp})))

;;
;; Query application:
;;

(defn- post-process-app [app user]
  ((comp
     (fn [application] (update-in application [:documents] (fn [documents]
                                                             (map
                                                               (comp
                                                                 model/mask-person-ids
                                                                 (fn [doc] (assoc doc :validationErrors (model/validate application doc))))
                                                               documents))))
     without-system-keys
     (partial meta-fields/with-meta-fields user)
     meta-fields/enrich-with-link-permit-data)
    app))

(defn find-authorities-in-applications-organization [app]
  (mongo/select :users
    {:organizations (:organization app) :role "authority" :enabled true}
    {:firstName 1 :lastName 1}))

(defquery application
  {:roles            [:applicant :authority]
   :states           action/all-states
   :extra-auth-roles [:any]
   :parameters       [:id]}
  [{app :application user :user}]
  (if app
    (let [app (assoc app :allowedAttachmentTypes (attachment/get-attachment-types-for-application app))]
      (ok :application (post-process-app app user)
          :authorities (find-authorities-in-applications-organization app)
          :permitSubtypes (permit/permit-subtypes (:permitType app))))
    (fail :error.not-found)))

(defn filter-repeating-party-docs [schema-version schema-names]
  (let [schemas (schemas/get-schemas schema-version)]
    (filter
      (fn [schema-name]
        (let [schema-info (get-in schemas [schema-name :info])]
          (and (:repeating schema-info) (= (:type schema-info) :party))))
      schema-names)))

(def ktj-format (tf/formatter "yyyyMMdd"))
(def output-format (tf/formatter "dd.MM.yyyy"))

(defn- autofill-rakennuspaikka [application time]
  (when (and (not (= "Y" (:permitType application))) (not (:infoRequest application)))
    (when-let [rakennuspaikka (domain/get-document-by-type application :location)]
      (when-let [ktj-tiedot (ktj/rekisteritiedot-xml (:propertyId application))]
        (let [updates [[[:kiinteisto :tilanNimi]        (or (:nimi ktj-tiedot) "")]
                       [[:kiinteisto :maapintaala]      (or (:maapintaala ktj-tiedot) "")]
                       [[:kiinteisto :vesipintaala]     (or (:vesipintaala ktj-tiedot) "")]
                       [[:kiinteisto :rekisterointipvm] (or (try
                                                              (tf/unparse output-format (tf/parse ktj-format (:rekisterointipvm ktj-tiedot)))
                                                              (catch Exception e (:rekisterointipvm ktj-tiedot))) "")]]
              schema (schemas/get-schema (:schema-info rakennuspaikka))
              updates (filter (fn [[update-path _]] (model/find-by-name (:body schema) update-path)) updates)]
          (commands/persist-model-updates
            application
            "documents"
            rakennuspaikka
            updates
            time))))))

(defquery party-document-names
  {:parameters [:id]
   :roles      [:applicant :authority]
   :states     action/all-application-states}
  [{application :application}]
  (let [documents (:documents application)
        initialOp (:name (first (:operations application)))
        original-schema-names (:required ((keyword initialOp) operations/operations))
        original-party-documents (filter-repeating-party-docs (:schema-version application) original-schema-names)]
    (ok :partyDocumentNames (conj original-party-documents "hakija"))))

;;
;; Invites
;;

(defquery invites
  {:roles [:applicant :authority]}
  [{{:keys [id]} :user}]
  (let [common     {:auth {$elemMatch {:invite.user.id id}}}
        projection (assoc common :_id 0)
        filter     {$and [common {:state {$ne :canceled}}]}
        data       (mongo/select :applications filter projection)
        invites    (map :invite (mapcat :auth data))]
    (ok :invites invites)))

(defn- create-invite-model [command conf]
  (assoc (notifications/create-app-model command conf) :message (get-in command [:data :text]) ))

(notifications/defemail :invite  {:recipients-fn  notifications/from-data
                                  :model-fn create-invite-model})

(defcommand invite
  {:parameters [id email title text documentName documentId path]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :states     (action/all-application-states-but [:canceled])
   :roles      [:applicant :authority]
   :notified   true
   :on-success (notify :invite)}
  [{:keys [created user application] :as command}]
  (let [email (-> email ss/lower-case ss/trim)]
    (if (domain/invite application email)
      (fail :invite.already-has-auth)
      (let [invited (user-api/get-or-create-user-by-email email user)
            invite  {:title        title
                     :application  id
                     :text         text
                     :path         path
                     :documentName documentName
                     :documentId   documentId
                     :created      created
                     :email        email
                     :user         (user/summary invited)
                     :inviter      (user/summary user)}
            writer  (user/user-in-role invited :writer)
            auth    (assoc writer :invite invite)]
        (if (domain/has-auth? application (:id invited))
          (fail :invite.already-has-auth)
          (update-application command
            {:auth {$not {$elemMatch {:invite.user.username email}}}}
            {$push {:auth     auth}
             $set  {:modified created}}))))))

(defcommand approve-invite
  {:parameters [id]
   :roles      [:applicant]
   :states     (action/all-application-states-but [:sent :verdictGiven :constructionStarted :closed :canceled])}
  [{:keys [created user application] :as command}]
  (when-let [my-invite (domain/invite application (:email user))]
    (update-application command
      {:auth {$elemMatch {:invite.user.id (:id user)}}}
      {$set  {:modified created
              :auth.$ (assoc (user/user-in-role user :writer) :inviteAccepted created)}})
    (when-let [document (domain/get-document-by-id application (:documentId my-invite))]
      ; Document can be undefined in invite or removed by the time invite is approved.
      ; It's not possible to combine Mongo writes here,
      ; because only the last $elemMatch counts.
      (set-user-to-document (domain/get-application-as id user) document (:id user) (:path my-invite) user created))))

(defn generate-remove-invalid-user-from-docs-updates [{docs :documents :as application}]
  (-<>> docs
    (map-indexed
      (fn [i doc]
        (->> (model/validate application doc)
          (filter #(= (:result %) [:err "application-does-not-have-given-auth"]))
          (map (comp (partial map name) :path))
          (map (comp (partial join ".") (partial concat ["documents" i "data"]))))))
    flatten
    (zipmap <> (repeat ""))))

(defn- do-remove-auth [{application :application :as command} email]
  (let [email (-> email ss/lower-case ss/trim)
        user-pred #(when (and (= (:username %) email) (not= (:type %) "owner")) %)]
    (when (some user-pred (:auth application))
      (let [updated-app (update-in application [:auth] (fn [a] (remove user-pred a)))
            doc-updates (generate-remove-invalid-user-from-docs-updates updated-app)]
        (update-application command
          (merge
            {$pull {:auth {$and [{:username email}, {:type {$ne :owner}}]}}
             $set  {:modified (:created command)}}
            (when (seq doc-updates) {$unset doc-updates})))))))

(defcommand remove-auth
  {:parameters [:id email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:canceled])}
  [command]
  (do-remove-auth command email))

(defcommand decline-invitation
  {:parameters [:id]
   :roles [:applicant :authority]
   :states     (action/all-application-states-but [:canceled])}
  [command]
  (do-remove-auth command (get-in command [:user :email])))

(defcommand mark-seen
  {:parameters [:id type]
   :input-validators [(fn [{{type :type} :data}] (when-not (collections-to-be-seen type) (fail :error.unknown-type)))]
   :roles [:applicant :authority]
   :states (action/all-application-states-but [:canceled])}
  [{:keys [data user created] :as command}]
  (update-application command {$set (mark-collection-seen-update user created type)}))

(defcommand mark-everything-seen
  {:parameters [:id]
   :roles      [:authority]
   :states     (action/all-application-states-but [:canceled])}
  [{:keys [application user created] :as command}]
  (update-application command {$set (mark-indicators-seen-updates application user created)}))

(defcommand set-user-to-document
  {:parameters [id documentId userId path]
   :roles      [:applicant :authority]
   :states     (action/all-states-but [:info :sent :verdictGiven :constructionStarted :closed :canceled])}
  [{:keys [user created application] :as command}]
  (if-let [document (domain/get-document-by-id application documentId)]
    (set-user-to-document application document userId path user created)
    (fail :error.document-not-found)))

;;
;; Assign
;;

(defcommand assign-to-me
  {:parameters [:id]
   :roles      [:authority]
   :states     (action/all-application-states-but [:draft :closed :canceled])}
  [{:keys [user created] :as command}]
  (update-application command
    {$set {:modified created
           :authority (user/summary user)}}))

(defcommand assign-application
  {:parameters  [:id assigneeId]
   :pre-checks  [open-inforequest/not-open-inforequest-user-validator]
   :roles       [:authority]
   :states      (action/all-application-states-but [:draft :closed :canceled])}
  [{:keys [user created] :as command}]
  (let [assignee (mongo/select-one :users {:_id assigneeId :enabled true})]
    (if (or assignee (nil? assigneeId))
      (update-application command
                          (if assignee
                            {$set   {:modified created
                                     :authority (user/summary assignee)}}
                            {$unset {:authority ""}}))
      (fail "error.user.not.found" :id assigneeId))))

(defcommand cancel-application
  {:parameters [id]
   :roles      [:applicant :authority]
   :notified   true
   :on-success (notify :application-state-change)
   :states     [:draft :info :open :submitted]}
  [{:keys [created] :as command}]
  (update-application command
    {$set {:modified created
           :canceled created
           :state    :canceled}})
  (mongo/remove-many :app-links {:link {$in [id]}})
  (ok))

(defcommand open-application
  {:parameters [id]
   :roles      [:applicant :authority]
   :notified   true
   :on-success (notify :application-state-change)
   :states     [:draft]}
  [{:keys [created] :as command}]
  (update-application command
    {$set {:modified created
           :opened   created
           :state    :open}}))

(defcommand request-for-complement
  {:parameters [:id]
   :roles      [:authority]
   :notified   true
   :on-success (notify :application-state-change)
   :states     [:sent]}
  [{:keys [created] :as command}]
  (update-application command
    {$set {:modified created
           :complementNeeded created
           :state :complement-needed}}))


;; Application approval

(defn is-link-permit-required [application]
  (or (= :muutoslupa (keyword (:permitSubtype application)))
      (some #(operations/link-permit-required-operations (keyword (:name %))) (:operations application))))

(defn- validate-link-permits [application]
  (let [application (meta-fields/enrich-with-link-permit-data application)
        linkPermits (-> application :linkPermitData count)]
    (when (and (is-link-permit-required application) (= 0 linkPermits))
      (fail :error.permit-must-have-link-permit))))

(defn- update-link-permit-data-with-kuntalupatunnus-from-verdict [application]
  (let [link-permit-app-id (-> application :linkPermitData first :id)
        verdicts (domain/get-application {:_id link-permit-app-id} {:verdicts 1})
        kuntalupatunnus (-> verdicts :verdicts first :kuntalupatunnus)]
    (if kuntalupatunnus
      (-> application
         (assoc-in [:linkPermitData 0 :id] kuntalupatunnus)
         (assoc-in [:linkPermitData 0 :type] "kuntalupatunnus"))
      (do
        (error "Not able to get a kuntalupatunnus for the application  " (:id application) " from it link permit's (" link-permit-app-id ") verdict.")
        (fail! :error.kuntalupatunnus-not-available-from-verdict)))))

(defn- organization-has-ftp-user? [organization application]
  (not (ss/blank? (get-in organization [:krysp (keyword (permit/permit-type application)) :ftpUser]))))

(defn- do-approve [application created id lang jatkoaika-app? do-rest-fn]
  (let [organization (organization/get-organization (:organization application))]
    (if (organization-has-ftp-user? organization application)
      (or
        (validate-link-permits application)
        (let [sent-file-ids (if jatkoaika-app?
                              (mapping-to-krysp/save-jatkoaika-as-krysp application lang organization)
                              (let [submitted-application (mongo/by-id :submitted-applications id)]
                                (mapping-to-krysp/save-application-as-krysp application lang submitted-application organization)))
              attachments-updates (or (attachment/create-sent-timestamp-update-statements (:attachments application) sent-file-ids created) {})]
          (do-rest-fn attachments-updates)))
      ;; SFTP user not defined for the organization -> let the approve command pass
      (do-rest-fn nil))))

(defcommand approve-application
  {:parameters [id lang]
   :roles      [:authority]
   :notified   true
   :on-success (notify :application-state-change)
   :states     [:submitted :complement-needed]}
  [{:keys [application created user] :as command}]
  (let [jatkoaika-app? (= :ya-jatkoaika (-> application :operations first :name keyword))
        app-updates (merge
                      {:modified created
                       :sent created
                       :authority (if (seq (:authority application)) (:authority application) (user/summary user))} ; LUPA-1450
                      (if jatkoaika-app?
                        {:state :closed :closed created}
                        {:state :sent}))
        application (-> application
                      meta-fields/enrich-with-link-permit-data
                      ((fn [app]
                        (if (= "lupapistetunnus" (-> app :linkPermitData first :type))
                          (update-link-permit-data-with-kuntalupatunnus-from-verdict app)
                          app)))
                      (merge app-updates))
        mongo-query (if jatkoaika-app?
                      {:state {$in ["submitted" "complement-needed"]}}
                      {})
        indicator-updates (mark-indicators-seen-updates application user created)
        do-update (fn [attachments-updates]
                    (update-application command
                      mongo-query
                      {$set (merge app-updates attachments-updates indicator-updates)})
                    (ok :integrationAvailable (not (nil? attachments-updates))))]

    (do-approve application created id lang jatkoaika-app? do-update)))


(defn- do-submit [command application created]
  (update-application command
                      {$set {:state     :submitted
                             :modified  created
                             :opened    (or (:opened application) created)
                             :submitted (or (:submitted application) created)}})
  (try
    (mongo/insert :submitted-applications
      (-> (meta-fields/enrich-with-link-permit-data application) (dissoc :id) (assoc :_id (:id application))))
    (catch com.mongodb.MongoException$DuplicateKey e
      ; This is ok. Only the first submit is saved.
      )))

(defcommand submit-application
  {:parameters [id]
   :roles      [:applicant :authority]
   :states     [:draft :open]
   :notified   true
   :on-success (notify :application-state-change)
   :pre-checks [validate-owner-or-writer]}
  [{:keys [application created] :as command}]
  (or (validate-link-permits application)
      (do-submit command application created)))

(defcommand refresh-ktj
  {:parameters [:id]
   :roles      [:authority]
   :states     [:draft :open :submitted :complement-needed]}
  [{:keys [application created]}]
  (try (autofill-rakennuspaikka application created)
    (catch Exception e (error e "KTJ data was not updated"))))

(defcommand save-application-drawings
  {:parameters [:id drawings]
   :roles      [:applicant :authority]
   :states     [:draft :info :answered :open :submitted :complement-needed]}
  [{:keys [created] :as command}]
  (when (sequential? drawings)
    (update-application command
      {$set {:modified created
             :drawings drawings}})))

(defn- make-marker-contents [id lang app]
  (merge
    {:id          (:id app)
     :title       (:title app)
     :location    (:location app)
     :operation   (->> (:operations app) first :name (i18n/localize lang "operations"))
     :authName    (-> (domain/get-auths-by-role app :owner)
                    first
                    (#(str (:firstName %) " " (:lastName %))))
     :comments    (->> (:comments app)
                    (filter #(not (= "system" (:type %))))
                    (map #(identity {:name (str (-> % :user :firstName) " " (-> % :user :lastName))
                                     :type (:type %)
                                     :time (:created %)
                                     :text (:text %)})))}
    (when-not (= id (:id app))
      {:link      (str (env/value :host) "/app/" (name lang) "/authority#!/inforequest/" (:id app))})))

(defn- remove-irs-by-id [target-irs irs-to-be-removed]
  (remove (fn [ir] (some #(= (:id ir) (:id %)) irs-to-be-removed)) target-irs))

(defquery inforequest-markers
  {:parameters [id lang x y]
   :roles      [:authority]
   :states     action/all-inforequest-states
   :input-validators [(partial action/non-blank-parameters [:x :y])]}
  [{:keys [application user]}]
  (let [x (util/->double x)
        y (util/->double y)
        inforequests (mongo/select :applications
                       (merge
                         (domain/application-query-for user)
                         {:infoRequest true})
                       {:title 1 :auth 1 :location 1 :operations 1 :comments 1})

        same-location-irs (filter
                            #(and (== x (-> % :location :x)) (== y (-> % :location :y)))
                            inforequests)

        inforequests (remove-irs-by-id inforequests same-location-irs)

        application-op-name (-> application :operations first :name)  ;; an inforequest can only have one operation

        same-op-irs (filter
                      (fn [ir]
                        (some #(= application-op-name (:name %)) (:operations ir)))
                      inforequests)

        others (remove-irs-by-id inforequests same-op-irs)

        same-location-irs (map (partial make-marker-contents id lang) same-location-irs)
        same-op-irs       (map (partial make-marker-contents id lang) same-op-irs)
        others            (map (partial make-marker-contents id lang) others)]

    (ok :sameLocation same-location-irs :sameOperation same-op-irs :others others)
    ))

(defn- make-attachments [created operation organization applicationState & {:keys [target]}]
  (for [[type-group type-id] (organization/get-organization-attachments-for-operation organization operation)]
    (attachment/make-attachment created target false applicationState operation {:type-group type-group :type-id type-id})))

(defn- schema-data-to-body [schema-data application]
  (keywordize-keys
    (reduce
      (fn [body [data-path data-value]]
        (let [path (if (= :value (last data-path)) data-path (conj (vec data-path) :value))
              val (if (fn? data-value) (data-value application) data-value)]
          (assoc-in body path val)))
      {} schema-data)))

;; TODO: permit-type splitting.
(defn- make-documents [user created op application]
  (let [op-info               (operations/operations (keyword (:name op)))
        op-schema-name        (:schema op-info)
        existing-documents    (:documents application)
        schema-version        (:schema-version application)
        make                  (fn [schema-name]
                                {:id (mongo/create-id)
                                 :schema-info (:info (schemas/get-schema schema-version schema-name))
                                 :created created
                                 :data (tools/timestamped
                                         (condp = schema-name
                                           op-schema-name           (schema-data-to-body (:schema-data op-info) application)
                                           "yleiset-alueet-maksaja" (schema-data-to-body operations/schema-data-yritys-selected application)
                                           "tyomaastaVastaava"      (schema-data-to-body operations/schema-data-yritys-selected application)
                                           {})
                                         created)})
        existing-schema-names (set (map (comp :name :schema-info) existing-documents))
        required-schema-names (remove existing-schema-names (:required op-info))
        required-docs         (map make required-schema-names)
        ;;The merge below: If :removable is set manually in schema's info, do not override it to true.
        op-doc                (update-in (make op-schema-name) [:schema-info] #(merge {:op op :removable true} %))
        new-docs              (cons op-doc required-docs)]
    (if-not user
      new-docs
      (let [permit-type (keyword (permit/permit-type application))
            hakija      (condp = permit-type
                          :YA (assoc-in (make "hakija-ya") [:data :_selected :value] "yritys")
                          (assoc-in (make "hakija") [:data :_selected :value] "henkilo"))]
        (conj new-docs hakija)))))

(defn- ->location [x y]
  {:x (util/->double x) :y (util/->double y)})

(defn- make-application-id [municipality]
  (let [year           (str (year (local-now)))
        sequence-name  (str "applications-" municipality "-" year)
        counter        (format "%05d" (mongo/get-next-sequence-value sequence-name))]
    (str "LP-" municipality "-" year "-" counter)))

(defn- make-op [op-name created]
  {:id (mongo/create-id)
   :name (keyword op-name)
   :created created})

(defn user-is-authority-in-organization? [user-id organization-id]
  (mongo/any? :users {$and [{:organizations organization-id} {:_id user-id}]}))

(defn- operation-validator [{{operation :operation} :data}]
  (when-not (operations/operations (keyword operation)) (fail :error.unknown-type)))

(defn make-application [id operation x y address property-id municipality organization info-request? open-inforequest? messages user created]
  (let [permit-type (operations/permit-type-of-operation operation)
        owner       (user/user-in-role user :owner :type :owner)
        op          (make-op operation created)
        state       (cond
                      info-request?          :info
                      (user/authority? user) :open
                      :else                  :draft)
        application (merge domain/application-skeleton
                      {:id                  id
                       :created             created
                       :opened              (when (#{:open :info} state) created)
                       :modified            created
                       :permitType          permit-type
                       :permitSubtype       (first (permit/permit-subtypes permit-type))
                       :infoRequest         info-request?
                       :openInfoRequest     open-inforequest?
                       :operations          [op]
                       :state               state
                       :municipality        municipality
                       :location            (->location x y)
                       :organization        (:id organization)
                       :address             address
                       :propertyId          property-id
                       :title               address
                       :auth                [owner]
                       :comments            (map #(domain/->comment % {:type "application"} (:role user) user nil created [:applicant :authority]) messages)
                       :schema-version      (schemas/get-latest-schema-version)})]
    (merge application (when-not info-request?
                         {:attachments (make-attachments created op organization state)
                          :documents   (make-documents user created op application)}))))

(defn- do-create-application
  [{{:keys [operation x y address propertyId municipality infoRequest messages]} :data :keys [user created] :as command}]
  (let [permit-type       (operations/permit-type-of-operation operation)
        organization      (organization/resolve-organization municipality permit-type)
        scope             (organization/resolve-organization-scope organization municipality permit-type)
        organization-id   (:id organization)
        info-request?     (boolean infoRequest)
        open-inforequest? (and info-request? (:open-inforequest scope))
        id                (make-application-id municipality)]
    (when-not (or (user/applicant? user) (user-is-authority-in-organization? (:id user) organization-id))
      (unauthorized!))
    (when-not organization-id
      (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))
    (if info-request?
      (when-not (:inforequest-enabled scope)
        (fail! :error.inforequests-disabled))
      (when-not (:new-application-enabled scope)
        (fail! :error.new-applications-disabled)))
    (make-application id operation x y address propertyId municipality organization info-request? open-inforequest? messages user created)))

;; TODO: separate methods for inforequests & applications for clarity.
(defcommand create-application
  {:parameters [:operation :x :y :address :propertyId :municipality]
   :roles      [:applicant :authority]
   :notified   true ; OIR
   :input-validators [(partial action/non-blank-parameters [:operation :address :municipality])
                      (partial property-id-parameters [:propertyId])
                      operation-validator]}
  [{{:keys [operation address municipality infoRequest]} :data :keys [user created] :as command}]

  ;; TODO: These let-bindings are repeated in do-create-application, merge th somehow
  (let [permit-type       (operations/permit-type-of-operation operation)
        organization      (organization/resolve-organization municipality permit-type)
        scope             (organization/resolve-organization-scope organization municipality permit-type)
        info-request?     (boolean infoRequest)
        open-inforequest? (and info-request? (:open-inforequest scope))
        created-application (do-create-application command)]

      (insert-application created-application)
      (when open-inforequest?
        (open-inforequest/new-open-inforequest! created-application))
      (try
        (autofill-rakennuspaikka created-application created)
        (catch Exception e (error e "KTJ data was not updated")))
      (ok :id (:id created-application))))

(defn- add-operation-allowed? [_ application]
  (let [op (-> application :operations first :name keyword)
        permitSubType (keyword (:permitSubtype application))]
    (when-not (and (or (nil? op) (:add-operation-allowed (op operations/operations)))
                   (not= permitSubType :muutoslupa))
      (fail :error.add-operation-not-allowed))))

(defcommand add-operation
  {:parameters [id operation]
   :roles      [:applicant :authority]
   :states     [:draft :open :submitted :complement-needed]
   :input-validators [operation-validator]
   :pre-checks [add-operation-allowed?]}
  [{:keys [application created] :as command}]
  (let [op-id      (mongo/create-id)
        op         (make-op operation created)
        new-docs   (make-documents nil created op application)
        organization (organization/get-organization (:organization application))]
    (update-application command {$push {:operations op
                                        :documents {$each new-docs}
                                        :attachments {$each (make-attachments created op organization (:state application))}}
                                 $set {:modified created}})))

(defcommand change-permit-sub-type
  {:parameters [id permitSubtype]
   :roles      [:applicant :authority]
   :states     [:draft :open :submitted :complement-needed]
   :pre-checks [permit/validate-permit-has-subtypes]}
  [{:keys [application created] :as command}]
  (if-let [validation-errors (permit/is-valid-subtype (keyword permitSubtype) application)]
    validation-errors
    (update-application command
      {$set {:permitSubtype permitSubtype
             :modified      created}})))

(defcommand change-location
  {:parameters [id x y address propertyId]
   :roles      [:applicant :authority]
   :states     [:draft :info :answered :open :submitted :complement-needed]
   :input-validators [(partial action/non-blank-parameters [:address])
                      (partial property-id-parameters [:propertyId])
                      validate-x validate-y]}
  [{:keys [created application] :as command}]
  (if (= (:municipality application) (organization/municipality-by-propertyId propertyId))
    (do
      (update-application command
        {$set {:location      (->location x y)
               :address       (trim address)
               :propertyId    propertyId
               :title         (trim address)
               :modified      created}})
      (try (autofill-rakennuspaikka (mongo/by-id :applications id) (now))
        (catch Exception e (error e "KTJ data was not updated."))))
    (fail :error.property-in-other-muinicipality)))

;;
;; Link permits
;;

(defquery link-permit-required
  {:description "Dummy command for UI logic: returns falsey if link permit is not required."
   :parameters [:id]
   :roles      [:applicant :authority]
   :states     [:draft :open :submitted :complement-needed]
   :pre-checks [(fn [_ application]
                  (when-not (is-link-permit-required application)
                    (fail :error.link-permit-not-required)))]})

(defquery app-matches-for-link-permits
  {:parameters [id]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:sent :closed :canceled])}
  [{{:keys [propertyId] :as application} :application user :user :as command}]
  (let [results (mongo/select :applications
                  (merge (domain/application-query-for user) {:_id {$ne id}
                                                              :state {$nin ["draft"]}
                                                              :infoRequest false
                                                              :permitType (:permitType application)
                                                              :operations.name {$nin ["ya-jatkoaika"]}})
                  {:_id 1 :permitType 1 :address 1 :propertyId 1})
        enriched-results (map
                           (fn [r]
                             (assoc r :text
                               (str
                                 (:address r) ", "
                                 (:id r))))
                           results)
        same-property-id-fn #(= propertyId (:propertyId %))
        with-same-property-id (vec (filter same-property-id-fn enriched-results))
        without-same-property-id (sort-by :text (vec (remove same-property-id-fn enriched-results)))
        organized-results (flatten (conj with-same-property-id without-same-property-id))
        final-results (map #(select-keys % [:id :text]) organized-results)]
    (ok :app-links final-results)))

(defn- make-mongo-id-for-link-permit [app-id link-permit-id]
  (if (<= (compare app-id link-permit-id) 0)
    (str app-id "|" link-permit-id)
    (str link-permit-id "|" app-id)))


(defn- do-add-link-permit [{:keys [id propertyId operations]} link-permit-id]
  {:pre [(mongo/valid-key? link-permit-id)
         (not= id link-permit-id)]}
  (let [db-id (make-mongo-id-for-link-permit id link-permit-id)]
    (mongo/update-by-id :app-links db-id
      {:_id  db-id
       :link [id link-permit-id]
       id    {:type "application"
              :apptype (:name (first operations))
              :propertyId propertyId}
       link-permit-id {:type "linkpermit"
                       :linkpermittype (if (.startsWith link-permit-id "LP-")
                                         "lupapistetunnus"
                                         "kuntalupatunnus")}}
      :upsert true)))

(defn- validate-jatkolupa-zero-link-permits [_ application]
  (let [application (meta-fields/enrich-with-link-permit-data application)]
    (when (and (= :ya-jatkoaika (-> application :operations first :name keyword))
            (not= 0 (-> application :linkPermitData count)))
      (fail :error.jatkolupa-can-only-be-added-one-link-permit))))

(defcommand add-link-permit
  {:parameters ["id" linkPermitId]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:sent :closed :canceled]);; Pitaako olla myos 'sent'-tila?
   :pre-checks [validate-jatkolupa-zero-link-permits]
   :input-validators [(partial action/non-blank-parameters [:linkPermitId])
                      (fn [{d :data}] (when-not (mongo/valid-key? (:linkPermitId d)) (fail :error.invalid-db-key)))]}
  [{application :application}]
  (do-add-link-permit application (ss/trim linkPermitId))
  (ok))

(defcommand remove-link-permit-by-app-id
  {:parameters [id linkPermitId]
   :roles      [:applicant :authority]
   :states     [:draft :open :submitted :complement-needed :verdictGiven :constructionStarted]}   ;; Pitaako olla myos 'sent'-tila?
  [{application :application}]
  (if (mongo/remove :app-links (make-mongo-id-for-link-permit id linkPermitId))
    (ok)
    (fail :error.unknown)))


;;
;; Change permit
;;

(defcommand create-change-permit
  {:parameters ["id"]
   :roles      [:applicant :authority]
   :states     [:verdictGiven :constructionStarted]
   :pre-checks [(permit/validate-permit-type-is permit/R)]}
  [{:keys [created user application] :as command}]
  (let [muutoslupa-app-id (make-application-id (:municipality application))
        muutoslupa-app (merge application
                         {:id                  muutoslupa-app-id
                          :created             created
                          :opened              created
                          :modified            created
                          :documents           (into [] (map
                                                          #(assoc % :id (mongo/create-id))
                                                          (:documents application)))
                          :state               (cond
                                                 (user/authority? user)  :open
                                                 :else                   :draft)
                          :permitSubtype       :muutoslupa}
                         (select-keys
                           domain/application-skeleton
                           [:attachments :statements :verdicts :comments :submitted :sent :neighbors
                            :_statements-seen-by :_comments-seen-by :_verdicts-seen-by]))]
    (do-add-link-permit muutoslupa-app (:id application))
    (insert-application muutoslupa-app)
    (ok :id muutoslupa-app-id)))


;;
;; Continuation period permit
;;

(defn- get-tyoaika-alkaa-from-ya-app [app]
  (let [mainostus-viitoitus-tapahtuma-doc (:data (domain/get-document-by-name app "mainosten-tai-viitoitusten-sijoittaminen"))
        tapahtuma-name-key (when mainostus-viitoitus-tapahtuma-doc
                             (-> mainostus-viitoitus-tapahtuma-doc :_selected :value keyword))
        tapahtuma-data (when tapahtuma-name-key
                         (mainostus-viitoitus-tapahtuma-doc tapahtuma-name-key))]
    (if (:started app)
      (util/to-local-date (:started app))
      (or
        (-> (domain/get-document-by-name app "tyoaika") :data :tyoaika-alkaa-pvm :value)
        (-> tapahtuma-data :tapahtuma-aika-alkaa-pvm :value)
        (util/to-local-date (:submitted app))))))

(defn- validate-not-jatkolupa-app [_ application]
  (when (= :ya-jatkoaika (-> application :operations first :name keyword))
    (fail :error.cannot-apply-jatkolupa-for-jatkolupa)))

(defcommand create-continuation-period-permit
  {:parameters ["id"]
   :roles      [:applicant :authority]
   :states     [:verdictGiven :constructionStarted]
   :pre-checks [(permit/validate-permit-type-is permit/YA) validate-not-jatkolupa-app]}
  [{:keys [created user application] :as command}]

  (let [continuation-app (do-create-application
                           (assoc command :data {:operation "ya-jatkoaika"
                                                 :x (-> application :location :x)
                                                 :y (-> application :location :y)
                                                 :address (:address application)
                                                 :propertyId (:propertyId application)
                                                 :municipality (:municipality application)
                                                 :infoRequest false
                                                 :messages []}))
        continuation-app (merge continuation-app {:authority (:authority application)})
        ;;
        ;; ************
        ;; Lain mukaan hankeen aloituspvm on hakupvm + 21pv, tai kunnan paatospvm jos se on tata aiempi.
        ;; kts.  http://www.finlex.fi/fi/laki/alkup/2005/20050547 ,  14 a pykala
        ;; ************
        ;;
        tyoaika-alkaa-pvm (get-tyoaika-alkaa-from-ya-app application)
        tyo-aika-for-jatkoaika-doc (-> continuation-app
                                     (domain/get-document-by-name "tyo-aika-for-jatkoaika")
                                     (assoc-in [:data :tyoaika-alkaa-pvm :value] tyoaika-alkaa-pvm))

        continuation-app (assoc continuation-app
                           :documents [(domain/get-document-by-name continuation-app "hankkeen-kuvaus-jatkoaika")
                                       tyo-aika-for-jatkoaika-doc
                                       (domain/get-document-by-name application "hakija-ya")
                                       (domain/get-document-by-name application "yleiset-alueet-maksaja")])]

    (do-add-link-permit continuation-app (:id application))
    (insert-application continuation-app)
    (ok :id (:id continuation-app))))

;;
;; Inform construction started & ready
;;

(defcommand inform-construction-started
  {:parameters ["id" startedTimestampStr]
   :roles      [:applicant :authority]
   :states     [:verdictGiven]
   :notified   true
   :on-success (notify :application-state-change)
   :pre-checks [(permit/validate-permit-type-is permit/YA)]
   :input-validators [(partial action/non-blank-parameters [:startedTimestampStr])]}
  [{:keys [user created] :as command}]
  (let [timestamp (util/to-millis-from-local-date-string startedTimestampStr)]
    (update-application command {$set {:modified created
                                       :started timestamp
                                       :startedBy (select-keys user [:id :firstName :lastName])
                                       :state  :constructionStarted}}))
  (ok))

(defcommand inform-building-construction-started
  {:parameters ["id" buildingIndex startedDate lang]
   :roles      [:NONE] ;FIXME rakentamisen aikaisen toimminan yhteydessa korjataan oikeae
   :states     [:verdictGiven :constructionStarted]
   :notified   true
   :pre-checks [(permit/validate-permit-type-is permit/R)]
   :input-validators [(partial action/non-blank-parameters [:buildingIndex :startedDate :lang])]}
  [{:keys [user created application] :as command}]
  (let [timestamp     (util/to-millis-from-local-date-string startedDate)
        app-updates   (merge
                        {:modified created}
                        (when (= "verdictGiven" (:state application))
                          {:started created
                           :state  :constructionStarted}))
        application   (merge application app-updates)
        organization  (organization/get-organization (:organization application))
        ftp-user?     (organization-has-ftp-user? organization application)
        building      (or
                        (some #(when (= (str buildingIndex) (:index %)) %) (:buildings application))
                        (fail! :error.unknown-building))]
    (when ftp-user?
      (mapping-to-krysp/save-aloitusilmoitus-as-krysp application lang organization timestamp building user))
    (update-application command
      {:buildings {$elemMatch {:index (:index building)}}}
      {$set (merge app-updates {:buildings.$.constructionStarted timestamp
                                :buildings.$.startedBy (select-keys user [:id :firstName :lastName])})})
    (when (= "verdictGiven" (:state application))
      (notifications/notify! :application-state-change command))
    (ok :integrationAvailable ftp-user?)))

(defcommand inform-construction-ready
  {:parameters ["id" readyTimestampStr lang]
   :roles      [:applicant :authority]
   :states     [:constructionStarted]
   :on-success (notify :application-state-change)
   :pre-checks [(permit/validate-permit-type-is permit/YA)]
   :input-validators [(partial action/non-blank-parameters [:readyTimestampStr])]}
  [{:keys [user created application] :as command}]
  (let [timestamp     (util/to-millis-from-local-date-string readyTimestampStr)
        app-updates   {:modified created
                       :closed timestamp
                       :closedBy (select-keys user [:id :firstName :lastName])
                       :state :closed}
        application   (merge application app-updates)
        organization  (organization/get-organization (:organization application))
        ftp-user?     (organization-has-ftp-user? organization application)]
    (when ftp-user?
      (mapping-to-krysp/save-application-as-krysp application lang application organization))
    (update-application command {$set app-updates})
    (ok :integrationAvailable ftp-user?)))


(defn- validate-new-applications-enabled [command {:keys [permitType municipality]}]
  (let [org   (organization/resolve-organization municipality permitType)
        scope (organization/resolve-organization-scope org municipality permitType)]
    (when-not (= (:new-application-enabled scope) true)
      (fail :error.new-applications.disabled))))

(defcommand convert-to-application
  {:parameters [id]
   :roles      [:applicant]
   :states     action/all-inforequest-states
   :pre-checks [validate-new-applications-enabled]}
  [{:keys [user created application] :as command}]
  (let [op (first (:operations application))
        organization (organization/get-organization (:organization application))]
    (update-application command
      {$set {:infoRequest false
             :state :open
             :documents (make-documents user created op application)
             :modified created}
       $push {:attachments {$each (make-attachments created op organization (:state application))}}})
    (try (autofill-rakennuspaikka application (now))
      (catch Exception e (error e "KTJ data was not updated")))))

;;
;; krysp enrichment
;;

(defn add-value-metadata [m meta-data]
  (reduce (fn [r [k v]] (assoc r k (if (map? v) (add-value-metadata v meta-data) (assoc meta-data :value v)))) {} m))

(defcommand merge-details-from-krysp
  {:parameters [id documentId buildingId collection]
   :input-validators [commands/validate-collection]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:sent :verdictGiven :constructionStarted :closed :canceled])}   ;; TODO: Info state removed, ok?
  [{created :created {:keys [organization propertyId] :as application} :application :as command}]
  (if-let [{url :url} (organization/get-krysp-wfs application)]
    (let [document     (commands/by-id application collection documentId)
          schema       (schemas/get-schema (:schema-info document))
          kryspxml     (krysp/building-xml url propertyId)
          updates      (-> (or (krysp/->rakennuksen-tiedot kryspxml buildingId) {}) tools/unwrapped tools/path-vals)
          ; Path should exist in schema!
          updates      (filter (fn [[path _]] (model/find-by-name (:body schema) path)) updates)]
      (infof "merging data into %s %s" (get-in document [:schema-info :name]) (:id document))
      (when (seq updates)
        (commands/persist-model-updates application collection document updates created :source "krysp"))
      (ok))
    (fail :error.no-legacy-available)))

(defcommand get-building-info-from-wfs
  {:parameters [id]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:sent :verdictGiven :constructionStarted :closed :canceled])}   ;; TODO: Info state removed, ok?
  [{{:keys [organization propertyId] :as application} :application}]
  (if-let [{url :url} (organization/get-krysp-wfs application)]
    (let [kryspxml  (krysp/building-xml url propertyId)
          buildings (krysp/->buildings-summary kryspxml)]
      (ok :data buildings))
    (fail :error.no-legacy-available)))

;;
;; Service point for jQuery dataTables:
;;

(defquery applications-for-datatables
  {:parameters [params]
   :roles      [:applicant :authority]}
  [{user :user}]
  (ok :data (search/applications-for-user user params)))
