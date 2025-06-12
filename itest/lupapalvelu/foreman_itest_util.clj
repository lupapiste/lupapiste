(ns lupapalvelu.foreman-itest-util
  (:require [lupapalvelu.itest-util :refer [command]]
            [sade.strings :as ss]))

(defn create-and-invite-foreman-command
  [apikey application-id role email user]
  (let [email?   (ss/not-blank? email)
        user-id? (ss/not-blank? user)]
    (cond
      email?   (command apikey :create-and-invite-foreman-by-email
                        {:id           application-id
                         :foremanRole  role
                         :foremanEmail email})
      user-id? (command apikey :create-and-invite-foreman-by-id
                        {:id          application-id
                         :foremanRole role
                         :foremanUser user})
      :else    (throw (ex-info "Needs either email or user" {})))))
