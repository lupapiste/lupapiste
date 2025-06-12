(ns lupapalvelu.ident.session
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

;;
;; public local api
;;

(defn get-session [session-id]
  (last (mongo/select :vetuma {:sessionid session-id, :user.stamp {$exists true}} [:user] {:created-at 1})))

(defn- get-data [stamp]
  (mongo/select-one :vetuma {:user.stamp stamp}))  ; Functions relying on stamp can be removed after old VETUMA is removed

(defn get-user [stamp]
  (:user (get-data stamp)))

(defn get-by-trid [trid]
  (mongo/select-one :vetuma {:trid trid}))

(defn consume-user [stamp]
  (when-let [user (get-data stamp)]
    (mongo/remove-many :vetuma {:_id (:id user)})
    (:user user)))

(defn delete-user [session]
  (mongo/remove :vetuma (:id session)))
