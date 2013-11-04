(ns lupapalvelu.user-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [noir.response :as resp]
            [noir.session :as session]
            [noir.core :refer [defpage]]
            [slingshot.slingshot :refer [throw+]]
            [monger.operators :refer :all]
            [sade.util :refer [future*]]
            [sade.env :refer [in-dev]]
            [sade.strings :as s]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand defraw]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.activation :as activation]
            [lupapalvelu.security :as security]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.user :refer [with-user-by-email] :as user]
            [lupapalvelu.token :as token]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.attachment :refer [encode-filename]]))

;;
;; ==============================================================================
;; Getting user and users:
;; ==============================================================================
;;

(defquery user
  {:authenticated true :verified true}
  [{user :user}]
  (ok :user user))

(defquery users
  {:roles [:admin]}
  [{{:keys [role organization]} :data}]
  (ok :users
    (map user/non-private
      (mongo/select :users (merge
                             (when role {:role role})
                             (when organization {:organizations {$in [organization]}}))))))

(defquery authority-users
  {:roles [:authorityAdmin]
   :verified true}
  [{{:keys [organizations]} :user}]
  (let [organization (first organizations)
        users (map user/non-private (mongo/select :users {:organizations {$in [organization]}}))]
    (ok :users users)))

(defquery applicant-users
  {:roles [:admin]
   :verified true}
  [_]
  (let [users (map user/non-private (mongo/select :users {:role "applicant"}))]
    (ok :users users)))

(defquery authority-admin-users
  {:roles [:admin]
   :verified true}
  [_] (ok :users (map user/non-private (mongo/select :users {:role "authorityAdmin"}))))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

(defcommand create-user
  {:parameters [:email :password]
   :roles      [:admin :authorityAdmin]}
  [{params :data caller :user}]
  #_(ok :id (user/create-user caller params))
  (fail "Not implemented correctly!"))

(defcommand create-authority-admin-user
  {:parameters [:firstName :lastName :email :password :organizations]
   :roles      [:admin]
   :verified   true}
  [{{:keys [password] :as data} :data}]
  (when-not (security/valid-password? password) (fail! :error.password-too-short))
  (let [new-user (user/create-authority-admin data)]
    (when-not new-user (fail! :error.create-admin-user))
    (future*
      (let [pimped-user (merge new-user {:_id (:id new-user)})] ;; FIXME
        (activation/send-activation-mail-for pimped-user)))
    (ok :id (:_id new-user))))

(defcommand create-authority-user
  {:parameters [:firstName :lastName :email :password]
   :roles      [:authorityAdmin]
   :verified   true}
  [{{:keys [organizations]} :user data :data}]
  (when-not (security/valid-password? (:password data)) (fail! :error.password-too-short))
  (let [new-user (user/create-authority (merge data {:organizations organizations}))]
    (ok :id (:_id new-user))))

;;
;; ==============================================================================
;; Updating user data:
;; ==============================================================================
;;

(defcommand edit-applicant-user
  {:parameters [:email :enabled]
   :roles      [:admin]
   :verified   true}
  [{{:keys [email enabled]} :data}]
  (user/update-user email {:enabled enabled}))

(defcommand edit-authority-admin-user
  {:parameters [:email :firstName :lastName :enabled :organizations]
   :roles      [:admin]
   :verified   true}
  [{{:keys [email firstName lastName enabled organizations]} :data}]
  (with-user-by-email email
    (user/update-user email {:firstName firstName :lastName lastName :enabled enabled :organizations organizations})))

(defcommand reset-authority-admin-password
  {:parameters [:email :password]
   :roles      [:admin]
   :verified true}
  [{{:keys [email password]} :data}]
  (with-user-by-email email
    (user/change-password email password)))

(defcommand update-authority-user-organisations
  {:parameters [:email :organization]
   :roles      [:authorityAdmin]
   :verified   true}
  [{{:keys [email organization]} :data}]
  (user/update-organizations-of-authority-user email organization))

(defcommand edit-authority-user
  {:parameters [:email :firstName :lastName :enabled]
   :roles      [:authorityAdmin]
   :verified   true}
  [{{:keys [municipality]} :user {:keys [email firstName lastName enabled]} :data}]
  (with-user-by-email (s/lower-case email)
    (if (not= municipality (:municipality user))
      (fail :error.invalid-authority)
      (user/update-user email {:firstName firstName :lastName lastName :enabled enabled}))))

(defcommand reset-authority-password
  {:parameters [:email :password]
   :roles      [:authorityAdmin]
   :verified true}
  [{{:keys [municipality]} :user {:keys [email password]} :data}]
  (with-user-by-email email
    (if-not (= municipality (:municipality user))
      (fail :error.invalid-authority)
      (user/change-password email password))))

(defcommand save-user-info
  {:parameters [:firstName :lastName]
   :authenticated true
   :verified true}
  [{data :data {user-id :id} :user}]
  (mongo/update-by-id
    :users
    user-id
    {$set (select-keys data [:firstName :lastName :street :city :zip :phone
                             :architect :degree :fise
                             :companyName :companyId])})
  (session/put! :user (user/get-user-by-id user-id))
  (ok))

;;
;; ==============================================================================
;; Change and reset password:
;; ==============================================================================
;;

(defcommand change-passwd
  {:parameters [:oldPassword :newPassword]
   :authenticated true
   :verified true}
  [{{:keys [oldPassword newPassword]} :data {user-id :id :as user} :user}]
  (let [user-data (mongo/by-id :users user-id)]
    (if (security/check-password oldPassword (-> user-data :private :password))
      (do
        (debug "Password change: user-id:" user-id)
        (user/change-password (:email user) newPassword)
        (ok))
      (do
        (warn "Password change: failed: old password does not match, user-id:" user-id)
        (fail :mypage.old-password-does-not-match)))))

(defcommand reset-password
  {:parameters    [:email]
   :notified      true
   :authenticated false}
  [{data :data}]
  (let [email (s/lower-case (:email data))]
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
  (let [email (s/lower-case (:email data))]
    (user/change-password email password)
    (infof "password reset performed: email=%s" email)
    (resp/status 200 (resp/json {:ok true}))))

;;
;; ==============================================================================
;; Login:
;; ==============================================================================
;;

;; TODO: count error trys!
(defcommand login
  {:parameters [:username :password] :verified false}
  [{{:keys [username password]} :data}]
  (if-let [user (user/get-user-with-password username password)]
    (do
      (info "login successful, username:" username)
      (session/put! :user user)
      (if-let [application-page (user/applicationpage-for (:role user))]
        (ok :user user :applicationpage application-page)
        (do
          (error "Unknown user role:" (:role user))
          (fail :error.login))))
    (do
      (info "login failed, username:" username)
      (fail :error.login))))

;;
;; ==============================================================================
;; Registering:
;; ==============================================================================
;;

(defcommand register-user
  {:parameters [:stamp :email :password :street :zip :city :phone]
   :verified   true}
  [{{:keys [stamp] :as data} :data}]
  (if-let [vetuma-data (vetuma/get-user stamp)]
    (let [email (-> data :email s/lower-case s/trim)]
      (if (.contains email "@")
        (try
          (infof "Registering new user: %s - details from vetuma: %s" (dissoc data :password) vetuma-data)
          (if-let [user (user/create-user (merge data vetuma-data {:email email}))]
            (do
              (future* (activation/send-activation-mail-for user))
              (vetuma/consume-user stamp)
              (ok :id (:_id user)))
            (fail :error.create-user))
          (catch IllegalArgumentException e
            (fail (keyword (.getMessage e)))))
        (fail :error.email)))
    (fail :error.create-user)))

;;
;; ==============================================================================
;; User attachments:
;; ==============================================================================
;;

(defquery user-attachments
  {:authenticated true}
  [{user :user}]
  (ok :attachments (:attachments user)))

(defpage [:post "/api/upload/user-attachment"] {[{:keys [tempfile filename content-type size]}] :files attachment-type :attachmentType}
  (let [user              (user/current-user)
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
    (user/refresh-user!)

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
  (user/refresh-user!)
  (mongo/delete-file {:id attachment-id :metadata.user-id (:id user)})
  (ok))

;;
;; ==============================================================================
;; Development utils:
;; ==============================================================================
;;


; FIXME: generalize
(in-dev

  (defcommand create-apikey
    {:parameters [:username :password]}
    [{{:keys [username password]} :data}]
    (let [apikey (security/random-password)
          user (user/get-user-with-password username password)]
      (when-not user (fail! :error.not-found))
      (mongo/update
        :users
        {:_id (:id user)}
        {$set {"private.apikey" apikey}})
      (ok :apikey apikey)))

  (require '[lupapalvelu.fixture])

  (defquery fixtures {} [_]
    (ok :fixtures (keys @lupapalvelu.fixture/fixtures)))

  (defquery apply-fixture
    {:parameters [:name]}
    [{{:keys [name]} :data}]
    (if (lupapalvelu.fixture/exists? name)
      (do
        (lupapalvelu.fixture/apply-fixture name)
        (ok))
      (do
        (warnf "fixture '%s' not found" name)
        (fail :error.fixture-not-found))))

  (defquery activate-user-by-email
    {:parameters [:email]}
    [{{:keys [email]} :data}]
    (if-let [user (activation/activate-account-by-email email)]
      (ok)
      (fail :cant_activate_user_by_email)))

  (defquery activations {} [query]
    (ok :activations (activation/activations))))

