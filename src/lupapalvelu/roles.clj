(ns lupapalvelu.roles
  (:require [clojure.set :refer [union]]))


;;
;; Roles
;;

(def all-authenticated-user-roles #{:applicant :authority :oirAuthority :admin :financialAuthority})
(def all-user-roles (conj all-authenticated-user-roles
                          :anonymous
                          :rest-api
                          :docstore-api
                          :trusted-etl
                          :trusted-salesforce
                          :onkalo-api
                          :dummy))

(def default-authz-writer-roles #{:writer :foreman})
(def default-authz-reader-roles (conj default-authz-writer-roles :foreman :reader :guest :guestAuthority :financialAuthority :biller))
(def all-authz-writer-roles (conj default-authz-writer-roles :statementGiver))
(def all-authz-roles (union all-authz-writer-roles default-authz-reader-roles))
(def comment-user-authz-roles (conj all-authz-writer-roles :foreman :financialAuthority))
(def writer-roles-with-foreman (conj default-authz-writer-roles :foreman))

(def default-org-authz-roles #{:authority})
(def commenter-org-authz-roles (conj default-org-authz-roles :commenter))
(def reader-org-authz-roles (conj commenter-org-authz-roles :reader :biller))
(def permanent-archive-authority-roles #{:tos-editor :tos-publisher :archivist :digitizer :digitization-project-user})
(def invoicing-roles #{:biller})
(def reporting-roles #{:reporter})
(def departmental-roles
  "Any orgAuthz role provides access to Departmental (Virkapääte). The explicit role is used
  _only_ when the user limited to only Departmental."
  #{:departmental})
(def all-org-authz-roles (union reader-org-authz-roles
                                commenter-org-authz-roles
                                default-org-authz-roles
                                permanent-archive-authority-roles
                                invoicing-roles
                                reporting-roles
                                departmental-roles
                                #{:approver :authorityAdmin}))

(def org-roles-without-admin (disj all-org-authz-roles :authorityAdmin))

(def default-user-authz {:query default-authz-reader-roles
                         :export default-authz-reader-roles
                         :command default-authz-writer-roles
                         :raw default-authz-writer-roles})

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
             (partial merge-with union)
             {} org-authz))

(defn default-authority-admin-organization-id [user]
  (some-> user
          :orgAuthz transpose-orgAuthz
          :authorityAdmin first))
