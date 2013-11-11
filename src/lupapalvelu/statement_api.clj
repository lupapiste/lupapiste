(ns lupapalvelu.statement-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand with-application executed]]
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

(defquery get-statement-persons
  {:roles [:authority :authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization (mongo/select-one :organizations {:_id (first organizations)})
        permitPersons (or (:statementPersons organization) [])]
    (ok :data permitPersons)))

(defcommand create-statement-person
  {:parameters [email text]
   :notified   true
   :roles      [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization-id (first organizations)
        organization    (mongo/select-one :organizations {:_id organization-id})
        email           (ss/lower-case email)
        statement-person-id (mongo/create-id)]
    (with-user-by-email email
      (when-not (user/authority? user) (fail! :error.not-authority))
      (mongo/update
        :organizations
        {:_id organization-id}
        {$push {:statementPersons {:id statement-person-id
                                   :text text
                                   :email email
                                   :name (str (:firstName user) " " (:lastName user))}}})
      (notifications/notify! "new-statement-person"  {:user user :data {:text text :organization organization}})
      (ok :id statement-person-id))))

(defcommand delete-statement-person
  {:parameters [personId]
   :roles      [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization-id (first organizations)]
  (mongo/update
    :organizations
    {:_id organization-id}
    {$pull {:statementPersons {:id personId}}})))

;;
;; Authority operations
;;

(defcommand should-see-unsubmitted-statements
  {:roles [:authority]} [_])

(defcommand request-for-statement
  {:parameters  [id personIds]
   :roles       [:authority]
   :states      [:draft :info :open :submitted :complement-needed]
   :notified    true
   :description "Adds statement-requests to the application and ensures writer-permission to all new users."}
  [{user :user {:keys [organization] :as application} :application now :created :as command}]
  (organization/with-organization organization
    (fn [{:keys [statementPersons]}]
      (let [personIdSet    (set personIds)
            persons        (filter #(personIdSet (:id %)) statementPersons)
            users          (map #(user-api/get-or-create-user-by-email (:email %)) persons)
            writers        (map #(user/user-in-role % :writer) users)
            new-writers    (filter #(not (domain/has-auth? application (:id %))) writers)
            new-userids    (set (map :id new-writers))
            unique-writers (distinct new-writers)
            ->statement    (fn [person] {:id        (mongo/create-id)
                                         :person    person
                                         :requested now
                                         :given     nil
                                         :status    nil})
            statements    (map ->statement persons)]
        (mongo/update-by-id :applications id {$pushAll {:statements statements
                                                        :auth unique-writers}})
        (notifications/send-on-request-for-statement! persons application user (env/value :host))))))

(defcommand delete-statement
  {:parameters [id statementId]
   :states     [:draft :info :open :submitted :complement-needed]
   :roles      [:authority]}
  [_]
  (mongo/update-by-id :applications id {$pull {:statements {:id statementId}}}))

(defcommand give-statement
  {:parameters  [id statementId status text]
   :validators  [statement-exists statement-owner #_statement-not-given]
   :states      [:draft :info :open :submitted :complement-needed]
   :roles       [:authority]
   :description "authrority-roled statement owners can give statements - notifies via comment."}
  [{:keys [application] :as command}]
  (mongo/update
    :applications
    {:_id id
     :statements {$elemMatch {:id statementId}}}
    {$set {:statements.$.status status
           :statements.$.given (now)
           :statements.$.text text}})
  (let [text (if (statement-given? application statementId)
                 "Hakemuksen lausuntoa on p\u00e4ivitetty." ; TODO localize?
                 "Hakemukselle lis\u00e4tty lausunto.")]
    ; FIXME combine mongo writes
    (executed "add-comment"
      (-> command
        (assoc :data {:id id
                      :text   text
                      :type   :system
                      :target {:type :statement
                               :id   statementId}})))))