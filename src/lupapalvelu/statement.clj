(ns lupapalvelu.statement
  (:use [monger.operators]
        [clojure.tools.logging]
        [lupapalvelu.core]
        [sade.env])
  (:require [sade.security :as sadesecurity]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.notifications :as notifications]))

;;
;; Common
;;

(defn get-statement [{:keys [statements]} id]
  (first (filter #(= id (:id %)) statements)))

(defn statement-exists [{{:keys [statementId]} :data} application]
  (when-not (get-statement application statementId)
    (fail :error.no-statement :statementId statementId)))

(defn statement-owner [{{:keys [statementId]} :data {user-email :email} :user} application]
  (let [{{statement-email :email} :person} (get-statement application statementId)]
    (when-not (= statement-email user-email)
      (fail :error.not-statement-owner))))

(defn statement-given? [application statementId]
  (boolean (->> statementId (get-statement application) :given)))

(defn statement-not-given [{{:keys [statementId]} :data} application]
  (when (statement-given? application statementId)
    (fail :error.statement-already-given)))

;;
;; Authority Admin operations
;;

(defquery "get-statement-persons"
  {:roles [:authority :authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization (mongo/select-one :organizations {:_id (first organizations)})
        permitPersons (or (:statementPersons organization) [])]
    (ok :data permitPersons)))

(defcommand "create-statement-person"
  {:parameters [:email :text]
   :notified   true
   :roles      [:authorityAdmin]}
  [{{:keys [email text]} :data {:keys [organizations] :as user} :user}]
  (let [organization-id (first organizations)
        organization    (mongo/select-one :organizations {:_id organization-id})]
    (with-user email
      (fn [{:keys [firstName lastName] :as user}]
        (if-not (security/authority? user)
          (fail :error.not-authority)
          (do
            (mongo/update
              :organizations
              {:_id organization-id}
              {$push {:statementPersons {:id (mongo/create-id)
                                         :text text
                                         :email email
                                         :name (str firstName " " lastName)}}})
            (notifications/send-create-statement-person! email text organization)))))))

(defcommand "delete-statement-person"
  {:parameters [:personId]
   :roles      [:authorityAdmin]}
  [{{:keys [personId]} :data {:keys [organizations] :as user} :user}]
  (let [organization-id (first organizations)]
  (mongo/update
    :organizations
    {:_id organization-id}
    {$pull {:statementPersons {:id personId}}})))

;;
;; Authority operations
;;

(defcommand "should-see-unsubmitted-statements"
  {:roles       [:authority]} [_])

(defcommand "request-for-statement"
  {:parameters  [:id :personIds]
   :roles       [:authority]
   :notified    true
   :states      [:draft :info :open :submitted :complement-needed]
   :description "Adds statement-requests to the application and ensures writer-permission to all new users."}
  [{user :user {:keys [id personIds]} :data {:keys [host]} :web :as command}]
  (with-application command
    (fn [{:keys [organization] :as application}]
      (organization/with-organization organization
        (fn [{:keys [statementPersons]}]
          (let [now            (now)
                personIdSet    (set personIds)
                persons        (filter #(-> % :id personIdSet) statementPersons)
                users          (map #(security/get-or-create-user-by-email (:email %)) persons)
                writers        (map #(role % :writer) users)
                new-writers    (filter #(not (domain/has-auth? application (:id %))) writers)
                new-userids    (set (map :id new-writers))
                unique-writers (distinct new-writers)
                ->statement    (fn [person] {:id        (mongo/create-id)
                                             :person    person
                                             :requested now
                                             :given     nil
                                             :status    nil})
                statements    (map ->statement persons)]
            (mongo/update :applications {:_id id} {$pushAll {:statements statements
                                                             :auth unique-writers}})
            (notifications/send-on-request-for-statement! persons application user host)))))))

(defcommand "delete-statement"
  {:parameters [:id :statementId]
   :states      [:draft :info :open :submitted :complement-needed]
   :roles      [:authority]}
  [{{:keys [id statementId]} :data}]
  (mongo/update :applications {:_id id} {$pull {:statements {:id statementId}}}))

(defcommand "give-statement"
  {:parameters  [:id :statementId :status :text]
   :validators  [statement-exists statement-owner #_statement-not-given]
   :states      [:draft :info :open :submitted :complement-needed]
   :roles       [:authority]
   :description "authrority-roled statement owners can give statements - notifies via comment."}
  [{{:keys [id statementId status text]} :data :keys [application] :as command}]
  (mongo/update
    :applications
    {:_id id
     :statements {$elemMatch {:id statementId}}}
    {$set {:statements.$.status status
           :statements.$.given (now)
           :statements.$.text text}})
  (let [text (if (statement-given? application statementId)
                 "Hakemuksen lausuntoa on p\u00e4ivitetty."
                 "Hakemukselle lis\u00e4tty lausunto.")]
    (executed "add-comment"
      (-> command
        (assoc :data {:id id
                      :text   text
                      :type   :system
                      :target {:type :statement
                               :id   statementId}})))))
