(ns lupapalvelu.security
  (:use monger.operators)
  (:use lupapalvelu.log)
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

(defn random-string [length]
  (let [ascii-codes (concat (range 48 58) (range 66 91) (range 97 123))]
    (apply str (repeatedly length #(char (rand-nth ascii-codes))))))

(defn- random-password [] (random-string 40))

(defn create-user [{:keys [email password userid firstname lastname phone address] :or {firstname "" lastname "" password (random-password)} :as user}]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)
        id                (mongo/create-id)]
    (info "Registering new user: %s" (dissoc user :password))
    (mongo/insert
      mongo/users
      {:id         id
       :username   email
       :email      email
       :role       :applicant
       :personId   userid
       :firstName  firstname
       :lastName   lastname
       :phone      phone
       :address    address
       :private    {:salt salt
                    :password hashed-password}})
    (get-user-by-email email)))

(defn get-user-by-email [email]
  (and email (non-private (first (mongo/select mongo/users {"email" email})))))

(defn get-or-create-user-by-email [email]
  (or (get-user-by-email email) (create-user {:email email})))
