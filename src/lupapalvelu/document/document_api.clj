(ns lupapalvelu.document.document-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error]]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! unauthorized! now]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.states :as states]
            [lupapalvelu.application :as application]
            [lupapalvelu.user :as user]
            [lupapalvelu.document.document :refer :all]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.tools :as tools]))


(def update-doc-states (states/all-application-states-but (conj states/terminal-states :sent :verdictGiven :constructionStarted)))

(def approve-doc-states (states/all-application-states-but (conj states/terminal-states :draft :sent :verdictGiven :constructionStarted)))

;;
;; CRUD
;;

(defcommand create-doc
  {:parameters [:id :schemaName]
   :optional-parameters [updates fetchRakennuspaikka]
   :user-roles #{:applicant :authority}
   :states     #{:draft :answered :open :submitted :complement-needed}
   :pre-checks [create-doc-validator
                application/validate-authority-in-drafts]}
  [command]
  (let [document (doc-persistence/do-create-doc command updates)]
    (when fetchRakennuspaikka
      (let [
            property-id (or
                          (tools/get-update-item-value updates "kiinteisto.kiinteistoTunnus")
                          (get-in command [:application :propertyId]))]
        (fetch-and-persist-ktj-tiedot (:application command) document property-id (now))))
    (ok :doc (:id document))))

(defcommand remove-doc
  {:parameters  [id docId]
    :user-roles #{:applicant :authority}
    :states     #{:draft :answered :open :submitted :complement-needed}
    :pre-checks [application/validate-authority-in-drafts
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
   :states     update-doc-states
   :pre-checks [application/validate-authority-in-drafts]}
  [command]
  (doc-persistence/update! command doc updates "documents"))

(defcommand update-task
  {:parameters [id doc updates]
   :user-roles #{:applicant :authority}
   :states     (states/all-application-states-but (conj states/terminal-states :sent))
   :pre-checks [application/validate-authority-in-drafts]}
  [command]
  (doc-persistence/update! command doc updates "tasks"))

(defcommand remove-document-data
  {:parameters       [id doc path collection]
   :user-roles       #{:applicant :authority}
   :states           #{:draft :answered :open :submitted :complement-needed}
   :input-validators [doc-persistence/validate-collection]
   :pre-checks       [application/validate-authority-in-drafts]}
  [{:keys [created application] :as command}]
  (let [document  (doc-persistence/by-id application collection doc)
        str-path  (ss/join "." path)
        data-path (str collection ".$.data." str-path)
        meta-path (str collection ".$.meta." str-path)]
    (when-not document (fail! :error.document-not-found))
    (update-application command
      {:documents {$elemMatch {:id (:id document)}}}
      {$unset {data-path "" meta-path ""}})))

;;
;; Document validation
;;

(defquery validate-doc
  {:parameters       [:id doc collection]
   :user-roles       #{:applicant :authority}
   :states           states/all-states
   :input-validators [doc-persistence/validate-collection]
   :user-authz-roles action/all-authz-roles
   :org-authz-roles  action/reader-org-authz-roles}
  [{:keys [application]}]
  (debug doc collection)
  (let [document (doc-persistence/by-id application collection doc)]
    (when-not document (fail! :error.document-not-found))
    (ok :results (model/validate application document))))

(defquery fetch-validation-errors
  {:parameters       [:id]
   :user-roles       #{:applicant :authority}
   :user-authz-roles action/all-authz-roles
   :org-authz-roles  action/reader-org-authz-roles
   :states           states/all-states}
  [{app :application}]
  (let [results (for [doc (:documents app)] (model/validate app doc))]
    (ok :results results)))

;;
;; Document approvals
;;

(defcommand approve-doc
  {:parameters [:id :doc :path :collection]
   :input-validators [doc-persistence/validate-collection]
   :user-roles #{:authority}
   :states     approve-doc-states}
  [command]
  (ok :approval (approve command "approved")))

(defcommand reject-doc
  {:parameters [:id :doc :path :collection]
   :input-validators [doc-persistence/validate-collection]
   :user-roles #{:authority}
   :states     approve-doc-states}
  [command]
  (ok :approval (approve command "rejected")))

;;
;; Set party to document
;;

(defcommand set-user-to-document
  {:parameters [id documentId userId path]
   :user-roles #{:applicant :authority}
   :pre-checks [user-can-be-set-validator
                application/validate-authority-in-drafts]
   :states     update-doc-states}
  [{:keys [created application] :as command}]
  (doc-persistence/do-set-user-to-document application documentId userId path created))

(defcommand set-current-user-to-document
  {:parameters [id documentId path]
   :user-roles #{:applicant :authority}
   :pre-checks [domain/validate-owner-or-write-access
                application/validate-authority-in-drafts]
   :states     update-doc-states}
  [{:keys [created application user] :as command}]
  (doc-persistence/do-set-user-to-document application documentId (:id user) path created))

(defcommand set-company-to-document
  {:parameters [id documentId companyId path]
   :user-roles #{:applicant :authority}
   :states     update-doc-states
   :pre-checks [application/validate-authority-in-drafts]}
  [{:keys [user created application] :as command}]
  (if-let [document (domain/get-document-by-id application documentId)]
    (doc-persistence/do-set-company-to-document application document companyId path (user/get-user-by-id (:id user)) created)
    (fail :error.document-not-found)))


;;
;; Repeating
;;

(defcommand copy-row
  {:parameters [id doc path source-index target-index]
   :user-roles #{:applicant :authority}
   :states     update-doc-states
   :pre-checks [application/validate-authority-in-drafts]}
  [{application :application :as command}]
  (let [document (-> application
                     (domain/get-document-by-id doc)
                     (get-in (cons :data (map keyword path))))
        updates (->> (get document ((comp keyword str) source-index))
                     (map (fn [[key {value :value}]] 
                            [(->> key (name) (conj path (str target-index)))
                             value]))
                     (filter second))]
    (doc-persistence/update! command doc updates "documents")))
