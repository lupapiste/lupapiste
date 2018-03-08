(ns lupapalvelu.user
  (:require [camel-snake-kebab.core :as csk]
            [clj-time.coerce :refer [to-date]]
            [clj-time.core :as time]
            [clojure.set :as set]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permissions :refer [defcontext] :as permissions]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.security :as security]
            [lupapalvelu.user-enums :as user-enums]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer [ok fail fail! now]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer [-<> some-<>> -<>>]]
            [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf]]))

;;
;; User schema
;;


(defschema SearchFilter
  {:id        sc/Str
   :title     sc/Str
   :sort     {:field (sc/enum "type" "location" "applicant" "submitted" "modified" "state" "handler" "foreman" "foremanRole" "id")
              :asc    sc/Bool}
   :filter   {(sc/optional-key :handlers)      [sc/Str]
              (sc/optional-key :tags)          [sc/Str]
              (sc/optional-key :companyTags)   [sc/Str]
              (sc/optional-key :operations)    [sc/Str]
              (sc/optional-key :organizations) [sc/Str]
              (sc/optional-key :areas)         [sc/Str]
              (sc/optional-key :event)        [sc/Str]}})

(def Id (ssc/min-length-string 1)) ; Variation of user ids in different environments is too diverse for a simple customized schema.

(def all-roles
  "vector of role strings that can be used in User's :role field"
  ["applicant"
   "authority"
   "oirAuthority"
   "authorityAdmin"
   "admin"
   "dummy"
   "rest-api"
   "trusted-etl"
   "trusted-salesforce"
   "docstore-api"
   "financialAuthority"
   "onkalo-api"])

(defschema Role (apply sc/enum all-roles))
(defschema OrgId (sc/pred keyword? "Organization ID"))
(defschema Authz (sc/either (sc/pred string? "Authz access right")
                            (sc/pred keyword? "Authz access right")))
(defschema OrgAuthz {OrgId (sc/either [Authz] #{Authz})})
(defschema PersonIdSource (sc/enum "identification-service" "user"))

(defschema User
          {:id                                    Id
           :firstName                             (ssc/max-length-string 255)
           :lastName                              (ssc/max-length-string 255)
           :role                                  Role
           :email                                 ssc/Email
           :username                              ssc/Username
           :enabled                               sc/Bool
           (sc/optional-key :private)             {(sc/optional-key :password) sc/Str
                                                   (sc/optional-key :apikey) sc/Str}
           (sc/optional-key :orgAuthz)            OrgAuthz
           (sc/optional-key :personId)            (sc/if ss/blank? ssc/BlankStr ssc/Hetu)
           (sc/optional-key :personIdSource)      PersonIdSource
           (sc/optional-key :street)              (sc/maybe (ssc/max-length-string 255))
           (sc/optional-key :city)                (sc/maybe (ssc/max-length-string 255))
           (sc/optional-key :zip)                 (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
           (sc/optional-key :phone)               (sc/maybe (ssc/max-length-string 255))
           (sc/optional-key :architect)           sc/Bool
           (sc/optional-key :degree)              (sc/maybe (apply sc/enum "" "other" user-enums/koulutusvalinta))
           (sc/optional-key :graduatingYear)      (sc/if ss/blank? ssc/BlankStr (ssc/fixed-length-string 4))
           (sc/optional-key :fise)                (ssc/max-length-string 255)
           (sc/optional-key :fiseKelpoisuus)      (sc/maybe (apply sc/enum "" user-enums/fise-kelpoisuus-lajit))
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
                                                   (sc/optional-key :foremanFilterId) (sc/maybe sc/Str)
                                                   (sc/optional-key :companyFilterId) (sc/maybe sc/Str)}
           (sc/optional-key :applicationFilters)  [SearchFilter]
           (sc/optional-key :foremanFilters)      [SearchFilter]
           (sc/optional-key :companyFilters)      [SearchFilter]
           (sc/optional-key :language)            i18n/EnumSupportedLanguages
           (sc/optional-key :seen-organization-links) {sc/Keyword ssc/Timestamp}
           (sc/optional-key :firstLogin)          sc/Bool
           (sc/optional-key :oauth)               {:client-id sc/Str
                                                   :client-secret sc/Str
                                                   :scopes [(sc/enum "read" "pay")]
                                                   :display-name (i18n/lenient-localization-schema sc/Str)
                                                   :callback-url ssc/HttpUrl}})

(defschema RegisterUser
                  {:email                            ssc/Email
                   :street                           (sc/maybe (ssc/max-length-string 255))
                   :city                             (sc/maybe (ssc/max-length-string 255))
                   :zip                              (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
                   :phone                            (sc/maybe (ssc/max-length-string 255))
                   (sc/optional-key :architect)      sc/Bool
                   (sc/optional-key :degree)         (sc/maybe (apply sc/enum "" "other" user-enums/koulutusvalinta))
                   (sc/optional-key :graduatingYear) (sc/if ss/blank? ssc/BlankStr (ssc/fixed-length-string 4))
                   (sc/optional-key :fise)           (ssc/max-length-string 255)
                   (sc/optional-key :fiseKelpoisuus) (sc/maybe (apply sc/enum "" user-enums/fise-kelpoisuus-lajit))
                   :allowDirectMarketing             sc/Bool
                   :rakentajafi                      sc/Bool
                   :stamp                            (sc/maybe (ssc/max-length-string 255))
                   :password                         (ssc/max-length-string 255)
                   (sc/optional-key :language)       i18n/EnumSupportedLanguages})

(defschema Handler
  {:id     ssc/ObjectIdStr
   :userId (:id User)
   :roleId ssc/ObjectIdStr})

(defschema UserForRestEndpoint
  (merge (select-keys User [:role :email :firstName :lastName])
         {(sc/optional-key :company) {:id sc/Str :name sc/Str}}))

;;
;; ==============================================================================
;; Utils:
;; ==============================================================================
;;

(defcontext without-application-context [{{org-authz :orgAuthz} :user application :application}]
  (when-not application
    {:context-scope :organization
     :context-roles (->> (vals org-authz)
                         (reduce into #{}))}))

(defn full-name [{:keys [firstName lastName]}] (str firstName " " lastName))

(defn non-private
  "Returns user without private details."
  [user]
  (dissoc user :private))

(defn create-handler [handler-id role-id {user-id :id first-name :firstName last-name :lastName :as user}]
  {:id        (or handler-id (mongo/create-id))
   :roleId    role-id
   :userId    user-id
   :firstName first-name
   :lastName  last-name})

(def summary-keys [:id :username :firstName :lastName :role])

(defschema SummaryUser (select-keys User (mapcat (juxt identity sc/optional-key) summary-keys)))

(defn summary
  "Returns common information about the user or nil"
  [user]
  (when user
    (select-keys user summary-keys)))

(defn summary-for-search-filter
  "Returns common information about the user or nil"
  [user]
  (when user
    (select-keys user (conj summary-keys :email :enabled))))

(defn coerce-org-authz
  "Coerces orgAuthz to schema {Keyword #{Keyword}}"
  [org-authz]
  (into {} (for [[k v] org-authz] [k (set (map keyword v))])))

(defn with-org-auth [user]
  (update-in user [:orgAuthz] coerce-org-authz))

(def session-summary-keys [:id :username :firstName :lastName :role :email :organizations :company :architect :orgAuthz :language])

(defschema SessionSummaryUser
  (-> (select-keys User (mapcat (juxt identity sc/optional-key) session-summary-keys))
      (assoc (sc/optional-key :orgAuthz) {sc/Keyword #{sc/Keyword}}
             (sc/optional-key :impersonating) sc/Bool
             :expires ssc/Timestamp)))

(defn session-summary
  "Returns common information about the user to be stored in session or nil"
  [user]
  (some-> user
    (select-keys session-summary-keys)
    with-org-auth
    (assoc :expires (+ (now) (.toMillis java.util.concurrent.TimeUnit/MINUTES 5)))))

(defn oir-authority? [{role :role}]
  (contains? #{:oirAuthority} (keyword role)))

(defn virtual-user?
  "True if user exists only in session, not in database"
  [{:keys [impersonating] :as user}]
  (or
    impersonating
    (oir-authority? user)))

(defn authority? [{role :role}]
  (contains? #{:authority} (keyword role)))

(defn verified-person-id? [{pid :personId source :personIdSource :as user}]
  (and (ss/not-blank? pid) (util/=as-kw :identification-service source)))

(defn validate-authority
  "Validator: current user must be an authority. To be used in commands'
   :pre-check vectors."
  [command]
  (if (authority? (:user command))
    nil
    (fail :error.unauthorized :desc "user is not an authority")))

(defn applicant? [{role :role}]
  (= :applicant (keyword role)))

(defn rest-user? [{role :role}]
  (= :rest-api (keyword role)))

(defn docstore-user? [{role :role}]
  (= :docstore-api (keyword role)))

(defn admin? [{role :role}]
  (= :admin (keyword role)))

(defn authority-admin? [{role :role}]
  (= :authorityAdmin (keyword role)))

(defn dummy? [{role :role}]
  (= :dummy (keyword role)))

(defn same-user? [{id1 :id} {id2 :id}]
  (= id1 id2))

(defn company-user? [user]
  (= (-> user :company :role) "user"))

(defn company-admin? [user]
  (= (-> user :company :role) "admin"))

(defn financial-authority? [{role :role}]
  (= :financialAuthority (keyword role)))

(defn onkalo-user? [{role :role}]
  (= (-> :onkalo-api (keyword role))))

(defn organization-ids
  "Returns user's organizations as a set of strings"
  [{org-authz :orgAuthz :as user}]
  (->> org-authz keys (map name) set))

(defn get-organizations
  "Query organizations for user. Area data is omitted by default."
  ([user]
   (let [projection (-> (ssc/plain-keys org/Organization)
                        (zipmap (repeat true))
                        (dissoc :areas :areas-wgs84
                                :operations-attachments))]
     (get-organizations user projection)))
  ([user projection]
   (org/get-organizations {:_id {$in (organization-ids user)}} projection)))

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

(defn authority-admins-organization
  "Organization for the authority admin user."
  [user]
  (-> user
      authority-admins-organization-id
      org/get-organization))

(defn user-is-authority-in-organization? [user organization-id]
  (let [org-set (organization-ids-by-roles user roles/default-org-authz-roles)]
    (contains? org-set organization-id)))

(defn user-has-role-in-organization? [user organization-id valid-org-authz-roles]
  (let [org-set (organization-ids-by-roles user (set valid-org-authz-roles))]
    (contains? org-set organization-id)))

(defn org-authz-match [organization-ids & [role]]
  {$or (for [org-id organization-ids] {(str "orgAuthz." (name org-id)) (or role {$exists true})})})

(def migration-user-summary
  {:id "-"
   :username "migraatio@lupapiste.fi"
   :lastName "Migraatio"
   :firstName "Lupapiste"
   :role "authority"})

(def batchrun-user-data
  {:id "-"
   :username "eraajo@lupapiste.fi"
   :enabled true
   :lastName "Er\u00e4ajo"
   :firstName "Lupapiste"
   :role "authority"})

(defn batchrun-user [org-ids]
  (let [org-authz (reduce (fn [m org-id] (assoc m (keyword org-id) #{:authority})) {} org-ids)]
    (assoc batchrun-user-data :orgAuthz org-authz)))

(defn user-is-archivist? [user organization]
  (let [archive-orgs (organization-ids-by-roles user #{:archivist})
        org-set (if organization (set/intersection #{organization} archive-orgs) archive-orgs)]
    (and (seq org-set) (org/some-organization-has-archive-enabled? org-set))))

(defn user-is-pure-digitizer? [user]
  (let [all-roles (apply set/union (vals (:orgAuthz (with-org-auth user))))]
    (and (authority? user)
         (every? #(= % :digitizer) all-roles))))

(defn check-password-pre-check [{{:keys [password]} :data user :user}]
  (when-not (security/check-password password
                                     (some-<>> user
                                               :id
                                               (mongo/by-id :users <> {:private.password true})
                                               :private
                                               :password))
    (fail :error.password)))

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
                (assoc query :username (ss/canonize-email username))
                query)
        query (if-let [email (:email query)]
                (assoc query :email (ss/canonize-email email))
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

(defn find-authorized-users-in-org [org-id org-authz]
  (mongo/select :users
                {(str "orgAuthz." org-id) {$in org-authz} :role "authority" :enabled true}
                summary-keys
                (array-map :lastName 1, :firstName 1)))

(defn authority-users-in-organizations [org-ids]
  (mongo/select :users
                {$and [{:role "authority" :enabled true} (org-authz-match org-ids "authority")]}
                [:id :username :firstName :lastName :email]
                (array-map :lastName 1, :firstName 1)))

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
                                  (map #(re-pattern (str "(?i)" %)) searches))))))

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
  (mongo/any? :logins {:_id (ss/canonize-email username)
                       :failed-logins {$gte (env/value :login :allowed-failures)}
                       :locked {$gt (logins-lock-expires-date)}}))

(defn login-failed [username]
  {:pre [username]}
  (mongo/remove-many :logins {:locked {$lte (logins-lock-expires-date)}})
  (mongo/update :logins {:_id (ss/canonize-email username)}
                {$set {:locked (java.util.Date.)}, $inc {:failed-logins 1}}
                :multi false
                :upsert true))

(defn clear-logins [username]
  {:pre [username]}
  (mongo/remove :logins (ss/canonize-email username)))

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

(defn get-user-by-oauth-id [client-id]
  {:pre [client-id]}
  (get-user {:oauth.client-id client-id}))

(defn email-in-use? [email]
  (as-> email $
    (get-user-by-email $)
    (and $ (not (dummy? $)))))

;; note: new user registration messages need to send a message even though
;; the user is not enabled, and some messages are intentionally sent directly
;; to an email address without having an associated user id to check.
;; Also when adding statement givers, user is disabled, but is sent a token link for password setting
(defn email-recipient?
  "If :id is present, check that the corresponding user is enabled or a dummy one.
   If not enabled, returns true if password is empty (reset password, statement giver creation).
   True otherwise."
  [user]
  (let [id (:id user)]
    (boolean
      (or (not id)
          (when-let [user (find-user {:id id})]
            (or (= (:role user) "dummy")
                (or (:enabled user)
                    (ss/blank? (get-in user [:private :password])))))))))

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
  (when (ss/blank? (:email user-data))
    (fail! :error.email))

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

    (when-not (#{:authority :authorityAdmin :applicant :dummy :financialAuthority} user-role)
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

    (when (and organization-id (not (org/get-organization organization-id)))
      (fail! :error.organization-not-found))

    (when (and (:apikey user-data) (not admin?))
      (fail! :error.unauthorized :desc "only admin can create create users with apikey"))

    ;; TODO validate against schema!
    )

  true)

(defn- create-new-user-entity [{:keys [enabled password] :as user-data}]
  (let [email (ss/canonize-email (:email user-data))]
    (-<> user-data
      (select-keys [:email :username :role :firstName :lastName :personId :personIdSource
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
        (if-let [field (and (= 11000 (.getErrorCode e))
                            (second (re-find #"E11000 duplicate key error (?:index|collection): lupapiste\.users (?:\.\$)|(?:index\:\s)([^\s._]+)"
                                             (.getMessage e))))]
          (do
            (warnf "Duplicate key detected when inserting new user %s: field=%s" email field)
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

(def ^:private system-user-sanization-regexp
  (->> (range 97 123)
       (map char)
       (apply str)
       (format "[^%s-]+")
       re-pattern))

(defn municipality-name->system-user-email [name]
  (format "jarjestelmatunnus.%s@lupapiste.fi"
          (-> (ss/lower-case name)
              (ss/replace #"\s" "")
              (ss/replace #"[\u00e4\u00e5]" "a")
              (ss/replace #"[\u00f6]" "o")
              (ss/replace system-user-sanization-regexp "_"))))

(defn create-system-user [name email organization-ids]
  (->> {:id (mongo/create-id)
        :username email
        :email email
        :firstName  "J\u00e4rjestelm\u00e4tunnus"
        :lastName  name
        :role  :authority
        :enabled true
        :orgAuthz  (zipmap organization-ids (repeat ["reader"]))
        :language :fi}
       (mongo/insert :users))
  {:username email})

(defn get-or-create-user-by-email [email current-user]
  (let [email (ss/canonize-email email)]
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
        n      (mongo/update-n :users {:email (ss/canonize-email email)} {$set {:private.apikey apikey}})]
    (when-not (= n 1) (fail! :error.user-not-found))
    apikey))

(defn get-apikey
  "User's apikey or nil."
  [email]
  (get-in (find-user {:email email}) [:private :apikey]))

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
        email             (ss/canonize-email email)
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
    (let [query (merge {:email (ss/canonize-email email)} extra-query)
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
