(ns lupapalvelu.roles
  (:require [clojure.set :as set]
            [lupapalvelu.asiointi.roles :as asiointi-api-roles]
            [schema.core :as sc]))

;;
;; Roles
;;

(def roles
  "All known & supported roles, both user roles and various authorization roles (e.g. `:orgAuthz`)."
  #{:admin :anonymous :api-reader :api-writer :applicant :approver :archivist :asiointi-api :auth-owner
    :authority :authorityAdmin :biller :canceler :commenter :departmental :departmental-limited
    :digitization-project-user :digitizer :director :docstore-api :dummy :financialAuthority :foreman
    :global :guest :guestAuthority :oirAuthority :onkalo-api :reader :reporter :rest-api
    :statementGiver :tos-editor :tos-publisher :trusted-etl :trusted-salesforce :writer})

(def Role (apply sc/enum roles))

(def valid-roles! (sc/validator (sc/cond-pre [Role] #{Role})))

;; Use-case specific groupings

(def all-authenticated-user-roles (valid-roles! #{:applicant :authority :oirAuthority :admin :financialAuthority}))
(def all-user-roles
  "Roles applicable to users through `:role`."
  (valid-roles! (conj all-authenticated-user-roles
                      :anonymous
                      :rest-api
                      :docstore-api
                      :asiointi-api
                      :trusted-etl
                      :trusted-salesforce
                      :onkalo-api
                      :dummy)))

(def default-authz-writer-roles (valid-roles! #{:writer :foreman}))
(def default-authz-reader-roles (valid-roles! (conj default-authz-writer-roles
                                                    :foreman :reader :guest :guestAuthority
                                                    :financialAuthority :biller)))
(def all-authz-writer-roles (valid-roles! (conj default-authz-writer-roles :statementGiver)))
(def all-authz-roles (valid-roles! (set/union all-authz-writer-roles default-authz-reader-roles)))
(def comment-user-authz-roles (valid-roles! (conj all-authz-writer-roles :foreman :financialAuthority)))
(def writer-roles-with-foreman (valid-roles! (conj default-authz-writer-roles :foreman)))

(def default-org-authz-roles (valid-roles! #{:authority}))
(def commenter-org-authz-roles (valid-roles! (conj default-org-authz-roles :commenter)))
(def reader-org-authz-roles (valid-roles! (conj commenter-org-authz-roles :reader :biller)))
(def permanent-archive-authority-roles (valid-roles! #{:tos-editor :tos-publisher :archivist :digitizer :digitization-project-user}))
(def invoicing-roles (valid-roles! #{:biller}))
(def integration-roles
  "`orgAuthz` roles used wrt. integration API users (AsiointiAPI)"
  (valid-roles! (set asiointi-api-roles/roles)))
(def reporting-roles (valid-roles! #{:reporter :director}))
(def departmental-roles
  "Oauth-roles that give access to Departmental (Virkapääte). In addition to oauth-roles,
  implicit :departmental role is given to those authorities that have :authority,
  :reader, or :commenter role in :orgAuthz"
  #{:departmental :departmental-limited})

(def authorized-to-orgs-roles
  "Roles that use `:orgAuthz` to access organization(s)."
  (valid-roles! #{:api-reader :api-writer :approver :authority :biller
                  :commenter :financialAuthority :reader}))

(def oauth-roles
  "Roles applicable to users through `:oauth-roles.role`"
  (valid-roles! (set/union departmental-roles reporting-roles #{:admin})))

(def oauth-roles-without-admin (valid-roles! (disj oauth-roles :admin)))

(def all-org-authz-roles
  "Roles applicable to users through `:orgAuthz`."
  (valid-roles! (set/union reader-org-authz-roles
                           commenter-org-authz-roles
                           default-org-authz-roles
                           permanent-archive-authority-roles
                           invoicing-roles
                           integration-roles
                           #{:approver :authorityAdmin})))

(def authority-managed-org-authz-roles
  "Roles applicable to users through `:orgAuthz`, which the authority admin may control."
  (valid-roles! (set/difference all-org-authz-roles
                                integration-roles)))

(def default-user-authz {:query   default-authz-reader-roles
                         :export  default-authz-reader-roles
                         :command default-authz-writer-roles
                         :raw     default-authz-writer-roles})

(defn organization-ids-by-roles
  "Returns a set of organization IDs where user has given roles.
  Note: the user must have gone through with-org-auth (the orgAuthz
  must be keywords)."
  [{org-authz :orgAuthz} roles]
  {:pre [(set? roles) (every? keyword? roles)]}
  (->> org-authz
       (filter (fn [[_ org-roles]] (some roles org-roles)))
       (map (comp name first))
       set))

(defn transpose-orgAuthz [org-authz]
  (transduce (map (fn [[org-id roles]] (zipmap roles (repeat #{org-id}))))
             (partial merge-with set/union)
             {} org-authz))

(defn default-authority-admin-organization-id [user]
  (some-> user
          :orgAuthz transpose-orgAuthz
          :authorityAdmin first))
