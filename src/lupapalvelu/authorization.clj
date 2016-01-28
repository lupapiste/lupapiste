(ns lupapalvelu.authorization
  (:require [lupapalvelu.user :as user]))

;;
;; Roles
;;

(def all-authenticated-user-roles #{:applicant :authority :oirAuthority :authorityAdmin :admin})
(def all-user-roles (conj all-authenticated-user-roles :anonymous :rest-api :trusted-etl))

(def default-authz-writer-roles #{:owner :writer :foreman})
(def default-authz-reader-roles (conj default-authz-writer-roles :reader :guest :guestAuthority))
(def all-authz-writer-roles (conj default-authz-writer-roles :statementGiver))
(def all-authz-roles (conj all-authz-writer-roles :reader :guest :guestAuthority))

(def default-org-authz-roles #{:authority :approver})
(def commenter-org-authz-roles (conj default-org-authz-roles :commenter))
(def reader-org-authz-roles (conj commenter-org-authz-roles :reader))
(def all-org-authz-roles (conj reader-org-authz-roles :authorityAdmin :tos-editor :tos-publisher :archivist))

(def default-user-authz {:query default-authz-reader-roles
                         :export default-authz-reader-roles
                         :command default-authz-writer-roles
                         :raw default-authz-writer-roles})

;;
;; Auth utils
;;



(defn get-auths-by-roles
  "Returns sequence of all auth-entries in an application with the
  given roles. Each role can be keyword or string."
  [{auth :auth} roles]
  (let [role-set (->> roles (map name) set)]
    (filter #(contains? role-set (:role %)) auth)))

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

(defn create-invite-auth [inviter invited application-id role timestamp & [text document-name document-id path]]
  {:pre [(seq inviter) (seq invited) application-id role timestamp]}
  (let [invite {:application  application-id
                :text         text
                :path         path
                :documentName document-name
                :documentId   document-id
                :created      timestamp
                :email        (:email invited)
                :role         role
                :user         (user/summary invited)
                :inviter      (user/summary inviter)}]
    (assoc (user/user-in-role invited :reader) :invite invite)))

;;
;; Authz checkers
;;

(defn user-authz? [roles application user]
  {:pre [(set? roles)]}
  (let [roles-in-app  (map (comp keyword :role) (get-auths application (:id user)))]
    (some roles roles-in-app)))

(defn org-authz
  "Returns user's org authz in given organization, nil if not found"
  [organization user]
  (get-in user [:orgAuthz (keyword organization)]))

(defn has-organization-authz-roles?
  "Returns true if user has requested roles in organization"
  [requested-authz-roles {organization :organization} user]
  (let [user-org-authz (org-authz organization user)]
    (and (user/authority? user) requested-authz-roles (some requested-authz-roles user-org-authz))))

