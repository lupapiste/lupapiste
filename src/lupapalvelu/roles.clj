(ns lupapalvelu.roles
  (:require [clojure.set :refer [union]]))


;;
;; Roles
;;

(def all-authenticated-user-roles #{:applicant :authority :oirAuthority :authorityAdmin :admin :financialAuthority})
(def all-user-roles (conj all-authenticated-user-roles :anonymous :rest-api :docstore-api :trusted-etl :trusted-salesforce :onkalo-api))

(def default-authz-writer-roles #{:writer})
(def default-authz-reader-roles (conj default-authz-writer-roles :foreman :reader :guest :guestAuthority :financialAuthority))
(def all-authz-writer-roles (conj default-authz-writer-roles :statementGiver))
(def all-authz-roles (union all-authz-writer-roles default-authz-reader-roles))
(def comment-user-authz-roles (conj all-authz-writer-roles :foreman :financialAuthority))
(def writer-roles-with-foreman (conj default-authz-writer-roles :foreman))

(def default-org-authz-roles #{:authority :approver})
(def commenter-org-authz-roles (conj default-org-authz-roles :commenter))
(def reader-org-authz-roles (conj commenter-org-authz-roles :reader))
(def all-org-authz-roles (conj reader-org-authz-roles :authorityAdmin :tos-editor :tos-publisher :archivist :digitizer))

(def default-user-authz {:query default-authz-reader-roles
                         :export default-authz-reader-roles
                         :command default-authz-writer-roles
                         :raw default-authz-writer-roles})
