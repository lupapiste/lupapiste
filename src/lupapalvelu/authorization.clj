(ns lupapalvelu.authorization
  (:require [clojure.set :refer [union]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.roles :refer :all]
            [lupapalvelu.permissions :as permissions]
            [lupapalvelu.application-schema :as aps]
            [lupapalvelu.document.tools :as doc-tools]
            [schema.core :refer [defschema] :as sc]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]))



;;
;; Schema
;;

(defschema Invite
  {(sc/optional-key :role)           (apply sc/enum all-authz-roles)
   (sc/optional-key :path)           sc/Str
   :email                            ssc/Email
   :created                          ssc/Timestamp
   :inviter                          usr/SummaryUser
   (sc/optional-key :documentName)   sc/Str
   (sc/optional-key :documentId)     ssc/ObjectIdStr
   :user                             usr/SummaryUser
   (sc/optional-key :title)          sc/Str
   (sc/optional-key :text)           sc/Str})

(defschema CompanyInvite
  {:user                             {:id usr/Id}
   (sc/optional-key :role)           (apply sc/enum all-authz-roles)
   (sc/optional-key :created)        ssc/Timestamp})

(defschema Auth
  {:id                               usr/Id
   :username                         ssc/Username
   :firstName                        sc/Str
   :lastName                         sc/Str
   :role                             (apply sc/enum all-authz-roles)
   (sc/optional-key :type)           (sc/enum :company)
   (sc/optional-key :company-role)   (sc/enum :admin :user)
   (sc/optional-key :name)           sc/Str
   (sc/optional-key :y)              ssc/FinnishY
   (sc/optional-key :unsubscribed)   sc/Bool
   (sc/optional-key :statementId)    ssc/ObjectIdStr
   (sc/optional-key :invite)         (sc/if :email Invite CompanyInvite)
   (sc/optional-key :inviteAccepted) ssc/Timestamp
   (sc/optional-key :inviter)        (sc/if map? usr/SummaryUser usr/Id)})

;;
;; Auth utils
;;

(defn get-auths-by-permissions
  ([application permissions]
   (get-auths-by-permissions application permissions [:application]))
  ([{auth :auth} permissions scopes]
   (let [roles (->> scopes
                    (map #(permissions/roles-in-scope-with-permissions % permissions))
                    (reduce into #{}))]
     (filter (comp roles keyword :role) auth))))

(defn get-auths-by-roles
  "Returns sequence of all auth-entries in an application with the
  given roles. Each role can be keyword or string."
  [{auth :auth} roles]
  (let [role-set (->> roles (map name) set)]
    ;; Roles in auths can also be keywords or strings.
    ;; (name nil) causes NPE so default value is needed.
    (filter #(contains? role-set (name (get % :role ""))) auth)))

(defn get-auths-by-role
  [application role]
  (get-auths-by-roles application [role]))

(defn get-auths [{auth :auth} user-id]
  (filter #(= (:id %) user-id) auth))

(defn get-auth [application user-id]
  (first (get-auths application user-id)))

(defn has-auth? [{auth :auth} user-id]
  (or (some (partial = user-id) (map :id auth)) false))

(defn has-auth-role? [{auth :auth} user-id role]
  (has-auth? {:auth (get-auths-by-role {:auth auth} role)} user-id))

(defn has-some-auth-role? [{auth :auth} user-id roles]
  (has-auth? {:auth (get-auths-by-roles {:auth auth} roles)} user-id))

(defn get-company-auths
  ([{auth :auth}]
   (filter #(and (= "writer" (:role %))
                 (= "company" (:type %)))
           auth))
  ([{auth :auth} {{company-id :id company-role :role} :company :as user}]
   (filter #(and (= company-id (:id %))
                 (contains? #{(keyword company-role) nil} (keyword (:company-role %))))
           auth)))

(defn auth-via-company [application user-or-user-id]
  (->> (if (map? user-or-user-id)
         user-or-user-id
         (usr/get-user-by-id user-or-user-id))
       (get-company-auths application)
       (util/find-first (comp #{"writer"} :role))))

(defn has-auth-via-company? [application user-id]
  (or (auth-via-company application user-id) false))

(defn create-invite-auth [inviter invited application-id role timestamp & [text document-name document-id path]]
  {:pre [(seq inviter) (seq invited) application-id role timestamp]}
  (let [invite (cond-> {:created      timestamp
                        :email        (:email invited)
                        :role         role
                        :user         (usr/summary invited)
                        :inviter      (usr/summary inviter)}
                 (not (nil? path))               (assoc :path path)
                 (not (ss/blank? text))          (assoc :text text)
                 (not (ss/blank? document-name)) (assoc :documentName document-name)
                 (not (ss/blank? document-id))   (assoc :documentId document-id))]
    (assoc (usr/user-in-role invited :reader) :invite invite)))

(defmulti approve-invite-auth
  {:arglists '([auth-elem user accepted-ts])}
  (fn [{auth-type :type :as auth} & _] (keyword auth-type)))

(defmethod approve-invite-auth :default [{invite :invite :as auth} user accepted-ts]
  (when invite
    (let [role (or (:role invite) (:role auth))]
      (util/assoc-when-pred (usr/user-in-role user role) util/not-empty-or-nil?
                            :inviter (:inviter invite)
                            :inviteAccepted accepted-ts))))

(defn make-user-auth [user unsubscribed?]
  (let [user-auth    (usr/user-in-role user :writer)]
    (assoc user-auth :unsubscribed unsubscribed?)))

;;
;; Authz checkers
;;

(defn user-authz? [roles application user]
  {:pre [(set? roles)]}
  (let [roles-in-app  (map (comp keyword :role) (get-auths application (:id user)))]
    (some roles roles-in-app)))

(defn company-authz? [roles application user]
  (->> (get-company-auths application user)
       (map (comp keyword :role))
       (some roles)))

(defn user-or-company-authz? [roles application user]
  (or (user-authz? roles application user)
      (company-authz? roles application user)))

(defn org-authz
  "Returns user's org authz in given organization, nil if not found"
  [organization-id user]
  (get-in user [:orgAuthz (keyword organization-id)]))

(defn has-organization-authz-roles?
  "Returns true if user has requested roles in organization"
  [requested-authz-roles organization-id user]
  (and (or (usr/authority? user) (usr/authority-admin? user) (usr/oir-authority? user))
       requested-authz-roles
       (some requested-authz-roles (org-authz organization-id user))))

(defn application-authority?
  "Returns true if the user is an authority in the organization that processes the application"
  [application user]
  (boolean (has-organization-authz-roles? #{:authority :approver} (:organization application) user)))

(defn application-authority-pre-check
  "Fails if user is NOT application authority"
  [{application :application user :user}]
  (when-not (application-authority? application user)
    (fail :error.unauthorized)))

(defn application-role [application user]
  (if (application-authority? application user) :authority :applicant))

(defn application-handler?
  "True if the user is a handler for the application. Note: a handler is
  always application-authority."
  [application user]
  (boolean (util/find-by-key :userId (:id user) (:handlers application))))

;;
;; Enrich auth array
;;

(defn party-document? [doc]
  (= :party (doc-tools/doc-type doc)))

(defn- enrich-auth-info-with-parties [sorted-parties-docs auth-info]
  (->> (filter (comp #{(:id auth-info)} doc-tools/party-doc-user-id) sorted-parties-docs)
       (map doc-tools/party-doc->user-role)
       (assoc auth-info :party-roles)))

(defn- enrich-authority-auth-info [authority-id auth-info]
  (if (and authority-id (= (:id auth-info) authority-id))
    (update auth-info :other-roles conj :authority)
    auth-info))

(defn enrich-auth-information [{auth :auth docs :documents {authority-id :id} :authority}]
  (let [parties-docs (->> (filter party-document? docs)
                          (sort-by (comp :order :schema-info)))]
    (->> auth
         (map (partial enrich-auth-info-with-parties parties-docs))
         (map (partial enrich-authority-auth-info authority-id)))))
