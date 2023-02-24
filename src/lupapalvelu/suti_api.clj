(ns lupapalvelu.suti-api
  "Suti is an external service that provides prerequisite documents/products."
  (:require [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [monger.operators :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.suti :as suti]))

(defcommand suti-toggle-enabled
  {:description      "Enable/disable Suti support."
   :parameters       [organizationId flag]
   :input-validators [(partial action/boolean-parameters [:flag])]
   :permissions      [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (org/toggle-group-enabled organizationId :suti flag))

(defcommand suti-toggle-operation
  {:description      "Toggles operation either requiring Suti or not."
   :parameters       [organizationId operationId flag]
   :input-validators [(partial op/visible-operation :operationId)
                      (partial action/boolean-parameters [:flag])]
   :permissions      [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (org/toggle-group-operation (util/find-by-id organizationId user-organizations) :suti (ss/trim operationId) flag))

(defquery suti-admin-details
  {:description "Suti details for the current authority admin's organization."
   :parameters  [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (ok :suti (suti/organization-details (util/find-by-id organizationId user-organizations))))

(defquery suti-operations
  {:description "Suti operations for the current authority admin's organization."
   :parameters  [organizationId]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (ok :operations (-> (util/find-by-id organizationId user-organizations) :suti :operations)))

(defcommand suti-www
  {:description      "Public Suti URL. Not to be confused with the Suti backend."
   :parameters       [organizationId www]
   :input-validators [(partial action/validate-optional-url :www)]
   :permissions      [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (suti/set-www (util/find-by-id organizationId user-organizations) (ss/trim www)))

(defquery suti-application-data
  {:description      "Fetches the Suti results for the given application."
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:authority :applicant}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :states           states/all-application-states}
  [{application :application organization :organization}]
  (ok :data (suti/application-data application @organization)))

(defquery suti-application-products
  {:description      "Fetches the Suti backend products for the given application."
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:authority :applicant}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :states           states/all-application-states}
  [{application :application organization :organization}]
  (ok :data (suti/application-products application @organization)))

(defcommand suti-update-id
  {:description      "Mechanism for updating Suti id property."
   :parameters       [id sutiId]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/string-parameters [:sutiId])]
   :user-roles       #{:authority :applicant}
   :states           states/all-application-states}
  [command]
  (action/update-application command {$set {:suti.id (ss/trim sutiId)}}))

(defcommand suti-update-added
  {:description      "Mechanism for updating Suti added property."
   :parameters       [id added]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/boolean-parameters [:added])]
   :user-roles       #{:authority :applicant}
   :states           states/all-application-states}
  [command]
  (action/update-application command {$set {:suti.added added}}))

(defquery suti-pre-sent-state
  {:description      "Pseudo query for checking the application state."
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:authority :applicant}
   :states           states/pre-sent-application-states})
