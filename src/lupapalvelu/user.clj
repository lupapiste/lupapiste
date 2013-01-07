(ns lupapalvelu.user
  (:use [monger.operators]
        [lupapalvelu.core]
        [lupapalvelu.log])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]))

(defcommand "change-passwd"
  {:parameters [:old-pw :new-pw]
   :roles      [:applicant :authority]}
  [{{:keys [old-pw new-pw]} :data user :user}]
  (let [user-id (:id user)
        user-data (mongo/by-id mongo/users user-id)
        old-pwd-matches (security/check-password old-pw (-> user-data :private :password))]
    (if old-pwd-matches
      (do
        (debug "Password change: user-id=%s" user-id)
        (security/change-password (:email user) new-pw)
        (ok))
      (do
        (warn "Password change: failed: old password does not match: user-id=%s" user-id)
        (fail :old-password-does-not-match)))))
