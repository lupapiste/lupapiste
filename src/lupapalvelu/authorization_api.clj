(ns lupapalvelu.authorization-api
  "API for manipulating application.auth"
  (:require [clojure.string :refer [blank? join trim split]]
            [swiss.arrows :refer [-<>>]]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.core :refer [ok fail fail! unauthorized]]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application all-application-states notify] :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.application :as a]
            [lupapalvelu.user-api :as user-api]
            [lupapalvelu.user :as user]
            [lupapalvelu.document.model :as model]
            ))


;;
;; Invites
;;

(defquery invites
  {:roles [:applicant :authority]}
  [{{:keys [id]} :user}]
  (let [common     {:auth {$elemMatch {:invite.user.id id}}}
        query      {$and [common {:state {$ne :canceled}}]}
        data       (mongo/select :applications query [:auth])
        invites    (filter #(= id (get-in % [:user :id])) (map :invite (mapcat :auth data)))]
    (ok :invites invites)))

(defn- create-invite-model [command conf recipient]
  (assoc (notifications/create-app-model command conf recipient)
    :message (get-in command [:data :text])
    :recipient-email (:email recipient)))

(notifications/defemail :invite  {:recipients-fn :recipients
                                  :model-fn create-invite-model})

(defn- valid-role [role]
  (#{:writer :foreman} (keyword role)))

(defn- create-invite [command id email text documentName documentId path role]
  {:pre [(valid-role role)]}
  (let [email (user/canonize-email email)
        {created :created user :user application :application} command]
    (if (domain/invite application email)
      (fail :invite.already-has-auth)
      (let [invited (user-api/get-or-create-user-by-email email user)
            invite  {:application  id
                     :text         text
                     :path         path
                     :documentName documentName
                     :documentId   documentId
                     :created      created
                     :email        email
                     :user         (user/summary invited)
                     :inviter      (user/summary user)}
            writer  (user/user-in-role invited (keyword role))
            auth    (assoc writer :invite invite)]
        (if (domain/has-auth? application (:id invited))
          (fail :invite.already-has-auth)
          (do
            (update-application command
              {:auth {$not {$elemMatch {:invite.user.username email}}}}
              {$push {:auth     auth}
               $set  {:modified created}})
            (notifications/notify! :invite (assoc command :recipients [invited]))
            (ok)))))))

(defn- role-validator [{{role :role} :data}]
  (when-not (valid-role role)
    (fail! :error.illegal-role :parameters role)))

(defcommand invite-with-role
  {:parameters [id email text documentName documentId path role]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator
                      role-validator]
   :states     (action/all-application-states-but [:closed :canceled])
   :roles      [:applicant :authority]
   :notified   true}
  [command]
  (create-invite command id email text documentName documentId path role))

(defcommand approve-invite
  {:parameters [id]
   :roles      [:applicant]
   :states     (action/all-application-states-but [:closed :canceled])}
  [{:keys [created user application] :as command}]
  (when-let [my-invite (domain/invite application (:email user))]

    (let [role (:role (domain/get-auth application (:id user)))]
      (update-application command
        {:auth {$elemMatch {:invite.user.id (:id user)}}}
        {$set {:modified created
               :auth.$   (assoc (user/user-in-role user role) :inviteAccepted created)}}))

    (when-not (empty? (:documentId my-invite))
      (when-let [document (domain/get-document-by-id application (:documentId my-invite))]
        ; Document can be undefined (invite's documentId is an empty string) in invite or removed by the time invite is approved.
        ; It's not possible to combine Mongo writes here, because only the last $elemMatch counts.
        (a/do-set-user-to-document (domain/get-application-as id user) document (:id user) (:path my-invite) user created)))))

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

(defn- do-remove-auth [{application :application :as command} username]
  (let [username (user/canonize-email username)
        user-pred #(when (and (= (:username %) username) (not= (:type %) "owner")) %)]
    (when (some user-pred (:auth application))
      (let [updated-app (update-in application [:auth] (fn [a] (remove user-pred a)))
            doc-updates (generate-remove-invalid-user-from-docs-updates updated-app)]
        (update-application command
          (merge
            {$pull {:auth {$and [{:username username}, {:type {$ne :owner}}]}}
             $set  {:modified (:created command)}}
            (when (seq doc-updates) {$unset doc-updates})))))))

(defcommand decline-invitation
  {:parameters [:id]
   :roles [:applicant :authority]
   :states     (action/all-application-states-but [:canceled])}
  [command]
  (do-remove-auth command (get-in command [:user :email])))

;;
;; Auhtorizations
;;

(defcommand remove-auth
  {:parameters [:id username]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :roles      [:applicant :authority]
   :states     (action/all-application-states-but [:canceled])}
  [command]
  (do-remove-auth command username))

(defn- manage-unsubscription [{application :application user :user :as command} unsubscribe?]
  (let [username (get-in command [:data :username])]
    (if (or (= username (:username user))
         (some (partial = (:organization application)) (:organizations user)))
      (update-application command
        {:auth {$elemMatch {:username username}}}
        {$set {:auth.$.unsubscribed unsubscribe?}})
      unauthorized)))

(defcommand unsubscribe-notifications
  {:parameters [:id :username]
   :roles [:applicant :authority]
   :states all-application-states}
  [command]
  (manage-unsubscription command true))

(defcommand subscribe-notifications
  {:parameters [:id :username]
   :roles [:applicant :authority]
   :states all-application-states}
  [command]
  (manage-unsubscription command false))
