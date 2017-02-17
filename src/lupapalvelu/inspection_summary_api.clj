(ns lupapalvelu.inspection-summary-api
  (:require [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.user :as usr]
            [lupapalvelu.action :as action :refer [defquery defcommand]]
            [sade.core :refer [ok fail fail! now unauthorized]]
            [sade.strings :as ss]
            [lupapalvelu.states :as states]
            [lupapalvelu.application :as app]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [sade.util :as util]))

(defquery organization-inspection-summary-settings
  {:description "Inspection summary templates for given organization."
   :pre-checks [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (ok (inspection-summary/settings-for-organization (usr/authority-admins-organization-id user))))

(defcommand create-inspection-summary-template
  {:description "Create a new inspection summary template in the given organization."
   :parameters  [templateText name]
   :input-validators [(partial action/non-blank-parameters [:templateText :name])]
   :pre-checks [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organizationId (usr/authority-admins-organization-id user)]
    (inspection-summary/create-template-for-organization organizationId name templateText)))

(defcommand delete-inspection-summary-template
  {:description "Modify inspection summary templates in the given organization."
   :parameters  [templateId]
   :input-validators [(partial action/non-blank-parameters [:templateId])]
   :pre-checks [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organizationId (usr/authority-admins-organization-id user)]
    (if (= (inspection-summary/delete-template organizationId templateId) 1)
      (ok)
      (fail :error.not-found))))

(defcommand modify-inspection-summary-template
  {:description "Deletes an inspection summary template in the given organization."
   :parameters  [name templateId templateText]
   :input-validators [(partial action/non-blank-parameters [:name :templateId :templateText])]
   :pre-checks [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organizationId (usr/authority-admins-organization-id user)]
    (if (= (inspection-summary/update-template organizationId templateId name templateText) 1)
      (ok)
      (fail :error.not-found))))

(defcommand set-inspection-summary-template-for-operation
  {:description "Toggles operation either requiring section or not."
   :parameters [operationId templateId]
   :input-validators [(partial action/non-blank-parameters [:operationId :templateId])]
   :pre-checks [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organizationId (usr/authority-admins-organization-id user)]
    (inspection-summary/set-default-template-for-operation organizationId operationId templateId)))

(defn- map-operation-to-frontend [app op]
  (let [document (domain/get-document-by-operation app op)
        {identifier-field :name} (schemas/find-identifier-field-from (get-in document [:schema-info :name]))]
    (assoc (select-keys op [:id :name :description])
      :op-identifier (or (get-in document [:data (keyword identifier-field) :value])
                         (get-in document [:data :valtakunnallinenNumero :value])))))

(defquery inspection-summaries-for-application
  {:pre-checks [(action/some-pre-check
                  inspection-summary/inspection-summary-api-authority-pre-check
                  inspection-summary/inspection-summary-api-applicant-pre-check)]
   :parameters [:id]
   :states states/post-verdict-states
   :user-roles #{:authority :applicant}}
  [{app :application}]
  (ok :templates (-> (inspection-summary/settings-for-organization (:organization app)) :templates)
      :summaries (inspection-summary/get-summaries app)
      :operations (->> (app/get-operations app)
                       (map (partial map-operation-to-frontend app))
                       (remove nil?))))

(defcommand create-inspection-summary
  {:pre-checks [inspection-summary/inspection-summary-api-authority-pre-check]
   :parameters [:id templateId operationId]
   :input-validators [(partial action/non-blank-parameters [:operationId :templateId])]
   :user-roles #{:authority}}
  [{app :application}]
  (ok :id (inspection-summary/new-summary-for-operation
            app
            (util/find-by-key :id operationId (app/get-operations app))
            templateId)))

(defcommand add-target-to-inspection-summary
  {:pre-checks [inspection-summary/inspection-summary-api-authority-pre-check]
   :parameters [:id summaryId targetName]
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetName])]
   :user-roles #{:authority}}
  [{{appId :id} :application}]
  (ok :id (inspection-summary/add-target appId summaryId targetName)))

(defcommand edit-inspection-summary-target
  {:pre-checks [inspection-summary/inspection-summary-api-authority-pre-check]
   :parameters [:id summaryId targetId targetName]
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetName])]
   :user-roles #{:authority}}
  [{application :application}]
  (inspection-summary/edit-target application summaryId targetId {:set {:target-name targetName}})
  (ok))

(defcommand remove-target-from-inspection-summary
  {:pre-checks [inspection-summary/inspection-summary-api-authority-pre-check]
   :parameters [:id summaryId targetId]
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetId])]
   :user-roles #{:authority}}
  [{application :application}]
  (inspection-summary/remove-target application summaryId targetId)
  (ok))

(defcommand set-target-status
  {:pre-checks [inspection-summary/inspection-summary-api-applicant-pre-check]
   :parameters [:id summaryId targetId status]
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetId])
                      (partial action/boolean-parameters [:status])]
   :user-roles #{:applicant}}
  [{application :application user :user}]
  (let [params (when status
                 {:set {:finished true :finished-date (now) :finished-by (usr/summary user)}}
                 {:set {:finished false}
                  :unset {:finished-date 1 :finished-by 1}})]
    (inspection-summary/edit-target application summaryId targetId params)
    (ok)))