(ns lupapalvelu.application
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [clojure.string :refer [blank? join trim split]]
            [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [clj-time.format :as tf]
            [sade.http :as http]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.xml :as xml]
            [lupapalvelu.core :refer [ok fail fail! now]]
            [lupapalvelu.action :refer [defquery defcommand executed update-application non-blank-parameters without-system-keys notify]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.i18n :as i18n]
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
            [lupapalvelu.ktj :as ktj]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.application-meta-fields :as meta-fields])
  (:import [java.net URL]))

;; Validators

(defn not-open-inforequest-user-validator [{user :user} _]
  (when (:oir user)
    (fail :error.not-allowed-for-oir)))

(defn- property-id? [^String s]
  (and s (re-matches #"^[0-9]{14}$" s)))

(defn property-id-parameters [params command]
  (when-let [invalid (seq (filter #(not (property-id? (get-in command [:data %]))) params))]
    (info "invalid property id parameters:" (join ", " invalid))
    (fail :error.invalid-property-id :parameters (vec invalid))))

(defn- validate-owner-or-writer
  "Validator: current user must be owner or writer.
   To be used in commands' :validators vector."
  [command application]
  (when-not (domain/owner-or-writer? application (-> command :user :id))
    (fail :error.unauthorized)))

(defn- validate-x [{{:keys [x]} :data}]
  (when (and x (not (< 10000 (util/->double x) 800000)))
    (fail :error.illegal-coordinates)))

(defn- validate-y [{{:keys [y]} :data}]
  (when (and y (not (<= 6610000 (util/->double y) 7779999)))
    (fail :error.illegal-coordinates)))

;; Helpers

(defn- set-user-to-document [application-id document user-id path current-user timestamp]
  {:pre [document]}
  (let [schema       (schemas/get-schema (:schema-info document))
         subject      (user/get-user-by-id user-id)
         with-hetu    (and
                        (domain/has-hetu? (:body schema) [path])
                        (user/same-user? current-user subject))
         person       (tools/unwrapped (domain/->henkilo subject :with-hetu with-hetu))
         model        (if-not (blank? path)
                        (assoc-in {} (map keyword (split path #"\.")) person)
                        person)
         updates      (tools/path-vals model)
         ; Path should exist in schema!
         updates      (filter (fn [[path _]] (model/find-by-name (:body schema) path)) updates)]
     (when-not schema (fail! :error.schema-not-found))
     (debugf "merging user %s with best effort into %s %s" model (get-in document [:schema-info :name]) (:id document))
     (commands/persist-model-updates application-id "documents" document updates timestamp)) ; TODO support for collection parameter
  )

;;
;; Query application:
;;

(defn- app-post-processor [user]
  (comp
    without-system-keys
    (partial meta-fields/with-meta-fields user)
    meta-fields/enrich-with-link-permit-data))

(defn find-authorities-in-applications-organization [app]
  (mongo/select :users
    {:organizations (:organization app) :role "authority" :enabled true}
    {:firstName 1 :lastName 1}))

(defquery application
  {:authenticated true
   :parameters [:id]}
  [{app :application user :user}]
  (if app
    (ok :application ((app-post-processor user) app)
        :authorities (find-authorities-in-applications-organization app)
        :permitSubtypes (permit/permit-subtypes (:permitType app)))
    (fail :error.not-found)))

;; Gets an array of application ids and returns a map for each application that contains the
;; application id and the authorities in that organization.
(defquery authorities-in-applications-organization
  {:parameters [:id]
   :authenticated true}
  [{app :application}]
  (ok :authorityInfo (find-authorities-in-applications-organization app)))

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
   (when (and (= "R" (:permitType application)) (not (:infoRequest application)))
     (when-let [rakennuspaikka (domain/get-document-by-name application "rakennuspaikka")]
       (when-let [ktj-tiedot (ktj/rekisteritiedot-xml (:propertyId application))]
         (let [updates [[[:kiinteisto :tilanNimi]        (or (:nimi ktj-tiedot) "")]
                        [[:kiinteisto :maapintaala]      (or (:maapintaala ktj-tiedot) "")]
                        [[:kiinteisto :vesipintaala]     (or (:vesipintaala ktj-tiedot) "")]
                        [[:kiinteisto :rekisterointipvm] (or (try
                                                               (tf/unparse output-format (tf/parse ktj-format (:rekisterointipvm ktj-tiedot)))
                                                               (catch Exception e (:rekisterointipvm ktj-tiedot))) "")]]]
           (commands/persist-model-updates
             (:id application)
             "documents"
             rakennuspaikka
             updates
             time))))))

(defquery party-document-names
  {:parameters [:id]
   :authenticated true}
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
  {:authenticated true
   :verified true}
  [{{:keys [id]} :user}]
  (let [filter     {:auth {$elemMatch {:invite.user.id id}}}
        projection (assoc filter :_id 0)
        data       (mongo/select :applications filter projection)
        invites    (map :invite (mapcat :auth data))]
    (ok :invites invites)))

(defcommand invite
  {:parameters [id email title text documentName documentId path]
   :input-validators [(partial non-blank-parameters [:email :documentName :documentId])]
   :roles      [:applicant :authority]
   :notified   true
   :on-success (notify :invite)
   :verified   true}
  [{:keys [created user application] :as command}]
  (let [email (ss/lower-case email)]
    (if (domain/invited? application email)
      (fail :invite.already-invited)
      (let [invited (user-api/get-or-create-user-by-email email)
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
            {$push {:auth auth}}))))))

(defcommand approve-invite
  {:parameters [id]
   :roles      [:applicant]
   :verified   true}
  [{user :user application :application created :created :as command}]
  (when-let [my-invite (domain/invite application (:email user))]
    (update-application command
      {:auth {$elemMatch {:invite.user.id (:id user)}}}
      {$set  {:auth.$ (user/user-in-role user :writer)}})
    (when-let [document (domain/get-document-by-id application (:documentId my-invite))]
      ; It's not possible to combine Mongo writes here,
      ; because only the last $elemMatch counts.
      (set-user-to-document id document (:id user) (:path my-invite) user created))))

(defcommand remove-invite
  {:parameters [id email]
   :roles      [:applicant :authority]
   :validators [validate-owner-or-writer]}
  [command]
  (let [email (ss/lower-case email)]
    (with-user-by-email email
      (update-application command
        {$pull {:auth {$and [{:username email}
                             {:type {$ne :owner}}]}}}))))

(defcommand remove-auth
  {:parameters [:id email]
   :roles      [:applicant :authority]}
  [command]
  (update-application command
    {$pull {:auth {$and [{:username (ss/lower-case email)}
                         {:type {$ne :owner}}]}}}))

(defn applicant-cant-set-to [{{:keys [to]} :data user :user} _]
  (when (and to (not (user/authority? user)))
    (fail :error.to-settable-only-by-authority)))

(defcommand can-target-comment-to-authority
  {:roles [:authority]
   :validators  [not-open-inforequest-user-validator]
   :description "Dummy command for UI logic"})

(defcommand add-comment
  {:parameters [id text target]
   :roles      [:applicant :authority]
   :validators [applicant-cant-set-to]
   :notified   true
   :on-success [(notify :new-comment)
                (fn [{data :data :as command} _]
                  (when-let [to-user (and (:to data) (user/get-user-by-id (:to data)))]
                    ;; LUPA-407
                    (notifications/notify! :application-targeted-comment (assoc command :user to-user))))
                open-inforequest/notify-on-comment]}
  [{{:keys [to mark-answered] :or {mark-answered true}} :data :keys [user created application] :as command}]
  (let [to-user   (and to (or (user/get-user-by-id to) (fail! :to-is-not-id-of-any-user-in-system)))]
    (update-application command
      (util/deep-merge
        {$set  {:modified created}
         $push {:comments {:text    text
                           :target  target
                           :created created
                           :to      (user/summary to-user)
                           :user    (user/summary user)}}}

        (case (keyword (:state application))
          ;; LUPA-XYZ (was: open-application)
          :draft  (when-not (blank? text) {$set {:state :open, :opened created}})

          ;; LUPA-371, LUPA-745
          :info (when (and mark-answered (user/authority? user)) {$set {:state :answered}})

          ;; LUPA-371 (was: mark-inforequest-answered)
          :answered (when (user/applicant? user) {$set {:state :info}})

          nil)))))

(defcommand mark-seen
  {:parameters [:id :type]
   :input-validators [(fn [{{type :type} :data}] (when-not (#{"comments" "statements" "verdicts"} type) (fail :error.unknown-type)))]
   :authenticated true}
  [{:keys [data user created] :as command}]
  (update-application command {$set {(str "_" (:type data) "-seen-by." (:id user)) created}}))

(defcommand set-user-to-document
  {:parameters [id documentId userId path]
   :authenticated true}
  [{:keys [user created application] :as command}]
  (if-let [document (domain/get-document-by-id application documentId)]
    (set-user-to-document id document userId path user created)
    (fail :error.document-not-found)))

;;
;; Assign
;;

(defcommand assign-to-me
  {:parameters [:id]
   :roles      [:authority]}
  [{user :user :as command}]
  (update-application command
    {$set {:authority (user/summary user)}}))

(defcommand assign-application
  {:parameters  [:id assigneeId]
   :validators  [not-open-inforequest-user-validator]
   :roles       [:authority]}
  [{user :user :as command}]
  (let [assignee (mongo/select-one :users {:_id assigneeId :enabled true})]
    (if (or assignee (nil? assigneeId))
      (update-application command
                          (if assignee
                            {$set   {:authority (user/summary assignee)}}
                            {$unset {:authority ""}}))
      (fail "error.user.not.found" :id assigneeId))))

(defcommand cancel-application
  {:parameters [id]
   :roles      [:applicant]
   :notified   true
   :on-success (notify :application-state-change)
   :states     [:draft :info :open :submitted]}
  [{:keys [created] :as command}]
  (update-application command
    {$set {:modified  created
           :state     :canceled}})
  (mongo/remove-many :app-links {:link {$in [id]}})
  (ok))

(defcommand request-for-complement
  {:parameters [:id]
   :roles      [:authority]
   :notified   true
   :on-success (notify :application-state-change)
   :states     [:sent]}
  [{:keys [created] :as command}]
  (update-application command
    {$set {:modified  created
           :state :complement-needed}}))

(defn- validate-jatkolupa-one-link-permit [_ application]
  (let [application (meta-fields/enrich-with-link-permit-data application)]
    (when (and (= :ya-jatkoaika (-> application :operations first :name keyword))
            (not= 1 (-> application :linkPermitData count)))
      (fail! :error.jatkolupa-must-have-exactly-one-link-permit))))

(defcommand approve-application
  {:parameters [id lang]
   :roles      [:authority]
   :notified   true
   :on-success (notify :application-state-change)
   :validators [validate-jatkolupa-one-link-permit]
   :states     [:submitted :complement-needed]}
  [{:keys [application created] :as command}]

  (println "\n approve-application, app: ")
  (clojure.pprint/pprint application)
  (println "\n")

  (let [application (meta-fields/enrich-with-link-permit-data application)
        ;; Jatkoaika app will need to have some more data from its link permit to enable its canonical transformation.
;        continuation-period-permit? ;(or
;                                      (= :ya-jatkoaika (-> application :operations first :name keyword))
;                                     ; (= "jatkolupa" (:permitSubtype application)))
;        _ (println "\n continuation-period-permit?: " continuation-period-permit?)
;        _ (println "\n app linkPermitData: " (:linkPermitData application))
;        application-for-krysp (if continuation-period-permit?
;                                #_(meta-fields/enrich-with-additional-data-from-link-permit application)
;                                (meta-fields/merge-continuation-period-permit-with-orig-application application)
;                                application)
;        submitted-application (mongo/by-id :submitted-applications (if continuation-period-permit?
;                                                                     (-> application :linkPermitData first :id)
;                                                                     id))
;        _ (println "\n app id: " (:id application-for-krysp) ", subm app id: " (if continuation-period-permit?
;                                                                                 (-> application :linkPermitData first :id)
;                                                                                 id) "\n")
;        organization (organization/get-organization (:organization application-for-krysp))
        organization (organization/get-organization (:organization application))
        ]

;    (println "\n app authority: " (:authority application))
    (when (empty? (:authority application))
      (executed "assign-to-me" command)) ;; FIXME combine mongo writes
    (try
      (if (= :ya-jatkoaika (-> application :operations first :name keyword))
        (mapping-to-krysp/save-jatkoaika-as-krysp application lang organization)
        (let [submitted-application (mongo/by-id :submitted-applications id)]
          (mapping-to-krysp/save-application-as-krysp application lang submitted-application organization)))

      (catch org.xml.sax.SAXParseException e
        (info e "Invalid KRYSP XML message")
        (fail (.getMessage e))))


    (println "\n approve-application, updating application \n")
    (update-application command
      {:state {$in ["submitted" "complement-needed"]}}
      {$set {:sent created
             :state :sent}})
    ))

(defcommand submit-application
  {:parameters [id]
   :roles      [:applicant :authority]
   :states     [:draft :info :open :complement-needed]
   :notified   true
   :on-success (notify :application-state-change)
   :validators [validate-owner-or-writer]}
  [{:keys [application created] :as command}]
  (update-application command
    {$set {:state     :submitted
           :opened    (or (:opened application) created)
           :submitted created}})

  (try
    (mongo/insert :submitted-applications
      (-> (meta-fields/enrich-with-link-permit-data application) (dissoc :id) (assoc :_id id)))
    (catch com.mongodb.MongoException$DuplicateKey e
      ; This is ok. Only the first submit is saved.
      )))

(defcommand refresh-ktj
  {:parameters [:id]
   :roles      [:authority]
   :states     [:draft :open :submitted :complement-needed]
   :validators [validate-owner-or-writer]}
  [{:keys [application]}]
  (try (autofill-rakennuspaikka application (now))
    (catch Exception e (error e "KTJ data was not updated"))))

(defcommand save-application-shape
  {:parameters [:id shape]
   :roles      [:applicant :authority]
   :states     [:draft :open :submitted :complement-needed :info]}
  [command]
  (update-application command
    {$set {:shapes [shape]}}))

(defcommand save-application-drawings
  {:parameters [:id drawings]
   :roles      [:applicant :authority]
   :states     [:draft :open :submitted :complement-needed :info]}
  [command]
  (update-application command
    {$set {:drawings drawings}}))

(defn make-attachments [created operation organization-id & {:keys [target]}]
  (let [organization (organization/get-organization organization-id)]
    (for [[type-group type-id] (organization/get-organization-attachments-for-operation organization operation)]
      (attachment/make-attachment created target false operation {:type-group type-group :type-id type-id}))))

(defn- schema-data-to-body [schema-data application]
  (reduce
    (fn [body [data-path data-value]]
      (let [path (if (= :value (last data-path)) data-path (conj (vec data-path) :value))
            val (if (fn? data-value) (data-value application) data-value)]
        ; FIXME: why not assoc-in?
        (update-in body path (constantly val))))
    {} schema-data))

;; TODO: permit-type splitting.
(defn- make-documents [user created op application]
  (let [op-info               (operations/operations (keyword (:name op)))
        existing-documents    (:documents application)
        permit-type           (keyword (permit/permit-type application))
        schema-version        (:schema-version application)
        make                  (fn [schema-name] {:id (mongo/create-id)
                                                 :schema-info (:info (schemas/get-schema schema-version schema-name))
                                                 :created created
                                                 :data (tools/timestamped
                                                         (if (= schema-name (:schema op-info))
                                                           (schema-data-to-body (:schema-data op-info) application)
                                                           {})
                                                         created)})
        existing-schema-names (set (map (comp :name :schema-info) existing-documents))
        required-schema-names (remove existing-schema-names (:required op-info))
        required-docs         (map make required-schema-names)
        op-schema-name        (:schema op-info)
        ;;The merge below: If :removable is set manually in schema's info, do not override it to true.
        op-doc                (update-in (make op-schema-name) [:schema-info] #(merge {:op op :removable true} %))
        new-docs              (cons op-doc required-docs)]
    (if-not user
      new-docs
      (let [hakija (condp = permit-type
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


(defn- do-create-application
  [{{:keys [operation x y address propertyId municipality infoRequest messages]} :data :keys [user created] :as command}]
  (let [permit-type       (operations/permit-type-of-operation operation)
        organization      (organization/resolve-organization municipality permit-type)
        organization-id   (:id organization)
        info-request?     (boolean infoRequest)
        open-inforequest? (and info-request? (:open-inforequest organization))]

    (when-not (or (user/applicant? user) (user-is-authority-in-organization? (:id user) organization-id))
      (fail! :error.unauthorized))
    (when-not organization-id
      (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))
    (if info-request?
      (when-not (:inforequest-enabled organization)
        (fail! :error.inforequests-disabled))
      (when-not (:new-application-enabled organization)
        (fail! :error.new-applications-disabled)))

    (let [id            (make-application-id municipality)
          owner         (user/user-in-role user :owner :type :owner)
          op            (make-op operation created)
          info-request? (boolean infoRequest)
          state         (cond
                          info-request?              :info
                          (user/authority? user)     :open
                          :else                      :draft)
          make-comment  (partial assoc {:target {:type "application"}
                                        :created created
                                        :user (user/summary user)} :text)

          application   (merge (domain/application-skeleton)
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
                           :organization        organization-id
                           :address             address
                           :propertyId          propertyId
                           :title               address
                           :auth                [owner]
                           :comments            (map make-comment messages)
                           :schema-version      (schemas/get-latest-schema-version)})

          application   (merge application
                          (if info-request?
                            {:allowedAttachmentTypes [[:muut [:muu]]]}
                            {:attachments            (make-attachments created op organization-id)
                             :allowedAttachmentTypes (attachment/get-attachment-types-by-permit-type permit-type)
                             :documents              (make-documents user created op application)}))

          application   (domain/set-software-version application)]

      (println "\n do-create-application, created application: ")
      (clojure.pprint/pprint application)
      (println "\n")

      application)))

;; TODO: separate methods for inforequests & applications for clarity.
(defcommand create-application
  {:parameters [:operation :x :y :address :propertyId :municipality]
   :roles      [:applicant :authority]
   :notified   true ; OIR
   :input-validators [(partial non-blank-parameters [:operation :address :municipality])
                      (partial property-id-parameters [:propertyId])
                      operation-validator]}
  [{{:keys [operation x y address propertyId municipality infoRequest messages]} :data :keys [user created] :as command}]
  (let [
        permit-type       (operations/permit-type-of-operation operation)
        organization      (organization/resolve-organization municipality permit-type)
;        organization-id   (:id organization)
        info-request?     (boolean infoRequest)
        open-inforequest? (and info-request? (:open-inforequest organization))
        created-application (do-create-application command)]

;    (when-not (or (user/applicant? user) (user-is-authority-in-organization? (:id user) organization-id))
;      (fail! :error.unauthorized))
;    (when-not organization-id
;      (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))
;    (if info-request?
;      (when-not (:inforequest-enabled organization)
;        (fail! :error.inforequests-disabled))
;      (when-not (:new-application-enabled organization)
;        (fail! :error.new-applications-disabled)))

;    (let [id            (make-application-id municipality)
;          owner         (user/user-in-role user :owner :type :owner)
;          op            (make-op operation created)
;          state         (cond
;                          info-request?              :info
;                          (user/authority? user)     :open
;                          :else                      :draft)
;          make-comment  (partial assoc {:target {:type "application"}
;                                        :created created
;                                        :user (user/summary user)} :text)
;
;          application   (merge (domain/application-skeleton)
;                          {:id                  id
;                           :created             created
;                           :opened              (when (#{:open :info} state) created)
;                           :modified            created
;                           :permitType          permit-type
;                           :permitSubtype       (first (permit/permit-subtypes permit-type))
;                           :infoRequest         info-request?
;                           :openInfoRequest     open-inforequest?
;                           :operations          [op]
;                           :state               state
;                           :municipality        municipality
;                           :location            (->location x y)
;                           :organization        organization-id
;                           :address             address
;                           :propertyId          propertyId
;                           :title               address
;                           :auth                [owner]
;                           :comments            (map make-comment messages)
;                           :schema-version      (schemas/get-latest-schema-version)})
;
;          application   (merge application
;                          (if info-request?
;                            {:allowedAttachmentTypes [[:muut [:muu]]]}
;                            {:attachments            (make-attachments created op organization-id)
;                             :allowedAttachmentTypes (attachment/get-attachment-types-by-permit-type permit-type)
;                             :documents              (make-documents user created op application)}))
;
;          application   (domain/set-software-version application)]

      (mongo/insert :applications created-application)
      (when open-inforequest?
        (open-inforequest/new-open-inforequest! created-application))
      (try
        (autofill-rakennuspaikka created-application created)
        (catch Exception e (error e "KTJ data was not updated")))
      (ok :id (:id created-application))
;      )
      ))

(defcommand add-operation
  {:parameters [id operation]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed :submitted]
   :input-validators [operation-validator]
   :validators [(permit/validate-permit-type-is permit/R)]}
  [{:keys [application created] :as command}]
  (let [op-id      (mongo/create-id)
        op         (make-op operation created)
        new-docs   (make-documents nil created op application)]
    (update-application command {$push {:operations op}
                                 $pushAll {:documents new-docs
                                           :attachments (make-attachments created op (:organization application))}
                                 $set {:modified created}})))

(defcommand change-permit-sub-type
  {:parameters [id permitSubtype]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed :submitted]
   :validators [permit/validate-permit-has-subtypes]}
  [{:keys [application created] :as command}]
  (if-let [validation-errors (permit/is-valid-subtype (keyword permitSubtype) application)]
    validation-errors
    (update-application command
      {$set {:permitSubtype permitSubtype
             :modified      created}})))

(defcommand change-location
  {:parameters [id x y address propertyId]
   :roles      [:applicant :authority]
   :states     [:draft :info :answered :open :complement-needed :submitted]
   :input-validators [(partial non-blank-parameters [:address])
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

(defquery app-matches-for-link-permits
  {:parameters [id]
   :verified   true
   :roles      [:applicant :authority]}
  [{{:keys [propertyId]} :application user :user :as command}]
  (let [results (mongo/select :applications
                  (merge (domain/application-query-for user) {:_id {$ne id}
                                                              :state {$in ["verdictGiven" "constructionStarted"]}})
                  {:_id 1 :permitType 1 :address 1 :propertyId 1})
        enriched-results (map
                           (fn [r]
                             (assoc r :text
                               (str
                                 (:address r) ", "
                                 (i18n/with-lang (:lang command) (i18n/loc (:permitType r))) ", "
                                 (:id r))))
                           results)
        same-property-id-fn #(= propertyId (:propertyId %))
        with-same-property-id (into [] (filter same-property-id-fn enriched-results))
        without-same-property-id (sort-by :text #(compare %1 %2)
                                   (into [] (filter (comp not same-property-id-fn) enriched-results)))
        organized-results (flatten (conj with-same-property-id without-same-property-id))
        final-results (map #(select-keys % [:id :text]) organized-results)]
    (ok :app-links final-results)))

(defn- make-mongo-id-for-link-permit [app-id link-permit-id]
  (if (<= (compare app-id link-permit-id) 0)
    (str app-id "|" link-permit-id)
    (str link-permit-id "|" app-id)))

(defn- do-add-link-permit [application linkPermitId]
  (let [id (:id application)
        db-id (make-mongo-id-for-link-permit id linkPermitId)]
    (mongo/update-by-id :app-links db-id
      {:_id db-id
       :link [id linkPermitId]
       (keyword id) {:type "application"
                     :apptype (-> application :operations first :name)
                     :propertyId (:propertyId application)}
       (keyword linkPermitId) {:type "linkpermit"
                               :linkpermittype (if (>= (.indexOf linkPermitId "LP-") 0)
                                                 "lupapistetunnus"
                                                 "kuntalupatunnus")}}
      :upsert true)))

(defn- validate-jatkolupa-zero-link-permits [_ application]
  (let [application (meta-fields/enrich-with-link-permit-data application)]
    (when (and (= :ya-jatkoaika (-> application :operations first :name keyword))
            (not= 0 (-> application :linkPermitData count)))
      (fail! :error.jatkolupa-can-only-be-added-one-link-permit))))

(defcommand add-link-permit
  {:parameters ["id" linkPermitId]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed :submitted]
   :validators [validate-jatkolupa-zero-link-permits]
   :input-validators [(partial non-blank-parameters [:linkPermitId])]}
  [{application :application}]
  (do-add-link-permit application linkPermitId))

(defcommand remove-link-permit-by-app-id
  {:parameters [id linkPermitId]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed :submitted]}
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
   :states     [:verdictGiven :constructionsStarted]
   :validators [(permit/validate-permit-type-is permit/R)]}
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
                           (domain/application-skeleton)
                           [:attachments :statements :verdicts :comments :submitted :sent :neighbors
                            :_statements-seen-by :_comments-seen-by :_verdicts-seen-by]))]
    (do-add-link-permit muutoslupa-app (:id application))
    (mongo/insert :applications muutoslupa-app)
    (ok :id muutoslupa-app-id)))


;;
;; Continuation period permit
;;

(defn- get-tyoaika-alkaa-from-ya-app [app]
  (let [mainostus-viitoitus-tapahtuma-doc (domain/get-document-by-name app "mainosten-tai-viitoitusten-sijoittaminen")
        _ (println "\n doc: " mainostus-viitoitus-tapahtuma-doc)
        mainostus-viitoitus-tapahtuma-name (when mainostus-viitoitus-tapahtuma-doc
                                             (-> mainostus-viitoitus-tapahtuma-doc :_selected :value))
        _ (println "\n name: " mainostus-viitoitus-tapahtuma-name)
        mainostus-viitoitus-tapahtuma (when mainostus-viitoitus-tapahtuma-name
                                        (mainostus-viitoitus-tapahtuma-doc (keyword mainostus-viitoitus-tapahtuma-name)))
        _ (println "\n tapahtuma: " mainostus-viitoitus-tapahtuma)]
    (or
      (-> (domain/get-document-by-name app "tyoaika") :data :tyoaika-alkaa-pvm :value)
      (-> mainostus-viitoitus-tapahtuma :tapahtuma-aika-paattyy-pvm :value)
      (util/to-local-date (:submitted app)))))

(defn- validate-not-jatkolupa-app [_ application]
  (println "\n validate-not-jatkolupa-app, app op: " (-> application :operations first :name keyword) "\n")
  (when (= :ya-jatkoaika (-> application :operations first :name keyword))
    (fail! :error.cannot-apply-jatkolupa-for-jatkolupa)))

;;
;; TODO: jatkoluvan viitelupa ei ole jatkolupa-tyyppiä -> lisaa rekursio, joka etsii oikean luvan lupaketjusta
;;
(defcommand create-continuation-period-permit
  {:parameters ["id"]
   :roles      [:applicant :authority]
   :states     [:verdictGiven :constructionsStarted]
   :validators [(permit/validate-permit-type-is permit/YA) validate-not-jatkolupa-app]}
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

        continuation-app (merge continuation-app
                           {;:permitSubtype :jatkolupa
                            :authority (:authority application)})

        ;; ************
        ;; Lain mukaan hankeen aloituspvm on hakupvm + 21pv, tai kunnan päätöspvm jos se on tata aiempi.
        ;; kts.  http://www.finlex.fi/fi/laki/alkup/2005/20050547 ,  14 a §
        ;; ************

        tyoaika-alkaa-pvm (get-tyoaika-alkaa-from-ya-app application)
        _ (println "\n resolved tyoaika-alkaa-pvm: " tyoaika-alkaa-pvm)

        tyo-aika-for-jatkoaika-doc (domain/get-document-by-name continuation-app "tyo-aika-for-jatkoaika")
        tyo-aika-for-jatkoaika-doc (assoc-in tyo-aika-for-jatkoaika-doc [:data :tyoaika-alkaa-pvm :value] tyoaika-alkaa-pvm)

        continuation-app (assoc continuation-app
                           :documents [(domain/get-document-by-name continuation-app "hankkeen-kuvaus-minimum")
                                       tyo-aika-for-jatkoaika-doc
                                       (domain/get-document-by-name application "hakija-ya")
                                       (domain/get-document-by-name application "yleiset-alueet-maksaja")])
        ]

    (println "\n create-continuation-period-permit, continuation-app: ")
    (clojure.pprint/pprint continuation-app)
    (println "\n")


    (do-add-link-permit continuation-app (:id application))
    (mongo/insert :applications continuation-app)
    (ok :id (:id continuation-app))
    )

  #_(let [continuation-app-id (make-application-id (:municipality application))
        continuation-app (merge application
                         {:id                  continuation-app-id
                          :created             created
                          :opened              created
                          :modified            created
                          :documents           (into [] (map
                                                          #(assoc % :id (mongo/create-id))
                                                          (:documents application)))
                          :state               (cond
                                                 (user/authority? user)  :open
                                                 :else                   :draft)
                          :permitSubtype       :jatkolupa}
                         (select-keys
                           (domain/application-skeleton)
                           [:attachments :statements :verdicts :comments :submitted :sent :neighbors
                            :_statements-seen-by :_comments-seen-by :_verdicts-seen-by]))]
    (do-add-link-permit continuation-app (:id application))
    (mongo/insert :applications continuation-app)
    (ok :id continuation-app-id)))


;;
;; Inform building ready
;;

(defcommand inform-building-ready
  {:parameters ["id" readyTimestampStr lang]
   :roles      [:applicant :authority]
   :states     [:verdictGiven :constructionsStarted]      ;;TODO: Tahan vain :constructionsStarted?
   :on-success (notify :application-state-change)
   :validators [(permit/validate-permit-type-is permit/YA)]
   :input-validators [(partial non-blank-parameters [:readyTimestampStr])]}
  [{:keys [created application] :as command}]
  (let [timestamp (util/to-xml-millis-from-string readyTimestampStr)
        application (assoc application :closed timestamp)
        organization (organization/get-organization (:organization application))]
    (mapping-to-krysp/save-application-as-krysp
      application
      lang
      application
      organization)
    (update-application command {$set {:closed timestamp
                                       :state :closed}})
    (ok)))


(defn- validate-new-applications-enabled [command {:keys [organization]}]
  (let [org (mongo/by-id :organizations organization {:new-application-enabled 1})]
    (if (= (:new-application-enabled org) true)
      nil
      (fail :error.new-applications.disabled))))

(defcommand convert-to-application
  {:parameters [id]
   :roles      [:applicant]
   :states     [:draft :info :answered]
   :validators [validate-new-applications-enabled]}
  [{:keys [user created application] :as command}]
  (let [op          (first (:operations application))
        permit-type (permit/permit-type application)]
    (update-application command
      {$set {:infoRequest false
             :state :open
             :allowedAttachmentTypes (attachment/get-attachment-types-by-permit-type permit-type)
             :documents (make-documents user created op application)
             :modified created}
       $pushAll {:attachments (make-attachments created op (:organization application))}})
    (try (autofill-rakennuspaikka application (now))
      (catch Exception e (error e "KTJ data was not updated")))))

;;
;; Verdicts
;;

(defn- validate-status [{{:keys [status]} :data}]
  (when (or (< status 1) (> status 42))
    (fail :error.false.status.out.of.range.when.giving.verdict)))

(defcommand give-verdict
  {:parameters [id verdictId status name given official]
   :input-validators [validate-status]
   :states     [:submitted :complement-needed :sent]
   :notified   true
   :on-success (notify :application-verdict)
   :roles      [:authority]}
  [{:keys [created] :as command}]
  (update-application command
    {$set {:modified created
           :state    :verdictGiven}
     $push {:verdicts (domain/->paatos
                        {:id verdictId      ; Kuntalupatunnus
                         :timestamp created ; tekninen Lupapisteen aikaleima
                         :name name         ; poytakirja[] / paatoksentekija
                         :given given       ; paivamaarat / antoPvm
                         :status status     ; poytakirja[] / paatoskoodi
                         :official official ; paivamaarat / lainvoimainenPvm
                         })}}))

(defn verdict-attachments [id user timestamp verdict]
  (assoc verdict
         :timestamp timestamp
         :paatokset (map
                      (fn [paatos]
                        (assoc paatos :poytakirjat
                               (map
                                 (fn [pk]
                                   (if-let [url (get-in pk [:liite :linkkiliitteeseen])]
                                     (do
                                       (debug "Download" url)
                                       (let [filename        (-> url (URL.) (.getPath) (ss/suffix "/"))

                                             resp            (http/get url :as :stream :throw-exceptions false)
                                             headerFilename  (when (get (:headers resp) "content-disposition")
                                                               (clojure.string/replace (get (:headers resp) "content-disposition") #"attachment;filename=" ""))

                                             content-length  (util/->int (get-in resp [:headers "content-length"] 0))
                                             urlhash         (digest/sha1 url)
                                             attachment-id   urlhash
                                             attachment-type {:type-group "muut" :type-id "muu"}
                                             target          {:type "verdict" :id urlhash}
                                             locked          true
                                             attachment-time (get-in pk [:liite :muokkausHetki] timestamp)]
                                         ; If the attachment-id, i.e., hash of the URL matches
                                         ; any old attachment, a new version will be added
                                         (if (= 200 (:status resp))
                                           (attachment/attach-file! {:application-id id
                                                                     :filename (or headerFilename filename)
                                                                     :size content-length
                                                                     :content (:body resp)
                                                                     :attachment-id attachment-id
                                                                     :attachment-type attachment-type
                                                                     :target target
                                                                     :locked locked
                                                                     :user user
                                                                     :created attachment-time})
                                           (error (str (:status resp) " - unable to download " url ": " resp)))
                                         (-> pk (assoc :urlHash urlhash) (dissoc :liite))))
                                     pk))
                                 (:poytakirjat paatos))))
                      (:paatokset verdict))))

(defmulti get-verdicts-with-attachments  (fn [application user timestamp] (:permitType application)))

(defmethod get-verdicts-with-attachments "YA" [{:keys [id organization]} user timestamp]
  (if-let [legacy   (organization/get-legacy organization)]
    (let [xml      (krysp/ya-application-xml legacy id)
          verdicts (krysp/->verdicts xml :yleinenAlueAsiatieto krysp/->ya-verdict)]
      (map (partial verdict-attachments id user timestamp) verdicts))
    (fail! :error.no-legacy-available)))

(defmethod get-verdicts-with-attachments "R" [{:keys [id organization]} user timestamp]
  (if-let [legacy   (organization/get-legacy organization)]
    (let [xml      (krysp/application-xml legacy id)
          verdicts (krysp/->verdicts xml :RakennusvalvontaAsia krysp/->verdict)]
      (map (partial verdict-attachments id user timestamp) verdicts))
    (fail! :error.no-legacy-available)))

(defcommand check-for-verdict
  {:description "Fetches verdicts from municipality backend system.
                 If the command is run more than once, existing verdicts are
                 replaced by the new ones."
   :parameters [:id]
   :states     [:submitted :complement-needed :sent :verdictGiven] ; states reviewed 2013-09-17
   :roles      [:authority]
   :notified   true
   :on-success  (notify     :application-verdict)}
  [{:keys [user created application] :as command}]
  (if-let [verdicts-with-attachments (seq (get-verdicts-with-attachments application user created))]
    (let [has-old-verdict-tasks (some #(= "verdict" (get-in % [:source :type]))  (:tasks application))
          tasks (when (env/feature? :rakentamisen-aikaiset-tabi) (tasks/verdicts->tasks (assoc application :verdicts verdicts-with-attachments) created))
          updates {$set (merge {:verdicts verdicts-with-attachments
                                :modified created
                                :state    :verdictGiven}
                          (when-not has-old-verdict-tasks {:tasks tasks}))}]
      (update-application command updates)
      (ok :verdictCount (count verdicts-with-attachments) :taskCount (count (get-in updates [$set :tasks]))))
    (fail :info.no-verdicts-found-from-backend)))

;;
;; krysp enrichment
;;

(defn add-value-metadata [m meta-data]
  (reduce (fn [r [k v]] (assoc r k (if (map? v) (add-value-metadata v meta-data) (assoc meta-data :value v)))) {} m))

(defcommand "merge-details-from-krysp"
  {:parameters [id documentId buildingId collection]
   :input-validators [commands/validate-collection]
   :roles      [:applicant :authority]}
  [{created :created {:keys [organization propertyId] :as application} :application :as command}]
  (if-let [legacy (organization/get-legacy organization)]
    (let [document     (commands/by-id application collection documentId)
          schema       (schemas/get-schema (:schema-info document))
          kryspxml     (krysp/building-xml legacy propertyId)
          updates      (-> (or (krysp/->rakennuksen-tiedot kryspxml buildingId) {}) tools/unwrapped tools/path-vals)
          updates      (filter (fn [[path _]] (model/find-by-name (:body schema) path)) updates)]
      (infof "merging data into %s %s" (get-in document [:schema-info :name]) (:id document))
      (when (seq updates)
        (commands/persist-model-updates id collection document updates created :source "krysp"))
      (ok))
    (fail :error.no-legacy-available)))

(defcommand get-building-info-from-legacy
  {:parameters [id]
   :roles      [:applicant :authority]}
  [{{:keys [organization propertyId] :as application} :application}]
  (if-let [legacy   (organization/get-legacy organization)]
    (let [kryspxml  (krysp/building-xml legacy propertyId)
          buildings (krysp/->buildings kryspxml)]
      (ok :data buildings))
    (fail :error.no-legacy-available)))

;;
;; Service point for jQuery dataTables:
;;

(def col-sources [(fn [app] (if (:infoRequest app) "inforequest" "application"))
                  (juxt :address :municipality)
                  meta-fields/get-application-operation
                  :applicant
                  :submitted
                  :indicators
                  :unseenComments
                  :modified
                  :state
                  :authority])

(def order-by (assoc col-sources
                     0 :infoRequest
                     1 :address
                     2 nil
                     3 nil
                     5 nil
                     6 nil))

(def col-map (zipmap col-sources (map str (range))))

(defn add-field [application data [app-field data-field]]
  (assoc data data-field (app-field application)))

(defn make-row [application]
  (let [base {"id" (:_id application)
              "kind" (if (:infoRequest application) "inforequest" "application")}]
    (reduce (partial add-field application) base col-map)))

(defn make-query [query {:keys [filter-search filter-kind filter-state filter-user]}]
  (merge
    query
    (condp = filter-kind
      "applications" {:infoRequest false}
      "inforequests" {:infoRequest true}
      "both"         nil)
    (condp = filter-state                       ;; TODO: Tanne closed-tila ?
      "all"       {:state {$ne "canceled"}}
      "active"    {:state {$nin ["draft" "canceled" "answered" "verdictGiven"]}}
      "canceled"  {:state "canceled"})
    (when-not (contains? #{nil "0"} filter-user)
      {$or [{"auth.id" filter-user}
            {"authority.id" filter-user}]})
    (when-not (blank? filter-search)
      {:address {$regex filter-search $options "i"}})))

(defn make-sort [params]
  (let [col (get order-by (:iSortCol_0 params))
        dir (if (= "asc" (:sSortDir_0 params)) 1 -1)]
    (if col {col dir} {})))

(defn applications-for-user [user params]
  (let [user-query  (domain/basic-application-query-for user)
        user-total  (mongo/count :applications user-query)
        query       (make-query user-query params)
        query-total (mongo/count :applications query)
        skip        (params :iDisplayStart)
        limit       (params :iDisplayLength)
        apps        (query/with-collection "applications"
                      (query/find query)
                      (query/sort (make-sort params))
                      (query/skip skip)
                      (query/limit limit))
        rows        (map (comp make-row (partial meta-fields/with-meta-fields user)) apps)
        echo        (str (util/->int (str (params :sEcho))))] ; Prevent XSS
    {:aaData                rows
     :iTotalRecords         user-total
     :iTotalDisplayRecords  query-total
     :sEcho                 echo}))

(defquery "applications-for-datatables"
  {:parameters [:params]
   :verified true}
  [{user :user {params :params} :data}]
  (ok :data (applications-for-user user params)))

;;
;; Query that returns number of applications or info-requests user has:
;;

(defquery applications-count
  {:parameters [kind]
   :authenticated true
   :verified true}
  [{:keys [user]}]
  (let [base-query (domain/application-query-for user)
        query (condp = kind
                "inforequests" (assoc base-query :infoRequest true)
                "applications" (assoc base-query :infoRequest false)
                "both"         base-query
                {:_id -1})]
    (ok :data (mongo/count :applications query))))
