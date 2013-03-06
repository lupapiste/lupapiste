(ns lupapalvelu.action
  (:use [monger.operators]
        [clojure.tools.logging]
        [lupapalvelu.strings :only [suffix]]
        [lupapalvelu.core])
  (:require [sade.security :as sadesecurity]
            [sade.client :as sadeclient]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]
            [lupapalvelu.email :as email]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(defcommand "create-id" {:authenticated true} [_] (ok :id (mongo/create-id)))

(defquery "invites"
  {:authenticated true}
  [{{:keys [id]} :user}]
  (let [filter     {:auth {$elemMatch {:invite.user.id id}}}
        projection (assoc filter :_id 0)
        data       (mongo/select :applications filter projection)
        invites    (map :invite (mapcat :auth data))]
    (ok :invites invites)))

(defn invite-body [user id host]
  (format
    (str
      "Tervehdys,\n\n%s %s lis\u00E4si teid\u00E4t suunnittelijaksi lupahakemukselleen.\n\n"
      "Hyv\u00E4ksy\u00E4ksesi rooli ja n\u00E4hd\u00E4ksesi hakemuksen tiedot avaa linkki %s/fi/applicant?gotobang=/application/%s#!/application/%s\n\n"
      "Yst\u00E4v\u00E4llisin terveisin,\n\n"
      "Lupapiste.fi")
    (:firstName user)
    (:lastName user)
    host
    id
    id))

(defcommand "invite"
  {:parameters [:id :email :title :text :documentName]
   :roles      [:applicant]}
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
              (future
                (if (not= (suffix email "@") "example.com")
                  (try
                    (info "sending email to" email)
                    (if (email/send-email email (:title application) (invite-body user application-id host))
                      (info "email was sent successfully")
                      (error "email could not be delivered."))
                    (catch Exception e (info e (.getMessage e))))
                  (info "we are not sending emails to @example.com domain.")))
              nil)))))))

(defcommand "approve-invite"
  {:parameters [:id]
   :roles      [:applicant]}
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
    (fail :error.unauthorized)))

(defcommand "register-user"
  {:parameters [:stamp :email :password :street :zip :city :phone]}
  [{data :data}]
  (let [vetuma   (client/json-get (str "/vetuma/stamp/" (:stamp data)))
        userdata (merge data vetuma)]
    (infof "Registering new user: %s - details from vetuma: %s" (dissoc data :password) vetuma)
    (if-let [user (security/create-user userdata)]
      (do
        (future
          (let [pimped_user (merge user {:_id (:id user)})] ;; FIXME
            (sadesecurity/send-activation-mail-for {:user pimped_user
                                                    :from "lupapiste@solita.fi"
                                                    :service-name "Lupapiste"
                                                    :host-url (sadeclient/uri)})))
        (ok :id (:_id user)))
      (fail :error.create_user))))

(defcommand "add-comment"
  {:parameters [:id :text :target]
   :roles      [:applicant :authority]}
  [{{:keys [text target]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (if (= "draft" (:state application))
        (executed "open-application" command))
      (mongo/update-by-id
        :applications
        (:id application)
        {$set {:modified (:created command)}
         $push {:comments {:text    text
                           :target  target
                           :created (:created command)
                           :user    (security/summary user)}}}))))

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
            full-path    (str "documents.$.body." path)]
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
