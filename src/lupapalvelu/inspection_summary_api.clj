(ns lupapalvelu.inspection-summary-api
  (:require [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.user :as usr]
            [lupapalvelu.action :as action :refer [defquery defcommand]]
            [sade.core :refer [ok fail fail! now unauthorized]]
            [sade.strings :as ss]
            [lupapalvelu.states :as states]))

(defquery organization-inspection-summary-settings
  {:description "Inspection summary templates for given organization."
   :pre-checks [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (ok (inspection-summary/settings-for-organization (usr/authority-admins-organization-id user))))

(defcommand modify-inspection-summary-template
  {:description "CRUD API endpoint for inspection summary templates in the given organization."
   :parameters  [func]
   :input-validators [(partial action/select-parameters [:func] #{"create" "update" "delete"})]
   :pre-checks [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :user-roles #{:authorityAdmin}}
  [{user :user {:keys [templateId templateText name]} :data}]
  (let [organizationId (usr/authority-admins-organization-id user)]
    (when (and (ss/blank? templateId) (#{"update" "delete"} func))
      (fail! :error.missing-parameters :parameters "templateId"))
    (when (and (ss/blank? templateText) (#{"create" "update"} func))
      (fail! :error.missing-parameters :parameters "templateText"))
    (when (and (ss/blank? name) (#{"create" "update"} func))
      (fail! :error.missing-parameters :parameters "name"))
    (condp = func
      "create" (inspection-summary/create-template-for-organization organizationId name templateText)
      "update" (if (= (inspection-summary/update-template organizationId templateId name templateText) 1)
                 (ok)
                 (fail :error.not-found))
      "delete" (if (= (inspection-summary/delete-template organizationId templateId) 1)
                 (ok)
                 (fail :error.not-found))
      (fail :error.illegal-function-code))))

(defcommand set-inspection-summary-template-for-operation
  {:description "Toggles operation either requiring section or not."
   :parameters [operationId templateId]
   :input-validators [(partial action/non-blank-parameters [:operationId :templateId])]
   :pre-checks [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organizationId (usr/authority-admins-organization-id user)]
    (inspection-summary/select-template-for-operation organizationId operationId templateId)))

(defquery inspection-summary-templates-for-application
  {:pre-checks [inspection-summary/inspection-summary-api-authority-pre-check]
   :parameters [:id]
   :user-roles #{:authority}}
  [{app :application}]
  (ok :templates (-> (inspection-summary/settings-for-organization (:organization app)) :templates)))