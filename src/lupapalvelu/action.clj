(ns lupapalvelu.action
  (:use [monger.operators]
        [clojure.tools.logging]
        [sade.strings :only [suffix]]
        [lupapalvelu.core])
  (:require [clojure.string :as s]
            [sade.security :as sadesecurity]
            [sade.client :as sadeclient]
            [sade.email :as email]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(defquery "invites"
  {:authenticated true
   :verified true}
  [{{:keys [id]} :user}]
  (let [filter     {:auth {$elemMatch {:invite.user.id id}}}
        projection (assoc filter :_id 0)
        data       (mongo/select :applications filter projection)
        invites    (map :invite (mapcat :auth data))]
    (ok :invites invites)))

(defcommand "invite"
  {:parameters [:id :email :title :text :documentName]
   :roles      [:applicant]
   :verified   true}
  [{created :created
    user    :user
    {:keys [id email title text documentName documentId]} :data {:keys [host]} :web :as command}]
  (with-application command
    (fn [{application-id :id :as application}]
      (if (domain/invited? application email)
        (fail :already-invited)
        (let [invited (security/get-or-create-user-by-email email)
              invite  {:title        title
                       :application  application-id
                       :text         text
                       :documentName documentName
                       :documentId   documentId
                       :created      created
                       :email        email
                       :user         (security/summary invited)
                       :inviter      (security/summary user)}
              writer  (role invited :writer)
              auth    (assoc writer :invite invite)]
          (if (domain/has-auth? application (:id invited))
            (fail :already-has-auth)
            (do
              (mongo/update
                :applications
                {:_id application-id
                 :auth {$not {$elemMatch {:invite.user.username email}}}}
                {$push {:auth auth}})
              (notifications/send-invite email text application user host))))))))

(defcommand "approve-invite"
  {:parameters [:id]
   :roles      [:applicant]
   :verified   true}
  [{user :user :as command}]
  (with-application command
    (fn [{application-id :id :as application}]
      (when-let [my-invite (domain/invite application (:email user))]
        (executed "set-user-to-document"
          (-> command
            (assoc-in [:data :documentId] (:documentId my-invite))
            (assoc-in [:data :userId]     (:id user))))
        (mongo/update :applications
          {:_id application-id :auth {$elemMatch {:invite.user.id (:id user)}}}
          {$set  {:auth.$ (role user :writer)}})))))

(defcommand "remove-invite"
  {:parameters [:id :email]
   :roles      [:applicant]}
  [{{:keys [id email]} :data :as command}]
  (with-application command
    (fn [{application-id :id}]
      (with-user email
        (fn [_]
          (mongo/update-by-id :applications application-id
            {$pull {:auth {$and [{:username email}
                                 {:type {$ne :owner}}]}}}))))))

;; TODO: we need a) custom validator to tell weathet this is ok and/or b) return effected rows (0 if owner)
(defcommand "remove-auth"
  {:parameters [:id :email]
   :roles      [:applicant]}
  [{{:keys [id email]} :data :as command}]
  (with-application command
    (fn [{application-id :id}]
      (mongo/update-by-id :applications application-id
        {$pull {:auth {$and [{:username email}
                             {:type {$ne :owner}}]}}}))))

(env/in-dev
  (defcommand "create-apikey"
  {:parameters [:username :password]}
  [command]
  (if-let [user (security/login (-> command :data :username) (-> command :data :password))]
    (let [apikey (security/create-apikey)]
      (mongo/update
        :users
        {:username (:username user)}
        {$set {"private.apikey" apikey}})
      (ok :apikey apikey))
      (fail :error.unauthorized))))

(defcommand "add-comment"
  {:parameters [:id :text :target]
   :roles      [:applicant :authority]}
  [{{:keys [text target]} :data {:keys [host]} :web user :user :as command}]
  (with-application command
    (fn [application]
      (when (and (= "draft" (:state application)) (not (s/blank? text)))
        (executed "open-application" command))
      (mongo/update-by-id
        :applications
        (:id application)
        {$set {:modified (:created command)}
         $push {:comments {:text    text
                           :target  target
                           :created (:created command)
                           :user    (security/summary user)}}})
      (notifications/send-notifications-on-new-comment application user text host))))

(defcommand "assign-to-me"
  {:parameters [:id]
   :roles      [:authority]}
  [{{id :id} :data user :user :as command}]
  (with-application command
    (fn [application]
      (mongo/update-by-id
        :applications (:id application)
        {$set {:authority (security/summary user)}}))))

(defcommand "set-user-to-document"
  {:parameters [:id :documentId :userId :path]
   :authenticated true}
  [{{:keys [documentId userId path]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (let [document     (domain/get-document-by-id application documentId)
            schema-name  (get-in document [:schema :info :name])
            schema       (get schemas/schemas schema-name)
            subject      (security/get-non-private-userinfo userId)
            henkilo      (domain/user2henkilo subject)
            full-path    (str "documents.$.body" (when-not (s/blank? path) (str "." path)))]
        (if (nil? document)
          (fail :error.document-not-found)
          (do
            (infof "merging user %s with best effort into document %s into path %s" subject name full-path)
            (mongo/update
              :applications
              {:_id (:id application)
               :documents {$elemMatch {:id documentId}}}
              {$set {full-path henkilo
                     :modified (:created command)}})))))))
