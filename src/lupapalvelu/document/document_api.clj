(ns lupapalvelu.document.document-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error]]
            [clojure.set :refer [intersection]]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! unauthorized unauthorized! now]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [lupapalvelu.document.document :refer :all]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.tools :as tools]))


(def update-doc-states #{:draft :open :submitted :complementNeeded})

(def approve-doc-states #{:open :submitted :complementNeeded})

(defn validate-is-construction-time-doc
  [{{doc-id :doc} :data app :application}]
  (when doc-id
    (when-not (some-> (domain/get-document-by-id (:documents app) doc-id)
                      (model/get-document-schema)
                      (get-in [:info :construction-time]))
      (fail :error.document-not-construction-time-doc))))

(defn validate-user-authz
  [{:keys [data application user]}]
  (let [doc-id (or (:documentId data) (:doc data))
        authority? (auth/application-authority? application user)
        schema (some-> (domain/get-document-by-id application doc-id) model/get-document-schema)
        user-roles (->> user :id (auth/get-auths application) (map (comp keyword :role)) set)
        allowed-roles (get-in schema [:info :user-authz-roles] auth/default-authz-writer-roles)]
    (when (and doc-id (not authority?))
      (cond
        (nil? schema) (fail :error.document-not-found)
        (empty? (intersection allowed-roles user-roles)) unauthorized))))

(defquery document
  {:parameters       [:id doc collection]
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
   :optional-parameters [updates fetchRakennuspaikka]
   :input-validators [(partial action/non-blank-parameters [:id :schemaName])]
   :user-roles #{:applicant :authority}
   :states     #{:draft :answered :open :submitted :complementNeeded}
   :pre-checks [create-doc-validator
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
   :input-validators [(partial action/non-blank-parameters [:id :docId])]
    :user-roles #{:applicant :authority}
    :states     #{:draft :answered :open :submitted :complementNeeded}
    :pre-checks [application/validate-authority-in-drafts
                 validate-user-authz
                 remove-doc-validator]}
  [{:keys [application created] :as command}]
  (if-let [document (domain/get-document-by-id application docId)]
    (do
      (doc-persistence/remove! command docId "documents")
      (ok))
    (fail :error.document-not-found)))

(defcommand update-doc
  {:parameters [id doc updates]
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :states     update-doc-states
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :pre-checks [validate-user-authz
                application/validate-authority-in-drafts]}
  [command]
  (doc-persistence/update! command doc updates "documents"))

(defcommand update-construction-time-doc
  {:parameters [id doc updates]
   :user-roles #{:applicant :authority}
   :states     states/post-verdict-states
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :pre-checks [validate-user-authz
                application/validate-authority-in-drafts
                validate-is-construction-time-doc]}
  [command]
  (doc-persistence/update! command doc updates "documents"))

(defcommand update-task
  {:parameters [id doc updates]
   :user-roles #{:applicant :authority}
   :states     (states/all-application-states-but (conj states/terminal-states :sent))
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :pre-checks [validate-user-authz
                application/validate-authority-in-drafts]}
  [command]
  (doc-persistence/update! command doc updates "tasks"))

(defcommand remove-document-data
  {:parameters       [id doc path collection]
   :user-roles       #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :states           #{:draft :answered :open :submitted :complementNeeded}
   :input-validators [doc-persistence/validate-collection]
   :pre-checks       [validate-user-authz
                      application/validate-authority-in-drafts]}
  [command]
  (doc-persistence/remove-document-data command doc [path] collection))

(defcommand remove-construction-time-document-data
  {:parameters       [id doc path collection]
   :user-roles       #{:applicant :authority}
   :states           states/post-verdict-states
   :input-validators [doc-persistence/validate-collection]
   :pre-checks       [validate-is-construction-time-doc
                      validate-user-authz
                      application/validate-authority-in-drafts]}
  [command]
  (doc-persistence/remove-document-data command doc [path] collection))

;;
;; Document validation
;;

(defquery validate-doc
  {:parameters       [:id doc collection]
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
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])
                      doc-persistence/validate-collection]
   :user-roles #{:authority}
   :states     approve-doc-states}
  [command]
  (ok :approval (approve command "approved")))

(defcommand approve-construction-time-doc
  {:parameters [:id :doc :path :collection]
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])
                      doc-persistence/validate-collection]
   :user-roles #{:authority}
   :states     states/post-verdict-states
   :pre-checks [validate-is-construction-time-doc]}
  [command]
  (ok :approval (approve command "approved")))

(defcommand reject-doc
  {:parameters [:id :doc :path :collection]
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])
                      doc-persistence/validate-collection]
   :user-roles #{:authority}
   :states     approve-doc-states}
  [command]
  (ok :approval (approve command "rejected")))

(defcommand reject-construction-time-doc
  {:parameters [:id :doc :path :collection]
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])
                      doc-persistence/validate-collection]
   :user-roles #{:authority}
   :states     states/post-verdict-states
   :pre-checks [validate-is-construction-time-doc]}
  [command]
  (ok :approval (approve command "rejected")))

;;
;; Set party to document
;;

(defcommand set-user-to-document
  {:parameters [id documentId userId path]
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks [user-can-be-set-validator
                validate-user-authz
                application/validate-authority-in-drafts]
   :states     update-doc-states}
  [{:keys [created application] :as command}]
  (doc-persistence/do-set-user-to-document application documentId userId path created))

(defcommand set-current-user-to-document
  {:parameters [id documentId path]
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks [domain/validate-owner-or-write-access
                validate-user-authz
                application/validate-authority-in-drafts]
   :states     update-doc-states}
  [{:keys [created application user] :as command}]
  (doc-persistence/do-set-user-to-document application documentId (:id user) path created))

(defcommand set-company-to-document
  {:parameters [id documentId companyId path]
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :states     update-doc-states
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks [validate-user-authz
                application/validate-authority-in-drafts]}
  [{:keys [user created application] :as command}]
  (if-let [document (domain/get-document-by-id application documentId)]
    (doc-persistence/do-set-company-to-document application document companyId path (user/get-user-by-id (:id user)) created)
    (fail :error.document-not-found)))
