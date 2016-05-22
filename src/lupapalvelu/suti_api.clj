(ns lupapalvelu.suti-api
  (:require [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters] :as action]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr]
            [lupapalvelu.suti :as suti]))

(defquery suti-operations
  {:description "Identifiers of the selected/checked Suti operations
  for the user's organization"
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
  [{user :user}]
  (ok :operations (-> user suti/admin-org :suti :operations)))

(defquery organization-operation-tree
  {:description "The full operation tree for the user's organization."
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
  [{user :user}]
  (ok :tree (op/organization-operations (suti/admin-org user))))

(defcommand suti-update-operations
  {:description "Reset the selected Suti operations. Operations
  parameter is a list of operation identifiers"
   :parameters [operations]
   :in
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
  [{user :user}]
  (suti/update-operations (suti/admin-org user) operations))
