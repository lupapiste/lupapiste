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
   :feature :suti-integration}
  [{user :user}]
  (suti/toggle-enable (usr/authority-admins-organization-id user) flag))

(defcommand suti-toggle-operation
  {:description "Toggles operation either requiring Suti or not."
   :parameters [operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
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
   :feature :suti-integration}
  [{user :user}]
  (ok :suti (suti/organization-details (suti/admin-org user))))

(defquery suti-operations
  {:description "Suti operations for the current authority admin's organization."
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
  [{user :user}]
  (ok :operations (-> user suti/admin-org :suti :operations)))

(defcommand suti-www
  {:description "Public Suti URL. Not to be confused with the Suti backend."
   :parameters [www]
   :input-validators [(partial action/validate-optional-url :www)]
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
  [{user :user}]
  (suti/set-www (suti/admin-org user) (ss/trim www)))

(defquery suti-application-data
  {:description "Fetches the Suti results for the given application."
   :parameters [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :states states/all-application-states
   :feature :suti-integration}
  [{application :application}]
  (ok :data (suti/application-data application)))

(defcommand suti-update-application
  {:description "Mechanism for updating Suti properties (id and added)
  of the application. Suti parameter does not have to be fully formed."
   :parameters [id suti]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/map-parameters [:suti])]
   :user-roles #{:authority :applicant}
   :states states/all-application-states
   :feature :suti-integration}
  [{application :application :as command}]
  (action/update-application command {$set {:suti (merge (:suti application)
                                                         (reduce (fn [acc [k v]]
                                                                   (assoc acc k (if (string? v)
                                                                                  (ss/trim v)
                                                                                  v)))
                                                                 {}
                                                                 (select-keys suti [:id :added])))}}))
