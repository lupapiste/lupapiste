(ns lupapalvelu.suti-api
  (:require [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr]
            [lupapalvelu.suti :as suti]))

(defcommand suti-enabled
  {:description "Enable/disable Suti support."
   :parameters [flag]
   :input-validators [(partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
  [{user :user}]
  (suti/enable (usr/authority-admins-organization-id user) flag))

(defcommand suti-toggle-operation
  {:description "Toggles operation either requiring Suti or not."
   :parameters [operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
  [{user :user}]
  (suti/toggle-operation (suti/admin-org user) operationId flag))

(defcommand section-toggle-operation
  {:description "Toggles operation either requiring section or not."
   :parameters [operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (suti/toggle-section-operation (suti/admin-org user) operationId flag))
