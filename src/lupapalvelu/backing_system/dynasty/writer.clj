(ns lupapalvelu.backing-system.dynasty.writer
  "Apply side-effects to messages (send to dynasty, generate PDFs, write to mongo..)"
  (:require [clojure.java.io :as io]
            [lupapalvelu.backing-system.dynasty.api.case :as case-api]
            [lupapalvelu.backing-system.dynasty.api.client :as client]
            [lupapalvelu.backing-system.dynasty.api.client-impl :as client-impl]
            [lupapalvelu.backing-system.dynasty.api.common :as common-api]
            [lupapalvelu.backing-system.dynasty.api.document :as document-api]
            [lupapalvelu.backing-system.dynasty.logging :as message-logging]
            [lupapalvelu.backing-system.dynasty.message :as message]
            [lupapalvelu.backing-system.dynasty.util :as d-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.storage.file-storage :as storage]
            [sade.core :as sade]
            [sade.files :as files]
            [sade.shared-schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

;; Override these bindings in tests to isolate the side effects
(def ^:private ^:dynamic *generate-pdf* pdf-export/generate)
(def ^:private ^:dynamic *storage-download* storage/download)
(def ^:private ^:dynamic *dynasty-client* (client-impl/make-client))

(sc/defschema SubmittedIds
  (st/optional-keys
    {:case-id        case-api/CaseId
     :document-id    document-api/DocumentId
     :contact-ids    #{common-api/Guid}
     :attachment-ids #{document-api/DocumentId}
     ;; Keep track of uploaded FileIds for the BackingSystem API
     :file-ids       #{ssc/FileId}}))

(sc/defschema Context
  (st/merge
    {::server-config client/ServerConfig
     ::application   {sc/Any sc/Any}
     ::user          {sc/Any sc/Any}
     ::submitted     SubmittedIds}
    (st/optional-keys
      {::existing-case-id case-api/CaseId})))

(defn- ctx->case-id
  [ctx]
  (or (-> ctx ::existing-case-id)
      (-> ctx ::submitted :case-id)))

(defmulti submit!
  "Submit a message to Dynasty"
  {:private true}
  (fn [_ctx msg]
    (::message/message-type msg)))

(sc/defmethod submit! ::message/case :- Context
  [{::keys               [server-config application user]
    {application-id :id} ::application
    :as                  ctx} :- Context
   {::message/keys [payload]} :- message/CaseMessage]
  (log/info "Uploading case..")
  (when-let [case-id (ctx->case-id ctx)]
    (log/errorf "Aborting case upload, already uploaded with id '%s'!" case-id)
    (sade/fail! :error.integration.dynasty-case-upload-failed))
  (try
    (let [{:keys [id]} (-> *dynasty-client*
                           (message-logging/middleware application user)
                           (case-api/create server-config payload))]
      (log/infof "Uploaded case (id %s)!" id)
      (d-util/set-application-case-id! application-id id)
      (assoc-in ctx [::submitted :case-id] id))
    (catch Exception e
      ;; Abort the whole operation if we can't create the case
      (log/error e "Failed to upload case!")
      (sade/fail! :error.integration.dynasty-case-upload-failed))))

(sc/defmethod submit! ::message/document :- Context
  [{::keys [server-config application user] :as ctx} :- Context
   {::message/keys [payload file-entry]} :- message/DocumentMessage]
  (let [case-id (ctx->case-id ctx)]
    (log/with-logging-context
      {:caseId case-id}
      (try
        (log/info "Preparing & uploading document..")
        (files/with-temp-file permit-file
          (*generate-pdf* application "fi" permit-file)
          (log/info "Document prepared..")
          (let [client       (-> *dynasty-client*
                                 (message-logging/middleware application user))
                document     (assoc-in payload [:case-attachment-options :case-selector :id] case-id)
                file-entry   (assoc file-entry :content permit-file)
                {:keys [id]} (document-api/create client server-config document file-entry)]
            (log/infof "Uploaded document (id %s)!" id)
            (d-util/set-application-document-id! (:id application) id)
            (assoc-in ctx [::submitted :document-id] id)))
        (catch Exception e
          ;; We've already created a case, don't abort if we can't upload the document
          (log/error e "Failed to prepare & upload document, ignoring..")
          ctx)))))

(sc/defmethod submit! ::message/attachment :- Context
  [{::keys [server-config application user] :as ctx} :- Context
   {::message/keys [payload attachment-id file-id]} :- message/AttachmentMessage]
  (let [case-id (ctx->case-id ctx)]
    (log/with-logging-context
      {:caseId       case-id
       :attachmentId attachment-id
       :fileId       file-id}
      (try
        (log/infof "Downloading & uploading attachment (%s).." file-id)
        (files/with-temp-file permit-file
          (let [{:keys [content contentType filename]} (*storage-download* application file-id)]
            (with-open [contents (content)]
              (io/copy contents permit-file))
            (log/info "Attachment downloaded..")
            (let [client       (-> *dynasty-client*
                                   (message-logging/middleware application user))
                  document     (assoc-in payload [:case-attachment-options :case-selector :id] case-id)
                  file-entry   {:content   permit-file
                                :mime-type contentType
                                :name      filename}
                  {:keys [id]} (document-api/create client server-config document file-entry)]
              (log/infof "Uploaded attachment (document id %s)!" id)
              (d-util/set-attachment-document-id (:id application) attachment-id id)
              (-> ctx
                  (update-in [::submitted :attachment-ids] (fnil conj #{}) id)
                  (update-in [::submitted :file-ids] (fnil conj #{}) file-id)))))
        (catch Exception e
          ;; We've already created a case, don't abort if we can't upload the document
          (log/error e "Failed to download & upload attachment, ignoring..")
          ctx)))))

(sc/defmethod submit! ::message/contact :- Context
  [{::keys [server-config application user]
    :as    ctx} :- Context
   {::message/keys [payload lp-document-id]} :- message/ContactMessage]
  (let [case-id (ctx->case-id ctx)]
    (log/with-logging-context
      {:caseId case-id}
      (try
        (log/info "Uploading contact..")
        (let [{:keys [guid]} (-> *dynasty-client*
                                 (message-logging/middleware application user)
                                 (case-api/add-contact server-config case-id payload))]
          (log/infof "Uploaded contact (guid %s)!" guid)
          (d-util/set-applicant-contact-id (:id application) lp-document-id guid)
          (update-in ctx [::submitted :contact-ids] (fnil conj #{}) guid))
        (catch Exception e
          ;; We've already created a case, don't abort if a particular contact fails to upload
          (log/error e "Failed to upload contact, ignoring.." )
          ctx)))))

(sc/defn submit-messages! :- SubmittedIds
  "Submit message(s) to Dynasty.

  Note that we first try to submit the case. If it fails, we abort the whole
  operation immediately."
  {:always-validate true}
  [server-config :- client/ServerConfig
   application :- {sc/Any sc/Any}
   user :- {sc/Any sc/Any}
   messages :- [message/Message]]
  (let [case-id (d-util/get-case-id application)
        ctx     (cond-> {::server-config server-config
                         ::application   application
                         ::user          user
                         ::submitted     {}}
                  case-id (assoc ::existing-case-id case-id))]
    (-> (reduce submit! ctx messages)
        ::submitted)))
