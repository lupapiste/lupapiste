(ns lupapalvelu.storage.file-migration
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [monger.operators :refer [$set]]
            [pandect.core :refer [sha1]]
            [taoensso.timbre :refer [info error]]
            [sade.env :as env]
            [lupapiste-commons.external-preview :as ext-preview]
            [lupapalvelu.action :as action]
            [lupapalvelu.domain :refer [get-application-no-access-checking]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :refer [s3-id application-bucket]]
            [lupapalvelu.storage.s3 :as s3])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn move-application-mongodb-files-to-s3 [id]
  {:pre [(string? id)]}
  (assert (env/feature? :s3) "s3 feature must be enabled")
  (let [{:keys [attachments] :as application} (get-application-no-access-checking id [:attachments :organization])
        preview-placeholder-sha1 (sha1 (ext-preview/placeholder-image-is))]
    (doseq [{:keys [versions latestVersion] att-id :id} attachments
            [idx {:keys [fileId originalFileId storageSystem]}] (map-indexed vector versions)
            :when (and (= (keyword storageSystem) :mongodb)
                       (some? fileId)
                       (not (.isInterrupted (Thread/currentThread))))]
      (info "Migrating attachment" att-id "version" idx)
      (doseq [file-id (if (= fileId originalFileId)
                        [fileId (str fileId "-preview")]
                        [fileId originalFileId (str fileId "-preview")])]
        (let [{:keys [content contentType filename metadata]} (mongo/download file-id)
              bos (ByteArrayOutputStream.)]
          (if content
            (do (with-open [is (content)]
                  (io/copy is bos))
                (let [mongo-data (.toByteArray bos)
                      mongo-data-sha1 (sha1 mongo-data)]
                  ; Do not copy the preview image if it is only the placeholder
                  (when (not= mongo-data-sha1 preview-placeholder-sha1)
                    (info "Uploading file" file-id "to s3")
                    (s3/put-file-or-input-stream application-bucket
                                                 (s3-id id file-id)
                                                 filename
                                                 contentType
                                                 (ByteArrayInputStream. mongo-data)
                                                 metadata)
                    (with-open [s3-data ((:content (s3/download application-bucket (s3-id id file-id))))]
                      (when (not= mongo-data-sha1 (sha1 s3-data))
                        (throw (Exception. (str "Data in MongoDB and S3 do not match for " (s3-id id file-id)))))))))
            (when-not (str/ends-with? file-id "preview")
              (error "File" file-id "not found in GridFS but linked on" id "attachment" att-id)))))
      (info "Changing attachment" att-id "version" idx "storageSystem to s3")
      (action/update-application
        (action/application->command application)
        {:attachments.id att-id}
        {$set (cond-> {(str "attachments.$.versions." idx ".storageSystem") :s3}
                      (= fileId (:fileId latestVersion))
                      (assoc "attachments.$.latestVersion.storageSystem" :s3))})
      (mongo/delete-file-by-id fileId)
      (when-not (= fileId originalFileId)
        (info "Deleting attachment" att-id "version" idx "original file" originalFileId "from GridFS")
        (mongo/delete-file-by-id originalFileId))
      (mongo/delete-file-by-id (str fileId "-preview"))
      (when (not= (:fileId latestVersion) (:fileId (last versions)))
        (error "Latest version fileId does not match the fileId of last element in versions in attachment" att-id)))))
