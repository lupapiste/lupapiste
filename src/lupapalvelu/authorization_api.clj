(ns lupapalvelu.authorization-api
  "API for manipulating application.auth"
  (:require [taoensso.timbre :refer [debug]]
            [clojure.string :refer [blank? join trim split]]
            [swiss.arrows :refer [-<>>]]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.core :refer [ok fail fail! unauthorized]]
            [sade.util :as util]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.states :as states]))

;;
;; Invites
;;

(defn- invites-with-application [application]
  (->>
    (domain/invites application)
    (map #(assoc % :application (select-keys application [:id :address :primaryOperation :municipality])))))

(defquery invites
  {:user-roles #{:applicant :authority :oirAuthority}}
  [{{:keys [id]} :user}]
  (let [common     {:auth {$elemMatch {:invite.user.id id}}}
        query      {$and [common {:state {$ne :canceled}}]}
        data       (mongo/select :applications query [:auth :primaryOperation :address :municipality])
        invites    (mapcat invites-with-application data)
        my-invites (filter #(= id (get-in % [:user :id])) invites)]
    (ok :invites my-invites)))

(defn- create-invite-email-model [command conf recipient]
  (assoc (notifications/create-app-model command conf recipient)
    :message (get-in command [:data :text])
    :recipient-email (:email recipient)
    :inviter-email (-> command :user :email)))

(notifications/defemail :invite  {:recipients-fn :recipients
                                  :model-fn create-invite-email-model})

(notifications/defemail :guest-invite  {:recipients-fn :recipients
                                        :model-fn create-invite-email-model})

(defn- create-prev-permit-invite-email-model [command conf recipient]
  (assoc (notifications/create-app-model command conf recipient)
    :kuntalupatunnus (get-in command [:data :kuntalupatunnus])
    :recipient-email (:email recipient)))

(notifications/defemail :invite-to-prev-permit  {:recipients-fn :recipients
                                                 :model-fn create-prev-permit-invite-email-model
                                                 :subject-key "invite"})

(defn- valid-role [role]
  (#{:writer :foreman :guest} (keyword role)))

(defn send-invite! [{{:keys [email text documentName documentId path role notification]} :data
                     timestamp :created
                     inviter :user
                     application :application
                     :as command}]
  {:pre [(valid-role role)]}
  (let [email (user/canonize-email email)
        existing-user (user/get-user-by-email email)]
    (if (or (domain/invite application email) (auth/has-auth? application (:id existing-user)))
      (fail :invite.already-has-auth)
      (let [invited (user/get-or-create-user-by-email email inviter)
            auth    (auth/create-invite-auth inviter invited (:id application) role timestamp text documentName documentId path)
            email-template (if (= notification "invite-to-prev-permit")
                             :invite-to-prev-permit
                             :invite)]
        (update-application command
          {:auth {$not {$elemMatch {:invite.user.username (:email invited)}}}}
          {$push {:auth     auth}
           $set  {:modified timestamp}})
        (notifications/notify! email-template (assoc command :recipients [invited]))
        (ok)))))

(defn- role-validator [{{role :role} :data}]
  (when-not (valid-role role)
    (fail! :error.illegal-role :parameters role)))

(defcommand invite-with-role
  {:parameters [:id :email :text :documentName :documentId :path :role]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator
                      role-validator]
   :states     states/all-application-states
   :user-roles #{:applicant :authority}
   :pre-checks  [application/validate-authority-in-drafts]
   :notified   true}
  [command]
  (send-invite! command))

(defcommand approve-invite
  {:parameters [id]
   :user-roles #{:applicant}
   :user-authz-roles auth/default-authz-reader-roles
   :states     states/all-application-states}
  [{:keys [created user application] :as command}]
  (when-let [my-invite (domain/invite application (:email user))]
    (let [my-auth (auth/get-auth application (:id user))
          role (or (:role my-invite) (:role my-auth))
          inviter (:inviter my-invite)
          document-id (:documentId my-invite)]
      (update-application command
        {:auth {$elemMatch {:invite.user.id (:id user)}}}
        {$set {:modified created
               :auth.$   (util/assoc-when (user/user-in-role user role) :inviter inviter :inviteAccepted created)}})
      (when-not (empty? document-id)
        (let [application (domain/get-application-as id user :include-canceled-apps? true)]
          ; Document can be undefined (invite's documentId is an empty string) in invite or removed by the time invite is approved.
          ; It's not possible to combine Mongo writes here, because only the last $elemMatch counts.
          (doc-persistence/do-set-user-to-document application document-id (:id user) (:path my-invite) created false)))
      (ok))))

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
   :user-roles #{:applicant :authority}
   :user-authz-roles auth/default-authz-reader-roles
   :states     states/all-application-states}
  [command]
  (do-remove-auth command (get-in command [:user :username])))

;;
;; Auhtorizations
;;

(defcommand remove-auth
  {:parameters [:id username]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :user-roles #{:applicant :authority}
   :states     (states/all-application-states-but [:canceled])
   :pre-checks [application/validate-authority-in-drafts]}
  [command]
  (do-remove-auth command username))

(defn- manage-unsubscription [{application :application user :user :as command} unsubscribe?]
  (let [username (get-in command [:data :username])]
    (if (or (= username (:username user))
            (some (partial = (:organization application)) (user/organization-ids-by-roles user #{:authority})))
      (update-application command
        {:auth {$elemMatch {:username username}}}
        {$set {:auth.$.unsubscribed unsubscribe?}})
      unauthorized)))

(defcommand unsubscribe-notifications
  {:parameters [:id :username]
   :input-validators [(partial action/non-blank-parameters [:id :username])]
   :user-roles #{:applicant :authority}
   :states states/all-application-states
   :pre-checks [application/validate-authority-in-drafts]}
  [command]
  (manage-unsubscription command true))

(defcommand subscribe-notifications
  {:parameters [:id :username]
   :input-validators [(partial action/non-blank-parameters [:id :username])]
   :user-roles #{:applicant :authority}
   :states states/all-application-states
   :pre-checks [application/validate-authority-in-drafts]}
  [command]
  (manage-unsubscription command false))
