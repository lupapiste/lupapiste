(ns lupapalvelu.backing-system.dynasty.util
  (:require [lupapalvelu.application :as application]
            [lupapalvelu.backing-system.dynasty.api.case :as case]
            [lupapalvelu.backing-system.dynasty.api.common :as common-api]
            [lupapalvelu.backing-system.dynasty.api.document :as document-api]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$elemMatch $set]]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(defn application->applicant-documents
  [application]
  (->> application
       common/documents-without-blanks
       tools/unwrapped
       domain/get-applicant-documents
       (sort-by :created)))

(defn- set-integration-key!
  [ikey app-id ivalue]
  (application/set-integration-key app-id ikey ivalue))

(defn- get-integration-key
  [ikey application]
  (-> application :integrationKeys ikey))

(sc/defn set-application-case-id!
  {:always-validate true}
  [application-id :- ssc/ApplicationId
   case-id :- case/CaseId]
  (set-integration-key! :dynastyCaseId application-id case-id))

(def get-case-id (partial get-integration-key :dynastyCaseId))

(sc/defn set-application-document-id!
  {:always-validate true}
  [application-id :- ssc/ApplicationId
   document-id :- document-api/DocumentId]
  (set-integration-key! :dynastyDocumentId application-id document-id))

(def get-document-id (partial get-integration-key :dynastyDocumentId))

(defn get-applicant-contact-id
  "Try to find the Dynasty contact id within a (lupapiste) document"
  [lp-document]
  (-> lp-document :meta :_integrationKeys :dynastyContactId))

(sc/defn set-applicant-contact-id
  "Assign a Dynasty contact id to the (lupapiste) document within the application"
  {:always-validate true}
  [application-id :- ssc/ApplicationId
   document-id :- ssc/ObjectIdStr
   contact-id :- common-api/Guid]
  (mongo/update-by-query
    :applications
    {:_id       application-id
     :documents {$elemMatch {:id document-id}}}
    {$set {:documents.$.meta._integrationKeys.dynastyContactId contact-id}}))

(defn get-attachment-document-id
  "Try to find the Dynasty document id within a (lupapiste) attachment"
  [attachment]
  (-> attachment :metadata :_integrationKeys :dynastyDocumentId))

;; TODO: How should we handle attachment version updates?

(sc/defn set-attachment-document-id
  [application-id :- ssc/ApplicationId
   attachment-id :- ssc/ObjectIdStr
   document-id :- document-api/DocumentId]
  (mongo/update-by-query
    :applications
    {:_id         application-id
     :attachments {$elemMatch {:id attachment-id}}}
    {$set {:attachments.$.metadata._integrationKeys.dynastyDocumentId document-id}}))
