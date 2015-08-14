(ns lupapalvelu.document.document-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error]]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! unauthorized!]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.states :as states]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.application :as application]
            [lupapalvelu.user :as user]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.model :as model]))

;;
;; CRUD
;;

(defn- create-doc-validator [command {documents :documents permit-type :permitType}]
  ;; Hide the "Lisaa osapuoli" button when application contains "party" type documents and more can not be added.
  (when (and
          (not (permit/multiple-parties-allowed? permit-type))
          (some (comp (partial = "party") :type :schema-info) documents))
    (fail :error.create-doc-not-allowed)))

(defcommand create-doc
  {:parameters [:id :schemaName]
   :user-roles #{:applicant :authority}
   :states     #{:draft :answered :open :submitted :complement-needed}
   :pre-checks [create-doc-validator
                application/validate-authority-in-drafts]}
  [command]
  (ok :doc (:id (doc-persistence/do-create-doc command))))

(defn- deny-remove-of-primary-operation [document application]
  (= (get-in document [:schema-info :op :id]) (get-in application [:primaryOperation :id])))

(defn- deny-remove-of-last-document [document documents]
  (let [schema (:schema-info document)
        schema_count (count ( (keyword (:name schema)) (group-by (comp keyword :name :schema-info) documents)))]
    (and (:deny-removing-last-document schema) (= schema_count 1))))

(defcommand remove-doc
  {:parameters  [id docId]
    :user-roles #{:applicant :authority}
    :states     #{:draft :answered :open :submitted :complement-needed}
    :pre-checks [application/validate-authority-in-drafts]}
  [{:keys [application created] :as command}]
  (let [document (domain/get-document-by-id application docId)]
    (when (deny-remove-of-primary-operation document application)
      (fail! :removal-of-primary-document-denied))
    (when (deny-remove-of-last-document document (:documents application))
      (fail! :error.removal-of-last-document-denied))
    (when-not document
      (fail! :error.document-not-found))

    (doc-persistence/remove! command docId "documents")
    (ok)))


(defcommand update-doc
  {:parameters [id doc updates]
   :user-roles #{:applicant :authority}
   :states     (states/all-application-states-but [:sent :verdictGiven :constructionStarted :closed :canceled])
   :pre-checks [application/validate-authority-in-drafts]}
  [command]
  (doc-persistence/update! command doc updates "documents"))

(defcommand update-task
  {:parameters [id doc updates]
   :user-roles #{:applicant :authority}
   :states     (states/all-states-but [:sent :closed :canceled])
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

(defn- validate-approvability [{{:keys [doc path collection]} :data application :application}]
  (let [path-v (if (ss/blank? path) [] (ss/split path #"\."))
        document (doc-persistence/by-id application collection doc)]
    (if document
      (when-not (model/approvable? document path-v)
        (fail :error.document-not-approvable))
      (fail :error.document-not-found))))

(defn- ->approval-mongo-model
  "Creates a mongo update map of approval data.
   To be used within model/with-timestamp."
  [path status user]
  (let [mongo-path (if (ss/blank? path) "documents.$.meta._approved" (str "documents.$.meta." path "._approved"))]
    {$set {mongo-path (model/->approved status user)
           :modified (model/current-timestamp)}}))

(defn- approve [{{:keys [id doc path collection]} :data user :user created :created :as command} status]
  (or
    (validate-approvability command)
    (model/with-timestamp created
      (update-application
        command
        {collection {$elemMatch {:id doc}}}
        (->approval-mongo-model path status user)))))

(defcommand approve-doc
  {:parameters [:id :doc :path :collection]
   :input-validators [doc-persistence/validate-collection]
   :user-roles #{:authority}
   :states     (states/all-application-states-but [#_:info :draft :sent :verdictGiven :constructionStarted :closed :canceled])}
  [command]
  (approve command "approved"))

(defcommand reject-doc
  {:parameters [:id :doc :path :collection]
   :input-validators [doc-persistence/validate-collection]
   :user-roles #{:authority}
   :states     (states/all-application-states-but [#_:info :draft :sent :verdictGiven :constructionStarted :closed :canceled])}
  [command]
  (approve command "rejected"))

;;
;; Set party to document
;;

(defn- user-can-be-set? [user-id application]
  (and (domain/has-auth? application user-id) (domain/no-pending-invites? application user-id)))

(defcommand set-user-to-document
  {:parameters [id documentId userId path]
   :user-roles #{:applicant :authority}
   :pre-checks [(fn [{{user-id :userId} :data} application]
                  (when-not (or (ss/blank? user-id) (user-can-be-set? user-id application))
                    (fail :error.application-does-not-have-given-auth)))
                application/validate-authority-in-drafts]
   :states     (states/all-states-but [:info :sent :verdictGiven :constructionStarted :closed :canceled])}
  [{:keys [created application] :as command}]
  (doc-persistence/do-set-user-to-document application documentId userId path created))

(defcommand set-current-user-to-document
  {:parameters [id documentId path]
   :user-roles #{:applicant :authority}
   :pre-checks [domain/validate-owner-or-write-access
                application/validate-authority-in-drafts]
   :states     (states/all-states-but [:info :sent :verdictGiven :constructionStarted :closed :canceled])}
  [{:keys [created application user] :as command}]
  (doc-persistence/do-set-user-to-document application documentId (:id user) path created))

(defcommand set-company-to-document
  {:parameters [id documentId companyId path]
   :user-roles #{:applicant :authority}
   :states     (states/all-states-but [:info :sent :verdictGiven :constructionStarted :closed :canceled])
   :pre-checks [application/validate-authority-in-drafts]}
  [{:keys [user created application] :as command}]
  (if-let [document (domain/get-document-by-id application documentId)]
    (doc-persistence/do-set-company-to-document application document companyId path (user/get-user-by-id (:id user)) created)
    (fail :error.document-not-found)))
