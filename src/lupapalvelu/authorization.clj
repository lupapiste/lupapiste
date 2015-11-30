(ns lupapalvelu.authorization
  (:require [lupapalvelu.user :as user]))

;;
;; Auth utils
;;

(defn has-auth? [{auth :auth} user-id]
  (or (some (partial = user-id) (map :id auth)) false))

(defn create-invite-auth [inviter invited application-id role timestamp & [text document-name document-id path]]
  (let [invite {:application  application-id
                :text         text
                :path         path
                :documentName document-name
                :documentId   document-id
                :created      timestamp
                :email        (:email invited)
                :role         role
                :user         (user/summary invited)
                :inviter      (user/summary inviter)}]
    (assoc (user/user-in-role invited :reader) :invite invite)))
