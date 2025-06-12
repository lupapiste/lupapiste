(ns lupapalvelu.conversion.cleaner
  "Mechanisms for safely remove (erroneously) converted applications"
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.schemas :refer [ConversionDocument DeleteOptions]]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log :refer [info]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [schema.core :as sc]))

(sc/defn ^:always-validate deletable-application!
  "Checks whether a converted application can be deleted. Returns application on success,
  throws if the application cannot be deleted. Returns nil, but does not throw if the
  application does not exist."
  ([{app-id :LP-id :as doc} :- ConversionDocument {:keys [force? nuke?]} :- DeleteOptions]
   (let [nope (fn [text]
                (let [msg (format "Cannot delete %s: %s." app-id text)]
                  (log/with-logging-context {:applicationId app-id}
                    (info msg))
                  (throw (ex-info msg {:application-id app-id}))))]
     (when-let [{:keys [organization archived facta-imported attachments auth]
                 :as   application} (mongo/by-id :applications app-id)]
       (when-not facta-imported
         (nope "Not a converted application"))

       (when-not (= organization (:organization doc))
         (nope "Wrong organization"))

       (when-not nuke?
         (when (:initial archived)
           (nope "Archived") )

         (when (mongo/any? :invoices {:application-id app-id})
           (nope "Invoices"))

         (when (mongo/any? :filebank {:_id app-id})
           (nope "Filebank"))

         (when (mongo/any? :application-bulletins {:_id (re-pattern app-id)})
           (nope "Bulletin"))

         (when (mongo/any? :linked-file-metadata
                           {:target-entity.application-id app-id})
           (nope "Linked file metadata")))

       (when-not (or force? nuke?)
         (when-not (every? conv-util/conversion-or-batchrun-user? auth)
           (nope "Authed users"))

         (when-not (every? #(some-> % :latestVersion :user
                                    conv-util/conversion-or-batchrun-user?)
                           attachments)
           (nope "User attachments")))
       application)))
  ([doc :- ConversionDocument]
   (deletable-application! doc {})))

(defn can-be-deleted?
  "Non-throwing predicate wrapper for `delete-check!`. False only if the wrapped function
  throws, true otherwise. Thus, a non-existing application can be deleted."
  ([conversion-doc delete-options]
   (try
     (deletable-application! conversion-doc delete-options)
     true
     (catch Exception _
       false)))
  ([conversion-doc]
   (can-be-deleted? conversion-doc {})))

(defn delete-conversion-document! [doc-id]
  (info "Delete conversion document" doc-id)
  (mongo/remove-by-id :conversion doc-id)
  nil)

(sc/defn ^:always-validate clean-conversion! :- (sc/maybe ConversionDocument)
  "Deletes the converted application, its attachments and links. If
  `delete-conversion-document?` option is true (default false) then the conversion
  document is deleted, too. Otherwise, the conversion document is just reset: `:converted`
  and `:linked` are set to false and refers a non-existing application. This does not
  cause any problems, since the application-ids sequenced by mongo, so the same
  application-id cannot be created outside of the conversion. Throws if the conversion
  cannot be cleaned (the application cannot be deleted). If the application does not
  exist, but `delete-conversion-document?` option is given, the document is
  deleted. Returns cleaned document (or nil)."
  ([{doc-id :id :as doc} :- ConversionDocument
    {:keys [delete-conversion-document?] :as opts} :- DeleteOptions]
   (when-let [{app-id :id
               :as    application} (deletable-application! doc opts)]
     ;; Cautious double checks just in case.
     (assert (ss/not-blank? app-id) "Application id")
     (assert (= app-id (:LP-id doc)) "Matching application")
     (assert (= (:organization doc) (:organization application))
             "Organization")
     (assert (:facta-imported application)
             "Only imported applications can be deleted")

     (log/with-logging-context {:applicationId app-id}
       ;; Delete attachments with files.
       (when-let [att-ids (->> (:attachments application)
                               (keep (fn [{:keys [id latestVersion]}]
                                       (when latestVersion
                                         id)))
                               ;; Just in case, duplicate ids are possible
                               distinct
                               seq)]
         (att/delete-attachments-assignments! app-id att-ids)
         (att/delete-attachments-files! application att-ids))
       (info "Delete application" app-id "and its links.")
       ;; Delete links
       (mongo/remove-many :app-links {:link app-id})
       ;; Delete application
       (mongo/remove-by-id :applications app-id)))
   (if delete-conversion-document?
     (delete-conversion-document! doc-id)
     (conv-doc/upsert-document :clean doc-id)))
  ([doc :- ConversionDocument]
   (clean-conversion! doc {})))
