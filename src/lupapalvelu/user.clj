(ns lupapalvelu.user
  (:use [monger.operators]
        [lupapalvelu.core])
  (:require [taoensso.timbre :as timbre :refer (trace debug info infof warn warnf error fatal)]
            [slingshot.slingshot :refer [throw+]]
            [lupapalvelu.mongo :as mongo]
            [camel-snake-kebab :as kebab]
            [lupapalvelu.security :as security]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.mime :as mime]
            [sade.security :as sadesecurity]
            [sade.util :refer [lower-case trim] :as util]
            [sade.env :as env]
            [sade.strings :as ss]
            [noir.session :as session]
            [noir.core :refer [defpage]]
            [lupapalvelu.token :as token]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.attachment :refer [encode-filename]]
            [noir.response :as resp]))

(defn applicationpage-for [role]
  (kebab/->kebab-case role))

;; TODO: count error trys!
(defcommand "login"
  {:parameters [:username :password] :verified false}
  [{{:keys [username password]} :data}]
  (if-let [user (security/login (-> username lower-case trim) password)]
    (do
      (info "login successful, username:" username)
      (session/put! :user user)
      (if-let [application-page (applicationpage-for (:role user))]
        (ok :user user :applicationpage application-page)
        (do
          (error "Unknown user role:" (:role user))
          (fail :error.login))))
    (do
      (info "login failed, username:" username)
      (fail :error.login))))

(defn refresh-user!
  "Loads user information from db and saves it to session. Call this after you make changes to user information."
  []
  (when-let [user (security/load-current-user)]
    (debug "user session refresh successful, username:" (:username user))
    (session/put! :user user)))

(defcommand "register-user"
  {:parameters [:stamp :email :password :street :zip :city :phone]
   :verified   true}
  [{{:keys [stamp] :as data} :data}]
  (if-let [vetuma-data (vetuma/get-user stamp)]
    (let [email (-> data :email lower-case trim)]
      (if (.contains email "@")
        (try
          (infof "Registering new user: %s - details from vetuma: %s" (dissoc data :password) vetuma-data)
          (if-let [user (security/create-user (merge data vetuma-data {:email email}))]
            (do
              (future (sadesecurity/send-activation-mail-for user))
              (vetuma/consume-user stamp)
              (ok :id (:_id user)))
            (fail :error.create-user))
          (catch IllegalArgumentException e
            (fail (keyword (.getMessage e)))))
        (fail :error.email)))
    (fail :error.create-user)))

(defcommand "change-passwd"
  {:parameters [:oldPassword :newPassword]
   :authenticated true
   :verified true}
  [{{:keys [oldPassword newPassword]} :data {user-id :id :as user} :user}]
  (let [user-data (mongo/by-id :users user-id)]
    (if (security/check-password oldPassword (-> user-data :private :password))
      (do
        (debug "Password change: user-id:" user-id)
        (security/change-password (:email user) newPassword)
        (ok))
      (do
        (warn "Password change: failed: old password does not match, user-id:" user-id)
        (fail :mypage.old-password-does-not-match)))))

(defcommand "reset-password"
  {:parameters    [:email]
   :notified      true
   :authenticated false}
  [{data :data}]
  (let [email (lower-case (:email data))]
    (infof "Password resert request: email=%s" email)
    (if (mongo/select-one :users {:email email :enabled true})
      (let [token (token/make-token :password-reset {:email email})]
        (infof "password reset request: email=%s, token=%s" email token)
        (notifications/send-password-reset-email! email token)
        (ok))
      (do
        (warnf "password reset request: unknown email: email=%s" email)
        (fail :email-not-found)))))

(defmethod token/handle-token :password-reset [{data :data} {password :password}]
  (let [email (lower-case (:email data))]
    (security/change-password email password)
    (infof "password reset performed: email=%s" email)
    (resp/status 200 (resp/json {:ok true}))))

(defquery "user"
  {:authenticated true :verified true}
  [{user :user}]
  (ok :user user))

(defcommand "save-user-info"
  {:parameters [:firstName :lastName]
   :authenticated true
   :verified true}
  [{data :data {user-id :id} :user}]
  (mongo/update-by-id
    :users
    user-id
    {$set (select-keys data [:firstName :lastName :street :city :zip :phone
                             :architect :degree :experience :fise :qualification
                             :companyName :companyId :companyStreet :companyZip :companyCity])})
  (session/put! :user (security/get-non-private-userinfo user-id))
  (ok))

(defquery user-attachments
  {:authenticated true}
  [{user :user}]
  (ok :attachments (:attachments user)))

(defpage [:post "/api/upload/user-attachment"] {[{:keys [tempfile filename content-type size]}] :files attachment-type :attachmentType}
  (let [user              (security/current-user)
        filename          (mime/sanitize-filename filename)
        attachment-type   (keyword attachment-type)
        attachment-id     (mongo/create-id)
        file-info         {:attachment-type  attachment-type
                           :attachment-id    attachment-id
                           :filename         filename
                           :content-type     content-type
                           :size             size
                           :created          (now)}]
    
    (info "upload/user-attachment" (:username user) ":" attachment-type "/" filename content-type size "id=" attachment-id)

    (when-not (#{:examination :proficiency :cv} attachment-type) (fail! "unknown attachment type" :attachment-type attachment-type))
    (when-not (mime/allowed-file? filename) (fail! "unsupported file type" :filename filename))
    
    (mongo/upload attachment-id filename content-type tempfile :user-id (:id user))
    (mongo/update-by-id :users (:id user) {$set {(str "attachments." attachment-id) file-info}})
    (refresh-user!)

    (->> (assoc file-info :ok true) 
      (resp/json)
      (resp/content-type "text/plain") ; IE is fucking stupid: must use content type text/plain, or else IE prompts to download response.  
      (resp/status 200))))

(defraw download-user-attachment
  {:parameters [attachment-id]}
  [{user :user}]
  (when-not user (throw+ {:status 401 :body "forbidden"}))
  (if-let [attachment (mongo/download-find {:id attachment-id :metadata.user-id (:id user)})]
    {:status 200
     :body ((:content attachment))
     :headers {"Content-Type" (:content-type attachment)
               "Content-Length" (str (:content-length attachment))
               "Content-Disposition" (format "attachment;filename=\"%s\"" (encode-filename (:file-name attachment)))}}
    {:status 404
     :body (str "can't file attachment: id=" attachment-id)}))

(defcommand remove-user-attachment
  {:parameters [attachment-id]}
  [{user :user}]
  (info "Removing user attachment: attachment-id:" attachment-id)
  (mongo/update-by-id :users (:id user) {$unset {(str "attachments." attachment-id) nil}})
  (refresh-user!)
  (mongo/delete-file {:id attachment-id :metadata.user-id (:id user)})
  (ok))

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
