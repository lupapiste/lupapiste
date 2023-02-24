(ns lupapalvelu.inspection-summary-api
  (:require [lupapalvelu.action :as action :refer [defquery defcommand]]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$push]]
            [sade.core :refer [ok fail]]
            [sade.util :as util]))

(defn- build-inspection-summary-query-params [{application-id :id} {summary-id :id}]
  {:id        application-id
   :summaryId summary-id})

(defmethod action/allowed-actions-for-category :inspection-summaries
  [command]
  (action/allowed-actions-for-collection :inspection-summaries build-inspection-summary-query-params command))

;; ----------------------------
;; Authority admin
;; ----------------------------

(defquery organization-inspection-summary-settings
  {:description "Inspection summary templates for given organization."
   :parameters  [organizationId]
   :pre-checks  [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :permissions [{:required [:organization/admin]}]}
  [{:keys [user-organizations]}]
  (ok (inspection-summary/settings-for-organization (util/find-by-id organizationId user-organizations))))

(defcommand create-inspection-summary-template
  {:description      "Create a new inspection summary template in the given organization."
   :parameters       [organizationId templateText name]
   :input-validators [(partial action/non-blank-parameters [:templateText :name])]
   :pre-checks       [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (ok :id (inspection-summary/create-template-for-organization organizationId name templateText)))

(defcommand delete-inspection-summary-template
  {:description      "Modify inspection summary templates in the given organization."
   :parameters       [organizationId templateId]
   :input-validators [(partial action/non-blank-parameters [:templateId])]
   :pre-checks       [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (if (= (inspection-summary/delete-template organizationId templateId) 1)
    (ok)
    (fail :error.not-found)))

(defcommand modify-inspection-summary-template
  {:description      "Deletes an inspection summary template in the given organization."
   :parameters       [organizationId name templateId templateText]
   :input-validators [(partial action/non-blank-parameters [:name :templateId :templateText])]
   :pre-checks       [inspection-summary/inspection-summary-api-auth-admin-pre-check]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (if (= (inspection-summary/update-template organizationId templateId name templateText) 1)
    (ok)
    (fail :error.not-found)))

(defcommand set-inspection-summary-template-for-operation
  {:description      "Toggles operation either requiring section or not."
   :parameters       [organizationId operationId templateId]
   :input-validators [(partial action/non-blank-parameters [:operationId :templateId])]
   :pre-checks       [inspection-summary/inspection-summary-api-auth-admin-pre-check
                      inspection-summary/operation-has-R-permit-type]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (inspection-summary/set-default-template-for-operation organizationId operationId templateId))

;; ----------------------------
;; Authority and applicant
;; ----------------------------

(defn- map-operation-to-frontend [app op]
  (let [document (domain/get-document-by-operation app op)]
    (assoc (select-keys op [:id :name :description])
      :op-identifier (or (schemas/resolve-identifier document)
                         (first (schemas/resolve-accordion-field-values document))))))

(defquery inspection-summaries-for-application
  {:pre-checks  [inspection-summary/inspection-summaries-enabled
                 inspection-summary/application-has-R-permit-type-pre-check
                 (action/not-pre-check (partial permit/valid-permit-types
                                                {:R ["tyonjohtaja-hakemus"
                                                     "tyonjohtaja-ilmoitus"]}))
                 (app/reject-primary-operations #{:raktyo-aloit-loppuunsaat :jatkoaika})]
   :parameters  [:id]
   :permissions [{:required [:application/read]}]
   :categories  #{:inspection-summaries}
   :states      states/post-verdict-states}
  [{app :application :keys [organization]}]
  (ok :templates (-> @organization inspection-summary/settings-for-organization :templates)
      :summaries (inspection-summary/get-summaries app)
      :operations (->> (app-utils/get-operations app)
                       (map (partial map-operation-to-frontend app))
                       (remove nil?))))

(defcommand create-inspection-summary
  {:pre-checks       [inspection-summary/inspection-summaries-enabled
                      inspection-summary/application-has-R-permit-type-pre-check]
   :parameters       [id templateId operationId]
   :categories       #{:inspection-summaries}
   :input-validators [(partial action/non-blank-parameters [:operationId :templateId])]
   :permissions      [{:required [:application/manage-inspection-summary]}]}
  [{app :application org :organization :as command}]
  (let [operation                    (util/find-by-id operationId (app-utils/get-operations app))
        {summary-id :id :as summary} (inspection-summary/summary-data-for-operation
                                       @org
                                       operation
                                       templateId)]
    (if summary
      (do
        (action/update-application command {$push {:inspection-summaries summary}})
        (ok :id summary-id))
      (fail :error.unknown))))

(defcommand delete-inspection-summary
  {:pre-checks       [inspection-summary/inspection-summaries-enabled
                      inspection-summary/validate-that-summary-can-be-deleted
                      inspection-summary/validate-summary-found-in-application
                      inspection-summary/validate-summary-not-locked]
   :parameters       [:id summaryId]
   :categories       #{:inspection-summaries}
   :input-validators [(partial action/non-blank-parameters [:summaryId])]
   :permissions      [{:required [:application/manage-inspection-summary]}]}
  [{app :application}]
  (inspection-summary/delete-summary app summaryId)
  (ok))

(defcommand toggle-inspection-summary-locking
  {:pre-checks       [inspection-summary/inspection-summaries-enabled
                      inspection-summary/validate-summary-found-in-application]
   :parameters       [:id summaryId isLocked]
   :categories       #{:inspection-summaries}
   :input-validators [(partial action/non-blank-parameters [:summaryId])
                      (partial action/boolean-parameters [:isLocked])]
   :permissions      [{:required [:application/manage-inspection-summary]}]}
  [command]
  (->> (inspection-summary/toggle-summary-locking command summaryId isLocked)
       (ok :job)))

(defcommand add-target-to-inspection-summary
  {:pre-checks       [inspection-summary/inspection-summaries-enabled
                      inspection-summary/validate-summary-not-locked]
   :parameters       [:id summaryId targetName]
   :categories       #{:inspection-summaries}
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetName])]
   :permissions      [{:required [:application/manage-inspection-summary]}]}
  [{{appId :id} :application}]
  (ok :id (inspection-summary/add-target appId summaryId targetName)))

(defcommand edit-inspection-summary-target
  {:pre-checks       [inspection-summary/inspection-summaries-enabled
                      inspection-summary/deny-if-finished
                      inspection-summary/validate-summary-target-found-in-application
                      inspection-summary/validate-summary-not-locked]
   :parameters       [:id summaryId targetId targetName]
   :categories       #{:inspection-summaries}
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetName])]
   :permissions      [{:required [:application/manage-inspection-summary]}]}
  [{application :application}]
  (inspection-summary/edit-target application summaryId targetId {:set {:target-name targetName}})
  (ok))

(defcommand remove-target-from-inspection-summary
  {:pre-checks       [inspection-summary/inspection-summaries-enabled
                      inspection-summary/validate-summary-not-locked]
   :parameters       [:id summaryId targetId]
   :categories       #{:inspection-summaries}
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetId])]
   :permissions      [{:required [:application/manage-inspection-summary]}]}
  [{application :application}]
  (inspection-summary/remove-target application summaryId targetId)
  (ok))

(defcommand set-target-status
  {:description      "Marks the target inspection either done or undone."
   :pre-checks       [inspection-summary/inspection-summaries-enabled
                      inspection-summary/validate-summary-target-found-in-application
                      inspection-summary/validate-summary-not-locked]
   :parameters       [:id summaryId targetId isFinished]
   :categories       #{:inspection-summaries}
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetId])
                      (partial action/boolean-parameters [:isFinished])]
   :permissions      [{:required [:application/edit-inspection-summary]}]}
  [{:keys [application user created]}]
  (inspection-summary/edit-target application summaryId targetId
                                  (if isFinished
                                    {:set {:finished      true
                                           :finished-date created
                                           :finished-by   (usr/summary user)}}
                                    {:set   {:finished false}
                                     :unset {:finished-date 1
                                             :finished-by   1}}))
  (ok))

(defcommand set-inspection-date
  {:pre-checks       [inspection-summary/inspection-summaries-enabled
                      inspection-summary/validate-summary-target-found-in-application
                      inspection-summary/validate-summary-not-locked
                      inspection-summary/deny-if-finished]
   :parameters       [:id summaryId targetId date]
   :categories       #{:inspection-summaries}
   :input-validators [(partial action/non-blank-parameters [:summaryId :targetId])]
   :permissions      [{:required [:application/edit-inspection-summary]}]}
  [{:keys [application]}]
  (inspection-summary/edit-target application summaryId targetId {:set {:inspection-date date}})
  (ok))
