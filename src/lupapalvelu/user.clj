(ns lupapalvelu.user
  (:require [taoensso.timbre :as timbre :refer [debug debugf info warn warnf]]
            [swiss.arrows :refer [-<>]]
            [clj-time.core :as time]
            [clj-time.coerce :refer [to-date]]
            [camel-snake-kebab.core :as csk]
            [monger.operators :refer :all]
            [monger.query :as query]
            [schema.core :as sc]
            [sade.core :refer [ok fail fail! now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [sade.schemas :as ssc]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.i18n :as i18n]))

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

(def SearchFilter
  {:id        sc/Str
   :title     sc/Str
   :sort     {:field (sc/enum "type" "location" "applicant" "submitted" "modified" "state" "handler" "foreman" "foremanRole" "id")
              :asc    sc/Bool}
   :filter   {(sc/optional-key :handlers)      [sc/Str]
              (sc/optional-key :tags)          [sc/Str]
              (sc/optional-key :operations)    [sc/Str]
              (sc/optional-key :organizations) [sc/Str]
              (sc/optional-key :areas)         [sc/Str]}})

(def supported-language (apply sc/enum (map name i18n/languages)))

(def User {:id                                    sc/Str
           :firstName                             (ssc/max-length-string 255)
           :lastName                              (ssc/max-length-string 255)
           :role                                  (sc/enum "applicant"
                                                           "authority"
                                                           "oirAuthority"
                                                           "authorityAdmin"
                                                           "admin"
                                                           "dummy"
                                                           "rest-api"
                                                           "trusted-etl")
           :email                                 ssc/Email
           :username                              ssc/Username
           :enabled                               sc/Bool
           (sc/optional-key :private)             {(sc/optional-key :password) sc/Str
                                                   (sc/optional-key :apikey) sc/Str}
           (sc/optional-key :orgAuthz)            {sc/Keyword [sc/Str]}
           (sc/optional-key :personId)            (sc/maybe ssc/Hetu)
           (sc/optional-key :street)              (sc/maybe (ssc/max-length-string 255))
           (sc/optional-key :city)                (sc/maybe (ssc/max-length-string 255))
           (sc/optional-key :zip)                 (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
           (sc/optional-key :phone)               (sc/maybe (ssc/max-length-string 255))
           (sc/optional-key :architect)           sc/Bool
           (sc/optional-key :degree)              (sc/maybe (apply sc/enum "" "other" (map :name (:body schemas/koulutusvalinta))))
           (sc/optional-key :graduatingYear)      (sc/if ss/blank? ssc/BlankStr (ssc/fixed-length-string 4))
           (sc/optional-key :fise)                (ssc/max-length-string 255)
           (sc/optional-key :fiseKelpoisuus)      (sc/maybe (apply sc/enum "" (map :name schemas/fise-kelpoisuus-lajit)))
           (sc/optional-key :companyName)         (ssc/max-length-string 255)
           (sc/optional-key :companyId)           (sc/if ss/blank? ssc/BlankStr ssc/FinnishY)
           (sc/optional-key :allowDirectMarketing) sc/Bool
           (sc/optional-key :attachments)         [{:attachment-type  {:type-group sc/Str, :type-id sc/Str}
                                                    :attachment-id sc/Str
                                                    :file-name  sc/Str
                                                    :content-type  sc/Str
                                                    :size  sc/Num
                                                    :created ssc/Timestamp}]
           (sc/optional-key :company)             {:id sc/Str :role sc/Str :submit sc/Bool}
           (sc/optional-key :partnerApplications) {(sc/optional-key :rakentajafi) {:id sc/Str
                                                                                   :created ssc/Timestamp
                                                                                   :origin sc/Bool}}
           (sc/optional-key :notification)        {(sc/optional-key :messageI18nkey) sc/Str
                                                   (sc/optional-key :titleI18nkey)   sc/Str
                                                   (sc/optional-key :message)        sc/Str
                                                   (sc/optional-key :title)          sc/Str}
           (sc/optional-key :defaultFilter)       {(sc/optional-key :id) (sc/maybe sc/Str)
                                                   (sc/optional-key :foremanFilterId) (sc/maybe sc/Str)}
           (sc/optional-key :applicationFilters)  [SearchFilter]
           (sc/optional-key :foremanFilters)      [SearchFilter]
           (sc/optional-key :language)            supported-language})

(def RegisterUser {:email                            ssc/Email
                   :street                           (sc/maybe (ssc/max-length-string 255))
                   :city                             (sc/maybe (ssc/max-length-string 255))
                   :zip                              (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
                   :phone                            (sc/maybe (ssc/max-length-string 255))
                   (sc/optional-key :architect)      sc/Bool
                   (sc/optional-key :degree)         (sc/maybe (apply sc/enum "" "other" (map :name (:body schemas/koulutusvalinta))))
                   (sc/optional-key :graduatingYear) (sc/if ss/blank? ssc/BlankStr (ssc/fixed-length-string 4))
                   (sc/optional-key :fise)           (ssc/max-length-string 255)
                   (sc/optional-key :fiseKelpoisuus) (sc/maybe (apply sc/enum "" (map :name schemas/fise-kelpoisuus-lajit)))
                   :allowDirectMarketing             sc/Bool
                   :rakentajafi                      sc/Bool
                   :stamp                            (sc/maybe (ssc/max-length-string 255))
                   :password                         (ssc/max-length-string 255)
                   (sc/optional-key :language)       supported-language})
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

(def SummaryUser (select-keys User (mapcat (juxt identity sc/optional-key) summary-keys)))

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
  (contains? #{:authority :oirAuthority} (keyword role)))

(defn applicant? [{role :role}]
  (= :applicant (keyword role)))

(defn rest-user? [{role :role}]
  (= :rest-api (keyword role)))

(defn admin? [{role :role}]
  (= :admin (keyword role)))

(defn authority-admin? [{role :role}]
  (= :authorityAdmin (keyword role)))

(defn dummy? [{role :role}]
  (= :dummy (keyword role)))

(defn same-user? [{id1 :id} {id2 :id}]
  (= id1 id2))

(def canonize-email (comp ss/lower-case ss/trim))

(defn organization-ids
  "Returns user's organizations as a set of strings"
  [{org-authz :orgAuthz :as user}]
  (->> org-authz keys (map name) set))

(defn organization-ids-by-roles
  "Returns a set of organization IDs where user has given roles.
  Note: the user must have gone through with-org-auth (the orgAuthz
  must be keywords)."
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
   :username "eraajo@lupapiste.fi"
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

(defn find-users
  ([query]
   (mongo/select :users (user-query query)))
  ([query order-by]
   (mongo/select-ordered :users (user-query query) order-by)))

(defn find-authorized-users-in-org [org-id & org-authz]
  (mongo/select :users
                {(str "orgAuthz." org-id) {$in org-authz}, :enabled true}
                summary-keys
                (array-map :lastName 1, :firstName 1)))

(defn authority-users-in-organizations [org-ids]
  (let [query (org-authz-match org-ids "authority")]
    (mongo/select :users
                  query
                  [:id :username :firstName :lastName :email]
                  (array-map :lastName 1, :firstName 1))))
;;
;; jQuery data-tables support:
;;

(defn- users-for-datatables-base-query [caller params]
  (let [caller-organizations (organization-ids caller)
        organizations        (:organizations params)
        organizations        (if (admin? caller) organizations (filter caller-organizations (or organizations caller-organizations)))
        role                 (:filter-role params)
        role                 (if (admin? caller) role :authority)
        enabled              (if (or (admin? caller) (authority-admin? caller)) (:filter-enabled params) true)]
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
        users            (mongo/with-collection "users"
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

(defn get-users
  ([q]
   (map non-private (find-users q)))
  ([q order-by]
   (map non-private (find-users q order-by))))

(defn get-user-by-id [id]
  {:pre [id]}
  (get-user {:id id}))

(defn get-user-by-id!
  "Get user or throw fail!"
  [id]
  (or (get-user-by-id id) (fail! :not-found)))

(defn get-user-by-email [email]
  {:pre [email]}
  (get-user {:email email}))

(defn email-in-use? [email]
  (as-> email $
    (get-user-by-email $)
    (and $ (not (dummy? $)))))

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
;; Create user:
;; ==============================================================================
;;

(defn- validate-create-new-user! [caller user-data]
  (when-let [missing (util/missing-keys user-data [:email :role])]
    (fail! :error.missing-parameters :parameters missing))

  (let [password         (:password user-data)
        user-role        (keyword (:role user-data))
        caller-role      (keyword (:role caller))
        org-authz        (:orgAuthz user-data)
        organization-id  (when (map? org-authz)
                           (name (first (keys org-authz))))
        admin?           (= caller-role :admin)
        authorityAdmin?  (= caller-role :authorityAdmin)]

    (when (and org-authz (not (every? coll? (vals org-authz))))
      (fail! :error.invalid-role :desc "new user has unsupported organization roles"))

    (when-not (#{:authority :authorityAdmin :applicant :dummy} user-role)
      (fail! :error.invalid-role :desc "new user has unsupported role" :user-role user-role))

    (when (and (= user-role :applicant) caller)
      (fail! :error.unauthorized :desc "applicants are born via registration"))

    (when (and (= user-role :authorityAdmin) (not admin?))
      (fail! :error.unauthorized :desc "only admin can create authorityAdmin users"))

    (when (and (= user-role :authority) (not authorityAdmin?))
      (fail! :error.unauthorized :desc "only authorityAdmin can create authority users" :user-role user-role :caller-role caller-role))

    (when (and (= user-role :authorityAdmin) (not organization-id))
      (fail! :error.missing-parameters :desc "new authorityAdmin user must have organization" :parameters [:organization]))

    (when (and (= user-role :authority) (and organization-id (not ((organization-ids caller) organization-id))))
      (fail! :error.unauthorized :desc "authorityAdmin can create users into his/her own organization only, or statement givers without any organization at all"))

    (when (and (= user-role :dummy) organization-id)
      (fail! :error.unauthorized :desc "dummy user may not have an organization" :missing :organization))

    (when (and password (not (security/valid-password? password)))
      (fail! :error.password.minlengt :desc "password specified, but it's not valid"))

    (when (and organization-id (not (organization/get-organization organization-id)))
      (fail! :error.organization-not-found))

    (when (and (:apikey user-data) (not admin?))
      (fail! :error.unauthorized :desc "only admin can create create users with apikey")))

  true)

(defn- create-new-user-entity [{:keys [enabled password] :as user-data}]
  (let [email (canonize-email (:email user-data))]
    (-<> user-data
      (select-keys [:email :username :role :firstName :lastName :personId
                    :phone :city :street :zip :enabled :orgAuthz :language
                    :allowDirectMarketing :architect :company
                    :graduatingYear :degree :fise :fiseKelpoisuus])
      (merge {:firstName "" :lastName "" :username email} <>)
      (assoc
        :email email
        :enabled (= "true" (str enabled))
        :private (if password {:password (security/get-hash password)} {})))))

(defn create-new-user
  "Insert new user to database, returns new user data without private information. If user
   exists and has role \"dummy\", overwrites users information. If users exists with any other
   role, throws exception."
  [caller user-data & {:keys [send-email] :or {send-email true}}]
  (validate-create-new-user! caller user-data)
  (let [user-entry  (create-new-user-entity user-data)
        old-user    (get-user-by-email (:email user-entry))
        new-user    (if old-user
                      (assoc user-entry :id (:id old-user))
                      (assoc user-entry :id (mongo/create-id)))
        email       (:email new-user)
        {old-id :id old-role :role}  old-user
        notification {:titleI18nkey "user.notification.firstLogin.title"
                      :messageI18nkey "user.notification.firstLogin.message"}
        new-user   (if (applicant? user-data)
                     (assoc new-user :notification notification)
                     new-user)]
    (try
      (condp = old-role
        nil     (do
                  (info "creating new user" (dissoc new-user :private))
                  (mongo/insert :users new-user))
        "dummy" (do
                  (info "rewriting over dummy user:" old-id (dissoc new-user :private :id))
                  (mongo/update-by-id :users old-id (dissoc new-user :id)))
        ; LUPA-1146
        "applicant" (if (and (= (:personId old-user) (:personId new-user)) (not (:enabled old-user)))
                      (do
                        (info "rewriting over inactive applicant user:" old-id (dissoc new-user :private :id))
                        (mongo/update-by-id :users old-id (dissoc new-user :id)))
                      (fail! :error.duplicate-email))
        (fail! :error.duplicate-email))

      (get-user-by-email email)

      (catch com.mongodb.DuplicateKeyException e
        (if-let [field (second (re-find #"E11000 duplicate key error index: lupapiste\.users\.\$([^\s._]+)" (.getMessage e)))]
          (do
            (warnf "Duplicate key detected when inserting new user: field=%s" field)
            (fail! :error.duplicate-email))
          (do
            (warn e "Inserting new user failed")
            (fail! :cant-insert)))))))

(defn create-rest-user
  "Creates and inserts new rest-api user to database, returns username and password to frontend.
   Only for Solita admin"
  [user-data]
  (let [pw        (security/random-password)
        user-data (merge user-data
                         {:enabled true
                          :role "rest-api"
                          :orgAuthz {(:organization user-data) ["authority"]}
                          :password pw})
        user (-> (create-new-user-entity user-data)
                 (assoc :id (mongo/create-id)))]
    (mongo/insert :users user)
    {:username (:username user)
     :password pw}))

(defn get-or-create-user-by-email [email current-user]
  (let [email (canonize-email email)]
    (or
      (get-user-by-email email)
      (create-new-user current-user {:email email :role "dummy"}))))

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
      :else (csk/->kebab-case s))))

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
    (when-not (= n 1) (fail! :error.user-not-found))
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
      (fail! :error.user-not-found))
    nil))

;;
;; ==============================================================================
;; Updating user information:
;; ==============================================================================
;;

(defn update-user-by-email
  "Returns (ok) or (fail :error.user-not-found)"
  ([email data] (update-user-by-email email {} data))
  ([email extra-query data]
    {:pre [(string? email) (map? extra-query) (map? data)]}
    (let [query (merge {:email (canonize-email email)} extra-query)
          updates (condp every? (keys data)
                    mongo/operator? data
                    (complement mongo/operator?) {$set data})]
      (if (pos? (mongo/update-n :users query updates))
        (ok)
        (fail :error.user-not-found)))))

;;
;; ==============================================================================
;; Remove dummy user
;; ==============================================================================
;;
(defn remove-dummy-user
  "Removes a dummy user with the given id"
  [user-id]
  {:pre [(string? user-id)]}
  (mongo/remove-many :users
                     {:_id user-id
                      :role "dummy"}))

;;
;; ==============================================================================
;; User manipulation
;; ==============================================================================
;;

(defn update-user-language
  "Sets user's language if given and missing. Returns user that is
  augmented with indicatorNote and language if the language has been set."
  [{:keys [id language] :as user} ui-lang]
  (if (and (not language) (not (sc/check supported-language ui-lang)) )
    (do (mongo/update :users {:_id id} {$set {:language ui-lang}})
        (assoc user
               :indicatorNote :user.language.note
               :language ui-lang))
    user))
