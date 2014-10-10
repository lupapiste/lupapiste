(ns lupapalvelu.authorization-api
  "API for manipulating application.auth"
  (:require [clojure.string :refer [blank? join trim split]]
            [swiss.arrows :refer [-<>>]]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.core :refer [ok fail fail! unauthorized]]
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
        projection (assoc common :_id 0)
        filter     {$and [common {:state {$ne :canceled}}]}
        data       (mongo/select :applications filter projection)
        invites    (map :invite (mapcat :auth data))]
    (ok :invites invites)))

(defn- create-invite-model [command conf]
  (assoc (notifications/create-app-model command conf) :message (get-in command [:data :text]) ))

(notifications/defemail :invite  {:recipients-fn  notifications/from-data
                                  :model-fn create-invite-model})

(defcommand invite
  {:parameters [id email title text documentName documentId path]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :states     (action/all-application-states-but [:closed :canceled])
   :roles      [:applicant :authority]
   :notified   true
   :on-success (notify :invite)}
  [{:keys [created user application] :as command}]
  (let [email (-> email ss/lower-case ss/trim)]
    (if (domain/invite application email)
      (fail :invite.already-has-auth)
      (let [invited (user-api/get-or-create-user-by-email email user)
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
   :states     (action/all-application-states-but [:closed :canceled])}
  [{:keys [created user application] :as command}]
  (when-let [my-invite (domain/invite application (:email user))]
    (update-application command
      {:auth {$elemMatch {:invite.user.id (:id user)}}}
      {$set  {:modified created
              :auth.$ (assoc (user/user-in-role user :writer) :inviteAccepted created)}})
    (when-let [document (domain/get-document-by-id application (:documentId my-invite))]
      ; Document can be undefined in invite or removed by the time invite is approved.
      ; It's not possible to combine Mongo writes here,
      ; because only the last $elemMatch counts.
      (a/set-user-to-document (domain/get-application-as id user) document (:id user) (:path my-invite) user created))))

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

(defn- do-remove-auth [{application :application :as command} email]
  (let [username (-> email ss/lower-case ss/trim)
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
