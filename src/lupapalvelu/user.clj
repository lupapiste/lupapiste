(ns lupapalvelu.user
  (:require [taoensso.timbre :as timbre :refer [debug debugf info warn warnf]]
            [monger.operators :refer :all]
            [noir.request :as request]
            [noir.session :as session]
            [camel-snake-kebab :as kebab]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.core :refer [fail fail!]]))

;;
;; ==============================================================================
;; Utils:
;; ==============================================================================
;;

(defn non-private
  "Returns user without private details."
  [user]
  (dissoc user :private))

(defn summary
  "Returns common information about the user or nil"
  [user]
  (when user
    (select-keys user [:id :username :firstName :lastName :role])))

(def authority? (fn-> :role keyword (= :authority)))
(def applicant? (fn-> :role keyword (= :applicant)))

(defn same-user? [{id1 :id} {id2 :id}]
  (= id1 id2))

;;
;; ==============================================================================
;; Finding user data:
;; ==============================================================================
;;

(defn- user-query [query]
  (assert (map? query))
  (let [query (if-let [id (:id query)]
                (-> query
                  (assoc :_id id)
                  (dissoc :id))
                query)
        query (if-let [username (:username query)]
                (assoc query :username (ss/lower-case username))
                query)
        query (if-let [email (:email query)]
                (assoc query :email (ss/lower-case email))
                query)]
    query))

(defn find-user [& {:as query}]
  (mongo/select-one :users (user-query query)))

(defn find-users [& {:as query}]
  (mongo/select :users (user-query query)))

;;
;; ==============================================================================
;; Getting non-private user data:
;; ==============================================================================
;;

(def get-user-by-id (comp non-private (partial find-user :id)))
(def get-user-by-email (comp non-private (partial find-user :email)))

(defn get-user-with-password [username password]
  (let [user (find-user :username username)]
    (when (and (:enabled user) (security/check-password password (get-in user [:private :password])))
      (non-private user))))

(defn get-user-with-apikey [apikey]
  (let [user (find-user :private.apikey apikey)]
    (when (:enabled user)
      (non-private user))))

(defmacro with-user-by-email [email & body]
  `(let [~'user (get-user-by-email ~email)]
     (when-not ~'user
       (debugf "user '%s' not found with email" ~email)
       (fail! :error.user-not-found :email ~email))
     ~@body))

(defn get-users [caller & query-params]
  (map non-private (apply find-users caller query-params)))

;;
;; ==============================================================================
;; User role:
;; ==============================================================================
;;

(defn applicationpage-for [role]
  (kebab/->kebab-case role))

(defn user-in-role [user role & params]
  (merge (apply hash-map params) (assoc (summary user) :role role)))

;;
;; ==============================================================================
;; Current user:
;; ==============================================================================
;;

(defn current-user
  "fetches the current user from session"
  ([] (current-user (request/ring-request)))
  ([request] (request :user)))

(defn load-current-user
  "fetch the current user from db"
  []
  (get-user-by-id (:id (current-user))))

(defn refresh-user!
  "Loads user information from db and saves it to session. Call this after you make changes to user information."
  []
  (when-let [user (load-current-user)]
    (debug "user session refresh successful, username:" (:username user))
    (session/put! :user user)))

;;
;; ==============================================================================
;; Creating API keys:
;; ==============================================================================
;;

(defn create-apikey
  "Add or replcae users api key. User is identified by email. Returns apikey. If user is unknown throws an exception."
  [email]
  (let [apikey (security/random-password)
        n      (mongo/update-n :users {:email (ss/lower-case email)} {$set {:private.apikey apikey}})]
    (when-not (= n 1) (fail! :unknown-user :email email))
    apikey))

;;
;; ==============================================================================
;; Change password:
;; ==============================================================================
;;

(defn change-password
  "Update users password. Returns nil. If user is not found, raises an exception."
  [email password]
  (let [salt              (security/dispense-salt)
        hashed-password   (security/get-hash password salt)]
    (when-not (= 1 (mongo/update-n :users
                                   {:email (ss/lower-case email)}
                                   {$set {:private.password hashed-password}}))
      (fail! :unknown-user :email email))
    nil))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

(def ^:private user-keys          [:id :email :role :firstName :lastName :personId :phone :city :street :zip :enabled :organizations])
(def ^:private required-keys      [:id :email])
(def ^:private user-defaults      {:firstName "" :lastName "" :enabled false :role :dummy})
(def ^:private known-user-roles   #{:admin :authority :authorityAdmin :applicant :dummy})

(defn create-user-entity [{:keys [email password role] :as user-data}]
  (when-let [missing (util/missing-keys user-data required-keys)]
    (fail! :error.missing-required-key :missing missing))
  (let [email (ss/lower-case email)]
    (merge
      user-defaults
      (select-keys user-data user-keys)
      {:username email
       :email    email
       :private  (if password
                   {:password (security/get-hash password)}
                   {})})))

(defn create-new-user
  "Insert new user to database, returns new user data without private information. If user
   exists and has role \"dummy\", overwrites users information. If users exists with any other
   role, throws exception."
  [caller user-data]
  (let [id        (mongo/create-id)
        new-user  (create-user-entity (assoc user-data :id id))
        old-user  (get-user-by-email (:email user-data))]
    (try
      (if (= "dummy" (:role old-user))
        (do
          (info "rewriting over dummy user:" (:id old-user) (dissoc new-user :private :id))
          (mongo/update-by-id :users (:id old-user) (dissoc new-user :id)))
        (do
          (info "creating new user" (dissoc new-user :private))
          (mongo/insert :users new-user)))
      (get-user-by-email (:email new-user))
      (catch com.mongodb.MongoException$DuplicateKey e
        (if-let [field (second (re-find #"E11000 duplicate key error index: lupapiste\.users\.\$([^\s._]+)" (.getMessage e)))]
          (do
            (warnf "Duplicate key detected when inserting new user: field=%s" field)
            (fail! :duplicate-key :field field))
          (do
            (warn e "Inserting new user failed")
            (fail! :cant-insert)))))))

;;
;; ==============================================================================
;; Updating user information:
;; ==============================================================================
;;

(defn update-user-by-email [email data]
  (mongo/update :users {:email (ss/lower-case email)} {$set data}))

(defn update-organizations-of-authority-user [email new-organization]
  (let [old-orgs (:organizations (get-user-by-email email))]
    (when (every? #(not (= % new-organization)) old-orgs)
      (update-user-by-email email {:organizations (merge old-orgs new-organization)}))))

;;
;; ==============================================================================
;; Other:
;; ==============================================================================
;;

; TODO: replace dummy users with tokens
; When (if?) dummy users are replaced with tokens, this should be removed too:

(defn get-or-create-user-by-email [email]
  (let [email (ss/lower-case email)]
    (or
      (get-user-by-email email)
      (create-new-user (current-user) {:email email}))))

(defn authority? [{role :role}]
  (= :authority (keyword role)))

(defn applicant? [{role :role}]
  (= :applicant (keyword role)))

(defn same-user? [{id1 :id} {id2 :id}]
  (= id1 id2))

(defn with-user [email function]
  (if (nil? email)
    (fail! :error.user-not-found)
    (if-let [user (get-user-by-email email)]
      (function user)
      (do
        (debugf "user '%s' not found with email" email)
        (fail! :error.user-not-found)))))
