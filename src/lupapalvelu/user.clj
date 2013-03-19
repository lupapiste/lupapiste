(ns lupapalvelu.user
  (:use [monger.operators]
        [lupapalvelu.core]
        [clojure.tools.logging])
  (:require [lupapalvelu.mongo :as mongo]
            [camel-snake-kebab :as kebab]
            [lupapalvelu.security :as security]
            [lupapalvelu.util :as util]
            [noir.session :as session]))

(defn applicationpage-for [role]
  (kebab/->kebab-case role))

(defcommand "login"
  {:parameters [:username :password]}
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

(defcommand "change-passwd"
  {:parameters [:oldPassword :newPassword]
   :authenticated true}
  [{{:keys [oldPassword newPassword]} :data user :user}]
  (let [user-id (:id user)
        user-data (mongo/by-id :users user-id)]
    (if (security/check-password oldPassword (-> user-data :private :password))
      (do
        (debug "Password change: user-id:" user-id)
        (security/change-password (:email user) newPassword)
        (ok))
      (do
        (warn "Password change: failed: old password does not match, user-id:" user-id)
        (fail :old-password-does-not-match)))))

(defquery "user" {:authenticated true} [{user :user}] (ok :user user))

(defcommand "save-user-info"
  {:parameters [:firstName :lastName :street :city :zip :phone]
   :authenticated true}
  [{data :data user :user}]
  (let [user-id (:id user)]
    (mongo/update-by-id
      :users
      user-id
      {$set (util/sub-map data [:firstName :lastName :street :city :zip :phone])})
    (session/put! :user (security/get-non-private-userinfo user-id))
    (ok)))
