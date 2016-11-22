(ns lupapalvelu.document.document-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error]]
            [clojure.set :refer [intersection union difference]]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! unauthorized unauthorized! now]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [lupapalvelu.document.document :refer :all]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]))


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

(defn- state-valid-by-schema? [schema schema-states-key default-states state]
  (-> (get-in schema [:info (keyword schema-states-key)])
      (or default-states)
      (contains? (keyword state))))

(defn editable-by-state?
  "Pre-check to determine if documents are editable in abnormal states"
  [data-key default-states {data :data {docs :documents state :state} :application}]
  (when-let [doc (domain/get-document-by-id docs (get data (keyword data-key)))]
    (when-not (-> doc
                  (model/get-document-schema)
                  (state-valid-by-schema? :editable-in-states default-states state))
      (fail :error.document-not-editable-in-current-state))))

(defn addable-by-state?
  [default-states {{schema-name :schemaName} :data {state :state schema-version :schema-version} :application}]
  (when (ss/not-blank? schema-name)
    (when-not (-> (schemas/get-schema schema-version schema-name)
                  (state-valid-by-schema? :addable-in-states default-states state))
      (fail :error.document.post-verdict-addition))))

(defn- validate-user-authz-by-key
  [doc-id-key {:keys [data application user]}]
  {:pre [(keyword? doc-id-key)]}
  (let [doc-id (get data doc-id-key)
        authority? (auth/application-authority? application user)
        schema (when doc-id (some-> application (domain/get-document-by-id doc-id) model/get-document-schema))
        user-roles (->> user :id (auth/get-auths application) (map (comp keyword :role)) set)
        allowed-roles (get-in schema [:info :user-authz-roles] auth/default-authz-writer-roles)]
    (when (and doc-id (not authority?))
      (cond
        (nil? schema) (fail :error.document-not-found)
        (empty? (intersection allowed-roles user-roles)) unauthorized))))

(def validate-user-authz-by-doc (partial validate-user-authz-by-key :doc))
(def validate-user-authz-by-doc-id (partial validate-user-authz-by-key :doc-id))
(def validate-user-authz-by-document-id (partial validate-user-authz-by-key :documentId))

(defquery document
  {:parameters       [:id doc collection]
   :categories       #{:documents :tasks}
   :user-roles       #{:applicant :authority}
   :states           states/all-states
   :input-validators [doc-persistence/validate-collection]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{:keys [application user]}]
  (if-let [document (doc-persistence/by-id application collection doc)]
    (ok :document (application/process-document-or-task user application document))
    (fail :error.document-not-found)))

;;
;; CRUD
;;

(defcommand create-doc
  {:parameters [:id :schemaName]
   :categories  #{:documents}
   :optional-parameters [updates fetchRakennuspaikka]
   :input-validators [(partial action/non-blank-parameters [:id :schemaName])]
   :user-roles #{:applicant :authority}
   :pre-checks [(partial addable-by-state? #{:draft :answered :open :submitted :complementNeeded})
                create-doc-validator
                application/validate-authority-in-drafts]}
  [{{schema-name :schemaName} :data :as command}]
  (let [document (doc-persistence/do-create-doc! command schema-name updates)]
    (when fetchRakennuspaikka
      (let [property-id (or
                          (tools/get-update-item-value updates "kiinteisto.kiinteistoTunnus")
                          (get-in command [:application :propertyId]))]
        (fetch-and-persist-ktj-tiedot (:application command) document property-id (now))))
    (ok :doc (:id document))))

(defcommand remove-doc
  {:parameters  [id docId]
   :categories  #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :docId])]
   :user-roles #{:applicant :authority}
   :pre-checks [(partial editable-by-state? :docId #{:draft :answered :open :submitted :complementNeeded})
                application/validate-authority-in-drafts
                validate-user-authz-by-doc-id
                (partial doc-disabled-validator :docId)
                remove-doc-validator]}
  [{:keys [application created] :as command}]
  (if-let [document (domain/get-document-by-id application docId)]
    (do
      (doc-persistence/remove! command docId "documents")
      (assignment/remove-assignments-by-target id docId)
      (ok))
    (fail :error.document-not-found)))

(defcommand set-doc-status
  {:description "Set document status to disabled: true or disabled: false"
   :parameters [id docId value]
   :categories  #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :docId])
                      (partial action/select-parameters [:value] #{"enabled" "disabled"})]
   :user-roles #{:applicant :authority}
   :states     states/post-verdict-states
   :pre-checks [(partial editable-by-state? :docId nil)            ; edition defined solely by document schema
                validate-user-authz-by-doc-id]}
  [command]
  (if (domain/get-document-by-id (:application command) docId)
    (do
      (doc-persistence/set-disabled-status command docId value)
      (assignment/set-assignment-status id docId (if (= value "disabled") "canceled" "active"))
      (ok))
    (fail :error.document-not-found)))

(defcommand update-doc
  {:parameters [id doc updates]
   :categories #{:documents}
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :pre-checks [(partial editable-by-state? :doc states/update-doc-states)
                validate-user-authz-by-doc
                application/validate-authority-in-drafts
                (partial doc-disabled-validator :doc)
                (partial validate-post-verdict-update-doc :doc)]}
  [command]
  (doc-persistence/update! command doc updates "documents"))

(defcommand update-task
  {:parameters [id doc updates]
   :categories #{:tasks}
   :user-roles #{:applicant :authority}
   :states     (states/all-application-states-but (conj states/terminal-states :sent))
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :pre-checks [validate-user-authz-by-doc
                application/validate-authority-in-drafts]}
  [command]
  (doc-persistence/update! command doc updates "tasks"))

(defcommand remove-document-data
  {:parameters       [id doc path collection]
   :categories       #{:documents :tasks}
   :user-roles       #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :input-validators [doc-persistence/validate-collection]
   :pre-checks       [(partial editable-by-state? :doc #{:draft :answered :open :submitted :complementNeeded})
                      validate-user-authz-by-doc
                      application/validate-authority-in-drafts
                      (partial doc-disabled-validator :doc)]}
  [command]
  (doc-persistence/remove-document-data command doc [path] collection))

;;
;; Document validation
;;

(defquery validate-doc
  {:parameters       [:id doc collection]
   :categories       #{:documents :tasks}
   :user-roles       #{:applicant :authority}
   :states           states/all-states
   :input-validators [doc-persistence/validate-collection]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{:keys [application]}]
  (let [document (doc-persistence/by-id application collection doc)]
    (when-not document (fail! :error.document-not-found))
    (ok :results (model/validate application document))))

(defquery fetch-validation-errors
  {:parameters       [:id]
   :user-roles       #{:applicant :authority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles
   :states           states/all-states}
  [{app :application}]
  (ok :results (application/pertinent-validation-errors app)))

;;
;; Document approvals
;;

(defcommand approve-doc
  {:parameters [:id :doc :path :collection]
   :categories       #{:documents :tasks}
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])
                      doc-persistence/validate-collection]
   :pre-checks [(partial editable-by-state? :doc states/approve-doc-states)
                (partial doc-disabled-validator :doc)]
   :user-roles #{:authority}}
  [command]
  (ok :approval (approve command "approved")))

(defcommand reject-doc
  {:parameters [:id :doc :path :collection]
   :categories       #{:documents :tasks}
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])
                      doc-persistence/validate-collection]
   :pre-checks [(partial editable-by-state? :doc states/approve-doc-states)
                (partial doc-disabled-validator :doc)]
   :user-roles #{:authority}}
  [command]
  (ok :approval (approve command "rejected")))

;;
;; Set party to document
;;

(defcommand set-user-to-document
  {:parameters [id documentId userId path]
   :categories #{:documents}
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks [(partial editable-by-state? :documentId states/update-doc-states)
                user-can-be-set-validator
                validate-user-authz-by-document-id
                application/validate-authority-in-drafts
                (partial doc-disabled-validator :documentId)]}
  [{:keys [created application] :as command}]
  (doc-persistence/do-set-user-to-document application documentId userId path created))

(defcommand set-current-user-to-document
  {:parameters [id documentId path]
   :categories #{:documents}
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks [(partial editable-by-state? :documentId states/update-doc-states)
                domain/validate-owner-or-write-access
                validate-user-authz-by-document-id
                application/validate-authority-in-drafts
                (partial doc-disabled-validator :documentId)]}
  [{:keys [created application user] :as command}]
  (doc-persistence/do-set-user-to-document application documentId (:id user) path created))

(defcommand set-company-to-document
  {:parameters [id documentId companyId path]
   :categories #{:documents}
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks [(partial editable-by-state? :documentId states/update-doc-states)
                validate-user-authz-by-document-id
                application/validate-authority-in-drafts
                (partial doc-disabled-validator :documentId)]}
  [{:keys [user created application] :as command}]
  (if-let [document (domain/get-document-by-id application documentId)]
    (doc-persistence/do-set-company-to-document application document companyId path (user/get-user-by-id (:id user)) created)
    (fail :error.document-not-found)))
