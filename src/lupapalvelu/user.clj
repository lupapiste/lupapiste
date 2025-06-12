(ns lupapalvelu.user
  (:require [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [lupapalvelu.http.csrf :as csrf]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :refer [debugf error errorf info warn warnf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.security :as security]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user-enums :as user-enums]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer [def- fail fail! now ok]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.session :as ssess]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as sv]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer :all])
  (:import [java.util.regex Pattern]))

(set! *warn-on-reflection* true)

;;
;; User schema
;;

(defschema SearchFilter
  {:id     sc/Str
   :title  sc/Str
   :sort   {:field (sc/enum "type" "location" "applicant" "submitted" "modified"
                            "state" "handler" "foreman" "foremanRole" "id")
            :asc   sc/Bool}
   :filter (st/optional-keys {:handlers                 [sc/Str]
                              :tags                     [sc/Str]
                              :companyTags              [sc/Str]
                              :operations               [sc/Str]
                              :organizations            [sc/Str]
                              :areas                    [sc/Str]
                              :event                    [sc/Str]
                              :states                   [sc/Str]
                              :foremanRoles             [sc/Str]
                              :text                     sc/Str
                              :role                     sc/Str
                              :includeConverted         sc/Bool
                              :includePaperApplications sc/Bool})})

(def Id (ssc/min-length-string 1)) ; Variation of user ids in different environments is too diverse for a simple customized schema.

(def all-roles
  "set of role strings that can be used in User's :role field"
  (-> (set (map name roles/all-user-roles))
      (disj "anonymous"))) ; anonymous used only in commands

(defschema Role (apply sc/enum all-roles))
(defschema OrgId (sc/pred keyword? "Organization ID"))
(defschema KwOrgAuthzRoles (apply sc/enum roles/all-org-authz-roles))
(defschema StrOrgAuthzRoles (apply sc/enum (map name roles/all-org-authz-roles)))
(defschema OrgAuthzRoles (sc/cond-pre StrOrgAuthzRoles KwOrgAuthzRoles))
(defschema OrgAuthz {OrgId (sc/cond-pre [OrgAuthzRoles] #{OrgAuthzRoles})})
(defschema PersonIdSource (sc/enum "identification-service" "user" "AD" "foreign-id" "eIDAS"))
(defschema EidasFields {:eidasId         ssc/NonBlankStr
                        :firstName       ssc/NonBlankStr ; same field as non-eIDAS
                        :eidasFamilyName ssc/NonBlankStr
                        :eidasBirthDate  ssc/NonBlankStr})
(defschema OauthRole (->> roles/oauth-roles
                          (map name)
                          set
                          (apply sc/enum)))

(defschema User
  (st/merge
    {:id        Id
     :firstName ssc/default-max-length-string
     :lastName  ssc/default-max-length-string
     :role      Role
     :email     ssc/Email
     :username  ssc/Username
     :enabled   sc/Bool}
    (st/optional-keys
      {:state                     (sc/enum "erased")
       :private                   (st/optional-keys {:password sc/Str
                                                     :apikey   sc/Str})
       :orgAuthz                  OrgAuthz
       :personId                  (sc/if ss/blank? ssc/BlankStr ssc/Hetu)
       :personIdSource            PersonIdSource
       :eidas                     [EidasFields]
       :street                    (sc/maybe ssc/default-max-length-string)
       :city                      (sc/maybe ssc/default-max-length-string)
       :zip                       (sc/if ss/blank? ssc/BlankStr ssc/LegacyZipcode)
       :phone                     (sc/maybe ssc/default-max-length-string)
       :architect                 sc/Bool
       :degree                    (sc/maybe (apply sc/enum "" "other" user-enums/koulutusvalinta))
       :graduatingYear            (sc/if ss/blank? ssc/BlankStr (ssc/fixed-length-string 4))
       :fise                      ssc/default-max-length-string
       :fiseKelpoisuus            (sc/maybe (apply sc/enum "" user-enums/fise-kelpoisuus-lajit))
       :companyName               ssc/default-max-length-string
       :companyId                 (sc/if ss/blank? ssc/BlankStr ssc/FinnishY)
       :allowDirectMarketing      sc/Bool
       :attachments               [{:attachment-type {:type-group sc/Str, :type-id sc/Str}
                                    :attachment-id   sc/Str
                                    :file-name       sc/Str
                                    :content-type    sc/Str
                                    :size            sc/Num
                                    :created         ssc/Timestamp
                                    :storageSystem   sssc/StorageSystem}]
       :company                   {:id     sc/Str
                                   :role   (sc/enum "admin" "user")
                                   :submit sc/Bool}
       ;; DEPRECATED; kept here for legacy
       :partnerApplications       {(sc/optional-key :rakentajafi) {:id      sc/Str
                                                                   :created ssc/Timestamp
                                                                   :origin  sc/Bool}}
       :notification              (st/optional-keys
                                    {:messageI18nkey sc/Str
                                     :titleI18nkey   sc/Str
                                     :message        sc/Str
                                     :title          sc/Str
                                     :id             sc/Str})
       :defaultFilter             (st/optional-keys
                                    {:id                      (sc/maybe sc/Str)
                                     :foremanFilterId         (sc/maybe sc/Str)
                                     :foremanDocumentFilterId (sc/maybe sc/Str)
                                     :companyFilterId         (sc/maybe sc/Str)})
       :applicationFilters        [SearchFilter]
       :foremanFilters            [SearchFilter]
       :foremanDocumentFilters    [SearchFilter]
       :companyFilters            [SearchFilter]
       :language                  i18n/EnumSupportedLanguages
       :seen-organization-links   {sc/Keyword ssc/Timestamp}
       :firstLogin                sc/Bool
       :oauth                     {;; Oauth access token's client-id will be matched to this client-id to resolve
                                   ;; client-type when roles from /rest/user endpoint are requested
                                   :client-id                        sc/Str
                                   :client-secret                    sc/Str
                                   :scopes                           [(sc/enum "read" "pay")]
                                   :display-name                     (i18n/lenient-localization-schema sc/Str)
                                   :callback-url                     ssc/HttpUrl
                                   (sc/optional-key :token-minutes)  ssc/PosInt
                                   (sc/optional-key :registration?)  sc/Bool
                                   ;; By default, :client-type will be the same as oauth-client's client-id. :client-type
                                   ;; field can be used to override that to enable multiple oauth-clients with the same
                                   ;; client-type functionality in lupapalvelu.rest.util/get-organization-roles
                                   (sc/optional-key :client-type)    ssc/NonBlankStr
                                   ;; If client-type is resolved to :bi-app, :client-version will be matched to
                                   ;; organization :reporting-system to
                                   (sc/optional-key :client-version) ssc/NonBlankStr}
       :oauth-consent             [{:client-id sc/Str
                                    :scopes    [sc/Str]}]
       :oauth-roles               [{:client-type                    ssc/NonBlankStr
                                    (sc/optional-key :organization) sc/Str
                                    :role                           OauthRole}]
       :ad-login                  sc/Bool
       :_sheriff-notes            sc/Any
       :digitization-project-user sc/Bool
       :password-set-at           ssc/Timestamp
       :passwordMeetsRequirements sc/Bool})))


(defschema AdminCreateUser
  "Schema for creating new users by adminAdmin and authorityAdmin."
  (-> User
      ; Limit the keys that can be given here:
      (st/select-keys [:email
                       :username :firstName :lastName
                       :role :orgAuthz
                       :personId :personIdSource
                       :phone :city :street :zip
                       :language
                       :architect
                       :allowDirectMarketing
                       :graduatingYear :degree :fise :fiseKelpoisuus
                       :digitization-project-user])
      ; Limit the roles that can be created here:
      (st/assoc :role (apply sc/enum #{"applicant"
                                       "authority"
                                       "dummy"
                                       "financialAuthority"}))
      ; All keys are optional, except :email and :role
      (st/optional-keys)
      (st/required-keys [:email :role])))

(defschema AdminCreateAsiointiApiUser
  (-> User
      (st/select-keys [:username
                       :orgAuthz])))

;; NewUser, shape of new users:
;; * new user does not have :id
;; * role "admin" is not allowed

(defschema NewUser (-> User
                       (st/dissoc :id)
                       (st/assoc :role (apply sc/enum (-> all-roles
                                                          (disj "admin"))))))

(def user-keys (->> User keys (map sc/explicit-schema-key) set))

(defschema RegisterUser
  (st/merge
    {:email                ssc/Email
     :street               (sc/maybe ssc/default-max-length-string)
     :city                 (sc/maybe ssc/default-max-length-string)
     :zip                  (sc/if ss/blank? ssc/BlankStr ssc/LegacyZipcode)
     :phone                (sc/maybe ssc/default-max-length-string)
     :allowDirectMarketing sc/Bool
     :stamp                (sc/maybe ssc/default-max-length-string)
     :password             ssc/default-max-length-string}
    (st/optional-keys
      {:architect      sc/Bool
       :degree         (sc/maybe (apply sc/enum "" "other" user-enums/koulutusvalinta))
       :graduatingYear (sc/if ss/blank? ssc/BlankStr (ssc/fixed-length-string 4))
       :fise           ssc/default-max-length-string
       :fiseKelpoisuus (sc/maybe (apply sc/enum "" user-enums/fise-kelpoisuus-lajit))
       :language       i18n/EnumSupportedLanguages})))

(defschema Handler
  {:id     ssc/ObjectIdStr
   :userId (:id User)
   :roleId ssc/ObjectIdStr})

(defschema UserForRestEndpoint
  (st/merge (st/select-keys User [:id :role :email :firstName :lastName])
            (st/optional-keys {:company       {:id sc/Str :name sc/Str}
                               :organizations [{:id sc/Str :roles [sc/Str]}]})))

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

(defn full-name
  "Firstname Lastname. Blank name parts are omitted."
  [{:keys [firstName lastName]}]
  (->> [firstName lastName]
       (map ss/trim)
       (ss/join-non-blanks " ")))

(defn non-private
  "Returns user without private details."
  [user]
  (dissoc user :private))

(defn create-handler [handler-id role-id {user-id :id first-name :firstName last-name :lastName}]
  {:id        (or handler-id (mongo/create-id))
   :roleId    role-id
   :userId    user-id
   :firstName first-name
   :lastName  last-name})

(def summary-keys [:id :username :firstName :lastName :role])

(defschema SummaryUser (st/select-keys User summary-keys))

(defn summary
  "Returns common information about the user or nil"
  [user]
  (when user
    (select-keys user summary-keys)))

(defn summary-for-search-filter
  "Returns common information about the user or nil"
  [user & additional-fields]
  (when user
    (select-keys user (concat summary-keys
                              [:email :enabled]
                              additional-fields))))

(defn coerce-org-authz
  "Coerces orgAuthz to schema {Keyword #{Keyword}}"
  [org-authz]
  (into {} (for [[k v] org-authz] [k (set (map keyword v))])))

(defn with-org-auth [user]
  (update user :orgAuthz coerce-org-authz))

(def session-summary-keys [:id :username :firstName :lastName :role :email :organizations :company :architect :orgAuthz :language])

(defschema SessionSummaryUser
  (-> (st/select-keys User session-summary-keys)
      (st/assoc (sc/optional-key :orgAuthz) {sc/Keyword #{sc/Keyword}}
                (sc/optional-key :impersonating) sc/Bool
                (sc/optional-key :ad-login?) sc/Bool
                :expires ssc/Timestamp)))

(defn session-summary
  "Returns common information about the user to be stored in session or nil"
  [user]
  (when user
    (-> user
        (select-keys session-summary-keys)
        with-org-auth
        (assoc :expires (date/timestamp (date/plus (date/now) :minutes 5))))))

(defn oir-authority? [{role :role}]
  (contains? #{:oirAuthority} (keyword role)))

(defn virtual-user?
  "True if user exists only in session, not in database"
  [{:keys [impersonating] :as user}]
  (or
    impersonating
    (oir-authority? user)))

(defn- user-in-roles? [roles user]
  (boolean (-> user :role keyword roles)))

(def authority? (partial user-in-roles? #{:authority}))
(def applicant? (partial user-in-roles? #{:applicant}))
(def rest-user? (partial user-in-roles? #{:rest-api}))
(def asiointi-api-user? (partial user-in-roles? #{:asiointi-api}))
(def admin? (partial user-in-roles? #{:admin}))
(def dummy? (partial user-in-roles? #{:dummy}))
(def docstore-user? (partial user-in-roles? #{:onkalo-api :docstore-api}))
(def financial-authority? (partial user-in-roles? #{:financialAuthority}))
(def onkalo-user? (partial user-in-roles? #{:onkalo-api}))
(def reporting-user? (partial user-in-roles? #{:reporter}))

(defn- user-in-company-roles? [roles user]
  (-> user :company :role keyword roles))

(def company-user? (partial user-in-company-roles? #{:user}))
(def company-admin? (partial user-in-company-roles? #{:admin}))

(defn verified-person-id? [{pid :personId source :personIdSource}]
  (and (ss/not-blank? pid) (util/=as-kw :identification-service source)))

(defn same-user? [{id1 :id} {id2 :id}]
  (= id1 id2))

(defn validate-authority
  "Validator: current user must be an authority. To be used in commands'
   :pre-check vectors."
  [command]
  (if (authority? (:user command))
    nil
    (fail :error.unauthorized :desc "user is not an authority")))

(defn user-in-state? [expected-state {:keys [state]}]
  (true? (and state (= (name state) expected-state))))

(def erased? (partial user-in-state? "erased"))

(defn organization-ids
  "Returns user's organizations as a set of strings"
  [{org-authz :orgAuthz}]
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

(def organization-ids-by-roles roles/organization-ids-by-roles)

(defn user-is-authority-in-organization? [user organization-id]
  (let [org-set (organization-ids-by-roles user roles/default-org-authz-roles)]
    (contains? org-set organization-id)))

(defn user-has-role-in-organization? [user organization-id valid-org-authz-roles]
  (let [org-set (organization-ids-by-roles user (set valid-org-authz-roles))]
    (contains? org-set organization-id)))

(defn org-authz-match [organization-ids & [role]]
  {$or (for [org-id organization-ids] {(util/kw-path :orgAuthz org-id) (or role {$exists true})})})

(defn oauth-role-match [organization-id allowed-oauth-roles]
  {:oauth-roles
   {$elemMatch {:organization organization-id
                :role         {$in allowed-oauth-roles}}}})

(def migration-user-summary
  {:id        "migration-user"
   :username  "migraatio@lupapiste.fi"
   :lastName  "Migraatio"
   :firstName "Lupapiste"
   :role      "authority"})

(def batchrun-user-data
  {:id        "batchrun-user"
   :username  "eraajo@lupapiste.fi"
   :enabled   true
   :lastName  "Eräajo"
   :firstName "Lupapiste"
   :role      "authority"})

(defn batchrun-user [org-ids]
  (let [org-authz (reduce (fn [m org-id] (assoc m (keyword org-id) #{:authority})) {} org-ids)]
    (assoc batchrun-user-data :orgAuthz org-authz)))

(defn batchrun-user? [{:keys [id]}]
  (= id (:id batchrun-user-data)))

(defn user-is-archivist? [user organization]
  (let [archive-orgs (organization-ids-by-roles user #{:archivist})
        org-set      (if organization (set/intersection #{organization} archive-orgs) archive-orgs)]
    (and (seq org-set) (org/some-organization-has-archive-enabled? org-set))))

(defn precheck-user-is-archivist [{user :user {:keys [organization]} :application}]
  (when (and (map? (:orgAuthz user))
             (string? organization)
             (not (-> (with-org-auth user)
                      :orgAuthz
                      (get (keyword organization))
                      (contains? :archivist))))
    (fail :error.unauthorized)))

(defn user-is-pure-digitizer?
  "True if the user is authority but has only digitizer auths."
  [user]
  (boolean
    (and (authority? user)
         (some->> (with-org-auth user)
                  :orgAuthz
                  vals
                  (apply concat)
                  set
                  (= #{:digitizer})))))

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
                      (assoc (util/kw-path :orgAuthz organization 0) {$exists true})
                    (dissoc :organization))
                query)]
    query))

(defn find-user
  ([query] (mongo/select-one :users (user-query query)))
  ([query projection] (mongo/select-one :users (user-query query) projection)))

(defn find-users
  ([query]
   (mongo/select :users (user-query query)))
  ([query order-by]
   (mongo/select-ordered :users (user-query query) order-by)))

(defn find-authorized-users-in-org
  ([org-id org-authz ks]
   (mongo/select :users
                 {(str "orgAuthz." org-id) {$in org-authz} :role "authority" :enabled true}
                 ks
                 (array-map :lastName 1, :firstName 1)))
  ([org-id org-authz]
   (find-authorized-users-in-org org-id org-authz summary-keys)))

(defn authority-users-in-organizations [org-ids]
  (mongo/select :users
                {$and [{:role "authority" :enabled true} (org-authz-match org-ids "authority")]}
                [:id :username :firstName :lastName :email]
                (array-map :lastName 1, :firstName 1)))

(defn authority-admin?
  "User is :authorityAdmin in some organization. You should probably use `authority-admin-in?` instead."
  [caller]
  (boolean (seq (organization-ids-by-roles caller #{:authorityAdmin}))))

(defn authority-admin-in? [org-id user]
  (contains? (get-in user [:orgAuthz (keyword org-id)]) :authorityAdmin))

;;
;; jQuery data-tables support:
;;

(defn- role-query
  [role organizations]
  (let [role-kw (keyword role)]
    (case role-kw
      nil       nil
      (:authorityAdmin
       :biller) (some->> (or (seq organizations)
                             (map :id (mongo/select :organizations {} [:id])))
                         (map #(hash-map (util/kw-path :orgAuthz %) role-kw))
                         (hash-map $or)
                         (merge (when (= :authorityAdmin role-kw)
                                  {:role :authority})))
      {:role role})))

(defn- users-for-datatables-base-query [caller organizationId {:keys [organizations filter-role filter-enabled]}]
  (let [organizations (if (admin? caller)
                        organizations ; admin can access every org but only wants these
                        (let [administrated-orgs (into #{} (map name)
                                                       (-> caller :orgAuthz roles/transpose-orgAuthz
                                                           :authorityAdmin))]
                          (filter administrated-orgs ; authority-admin can only access these orgs
                                  (or organizations ; authority-admin explicitly requested these
                                      [organizationId])))) ; fall back to currently active organization
        role          (if (admin? caller) filter-role :authority)
        enabled       (if (or (admin? caller) (authority-admin? caller)) filter-enabled true)]
    (merge {}
           (when (seq organizations) {:organizations organizations})
           (role-query role organizations)
           (when-not (nil? enabled) {:enabled enabled}))))

(defn- users-for-datatables-query [base-query {:keys [filter-search]}]
  (if (ss/blank? filter-search)
    base-query
    (let [searches (ss/split filter-search #"\s+")]
      (assoc base-query $and (map (fn [t]
                                    {$or (map hash-map
                                              [:email :firstName :lastName]
                                              (repeat t))})
                                  (map #(re-pattern (str "(?i)" (Pattern/quote %))) searches))))))

(defn- limit-organizations [query]
  (if-let [org-ids (:organizations query)]
    {$and [(dissoc query :organizations) (org-authz-match org-ids)]}
    query))

(defn users-for-datatables [caller organizationId params]
  (let [base-query       (users-for-datatables-base-query caller organizationId params)
        base-query-total (mongo/count :users (limit-organizations base-query))
        query            (limit-organizations (users-for-datatables-query base-query params))
        query-total      (mongo/count :users query)
        users            (mongo/with-collection "users"
                           (query/find query)
                           (query/fields (cond-> [:email :firstName :lastName
                                                  :role :orgAuthz :oauth-roles :enabled :digitization-project-user]
                                           (admin? caller) (conj :allowDirectMarketing :username)))
                           (query/skip (util/->int (:start params) 0))
                           (query/limit (util/->int (:length params) 16)))]
    {:rows    (into [] users)
     :total   base-query-total
     :display query-total
     :draw    (str (util/->int (str (:draw params))))}))

;;
;; ==============================================================================
;; Login throttle
;; ==============================================================================
;;

(defn- logins-lock-expires-date []
  (.toInstant (date/minus (date/now) :seconds (env/value :login :throttle-expires))))

(defn throttle-login? [username]
  {:pre [username]}
  (mongo/any? :logins {:_id           (ss/canonize-email username)
                       :failed-logins {$gte (env/value :login :allowed-failures)}
                       :locked        {$gt (logins-lock-expires-date)}}))

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

(defn get-user
  ([q] (non-private (find-user q)))
  ([q projection] (non-private (find-user q projection))))

(defn get-users
  ([q]
   (map non-private (find-users q)))
  ([q order-by]
   (map non-private (find-users q order-by))))

(defn get-user-by-id
  ([id] {:pre [id]}
   (get-user {:id id}))
  ([id projection] {:pre [id]}
   (get-user {:id id} projection)))

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

(defn passwordless-login-accounts-query
  [hetu]
  {:personId       hetu
   :personIdSource "identification-service"
   :enabled        true})

(defn get-users-by-person-id
  ([person-id]
   (mongo/select :users (passwordless-login-accounts-query person-id)))
  ([person-id projection]
   (mongo/select :users (passwordless-login-accounts-query person-id) projection)))

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

(defn- get-user-with-field
  "Finds user, whose `k` property equals `v` and returns it. The resulting user is enabled
  and has passed through `session-summary` (orgAuthz are coerced, no extra fields). Nil,
  if `v` is blank or an enabled user is not found."
  [k v]
  (when-not (ss/blank? v)
    (let [user (find-user {k v})]
      (when (:enabled user)
        (session-summary user)))))

(defn get-user-with-apikey
  "_Session_ user via `apikey or nil`."
  [apikey]
  (get-user-with-field :private.apikey apikey))

(defn get-user-with-token
  "_Session_ user via token or nil."
  [{:keys [user-id]}]
  (get-user-with-field :_id user-id))

(defmacro with-user-by-email [email & body]
  `(let [~'user (get-user-by-email ~email)]
     (when-not ~'user
       (debugf "user '%s' not found with email" ~email)
       (fail! :error.user-not-found :email ~email))
     ~@body))

(defn coerce-person-id
  "Takes a string that is either:
   a) already a personal identification number and leaves it as is or
   b) a Lupapiste user id, which is then used to get the user's personal id, if any"
  [id]
  (let [user (delay (get-user-by-id id))]
    (cond
      (not (string? id)) (errorf "Non-string personId: '%s'" id)
      (sv/valid-hetu? id) id
      (some? @user) (if-let [person-id (:personId @user)]
                      person-id
                      (errorf "User %s doesn't have personId" id))
      :else (errorf "Couldn't find user id: '%s'" id))))

;;
;; ==============================================================================
;; Create user:
;; ==============================================================================
;;

(def new-user-checker (sc/checker NewUser))

(def create-new-user-rules
  [{:desc  "user data matches User schema"
    :error :error.missing-parameters
    :fail? (fn [{:keys [user-data]}]
             (when-let [schema-errors (new-user-checker user-data)]
               ;; Help problem tracing by adding a stack trace to logs
               (error (ex-info "stack trace" {}) "new user does not match NewUser schema" schema-errors)
               true))}

   {:desc  "user-data has orgAuthz in expected form" ; keys as keyword, roles as string
    :error :error.missing-parameters
    :fail? (fn [{{:keys [orgAuthz]} :user-data}]
             (when orgAuthz
               (not (and (every? keyword? (keys orgAuthz))
                         (every? string? (apply concat (vals orgAuthz)))))))}

   {:desc  "caller has been run through 'with-org-auth'"
    :error :error.missing-parameters
    :fail? (fn [{{:keys [orgAuthz]} :caller}]
             (when (seq orgAuthz)
               (not (and (every? keyword? (keys orgAuthz))
                         (every? set? (vals orgAuthz))
                         (every? keyword? (apply concat (vals orgAuthz)))))))}

   {:desc  "applicant can not create other than dummy users"
    :error :error.unauthorized
    :fail? (fn [{{data-role :role} :user-data
                 {:keys [role]}    :caller}]
             (and (= role "applicant")
                  (not= "dummy" data-role)))}

   {:desc  "applicants are born via registration"
    :error :error.unauthorized
    :fail? (fn [{{:keys [role personIdSource]} :user-data
                 :keys                         [caller]}]
             (and (= role "applicant")
                  (not= personIdSource "foreign-id")
                  caller))}

   {:desc  "applicants may not have an organizations"
    :error :error.unauthorized
    :fail? (fn [{{:keys [role orgAuthz]} :user-data}]
             (and (= role "applicant") (seq orgAuthz)))}

   {:desc  "authorityAdmin can create users into his/her own organizations only"
    :error :error.unauthorized
    :fail? (fn [{:keys [caller user-data]}]
             (and (-> caller :role (not= "admin"))
                  (not (every? (organization-ids-by-roles caller #{:authorityAdmin})
                               (map name (-> user-data :orgAuthz keys))))))}

   {:desc  "dummy user may not have an organization roles"
    :error :error.unauthorized
    :fail? (fn [{{:keys [role orgAuthz]} :user-data}]
             (and (= role "dummy")
                  (seq orgAuthz)))}

   ;; In principle, the "authorityAdmin can create users into his/her own organizations only" test
   ;; should cover this. The only way this test traps errors is that the organization was deleted, but
   ;; some authority still has the roles for deleted organization in her :authOrgz.
   {:desc  "all organizations must be known"
    :error :error.organization-not-found
    :fail? (fn [{{:keys [orgAuthz]} :user-data
                 :keys              [known-organizations?]}]
             (not (known-organizations? (keys orgAuthz))))}

   {:desc  "only admin can create create users with apikey"
    :error :error.unauthorized
    :fail? (fn [{:keys [caller apikey]}]
             (and apikey (-> caller :role (not= "admin"))))}])

(defn- new-user-error [data]
  (some (fn [{:keys [fail?] :as rule}]
          (when (fail? data)
            rule))
        create-new-user-rules))

(defn- validate-create-new-user! [caller user-data]
  (when-let [e (new-user-error {:caller               caller
                                :user-data            user-data
                                :known-organizations? org/known-organizations?})]
    (fail! (:error e) :desc (:desc e)))
  user-data)

(defn- create-new-user-entity [{:as user-data :keys [password]}]
  (let [email (-> user-data :email ss/canonize-email)]
    (-> user-data
        (select-keys user-keys)
        (assoc :email email)
        (->> (merge {:firstName "" :lastName email :username email}))
        (update :role (fn [role] (if (keyword? role) (name role) role)))
        (assoc :enabled (-> user-data :enabled str (= "true"))
               :private (if password
                          {:password (security/get-hash password)}
                          {}))
        (cond-> password (assoc :password-set-at (now)))
        ss/trimwalk)))

(defn create-new-user
  "Insert new user to database, returns new user data without private information. If user
   exists and has role \"dummy\", overwrites users information. If users exists with any other
   role, throws exception. Caller should have gone through 'lupapalvelu.user/with-org-authz'."
  [caller user-data]
  (let [user-data (->> user-data
                       (create-new-user-entity)
                       (validate-create-new-user! caller))
        old-user  (get-user-by-email (:email user-data))
        new-user  (if old-user
                    (assoc user-data :id (:id old-user))
                    (assoc user-data :id (mongo/create-id)))
        email     (:email new-user)
        {old-id :id old-role :role} old-user
        new-user  (if (applicant? user-data)
                    (assoc new-user :notification {:titleI18nkey   "user.notification.firstLogin.title"
                                                   :messageI18nkey "user.notification.firstLogin.message"})
                    new-user)]
    (try
      (condp = old-role
        nil (do
              (info "creating new user" (dissoc new-user :private :personId))
              (mongo/insert :users new-user))
        "dummy" (do
                  (info "rewriting over dummy user:" old-id (dissoc new-user :private :id))
                  (mongo/update-by-id :users old-id (dissoc new-user :id)))
        ; LUPA-1146
        "applicant" (if (and (= (:personId old-user) (:personId new-user))
                             (not (:enabled old-user)))
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
   Only for Lupapiste admin"
  [caller user-data]
  (let [pw        (security/random-password)
        user-data (merge user-data
                         {:enabled  true
                          :role     "rest-api"
                          :orgAuthz {(keyword (:organization user-data)) ["authority"]}
                          :password pw})
        user      (->> (create-new-user-entity user-data)
                       (validate-create-new-user! caller))]
    (mongo/insert :users (assoc user :id (mongo/create-id)))
    {:username (:username user)
     :password pw}))

(defn create-asiointiapi-user
  "Creates and inserts new AsiointiApi user to database, returns username and password to frontend.
   Only for Lupapiste admin"
  [caller user-data]
  (let [pw        (security/random-password)
        user-data (merge user-data
                         {:enabled  true
                          :role     :asiointi-api
                          :password pw})
        user      (->> (create-new-user-entity user-data)
                       (validate-create-new-user! caller))]
    (mongo/insert :users (assoc user :id (mongo/create-id)))
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
              (ss/replace #"[äå]" "a")
              (ss/replace #"[ö]" "o")
              (ss/replace system-user-sanization-regexp "_"))))

(defn create-system-user [caller name email organization-ids]
  (let [user-data (->> {:username  email
                        :email     email
                        :firstName "Järjestelmätunnus"
                        :lastName  name
                        :role      "authority"
                        :enabled   true
                        :orgAuthz  (zipmap (map keyword organization-ids) (repeat ["reader"]))
                        :language  "fi"}
                       (validate-create-new-user! caller))]
    (mongo/insert :users (assoc user-data :id (mongo/create-id)))
    {:username email}))

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

(defn purely-authorityAdmin? [user]
  (let [auth-orgs (roles/transpose-orgAuthz (:orgAuthz user))]
    (= (keys auth-orgs) [:authorityAdmin])))

(defn applicationpage-for [{:keys [role] :as user}]
  (let [s (if (some? role) (name role) "")]
    (cond
      (or (ss/blank? s) (= s "dummy")) "applicant"
      (= s "oirAuthority") "oir"
      (= s "authority") (if (purely-authorityAdmin? user)
                          "authority-admin"
                          "authority")
      :else (csk/->kebab-case s))))

(defn user-in-role [user role & params]
  (merge (apply hash-map params) (assoc (summary user) :role role)))

;;
;; ==============================================================================
;; Current user:
;; ==============================================================================
;;


(defn setpw-response [user]
  {:ok             false
   :text           :error.password.must-reset
   :reset-password true
   :user           user})

(defn login-no-password [{:keys [username] :as user} command]
  (clear-logins username)
  (if-let [application-page (applicationpage-for (with-org-auth user))]
    (-> (ssess/merge-to-session
          command
          (ok :user (-> user with-org-auth non-private)
              :applicationpage application-page
              :lang (:language user))
          {:user (session-summary user)}
          ;; always rotate session after succesfull login to prevent session fixation attacks
          {:rotate-session? true})
        (update :cookies merge (csrf/new-csrf-cookie)))
    (do
      (error "Unknown user role:" (:role user))
      (fail :error.login))))

(defn login
  ([{{:keys [username password]} :data :as command}]
   (let [user* (delay (get-user-with-password username password))]
     (cond (throttle-login? username)
           (do
             (info "login throttled, username:" username)
             (fail :error.login-throttle))
           (and @user* (or (not (env/feature? :password-check))
                           (security/valid-password-score? password @user*)))
           (do
             (info "login successful, username:" username)
             (login-no-password @user* command))
           @user*
           (setpw-response @user*)
           :else
           (do
             (info "login failed, username:" username)
             (login-failed username)
             (fail :error.login)))))
  ([request username password]
   (login {:session (:session request)
           :data    {:username username :password password}})))

(defn current-user
  "fetches the current user from session"
  [request] (:user request))

;;
;; ==============================================================================
;; Creating API keys:
;; ==============================================================================
;;

(defn create-apikey
  "Add or replace users api key. User is identified by email. Returns apikey. If user is unknown throws an exception."
  [email]
  (let [apikey (security/random-password)
        n      (mongo/update-by-query :users {:email (ss/canonize-email email)} {$set {:private.apikey apikey}})]
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
  "Update users password. Returns nil. If user is not found or password is not sufficient,
  raises an exception.

  When current-session-id (the application-level :id from the session data map) is supplied,
  only the *other* sessions for this user are deleted, leaving the current session alive.

  NOTE: Session key must be rotated when current session left alive. Make sure the action
  returns for example: (ssess/merge-to-session command (ok) nil {:rotate-session? true})

  When current-session-id is not provided, all sessions for the user are deleted."
  ([email password] (change-password email password nil))
  ([email password current-session-id]
   (let [email (ss/canonize-email email)
         user  (get-user-by-email email)]
     (when-not user
       (fail! :error.user-not-found))
     (when-not (security/valid-password-length? password)
       (warn "Frontend failed to evaluate password length!")
       (fail! :error.password.minlength))
     (when-not (security/valid-password-score? password user)
       (warn "Frontend failed to evaluate password score!")
       (fail! :error.password.minscore))
     (let [salt            (security/dispense-salt)
           hashed-password (security/get-hash password salt)
           updated-user    (mongo/update-one-and-return :users
                                                        {:email email}
                                                        {$set   {:private.password          hashed-password
                                                                 :enabled                   true
                                                                 :password-set-at           (now)
                                                                 :passwordMeetsRequirements true}
                                                         $unset {:ad-login true}})]
       (if updated-user
         (do
           (mongo/remove-many :activation {:email email})
           (clear-logins (:username updated-user))
           (if current-session-id
             (ssess/delete-other-sessions-for-user! (:_id updated-user) current-session-id)
             (ssess/delete-sessions-for-user! (:_id updated-user))))
         (fail! :error.user-not-found))
       nil))))

;;
;; ==============================================================================
;; Updating user information:
;; ==============================================================================
;;

(defn update-user-by-email
  "Returns (ok) or (fail :error.user-not-found)"
  ([email data] (update-user-by-email email {} data))
  ([email extra-query data] (update-user-by-email email extra-query data false))
  ([email extra-query data raw-email?]
   {:pre [(string? email) (map? extra-query) (map? data)]}
   (let [email   (cond-> email
                   (not raw-email?) ss/canonize-email)
         query   (assoc extra-query :email email)
         updates (condp every? (keys data)
                   mongo/operator? data
                   (complement mongo/operator?) {$set data})]
     (if (pos? (mongo/update-by-query :users query updates))
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
                     {:_id  user-id
                      :role "dummy"}))


;;
;; ==============================================================================
;; Erase user information
;; ==============================================================================
;;

(def- erasure-strategy
  (into {}
        (map (fn [[k _]]
               (cond
                 ;; Retain id and role, anonymize other compulsory fields:
                 (contains? #{:id :role} k) [k :retain]
                 (not (sc/optional-key? k)) [k :anonymize]

                 ;; Anonymize state, remove other optional fields:
                 (= (:k k) :state) [(:k k) :anonymize]
                 :else [(:k k) :remove])))
        User))

(defn- anonymized-user [user-id]
  (let [email (str "poistunut_" user-id "@example.com")]
    {:firstName "Poistunut"
     :lastName  "Käyttäjä"
     :email     email
     :username  email
     :enabled   false
     :state     "erased"}))

(def- erasure-unsetter
  (into {} (for [[k v] erasure-strategy :when (= v :remove)] [k ""])))

(defn erase-user
  "Erases/anonymizes user information but retains the user record in database. Returns nil."
  [user-id]
  ;; Remove attachment files:
  (doseq [{:keys [attachment-id]} (:attachments (get-user-by-id user-id {:attachments 1}))]
    (storage/delete-user-attachment user-id attachment-id))

  ;; Erase user record:
  (mongo/update-by-id :users user-id
    {$set   (anonymized-user user-id)
     $unset erasure-unsetter})

  ;; Invalidate all active sessions for the erased user
  (ssess/delete-sessions-for-user! user-id))

;;
;; ==============================================================================
;; Strong authentication
;; ==============================================================================
;;
(defn strong-authentication-required? [user]
  (and (not (company-user? user))
       (not (financial-authority? user))))
