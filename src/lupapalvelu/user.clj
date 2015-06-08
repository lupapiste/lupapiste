(ns lupapalvelu.user
  (:require [taoensso.timbre :as timbre :refer [debug debugf info warn warnf]]
            [lupapalvelu.document.schemas :as schemas]
            [clj-time.core :as time]
            [clj-time.coerce :refer [to-date]]
            [monger.operators :refer :all]
            [monger.query :as query]
            [camel-snake-kebab :as kebab]
            [sade.core :refer [fail fail! now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [schema.core :as sc]))

;;
;; User schema
;;

(def user-skeleton
  {:id        ""
   :firstName ""
   :lastName  ""
   :role      "dummy"
   :email     "dummy@example.com"
   :username  "dummy@example.com"
   :enabled   false})


(def User {:id                                    sc/Str
           :firstName                             (util/max-length-string 255)
           :lastName                              (util/max-length-string 255)
           :role                                  (sc/enum "applicant"
                                                           "authority"
                                                           "oirAuthority"
                                                           "authorityAdmin"
                                                           "admin"
                                                           "dummy"
                                                           "rest-api"
                                                           "trusted-etl")
           :email                                 (sc/both
                                                    (sc/pred util/valid-email? "Not valid email")
                                                    (util/max-length-string 255))
           :username                              (util/max-length-string 255)
           :enabled                               sc/Bool
           (sc/optional-key :private)             {(sc/optional-key :password) sc/Str
                                                   (sc/optional-key :apikey) sc/Str}
           (sc/optional-key :orgAuthz)            {sc/Keyword (sc/pred vector? "OrgAuthz must be vector")}
           (sc/optional-key :personId)            (sc/pred util/valid-hetu? "Not valid hetu")
           (sc/optional-key :street)              (sc/maybe (util/max-length-string 255))
           (sc/optional-key :city)                (sc/maybe (util/max-length-string 255))
           (sc/optional-key :zip)                 (sc/either
                                                    (sc/pred util/finnish-zip? "Not a valid zip code")
                                                    (sc/pred ss/blank?))
           (sc/optional-key :phone)               (sc/maybe (util/max-length-string 255))
           (sc/optional-key :architect)           sc/Bool
           (sc/optional-key :degree)              (sc/either
                                                    (apply sc/enum (conj
                                                                     (map :name (:body schemas/koulutusvalinta))
                                                                     "other"))
                                                    (sc/pred ss/blank?))
           (sc/optional-key :graduatingYear)      (sc/either
                                                    (sc/both (util/min-length-string 4) (util/max-length-string 4))
                                                    (sc/pred ss/blank?))
           (sc/optional-key :fise)                (util/max-length-string 255)
           (sc/optional-key :companyName)         (util/max-length-string 255)
           (sc/optional-key :companyId)           (sc/either
                                                    (sc/pred util/finnish-y? "Not valid Y code")
                                                    (sc/pred ss/blank?))
           (sc/optional-key :allowDirectMarketing) sc/Bool
           (sc/optional-key :attachments)         (sc/pred vector? "Attachments are in a vector")
           (sc/optional-key :company)             {:id sc/Str :role sc/Str}
           (sc/optional-key :partnerApplications) {:rakentajafi {:id sc/Str
                                                                 :created sc/Int
                                                                 :origin sc/Bool}}
           (sc/optional-key :notification)        {:messageI18nkey sc/Str
                                                   :titleI18nkey   sc/Str}})

;;
;; ==============================================================================
;; Utils:
;; ==============================================================================
;;

(defn non-private
  "Returns user without private details."
  [user]
  (dissoc user :private))

(def summary-keys [:id :username :firstName :lastName :role])

(defn summary
  "Returns common information about the user or nil"
  [user]
  (when user
    (select-keys user summary-keys)))

(defn coerce-org-authz
  "Coerces orgAuthz to schema {Keyword #{Keyword}}"
  [org-authz]
  (into {} (for [[k v] org-authz] [k (set (map keyword v))])))

(defn with-org-auth [user]
  (update-in user [:orgAuthz] coerce-org-authz))

(defn session-summary
  "Returns common information about the user to be stored in session or nil"
  [user]
  (some-> user
    (select-keys [:id :username :firstName :lastName :role :email :organizations :company :architect :orgAuthz])
    with-org-auth
    (assoc :expires (+ (now) (.toMillis java.util.concurrent.TimeUnit/MINUTES 5)))))

(defn virtual-user?
  "True if user exists only in session, not in database"
  [{:keys [role impersonating]}]
  (or
    impersonating
    (contains? #{:oirAuthority} (keyword role))))

(defn authority? [{role :role}]
  (#{:authority :oirAuthority} (keyword role)))

(defn applicant? [{role :role}]
  (= :applicant (keyword role)))

(defn rest-user? [{role :role}]
  (= :rest-api (keyword role)))

(defn same-user? [{id1 :id} {id2 :id}]
  (= id1 id2))

(def canonize-email (comp ss/lower-case ss/trim))

(defn organization-ids
  "Returns user's organizations as a set of strings"
  [{org-authz :orgAuthz :as user}]
  (->> org-authz keys (map name) set))

(defn organization-ids-by-roles
  "Returns a set of organization IDs where user has given roles."
  [{org-authz :orgAuthz :as user} roles]
  {:pre [(set? roles) (every? keyword? roles)]}
  (->> org-authz
    (filter (fn [[org org-roles]] (some roles org-roles)))
    (map (comp name first))
    set))

(defn authority-admins-organization-id [user]
  (first (organization-ids-by-roles user #{:authorityAdmin})))

(defn user-is-authority-in-organization? [user organization-id]
  (let [org-set (organization-ids-by-roles user #{:authority})]
    (contains? org-set organization-id)))

(defn org-authz-match [organization-ids & [role]]
  {$or (for [org-id organization-ids] {(str "orgAuthz." (name org-id)) (or role {$exists true})})})

(defn batchrun-user [org-ids]
  {:id "-"
   :enabled true
   :lastName "Er\u00e4ajo"
   :firstName "Lupapiste"
   :role "authority"
   :orgAuthz (reduce (fn [m org-id] (assoc m (keyword org-id) #{:authority})) {} org-ids)})

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
                (assoc query :username (canonize-email username))
                query)
        query (if-let [email (:email query)]
                (assoc query :email (canonize-email email))
                query)
        query (if-let [organization (:organization query)]
                (-> query
                  (assoc (str "orgAuthz." organization) {$exists true})
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
        caller-organizations (organization-ids caller)
        organizations        (:organizations params)
        organizations        (if admin? organizations (filter caller-organizations (or organizations caller-organizations)))
        role                 (:filter-role params)
        role                 (if admin? role :authority)
        enabled              (if admin? (:filter-enabled params) true)]
    (merge {}
      (when (seq organizations) {:organizations organizations})
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

(defn- limit-organizations [query]
  (if-let [org-ids (:organizations query)]
    {$and [(dissoc query :organizations), (org-authz-match org-ids)]}
    query))

(defn users-for-datatables [caller params]
  (let [base-query       (users-for-datatables-base-query caller params)
        base-query-total (mongo/count :users (limit-organizations base-query))
        query            (limit-organizations (users-for-datatables-query base-query params))
        query-total      (mongo/count :users query)
        users            (query/with-collection "users"
                           (query/find query)
                           (query/fields [:email :firstName :lastName :role :orgAuthz :enabled])
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
  (mongo/any? :logins {:_id (canonize-email username)
                       :failed-logins {$gte (env/value :login :allowed-failures)}
                       :locked {$gt (logins-lock-expires-date)}}))

(defn login-failed [username]
  {:pre [username]}
  (mongo/remove-many :logins {:locked {$lte (logins-lock-expires-date)}})
  (mongo/update :logins {:_id (canonize-email username)}
                {$set {:locked (java.util.Date.)}, $inc {:failed-logins 1}}
                :multi false
                :upsert true))

(defn clear-logins [username]
  {:pre [username]}
  (mongo/remove :logins (canonize-email username)))

;;
;; ==============================================================================
;; Getting non-private user data:
;; ==============================================================================
;;

(defn get-user [q]
  (non-private (find-user q)))

(defn get-users [q]
  (map non-private (find-users q)))

(defn get-user-by-id [id]
  {:pre [id]}
  (get-user {:id id}))

(defn get-user-by-id! [id]
  (or (get-user-by-id id) (fail! :not-found)))

(defn get-user-by-email [email]
  {:pre [email]}
  (get-user {:email email}))

(defn get-user-with-password [username password]
  (when-not (or (ss/blank? username) (ss/blank? password))
    (let [user (find-user {:username username})]
     (when (and user (:enabled user) (security/check-password password (get-in user [:private :password])))
       (non-private user)))))

(defn get-user-with-apikey [apikey]
  (when-not (ss/blank? apikey)
    (let [user (find-user {:private.apikey apikey})]
      (when (:enabled user)
        (session-summary user)))))

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
  (let [s (name role)]
    (cond
      (or (ss/blank? s) (= s "dummy")) "applicant"
      (= s "oirAuthority") "oir"
      :else (kebab/->kebab-case s))))

(defn user-in-role [user role & params]
  (merge (apply hash-map params) (assoc (summary user) :role role)))

;;
;; ==============================================================================
;; Current user:
;; ==============================================================================
;;

(defn current-user
  "fetches the current user from session"
  [request] (:user request ))

;;
;; ==============================================================================
;; Creating API keys:
;; ==============================================================================
;;

(defn create-apikey
  "Add or replace users api key. User is identified by email. Returns apikey. If user is unknown throws an exception."
  [email]
  (let [apikey (security/random-password)
        n      (mongo/update-n :users {:email (canonize-email email)} {$set {:private.apikey apikey}})]
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
        email             (canonize-email email)
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
  (mongo/update :users {:email (canonize-email email)} {$set data}))

;;
;; ==============================================================================
;; Other:
;; ==============================================================================
;;

;;
;; Link user to company:
;;

(defn link-user-to-company! [user-id company-id role]
  (mongo/update :users {:_id user-id} {$set {:company {:id company-id, :role role}}}))
