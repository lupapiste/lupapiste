(ns lupapalvelu.user-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defcommand defquery defraw email-validator] :as action]
            [lupapalvelu.activation :as activation]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.change-email :as change-email]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.ident.ad-login-util :as ad-util]
            [lupapalvelu.ident.ident-util :as ident-util]
            [lupapalvelu.logging :as log :refer [debug error info infof warn]]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.oauth.core :as oauth]
            [lupapalvelu.organization :as org]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.rest.rest-api :refer [defendpoint]]
            [lupapalvelu.rest.util :as rest-util]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.security :as security]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [lupapalvelu.user-utils :as uu]
            [lupapalvelu.vetuma :as vetuma]
            [monger.operators :refer :all]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.password :as spw]
            [sade.schemas :as ssc]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [slingshot.slingshot :refer [throw+ try+]]
            [swiss.arrows :refer :all]
            [lupapalvelu.system.util-components :as components]))

(set! *warn-on-reflection* true)

;;
;; ==============================================================================
;; Getting user and users:
;; ==============================================================================
;;

(defn ad-login-available? [{:keys [username]}]
  (when-let [domain (ad-login/username->domain username)]
    (ad-login/domain-enabled? domain)))

(defn show-reporting-link?
  "Returns true for all users that have access to Rami 2.0"
  [user]
  (->> (oauth/elevate-user-for-oauth-access user nil nil :bi-app "bi-app2")
       (rest-util/get-organization-roles :bi-app)
       (mapcat :roles)
       (some #(contains? roles/reporting-roles (keyword %)))
       boolean))

(defquery rami-access
  {:description "Returns ok for all users that have access to Rami 2.0, fails if
                 user does not have access to Rami 2.0"
   :feature     :rami-header-link
   :user-roles  roles/all-authenticated-user-roles
   :pre-checks  [(fn [{:keys [user]}]
                   (when-not (some-> user
                                     :email
                                     usr/get-user-by-email
                                     show-reporting-link?)
                     (fail :no-rami-access)))]}
  (ok))

(defn- get-user [user]
  (if (usr/virtual-user? user)
    user
    (let [full-user (usr/get-user-by-id (:id user))]
      (cond-> (dissoc full-user :private)
        (usr/verified-person-id? full-user) (dissoc :personId)
        :always (assoc :ad-login-available? (ad-login-available? full-user))))))

(defquery user
  {:on-success          (fn [_ {user :user}]
                          (when (:firstLogin user)
                            (mongo/update-by-id :users (:id user) {$unset {:firstLogin true}})))
   :user-roles          roles/all-authenticated-user-roles
   :allowed-token-types #{:map-3d-token}}
  [{:keys [user]}]
  (if-let [full-user (get-user user)]
    (ok :user (assoc full-user :virtual (usr/virtual-user? user)))
    (fail :error.user.not.found)))

(defquery users
  {:user-roles #{:admin}}
  [{:keys [data]}]
  (let [base-query (-> data
                       (set/rename-keys {:userId :id})
                       (select-keys [:id :role :email :username :firstName :lastName :enabled :allowDirectMarketing]))
        org-ids    (cond
                     (:organization data)  [(:organization data)]
                     (:organizations data) (:organizations data))
        query      (if (seq org-ids)
                     {$and [base-query, (usr/org-authz-match org-ids)]}
                     base-query)
        users      (usr/find-users query)]
    (ok :users (mapv (comp usr/with-org-auth usr/non-private) users))))

(defquery users-in-same-organizations
  {:description "Returns all 'authority' orgAuthzers from my organization(s)."
   :user-roles  #{:authority}}
  [{:keys [user]}]
  (if-let [organization-ids (seq (usr/organization-ids user))]
    (let [query {$and [{:role "authority" :enabled true}, (usr/org-authz-match organization-ids "authority")]}
          users (usr/find-users query)]
      (ok :users (mapv usr/summary-for-search-filter users)))
    (ok :users [])))

(defquery users-organizations-handling-times
  {:description "Returns the configured handling time(s) for my organization(s)"
   :user-roles  #{:authority}}
  [{:keys [user]}]
  (if-let [organization-ids (seq (usr/organization-ids user))]
    (->> (org/get-organizations {:_id {$in organization-ids}} [:handling-time])
         (filter :handling-time)
         (util/key-by :id)
         (util/map-values :handling-time)
         (ok :handling-times))
    (ok :handling-times {})))

(env/in-dev
  (defquery user-by-email
    {:parameters       [email]
     :input-validators [(partial action/non-blank-parameters [:email])]
     :feature          :dev-mode-supported
     :user-roles       #{:admin}}
    [_]
    (ok :user (usr/get-user-by-email email))))

(defquery users-for-datatables
  {:optional-parameters [organizationId]
   :permissions         [{:required [:users/admin-query]}]
   :pre-checks          [(fn [{{:keys [organizationId]} :data :keys [user]}]
                           (when organizationId
                             (when-not (or (usr/admin? user) (string? organizationId))
                               (fail :error.unknown-organization))))]}
  [{caller :user {params :params} :data}]
  (ok :data (usr/users-for-datatables caller organizationId params)))


(defquery user-for-edit-authority
  {:permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId authority-id]
   :input-validators [(partial action/non-blank-parameters [:authority-id])]}
  [{auth-admin :user}]
  (if-let [{:keys [username firstName lastName email orgAuthz oauth-roles]
            :as   authority} (usr/get-user {:id authority-id :organization organizationId})]
    (let [ad-confs (ad-login/fetch-enabled-ad-confs-for-organization organizationId)
          data     (util/assoc-when {:email                               email
                                     :username                            username
                                     :firstName                           firstName
                                     :lastName                            lastName
                                     :orgAuthz                            orgAuthz
                                     :ad-login?                           (ad-login/user-should-use-ad? username)
                                     :current-org-roles-maintained-in-ad? (ad-login/roles-maintained-in-ad?
                                                                            ad-confs
                                                                            authority)}
                                    :oauth-roles oauth-roles)]
      (ok :data data))
    (fail :error.unauthorized)))

(defquery user-contact-infos
  {:description      "Returns the user-id, name and address information for every authed person
  in the application. Implicit company account auths are excluded, but if a company user
  has personal auth, the company name is included. Also returns a flag stating whether the
  user has a verified personId (hetu or social security number) for suomifi messages."
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:required [:application/get-user-contact-info]}]}
  [{:keys [application]}]
  (->> (:auth application)
       (filter (fn [auth]
                 (and (util/not=as-kw (:type auth) :company)
                      (domain/no-pending-invites? application (:id auth)))))
       (keep (fn [{user-id :id}]
               (when-let [full-user (usr/get-user-by-id user-id)]
                 (-> full-user
                     (select-keys [:id :firstName :lastName :street :zip :city])
                     (assoc :hasPersonId (usr/verified-person-id? full-user))
                     (util/assoc-when :companyName (some-<> full-user :company :id
                                                            (mongo/by-id :companies <> [:name])
                                                            :name))))))
       (into [])
       (ok :users)))

(defquery ad-logged-in
  {:description "Pseudo-query that signals whether the user is logged in via AD."
   :user-roles  roles/all-authenticated-user-roles
   :pre-checks  [#(when-not (ad-login/ad-logged-in? %)
                    (fail :error.no-ad-login))]}
  [_]
  (ok))

(defendpoint "/rest/user"
  {:summary             "Returns details of the user associated to the provided access token"
   :description         ""
   :parameters          []
   :optional-parameters [:dummy-role sc/Str]
   :oauth-scope         :read
   :returns             usr/UserForRestEndpoint}
  (cond-> (rest-util/rest-user user)
    (and (env/feature? :dummy-rest-user-role) dummy-role) (assoc :role dummy-role)))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;
(defn- setpw-response [system caller user]
  ;; Don't auto-consume; allows reading token for password quality estimation
  (let [token (token/make-token :password-reset caller {:email (:email user)} :ttl ttl/create-user-token-ttl :auto-consume false)
        host  (components/system->host system)]
    (ok :id (:id user)
        :user user
        :linkFi (str host "/app/fi/welcome#!/setpw/" token)
        :linkSv (str host "/app/sv/welcome#!/setpw/" token))))

(defn- create-authority-user-with-organization [system caller new-organization email firstName lastName roles oauth-roles]
  (let [org-authz {(keyword new-organization) (into #{} roles)}
        user-data (-> {:email     email
                       :orgAuthz  org-authz
                       :role      :authority
                       :enabled   true
                       :firstName firstName
                       :lastName  lastName}
                      (util/assoc-when :oauth-roles oauth-roles))
        new-user  (usr/create-new-user caller user-data)]
    (infof "invitation for new authority user: email=%s, organization=%s" email new-organization)
    (uu/notify-new-authority system new-user caller new-organization)
    (ok :operation "invited")))

(defcommand create-user
  {:input-validators [usr/AdminCreateUser]
   :pre-checks       [org/orgAuthz-pre-checker]
   :notified         true
   :permissions      [{:required [:users/create]}]}
  [{user-data :data caller :user system :system}]
  (let [user (usr/create-new-user caller user-data)]
    (setpw-response system caller user)))

(defn integration-username->email
  [username]
  (str username "@example.com"))

(defcommand create-rest-api-user
  {:description      "Creates REST API user for organization. Admin only."
   :parameters       [username]
   :input-validators [(partial action/string-parameters [:username])
                      (partial action/ascii-parameters [:username])]
   :user-roles       #{:admin}}
  [{user-data :data caller :user}]
  (let [rest-user-email (integration-username->email username)
        user-data       (assoc user-data :email rest-user-email)]
    (when (usr/get-user-by-email rest-user-email)
      (fail! :email-in-use))
    (ok :user (usr/create-rest-user caller user-data))))

(defcommand create-asiointi-api-user
  {:description      "Creates an Asiointi API user for organization(s). Admin only."
   :parameters       [username orgAuthz]
   :input-validators [usr/AdminCreateAsiointiApiUser]
   :feature          :asiointi-api
   :pre-checks       [org/orgAuthz-pre-checker
                      (org/orgAuthz-roles-pre-checker-fn roles/integration-roles)]
   :user-roles       #{:admin}}
  [{caller :user}]
  (let [api-user-email (integration-username->email username)]
    (if (or (usr/get-user-by-email api-user-email)
            (usr/get-user {:username username}))
      (fail :email-in-use)
      (let [user-data (util/mmap {:email api-user-email}
                                 username orgAuthz)]
        (ok :user (usr/create-asiointiapi-user caller user-data))))))

(defcommand reset-asiointi-api-user-password
  {:description      "Assign a new random password to an Asiointi API user. Admin only."
   :parameters       [username]
   :input-validators [{:username ssc/Username}]
   :feature          :asiointi-api
   :user-roles       #{:admin}}
  [_]
  (let [api-user-email (integration-username->email username)
        user           (usr/get-user-by-email api-user-email)]
    (if (and user
             (usr/asiointi-api-user? user))
      (let [password (security/random-password)]
        (usr/change-password api-user-email password)
        (ok :password password))
      (fail :not-found))))

(defcommand modify-asiointi-api-user
  {:description      "Modify an Asiointi API user (orgAuthz only). Admin only."
   :parameters       [username orgAuthz]
   :input-validators [usr/AdminCreateAsiointiApiUser]
   :feature          :asiointi-api
   :pre-checks       [org/orgAuthz-pre-checker
                      (org/orgAuthz-roles-pre-checker-fn roles/integration-roles)]
   :user-roles       #{:admin}}
  [_]
  (let [api-user-email (integration-username->email username)
        user           (usr/get-user-by-email api-user-email)]
    (if (and user
             (usr/asiointi-api-user? user))
      (usr/update-user-by-email api-user-email {:orgAuthz orgAuthz})
      (fail :not-found))))

(defcommand create-system-user
  {:description      "Creates system user for embedded Lupapiste view in Facta. Admin only."
   :parameters       [municipality-name organization-ids]
   :input-validators [(partial action/string-parameters [:municipality-name])
                      (partial action/vector-parameter-of :organization-ids string?)]
   :user-roles       #{:admin}}
  [{caller :user}]
  (let [email (usr/municipality-name->system-user-email municipality-name)]
    (when (usr/get-user-by-email email)
      (fail! :email-in-use))
    (ok (usr/create-system-user caller municipality-name email organization-ids))))

(defcommand create-foreign-user
  {:description      "Creates applicant with :personIdSource 'foreign-id' or converts existing
                      not-enabled dummy user to enabled applicant with :personIdSource 'foreign-id'"
   :parameters       [:email]
   :user-roles       #{:admin}
   :input-validators [action/email-validator
                      (st/merge {:email ssc/NonBlankStr}
                                (st/optional-keys {:firstName ssc/NonBlankStr
                                                   :lastName  ssc/NonBlankStr}))]}
  [{user-data :data caller :user system :system}]
  (let [foreign-user-data {:enabled        true
                           :personIdSource "foreign-id"
                           :role           "applicant"}
        user (usr/create-new-user caller (merge user-data foreign-user-data))]
    (setpw-response system caller user)))

;;
;; ==============================================================================
;; Updating user data:
;; ==============================================================================
;;

;;
;; General changes:
;;

(def- user-data-editable-fields [:firstName :lastName :street :city :zip :phone :language
                                 :architect :degree :graduatingYear :fise :fiseKelpoisuus
                                 :companyName :companyId :allowDirectMarketing :personId])

;; Define schema for update data
(def- UserUpdate (dissoc usr/User :id :role :email :username :enabled))

(def user-update-checker (sc/checker UserUpdate))

(defn- validate-updatable-user [{user-data :data}]
  (when (user-update-checker (dissoc user-data :organizationId))
    (fail :error.invalid-user-data)))

(def register-user-checker (sc/checker usr/RegisterUser))

(defn- validate-registrable-user [{user-data :data}]
  (when (register-user-checker user-data)
    (fail :error.invalid-user-data)))

(defn- validate-person-id-update-is-allowed! [{{person-id :personId email :email} :data {caller-email :email} :user}]
  (when (and person-id (-> (or email caller-email)
                           usr/get-user-by-email
                           usr/verified-person-id?))
    (fail :error.user.trying-to-update-verified-person-id)))

(defcommand update-user
  {:input-validators [validate-updatable-user]
   :pre-checks       [validate-person-id-update-is-allowed!]
   :permissions      [{:required [:users/update-own-data]}]
   :description      "User can update own data, but only restricted fields."}
  [{caller :user {person-id :personId :as user-data} :data :as command}]
  (let [email     (ss/canonize-email (:email caller))
        user-data (assoc user-data :email email)]
    (if (= 1 (mongo/update-by-query :users {:email email} {$set (cond-> (select-keys user-data user-data-editable-fields)
                                                                  person-id (assoc :personIdSource :user))}))
      (if (= email (:email caller))
        (ssess/merge-to-session command (ok) {:user (usr/session-summary (usr/get-user-by-id (:id caller)))})
        (ok))
      (fail :not-found :email email))))

(defcommand applicant-to-authority
  {:parameters       [email]
   :user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :description      "Changes applicant or dummy account into authority"}
  [_]
  (let [user (usr/get-user-by-email email)]
    (if (#{"dummy" "applicant"} (:role user))
      (do (mongo/update :users {:email email} {$set {:role "authority"}})
          (ok))
      (fail :error.user-not-found))))

(defcommand demote-authority
  {:parameters       [:email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :user-roles       #{:admin}
   :description      "Demotes an authority user to a applicant or dummy, depending on whether the
                      user has already been verified or not. User must be removed from all organisations
                      first (I.E. user must not have any orgAuthz)"}
  [{{:keys [email]} :data}]
  (let [email (ss/canonize-email email)
        user  (usr/get-user {:email email :role :authority :enabled true})]
    (if user
      (let [has-authz (not-empty (user :orgAuthz))
            verified? (usr/verified-person-id? user)
            ad-user?  (ad-login/user-should-use-ad? (:username user))]
        (cond
          has-authz (fail :error.user-has-authz)
          ad-user?  (fail :users.op.demote-authority.response.error.ad-login-user)
          :else     (do
                      (infof "demoting authority to %s: email=%s" (if verified? "applicant" "dummy") email)
                      (mongo/update-by-id :users (:id user) (if verified?
                                                              {$set   {:role :applicant}
                                                               $unset {:orgAuthz true}}
                                                              {$set   {:role    :dummy
                                                                       :enabled false}
                                                               $unset {:private.password true
                                                                       :orgAuthz         true}}))
                      (ok :result (if verified? :demoted-to-applicant :demoted-to-dummy)))))
      (fail :error.user-not-found))))

(defcommand update-rest-api-user
  {:parameters       [email orgAuthz]
   :user-roles       #{:admin}
   :input-validators [action/email-validator
                      {:email    ssc/NonBlankStr
                       :orgAuthz usr/OrgAuthz}]
   :description      "Updates the rest-api-user's orgAuthz"}
   [_]
  (when (->> {$set {:orgAuthz orgAuthz}}
             (mongo/update-by-query :users {:email email :role :rest-api})
             zero?)
    (fail :error.user-not-found)))

;;
;; Saved search filters
;;

(def filter-storage-key
  {"application"     :applicationFilters
   "company"         :companyApplicationFilters
   "foreman"         :foremanFilters
   "foremanDocument" :foremanDocumentFilters})

(def default-filter-storage-key
  {"application"     "id"
   "foreman"         "foremanFilterId"
   "foremanDocument" "foremanDocumentFilterId"
   "company"         "companyFilterId"})

(defn- validate-filter-type [{{filter-type :filterType} :data}]
  (when-not (contains? filter-storage-key filter-type)
    (fail :error.invalid-type)))

(defcommand update-default-application-filter
  {:parameters       [filterId filterType]
   :user-roles       #{:authority :applicant}
   :input-validators [validate-filter-type
                      (fn [{{filter-id :filterId filter-type :filterType} :data {user-id :id} :user}]
                        (let [user    (usr/get-user-by-id user-id)
                              filters (get user (filter-storage-key filter-type))]
                          (when-not (or (nil? filter-id) (util/find-by-id filter-id filters))
                            (fail :error.filter-not-found))))]
   :description      "Adds/Updates users default filter for the application search"}
  [{{user-id :id} :user}]
  (let [id-key (default-filter-storage-key filterType)]
    (mongo/update-by-id :users user-id {$set {(str "defaultFilter." id-key) filterId}}))
  (ok))

(def ^:private valid-search-filters! (sc/validator [usr/SearchFilter]))

(defcommand save-application-filter
  {:parameters       [title :filter sort filterType]
   :user-roles       #{:authority :applicant}
   :input-validators [validate-filter-type
                      (partial action/non-blank-parameters [:title])
                      (partial action/map-parameters [:filter])
                      (partial action/map-parameters-with-required-keys [:sort] [:field :asc])
                      (fn [{{filter-id :filterId} :data}]
                        (when (and filter-id (not (mongo/valid-key? filter-id)))
                          (fail :error.illegal-key)))]
   :description      "Adds/updates application filter for the user"}
  [{{user-id :id} :user {filter-data :filter filter-id :filterId} :data}]
  (let [filter-id        (or filter-id (mongo/create-id))
        storage-key      (filter-storage-key filterType)
        id-key           (default-filter-storage-key filterType)
        user             (usr/get-user-by-id user-id)
        filters          (get user storage-key)
        title-collision? (->> filters
                              (filter #(= (:title %) title))
                              (map :id)
                              (some (partial not= filter-id)))
        search-filter    {:id filter-id :title title :filter filter-data :sort sort}
        ; enable editing existing filter
        updated-filters  (if (empty? filters)
                           [search-filter]
                           (as-> filters $
                                 (zipmap (map :id $) (range))
                                 (get $ filter-id (count $))
                                 (assoc-in filters [$] search-filter)))
        update           {$set (merge {storage-key updated-filters}
                                      (when (empty? filters)
                                        {(str "defaultFilter." id-key) filter-id}))}]

    (when title-collision?
      (fail! :error.filter-title-collision))

    ;; Should always  pass, but if not, throws exception which will be caught
    ;; by our action framework. User gets an unexpected error.
    (valid-search-filters! updated-filters)

    (mongo/update-by-id :users user-id update)
    (ok :filter search-filter)))

(defcommand remove-application-filter
  {:parameters       [filterId filterType]
   :user-roles       #{:authority :applicant}
   :input-validators [validate-filter-type
                      (partial action/non-blank-parameters [:filterId])]
   :description      "Removes users application filter"}
  [{{user-id :id} :user}]
  (let [user        (usr/get-user-by-id user-id)
        storage-key (filter-storage-key filterType)
        id-key      (default-filter-storage-key filterType)
        update      (merge {$pull {storage-key {:id filterId}}}
                           (when (= (get-in user [:defaultFilter (keyword id-key)]) filterId)
                             {$set {(str "defaultFilter." id-key) ""}}))]
    (mongo/update-by-id :users user-id update)
    (ok)))

(defquery saved-application-filters
  {:user-roles  roles/all-authenticated-user-roles
   :description "Returns search filters for external services. The same data is provided by user query."}
  [{user :user}]
  (if-let [full-user (get-user user)]
    (ok (select-keys full-user [:applicationFilters :foremanFilters :foremanDocumentFilters :defaultFilter]))
    (fail :error.user-not-found)))

;;
;; Change organization data:
;;

(defn- allowed-roles [field allowed-roles command]
  (when (some-> (get-in command [:data field])
                (util/difference-as-kw allowed-roles)
                not-empty)
    (fail :error.invalid-role)))

(defn- is-organization-oauth-role? [org-id allowed-oauth-roles {:keys [role organization]}]
  (and (util/includes-as-kw? allowed-oauth-roles role)
       (= organization org-id)))

(defn- org-id->allowed-oauth-roles [org-id]
  (-> org-id
      org/get-organization
      org/allowed-oauth-roles-in-organization))

(defn- updated-oauth-roles
  "Returns user's new oauth-roles in current organization concatenated with
  user's existing oauth-roles in other organizations and such oauth-roles in
  current organization that are currently irrelevant"
  [user organizationId oauth-roles]
  (let [allowed-oauth-roles       (org-id->allowed-oauth-roles organizationId)
        oauth-roles-in-other-orgs (->> user
                                       :oauth-roles
                                       (remove (partial is-organization-oauth-role? organizationId allowed-oauth-roles)))
        filtered-oauth-roles      (filter (partial util/includes-as-kw? allowed-oauth-roles) oauth-roles)]
    (concat oauth-roles-in-other-orgs
            (ad-util/make-oauth-roles organizationId filtered-oauth-roles))))

(defn user-is-not-digitization-project-user-in-the-org [{{:keys [email organizationId]} :data}]
  (when-let [user (usr/get-user-by-email email)]
    (when (-> (get-in user [:orgAuthz (keyword organizationId)])
              set
              (contains? "digitization-project-user"))
      (fail! :error.no-permission-to-modify-the-user))))

(defcommand upsert-organization-user
  {:parameters          [organizationId email firstName lastName roles]
   :optional-parameters [oauth-roles]
   :input-validators    [(partial action/non-blank-parameters [:organizationId :email :firstName :lastName])
                         (partial action/vector-parameters-with-at-least-n-non-blank-items 1 [:roles])
                         action/email-validator
                         ;;Note that authority admin is not allowed to give role digitization-project-user or
                         ;;update user that has digitization-project-user role in the org
                         (partial allowed-roles :roles (disj roles/authority-managed-org-authz-roles :digitization-project-user))
                         (partial allowed-roles :oauth-roles roles/oauth-roles-without-admin)
                         user-is-not-digitization-project-user-in-the-org]
   :notified            true
   :permissions         [{:required [:organization/admin]}]
   :pre-checks          [org/orgAuthz-pre-checker
                         ad-login/roles-maintained-in-ad-pre-checker]
   :description         "Updates or creates user in admins organization"}
  [{caller :user system :system}]
  (let [email        (ss/canonize-email email)
        user         (usr/get-user-by-email email)
        actual-roles (org/filter-valid-user-roles-in-organization organizationId roles)
        oauth-roles  (seq (updated-oauth-roles user organizationId oauth-roles))
        result       (usr/update-user-by-email email {:role "authority"} {$set (-> {(str "orgAuthz." organizationId) actual-roles}
                                                                                   (util/assoc-when :oauth-roles oauth-roles))})]
    (if (ok? result)
      (do (uu/notify-authority-added system email organizationId)
          (ok :operation "add"))
      (if-not user
        (create-authority-user-with-organization system caller organizationId email firstName lastName actual-roles oauth-roles)
        (fail :error.user-not-found)))))

(defcommand remove-user-organization
  {:parameters       [organizationId email]
   :input-validators [(partial action/non-blank-parameters [:organizationId :email])]
   :permissions      [{:required [:organization/admin]}]}
  [{caller :user}]
  (usr/update-user-by-email email
                            {:role :authority}
                            {$unset {(util/kw-path :orgAuthz organizationId) ""}
                             $pull  {:oauth-roles {:organization organizationId}}}))

(defn- user-removing-own-authorityAdmin? [caller organizationId email roles]
  (let [callers-roles-in-org (get-in caller [:orgAuthz (keyword organizationId)])
        role-difference      (set/difference callers-roles-in-org (set (map keyword roles)))]
    (boolean (and (= email (:email caller))
                  (role-difference :authorityAdmin)))))

(defn- one-authorityAdmin-left-in-org? [organizationId]
  (= 1 (mongo/count :users {(keyword (str "orgAuthz." organizationId)) "authorityAdmin"})))

(defcommand update-user-roles
  {:parameters       [organizationId email roles oauth-roles]
   :input-validators [(partial action/non-blank-parameters [:organizationId :email])
                      (partial action/vector-parameters-with-at-least-n-non-blank-items 1 [:roles])
                      ;;Note that authority admin is not allowed to give role digitization-project-user or
                      ;;update user that has digitization-project-user role in the org
                      (partial allowed-roles :roles (disj roles/authority-managed-org-authz-roles :digitization-project-user))
                      (partial allowed-roles :oauth-roles roles/oauth-roles-without-admin)
                      user-is-not-digitization-project-user-in-the-org]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [ad-login/roles-maintained-in-ad-pre-checker]}
  [{caller :user}]
  (let [email (ss/canonize-email email)]
    (if-let [user (usr/get-user-by-email email)]
      (let [updated-oauth-roles        (updated-oauth-roles user organizationId oauth-roles)
            filtered-valid-roles       (set (org/filter-valid-user-roles-in-organization organizationId roles))
            previous-roles             (-> user (get-in [:orgAuthz (keyword organizationId)]) set)
            pre-existing-invalid-roles (-> (set roles)
                                           (set/difference filtered-valid-roles)
                                           (set/intersection previous-roles))
            actual-roles               (concat pre-existing-invalid-roles filtered-valid-roles)]

        ;; Check that the last authorityAdmin is not trying to remove authz from him/herself.
        (if (and (user-removing-own-authorityAdmin? caller organizationId email roles)
                 (one-authorityAdmin-left-in-org? organizationId))
          (fail :error.last-authorityadmin)
          (let [orgAuthz-path (str "orgAuthz." organizationId)]
            (usr/update-user-by-email email
                                      {:role "authority"}
                                      {$set {orgAuthz-path actual-roles
                                             :oauth-roles  updated-oauth-roles}})
            (ok))))
      (fail :error.user-not-found))))

(defn- update-authority [authority email data]
  (let [new-email (:email data)]
    (when (not= email new-email)
      (change-email/update-email-in-application-auth! (:id authority) email new-email)
      (change-email/update-email-in-invite-auth! (:id authority) email new-email))
    (usr/update-user-by-email email {} {$set data} true)))

(defcommand update-auth-info
  {:parameters       [organizationId firstName lastName email new-email]
   :input-validators [(partial action/non-blank-parameters [:firstName :lastName :email :new-email])
                      (partial action/email-validator :new-email)]
   :permissions      [{:required [:organization/admin]}]}
  [{auth-admin :user}]
  (let [email (ss/canonize-email email)]
    (if-let [authority (usr/get-user {:email email :organization organizationId})]
      (let [new-email           (ss/canonize-email new-email)
            valid-domain?       (uu/admin-and-user-have-same-email-domain? authority auth-admin)
            email-not-in-use?   (if (= email new-email) true (not (usr/email-in-use? new-email)))
            user-info-editable? (uu/authority-has-only-one-org? authority)
            data                {:email     new-email
                                 :username  new-email
                                 :firstName firstName
                                 :lastName  lastName}
            ad-user?            (ad-login/user-should-use-ad? (:username authority))]
        (cond
          (not email-not-in-use?)   (fail :error.auth-admin-duplicate-email)
          (not valid-domain?)       (fail :error.auth-admin-and-authority-have-different-domains)
          (not user-info-editable?) (fail :error.authority-has-multiple-orgs)
          ad-user?                  (fail :error.user-information-maintained-in-ad)
          :else                     (update-authority authority email data)))
      (fail :error.user-not-found))))

(defn valid-user-roles-for-digitization-user [{{user-roles :user-roles} :data}]
  (when-not (= (set user-roles) #{"digitizer" "digitization-project-user"})
    (fail! :error.invalid-role)))

(defn valid-organization-ids [{{org-ids :organizationIds} :data}]
  (when-not (org/known-organizations? org-ids)
    (fail! :error.unknown-organization)))

(defcommand toggle-digitization-user-status
  {:description      "Adds and removes user-roles from orgAuthz based on organizationIds.
                      For example if organizationIds is empty coll and user-type is digitization-project-user
                      then removes roles digitizer and digitization-project-user from every org in orgAuthz map."
   :parameters       [email organizationIds user-roles]
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:organizationIds])
                      valid-organization-ids
                      valid-user-roles-for-digitization-user]
   :permissions      [{:required [:users/toggle-digitization-user-status]}]}
  [{caller :user}]
  (let [user               (usr/get-user-by-email email)
        user-roles         (set user-roles)
        filtered-org-authz (util/map-values
                             (fn [single-org-authz] (remove #(contains? user-roles %) single-org-authz))
                             (:orgAuthz user))
        new-org-authz      (reduce (fn [org-authz org-id]
                                     (update org-authz org-id into user-roles))
                                   filtered-org-authz (mapv keyword organizationIds))]
    (if (ok? (usr/update-user-by-email email {$set {:orgAuthz new-org-authz}}))
      (ok :organizationIds (when (not-empty organizationIds)
                             (ss/join ", " organizationIds))))))

(defmethod token/handle-token :authority-invitation [{{:keys [email organization caller-email]} :data} {password :password} _system]
  (infof "invitation for new authority: email=%s: processing..." email)
  (let [caller (usr/get-user-by-email caller-email)]
    (when-not caller (fail! :not-found))
    (usr/change-password email password)
    (infof "invitation was accepted: email=%s, organization=%s" email organization)
    (ok)))

;;
;; Change and reset password:
;;

(defcommand check-password
  {:parameters       [password]
   :user-roles       #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:password])]}
  [{user :user}]
  (if (security/check-password password
                               (some-<>> user
                                         :id
                                         (mongo/by-id :users <> {:private.password true})
                                         :private
                                         :password))
    (ok)
    (fail :error.password)))

(defcommand change-password
  {:parameters       [oldPassword newPassword]
   :input-validators [(partial action/non-blank-parameters [:oldPassword :newPassword])
                      (partial security/valid-password-length-validator :newPassword)
                      (partial security/valid-password-score-validator :newPassword :user)]
   :user-roles       (disj roles/all-authenticated-user-roles :oirAuthority)}
  [{{user-id :id :as user} :user :as command}]
  (let [user-data (mongo/by-id :users user-id)]
    (if (security/check-password oldPassword (-> user-data :private :password))
      (do
        (debug "Password change: user-id:" user-id)
        ;; Pass the current session's application-level :id so that only the other
        ;; sessions are deleted, leaving this one alive for key rotation below.
        (usr/change-password (:email user) newPassword (get-in command [:session :id]))
        (ssess/merge-to-session command (ok) nil {:rotate-session? true}))
      (do
        (warn "Password change: failed: old password does not match, user-id:" user-id)
        ; Throttle giving information about incorrect password
        (Thread/sleep 2000)
        (fail :mypage.old-password-does-not-match)))))

(defcommand change-password-suomifi
  {:description      "Strongly authenticated (ie. suomi.fi) user may change the password of their existing account"
   :parameters       [userId password]
   :input-validators [(partial security/valid-password-length-validator :password)]
   :user-roles       #{:anonymous}}
  [_]
  (if-let [person-id (some-> (vetuma/vetuma-session) :user :userid)]
    (if-let [user (->> (usr/get-users-by-person-id person-id)
                       (remove (comp ad-login/user-should-use-ad? :username))
                       (util/find-by-id userId))]
      (if (security/valid-password-score? password user)
        (do
          (debug "Password change: user-id:" userId)
          (usr/change-password (:email user) password)
          (ok))
        (fail :error.password.minscore))
      (fail :error.user-not-found))
    (fail :error.invalid-vetuma-user)))

(defcommand reset-password
  {:parameters       [email]
   :user-roles       #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :notified         true}
  [{:keys [system]}]
  (pw-reset/reset-password-by-email system email)
  (ok))

(defcommand admin-reset-password
  {:parameters       [email]
   :user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:email])]
   :notified         true}
  [{:keys [system]}]
  (let [user (usr/get-user-by-email email)]
    (if (and user (not (usr/dummy? user)))
      (ok :link (pw-reset/reset-link system
                                     (or (:language user) "fi")
                                     (pw-reset/reset-password system user {:token-only? true})))
      (fail :error.email-not-found))))

(defmethod token/handle-token :password-reset [{data :data} {password :password} _system]
  (let [email (ss/canonize-email (:email data))]
    (usr/change-password email password)
    (infof "password reset performed: email=%s" email)
    (ok)))

(defcommand evaluate-password
  {:parameters          [password]
   :optional-parameters [userId
                         firstName
                         lastName
                         email
                         token
                         organizationId]
   :user-roles          #{:anonymous}
   :input-validators    [{:password                         ssc/NonBlankStr
                          ;; For when a new user is registering
                          (sc/optional-key :firstName)      sc/Str
                          (sc/optional-key :lastName)       sc/Str
                          (sc/optional-key :email)          sc/Str
                          ;; For when an existing user is resetting the password from an email link
                          (sc/optional-key :token)          sc/Str
                          ;; For when an existing user is resetting the password after strong authentication
                          ;; (but before formally logging in)
                          (sc/optional-key :userId)         ssc/ObjectIdStr
                          ;; auth-admin views include org-id
                          (sc/optional-key :organizationId) sc/Str}]}
  [{:keys [user]}]
  (let [user (cond
               ;; Logged in user changing password
               (:email user) user
               ;; User resetting password with token
               token         (if-let [{{:keys [email]} :data} (token/get-usable-token token)]
                               (usr/get-user-by-email email)
                               (fail! :error.token-used))
               ;; Check if the requested user account matches the authentication
               userId        (if-let [person-id (some-> (vetuma/vetuma-session) :user :userid)]
                               (if-let [user (->> (usr/get-users-by-person-id person-id)
                                                  (remove (comp ad-login/user-should-use-ad? :username))
                                                  (util/find-by-id userId))]
                                 user
                                 (fail! :error.user-not-found))
                               (fail! :error.invalid-vetuma-user))
               ;; New user registering
               :else         (util/strip-blanks {:firstName firstName
                                                 :lastName  lastName
                                                 :email     email}))]
    (ok :score (spw/measure-password-quality password user))))

;;
;; admin change email
;;

(defcommand admin-change-user-email
  {:parameters       [old-email new-email]
   :description      "Allows a Lupapiste admin to init email change when user is an authority in multiple orgs"
   :user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:old-email :new-email])
                      (partial action/email-validator :new-email)]
   :notified         true}
  [_]
  (let [new-email  (ss/canonize-email new-email)
        ;; Do not canonize old email to allow us to fix emails with whitespace, upper-case letters, etc.
        user       (mongo/select-one :users {:email old-email})
        in-use?    (mongo/any? :users {:email new-email})
        not-found? (or (nil? user)
                       (usr/dummy? user))]
    (cond
      in-use?    (fail :error.auth-admin-duplicate-email)
      not-found? (fail :error.email-not-found)
      :else      (update-authority user old-email {:email    new-email
                                                   :username new-email}))))

;;
;; enable/disable:
;;

(defcommand set-user-enabled
  {:parameters       [email enabled]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :user-roles       #{:admin}}
  [_]
  (let [email   (ss/canonize-email email)
        enabled (contains? #{true "true"} enabled)]
    (infof "%s user: email=%s" (if enabled "enable" "disable") email)
    (if (= 1 (mongo/update-by-query :users {:email email} {$set {:enabled enabled}}))
      (ok)
      (fail :not-found))))

;;
;; erase user data:
;;

(defcommand erase-user
  {:description      "Erase/anonymize user data but retain the record in database."
   :parameters       [email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :user-roles       #{:admin}}
  [_]
  (if-let [user (usr/get-user-by-email (ss/canonize-email email))]
    (do
      (usr/erase-user (:id user))
      (ok))
    (fail :not-found)))

;;
;; ==============================================================================
;; Login:
;; ==============================================================================
;;


(defquery pre-login
  {:parameters       [parameter]
   :input-validators [(partial action/non-blank-parameters [:parameter])]
   :user-roles       roles/all-user-roles}
  [command]
  (ok :message ident-util/resolve-redirect))

(defcommand login
  {:parameters          [username password]
   :input-validators    [(partial action/non-blank-parameters [:username :password])]
   ; Failed login count is written to DB but we'll allow this in readonly mode
   :allowed-in-lockdown true
   :user-roles          #{:anonymous}}
  [command]
  (usr/login command))

(defquery redirect-after-login
  {:user-roles roles/all-authenticated-user-roles}
  [{session :session}]
  (ok :url (get session :redirect-after-login "")))

(defcommand impersonate-authority
  {:parameters       [organizationId role password]
   :user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (fn [{data :data}] (when-not (#{"authority" "authorityAdmin" "archivist" "biller"} (:role data)) (fail :error.invalid-role)))]
   :description      "Changes admin session into authority session with access to given organization"}
  [{user :user :as command}]
  (if (usr/get-user-with-password (:username user) password)
    (let [org-roles (case role
                      "archivist" [:authority :approver :archivist]
                      "authority" [:authority :approver]
                      "biller" [:authority :biller]
                      [(keyword role)])
          imposter  (assoc user :impersonating true :role "authority" :orgAuthz {(keyword organizationId) (set org-roles)})]
      (ssess/merge-to-session command (ok)
                              {:user imposter}
                              {:rotate-session? true}))
    (fail :error.login)))

;;
;; ==============================================================================
;; Registering:
;; ==============================================================================
;;

(defn- register [system user stamp]
  (activation/send-activation-mail-for system user)
  (vetuma/consume-user stamp)
  user)

(defn- vetuma-data-to-user [{:keys [userid firstName lastName eidasFamilyName]
                             :as   vetuma-data}
                            {:keys [architect email] :as data}]
  (merge vetuma-data
         (if (ss/not-blank? userid)
           {:firstName      (str firstName)
            :lastName       (str lastName)
            :personId       userid
            :personIdSource "identification-service"}
           {:firstName      (str firstName)
            :lastName       (str eidasFamilyName)
            ;; eIDAS id is not necessarily a hetu and can't be used as such
            :eidas          [(select-keys vetuma-data [:eidasId
                                                       :firstName
                                                       :eidasFamilyName
                                                       :eidasBirthDate])]
            :personIdSource "eIDAS"})
         (select-keys data [:password :language :street :zip :city :phone :allowDirectMarketing])
         (when architect
           (select-keys data [:architect :degree :graduatingYear :fise :fiseKelpoisuus]))
         {:email   (ss/canonize-email email)
          :role    "applicant"
          :enabled false}))

(defcommand register-user
  {:parameters          [stamp email password street zip city phone allowDirectMarketing]
   :optional-parameters [language]
   :user-roles          #{:anonymous}
   :input-validators    [action/email-validator validate-registrable-user (partial security/valid-password-length-validator :password)]}
  [{:keys [data system]}]
  (if-let [vetuma-data (vetuma/get-user stamp)]
    (try
      (infof "Registering new user: %s - details from vetuma: %s" (dissoc data :password) vetuma-data)
      (let [proto-user (vetuma-data-to-user vetuma-data data)]
        (when-not (security/valid-password-score? (:password data) proto-user)
          (fail! :error.password.minscore))
        (if-let [user (usr/create-new-user nil proto-user)]
          (ok :id (:id (register system user stamp)))
          (fail :error.create-user)))

      (catch IllegalArgumentException e
        (fail (keyword (.getMessage e)))))
    (fail :error.create-user)))

;;
;; ==============================================================================
;; User attachments:
;; ==============================================================================
;;

(defquery user-attachments
  {:user-roles #{:applicant :authority}}
  [{user :user}]
  (if-let [current-user (usr/get-user-by-id (:id user))]
    (ok :attachments (:attachments current-user))
    (fail :error.user-not-found)))

(def- add-user-attachment-allowed? (some-fn usr/applicant? usr/authority?))

(defquery add-user-attachment-allowed
  {:description "Dummy command for UI logic: returns falsey if current user is not allowed to add \"user attachments\"."
   :pre-checks  [(fn [command]
                   (when-not (add-user-attachment-allowed? (:user command))
                     unauthorized))]
   :user-roles  #{:anonymous}}
  [_]
  (ok))

(defpage [:post "/api/upload/user-attachment"] {[{:keys [tempfile filename size]}] :files attachmentType :attachmentType}
  (try+
    (let [user            (usr/current-user (request/ring-request))
          filename        (mime/sanitize-filename filename)
          attachment-type (att-type/parse-attachment-type attachmentType)
          attachment-id   (str (random-uuid))
          content-type    (mime/mime-type filename)
          file-info       {:attachment-type attachment-type
                           :attachment-id   attachment-id
                           :file-name       filename
                           :content-type    content-type
                           :size            size
                           :created         (now)
                           :storageSystem   (storage/default-storage-system-id)}]
      (log/with-logging-context {:userId (:id user)}
        (when-not (add-user-attachment-allowed? user) (throw+ {:status 401 :body "forbidden"}))

        (info "upload/user-attachment" (:username user) ":" attachment-type "/" filename size "id=" attachment-id)
        (when-not ((set att-type/osapuolet) (:type-id attachment-type)) (fail! :error.illegal-attachment-type))
        (when-not (mime/allowed-file? filename) (fail! :error.file-upload.illegal-file-type))
        (storage/upload attachment-id filename content-type tempfile {:user-id (:id user)})
        (mongo/update-by-id :users (:id user) {$push {:attachments file-info}})
        (resp/json (assoc file-info :ok true))))
    (catch [:sade.core/type :sade.core/fail] {:keys [text] :as all}
      (error "fail! in user-attachment: " text)
      (resp/json (fail text)))
    (catch Exception e
      (error e "exception while uploading user attachment" (class e) (str e))
      (resp/json (fail :error.unknown)))))

(defraw download-user-attachment
  {:parameters       [attachment-id]
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:applicant}}
  [{user :user}]
  (when-not user (throw+ {:status 401 :body "forbidden"}))
  (if-let [attachment (storage/download-user-attachment (:id user) attachment-id)]
    {:status  200
     :body    ((:content attachment))
     :headers {"Content-Type"        (:contentType attachment)
               "Content-Length"      (str (:size attachment))
               "Content-Disposition" (format "attachment;filename=\"%s\"" (ss/encode-filename (:filename attachment)))}}
    {:status 404
     :body   (str "Attachment not found: id=" attachment-id)}))

(defcommand remove-user-attachment
  {:parameters       [attachment-id]
   :input-validators [(partial action/non-blank-parameters [:attachment-id])]
   :user-roles       #{:applicant}}
  [{user :user}]
  (info "Removing user attachment: attachment-id:" attachment-id)
  (storage/delete-user-attachment (:id user) attachment-id)
  (mongo/update-by-id :users (:id user) {$pull {:attachments {:attachment-id attachment-id}}})
  (ok))

(defn- allowed-state?
  "Check possible attachments against application state.
   pre-verdict states are all true, post-verdict states are checked by attachment's applicationState.
   Similar to attachment-editable-by-application-state pre-check"
  [app-state {create-state :applicationState}]
  (if (states/post-verdict-states (keyword app-state))
    (not (nil? (states/post-verdict-states (keyword create-state))))
    true))

(defn allowed-attachments-same-type
  "Return attachments of given type, that are allowed in current state and not flagged readOnly / locked."
  [application attachment-type]
  (->> (att/get-attachments-by-type application attachment-type)
       (filter (partial allowed-state? (:state application)))
       (remove #(or (att/attachment-is-readOnly? %) (att/attachment-is-locked? %)))))

(defn user-attachments-exists [{user :user}]
  (when (empty? (:attachments (mongo/by-id :users (:id user) {:attachments true})))
    (fail :error.no-user-attachments)))

(defn- foreman-app-in-pre-verdict-state [{:keys [application]}]
  (when (and (foreman-util/foreman-app? application)
             (not (contains? states/pre-verdict-states
                             (-> application :state keyword))))
    (fail :error.illegal-state)))

(defcommand copy-user-attachments-to-application
  {:parameters       [id]
   :user-roles       #{:applicant}
   :user-authz-roles roles/writer-roles-with-foreman
   :states           (states/all-application-states-but states/terminal-states)
   :pre-checks       [(fn [command]
                        (when-not (-> command :user :architect)
                          unauthorized))
                      user-attachments-exists
                      foreman-app-in-pre-verdict-state]}
  [{user :user ts :created :as command}]
  (doseq [attachment (:attachments (mongo/by-id :users (:id user) {:attachments true}))]
    (let [application-id         id
          user-id                (:id user)
          {:keys [attachment-type attachment-id file-name content-type size storageSystem]} attachment
          attachment             (storage/download-user-attachment user-id attachment-id storageSystem)
          maybe-attachment-id    (str application-id "." user-id "." attachment-id) ; proposed attachment id (if empty placeholder is not found)
          updated-application    (mongo/by-id :applications application-id)
          same-attachments       (allowed-attachments-same-type updated-application attachment-type) ; attachments of same type
          old-user-attachment-id (some (hash-set maybe-attachment-id) (map :id same-attachments)) ; if id is already present, use it

          attachment-id          (or old-user-attachment-id
                                     (-> (remove :latestVersion same-attachments) first :id) ; upload user attachment to empty placeholder
                                     maybe-attachment-id)]
      (when (zero? (mongo/count :applications {:_id         application-id
                                               :attachments {$elemMatch {:id                    attachment-id ; skip upload when user attachment as already been uploaded
                                                                         :type.type-id          (:type-id attachment-type)
                                                                         :latestVersion.user.id (:id user)}}}))
        (att/upload-and-attach! command
                                {:attachment-id   attachment-id
                                 :attachment-type attachment-type
                                 :group           {:groupType (get-in (att-type/attachment-type attachment-type) [:metadata :grouping])}
                                 :created         ts
                                 :required        false
                                 :locked          false}
                                {:content      ((:content attachment))
                                 :filename     file-name
                                 :content-type content-type
                                 :size         size}))))
  (ok))

(defquery email-in-use
  {:parameters       [email]
   :input-validators [email-validator]
   :user-roles       #{:anonymous}}
  [_]
  (if (usr/email-in-use? email)
    (ok)
    (fail :email-not-in-use)))

(defcommand remove-user-notification
  {:user-roles #{:applicant :authority}}
  [{{id :id} :user}]
  (mongo/update-by-id :users id {$unset {:notification 1}}))

(defquery enable-foreman-search
  {:user-roles #{:authority}
   :pre-checks [(fn [{:keys [user]}]
                  (let [org-ids (->> (usr/organization-ids user)
                                     (filter
                                       (fn [oid]
                                         (some roles/reader-org-authz-roles (get-in user [:orgAuthz (keyword oid)])))))]
                    (when-not (pos? (mongo/count :organizations {:_id {$in org-ids} :scope.permitType permit/R}))
                      unauthorized)))]}
  [_]
  (ok))

(defn filter-allowed-oauth-roles [org-id allowed-oauth-roles user]
  (update user :oauth-roles (partial filterv (partial is-organization-oauth-role? org-id allowed-oauth-roles))))

(defquery get-oauth-users
  {:description      "Get users who have oauth-roles matching the given organization"
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (let [allowed-oauth-roles (org-id->allowed-oauth-roles organizationId)
        query               (usr/oauth-role-match organizationId allowed-oauth-roles)
        ad-confs            (ad-login/fetch-enabled-ad-confs-for-organization organizationId)
        users               (->> (usr/find-users query)
                                 (map (partial filter-allowed-oauth-roles organizationId allowed-oauth-roles))
                                 (map #(assoc %
                                              :current-org-roles-maintained-in-ad?
                                              (ad-login/roles-maintained-in-ad? ad-confs %)))
                                 (mapv #(usr/summary-for-search-filter % :oauth-roles :current-org-roles-maintained-in-ad?)))]
    (ok :users users)))

(defcommand update-user-oauth-roles
  {:parameters       [:organizationId :email :oauth-roles]
   :input-validators [(partial action/non-blank-parameters [:organizationId :email])
                      (partial allowed-roles :oauth-roles roles/oauth-roles-without-admin)
                      user-is-not-digitization-project-user-in-the-org]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [ad-login/roles-maintained-in-ad-pre-checker]
   :notified         true}
  [{caller :user {:keys [email organizationId oauth-roles]} :data system :system}]
  (let [email         (ss/canonize-email email)
        existing-user (usr/get-user-by-email email)]
    (if-let [user (if (or (nil? existing-user) (usr/dummy? existing-user))
                    (uu/authority-by-email system organizationId caller email)
                    existing-user)]
      (let [oauth-roles (updated-oauth-roles user organizationId oauth-roles)]
        (usr/update-user-by-email email {$set {:oauth-roles oauth-roles}}))
      (fail :error.user-not-found))))

(defcommand connect-sso
  {:description "Find ad-login matching the user's domain and link it to the user account."
   :permissions [{:required [:users/update-own-data]}]}
  [{{user-id :id username :username} :user}]
  (let [domain (ad-login/username->domain username)]
    (when-not (ad-login/get-ad-login-by-domain domain)
      (fail :error.ad-login.unsupported-domain))
    (mongo/update-by-id :users user-id {$set {:ad-login true}})
    (ok {:user (usr/get-user-by-id user-id)})))

(defcommand disconnect-sso
  {:description "Send reset password email to user to disconnect ad-login."
   :permissions [{:required [:users/update-own-data]}]}
  [{{user-id :id email :email} :user system :system}]
  (pw-reset/reset-password-by-email system email)
  (ok {:user (usr/get-user-by-id user-id)}))
