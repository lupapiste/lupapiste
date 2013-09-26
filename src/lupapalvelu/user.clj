(ns lupapalvelu.user
  (:require [taoensso.timbre :as timbre :refer [debug debugf info warn]]
            [monger.operators :refer :all]
            [noir.request :as request]
            [noir.session :as session]
            [camel-snake-kebab :as kebab]
            [sade.strings :as s]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.core :refer [fail fail!]]))

;;
;; Securing data:
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

(defn applicationpage-for [role]
  (kebab/->kebab-case role))


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

(defn refresh-user!
  "Loads user information from db and saves it to session. Call this after you make changes to user information."
  []
  (when-let [user (load-current-user)]
    (debug "user session refresh successful, username:" (:username user))
    (session/put! :user user)))

(defn user-in-role [user role & params]
  (merge (apply hash-map params) (assoc (summary user) :role role)))




(defn login
  "returns non-private information of enabled user with the username and password"
  [username password]
  (when-let [user (load-user username)]
    (and
      (:enabled user)
      (security/check-password password (-> user :private :password))
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
  (let [apikey (security/random-password)
        result (mongo/update :users {:email (s/lower-case email)} {$set {:private.apikey apikey}})]
    (when result
      apikey)))

(defn change-password [email password]
  (let [salt              (security/dispense-salt)
        hashed-password   (security/get-hash password salt)]
    (mongo/update :users {:email (s/lower-case email)} {$set {:private.salt     salt
                                                              :private.password hashed-password}})))

(def required-user-keys [:email :id :role])
(def user-keys          [:id :role :firstName :lastName :personId :phone :city :street :zip :enabled :organizations])
(def user-defaults      {:firstName "" :lastName "" :enabled false})
(def known-user-roles   #{:admin :authority :authorityAdmin :applicant :dummy})

(defn create-user-entity [{:keys [email password] :as user-data}]
  (when-not (every? identity (map user-data required-user-keys)) (fail! :error.missing-required-key))
  (let [email    (s/lower-case email)
        private  (when password
                   (let [salt (security/dispense-salt)]
                     {:salt     salt
                      :password (security/get-hash password salt)}))]
    (merge
      user-defaults
      (select-keys user-data user-keys)
      {:username email
       :email    email
       :private  private})))



(defn- create-any-user [user-data]
  (let [id           (mongo/create-id)
        new-user     (create-user-entity (assoc user-data :id id))
        old-user     (get-user-by-email (:email user-data))]
    (info "register user:" (dissoc new-user :private))
    (try
      (if (= "dummy" (:role old-user))
        (do
          (info "rewriting over dummy user:" (:id old-user) (dissoc new-user :private :id))
          (mongo/update-by-id :users (:id old-user) (assoc new-user :id (:id old-user))))
        (do
          (info "creating new user" (dissoc new-user :private))
          (mongo/insert :users new-user)))
      (get-user-by-email (:email new-user))
      (catch com.mongodb.MongoException$DuplicateKey e
        (warn e)
        (throw (IllegalArgumentException.
                 (condp re-find (.getMessage e)
                   #"\.personId\."  "error.duplicate-person-id"
                   #"\.email\."     "error.duplicate-email"
                   #"\.username\."  "error.duplicate-email"
                   "error.create-user")))))))

(defn create-authority [user]
  (create-any-user (merge user {:role :authority :enabled true})))

(defn create-authority-admin [user]
  (create-any-user (merge user {:role :authorityAdmin :enabled true})))

(defn create-user [user]
  (create-any-user (merge user {:role :applicant :enabled true})))


(defn update-user [email data]
  (mongo/update :users {:email (s/lower-case email)} {$set data}))

(defn update-organizations-of-authority-user [email new-organization]
  (let [old-orgs (:organizations (get-user-by-email email))]
    (when (every? #(not (= % new-organization)) old-orgs)
      (update-user email {:organizations (merge old-orgs new-organization)}))))

(defn change-password [email password]
  (let [salt              (security/dispense-salt)
        hashed-password   (security/get-hash password salt)]
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







(defn with-user [email function]
  (if (nil? email)
    (fail :error.user-not-found)
    (if-let [user (get-user-by-email email)]
      (function user)
      (do
        (debugf "user '%s' not found with email" email)
        (fail :error.user-not-found)))))












