(ns lupapalvelu.attachment.appeal
  (:require [sade.core :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.preview :as preview]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.file-upload :as file-upload]))


(defn- create-appeal-attachment-data!
  "Return attachment model for new appeal attachment with version(s) included.
   If PDF, converts to PDF/A (if applicable) and thus creates a new file to GridFS as side effect.
   Initial uploaded file is references as originalFileId in version."
  [{app :application created :created user :user} appeal-id appeal-type file]
  (let [type                 (att-type/attachment-type-for-appeal appeal-type)
        target               {:id appeal-id
                              :type appeal-type}
        attachment-data      (att/create-attachment-data app
                                                         {:attachment-type type
                                                          :created created
                                                          :target target
                                                          :locked true
                                                          :read-only true})
        archivability-result (conversion/archivability-conversion app {:content ((:content file))
                                                                       :attachment-type type
                                                                       :filename (:file-name file)
                                                                       :contentType (or (:contentType file) (:content-type file))})
        converted-filedata   (when (:autoConversion archivability-result)
                               (file-upload/save-file (select-keys archivability-result [:content :filename]) :application (:id app)))
        version-data         (merge {:fileId           (or (:fileId converted-filedata) (:fileId file))
                                     :original-file-id (:fileId file)
                                     :filename         (or (:filename converted-filedata) (:file-name file))
                                     :contentType      (or (:contentType converted-filedata) (:contentType file) (:content-type file))
                                     :size             (or (:size converted-filedata) (:size file))
                                     :created created
                                     :stamped false}
                                    archivability-result)
        version-model        (att/make-version attachment-data user version-data)]
    (preview/preview-image! (:id app) (:fileId version-data) (:filename version-data) (:contentType version-data))
    (-> attachment-data
        (update :versions conj version-model)
        (assoc :latestVersion version-model))))

(defn new-appeal-attachment-updates!
  "Return $push operation for attachments, with attachments created for given fileIds.
   As a side effect, creates converted PDF/A version to mongo for PDF files (if applicable)."
  [command appeal-id appeal-type fileIds]
  (let [file-objects    (seq (mongo/download-find-many {:_id {$in fileIds}}))
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
            (and (= "appeal" (:type target))
                 (some (hash-set (:id target)) appeal-ids)))]
    (filter appeal-filter attachments)))
