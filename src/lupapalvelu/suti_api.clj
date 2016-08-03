(ns lupapalvelu.suti-api
  (:require [sade.core :refer :all]
            [sade.strings :as ss]
            [monger.operators :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr]
            [lupapalvelu.states :as states]
            [lupapalvelu.suti :as suti]))

(defcommand suti-toggle-enabled
  {:description "Enable/disable Suti support."
   :parameters [flag]
   :input-validators [(partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}
   :feature :suti}
  [{user :user}]
  (suti/toggle-enable (usr/authority-admins-organization-id user) flag))

(defcommand suti-toggle-operation
  {:description "Toggles operation either requiring Suti or not."
   :parameters [operationId flag]
   :input-validators [(partial op/visible-operation :operationId)
                      (partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}
   :feature :suti}
  [{user :user}]
  (suti/toggle-operation (suti/admin-org user) (ss/trim operationId) flag))

(defcommand section-toggle-operation
  {:description "Toggles operation either requiring section or not."
   :parameters [operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (suti/toggle-section-operation (suti/admin-org user) (ss/trim operationId) flag))

(defquery suti-admin-details
  {:description "Suti details for the current authority admin's organization."
   :user-roles #{:authorityAdmin}
   :feature :suti}
  [{user :user}]
  (ok :suti (suti/organization-details (suti/admin-org user))))

(defquery suti-operations
  {:description "Suti operations for the current authority admin's organization."
   :user-roles #{:authorityAdmin}
   :feature :suti}
  [{user :user}]
  (ok :operations (-> user suti/admin-org :suti :operations)))

(defcommand suti-www
  {:description "Public Suti URL. Not to be confused with the Suti backend."
   :parameters [www]
   :input-validators [(partial action/validate-optional-url :www)]
   :user-roles #{:authorityAdmin}
   :feature :suti}
  [{user :user}]
  (suti/set-www (suti/admin-org user) (ss/trim www)))

(defquery suti-application-data
  {:description "Fetches the Suti results for the given application."
   :parameters [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :states states/all-application-states
   :feature :suti}
  [{application :application organization :organization}]
  (ok :data (suti/application-data application @organization)))

(defquery suti-application-products
  {:description "Fetches the Suti backend products for the given application."
   :parameters [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :states states/all-application-states
   :feature :suti}
  [{application :application organization :organization}]
  (ok :data (suti/application-products application @organization)))

(defcommand suti-update-id
  {:description "Mechanism for updating Suti id property."
   :parameters [id sutiId]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/string-parameters [:sutiId])]
   :user-roles #{:authority :applicant}
   :states states/all-application-states
   :feature :suti}
  [command]
  (action/update-application command {$set {:suti.id (ss/trim sutiId)}}))

(defcommand suti-update-added
  {:description "Mechanism for updating Suti added property."
   :parameters [id added]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/boolean-parameters [:added])]
   :user-roles #{:authority :applicant}
   :states states/all-application-states
   :feature :suti}
  [command]
  (action/update-application command {$set {:suti.added added}}))
