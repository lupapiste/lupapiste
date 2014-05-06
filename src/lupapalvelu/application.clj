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
            [lupapalvelu.action :refer [defquery defcommand update-application without-system-keys notify] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.comment :as comment]
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
   To be used in commands' :pre-checks vector."
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
                        (model/has-hetu? (:body schema) [path])
                        (user/same-user? current-user subject))
         person       (tools/unwrapped (model/->henkilo subject :with-hetu with-hetu))
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
   :extra-auth-roles [:any]
   :parameters [:id]}
  [{app :application user :user}]
  (if app
    (let [app (assoc app :allowedAttachmentTypes (attachment/get-attachment-types-for-application app))]
      (ok :application ((app-post-processor user) app)
          :authorities (find-authorities-in-applications-organization app)
          :permitSubtypes (permit/permit-subtypes (:permitType app))))
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
  (when (and (not (= "Y" (:permitType application))) (not (:infoRequest application)))
    (when-let [rakennuspaikka (or (domain/get-document-by-name application "rakennuspaikka")
                                  (domain/get-document-by-name application "poikkeusasian-rakennuspaikka")
                                  (domain/get-document-by-name application "vesihuolto-kiinteisto"))]
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
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :roles      [:applicant :authority]
   :notified   true
   :on-success (notify :invite)
   :verified   true}
  [{:keys [created user application] :as command}]
  (let [email (-> email ss/lower-case ss/trim)]
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
            {$push {:auth     auth}
             $set  {:modified created}}))))))

(defcommand approve-invite
  {:parameters [id]
   :roles      [:applicant]
   :verified   true}
  [{:keys [created user application] :as command}]
  (when-let [my-invite (domain/invite application (:email user))]
    (update-application command
      {:auth {$elemMatch {:invite.user.id (:id user)}}}
      {$set  {:modified created
              :auth.$ (user/user-in-role user :writer)}})
    (when-let [document (domain/get-document-by-id application (:documentId my-invite))]
      ; It's not possible to combine Mongo writes here,
      ; because only the last $elemMatch counts.
      (set-user-to-document id document (:id user) (:path my-invite) user created))))

(defn- do-remove-auth [command email]
  (update-application command
      {$pull {:auth {$and [{:username (ss/lower-case email)}
                           {:type {$ne :owner}}]}}
       $set  {:modified (:created command)}}))

(defcommand decline-invitation
  {:parameters [:id]
   :authenticated true}
  [command]
  (do-remove-auth command (get-in command [:user :email])))

(defcommand remove-auth
  {:parameters [:id email]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :roles      [:applicant :authority]}
  [command]
  (do-remove-auth command email))

(defn applicant-cant-set-to [{{:keys [to]} :data user :user} _]
  (when (and to (not (user/authority? user)))
    (fail :error.to-settable-only-by-authority)))

(defn- validate-comment-target [{{:keys [target]} :data}]
  (when (string? target)
    (fail :error.unknown-type)))

(defcommand can-target-comment-to-authority
  {:roles [:authority]
   :pre-checks  [not-open-inforequest-user-validator]
   :description "Dummy command for UI logic"})

(defcommand add-comment
  {:parameters [id text target]
   :roles      [:applicant :authority]
   :extra-auth-roles [:statementGiver]
   :pre-checks [applicant-cant-set-to]
   :input-validators [validate-comment-target]
   :notified   true
   :on-success [(notify :new-comment)
                (fn [{data :data :as command} _]
                  (when-let [to-user (and (:to data) (user/get-user-by-id (:to data)))]
                    ;; LUPA-407
                    (notifications/notify! :application-targeted-comment (assoc command :user to-user))))
                open-inforequest/notify-on-comment]}
  [{{:keys [to mark-answered openApplication] :or {mark-answered true}} :data :keys [user created application] :as command}]
  (let [to-user   (and to (or (user/get-user-by-id to) (fail! :to-is-not-id-of-any-user-in-system)))]
    (update-application command
      (util/deep-merge
        (comment/comment-mongo-update (:state application) text target (:role user) mark-answered user to-user created)
        (when openApplication {$set {:state :open, :opened created}})))))

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
  [{:keys [user created] :as command}]
  (update-application command
    {$set {:modified created
           :authority (user/summary user)}}))

(defcommand assign-application
  {:parameters  [:id assigneeId]
   :pre-checks  [not-open-inforequest-user-validator]
   :roles       [:authority]}
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
        verdicts (mongo/select-one :applications {:_id link-permit-app-id} {:verdicts 1})
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
              attachments-argument (or (attachment/create-sent-timestamp-update-statements (:attachments application) sent-file-ids created) {})]
          (do-rest-fn attachments-argument)))
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
        do-update (fn [attachments-argument]
                    (update-application command
                      mongo-query
                      {$set (merge app-updates attachments-argument)})
                    (ok :integrationAvailable (not (nil? attachments-argument))))]

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
   :states     [:draft :info :open]
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
   :states     [:draft :open :submitted :complement-needed :info]}
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

(defquery inforequest-markers
  {:parameters [id lang x y]
   :roles      [:authority]
   :states     [:draft :open :submitted :complement-needed :info]   ;; TODO: Mitka tilat?
   :input-validators [(partial action/non-blank-parameters [:x :y])]}
  [{:keys [application user]}]
  (let [inforequests (mongo/select :applications
                      (merge
                        (domain/application-query-for user)
                        {:infoRequest true})
                      {:title 1 :auth 1 :location 1 :operations 1 :comments 1})

       same-location-irs (filter
                           #(and (= x (-> % :location :x str)) (= y (-> % :location :y str)))
                           inforequests)

        remove-irs-by-id-fn (fn [target-irs irs-to-be-removed]
                              (remove
                                (fn [ir] (some #(= (:id ir) (:id %)) irs-to-be-removed))
                                target-irs))

        inforequests (remove-irs-by-id-fn inforequests same-location-irs)

        application-op-name (-> application :operations first :name)  ;; an inforequest can only have one operation

        same-op-irs (filter
                      (fn [ir]
                        (some #(= application-op-name (:name %)) (:operations ir)))
                      inforequests)

        others (remove-irs-by-id-fn inforequests same-op-irs)

        same-location-irs (map (partial make-marker-contents id lang) same-location-irs)
        same-op-irs       (map (partial make-marker-contents id lang) same-op-irs)
        others            (map (partial make-marker-contents id lang) others)]

    (ok :sameLocation same-location-irs :sameOperation same-op-irs :others others)
    ))

(defn make-attachments [created operation organization-id applicationState & {:keys [target]}]
  (let [organization (organization/get-organization organization-id)]
    (for [[type-group type-id] (organization/get-organization-attachments-for-operation organization operation)]
      (attachment/make-attachment created target false applicationState operation {:type-group type-group :type-id type-id}))))

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

          application   (merge domain/application-skeleton
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
                          (when-not info-request?
                            {:attachments            (make-attachments created op organization-id state)
                             :documents              (make-documents user created op application)}))]

      application)))

;; TODO: separate methods for inforequests & applications for clarity.
(defcommand create-application
  {:parameters [:operation :x :y :address :propertyId :municipality]
   :roles      [:applicant :authority]
   :notified   true ; OIR
   :input-validators [(partial action/non-blank-parameters [:operation :address :municipality])
                      (partial property-id-parameters [:propertyId])
                      operation-validator]}
  [{{:keys [operation x y address propertyId municipality infoRequest messages]} :data :keys [user created] :as command}]

  ;; TODO: These let-bindings are repeated in do-create-application, merge th somehow
  (let [permit-type       (operations/permit-type-of-operation operation)
        organization      (organization/resolve-organization municipality permit-type)
        info-request?     (boolean infoRequest)
        open-inforequest? (and info-request? (:open-inforequest organization))
        created-application (do-create-application command)]

      (mongo/insert :applications created-application)
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
   :states     [:draft :open :complement-needed :submitted]
   :input-validators [operation-validator]
   :pre-checks [add-operation-allowed?]}
  [{:keys [application created] :as command}]
  (let [op-id      (mongo/create-id)
        op         (make-op operation created)
        new-docs   (make-documents nil created op application)]
    (update-application command {$push {:operations op}
                                 $pushAll {:documents new-docs
                                           :attachments (make-attachments created op (:organization application) (:state application))}
                                 $set {:modified created}})))

(defn- link-permit-required? [_ application]
  (when (nil? (some
                (fn [{op-name :name}]
                  (:link-permit-required ((keyword op-name) operations/operations)))
                (:operations application)))
    (fail :error.link-permit-not-required)))

(defcommand link-permit-required
  {:parameters [id]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed :submitted]
   :pre-checks [link-permit-required?]})

(defcommand change-permit-sub-type
  {:parameters [id permitSubtype]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed :submitted]
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
   :states     [:draft :info :answered :open :complement-needed :submitted]
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

(defquery app-matches-for-link-permits
  {:parameters [id]
   :verified   true
   :roles      [:applicant :authority]}
  [{{:keys [propertyId] :as application} :application user :user :as command}]
  (let [results (mongo/select :applications
                  (merge (domain/application-query-for user) {:_id {$ne id}
                                                              :state {$in ["verdictGiven" "constructionStarted"]}
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
        without-same-property-id (sort-by :text
                                   (vec (filter (comp not same-property-id-fn) enriched-results)))
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
      (fail :error.jatkolupa-can-only-be-added-one-link-permit))))

(defcommand add-link-permit
  {:parameters ["id" linkPermitId]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed :submitted]
   :pre-checks [validate-jatkolupa-zero-link-permits]
   :input-validators [(partial action/non-blank-parameters [:linkPermitId])]}
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
    (mongo/insert :applications muutoslupa-app)
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
    (mongo/insert :applications continuation-app)
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
   :roles      [:applicant :authority]
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


(defn- validate-new-applications-enabled [command {:keys [organization]}]
  (let [org (mongo/by-id :organizations organization {:new-application-enabled 1})]
    (when-not (= (:new-application-enabled org) true)
      (fail :error.new-applications.disabled))))

(defcommand convert-to-application
  {:parameters [id]
   :roles      [:applicant]
   :states     [:draft :info :answered]
   :pre-checks [validate-new-applications-enabled]}
  [{:keys [user created application] :as command}]
  (let [op          (first (:operations application))]
    (update-application command
      {$set {:infoRequest false
             :state :open
             :documents (make-documents user created op application)
             :modified created}
       $pushAll {:attachments (make-attachments created op (:organization application) (:state application))}})
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
                         :name name         ; poytakirjat[] / paatoksentekija
                         :given given       ; paivamaarat / antoPvm
                         :status status     ; poytakirjat[] / paatoskoodi
                         :official official ; paivamaarat / lainvoimainenPvm
                         })}}))

(defn verdict-attachments [application user timestamp verdict]
  {:pre [application]}
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
                                             header-filename  (when (get (:headers resp) "content-disposition")
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
                                           (attachment/attach-file! {:application application
                                                                     :filename (or header-filename filename)
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

(defn get-application-xml [{:keys [id permitType] :as application} & [raw?]]
  (if-let [{url :url} (organization/get-krysp-wfs application)]
    (if-let [fetch (permit/get-application-xml-getter permitType)]
      (fetch url id raw?)
      (do
        (error "No fetch function for" permitType (:organization application))
        (fail! :error.unknown)))
    (fail! :error.no-legacy-available)))

(defn- get-verdicts-with-attachments  [application user timestamp xml]
  (let [permit-type (:permitType application)
        reader (permit/get-verdict-reader permit-type)
        verdicts (krysp/->verdicts xml reader)]
    (map (partial verdict-attachments application user timestamp) verdicts)))

(defcommand check-for-verdict
  {:description "Fetches verdicts from municipality backend system.
                 If the command is run more than once, existing verdicts are
                 replaced by the new ones."
   :parameters [:id]
   :states     [:submitted :complement-needed :sent :verdictGiven] ; states reviewed 2013-09-17
   :roles      [:authority]
   :notified   true
   :on-success  (notify :application-verdict)}
  [{:keys [user created application] :as command}]
  (let [xml (get-application-xml application)
        extras-reader (permit/get-verdict-extras-reader (:permitType application))]
    (if-let [verdicts-with-attachments (seq (get-verdicts-with-attachments application user created xml))]
     (let [has-old-verdict-tasks (some #(= "verdict" (get-in % [:source :type]))  (:tasks application))
           tasks (tasks/verdicts->tasks (assoc application :verdicts verdicts-with-attachments) created)
           updates {$set (merge {:verdicts verdicts-with-attachments
                                 :modified created
                                 :state    :verdictGiven}
                           (when-not has-old-verdict-tasks {:tasks tasks})
                           (when extras-reader (extras-reader xml)))}]
       (update-application command updates)
       (ok :verdictCount (count verdicts-with-attachments) :taskCount (count (get-in updates [$set :tasks]))))
     (fail :info.no-verdicts-found-from-backend))))

;;
;; krysp enrichment
;;

(defn add-value-metadata [m meta-data]
  (reduce (fn [r [k v]] (assoc r k (if (map? v) (add-value-metadata v meta-data) (assoc meta-data :value v)))) {} m))

(defcommand merge-details-from-krysp
  {:parameters [id documentId buildingId collection]
   :input-validators [commands/validate-collection]
   :roles      [:applicant :authority]}
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
        (commands/persist-model-updates id collection document updates created :source "krysp"))
      (ok))
    (fail :error.no-legacy-available)))

(defcommand get-building-info-from-wfs
  {:parameters [id]
   :roles      [:applicant :authority]}
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
   :verified true}
  [{user :user}]
  (ok :data (search/applications-for-user user params)))

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
