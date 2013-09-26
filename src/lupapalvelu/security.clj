(ns lupapalvelu.security
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as s]
            [sade.env :as env]
            [noir.request :as request]
            [noir.session :as session])
  (:import [org.mindrot.jbcrypt BCrypt]
           [com.mongodb MongoException MongoException$DuplicateKey]))

;;
;; Password generation and checking:
;;

(def ^:private token-chars (concat (range (int \0) (inc (int \9)))
                                   (range (int \A) (inc (int \Z)))
                                   (range (int \a) (inc (int \z)))))

(defn random-password
  ([]
    (random-password 40))
  ([len]
    (apply str (repeatedly len (comp char (partial rand-nth token-chars))))))

(defn valid-password? [password]
    (>= (count password) (env/value :password :minlength)))  ; length should match the length in util.js

(defn get-hash [password salt]
  (BCrypt/hashpw password salt))

(defn dispense-salt []
  (BCrypt/gensalt (or (env/value :salt-strength) 10)))

(defn check-password [candidate hashed]
  (BCrypt/checkpw candidate hashed))

;;
;; Data privatization:
;;

(defn non-private
  "Returns user without private details."
  [user] (dissoc user :private))

(defn summary
  "Returns common information about the user or nil"
  [user]
  (when user
    (select-keys user [:id :username :firstName :lastName :role])))

;;
;; User management:
;;

(defn current-user
  "fetches the current user from session"
  ([] (current-user (request/ring-request)))
  ([request] (request :user)))

(defn- load-user [username]
  (when username
    (mongo/select-one :users {:username (s/lower-case username)})))

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
  (when user-id
    (non-private (mongo/select-one :users {:_id user-id}))))

(defn get-user-by-email [email]
  (when email
    (non-private (mongo/select-one :users {:email (s/lower-case email)}))))

(defn create-apikey [email]
  (let [apikey (random-password)
        result (mongo/update :users {:email (s/lower-case email)} {$set {:private.apikey apikey}})]
    (when result
      apikey)))

(defn change-password [email password]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)]
    (mongo/update :users {:email (s/lower-case email)} {$set {:private.salt     salt
                                                              :private.password hashed-password}})))

(defn create-user-entity [email password person-id role firstname lastname phone city street zip enabled organizations]
  (let [email             (s/lower-case email)
        salt              (dispense-salt)
        hashed-password   (get-hash password salt)]
    (-> {:username      email
         :email         email
         :role          role
         :firstName     firstname
         :lastName      lastname
         :personId      person-id
         :phone         phone
         :city          city
         :street        street
         :zip           zip
         :enabled       enabled
         :organizations organizations
         :private       {:salt salt :password hashed-password}})))

(def user-defaults {:firstName     ""
                    :lastName      ""
                    :personId      ""
                    :phone         ""
                    :city          ""
                    :street        ""
                    :zip           ""
                    :enabled       false
                    :organizations []})

(def required-fields #{:username :email})

(defn- create-any-user [{:keys [email password userid role firstname lastname phone city street zip enabled organizations]
                         :or {firstname "" lastname "" password (random-password) role :dummy enabled false} :as user}]
  (let [email             (s/lower-case email)
        id                (mongo/create-id)
        old-user          (get-user-by-email email)
        new-user-base     (create-user-entity email password userid role firstname lastname phone city street zip enabled organizations)
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

(defn create-user [caller params]
  (let [params (merge user-defaults params)])
  (create-any-user (merge params {:role :applicant :enabled false})))

(defn create-authority [user]
  (create-any-user (merge user {:role :authority :enabled true})))

(defn create-authority-admin [user]
  (create-any-user (merge user {:role :authorityAdmin :enabled true})))

(defn update-user [email data]
  (mongo/update :users {:email (s/lower-case email)} {$set data}))

(defn update-organizations-of-authority-user [email new-organization]
  (let [old-orgs (:organizations (get-user-by-email email))]
    (when (every? #(not (= % new-organization)) old-orgs)
      (update-user email {:organizations (merge old-orgs new-organization)}))))

(defn change-password [email password]
  (let [salt              (dispense-salt)
        hashed-password   (get-hash password salt)]
    (mongo/update :users {:email (s/lower-case email)} {$set {:private.salt  salt
                                                            :private.password hashed-password}})))

(defn get-or-create-user-by-email [email]
  (let [email (s/lower-case email)]
    (or
      (get-user-by-email email)
      (create-any-user {:email email}))))

(defn authority? [{role :role}]
  (= :authority (keyword role)))

(defn applicant? [{role :role}]
  (= :applicant (keyword role)))

(defn same-user? [{id1 :id :as user1} {id2 :id :as user2}]
  (= id1 id2))
