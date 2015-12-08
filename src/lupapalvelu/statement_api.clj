(ns lupapalvelu.statement-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.validators :as v]
            [lupapalvelu.action :refer [defquery defcommand update-application executed] :as action]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.statement :refer :all]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as user]
            [lupapalvelu.child-to-attachment :as child-to-attachment]))

;;
;; Authority Admin operations
;;

(defn- fetch-organization-statement-givers [org-id]
  (let [organization (organization/get-organization org-id)
        permitPersons (or (:statementGivers organization) [])]
    (ok :data permitPersons)))

(defquery get-organizations-statement-givers
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (user/authority-admins-organization-id user)]
    (fetch-organization-statement-givers org-id)))

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
    (if-let [user (user/get-user-by-email email)]
      (do
        (when-not (user/authority? user) (fail! :error.not-authority))
        (organization/update-organization (:id organization) {$push {:statementGivers {:id statement-giver-id
                                                                                       :text text
                                                                                       :email email
                                                                                       :name (str (:firstName user) " " (:lastName user))}}})
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
  (ok :data (possible-statement-statuses application)))

(defquery get-statement-givers
  {:parameters [:id]
   :user-roles #{:authority :applicant}
   :user-authz-roles auth/default-authz-writer-roles
   :states states/all-application-states}
  [{application :application}]
  (let [org-id (:organization application)]
    (fetch-organization-statement-givers org-id)))

(defquery should-see-unsubmitted-statements
  {:description "Pseudo query for UI authorization logic"
   :parameters [:id]
   :states (states/all-application-states-but [:draft])
   :user-roles #{:authority :applicant}
   :user-authz-roles #{:statementGiver}}
  [_])

(notifications/defemail :request-statement
  {:recipients-fn  :recipients
   :subject-key    "statement-request"
   :show-municipality-in-subject true})

(defn validate-selected-persons [{{selectedPersons :selectedPersons} :data}]
  (let [non-blank-string-keys (when (some
                                      #(some (fn [k] (or (-> % k string? not) (-> % k ss/blank?))) [:email :name :text])
                                      selectedPersons)
                                (fail :error.missing-parameters))
        has-invalid-email (when (some
                                  #(not (v/email-and-domain-valid? (:email %)))
                                  selectedPersons)
                            (fail :error.email))]
    (or non-blank-string-keys has-invalid-email)))

(defcommand request-for-statement
  {:parameters [functionCode id selectedPersons saateText dueDate]
   :user-roles #{:authority}
   :states #{:open :submitted :complementNeeded}
   :input-validators [(partial action/non-blank-parameters [:saateText])
                      (partial action/number-parameters [:dueDate])
                      (partial action/vector-parameters-with-map-items-with-required-keys [:selectedPersons] [:email :name :text])
                      validate-selected-persons]
   :notified true
   :description "Adds statement-requests to the application and ensures permission to all new users."}
  [{user :user {:keys [organization] :as application} :application now :created :as command}]
  (organization/with-organization organization
                                  (fn [{:keys [statementGivers]}]
                                    (let [stm-givers  (filter (comp string? (set (map :id selectedPersons)) :id) statementGivers)
                                          persons     (concat stm-givers (remove :id selectedPersons))
                                          users       (map (comp #(user/get-or-create-user-by-email % user) :email) persons)
                                          persons+uid (map #(assoc %1 :userId (:id %2)) persons users)
                                          metadata    (when (seq functionCode) (t/metadata-for-document organization functionCode "lausunto"))
                                          statements  (map (partial create-statement now metadata saateText dueDate) persons+uid)
                                          auth        (map #(user/user-in-role %1 :statementGiver :statementId (:id %2)) users statements)]
                                      (update-application command {$push {:statements {$each statements}
                                                                          :auth       {$each auth}}})
                                      (notifications/notify! :request-statement (assoc command :recipients users))))))

(defcommand delete-statement
  {:parameters [id statementId]
   :input-validators [(partial action/non-blank-parameters [:id :statementId])]
   :states     #{:open :submitted :complementNeeded}
   :user-roles #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :pre-checks [statement-not-given
                authority-or-statement-owner-applicant]}
  [command]
  (update-application command {$pull {:statements {:id statementId} :auth {:statementId statementId}}}))

(defcommand save-statement-as-draft
  {:parameters       [:id statementId :lang]
   :pre-checks       [statement-exists statement-owner statement-not-given]
   :states           #{:open :submitted :complementNeeded}
   :user-roles       #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :description "authrority-roled statement owners can save statements as draft before giving final statement."}
  [{application :application {:keys [text status modify-id prev-modify-id]} :data :as command}]
  (when (and status (not ((possible-statement-statuses application) status)))
    (fail! :error.unknown-statement-status))
  (let [statement (-> (util/find-by-id statementId (:statements application))
                      (update-draft text status modify-id prev-modify-id))]
    (update-application command
                        {:statements {$elemMatch {:id statementId}}}
                        {$set {:statements.$ statement}})))

(defcommand give-statement
  {:parameters  [:id statementId status text :lang]
   :input-validators [(partial action/non-blank-parameters [:id :statementId :status :text :lang])]
   :pre-checks  [statement-exists statement-owner statement-not-given]
   :states      #{:open :submitted :complementNeeded}
   :user-roles  #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :notified    true
   :on-success  [(fn [command _] (notifications/notify! :new-comment command))]
   :description "authrority-roled statement owners can give statements - notifies via comment."}
  [{:keys [application user created lang] {:keys [modify-id prev-modify-id]} :data :as command}]
  (when-not ((possible-statement-statuses application) status)
    (fail! :error.unknown-statement-status))
  (let [comment-text   (if (statement-given? application statementId)
                         (i18n/loc "statement.updated")
                         (i18n/loc "statement.given"))
        comment-target {:type :statement :id statementId}
        comment-model  (comment/comment-mongo-update (:state application) comment-text comment-target :system false user nil created)
        statement   (-> (util/find-by-id statementId (:statements application))
                        (give-statement text status modify-id prev-modify-id))
        response (update-application command
                                     {:statements {$elemMatch {:id statementId}}}
                                     (util/deep-merge
                                      comment-model
                                      {$set {:statements.$ statement}}))
        updated-app (assoc application :statements (util/update-by-id statement (:statements application)))]
    (child-to-attachment/create-attachment-from-children user updated-app :statements statementId lang)
    response))
