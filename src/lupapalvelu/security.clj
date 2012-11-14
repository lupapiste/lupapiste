(ns lupapalvelu.security
  (:use monger.operators)
  (:require [lupapalvelu.mongo :as mongo])
  (:import [org.mindrot.jbcrypt BCrypt]))

(defn non-private [map]
  (dissoc map :private))

(defn get-hash [password salt] (BCrypt/hashpw password salt))
(defn dispense-salt ([] (dispense-salt 10)) ([n] (BCrypt/gensalt n)))
(defn check-password [candidate hashed] (BCrypt/checkpw candidate hashed))
(defn create-apikey [] (apply str (take 40 (repeatedly #(rand-int 10)))))

(defn summary
  "returns common information about the user or nil"
  [user]
  (and user {:id        (:id user)
             :username  (:username user)
             :firstName (:firstName user)
             :lastName  (:lastName user)
             :role      (:role user)}))

(defn login
  "returns non-private information of first user with the username and password"
  [username password]
  (if-let [user (mongo/select-one mongo/users {:username username})]
    (if (check-password password (-> user :private :password))
      (non-private user))))

(defn login-with-apikey
  "returns non-private information of first user with the apikey"
  [apikey]
  (and apikey (non-private (first (mongo/select mongo/users {"private.apikey" apikey})))))

(defn get-user-by-email [email]
  (and email (non-private (first (mongo/select mongo/users {"email" email})))))
