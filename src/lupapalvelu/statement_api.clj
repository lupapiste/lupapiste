(ns lupapalvelu.statement-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand update-application executed]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :refer [with-user-by-email] :as user]
            [lupapalvelu.user-api :as user-api]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.statement :refer :all]))

;;
;; Authority Admin operations
;;

(defquery get-statement-givers
  {:roles [:authority :authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization (mongo/select-one :organizations {:_id (first organizations)})
        permitPersons (or (:statementGivers organization) [])]
    (ok :data permitPersons)))

(defcommand create-statement-giver
  {:parameters [email text]
   :notified   true
   :roles      [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization-id (first organizations)
        organization    (mongo/select-one :organizations {:_id organization-id})
        email           (ss/lower-case email)
        statement-giver-id (mongo/create-id)]
    (with-user-by-email email
      (when-not (user/authority? user) (fail! :error.not-authority))
      (mongo/update
        :organizations
        {:_id organization-id}
        {$push {:statementGivers {:id statement-giver-id
                                   :text text
                                   :email email
                                   :name (str (:firstName user) " " (:lastName user))}}})
      (notifications/notify! :add-statement-giver  {:user user :data {:text text :organization organization}})
      (ok :id statement-giver-id))))

(defcommand delete-statement-giver
  {:parameters [personId]
   :roles      [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization-id (first organizations)]
  (mongo/update
    :organizations
    {:_id organization-id}
    {$pull {:statementGivers {:id personId}}})))

;;
;; Authority operations
;;

(defcommand should-see-unsubmitted-statements
  {:description "Pseudo command for UI authorization logic"
   :roles [:authority] :extra-auth-roles [:statementGiver]} [_])

(defcommand request-for-statement
  {:parameters  [id personIds]
   :roles       [:authority]
   :states      [:draft :info :open :submitted :complement-needed]
   :notified    true
   :description "Adds statement-requests to the application and ensures permission to all new users."}
  [{user :user {:keys [organization] :as application} :application now :created :as command}]
  (organization/with-organization organization
    (fn [{:keys [statementGivers]}]
      (let [personIdSet (set personIds)
            persons     (filter #(personIdSet (:id %)) statementGivers)
            details     (map #(let [user (or (user/get-user-by-email (:email %)) (fail! :error.not-found))
                                    statement-id (mongo/create-id)
                                    statement-giver (assoc % :userId (:id user))]
                                {:statement {:id statement-id
                                             :person    statement-giver
                                             :requested now
                                             :given     nil
                                             :status    nil}
                                 :auth (user/user-in-role user :statementGiver :statementId statement-id)
                                 :mail-list (assoc user :email (:email %))}) persons)
            statements (map :statement details)
            auth       (map :auth details)
            mail-list  (map :mail-list details)]
          (update-application command {$pushAll {:statements statements :auth auth}})
          (notifications/notify! :request-statement (assoc command :data {:users mail-list}))))))

(defcommand delete-statement
  {:parameters [id statementId]
   :states     [:draft :info :open :submitted :complement-needed]
   :roles      [:authority]}
  [command]
  (update-application command {$pull {:statements {:id statementId}}}))

(defcommand give-statement
  {:parameters  [id statementId status text]
   :pre-checks  [statement-exists statement-owner #_statement-not-given]
   :states      [:draft :info :open :submitted :complement-needed]
   :roles       [:authority]
   :extra-auth-roles [:statementGiver]
   :description "authrority-roled statement owners can give statements - notifies via comment."}
  [{:keys [application] :as command}]
  (update-application command
    {:statements {$elemMatch {:id statementId}}}
    {$set {:statements.$.status status
           :statements.$.given (now)
           :statements.$.text text}})
  (let [text (if (statement-given? application statementId)
                 "Hakemuksen lausuntoa on p\u00e4ivitetty." ; TODO localize?
                 "Hakemukselle lis\u00e4tty lausunto.")]
    ; FIXME combine mongo writes
    (executed "add-comment"
      (assoc command :data {:id id
                            :text   text
                            :type   :system
                            :target {:type :statement
                                     :id   statementId}}))))
