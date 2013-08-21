(ns lupapalvelu.security
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal)]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.env :as env]
            [noir.request :as request]
            [noir.session :as session])
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

(defn current-user
  "fetches the current user from session"
  ([] (current-user (request/ring-request)))
  ([request] (request :user)))

(defn- load-user [username]
  (mongo/select-one :users {:username username}))

(defn load-current-user
  "fetch the current user from db"
  []
  (when-let [user (load-user (:username (current-user)))]
    (non-private user)))

(defn login
  "returns non-private information of enabled user with the username and password"
  [username password]
  (when-let [user (load-user username)]
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
  (and email (non-private (mongo/select-one :users {:email (util/lower-case email)}))))

(defn- random-password []
  (let [ascii-codes (concat (range 48 58) (range 66 91) (range 97 123))]
    (apply str (repeatedly 40 #(char (rand-nth ascii-codes))))))

; length should match the length in util.js
(defn valid-password? [password]
  (>= (count password) (env/value :password :minlength)))

(defn create-use-entity [email password userid role firstname lastname phone city street zip enabled organizations]
  (let [email             (util/lower-case email)
        salt              (dispense-salt)
        hashed-password   (get-hash password salt)]
    (-> {:username     email
         :email        email
         :role         role
         :firstName    firstname
         :lastName     lastname
         :phone        phone
         :city         city
         :street       street
         :zip          zip
         :enabled      enabled
         :private      {:salt salt :password hashed-password}}
      (#(if userid (assoc % :personId userid) %))
      (#(if (#{:authority :authorityAdmin} (keyword role)) (assoc % :organizations organizations) %)))))

(defn- create-any-user [{:keys [email password userid role firstname lastname phone city street zip enabled organizations]
                         :or {firstname "" lastname "" password (random-password) role :dummy enabled false} :as user}]
  (let [email             (util/lower-case email)
        id                (mongo/create-id)
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

(defn create-authority-admin [user]
  (create-any-user (merge user {:role :authorityAdmin :enabled true})))

(defn update-user [email data]
  (mongo/update :users {:email (util/lower-case email)} {$set data}))

(defn change-password [email password]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)]
    (mongo/update :users {:email (util/lower-case email)} {$set {:private.salt  salt
                                                            :private.password hashed-password}})))

(defn get-or-create-user-by-email [email]
  (let [email (util/lower-case email)]
    (or
      (get-user-by-email email)
      (create-any-user {:email email}))))

(defn authority? [{role :role}]
  (= :authority (keyword role)))

(defn applicant? [{role :role}]
  (= :applicant (keyword role)))

(defn same-user? [{id1 :id :as user1} {id2 :id :as user2}]
  (= id1 id2))
