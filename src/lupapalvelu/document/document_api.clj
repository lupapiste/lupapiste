(ns lupapalvelu.document.document-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error]]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! unauthorized unauthorized! now]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.building-site :as bsite]
            [lupapalvelu.document.approval :as approval]
            [lupapalvelu.document.document :refer :all]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]))


;; Action category: documents & tasks

(defn- build-document-params [{application-id :id} {document-id :id info :schema-info}]
  {:id  application-id
   :doc document-id
   :docId document-id
   :documentId document-id
   :schemaName (:name info)
   :collection "documents"})

(defn- build-task-params [{application-id :id} {document-id :id}]
  {:id  application-id
   :doc document-id
   :docId document-id
   :documentId document-id
   :collection "tasks"})

(defmethod action/allowed-actions-for-category :documents
  [command]
  (action/allowed-actions-for-collection :documents build-document-params command))

(defmethod action/allowed-actions-for-category :tasks
  [command]
  (action/allowed-actions-for-collection :tasks build-task-params command))

(defn editable-by-state?
  "Pre-check to determine if documents are editable in abnormal states"
  [default-states]
  (fn [{document :document {state :state} :application}]
    (when document
      (when-not (-> document
                    (model/get-document-schema)
                    (state-valid-by-schema? :editable-in-states default-states state))
        (fail :error.document-not-editable-in-current-state)))))

(defn addable-by-state?
  [default-states]
  (fn [{{schema-name :schemaName} :data {state :state schema-version :schema-version} :application}]
    (when (ss/not-blank? schema-name)
      (when-not (-> (schemas/get-schema schema-version schema-name)
                    (state-valid-by-schema? :addable-in-states default-states state))
        (fail :error.document.post-verdict-addition)))))

(defquery document
  {:parameters       [:id doc collection]
   :categories       #{:documents :tasks}
   :states           states/all-states
   :input-validators [doc-persistence/validate-collection]
   :permissions      [{:required [:application/read]}]}
  [{:keys [application user]}]
  (if-let [document (doc-persistence/by-id application collection doc)]
    (ok :document (application/process-document-or-task user application document))
    (fail :error.document-not-found)))

;;
;; CRUD
;;

(def document-edit-permissions
  [{:context  {:application {:state #{:draft}}}
    :required [:application/read :document/edit-draft]}
   {:required [:application/read :document/edit]}])

(defcommand create-doc
  {:parameters [:id :schemaName]
   :categories  #{:documents}
   :optional-parameters [updates fetchRakennuspaikka]
   :input-validators [(partial action/non-blank-parameters [:id :schemaName])]
   :pre-checks [(addable-by-state? states/create-doc-states)
                create-doc-validator]
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft]}
                 {:required [:application/edit]}]}
  [{{schema-name :schemaName} :data :as command}]
  (let [document (doc-persistence/do-create-doc! command schema-name updates)]
    (when fetchRakennuspaikka
      (let [property-id (or (tools/get-update-item-value updates "kiinteisto.kiinteistoTunnus")
                            (get-in command [:application :propertyId]))]
        (bsite/fetch-and-persist-ktj-tiedot (:application command) document property-id (now))))
    (ok :doc (:id document))))

(defn do-remove-doc! [command document id docId]
  (doc-persistence/remove! command document)
  (assignment/remove-target-from-assignments id docId))

(defcommand remove-doc
  {:parameters       [id docId]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :docId])]
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :pre-checks       [(editable-by-state? #{:draft :answered :open :submitted :complementNeeded})
                      doc-disabled-validator
                      remove-doc-validator]}
  [{:keys [document] :as command}]
  (do-remove-doc! command document id docId)
  #_(doc-persistence/remove! command document)
  #_(assignment/remove-target-from-assignments id docId)
  (ok))

(defn- update-document-assignment-statuses [app-id doc-id doc-status]
  (if (= doc-status "disabled")
    (assignment/update-assignments {$and [{:application.id app-id}
                                          {:targets {$size 1}}
                                          {:targets.id doc-id}]}
                                   {$set {:status "canceled"}})
    (assignment/set-assignment-status app-id doc-id "active")))

(defcommand set-doc-status
  {:description      "Set document status to disabled: true or disabled: false"
   :parameters       [id docId value]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :docId])
                      (partial action/select-parameters [:value] #{"enabled" "disabled"})]
   :states           states/post-verdict-states
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :pre-checks       [(editable-by-state? nil)       ; edition defined solely by document schema
                      document-disableable-precheck
                      validate-document-is-pre-verdict-or-approved]}
  [command]
  (doc-persistence/set-disabled-status command docId value)
  (update-document-assignment-statuses id docId value)
  (ok))

(defcommand update-doc
  {:parameters       [id doc updates]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :pre-checks       [(editable-by-state? states/update-doc-states)
                      doc-disabled-validator
                      validate-created-after-verdict
                      validate-post-verdict-not-approved]}
  [command]
  (doc-persistence/update! command doc updates "documents"))

(defcommand update-task
  {:parameters       [id doc updates]
   :categories       #{:tasks}
   :states           (states/all-application-states-but (conj states/terminal-states :sent :draft))
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :permissions      [{:required [:application/edit :task/edit]}]}
  [command]
  (doc-persistence/update! command doc updates "tasks"))

(defcommand remove-document-data
  "Remove document data under some path (recurring sections).
   This previously supported also tasks, but there are no task schemas that would use it, so the support was removed."
  {:parameters  [id doc path]
   :categories  #{:documents}
   :contexts    [document-context]
   :permissions document-edit-permissions
   :pre-checks  [(editable-by-state? #{:draft :answered :open :submitted :complementNeeded})
                 doc-disabled-validator]}
  [command]
  (doc-persistence/remove-document-data command doc [path] :documents))

;;
;; Document validation
;;

(defquery fetch-validation-errors
  {:parameters  [:id]
   :permissions [{:required [:application/read]}]
   :states      states/all-states}
  [{app :application}]
  (ok :results (application/pertinent-validation-errors app)))

;;
;; Document approvals
;;

(defcommand approve-doc
  {:parameters       [:id :doc :path]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc])]
   :contexts         [document-context]
   :permissions      [{:required [:application/edit :document/approve]}]
   :pre-checks       [(editable-by-state? states/approve-doc-states)
                      doc-disabled-validator]}
  [command]
  (ok :approval (approval/approve command "approved")))

(defcommand reject-doc
  {:parameters       [:id :doc :path]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])]
   :contexts         [document-context]
   :permissions      [{:required [:application/edit :document/approve]}]
   :pre-checks       [(editable-by-state? states/approve-doc-states)
                      doc-disabled-validator]}
  [command]
  (ok :approval (approval/approve command "rejected")))

(defcommand reject-doc-note
  {:description      "Explanatory note regarding the reject reason. Adding
  note updates application modified timestamp but not the approval
  timestamp"
   :parameters       [:id :doc :path note]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])]
   :contexts         [document-context]
   :permissions      [{:required [:application/edit :document/approve]}]
   :pre-checks       [(editable-by-state? states/approve-doc-states)]}
  [command]
  (approval/set-rejection-note command note)
  (ok))

;;
;; Set party to document
;;

(defcommand set-user-to-document
  {:parameters       [id documentId userId path]
   :categories       #{:documents}
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks       [(editable-by-state? states/update-doc-states)
                      user-can-be-set-validator
                      doc-disabled-validator]}
  [{:keys [created application user] :as command}]
  (doc-persistence/do-set-user-to-document application documentId userId path created user))

(defcommand set-current-user-to-document
  {:parameters       [id documentId path]
   :categories       #{:documents}
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks       [(editable-by-state? states/update-doc-states)
                      doc-disabled-validator]}
  [{:keys [created application user] :as command}]
  (doc-persistence/do-set-user-to-document application documentId (:id user) path created user))

(defcommand set-company-to-document
  {:description      "Updates company information on the document.The
  contact person information is filled only if either the current user
  is a company member or the application has a company member in the
  auth array. Otherwise, left empty."
   :parameters       [id documentId companyId path]
   :categories       #{:documents}
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks       [(editable-by-state? states/update-doc-states)
                      doc-disabled-validator]}
  [{:keys [user created application document] :as command}]
  (doc-persistence/do-set-company-to-document application
                                              document
                                              companyId
                                              path
                                              (user/get-user-by-id (:id user))
                                              created))
