(ns lupapalvelu.ident.session
  (:require [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [monger.operators :refer :all]
            [noir.response :as response]))

;;
;; public local api
;;

(defn collection-name []
  (cond
    (env/feature? :dummy-ident) :ident
    (env/feature? :suomifi-ident) :ident
    :else :vetuma))

(defn get-session [session-id]
  (last (mongo/select (collection-name) {:sessionid session-id, :user.stamp {$exists true}} [:user] {:created-at 1})))

(defn- get-data [stamp]
  (mongo/select-one (collection-name) {:user.stamp stamp}))

(defn get-user [stamp]
  (:user (get-data stamp)))

(defn consume-user [stamp]
  (when-let [user (get-data stamp)]
    (mongo/remove-many (collection-name) {:_id (:id user)})
    (:user user)))

(defn delete-user [session]
  (mongo/remove (collection-name) (:id session)))
