(ns lupapalvelu.filebank-api
  (:require [lupapalvelu.action :refer [defquery defcommand defraw] :as action]
            [sade.core :refer [ok unauthorized fail]]
            [lupapalvelu.filebank :as filebank]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment-api :as att-api]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.job :as job]
            [lupapalvelu.user :as usr]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]))

(defquery get-filebank-files
  {:description      "Gets files for the application id from the filebank"
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:required [:organization/check-filebank-enabled
                                  :application/filebank-read]}]}
  (ok :files (:files (mongo/by-id :filebank id))))

(defquery filebank-enabled
  {:contexts    [usr/without-application-context]
   :permissions [{:required [:organization/check-filebank-enabled]}]
   :pre-checks  [filebank/validate-filebank-enabled]}
  (ok))

(defquery bind-filebank-job
  {:description      "Polls the given job for status so the frontend can know when the binding is done"
   :parameters       [jobId]
   :permissions      [{:required [:organization/edit-filebank]}]
   :input-validators [(partial action/non-blank-parameters [:jobId])]}
  [{{job-id :jobId version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (ok (job/status job-id (util/->long version) (util/->long timeout))))

(defcommand bind-filebank-files
  {:description      "Binds the files to the given application's filebank"
   :parameters       [id filedatas]
   :permissions      [{:required [:application/filebank-edit]}]
   :input-validators [(partial action/non-blank-parameters [:id])]}
  [command]
  (ok :job (->> filedatas
                (mapv #(select-keys % [:fileId :keywords]))
                (mapv #(assoc % :filebankId id))
                (bind/make-bind-job command :filebank))))

(defcommand update-filebank-keywords
  {:description      "Updates keywords to the given file"
   :parameters       [id fileId keywords]
   :permissions      [{:required [:application/filebank-edit]}]
   :input-validators [(partial action/non-blank-parameters [:id :fileId])
                      (partial action/vector-parameters-with-non-blank-items [:keywords])]}
  [_]
  (mongo/update :filebank
                {:_id id :files {$elemMatch {:file-id fileId}}}
                {$set {:files.$.keywords keywords}})
  (ok))

(defcommand delete-filebank-file
  {:description      "Removes the given file from the filebank.
                       Non-atomic: first deletes file, then updates document."
   :parameters       [id fileId]
   :permissions      [{:required [:application/filebank-edit]}]
   :input-validators [(partial action/non-blank-parameters [:id :fileId])]}
  [_]
  (filebank/delete-filebank-file! id fileId)
  (ok))

(defraw download-filebank-file
  {:description      "Downloads the given file from the filebank"
   :parameters       [id fileId]
   :permissions      [{:required [:application/filebank-read]}]
   :input-validators [(partial action/non-blank-parameters [:id :fileId])]}
  [_]
  (if-let [file-doc (filebank/find-filebank-file id fileId)]
    (-> (storage/download-from-system id ; Same as application ID
                                      fileId
                                      (:storageSystem file-doc))
        (att/output-attachment true))
    (fail :error.file-not-found)))

(defraw download-filebank-files
  {:description      "Downloads the given files from the filebank as a ZIP file"
   :parameters       [id fileIds]
   :permissions      [{:required [:application/filebank-read]}]
   :input-validators [(partial action/non-blank-parameters [:id :fileIds])]}
  [_]
  (let [fileIds (ss/split fileIds #",")]
    {:status  200
     :headers {"Content-Type"        "application/octet-stream"
               "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "filebank.zip.filename") "\"")}
     :body    (->> (att-api/use-tempfile-for-attachments? fileIds) ;Just checks against maximum number
                   (filebank/get-filebank-files-for-user! id fileIds))}))
