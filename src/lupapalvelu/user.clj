(ns lupapalvelu.user
  (:require [taoensso.timbre :as timbre :refer [debug debugf info warn warnf]]
            [monger.operators :refer :all]
            [monger.query :as query]
            [noir.request :as request]
            [noir.session :as session]
            [camel-snake-kebab :as kebab]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.core :refer [fail fail!]]
            [clj-time.core :as time]
            [clj-time.coerce :refer [to-date]]
            [lupapalvelu.security :as security]))

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
  {:pre [(map? query)]}
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
                query)
        query (if-let [organization (:organization query)]
                (-> query
                  (assoc :organizations organization)
                  (dissoc :organization))
                query)]
    query))

(defn find-user [query]
  (mongo/select-one :users (user-query query)))

(defn find-users [query]
  (mongo/select :users (user-query query)))

;;
;; jQuery data-tables support:
;;

(defn- users-for-datatables-base-query [caller params]
  (let [admin?               (= (-> caller :role keyword) :admin)
        caller-organizations (set (:organizations caller))
        organizations        (:organizations params)
        organizations        (if admin? organizations (filter caller-organizations (or organizations caller-organizations)))
        role                 (:filter-role params)
        role                 (if admin? role :authority)
        enabled              (if admin? (:filter-enabled params) true)]
    (merge {}
      (when organizations       {:organizations {$in organizations}})
      (when role                {:role role})
      (when-not (nil? enabled)  {:enabled enabled}))))

(defn- users-for-datatables-query [base-query {:keys [filter-search]}]
  (if (ss/blank? filter-search)
    base-query
    (let [searches (ss/split filter-search #"\s+")]
      (assoc base-query $and (map (fn [t]
                                    {$or (map hash-map
                                           [:email :firstName :lastName]
                                           (repeat t))})
                               (map re-pattern searches))))))

(defn users-for-datatables [caller params]
  (let [base-query       (users-for-datatables-base-query caller params)
        base-query-total (mongo/count :users base-query)
        query            (users-for-datatables-query base-query params)
        query-total      (mongo/count :users query)
        users            (query/with-collection "users"
                           (query/find query)
                           (query/fields [:email :firstName :lastName :role :organizations :enabled])
                           (query/skip (util/->int (:iDisplayStart params) 0))
                           (query/limit (util/->int (:iDisplayLength params) 16)))]
    {:rows     users
     :total    base-query-total
     :display  query-total
     :echo     (str (util/->int (str (:sEcho params))))}))

;;
;; ==============================================================================
;; Login throttle
;; ==============================================================================
;;

(defn- logins-lock-expires-date []
  (to-date (time/minus (time/now) (time/seconds (env/value :login :throttle-expires)))))

(defn throttle-login? [username]
  {:pre [username]}
  (mongo/any? :logins {:_id (ss/lower-case username)
                       :failed-logins {$gte (env/value :login :allowed-failures)}
                       :locked {$gt (logins-lock-expires-date)}}))

(defn login-failed [username]
  {:pre [username]}
  (mongo/remove-many :logins {:locked {$lte (logins-lock-expires-date)}})
  (mongo/update :logins {:_id (ss/lower-case username)}
                {$set {:locked (java.util.Date.)}, $inc {:failed-logins 1}}
                :multi false
                :upsert true))

(defn clear-logins [username]
  {:pre [username]}
  (mongo/remove :logins (ss/lower-case username)))

;;
;; ==============================================================================
;; Getting non-private user data:
;; ==============================================================================
;;

(defn get-user-by-id [id]
  {:pre [id]}
  (non-private (find-user {:id id})))

(defn get-user-by-email [email]
  {:pre [email]}
  (non-private (find-user {:email email})))

(defn get-user-with-password [username password]
  (when-not (or (ss/blank? username) (ss/blank? password))
    (let [user (find-user {:username username})]
     (when (and user (:enabled user) (security/check-password password (get-in user [:private :password])))
       (non-private user)))))

(defn get-user-with-apikey [apikey]
  (when-not (ss/blank? apikey)
    (let [user (find-user {:private.apikey apikey})]
      (when (:enabled user)
        (non-private user)))))

(defmacro with-user-by-email [email & body]
  `(let [~'user (get-user-by-email ~email)]
     (when-not ~'user
       (debugf "user '%s' not found with email" ~email)
       (fail! :error.user-not-found :email ~email))
     ~@body))

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

(defn refresh-user!
  "Loads user information from db and saves it to session. Call this after you make changes to user information."
  [user-id]
  {:pre [user-id]}
  (when-let [user (get-user-by-id user-id)]
    (debug "user session refresh successful, username:" (:username user))
    (session/put! :user user)))

;;
;; ==============================================================================
;; Creating API keys:
;; ==============================================================================
;;

(defn create-apikey
  "Add or replace users api key. User is identified by email. Returns apikey. If user is unknown throws an exception."
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
        hashed-password   (security/get-hash password salt)
        email             (ss/lower-case email)
        updated-user      (mongo/update-one-and-return :users
                            {:email email}
                            {$set {:private.password hashed-password
                                   :enabled true}})]
    (if updated-user
      (do
        (mongo/remove-many :activation {:email email})
        (clear-logins (:username updated-user)))
      (fail! :unknown-user :email email))
    nil))

;;
;; ==============================================================================
;; Updating user information:
;; ==============================================================================
;;

(defn update-user-by-email [email data]
  (mongo/update :users {:email (ss/lower-case email)} {$set data}))

(defn update-organizations-of-authority-user [email new-organization]
  (let [old-orgs (:organizations (get-user-by-email email))]
    (when (every? #(not= % new-organization) old-orgs)
      (update-user-by-email email {:organizations (merge old-orgs new-organization)}))))


;;
;; ==============================================================================
;; Other:
;; ==============================================================================
;;

(defn authority? [{role :role}]
  (= :authority (keyword role)))

(defn applicant? [{role :role}]
  (= :applicant (keyword role)))

(defn same-user? [{id1 :id} {id2 :id}]
  (= id1 id2))
