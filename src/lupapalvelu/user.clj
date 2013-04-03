(ns lupapalvelu.user
  (:use [monger.operators]
        [lupapalvelu.core]
        [lupapalvelu.i18n :only [*lang*]]
        [clojure.tools.logging])
  (:require [lupapalvelu.mongo :as mongo]
            [camel-snake-kebab :as kebab]
            [lupapalvelu.security :as security]
            [lupapalvelu.vetuma :as vetuma]
            [sade.security :as sadesecurity]
            [sade.util :as util]
            [noir.session :as session]
            [lupapalvelu.token :as token]
            [lupapalvelu.notifications :as notifications]
            [noir.response :as resp]))

(defn applicationpage-for [role]
  (kebab/->kebab-case role))

;; TODO: count error trys!
(defcommand "login"
  {:parameters [:username :password] :verified false}
  [{{:keys [username password]} :data}]
  (if-let [user (security/login username password)]
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

(defcommand "register-user"
  {:parameters [:stamp :email :password :street :zip :city :phone]
   :verified   true}
  [{{:keys [stamp] :as data} :data}]
  (if-let [vetuma-data (vetuma/get-user stamp)]
    (do
      (infof "Registering new user: %s - details from vetuma: %s" (dissoc data :password) vetuma-data)
      (try
        (if-let [user (security/create-user (merge data vetuma-data))]
          (do
            (future (sadesecurity/send-activation-mail-for user))
            (vetuma/consume-user stamp)
            (ok :id (:_id user)))
          (fail :error.create-user))
        (catch IllegalArgumentException e
          (fail (keyword (.getMessage e))))))
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
  {:parameters [:email]}
  [{{email :email} :data}]
  (infof "Password resert request: email=%s" email)
  (if-let [user (mongo/select-one :users {:email email :enabled true})]
    (let [token (token/make-token :password-reset {:user-id (:id user)})]
      (infof "password reset request: email=%s, id=%s, token=%s" email (:id user) token)
      (if (notifications/send-password-reset-email email token)
        (ok)
        (fail :email-send-failed))) 
    (do
      (warnf "password reset request: unknown email: email=%s" email)
      (fail :email-not-found))))

(defmethod token/handle-token :password-reset [token-data params]
  (println "PASSWORD-RESET:" (:password params) (:_id token-data))
  ; FIXME: change password
  (resp/status 200 (resp/json {:ok true})))

(defquery "user"
  {:authenticated true :verified true}
  [{user :user}]
  (ok :user user))

(defcommand "save-user-info"
  {:parameters [:firstName :lastName :street :city :zip :phone]
   :authenticated true
   :verified true}
  [{data :data {user-id :id} :user}]
  (mongo/update-by-id
    :users
    user-id
    {$set (select-keys data [:firstName :lastName :street :city :zip :phone])})
  (session/put! :user (security/get-non-private-userinfo user-id))
  (ok))
