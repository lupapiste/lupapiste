(ns lupapalvelu.statement-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application executed] :as action]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as user]
            [lupapalvelu.child-to-attachment :as child-to-attachment]))

;;
;; Authority Admin operations
;;

(defquery get-organizations-statement-givers
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (user/authority-admins-organization-id user)]
    (statement/fetch-organization-statement-givers org-id)))

(defn- statement-giver-model [{{:keys [text organization]} :data} _ __]
  {:text text
   :organization-fi (:fi (:name organization))
   :organization-sv (:sv (:name organization))})

(notifications/defemail :add-statement-giver
  {:recipients-fn  notifications/from-user
   :subject-key    "application.statements"
   :model-fn       statement-giver-model})

(defcommand create-statement-giver
  {:parameters [email text]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :notified   true
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization (organization/get-organization (user/authority-admins-organization-id user))
        email           (user/canonize-email email)
        statement-giver-id (mongo/create-id)]
    (if-let [{fname :firstName lname :lastName :as user} (user/get-user-by-email email)]
      (do
        (when-not (user/authority? user) (fail! :error.not-authority))
        (organization/update-organization (:id organization) {$push
                                                              {:statementGivers
                                                               {:id statement-giver-id
                                                                :text text
                                                                :email email
                                                                :name (if-not (and (empty? fname) (empty? lname))
                                                                        (str fname " " lname)
                                                                        email)}}})
        (notifications/notify! :add-statement-giver  {:user user :data {:text text :organization organization}})
        (ok :id statement-giver-id))
      (fail :error.user-not-found))))

(defcommand delete-statement-giver
  {:parameters [personId]
   :input-validators [(partial action/non-blank-parameters [:personId])]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (organization/update-organization
    (user/authority-admins-organization-id user)
    {$pull {:statementGivers {:id personId}}}))

;;
;; Authority operations
;;

(defquery get-possible-statement-statuses
  {:description "Provides the possible statement statuses according to the krysp version in use."
   :parameters [:id]
   :user-roles #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :states states/all-application-states}
  [{application :application}]
  (ok :data (statement/possible-statement-statuses application)))

(defquery get-statement-givers
  {:parameters [:id]
   :user-roles #{:authority}
   :user-authz-roles auth/default-authz-writer-roles
   :states states/all-application-states}
  [{application :application organization :organization}]
  (statement/fetch-organization-statement-givers @organization))

(defn- get-dueDate-loc [lang dueDate]
  (if dueDate
    (str (i18n/with-lang lang (i18n/loc "statement.email.template.duedate-is")) " " (util/to-local-date dueDate) ".")
    ""))

(defn- request-statement-model [{{:keys [saateText dueDate]} :data app :application} _ recipient]
  {:link-fi (notifications/get-application-link app "/statement" "fi" recipient)
   :link-sv (notifications/get-application-link app "/statement" "sv" recipient)
   :saateText saateText
   :recipient-email (:email recipient)
   :dueDate (util/to-local-date dueDate)
   :due-date-str-fi (get-dueDate-loc "fi" dueDate)
   :due-date-str-sv (get-dueDate-loc "sv" dueDate)})


(notifications/defemail :request-statement
  {:recipients-fn  :recipients
   :subject-key    "statement-request"
   :model-fn       request-statement-model
   :show-municipality-in-subject true})

(notifications/defemail :request-statement-new-user
  {:recipients-fn  :recipients
   :subject-key    "statement-request-new-user"
   :model-fn       request-statement-model
   :show-municipality-in-subject true})

(defcommand request-for-statement
  {:parameters [functionCode id selectedPersons]
   :user-roles #{:authority}
   :states #{:open :submitted :complementNeeded :sent}
   :pre-checks [statement/statement-in-sent-state-allowed]
   :input-validators [(partial action/vector-parameters-with-map-items-with-required-keys [:selectedPersons] [:email :name :text])
                      statement/validate-selected-persons]
   :notified true
   :description "Adds statement-requests to the application and ensures permission to all new users."}
  [{{:keys [dueDate saateText]} :data user :user {:keys [organization] :as application} :application now :created :as command}]
  (let [new-emails  (->> (map :email selectedPersons) (remove user/get-user-by-email) (set))
        users       (map (comp #(user/get-or-create-user-by-email % user) :email) selectedPersons)
        persons+uid (map #(assoc %1 :userId (:id %2)) selectedPersons users)
        metadata    (when (seq functionCode) (tos/metadata-for-document organization functionCode "lausunto"))
        statements  (map (partial statement/create-statement now metadata saateText dueDate) persons+uid)
        auth        (map #(user/user-in-role %1 :statementGiver :statementId (:id %2)) users statements)]
    (update-application command {$push {:statements {$each statements}
                                        :auth       {$each auth}}})
    (notifications/notify! :request-statement-new-user (assoc command :recipients (filter (comp new-emails :email) users)))
    (notifications/notify! :request-statement (assoc command :recipients (remove (comp new-emails :email) users)))))

(defcommand delete-statement
  {:parameters [id statementId]
   :input-validators [(partial action/non-blank-parameters [:id :statementId])]
   :states     #{:open :submitted :complementNeeded :sent}
   :user-roles #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :pre-checks [statement/statement-not-given
                statement/authority-or-statement-owner-applicant]}
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
   :description "statement owners can save statements as draft before giving final statement."}
  [{application :application user :user {:keys [text status modify-id]} :data :as command}]
  (when (and status (not ((statement/possible-statement-statuses application) status)))
    (fail! :error.unknown-statement-status))
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (statement/update-draft text status modify-id (:id user)))]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})
    (ok :modify-id (:modify-id statement))))

(defcommand give-statement
  {:parameters  [:id statementId status text :lang]
   :input-validators [(partial action/non-blank-parameters [:id :statementId :status :text :lang])]
   :pre-checks  [statement/statement-owner
                 statement/statement-not-given
                 statement/statement-in-sent-state-allowed]
   :states      #{:open :submitted :complementNeeded :sent}
   :user-roles  #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :notified    true
   :on-success  [(fn [command _] (notifications/notify! :new-comment command))]
   :description "statement owners can give statements - notifies via comment."}
  [{:keys [application user created lang] {:keys [modify-id]} :data :as command}]
  (when-not ((statement/possible-statement-statuses application) status)
    (fail! :error.unknown-statement-status))
  (let [comment-text (i18n/loc "statement.given")
        comment-target {:type :statement :id statementId}
        comment-model  (comment/comment-mongo-update (:state application) comment-text comment-target :system false user nil created)
        statement   (-> (util/find-by-id statementId (:statements application))
                        (statement/give-statement text status modify-id (:id user)))
        attachment-updates (statement/attachments-readonly-updates application statementId)]
    (update-application command
      {:statements {$elemMatch {:id statementId}}}
      (util/deep-merge comment-model attachment-updates {$set {:statements.$ statement}}))
    (child-to-attachment/create-attachment-from-children user (domain/get-application-no-access-checking (:id application)) :statements statementId lang)
    (ok :modify-id (:modify-id statement))))

(defcommand request-for-statement-reply
  {:parameters       [:id statementId :lang]
   :input-validators [(partial action/non-blank-parameters [:id :statementId :lang])]
   :pre-checks       [statement/statement-given
                      statement/replies-enabled
                      statement/reply-not-visible
                      statement/statement-in-sent-state-allowed]
   :states           #{:open :submitted :complementNeeded :sent}
   :user-roles       #{:authority}
   :description      "request for reply for statement when statement is given and organization has enabled statement replies"}
  [{application :application user :user {:keys [text]} :data :as command}]
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (statement/request-for-reply text (:id user)))]
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
   :user-authz-roles auth/default-authz-writer-roles
   :description      "save reply for the statement as draft"}
  [{application :application user :user {:keys [text nothing-to-add modify-id]} :data :as command}]
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (statement/update-reply-draft text nothing-to-add modify-id (:id user)))]
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
   :user-authz-roles auth/default-authz-writer-roles
   :description      "reply to statement"}
  [{application :application user :user {:keys [text nothing-to-add modify-id]} :data :as command}]
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (statement/reply-statement text nothing-to-add modify-id (:id user)))]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})
    (ok :modify-id (:modify-id statement))))

(defquery statement-replies-enabled
  {:description      "Pseudo query for UI authorization logic"
   :parameters       [:id]
   :states           (states/all-application-states-but [:draft])
   :user-roles       #{:authority :applicant}
   :user-authz-roles auth/default-authz-writer-roles
   :pre-checks       [statement/replies-enabled]}
  [_])

(defquery statement-is-replyable
  {:description      "Pseudo query for UI authorization logic"
   :parameters       [:id]
   :states           (states/all-application-states-but [:draft])
   :user-roles       #{:authority :applicant}
   :user-authz-roles auth/default-authz-writer-roles
   :pre-checks       [statement/reply-visible]}
  [_])

(defquery authorized-for-requesting-statement-reply
  {:description      "Pseudo query for UI authorization logic"
   :parameters       [:id]
   :states           (states/all-application-states-but [:draft])
   :user-roles       #{:authority}
   :user-authz-roles auth/default-authz-writer-roles
   :pre-checks       [statement/replies-enabled
                      statement/reply-not-visible]}
  [_])

(defquery statement-attachment-allowed
  {:parameters  [:id]
   :pre-checks  [statement/statement-owner
                 statement/statement-not-given
                 statement/statement-in-sent-state-allowed]
   :states      #{:draft :open :submitted :complementNeeded :sent}
   :user-roles  #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :description "Pseudo query for showing Add attachment button."}
  [_])

(defquery statements-after-approve-allowed
  {:parameters [:id]
   :pre-checks [statement/statement-in-sent-state-allowed]
   :states      #{:submitted :complementNeeded}
   :user-roles  #{:authority}
   :description "Pseudo query for determining whether show a warning for statement drafts upon application approval."}
  [_])
