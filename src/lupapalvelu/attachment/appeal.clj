(ns lupapalvelu.attachment.appeal
  (:require [sade.core :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.preview :as preview]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.storage.file-storage :as storage]))


(defn- create-appeal-attachment-data!
  "Return attachment model for new appeal attachment with version(s) included.
   If PDF, converts to PDF/A (if applicable) and thus creates a new file to GridFS as side effect.
   Initial uploaded file is references as originalFileId in version."
  [{app :application created :created user :user} appeal-id appeal-type file]
  ; File has been uploaded unlinked, so link it first.
  (storage/link-files-to-application (:id user) (:id app) [(:fileId file)])
  (let [type (att-type/attachment-type-for-appeal appeal-type)
        target {:id   appeal-id
                :type appeal-type}
        attachment-data (att/create-attachment-data app
                                                    {:attachment-type type
                                                     :created         created
                                                     :target          target
                                                     :locked          true
                                                     :read-only       true})
        {archivability-result :result
         converted-filedata   :file} (conversion/archivability-conversion {:application (:id app)} app file)
        version-data (merge {:fileId           (or (:fileId converted-filedata) (:fileId file))
                             :original-file-id (:fileId file)
                             :filename         (:filename file)
                             :contentType      (or (:contentType converted-filedata)
                                                   (:contentType file)
                                                   (:content-type file))
                             :size             (or (:size converted-filedata) (:size file))
                             :created          created
                             :stamped          false}
                            archivability-result)
        version-model (att/make-version attachment-data user version-data)]
    (preview/preview-image (:id app) version-data)
    (-> attachment-data
        (update :versions conj version-model)
        (assoc :latestVersion version-model))))

(defn new-appeal-attachment-updates!
  "Return $push operation for attachments, with attachments created for given fileIds.
   As a side effect, creates converted PDF/A version to mongo for PDF files (if applicable)."
  [{:keys [user] :as command} appeal-id appeal-type fileIds]
  (let [file-objects (->> fileIds
                          (map #(storage/download-unlinked-file (:id user) %))
                          seq)
        new-attachments (map
                          (partial
                            create-appeal-attachment-data!
                            command
                            appeal-id
                            appeal-type)
                          file-objects)]
    (when (seq new-attachments)
      {$push {:attachments {$each new-attachments}}})))


(defn appeals-attachments
  "Returns attachments regarding given appeal-ids"
  [{attachments :attachments} appeal-ids]
  (letfn [(appeal-filter [{target :target}]
            (and (some (hash-set (:type target)) ["appeal" "appealVerdict" "rectification"])
                 (some (hash-set (:id target)) appeal-ids)))]
    (filter appeal-filter attachments)))
