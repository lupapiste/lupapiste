(ns lupapalvelu.application
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [clojure.string :refer [blank? join trim split]]
            [clojure.walk :refer [keywordize-keys]]
            [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [clj-time.format :as tf]
            [monger.operators :refer :all]
            [swiss.arrows :refer [-<>>]]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.xml :as xml]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand update-application without-system-keys notify application->command] :as action]
            [lupapalvelu.mongo :refer [$each] :as mongo]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.document.commands :as commands]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.user :as user]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.verdict-api :as verdict-api]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch-api]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rakennuslupa-mapping]
            [lupapalvelu.ktj :as ktj]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.company :as c]))

;; Notifications

(notifications/defemail :application-state-change
  {:subject-key    "state-change"
   :application-fn (fn [{id :id}] (domain/get-application-no-access-checking id))})

;; Validators

(defn- property-id? [^String s]
  (and s (re-matches #"^[0-9]{14}$" s)))

(defn property-id-parameters [params command]
  (when-let [invalid (seq (filter #(not (property-id? (get-in command [:data %]))) params))]
    (info "invalid property id parameters:" (join ", " invalid))
    (fail :error.invalid-property-id :parameters (vec invalid))))

(defn- validate-x [{{:keys [x]} :data}]
  (when (and x (not (< 10000 (util/->double x) 800000)))
    (fail :error.illegal-coordinates)))

(defn- validate-y [{{:keys [y]} :data}]
  (when (and y (not (<= 6610000 (util/->double y) 7779999)))
    (fail :error.illegal-coordinates)))


;; Helpers

(defn set-user-to-document [application document user-id path current-user timestamp]
  {:pre [document]}
  (when-not (ss/blank? user-id)
    (let [path-arr     (if-not (blank? path) (split path #"\.") [])
          schema       (schemas/get-schema (:schema-info document))
          subject      (user/get-user-by-id user-id)
          with-hetu    (model/has-hetu? (:body schema) path-arr)
          person       (tools/unwrapped (model/->henkilo subject :with-hetu with-hetu :with-empty-defaults true))
          model        (if (seq path-arr)
                         (assoc-in {} (map keyword path-arr) person)
                         person)
          updates      (tools/path-vals model)
          ; Path should exist in schema!
          updates      (filter (fn [[update-path _]] (model/find-by-name (:body schema) update-path)) updates)]
      (when-not schema (fail! :error.schema-not-found))
      (when-not subject (fail! :error.user-not-found))
      (when-not (and (domain/has-auth? application user-id) (domain/no-pending-invites? application user-id))
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

(defn- process-documents [user {authority :authority :as application}]
  (let [validate (fn [doc] (assoc doc :validationErrors (model/validate application doc)))
        mask-person-ids (if-not (user/same-user? user authority) model/mask-person-ids identity)
        doc-mapper (comp mask-person-ids validate)]
    (update-in application [:documents] (partial map doc-mapper))))

(defn- post-process-app [app user]
  (-> app
    meta-fields/enrich-with-link-permit-data
    ((partial meta-fields/with-meta-fields user))
    without-system-keys
    ((partial process-documents user))))

(defn find-authorities-in-applications-organization [app]
  (mongo/select :users
    {:organizations (:organization app) :role "authority" :enabled true}
    {:firstName 1, :lastName 1}
    (array-map :lastName 1, :firstName 1)))

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

(defcommand assign-application
  {:parameters  [:id assigneeId]
   :pre-checks  [open-inforequest/not-open-inforequest-user-validator]
   :roles       [:authority]
   :states      (action/all-states-but [:draft :closed :canceled])}
  [{:keys [user created application] :as command}]
  (let [assignee (user/find-user {:_id assigneeId :enabled true
                                  :role "authority" :organizations (:organization application)})]
    (if (or assignee (ss/blank? assigneeId))
      (update-application command
        {$set {:modified created
               :authority  (if assignee (user/summary assignee) (:authority domain/application-skeleton))}})
      (fail "error.user.not.found"))))

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
         (assoc-in [:linkPermitData 0 :lupapisteId] link-permit-app-id)
         (assoc-in [:linkPermitData 0 :id] kuntalupatunnus)
         (assoc-in [:linkPermitData 0 :type] "kuntalupatunnus"))
      (do
        (error "Not able to get a kuntalupatunnus for the application  " (:id application) " from it's link permit's (" link-permit-app-id ") verdict."
               " Associated Link-permit data: " (:linkPermitData application))
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
                      (#(if (= "lupapistetunnus" (-> % :linkPermitData first :type))
                         (update-link-permit-data-with-kuntalupatunnus-from-verdict %)
                         %))
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
      (-> application meta-fields/enrich-with-link-permit-data (dissoc :id) (assoc :_id (:id application))))
    (catch com.mongodb.MongoException$DuplicateKey e
      ; This is ok. Only the first submit is saved.
      )))

(defcommand submit-application
  {:parameters [id]
   :roles      [:applicant :authority]
   :states     [:draft :open]
   :notified   true
   :on-success (notify :application-state-change)
   :pre-checks [domain/validate-owner-or-writer]}
  [{:keys [application created] :as command}]
  (or (validate-link-permits application)
      (do-submit command application created)))

(defcommand refresh-ktj
  {:parameters [:id]
   :roles      [:authority]
   :states     action/all-states}
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
     :authName    (-> app
                    (domain/get-auths-by-role :owner)
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
                       [:title :auth :location :operations :comments])

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
(defn- make-documents [user created op application & [manual-schema-datas]]
  {:pre [(or (nil? manual-schema-datas) (map? manual-schema-datas))]}

  (let [op-info               (operations/operations (keyword (:name op)))
        op-schema-name        (:schema op-info)
        schema-version        (:schema-version application)
        default-schema-datas  (util/assoc-when {}
                                op-schema-name           (:schema-data op-info)
                                "yleiset-alueet-maksaja" operations/schema-data-yritys-selected
                                "tyomaastaVastaava"      operations/schema-data-yritys-selected)
        merged-schema-datas   (merge-with conj default-schema-datas manual-schema-datas)
        make                  (fn [schema-name]
                                {:id (mongo/create-id)
                                 :schema-info (:info (schemas/get-schema schema-version schema-name))
                                 :created created
                                 :data (tools/timestamped
                                         (if-let [schema-data (get-in merged-schema-datas [schema-name])]
                                           (schema-data-to-body schema-data application)
                                           {})
                                         created)})
        ;;The merge below: If :removable is set manually in schema's info, do not override it to true.
        op-doc                (update-in (make op-schema-name) [:schema-info] #(merge {:op op :removable true} %))
        new-docs (-<>> (:documents application)
                   (map (comp :name :schema-info))  ;; existing schema names
                   set
                   (remove <> (:required op-info))  ;; required schema names
                   (map make)                       ;; required docs
                   (cons op-doc))]                  ;; new docs
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
   :description nil
   :created created})

(defn user-is-authority-in-organization? [user-id organization-id]
  (mongo/any? :users {$and [{:organizations organization-id} {:_id user-id}]}))

(defn- operation-validator [{{operation :operation} :data}]
  (when-not (operations/operations (keyword operation)) (fail :error.unknown-type)))

(defn make-application [id operation x y address property-id municipality organization info-request? open-inforequest? messages user created manual-schema-datas]
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
                       :auth                (if-let [company (some-> user :company :id c/find-company-by-id c/company->auth)]
                                              [owner company]
                                              [owner])
                       :comments            (map #(domain/->comment % {:type "application"} (:role user) user nil created [:applicant :authority]) messages)
                       :schema-version      (schemas/get-latest-schema-version)})]
    (merge application (when-not info-request?
                         {:attachments (make-attachments created op organization state)
                          :documents   (make-documents user created op application manual-schema-datas)}))))

(defn- do-create-application
  [{{:keys [operation x y address propertyId municipality infoRequest messages]} :data :keys [user created] :as command} & [manual-schema-datas]]
  (let [permit-type       (operations/permit-type-of-operation operation)
        organization      (organization/resolve-organization municipality permit-type)
        scope             (organization/resolve-organization-scope municipality permit-type organization)
        organization-id   (:id organization)
        info-request?     (boolean infoRequest)
        open-inforequest? (and info-request? (:open-inforequest scope))]

    (when-not (or (user/applicant? user) (user-is-authority-in-organization? (:id user) organization-id))
      (unauthorized!))
    (when-not organization-id
      (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))
    (if info-request?
      (when-not (:inforequest-enabled scope)
        (fail! :error.inforequests-disabled))
      (when-not (:new-application-enabled scope)
        (fail! :error.new-applications-disabled)))

    (let [id (make-application-id municipality)]
      (make-application id operation x y address propertyId municipality organization info-request? open-inforequest? messages user created manual-schema-datas))))

;; TODO: separate methods for inforequests & applications for clarity.
(defcommand create-application
  {:parameters [:operation :x :y :address :propertyId :municipality]
   :roles      [:applicant :authority]
   :notified   true ; OIR
   :input-validators [(partial action/non-blank-parameters [:operation :address :municipality])
                      (partial property-id-parameters [:propertyId])
                      operation-validator]}
  [{{:keys [operation address municipality infoRequest]} :data :keys [user created] :as command}]
  (let [permit-type (operations/permit-type-of-operation operation)
        organization (organization/resolve-organization municipality permit-type)]

  ;; TODO: These let-bindings are repeated in do-create-application, merge th somehow
    (let [scope             (organization/resolve-organization-scope municipality permit-type organization)
          info-request?     (boolean infoRequest)
          open-inforequest? (and info-request? (:open-inforequest scope))
          created-application (do-create-application command)]

      (insert-application created-application)
      (when open-inforequest?
        (open-inforequest/new-open-inforequest! created-application))
      (try
        (autofill-rakennuspaikka created-application created)
        (catch Exception e (error e "KTJ data was not updated")))
      (ok :id (:id created-application)))))

(defcommand create-application-from-previous-permit
  {:parameters [:operation :x :y :address :propertyId :municipality :kuntalupatunnus]
   :roles      [:applicant :authority]
   :notified   true ; OIR
   :input-validators [(partial action/non-blank-parameters [:operation :municipality])  ;; no :address included
                      #_(partial property-id-parameters [:propertyId])
                      operation-validator]}
  [{{:keys [operation x y address propertyId municipality kuntalupatunnus]} :data :keys [user created] :as command}]

  ;;
  ;; TODO: Pitaisiko propertyId ja address tarkistaa tassa?
  ;;
;  (when-not (ss/blank? propertyId) (property-id-parameters [:propertyId] propertyId))

  (let [permit-type (operations/permit-type-of-operation operation)
        organization (organization/resolve-organization municipality permit-type)]

    ;; Prevent creating many applications based on the same kuntalupatunnus:
    ;; Check if we have in database an application of same organization that has a verdict with the given kuntalupatunnus.
    ;; If so, open that application, otherwise go create a new application.
    (if-let [app-with-verdict (domain/get-application {:organization (:id organization)
                                                       :verdicts {$elemMatch {:kuntalupatunnus kuntalupatunnus}}})]

      (if (domain/owner-or-writer? app-with-verdict (:id user))
        (ok :id (:id app-with-verdict))
        (fail :error.lupapiste-application-already-exists-but-unauthorized-to-access-it :id (:id app-with-verdict)))

      ;; Fetch application from backing system with the provided kuntalupatunnus
      (if-let [xml (krysp-fetch-api/get-application-xml
                     {:id kuntalupatunnus :permitType permit-type :organization (:id organization)}
                     false true)]

        (let [enough-info-from-parameters (and
                                            (not (ss/blank? address)) (not (ss/blank? propertyId))
                                            (-> x util/->double pos?) (-> y util/->double pos?))
              app-info (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
              rakennuspaikka-exists (and (:rakennuspaikka app-info) (every? #{:x :y :address :propertyId} (-> app-info :rakennuspaikka keys)))
              lupapiste-tunnus (:id app-info)
              asian-kuvaus (:rakennusvalvontaasianKuvaus app-info)
              poikkeamat (:vahainenPoikkeaminen app-info)
              manual-schema-datas {"hankkeen-kuvaus" (filter seq
                                                       (conj []
                                                         (when-not (ss/blank? asian-kuvaus) [["kuvaus"] asian-kuvaus])
                                                         (when-not (ss/blank? poikkeamat)   [["poikkeamat"] poikkeamat])))}]
          (if-not (empty? app-info)

             (if (= (:id organization) (:id (organization/resolve-organization (:municipality app-info) permit-type)))

              (if (or rakennuspaikka-exists enough-info-from-parameters)

                (if (ss/blank? lupapiste-tunnus)

                  ;; create the application
                  (let [info-source (cond
                                      rakennuspaikka-exists            (:rakennuspaikka app-info)
                                      enough-info-from-parameters      {:x x :y y :address address :propertyId propertyId}
;                                      (:ensimmainen-rakennus app-info) (:ensimmainen-rakennus app-info)     ;; Tatako ei tarvita?
                                      )
                        command (update-in command [:data] merge {:infoRequest false :messages []} info-source)
                        created-application (do-create-application command manual-schema-datas)
                        ;;
                        ;; *** TODO: Aseta applicationille viitelupatiedot -> kts. app-infon :viitelupatiedot ***
                        ;;

                        ;;
                        ;; TODO: Aseta applicationille viimeisin state?
                        ;;
                        ;; lupapalvelu.document.canonical-common/application-state-to-krysp-state kaanteisesti
;                        (assoc created-application
;                          :state (some #(when (= (-> app-info :viimeisin-tila :tila) (val %)) (first %)) lupapalvelu.document.canonical-common/application-state-to-krysp-state))

                        ;; The application has to be inserted first, because it is assumed to be in the database when checking for verdicts (and their attachments).
                        _ (insert-application created-application)

                        ;; Update the hakija document
                        _ (if-let [document (domain/get-document-by-name created-application "hakija")]
                            (set-user-to-document created-application document (:id user) "henkilo" user created)
                            (fail! :error.document-not-found))

                        ;; attaches the new application, and its id to path [:data :id], into the command
                        command (merge command (application->command created-application))
                        ;; Get verdicts for the application
                        _ (verdict-api/do-check-for-verdict command xml)]

                    (ok :id (:id created-application)))


                  ;; Jos ks. kuntalupatunnuksella on jo Lupapisteessa lupa, ja ks. henkilolla on sille oikeudet, avaa suoraan tama lupa.
                  ;; Jos henkilolla ei ole oikeuksia talle luvalle, nayta virheilmoitus.
                  (if-let [existing-application (mongo/by-id :applications lupapiste-tunnus)]
                    (if (domain/owner-or-writer? existing-application (:id user))
                      (ok :id lupapiste-tunnus)
                      (fail :error.lupapiste-application-already-exists-but-unauthorized-to-access-it :id lupapiste-tunnus))
                    ;; The xml message included lupapiste-id, but an application with that id is not found from database. This should never be the case.
                    (do
                      (error "Creating application from previous permit. Not able to find application id '" lupapiste-tunnus "' it includes from database.")
                      (fail :error.not-able-to-open-with-lupapiste-id-that-previous-permit-included :id lupapiste-tunnus))))

                ;; If we did not get the "rakennuspaikkatieto" element in the verdict xml message,
                ;; let's ask more needed info from user
                (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true))

              (fail :error.previous-permit-found-from-backend-is-of-different-organization))

            ;; Sanomasta ei saatu purettua tietoa, esimerkiksi sanomassa ei kuitenkaan ollut asiatietoa annetulla kuntalupatunnuksella.
            (fail :error.no-previous-permit-found-from-backend)))


        ;; Annetulle kuntalupatunnukselle ei loytynyt sanomaa.
        (fail :error.no-previous-permit-found-from-backend)))))

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

(defcommand update-op-description
  {:parameters [id op-id desc]
   :roles      [:applicant :authority]
   :states     [:draft :open :submitted :complement-needed]}
  [command]
  (update-application command {"operations" {$elemMatch {:id op-id}}} {$set {"operations.$.description" desc}}))

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
  (let [application (meta-fields/enrich-with-link-permit-data application)
        ;; exclude from results the current application itself, and the applications that have a link-permit relation to it
        ignore-ids (-> application
                     (#(concat (:linkPermitData %) (:appsLinkingToUs %)))
                     (#(map :id %))
                     (conj id))
        results (mongo/select :applications
                  (merge (domain/application-query-for user) {:_id {$nin ignore-ids}
                                                              :infoRequest false
                                                              :permitType (:permitType application)
                                                              :operations.name {$nin ["ya-jatkoaika"]}})
                  [:permitType :address :propertyId])
        ;; add the text to show in the dropdown for selections
        enriched-results (map
                           (fn [r] (assoc r :text (str (:address r) ", " (:id r))))
                           results)
        ;; sort the results
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
       id             {:type "application"
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
            (pos? (-> application :linkPermitData count)))
      (fail :error.jatkolupa-can-only-be-added-one-link-permit))))

(defn- validate-link-permit-id [{:keys [data]} application]
  (let [application (meta-fields/enrich-with-link-permit-data application)
        ignore-ids (-> application
                     (#(concat (:linkPermitData %) (:appsLinkingToUs %)))
                     (#(map :id %))
                     (conj (:id application)))]
    (when (some
            #( = (:id %) (:linkPermitId data))
            (:appsLinkingToUs application))
      (fail :error.link-permit-already-having-us-as-link-permit))))

(defcommand add-link-permit
  {:parameters ["id" linkPermitId]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:sent :closed :canceled]);; Pitaako olla myos 'sent'-tila?
   :pre-checks [validate-jatkolupa-zero-link-permits
                validate-link-permit-id]
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
        (-> app (domain/get-document-by-name "tyoaika") :data :tyoaika-alkaa-pvm :value)
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
  (let [scope (organization/resolve-organization-scope municipality permitType)]
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
             :openInfoRequest false
             :state :open
             :opened created
             :convertedToApplication created
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

(defn- load-building-data [url property-id building-id overwrite-all?]
  (let [all-data (krysp-reader/->rakennuksen-tiedot (krysp-reader/building-xml url property-id) building-id)]
    (if overwrite-all?
      all-data
      (select-keys all-data (keys krysp-reader/empty-building-ids)))))

(defcommand merge-details-from-krysp
  {:parameters [id documentId path buildingId overwrite collection]
   :input-validators [commands/validate-collection
                      (partial action/non-blank-parameters [:documentId :path])
                      (partial action/boolean-parameters [:overwrite])]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:sent :verdictGiven :constructionStarted :closed :canceled])}
  [{created :created {:keys [organization propertyId] :as application} :application :as command}]
  (if-let [{url :url} (organization/get-krysp-wfs application)]
    (let [document     (commands/by-id application collection documentId)
          schema       (schemas/get-schema (:schema-info document))
          clear-ids?   (or (ss/blank? buildingId) (= "other" buildingId))
          base-updates (concat
                         (commands/->model-updates [[path buildingId]])
                         (tools/path-vals
                           (if clear-ids?
                             krysp-reader/empty-building-ids
                             (load-building-data url propertyId buildingId overwrite))))
          ; Path should exist in schema!
          updates      (filter (fn [[path _]] (model/find-by-name (:body schema) path)) base-updates)]
      (infof "merging data into %s %s" (get-in document [:schema-info :name]) (:id document))
      (commands/persist-model-updates application collection document updates created :source "krysp")
      (ok))
    (fail :error.no-legacy-available)))

(defcommand get-building-info-from-wfs
  {:parameters [id]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:sent :verdictGiven :constructionStarted :closed :canceled])}
  [{{:keys [organization propertyId] :as application} :application}]
  (if-let [{url :url} (organization/get-krysp-wfs application)]
    (let [kryspxml  (krysp-reader/building-xml url propertyId)
          buildings (krysp-reader/->buildings-summary kryspxml)]
      (ok :data buildings))
    (fail :error.no-legacy-available)))
