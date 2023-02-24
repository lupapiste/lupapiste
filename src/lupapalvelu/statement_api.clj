(ns lupapalvelu.statement-api
  (:require [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.asianhallinta.core :as ah]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.ely :as ely]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as usr]
            [lupapalvelu.user-utils :as uu]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [error]]))

;;
;; Authority Admin operations
;;

(defquery get-organizations-statement-givers
  {:parameters [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (statement/fetch-organization-statement-givers organizationId))

(defn- statement-giver-model [{{:keys [text organization]} :data} _ __]
  {:text         text
   :organization #(get-in organization [:name (or (keyword %) :fi)])})

(defn- statement-giver-exists [{{personId :personId organizationId :organizationId} :data}]
  "Pre-check that fails if the edited statement giver doesn't exist.
  Is only checked when actually editing the statement giver, and not when determining auth for the command"
  (when personId
    (when-let [giver-ids (->> (organization/get-organization organizationId)
                              :statementGivers
                              (map :id)
                              set)]
      (when-not (contains? giver-ids personId)
        (fail :error.unknown-id)))))

(notifications/defemail :add-statement-giver
  {:recipients-fn notifications/from-user
   :subject-key   "application.statements"
   :model-fn      statement-giver-model})

(defcommand create-statement-giver
  {:description         "Creates new statement giver into the organization. If
  the email is an existing authority, her name is used. Name parameter
  and ultimately the email are fallbacks."
   :parameters          [organizationId email text]
   :optional-parameters [name]
   :input-validators    [(partial action/non-blank-parameters [:email :text])
                         action/email-validator]
   :pre-checks          [(fn [{data :data}] (if (some? (:email data))
                                              (let [user (usr/get-user-by-email (:email data))]
                                                (if (usr/financial-authority? user)
                                                  (fail! :error.is-financial-authority)))))]
   :notified            true
   :permissions         [{:required [:organization/admin]}]}
  [{:keys [user]}]
  (let [organization       (organization/get-organization organizationId)
        email              (ss/canonize-email email)
        statement-giver-id (mongo/create-id)]
    (if-let [{fname :firstName lname :lastName :as authority} (uu/authority-by-email organizationId user email)]
      (do
        (organization/update-organization organizationId {$push
                                                          {:statementGivers
                                                           {:id    statement-giver-id
                                                            :text  (ss/trim text)
                                                            :email email
                                                            :name  (cond
                                                                     (ss/not-blank? fname) (str fname " " lname)
                                                                     (ss/not-blank? name) (ss/trim name)
                                                                     :default email)}}})
        (notifications/notify! :add-statement-giver {:user authority
                                                     :data {:text text :organization organization}})
        (ok :id statement-giver-id))
      (fail :error.not-authority))))

(defcommand edit-statement-giver
  {:description         "Updates a statement giver's information.
  Editing email information or the id is prohibited.
  The name update is used even if the email belongs to an existing authority."
   :parameters          [organizationId personId name text]
   :input-validators    [(partial action/non-blank-parameters [:organizationId :personId :text])]
   :pre-checks          [statement-giver-exists]
   :permissions         [{:required [:organization/admin]}]}
  [{user :user}]
  (do
    (organization/update-organization
      organizationId
      {:statementGivers {$elemMatch {:id personId}}}
      {$set {:statementGivers.$.name (ss/trim name)
             :statementGivers.$.text (ss/trim text)}})
    (ok :id personId)))

(defcommand delete-statement-giver
  {:description      "Delete a statement giver from the organization."
   :parameters       [organizationId personId]
   :input-validators [(partial action/non-blank-parameters [:personId])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (organization/update-organization organizationId {$pull {:statementGivers {:id personId}}}))

;;
;; Authority operations
;;

(defquery get-possible-statement-statuses
  {:description      "Provides the possible statement statuses according to the krysp version in use."
   :parameters       [:id]
   :user-roles       #{:authority :applicant}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :states           states/all-application-states}
  [command]
  (ok :data (statement/possible-statement-statuses command)))

(defquery get-statement-givers
  {:parameters       [:id]
   :user-roles       #{:authority}
   :user-authz-roles roles/default-authz-writer-roles
   :states           states/all-application-states}
  [{:keys [organization]}]
  (statement/fetch-organization-statement-givers @organization))

(defn- request-statement-model [{{:keys [saateText dueDate]} :data app :application caller :user :as command} _ recipient]
  (merge (notifications/create-app-model command nil recipient)
         {:link          #(notifications/get-application-link app "/statement" (name %) recipient)
          :message       saateText
          :requester     caller
          :due-date      (date/finnish-date dueDate)
          :due-date-line #(some->> dueDate
                                   date/finnish-date
                                   (i18n/localize-and-fill % :email.statement.due-date))}))


(notifications/defemail :request-statement
  {:recipients-fn :recipients
   :subject-key   "statement-request"
   :model-fn      request-statement-model})

(notifications/defemail :request-statement-new-user
  {:recipients-fn :recipients
   :subject-key   "statement-request-new-user"
   :model-fn      request-statement-model})

(defcommand request-for-statement
  {:parameters       [functionCode id selectedPersons]
   :user-roles       #{:authority}
   :states           #{:open :submitted :complementNeeded :sent}
   :pre-checks       [statement/statement-in-sent-state-allowed]
   :input-validators [(partial action/vector-parameters-with-map-items-with-required-keys [:selectedPersons] [:email :name :text])
                      statement/validate-selected-persons
                      statement/validate-due-date]
   :notified         true
   :description      "Adds statement-requests to the application and ensures permission to all new users."}
  [{{:keys [dueDate saateText]} :data user :user {:keys [organization]} :application now :created :as command}]
  (let [persons     (map #(update % :email ss/canonize-email) (ss/trimwalk selectedPersons))
        new-emails  (->> (map :email persons) (remove usr/get-user-by-email) (set))
        users       (map (comp #(usr/get-or-create-user-by-email % user) :email) persons)
        persons+uid (map #(assoc %1 :userId (:id %2)) persons users)
        metadata    (when (seq functionCode) (tos/metadata-for-document organization functionCode "lausunto"))
        statements  (map #(statement/create-statement now (ss/trim saateText) dueDate % metadata) persons+uid)
        auth        (map #(usr/user-in-role %1 :statementGiver :statementId (:id %2)) users statements)]
    (update-application command {$push {:statements {$each statements}
                                        :auth       {$each auth}}})
    (notifications/notify! :request-statement-new-user (assoc command :recipients (filter (comp new-emails :email) users)))
    (notifications/notify! :request-statement (assoc command :recipients (remove (comp new-emails :email) users)))))

(defquery ely-statement-types
  {:parameters       [id]
   :description      "Returns possible ELY statement types for application"
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:authority}                                              ; default-org-authz-roles
   :states           (states/all-application-states-but :draft :canceled)}
  [{{:keys [permitType]} :application}]
  (ok :statementTypes (permit/get-metadata permitType :ely-statement-types)))


(defcommand ely-statement-request
  {:parameters          [id subtype]
   :optional-parameters [functionCode lang dueDate saateText]
   :user-roles          #{:authority}
   :states              #{:open :submitted :complementNeeded :sent}
   :pre-checks          [statement/statement-in-sent-state-allowed
                         ely/ely-uspa-enabled
                         statement/validate-due-date]
   :input-validators    [ely/subtype-input-validator]
   :description         "Sends request for statement to ELY-keskus via integration"}
  [{:keys [created user organization] :as command}]
  (let [submitted-application (mongo/by-id :submitted-applications id)
        metadata              (when (seq functionCode) (tos/metadata-for-document organization functionCode "lausunto"))
        ely-statement-giver   (ely/ely-statement-giver (i18n/localize lang subtype))
        message-id            (mongo/create-id)
        ely-data              {:partner   "ely"
                               :subtype   subtype
                               :messageId message-id}
        statement             (statement/create-statement created (ss/trim saateText) dueDate
                                                          ely-statement-giver metadata ely-data)]
    (ah/save-statement-request command submitted-application statement (or lang (:language user)))
    (update-application command {$push {:statements statement}})
    (ok :text :ely.statement.sent)))

(defcommand delete-statement
  {:parameters       [id statementId]
   :input-validators [(partial action/non-blank-parameters [:id :statementId])]
   :states           #{:open :submitted :complementNeeded :sent}
   :user-roles       #{:authority}
   :pre-checks       [statement/statement-not-given
                      statement/not-ely-statement]}
  [command]
  (update-application command {$pull {:statements {:id statementId} :auth {:statementId statementId}}}))

(defcommand save-statement-as-draft
  {:parameters       [:id statementId :lang]
   :input-validators [(partial action/non-blank-parameters [:id :statementId :lang])]
   :pre-checks       [statement/statement-owner
                      statement/statement-not-given
                      statement/statement-in-sent-state-allowed]
   :states           #{:open :submitted :complementNeeded :sent}
   :user-roles       #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :description      "statement owners can save statements as draft before giving final statement."}
  [{application :application user :user {:keys [text status modify-id in-attachment]} :data :as command}]
  (when (and status (not ((statement/possible-statement-statuses command) status)))
    (fail! :error.unknown-statement-status))
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (statement/update-draft text status modify-id (:id user) in-attachment))]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})
    (ok :modify-id (:modify-id statement))))

(notifications/defemail :notify-statement-due-date-change
  {:recipients-fn (fn [{:keys [application data] :as command}]
                    [(some-> (statement/get-statement application (:statementId data))
                             :person
                             :userId
                             usr/get-user-by-id)])
   :subject-key   "notify-statement-due-date-change"
   :model-fn      request-statement-model})

(defcommand save-statement-due-date
  {:parameters [:id statementId dueDate :lang]
   :input-validators [(partial action/non-blank-parameters [:id :statementId :lang])
                      (partial action/timestamp-parameters [:dueDate])
                      statement/validate-due-date]
   :pre-checks [statement/statement-not-given
                statement/statement-in-sent-state-allowed]
   :states #{:open :submitted :complementNeeded :sent}
   :user-roles #{:authority}
   :notified true
   :on-success [(fn [command _] (notifications/notify! :notify-statement-due-date-change command))]
   :description "authorities can change statements due date and save the statement as draft."}
  [{application :application user :user {:keys [text status modify-id in-attachment]} :data :as command}]
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (assoc :dueDate dueDate))]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})
    (ok :modify-id (:modify-id statement))))

(defcommand give-statement
  {:parameters          [:id statementId status :lang]
   :optional-parameters [text in-attachment]
   :input-validators    [(partial action/non-blank-parameters [:id :statementId :status :lang])]
   :pre-checks          [statement/statement-owner
                         statement/statement-not-given
                         statement/statement-in-sent-state-allowed
                         statement/not-ely-statement]
   :states              #{:open :submitted :complementNeeded :sent}
   :user-roles          #{:authority :applicant}
   :user-authz-roles    #{:statementGiver}
   :notified            true
   :on-success          [(fn [command _] (notifications/notify! :new-comment command))]
   :description         "statement owners can give statements - notifies via comment."}
  [{:keys [application user created lang] {:keys [modify-id]} :data :as command}]
  (cond
    (not ((statement/possible-statement-statuses command) status))
    (fail! :error.unknown-statement-status)

    (and in-attachment
         (->> (att/get-attachments-by-target-type-and-id application {:type "statement" :id statementId})
              (not-any? #(= (att/attachment-type-coercer (:type %))
                            {:type-group :ennakkoluvat_ja_lausunnot :type-id :lausunto}))))
    (fail! :error.statement-attachment-missing)

    (and (not in-attachment)
         (ss/blank? text))
    (fail! :error.statement-text-or-attachment-required))

  (let [comment-text       (i18n/loc "statement.given")
        comment-target     {:type :statement :id statementId}
        comment-model      (comment/comment-mongo-update (:state application) comment-text
                                                         comment-target :system false user nil created)
        content-text       (or (ss/trim text) (i18n/localize lang :statement.statement-in-attachment))
        {:keys [modify-id]
         :as   statement}  (-> (util/find-by-id statementId (:statements application))
                               (statement/give-statement content-text status modify-id
                                                         (:id user) in-attachment created))
        attachment-updates (statement/attachments-readonly-updates application statementId)]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})
    (try
      (when-not in-attachment
        (statement/generate-statement-attachment user
                                                 (domain/get-application-no-access-checking (:id application))
                                                 statementId
                                                 lang))
      (update-application command
                          (util/deep-merge comment-model attachment-updates))
      (ok :modify-id modify-id)
      (catch Exception e
        (error e "Cannot give statement" statementId)
        ;; Rollback statement state
        (update-application command
                            {:statements {$elemMatch {:id statementId}}}
                            {$set   {:statements.$.state :draft}
                             $unset {:statements.$.given true}})
        (fail :error.statement.give-failure
              :modify-id modify-id)))))

(defcommand request-for-statement-reply
  {:parameters          [:id statementId :lang]
   :optional-parameters [text]
   :input-validators    [(partial action/non-blank-parameters [:id :statementId :lang])]
   :pre-checks          [statement/statement-given
                         statement/replies-enabled
                         statement/reply-not-visible
                         statement/statement-in-sent-state-allowed]
   :states              #{:open :submitted :complementNeeded :sent}
   :user-roles          #{:authority}
   :description         "request for reply for statement when statement is given and organization has enabled statement replies"}
  [{:keys [application user] :as command}]
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (statement/request-for-reply (ss/trim text) (:id user)))]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})
    (ok :modify-id (:modify-id statement))))

(defcommand save-statement-reply-as-draft
  {:parameters       [:id statementId :lang]
   :input-validators [(partial action/non-blank-parameters [:id :statementId :lang])]
   :pre-checks       [statement/statement-replyable
                      statement/statement-in-sent-state-allowed]
   :states           #{:open :submitted :complementNeeded :sent}
   :user-roles       #{:applicant}
   :user-authz-roles roles/default-authz-writer-roles
   :description      "save reply for the statement as draft"}
  [{application :application user :user {:keys [text nothing-to-add modify-id]} :data :as command}]
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (statement/update-reply-draft (ss/trim text)
                                                    nothing-to-add
                                                    modify-id
                                                    (:id user)))]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})
    (ok :modify-id (:modify-id statement))))

(defcommand reply-statement
  {:parameters       [:id statementId :lang]
   :input-validators [(partial action/non-blank-parameters [:id :statementId :lang])]
   :pre-checks       [statement/statement-replyable
                      statement/statement-in-sent-state-allowed]
   :states           #{:open :submitted :complementNeeded :sent}
   :user-roles       #{:applicant}
   :user-authz-roles roles/default-authz-writer-roles
   :description      "reply to statement"}
  [{application :application user :user {:keys [text nothing-to-add modify-id]} :data :as command}]
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (statement/reply-statement (ss/trim text) nothing-to-add modify-id (:id user)))]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})
    (ok :modify-id (:modify-id statement))))

(defquery statement-replies-enabled
  {:description      "Pseudo query for UI authorization logic"
   :parameters       [:id]
   :states           (states/all-application-states-but [:draft])
   :user-roles       #{:authority :applicant}
   :user-authz-roles roles/default-authz-writer-roles
   :pre-checks       [statement/replies-enabled]}
  [_])

(defquery statement-is-replyable
  {:description      "Pseudo query for UI authorization logic"
   :parameters       [:id]
   :states           (states/all-application-states-but [:draft])
   :user-roles       #{:authority :applicant}
   :user-authz-roles roles/default-authz-writer-roles
   :pre-checks       [statement/reply-visible]}
  [_])

(defquery authorized-for-requesting-statement-reply
  {:description      "Pseudo query for UI authorization logic"
   :parameters       [:id]
   :states           (states/all-application-states-but [:draft])
   :user-roles       #{:authority}
   :user-authz-roles roles/default-authz-writer-roles
   :pre-checks       [statement/replies-enabled
                      statement/reply-not-visible]}
  [_])

(defquery statement-attachment-allowed
  {:parameters       [:id]
   :pre-checks       [statement/statement-owner
                      statement/statement-not-given
                      statement/statement-in-sent-state-allowed]
   :states           #{:draft :open :submitted :complementNeeded :sent}
   :user-roles       #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :description      "Pseudo query for showing Add attachment button."}
  [_])

(defquery statements-after-approve-allowed
  {:parameters  [:id]
   :pre-checks  [statement/statement-in-sent-state-allowed]
   :states      #{:submitted :complementNeeded}
   :user-roles  #{:authority}
   :description "Pseudo query for determining whether show a warning for statement drafts upon application approval."}
  [_])

(defquery neighbors-statement-enabled
  {:parameters  [:id]
   :states      (states/all-application-states-but [:draft])
   :user-roles  #{:authority :applicant}
   :pre-checks  [(permit/validate-permit-type-is-not permit/YA permit/A)]
   :description "Pseudo query for determining whether show neighbors statement section."}
  [_])

(defquery application-statement-tab-visible
  {:description      "Pseudo query for tab visibility logic"
   :parameters       [:id]
   :states           states/pre-verdict-but-draft
   :user-roles       #{:authority :applicant}
   :user-authz-roles roles/all-authz-writer-roles
   :pre-checks       [permit/is-not-archiving-project
                      (fn foreman-app-check [{application :application}]
                        (cond
                          (app/ymp-clarification-app? application) (fail :ymp-clarification-application)
                          (foreman/foreman-app? application)       (fail :error.unauthorized)))]} [_])
