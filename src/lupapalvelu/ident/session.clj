(ns lupapalvelu.ident.session
  (:require [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [monger.operators :refer :all]
            [noir.response :as response]))

;;
;; public local api
;;

(defn get-session [session-id]
  (last (mongo/select :vetuma {:sessionid session-id, :user.stamp {$exists true}} [:user] {:created-at 1})))

(defn- get-data [stamp]
  (mongo/select-one :vetuma {:user.stamp stamp}))

(defn get-user [stamp]
  (:user (get-data stamp)))

(defn consume-user [stamp]
  (when-let [user (get-data stamp)]
    (mongo/remove-many :vetuma {:_id (:id user)})
    (:user user)))

(defn delete-user [session]
  (mongo/remove :vetuma (:id session)))
