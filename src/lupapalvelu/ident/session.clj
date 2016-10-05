(ns lupapalvelu.ident.session
  (:require [lupapalvelu.mongo :as mongo]))

;;
;; public local api
;;

(defn- get-data [stamp]
  (mongo/select-one :vetuma {:user.stamp stamp}))

(defn get-user [stamp]
  (:user (get-data stamp)))

(defn consume-user [stamp]
  (when-let [user (get-data stamp)]
    (mongo/remove-many :vetuma {:_id (:id user)})
    (:user user)))
