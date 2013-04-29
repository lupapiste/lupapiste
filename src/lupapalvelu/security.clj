(ns lupapalvelu.security
  (:use [monger.operators]
        [clojure.tools.logging])
  (:require [lupapalvelu.mongo :as mongo]
            [sade.util :as util]
            [sade.env :as env])
  (:import [org.mindrot.jbcrypt BCrypt]
           [com.mongodb MongoException MongoException$DuplicateKey]))

(defn non-private [map] (dissoc map :private))

(defn get-hash [password salt] (BCrypt/hashpw password salt))
(defn dispense-salt ([] (dispense-salt 10)) ([n] (BCrypt/gensalt n)))
(defn check-password [candidate hashed] (BCrypt/checkpw candidate hashed))
(defn create-apikey [] (apply str (take 40 (repeatedly #(rand-int 10)))))

(defn summary
  "returns common information about the user or nil"
  [user]
  (when user
    (select-keys user [:id :username :firstName :lastName :role])))

(defn login
  "returns non-private information of enabled user with the username and password"
  [username password]
  (when-let [user (mongo/select-one :users {:username username})]
    (and
      (:enabled user)
      (check-password password (-> user :private :password))
      (non-private user))))

(defn login-with-apikey
  "returns non-private information of enabled user with the apikey"
  [apikey]
  (when apikey
    (when-let [user (non-private (mongo/select-one :users {:private.apikey apikey}))]
      (when (:enabled user) user))))

(defn get-non-private-userinfo [user-id]
  (non-private (mongo/select-one :users {:_id user-id})))

(defn get-user-by-email [email]
  (and email (non-private (mongo/select-one :users {:email email}))))

(defn- random-password []
  (let [ascii-codes (concat (range 48 58) (range 66 91) (range 97 123))]
    (apply str (repeatedly 40 #(char (rand-nth ascii-codes))))))

; length should match the length in util.js
(defn valid-password? [password]
  (>= (count password) (get-in env/config [:password :minlength])))

(defn- create-use-entity [email password userid role firstname lastname phone city street zip enabled organizations]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)
        new-user-base     {:username     email
                           :email        email
                           :role         role
                           :firstName    firstname
                           :lastName     lastname
                           :phone        phone
                           :city         city
                           :street       street
                           :zip          zip
                           :organizations organizations
                           :enabled      enabled
                           :private      {:salt salt :password hashed-password}}]
    (if userid (assoc new-user-base :personId userid) new-user-base)))

(defn- create-any-user [{:keys [email password userid role firstname lastname phone city street zip enabled organizations]
                         :or {firstname "" lastname "" password (random-password) role :dummy enabled false} :as user}]
  (let [id                (mongo/create-id)
        old-user          (get-user-by-email email)
        new-user-base     (create-use-entity email password userid role firstname lastname phone city street zip enabled organizations)
        new-user          (assoc new-user-base :id id)]
    (info "register user:" (dissoc user :password))
    (try
      (if (= "dummy" (:role old-user))
        (do
          (info "rewriting over dummy user:" (:id old-user))
          (mongo/update-by-id :users (:id old-user) (assoc new-user :id (:id old-user))))
        (do
          (info "creating new user")
          (mongo/insert :users new-user)))
      (catch MongoException$DuplicateKey e
        (warn e)
        (let [error-code  (condp re-matches (.getMessage e)
                            #".+personId.+"  "error.duplicate-person-id"
                            #".+email.+"     "error.duplicate-email"
                            #".+username.+"  "error.duplicate-email"
                            #".*"            "error.create-user")]
          (throw (IllegalArgumentException. error-code)))))
    (get-user-by-email email)))

(defn create-user [user]
  (create-any-user (merge user {:role :applicant :enabled false})))

(defn create-authority [user]
  (create-any-user (merge user {:role :authority :enabled true})))

(defn update-user [email data]
  (mongo/update :users {:email email} {$set data}))

(defn change-password [email password]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)]
    (mongo/update :users {:email email} {$set {:private.salt  salt
                                               :private.password hashed-password}})))

(defn get-or-create-user-by-email [email]
  (or
    (get-user-by-email email)
    (create-any-user {:email email})))

(defn authority? [{role :role}]
  (= :authority (keyword role)))

(defn applicant? [{role :role}]
  (= :applicant (keyword role)))
