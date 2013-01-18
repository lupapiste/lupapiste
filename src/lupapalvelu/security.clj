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
  "returns non-private information of first enabled user with the username and password"
  [username password]
  (if-let [user (mongo/select-one mongo/users {:username username})]
    (and
      (:enabled user)
      (check-password password (-> user :private :password))
      (non-private user))))

(defn login-with-apikey
  "returns non-private information of first enabled user with the apikey"
  [apikey]
  (when apikey
    (let [user (non-private (first (mongo/select mongo/users {:private.apikey apikey})))]
      (when (:enabled user) user))))

(defn get-user-by-email [email]
  (and email (non-private (first (mongo/select mongo/users {:email email})))))

(defn- random-password []
  (let [ascii-codes (concat (range 48 58) (range 66 91) (range 97 123))]
    (apply str (repeatedly 40 #(char (rand-nth ascii-codes))))))

(defn- create-any-user [{:keys [email password userid role firstname lastname phone address enabled authority] :or {firstname "" lastname "" password (random-password) role :dummy enabled false} :as user}]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)
        id                (mongo/create-id)
        old-user          (get-user-by-email email)
        new-user          {:id         id
                           :username   email
                           :email      email
                           :role       role
                           :personId   userid
                           :firstName  firstname
                           :lastName   lastname
                           :phone      phone
                           :address    address
                           :authority  authority
                           :enabled    enabled
                           :private    {:salt salt
                                        :password hashed-password}}]
    (info "register user: %s" (dissoc user :password))
    (if (= "dummy" (:role old-user))
      (do
        (info "rewriting over dummy user: %s" (:id old-user))
        (mongo/update-by-id mongo/users (:id old-user) new-user))
      (do
        (info "creating new user")
        (mongo/insert mongo/users new-user)))
    (get-user-by-email email)))

(defn create-user [user]
  (create-any-user (merge user {:role :applicant :enabled false})))

(defn create-authority [user]
  (create-any-user (merge user {:role :authority :enabled true})))

(defn update-user [email data]
  (mongo/update mongo/users {:email email} {$set data}))

(defn change-password [email password]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)]
    (mongo/update mongo/users {:email email} {$set {:private.salt  salt
                                                    :private.password hashed-password}})))

(defn get-or-create-user-by-email [email]
  (or
    (get-user-by-email email)
    (create-user {:email email})))
